package org.openstrap.app.data.db

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/** A version 19 database opens at 20 with its data intact and an empty weight table, one weight a day. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WeightMigrationTest {
    @Test
    fun weightsAfterMigration() = runTest {
        val file = File(System.getProperty("java.io.tmpdir"), "openstrap-migration-${System.nanoTime()}.db")
        // Built from the exported schema, so it's the database version 19 really made.
        val schema = JSONObject(File("schemas/org.openstrap.app.data.db.OpenStrapDatabase/19.json").readText()).getJSONObject("database")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val e = entities.getJSONObject(i)
                val table = e.getString("tableName")
                db.execSQL(e.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = e.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL("INSERT INTO check_in (day, feeling, updatedAt) VALUES (1000, 4, 1000)")
            db.version = 19
        }

        val room = Room.databaseBuilder(ApplicationProvider.getApplicationContext(), OpenStrapDatabase::class.java, file.absolutePath)
            .allowMainThreadQueries().build()
        try {
            val dao = room.healthDao()
            assertEquals(listOf(4), dao.checkInsOnce(0, 2000).map { it.feeling })
            assertEquals(emptyList<WeightEntry>(), dao.weightsOnce(0, Long.MAX_VALUE))
            dao.insertWeights(listOf(WeightEntry(86_400, 80.4f, 1), WeightEntry(2 * 86_400, 80.1f, 2)))
            // The same day again replaces it.
            dao.insertWeights(listOf(WeightEntry(2 * 86_400, 79.9f, 3)))
            assertEquals(listOf(80.4f, 79.9f), dao.weights(0, Long.MAX_VALUE).first().map { it.kg })
            assertEquals(3L, dao.latestWeightUpdate().first())
            dao.deleteWeight(86_400)
            assertEquals(listOf(2 * 86_400L), dao.weightsOnce(0, Long.MAX_VALUE).map { it.day })
        } finally {
            room.close()
            listOf("", "-wal", "-shm", "-journal").forEach { File(file.path + it).delete() }
        }
    }
}
