package com.stemdeck.remote.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.stemdeck.remote.playback.ConsoleTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.stemdeck.remote.models.Job
import com.stemdeck.remote.models.PairedServer
import com.stemdeck.remote.playback.PlaybackCoordinator
import com.stemdeck.remote.playback.StemDownloadQueue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(server: PairedServer, coordinator: PlaybackCoordinator, viewModel: LibraryViewModel = hiltViewModel()) {
    val songs by viewModel.songs.collectAsStateWithLifecycle()
    val displayedSongs by viewModel.displayedSongs.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val searchText by viewModel.searchText.collectAsStateWithLifecycle()
    val sortOption by viewModel.sortOption.collectAsStateWithLifecycle()
    val folders by viewModel.folderStore.folders.collectAsStateWithLifecycle()
    val assignments by viewModel.folderStore.assignments.collectAsStateWithLifecycle()
    val downloadStatuses by coordinator.stemDownloadQueue.statuses.collectAsStateWithLifecycle()
    val trashedEntries by viewModel.trashedSongStore.entries.collectAsStateWithLifecycle()

    // `displayedSongs` with anything the user has trashed excluded — the
    // set every "normal" rendering path (plain list, folder buckets)
    // draws from. Trashed songs still exist in `songs`/on StemDeck; this
    // is a pure display exclusion, same as the iOS side's `visibleSongs`.
    val trashedIDs = remember(trashedEntries) { trashedEntries.map { it.job.id }.toSet() }
    val visibleSongs = remember(displayedSongs, trashedIDs) { displayedSongs.filterNot { it.id in trashedIDs } }

    // Trashed songs matching the current search, newest-trashed first —
    // same title-substring predicate `displayedSongs` uses, replicated
    // here since trashed entries are their own snapshots, independent of
    // `viewModel.songs`.
    val trashedJobs = remember(trashedEntries, searchText) {
        val trimmed = searchText.trim()
        val matching = if (trimmed.isEmpty()) trashedEntries else trashedEntries.filter { it.job.title.orEmpty().contains(trimmed, ignoreCase = true) }
        matching.sortedByDescending { it.trashedAtEpochMillis }.map { it.job }
    }

    var isSelecting by rememberSaveable { mutableStateOf(false) }
    var selectedIDs by remember { mutableStateOf(setOf<String>()) }
    var isShowingManageFolders by rememberSaveable { mutableStateOf(false) }
    var isShowingMoveDialog by remember { mutableStateOf(false) }
    var isShowingSortMenu by remember { mutableStateOf(false) }
    var isShowingOverflowMenu by remember { mutableStateOf(false) }
    var isShowingSettingsMenu by remember { mutableStateOf(false) }
    var showingForgetConfirmation by remember { mutableStateOf(false) }
    var isSearchActive by rememberSaveable { mutableStateOf(false) }
    var collapsedFolderIDs by remember { mutableStateOf(setOf<String>()) }

    LaunchedEffect(server) {
        viewModel.configure(server)
        viewModel.refresh()
    }

    fun exitSelection() {
        isSelecting = false
        selectedIDs = emptySet()
    }

    // True once every currently-displayed, non-trashed song (same set
    // "Select All" fills in) is selected — drives the Select All ↔
    // Deselect All label, same as the iOS side's `isAllSelected`.
    val isAllSelected = visibleSongs.isNotEmpty() && visibleSongs.all { it.id in selectedIDs }

    fun toggleSelectAll() {
        selectedIDs = if (isAllSelected) emptySet() else visibleSongs.map { it.id }.toSet()
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("StemDeck") },
                    navigationIcon = {
                        if (isSelecting) {
                            TextButton(onClick = { toggleSelectAll() }) {
                                Text(if (isAllSelected) "Deselect All" else "Select All")
                            }
                        } else {
                            Box {
                                IconButton(onClick = { isShowingSortMenu = true }) {
                                    Icon(Icons.Filled.SwapVert, contentDescription = "Sort")
                                }
                                DropdownMenu(expanded = isShowingSortMenu, onDismissRequest = { isShowingSortMenu = false }) {
                                    SongSortOption.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = { viewModel.setSortOption(option); isShowingSortMenu = false },
                                            leadingIcon = if (option == sortOption) { { Icon(Icons.Filled.CheckCircle, contentDescription = null) } } else null,
                                        )
                                    }
                                }
                            }
                        }
                    },
                    actions = {
                        if (isSelecting) {
                            TextButton(onClick = { exitSelection() }) { Text("Cancel") }
                        } else {
                            Box {
                                IconButton(onClick = { isShowingOverflowMenu = true }) { Icon(Icons.Filled.Folder, contentDescription = "Folders") }
                                DropdownMenu(expanded = isShowingOverflowMenu, onDismissRequest = { isShowingOverflowMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Select All") },
                                        onClick = { isSelecting = true; toggleSelectAll(); isShowingOverflowMenu = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Manage Folders") },
                                        onClick = { isShowingManageFolders = true; isShowingOverflowMenu = false },
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { isShowingSettingsMenu = true }) { Icon(Icons.Filled.Settings, contentDescription = "Settings") }
                                DropdownMenu(expanded = isShowingSettingsMenu, onDismissRequest = { isShowingSettingsMenu = false }) {
                                    DropdownMenuItem(text = { Text(server.displayAddress) }, onClick = {}, enabled = false)
                                    DropdownMenuItem(
                                        text = { Text("Disconnect", color = MaterialTheme.colorScheme.error) },
                                        onClick = { showingForgetConfirmation = true; isShowingSettingsMenu = false },
                                    )
                                }
                            }
                        }
                    },
                )
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { viewModel.setSearchText(it) },
                    placeholder = { Text("Search songs") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        },
        bottomBar = {
            if (isSelecting) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${selectedIDs.size} selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { isShowingMoveDialog = true }, enabled = selectedIDs.isNotEmpty()) { Text("Move to Folder…") }
                }
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val s = state) {
                is LibraryLoadState.Idle -> LoadingState()
                is LibraryLoadState.Loading -> if (songs.isEmpty()) LoadingState() else SongListContent(
                    songs = songs, visibleSongs = visibleSongs, trashedJobs = trashedJobs, hasEverTrashed = trashedEntries.isNotEmpty(), isOffline = isOffline, folders = folders, assignments = assignments,
                    collapsedFolderIDs = collapsedFolderIDs, onToggleFolder = { id -> collapsedFolderIDs = if (id in collapsedFolderIDs) collapsedFolderIDs - id else collapsedFolderIDs + id },
                    isSelecting = isSelecting, selectedIDs = selectedIDs,
                    onToggleSelected = { id -> selectedIDs = if (id in selectedIDs) selectedIDs - id else selectedIDs + id },
                    onOpenSong = { job -> coordinator.play(job, server) },
                    onTrashSong = { job -> selectedIDs = selectedIDs - job.id; viewModel.trashSong(job) },
                    onMoveSong = { job -> selectedIDs = setOf(job.id); isShowingMoveDialog = true },
                    onLongPressSong = { job -> isSelecting = true; selectedIDs = selectedIDs + job.id },
                    onRestoreSong = { id -> viewModel.restoreSong(id) },
                    searchText = searchText,
                    downloadStatuses = downloadStatuses,
                    isRefreshing = true,
                    onRefresh = { viewModel.refresh() },
                )
                is LibraryLoadState.Unreachable -> StatusView(
                    icon = Icons.Filled.WifiOff,
                    title = "Can't Reach StemDeck",
                    message = "Check that StemDeck is running on ${server.displayAddress} and that your phone is on the same Wi-Fi network.",
                    onRetry = { viewModel.refresh() },
                )
                is LibraryLoadState.CertificateChanged -> StatusView(
                    icon = Icons.Filled.WifiOff,
                    title = "Certificate Changed",
                    message = "StemDeck's certificate no longer matches what this app trusted. Disconnect and pair again.",
                    onRetry = { viewModel.refresh() },
                )
                is LibraryLoadState.Refused -> StatusView(
                    icon = Icons.Filled.WifiOff,
                    title = "StemDeck Refused This Connection",
                    message = s.detail,
                    onRetry = { viewModel.refresh() },
                )
                is LibraryLoadState.Loaded -> SongListContent(
                    songs = songs, visibleSongs = visibleSongs, trashedJobs = trashedJobs, hasEverTrashed = trashedEntries.isNotEmpty(), isOffline = isOffline, folders = folders, assignments = assignments,
                    collapsedFolderIDs = collapsedFolderIDs, onToggleFolder = { id -> collapsedFolderIDs = if (id in collapsedFolderIDs) collapsedFolderIDs - id else collapsedFolderIDs + id },
                    isSelecting = isSelecting, selectedIDs = selectedIDs,
                    onToggleSelected = { id -> selectedIDs = if (id in selectedIDs) selectedIDs - id else selectedIDs + id },
                    onOpenSong = { job -> coordinator.play(job, server) },
                    onTrashSong = { job -> selectedIDs = selectedIDs - job.id; viewModel.trashSong(job) },
                    onMoveSong = { job -> selectedIDs = setOf(job.id); isShowingMoveDialog = true },
                    onLongPressSong = { job -> isSelecting = true; selectedIDs = selectedIDs + job.id },
                    onRestoreSong = { id -> viewModel.restoreSong(id) },
                    searchText = searchText,
                    downloadStatuses = downloadStatuses,
                    onRefresh = { viewModel.refresh() },
                )
            }
        }
    }

    if (isShowingManageFolders) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { isShowingManageFolders = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ManageFoldersScreen(folderStore = viewModel.folderStore, onDismiss = { isShowingManageFolders = false })
        }
    }

    if (isShowingMoveDialog) {
        AlertDialog(
            onDismissRequest = { isShowingMoveDialog = false; if (!isSelecting) selectedIDs = emptySet() },
            title = { Text("Move ${selectedIDs.size} Song${if (selectedIDs.size == 1) "" else "s"}") },
            text = {
                Column {
                    folders.forEach { folder ->
                        TextButton(onClick = {
                            viewModel.folderStore.assign(selectedIDs, folder.id)
                            isShowingMoveDialog = false
                            exitSelection()
                        }) { Text(folder.name) }
                    }
                    TextButton(onClick = {
                        viewModel.folderStore.assign(selectedIDs, null)
                        isShowingMoveDialog = false
                        exitSelection()
                    }) { Text("Unassigned") }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = {
                    isShowingMoveDialog = false
                    // A swipe-to-move never entered real multi-select, so
                    // the single ad-hoc ID it staged in `selectedIDs`
                    // shouldn't linger into the next real selection.
                    if (!isSelecting) selectedIDs = emptySet()
                }) { Text("Cancel") }
            },
        )
    }

    if (showingForgetConfirmation) {
        AlertDialog(
            onDismissRequest = { showingForgetConfirmation = false },
            title = { Text("Disconnect from ${server.displayAddress}?") },
            confirmButton = {
                TextButton(onClick = { showingForgetConfirmation = false; viewModel.forgetServer() }) {
                    Text("Disconnect", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { showingForgetConfirmation = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Text("Loading library…", modifier = Modifier.padding(top = 12.dp))
        }
    }
}

@Composable
private fun StatusView(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
            Button(onClick = onRetry, modifier = Modifier.padding(top = 12.dp)) { Text("Retry") }
        }
    }
}

private data class FolderSection(val id: String?, val name: String, val songs: List<Job>, val isTrash: Boolean = false)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SongListContent(
    songs: List<Job>,
    visibleSongs: List<Job>,
    trashedJobs: List<Job>,
    hasEverTrashed: Boolean,
    isOffline: Boolean,
    folders: List<LibraryFolder>,
    assignments: Map<String, String>,
    collapsedFolderIDs: Set<String>,
    onToggleFolder: (String) -> Unit,
    isSelecting: Boolean,
    selectedIDs: Set<String>,
    onToggleSelected: (String) -> Unit,
    onOpenSong: (Job) -> Unit,
    onTrashSong: (Job) -> Unit,
    onMoveSong: (Job) -> Unit,
    onLongPressSong: (Job) -> Unit,
    onRestoreSong: (String) -> Unit,
    searchText: String,
    downloadStatuses: Map<String, StemDownloadQueue.Status>,
    isRefreshing: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    if (songs.isEmpty()) {
        StatusView(icon = Icons.Filled.MusicNote, title = "No Songs Yet", message = "Separate a track in StemDeck on your computer, then pull to refresh here.", onRetry = { onRefresh?.invoke() })
        return
    }
    if (visibleSongs.isEmpty() && trashedJobs.isEmpty()) {
        StatusView(icon = Icons.Filled.Search, title = "No Matches", message = "No songs match \"$searchText\".", onRetry = { onRefresh?.invoke() })
        return
    }

    // Sectioned rendering (folder headers, an "Unassigned" bucket, and
    // Trash) kicks in once there's a real folder or anything's ever been
    // trashed; otherwise it's the plain flat list everyone starts with.
    // Gated on `hasEverTrashed` (not the search-filtered `trashedJobs`) so
    // typing in the search field never flips this mode on/off by itself.
    // Matches the iOS side's `songList`/`groupedSections` split exactly —
    // including the iOS side's reason for keeping this stable: `List` +
    // `ForEach` + `DisclosureGroup` crashes there when a section's own
    // existence changes in the same update as a sibling section's row
    // count. Compose's `LazyColumn` doesn't have that failure mode, but
    // this keeps both apps' behavior identical rather than diverging.
    val showSectioned = folders.isNotEmpty() || hasEverTrashed

    // Every real folder + Unassigned + Trash is always present once
    // sectioned rendering is showing at all — never appearing/disappearing
    // based on whether it currently has anything in it — for the same
    // reason as the iOS side's `groupedSections`.
    val sections: List<FolderSection> = remember(visibleSongs, folders, assignments, trashedJobs) {
        val validIDs = folders.map { it.id }.toSet()
        val bucket = mutableMapOf<String, MutableList<Job>>()
        val unassigned = mutableListOf<Job>()
        for (job in visibleSongs) {
            val folderID = assignments[job.id]
            if (folderID != null && folderID in validIDs) bucket.getOrPut(folderID) { mutableListOf() }.add(job) else unassigned.add(job)
        }
        val result = folders.map { folder -> FolderSection(folder.id, folder.name, bucket[folder.id] ?: emptyList()) }.toMutableList()
        result.add(FolderSection("__unassigned__", "Unassigned", unassigned))
        result.add(FolderSection("__trash__", "Trash", trashedJobs, isTrash = true))
        result
    }

    val content: @Composable () -> Unit = {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (isOffline) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.WifiOff, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.size(4.dp))
                        Text("Offline — showing your downloaded library", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (!showSectioned) {
                items(visibleSongs, key = { it.id }) { job ->
                    SongRow(
                        job, isSelecting, job.id in selectedIDs, downloadStatuses[job.id] ?: StemDownloadQueue.Status.NotQueued,
                        onClick = { if (isSelecting) onToggleSelected(job.id) else onOpenSong(job) },
                        onLongClick = { onLongPressSong(job) },
                        onDelete = { onTrashSong(job) },
                        onMove = { onMoveSong(job) },
                    )
                }
            } else {
                sections.forEach { section ->
                    val sectionKey = section.id ?: "root"
                    item(key = "header-$sectionKey") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { section.id?.let(onToggleFolder) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                if (section.isTrash) Icons.Filled.Delete else Icons.Filled.Folder,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(section.name, modifier = Modifier.padding(start = 8.dp).weight(1f))
                            Text("${section.songs.size}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (section.id !in collapsedFolderIDs) {
                        items(section.songs, key = { "song-${sectionKey}-${it.id}" }) { job ->
                            if (section.isTrash) {
                                TrashRow(job, onClick = { onOpenSong(job) }, onRestore = { onRestoreSong(job.id) })
                            } else {
                                SongRow(
                                    job, isSelecting, job.id in selectedIDs, downloadStatuses[job.id] ?: StemDownloadQueue.Status.NotQueued,
                                    onClick = { if (isSelecting) onToggleSelected(job.id) else onOpenSong(job) },
                                    onLongClick = { onLongPressSong(job) },
                                    onDelete = { onTrashSong(job) },
                                    onMove = { onMoveSong(job) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (onRefresh != null) {
        val pullState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, state = pullState, modifier = Modifier.fillMaxSize()) {
            content()
        }
    } else {
        content()
    }
}

/** Thumbnail + title/subtitle/download-status + duration — the visual content shared by both a normal row and a Trash row. */
@Composable
private fun SongRowContent(job: Job, isSelecting: Boolean, isSelected: Boolean, status: StemDownloadQueue.Status, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (isSelecting) {
            Icon(
                if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 12.dp),
            )
        }

        Box(
            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (job.thumbnail != null) {
                AsyncImage(model = job.thumbnail, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(44.dp).clip(RoundedCornerShape(6.dp)))
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(job.title ?: "Untitled", maxLines = 1)
            job.subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            when (status) {
                is StemDownloadQueue.Status.Queued -> Text("Queued to download", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                is StemDownloadQueue.Status.Downloading -> Column {
                    Text("Downloading stems… ${Math.round(status.progress * 100)}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { status.progress.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 2.dp))
                }
                is StemDownloadQueue.Status.Failed -> Text("Download failed — tap to retry", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }

        job.formattedDuration?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/**
 * A normal library row: swipe from the end (left, in LTR) to trash it —
 * mobile-only, never touches StemDeck (see `LibraryViewModel.trashSong`) —
 * or swipe from the start (right) to move it to a folder without entering
 * multi-select. Long-pressing enters multi-select with this song already
 * checked, same as the swipe-to-move entry point but for picking several
 * songs at once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SongRow(
    job: Job,
    isSelecting: Boolean,
    isSelected: Boolean,
    status: StemDownloadQueue.Status,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onMove: () -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> { onDelete(); true }
                // Moving doesn't remove the row, so snap back rather than dismiss.
                SwipeToDismissBoxValue.StartToEnd -> { onMove(); false }
                else -> false
            }
        },
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = !isSelecting,
        enableDismissFromEndToStart = !isSelecting,
        backgroundContent = {
            when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Box(
                    modifier = Modifier.fillMaxSize().background(ConsoleTheme.accent).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = "Move to Folder", tint = Color.Black.copy(alpha = 0.85f))
                }
                else -> Box(
                    modifier = Modifier.fillMaxSize().background(ConsoleTheme.markerOut).padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Black.copy(alpha = 0.85f))
                }
            }
        },
    ) {
        SongRowContent(
            job, isSelecting, isSelected, status,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        )
    }
}

/** A row inside the Trash section: same visual as a normal row, but tapping always plays (no selection checkbox — trashed songs aren't part of the move-to-folder flow) and its action is Restore rather than swipe-to-delete. */
@Composable
private fun TrashRow(job: Job, onClick: () -> Unit, onRestore: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SongRowContent(job, isSelecting = false, isSelected = false, status = StemDownloadQueue.Status.NotQueued, modifier = Modifier.weight(1f))
        TextButton(onClick = onRestore) {
            Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(4.dp))
            Text("Restore")
        }
    }
}

