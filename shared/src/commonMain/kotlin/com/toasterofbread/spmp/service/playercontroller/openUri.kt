package com.toasterofbread.spmp.service.playercontroller

import com.toasterofbread.composekit.utils.common.indexOfOrNull
import com.toasterofbread.spmp.model.mediaitem.artist.Artist
import com.toasterofbread.spmp.model.mediaitem.artist.ArtistRef
import com.toasterofbread.spmp.model.mediaitem.playlist.Playlist
import com.toasterofbread.spmp.model.mediaitem.playlist.RemotePlaylistRef
import com.toasterofbread.spmp.model.mediaitem.song.Song
import com.toasterofbread.spmp.model.mediaitem.song.SongRef
import com.toasterofbread.spmp.model.mediaitem.MediaItem
import java.net.URI
import java.net.URISyntaxException

suspend fun PlayerState.openUri(uri_string: String): Result<Unit> {
    fun failure(reason: String): Result<Unit> = Result.failure(URISyntaxException(uri_string, reason))

    val uri: URI = URI(uri_string)
    if (uri.host != "music.youtube.com" && uri.host != "www.youtube.com") {
        return failure("Unsupported host '${uri.host}'")
    }

    val item: MediaItem

    val path_parts: List<String> = uri.path.split('/').filter { it.isNotBlank() }
    when (path_parts.firstOrNull()) {
        "channel" -> {
            val channel_id: String = path_parts.elementAtOrNull(1) ?: return failure("No channel ID")
            item = ArtistRef(channel_id)
        }
        "watch" -> {
            val v_start: Int = (uri.query.indexOfOrNull("v=") ?: return failure("'v' query parameter not found")) + 2
            val v_end: Int = uri.query.indexOfOrNull("&", v_start) ?: uri.query.length
            item = SongRef(uri.query.substring(v_start, v_end))
        }
        "playlist" -> {
            val list_start: Int = (uri.query.indexOfOrNull("list=") ?: return failure("'list' query parameter not found")) + 5
            val list_end: Int = uri.query.indexOfOrNull("&", list_start) ?: uri.query.length
            item = RemotePlaylistRef(uri.query.substring(list_start, list_end))
        }
        else -> return failure("Uri path not implemented")
    }

    item.loadData(context, populate_data = false, force = true)

    withPlayer {
        openMediaItem(item)
    }

    return Result.success(Unit)
}
