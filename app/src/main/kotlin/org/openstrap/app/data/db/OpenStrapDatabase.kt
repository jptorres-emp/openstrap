package org.openstrap.app.data.db

import android.content.Context
import androidx.room.AutoMigration
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
        SkinTemperature::class,
        BreathRate::class,
        PaiSample::class,
        HrvSdnnSample::class,
        Workout::class,
        WorkoutHeartRate::class,
        SportEffectRow::class,
        StrapReadinessRow::class,
        SleepSpo2Sample::class,
        OsaEventRow::class,
        OsaResultRow::class,
        DailyMaxHr::class,
        RecoveryHeartRateRow::class,
        ActivityExtendedMinute::class,
        RawRecord::class,
        HealthAlertRow::class,
        CheckInRow::class,
        DayTagRow::class,
        NightMetricsRow::class,
        AppWorkout::class,
        AppWorkoutHeartRate::class,
        DismissedWorkout::class,
        AppWorkoutRoutePoint::class,
        AppWorkoutSet::class,
    ],
    version = 11,
    autoMigrations = [
        AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7), AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9), AutoMigration(from = 9, to = 10),
        AutoMigration(from = 10, to = 11),
    ],
)
abstract class OpenStrapDatabase : RoomDatabase() {
    abstract fun healthDao(): HealthDao

    companion object {
        fun create(context: Context): OpenStrapDatabase =
            Room.databaseBuilder(context, OpenStrapDatabase::class.java, "openstrap.db").build()
    }
}
