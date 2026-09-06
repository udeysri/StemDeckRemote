import SwiftUI

/// Create, delete, and reorder folders. Reordering and deleting reuse
/// List's native edit mode (drag handles + swipe-to-delete) rather than
/// custom gesture handling.
struct ManageFoldersView: View {
    @ObservedObject private var folderStore = LibraryFolderStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var isShowingNewFolderPrompt = false
    @State private var newFolderName = ""

    var body: some View {
        NavigationStack {
            List {
                if folderStore.folders.isEmpty {
                    Text("No folders yet. Create one to start organizing your songs.")
                        .foregroundStyle(.secondary)
                        .listRowSeparator(.hidden)
                } else {
                    ForEach(folderStore.folders) { folder in
                        Label(folder.name, systemImage: "folder.fill")
                    }
                    .onDelete { folderStore.deleteFolders(at: $0) }
                    .onMove { folderStore.moveFolders(fromOffsets: $0, toOffset: $1) }
                }
            }
            .navigationTitle("Folders")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Done") { dismiss() }
                }
                ToolbarItemGroup(placement: .primaryAction) {
                    if !folderStore.folders.isEmpty { EditButton() }
                    Button {
                        newFolderName = ""
                        isShowingNewFolderPrompt = true
                    } label: {
                        Image(systemName: "folder.badge.plus")
                    }
                }
            }
            .alert("New Folder", isPresented: $isShowingNewFolderPrompt) {
                TextField("Folder Name", text: $newFolderName)
                Button("Cancel", role: .cancel) {}
                Button("Create") {
                    let trimmed = newFolderName.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard !trimmed.isEmpty else { return }
                    folderStore.createFolder(name: trimmed)
                }
            }
        }
    }
}
