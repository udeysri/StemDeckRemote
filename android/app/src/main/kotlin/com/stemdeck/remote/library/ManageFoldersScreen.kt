package com.stemdeck.remote.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.filled.Add

/**
 * Create and delete folders. The iOS side also supports drag-to-reorder via
 * `List`'s native edit mode; reordering isn't included here since Compose
 * has no equally lightweight built-in for it — folders can still be created,
 * renamed by deleting and recreating, and deleted, which covers the same
 * organizational need.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFoldersScreen(folderStore: LibraryFolderStore, onDismiss: () -> Unit) {
    val folders by folderStore.folders.collectAsStateWithLifecycle()
    var isShowingNewFolderPrompt by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Folders") },
                navigationIcon = { TextButton(onClick = onDismiss) { Text("Done") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { newFolderName = ""; isShowingNewFolderPrompt = true }) {
                Icon(Icons.Filled.Add, contentDescription = "New Folder")
            }
        },
    ) { padding ->
        if (folders.isEmpty()) {
            Text(
                "No folders yet. Create one to start organizing your songs.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(padding).padding(24.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.padding(padding).fillMaxSize()) {
                items(folders, key = { it.id }) { folder ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(folder.name, modifier = Modifier.padding(start = 12.dp))
                        }
                        IconButton(onClick = { folderStore.deleteFolders(setOf(folder.id)) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }

    if (isShowingNewFolderPrompt) {
        AlertDialog(
            onDismissRequest = { isShowingNewFolderPrompt = false },
            title = { Text("New Folder") },
            text = {
                OutlinedTextField(value = newFolderName, onValueChange = { newFolderName = it }, label = { Text("Folder Name") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    val trimmed = newFolderName.trim()
                    if (trimmed.isNotEmpty()) folderStore.createFolder(trimmed)
                    isShowingNewFolderPrompt = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { isShowingNewFolderPrompt = false }) { Text("Cancel") } },
        )
    }
}
