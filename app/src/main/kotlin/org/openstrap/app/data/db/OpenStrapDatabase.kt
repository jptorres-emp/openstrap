package org.openstrap.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        HeartRateSample::class,
        ActivityMinute::class,
        SleepSession::class,
        SleepStage::class,
        Spo2Sample::class,
        StressSample::class,
        HrvSample::class,
        RestingHeartRate::class,
    ],
    version = 1,
)
abstract class OpenStrapDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao

    companion object {
        fun create(context: Context): OpenStrapDatabase =
            Room.databaseBuilder(context, OpenStrapDatabase::class.java, "openstrap.db").build()
    }
}
