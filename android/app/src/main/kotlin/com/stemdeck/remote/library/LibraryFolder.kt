package com.stemdeck.remote.library

import java.util.UUID
import kotlinx.serialization.Serializable

/**
 * A user-created grouping in the song list (see [LibraryFolderStore]) —
 * purely a client-side organizational layer. StemDeck's own library has no
 * concept of folders, so this never round-trips to the server.
 */
@Serializable
data class LibraryFolder(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
)
