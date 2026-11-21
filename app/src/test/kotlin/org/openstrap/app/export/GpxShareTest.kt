package org.openstrap.app.export

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** The GPX file a workout's Export hands to the share sheet: its name, and the cached copy behind the FileProvider. */
@RunWith(RobolectricTestRunner::class)
class GpxShareTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    // FileProvider keeps its roots per authority for the process, and Robolectric gives each test its own cache dir.
    @Before fun forgetProviderRoots() {
        (FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }.get(null) as MutableMap<*, *>).clear()
    }

    private fun read(uri: android.net.Uri) = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }

    @Test fun `fileName names the file after the workout`() {
        assertEquals("unstrap_1792000000.gpx", GpxShare.fileName(1_792_000_000))
    }

    @Test fun `forSharing writes the text and returns a content uri`() {
        val uri = GpxShare.forSharing(context, "run.gpx", "<gpx/>")
        assertNotNull(uri)
        assertEquals(context.packageName + ".share", uri!!.authority)
        assertEquals("<gpx/>", read(uri))
    }

    @Test fun `a second export replaces the first`() {
        GpxShare.forSharing(context, "run.gpx", "<gpx>first and longer</gpx>")
        val uri = GpxShare.forSharing(context, "run.gpx", "<gpx>2</gpx>")
        assertEquals("<gpx>2</gpx>", read(uri!!))
    }

    @Test fun `an export of another workout removes the earlier file`() {
        GpxShare.forSharing(context, "a.gpx", "<gpx/>")
        GpxShare.forSharing(context, "b.gpx", "<gpx/>")
        assertEquals(listOf("b.gpx"), File(context.cacheDir, "share").list()!!.filter { it.endsWith(".gpx") })
    }
}
