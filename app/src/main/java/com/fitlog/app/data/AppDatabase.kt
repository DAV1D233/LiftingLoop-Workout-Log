package com.fitlog.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [FoodRecord::class, WorkoutPlan::class, PlanExercise::class, WorkoutRecord::class, SleepRecord::class],
    version = 11, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun food(): FoodDao
    abstract fun workouts(): WorkoutDao
    abstract fun sleep(): SleepDao

    companion object {
        val FROM_3 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE food_records ADD COLUMN proteinGrams REAL")
                db.execSQL("ALTER TABLE food_records ADD COLUMN carbsGrams REAL")
                db.execSQL("ALTER TABLE food_records ADD COLUMN fatGrams REAL")
            }
        }
        val FROM_4 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_records ADD COLUMN sessionKey TEXT")
                db.execSQL("CREATE UNIQUE INDEX index_workout_records_sessionKey ON workout_records(sessionKey)")
            }
        }
        @Volatile private var instance: AppDatabase? = null
        val FROM_5 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_exercises ADD COLUMN isBodyweight INTEGER NOT NULL DEFAULT 0")
            }
        }
        val FROM_6 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_exercises ADD COLUMN muscleGroup TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE plan_exercises ADD COLUMN iconPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE plan_exercises ADD COLUMN videoUrl TEXT NOT NULL DEFAULT ''")
            }
        }
        val FROM_7 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_exercises ADD COLUMN isCardio INTEGER NOT NULL DEFAULT 0")
            }
        }
        val FROM_8 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_exercises ADD COLUMN setTargetsJson TEXT NOT NULL DEFAULT '[]'")
            }
        }
        val FROM_9 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE workout_records ADD COLUMN volumeKg REAL")
            }
        }
        val FROM_10 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE plan_exercises ADD COLUMN cardioJson TEXT NOT NULL DEFAULT '{}'")
                db.execSQL("ALTER TABLE workout_records ADD COLUMN cardioSeconds INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_records ADD COLUMN cardioDistanceMeters REAL NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE workout_records ADD COLUMN cardioRounds INTEGER NOT NULL DEFAULT 0")
            }
        }
        fun open(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "fitlog.db")
                .addMigrations(FROM_3, FROM_4, FROM_5, FROM_6, FROM_7, FROM_8, FROM_9, FROM_10).build().also { instance = it }
        }
    }
}
