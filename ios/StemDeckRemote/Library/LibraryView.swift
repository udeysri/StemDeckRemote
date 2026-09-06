import SwiftUI

struct LibraryView: View {
    let server: PairedServer
    @StateObject private var viewModel = LibraryViewModel()
    @ObservedObject private var folderStore = LibraryFolderStore.shared
    @ObservedObject private var trashStore = TrashedSongStore.shared
    @State private var showingForgetConfirmation = false
    @ObservedObject private var store = PairingStore.shared

    @State private var isSelecting = false
    @State private var selectedIDs: Set<String> = []
    @State private var isShowingManageFolders = false
    @State private var isShowingMoveDialog = false
    @State private var collapsedSectionIDs: Set<UUID> = []

    /// Stands in for "Unassigned" in `collapsedSectionIDs`/`FolderSection.id`
    /// — there's no real `LibraryFolder` for it, just this fixed marker.
    private static let unassignedSectionID = UUID(uuidString: "00000000-0000-0000-0000-000000000001")!
    /// Same idea as `unassignedSectionID`, for the Trash section.
    private static let trashSectionID = UUID(uuidString: "00000000-0000-0000-0000-000000000002")!

    var body: some View {
        NavigationStack {
            content
                .navigationTitle("StemDeck")
                .toolbar {
                    ToolbarItem(placement: .topBarLeading) {
                        if isSelecting {
                            Button(isAllSelected ? "Deselect All" : "Select All") {
                                toggleSelectAll()
                            }
                        } else {
                            Menu {
                                Picker("Sort", selection: $viewModel.sortOption) {
                                    ForEach(SongSortOption.allCases) { option in
                                        Label(option.rawValue, systemImage: option.systemImage).tag(option)
                                    }
                                }
                            } label: {
                                Image(systemName: "arrow.up.arrow.down")
                            }
                        }
                    }
                    ToolbarItemGroup(placement: .topBarTrailing) {
                        if isSelecting {
                            Button("Cancel") { exitSelection() }
                        } else {
                            Menu {
                                Button {
                                    isSelecting = true
                                } label: {
                                    Label("Select Songs", systemImage: "checkmark.circle")
                                }
                                Button {
                                    isShowingManageFolders = true
                                } label: {
                                    Label("Manage Folders", systemImage: "folder")
                                }
                            } label: {
                                Image(systemName: "folder")
                            }
                            Menu {
                                Text(server.displayAddress)
                                Button("Disconnect", role: .destructive) {
                                    showingForgetConfirmation = true
                                }
                            } label: {
                                Image(systemName: "gearshape")
                            }
                        }
                    }
                }
                .confirmationDialog(
                    "Disconnect from \(server.displayAddress)?",
                    isPresented: $showingForgetConfirmation,
                    titleVisibility: .visible
                ) {
                    Button("Disconnect", role: .destructive) {
                        store.forget()
                    }
                }
                .confirmationDialog(
                    "Move \(selectedIDs.count) Song\(selectedIDs.count == 1 ? "" : "s")",
                    isPresented: $isShowingMoveDialog,
                    titleVisibility: .visible
                ) {
                    ForEach(folderStore.folders) { folder in
                        Button(folder.name) {
                            folderStore.assign(jobIDs: selectedIDs, toFolder: folder.id)
                            exitSelection()
                        }
                    }
                    Button("Unassigned") {
                        folderStore.assign(jobIDs: selectedIDs, toFolder: nil)
                        exitSelection()
                    }
                    Button("Cancel", role: .cancel) {}
                }
                .sheet(isPresented: $isShowingManageFolders) {
                    ManageFoldersView()
                }
                .searchable(text: $viewModel.searchText, prompt: "Search songs")
        }
        .task {
            viewModel.configure(with: server)
            await viewModel.refresh()
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.state {
        case .idle:
            ProgressView("Loading library…")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .loading where viewModel.songs.isEmpty:
            ProgressView("Loading library…")
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        case .unreachable:
            statusView(
                icon: "wifi.slash",
                title: "Can't Reach StemDeck",
                message: "Check that StemDeck is running on \(server.displayAddress) and that your iPhone is on the same Wi-Fi network."
            )
        case .certificateChanged:
            statusView(
                icon: "exclamationmark.shield",
                title: "Certificate Changed",
                message: "StemDeck's certificate no longer matches what this app trusted. Disconnect and pair again."
            )
        case .refused(let detail):
            statusView(
                icon: "lock.slash",
                title: "StemDeck Refused This Connection",
                message: detail
            )
        default:
            songList
        }
    }

    /// `viewModel.displayedSongs` with anything the user has trashed
    /// (`TrashedSongStore`) excluded — the set every "normal" rendering
    /// path (plain list, folder buckets) draws from. Trashed songs still
    /// exist in `viewModel.songs`/on StemDeck; this is a pure display
    /// exclusion; see `TrashedSongStore` for why.
    private var visibleSongs: [Job] {
        viewModel.displayedSongs.filter { !trashStore.isTrashed($0.id) }
    }

    /// Trashed songs matching the current search, newest-trashed first —
    /// same title-substring predicate `displayedSongs` uses, replicated
    /// here since trashed entries are their own snapshots, independent of
    /// `viewModel.songs`.
    private var trashedSongs: [Job] {
        let trimmed = viewModel.searchText.trimmingCharacters(in: .whitespaces)
        let matching = trimmed.isEmpty
            ? trashStore.entries
            : trashStore.entries.filter { ($0.job.title ?? "").localizedCaseInsensitiveContains(trimmed) }
        return matching.sorted { $0.trashedAt > $1.trashedAt }.map(\.job)
    }

    private var songList: some View {
        Group {
            if viewModel.songs.isEmpty {
                statusView(
                    icon: "music.note.list",
                    title: "No Songs Yet",
                    message: "Separate a track in StemDeck on your computer, then pull to refresh here."
                )
            } else if visibleSongs.isEmpty && trashedSongs.isEmpty {
                statusView(
                    icon: "magnifyingglass",
                    title: "No Matches",
                    message: "No songs match \"\(viewModel.searchText)\"."
                )
            } else if folderStore.folders.isEmpty && trashStore.entries.isEmpty {
                // No folders created yet and nothing's ever been trashed —
                // the plain list everyone starts with. Gated on
                // `trashStore.entries` (not the search-filtered
                // `trashedSongs`) so typing in the search field never flips
                // this mode on/off by itself — see the crash this used to
                // cause, below.
                List {
                    ForEach(visibleSongs) { job in songRow(job) }
                }
                .listStyle(.plain)
            } else {
                // `groupedSections`' outer list is deliberately kept
                // structurally stable (every real folder + Unassigned +
                // Trash always present, never appearing/disappearing based
                // on whether they currently have anything in them) — List
                // + ForEach + DisclosureGroup crashes
                // (NSInternalInconsistencyException, "invalid number of
                // items in section 0") when a *section's own existence*
                // changes in the same update as a sibling section's row
                // count, which a background download's progress ticks
                // made easy to hit right after trashing a song. Only each
                // section's *row count* varies now, which List's diffing
                // handles reliably.
                List {
                    ForEach(groupedSections) { section in
                        DisclosureGroup(isExpanded: isExpandedBinding(for: section.id)) {
                            ForEach(section.songs) { job in
                                if section.id == Self.trashSectionID {
                                    trashRow(job)
                                } else {
                                    songRow(job)
                                }
                            }
                        } label: {
                            HStack {
                                Image(systemName: section.icon)
                                    .foregroundStyle(.secondary)
                                Text(section.name)
                                Spacer()
                                Text("\(section.songs.count)")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }
                .listStyle(.plain)
                .transaction { $0.disablesAnimations = true }
            }
        }
        .safeAreaInset(edge: .top, spacing: 0) {
            if viewModel.isOffline { offlineBanner }
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if isSelecting { selectionBar }
        }
        .refreshable { await viewModel.refresh() }
    }

    private struct FolderSection: Identifiable {
        let id: UUID
        let name: String
        let icon: String
        let songs: [Job]
    }

    /// `visibleSongs` (already search-filtered, sorted, and trash-excluded)
    /// partitioned into folders, in folder order, then Unassigned, then
    /// Trash. Every one of these sections is always present — even with
    /// zero matching songs — rather than appearing/disappearing based on
    /// content: `List`/`ForEach`/`DisclosureGroup` crashes
    /// (`NSInternalInconsistencyException`, "invalid number of items in
    /// section 0") when a section's own existence changes in the same
    /// update as a sibling section's row count, which trashing a song (or
    /// search filtering) made easy to trigger. Keeping this outer list's
    /// *identity* stable and only ever varying each section's *row count*
    /// is the fix — see the `.transaction` at the call site too.
    private var groupedSections: [FolderSection] {
        var bucket: [UUID: [Job]] = [:]
        var unassigned: [Job] = []
        let validFolderIDs = Set(folderStore.folders.map(\.id))
        for job in visibleSongs {
            if let folderID = folderStore.folderID(forJobID: job.id), validFolderIDs.contains(folderID) {
                bucket[folderID, default: []].append(job)
            } else {
                unassigned.append(job)
            }
        }
        var sections = folderStore.folders.map { folder in
            FolderSection(id: folder.id, name: folder.name, icon: "folder.fill", songs: bucket[folder.id] ?? [])
        }
        sections.append(FolderSection(id: Self.unassignedSectionID, name: "Unassigned", icon: "tray", songs: unassigned))
        sections.append(FolderSection(id: Self.trashSectionID, name: "Trash", icon: "trash", songs: trashedSongs))
        return sections
    }

    private func isExpandedBinding(for sectionID: UUID) -> Binding<Bool> {
        Binding(
            get: { !collapsedSectionIDs.contains(sectionID) },
            set: { isExpanded in
                if isExpanded {
                    collapsedSectionIDs.remove(sectionID)
                } else {
                    collapsedSectionIDs.insert(sectionID)
                }
            }
        )
    }

    @ViewBuilder
    private func songRow(_ job: Job) -> some View {
        Button {
            if isSelecting {
                if selectedIDs.contains(job.id) {
                    selectedIDs.remove(job.id)
                } else {
                    selectedIDs.insert(job.id)
                }
            } else {
                PlaybackCoordinator.shared.play(job: job, server: server)
            }
        } label: {
            HStack(spacing: 12) {
                if isSelecting {
                    Image(systemName: selectedIDs.contains(job.id) ? "checkmark.circle.fill" : "circle")
                        .foregroundStyle(selectedIDs.contains(job.id) ? Color.accentColor : Color.secondary)
                }
                SongRow(job: job)
            }
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing) {
            Button(role: .destructive) {
                trashSong(job)
            } label: {
                Label("Delete", systemImage: "trash")
            }
        }
    }

    /// A row inside the Trash section: same visual as a normal row, but
    /// tapping always plays (no selection-mode checkbox — trashed songs
    /// aren't part of the move-to-folder flow) and its only swipe action is
    /// Restore rather than Delete.
    @ViewBuilder
    private func trashRow(_ job: Job) -> some View {
        Button {
            PlaybackCoordinator.shared.play(job: job, server: server)
        } label: {
            SongRow(job: job)
        }
        .buttonStyle(.plain)
        .swipeActions(edge: .trailing) {
            Button {
                TrashedSongStore.shared.restore(job.id)
            } label: {
                Label("Restore", systemImage: "arrow.uturn.backward")
            }
            .tint(.blue)
        }
    }

    private func exitSelection() {
        isSelecting = false
        selectedIDs.removeAll()
    }

    /// True once every currently-displayed, non-trashed song (same set
    /// "Select All" fills in) is selected — drives the toolbar button's
    /// Select All ↔ Deselect All label.
    private var isAllSelected: Bool {
        let visibleIDs = visibleSongs.map(\.id)
        return !visibleIDs.isEmpty && visibleIDs.allSatisfy(selectedIDs.contains)
    }

    private func toggleSelectAll() {
        if isAllSelected {
            selectedIDs.removeAll()
        } else {
            selectedIDs = Set(visibleSongs.map(\.id))
        }
    }

    private func trashSong(_ job: Job) {
        selectedIDs.remove(job.id)
        TrashedSongStore.shared.trash(job)
        StemFileStore().deleteAll(jobID: job.id)
        StemDownloadQueue.shared.forget(jobID: job.id)
    }

    private var selectionBar: some View {
        HStack {
            Text("\(selectedIDs.count) selected")
                .font(.footnote)
                .foregroundStyle(.secondary)
            Spacer()
            Button("Move to Folder…") { isShowingMoveDialog = true }
                .disabled(selectedIDs.isEmpty)
        }
        .padding(.horizontal)
        .padding(.vertical, 10)
        .background(.bar)
    }

    private var offlineBanner: some View {
        Label("Offline — showing your downloaded library", systemImage: "wifi.slash")
            .font(.caption)
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 8)
            .background(Color(.secondarySystemBackground))
    }

    private func statusView(icon: String, title: String, message: String) -> some View {
        ScrollView {
            VStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 40))
                    .foregroundStyle(.secondary)
                Text(title)
                    .font(.headline)
                Text(message)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                    .multilineTextAlignment(.center)
                Button("Retry") { Task { await viewModel.refresh() } }
                    .buttonStyle(.bordered)
                    .padding(.top, 8)
            }
            .padding(32)
            .frame(maxWidth: .infinity, minHeight: 400)
        }
    }
}

