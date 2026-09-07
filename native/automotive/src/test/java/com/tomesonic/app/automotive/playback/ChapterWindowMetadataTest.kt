package com.tomesonic.app.automotive.playback

import android.app.Application
import android.net.Uri
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * What one synthetic chapter window presents to controllers.
 *
 * The legacy MediaSession bridge (Android Auto's now-playing lines, the
 * Automotive Media Center, a watch's remote controls) reads a window through
 * its MediaDescription: once a display title is set, the second and third
 * lines come ONLY from `subtitle`/`description` — the artist/album fallback a
 * plain item enjoys is skipped. A window that carried a display title without
 * the display pair rendered its chapter over an EMPTY second line, which is
 * the regression these pin down.
 *
 * Robolectric only for android.net.Uri and Bundle, which MediaMetadata is
 * built on; no Context is touched.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@androidx.annotation.OptIn(UnstableApi::class)
class ChapterWindowMetadataTest {

    private val cover = Uri.parse("https://abs.local/api/items/li_1/cover")
    private val bytes = byteArrayOf(1, 2, 3)

    // The flat item as SessionManager builds it: book title, author, album = book.
    private val base = MediaMetadata.Builder()
        .setTitle("Dune")
        .setArtist("Frank Herbert")
        .setAlbumTitle("Dune")
        .setArtworkUri(cover)
        .setArtworkData(bytes, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
        .build()

    @Test
    fun windowNamesTheChapterAsTitleAndDisplayTitle() {
        val meta = chapterWindowMetadata(base, "Chapter 3", index = 2, count = 10, isCurrent = true)
        assertEquals("Chapter 3", meta.title.toString())
        assertEquals("Chapter 3", meta.displayTitle.toString())
        assertEquals(3, meta.trackNumber)
        assertEquals(10, meta.totalTrackCount)
    }

    @Test
    fun displaySubtitleAndDescriptionMirrorTheArtistAlbumFallback() {
        val meta = chapterWindowMetadata(base, "Chapter 3", 2, 10, isCurrent = true)
        assertEquals("Frank Herbert", meta.subtitle.toString())
        assertEquals("Dune", meta.description.toString())
        // The artist/album keys themselves survive for AVRCP and media3 readers.
        assertEquals("Frank Herbert", meta.artist.toString())
        assertEquals("Dune", meta.albumTitle.toString())
    }

    @Test
    fun anExplicitSubtitleOrDescriptionOnTheFlatItemWins() {
        val explicit = base.buildUpon()
            .setSubtitle("Dune • Frank Herbert")
            .setDescription("Read by Scott Brick")
            .build()
        val meta = chapterWindowMetadata(explicit, "Chapter 1", 0, 10, isCurrent = true)
        assertEquals("Dune • Frank Herbert", meta.subtitle.toString())
        assertEquals("Read by Scott Brick", meta.description.toString())
    }

    @Test
    fun aFlatItemWithoutAnAlbumLeavesTheDescriptionUnset() {
        // The phone's flat item carries title + artist ("Book • Author") only —
        // the second line is the whole story there, nothing feeds a third.
        val phone = MediaMetadata.Builder().setTitle("Dune").setArtist("Dune • Frank Herbert").build()
        val meta = chapterWindowMetadata(phone, "Chapter 1", 0, 3, isCurrent = true)
        assertEquals("Dune • Frank Herbert", meta.subtitle.toString())
        assertNull(meta.description)
    }

    @Test
    fun inlineArtworkBytesRideOnlyTheCurrentWindow() {
        val current = chapterWindowMetadata(base, "Chapter 1", 0, 2, isCurrent = true)
        val other = chapterWindowMetadata(base, "Chapter 2", 1, 2, isCurrent = false)
        assertArrayEquals(bytes, current.artworkData)
        assertNull(other.artworkData)
        // The URI survives the strip, so a URI-capable reader still gets the cover.
        assertEquals(cover, other.artworkUri)
    }
}