private struct SongRow: View {
    let job: Job
    @ObservedObject private var downloadQueue = StemDownloadQueue.shared

    var body: some View {
        HStack(spacing: 12) {
            thumbnail
            VStack(alignment: .leading, spacing: 2) {
                Text(job.title ?? "Untitled")
                    .font(.body)
                    .lineLimit(1)
                if let subtitle = job.subtitle {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
                downloadStatus
            }
            Spacer()
            if let duration = job.formattedDuration {
                Text(duration)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .monospacedDigit()
            }
        }
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private var downloadStatus: some View {
        switch downloadQueue.status(for: job.id) {
        case .queued:
            Label("Queued to download", systemImage: "arrow.down.circle")
                .font(.caption2)
                .foregroundStyle(.secondary)
                .padding(.top, 2)
        case .downloading(let progress):
            VStack(alignment: .leading, spacing: 3) {
                Text("Downloading stems… \(Int(progress * 100))%")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
                ProgressView(value: progress)
            }
            .padding(.top, 2)
        case .failed:
            Label("Download failed — tap to retry", systemImage: "exclamationmark.circle")
                .font(.caption2)
                .foregroundStyle(.red)
                .padding(.top, 2)
        case .downloaded, .notQueued:
            EmptyView()
        }
    }

    @ViewBuilder
    private var thumbnail: some View {
        let placeholder = RoundedRectangle(cornerRadius: 6)
            .fill(Color(.secondarySystemBackground))
            .overlay(Image(systemName: "music.note").foregroundStyle(.secondary))

        Group {
            if let thumbnail = job.thumbnail, let url = URL(string: thumbnail) {
                AsyncImage(url: url) { phase in
                    if let image = phase.image {
                        image.resizable().aspectRatio(contentMode: .fill)
                    } else {
                        placeholder
                    }
                }
            } else {
                placeholder
            }
        }
        .frame(width: 44, height: 44)
        .clipShape(RoundedRectangle(cornerRadius: 6))
    }
}
