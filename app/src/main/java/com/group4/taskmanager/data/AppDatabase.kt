package com.group4.taskmanager.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Task::class, Subtask::class, Category::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun taskDao(): TaskDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "tasks_database"
                ).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val defaults = listOf("School", "Home", "Work", "Personal")
                        defaults.forEachIndexed { index, name ->
                            db.execSQL(
                                "INSERT INTO categories (name, color) VALUES ('$name', ${CategoryColors.all[index]})"
                            )
                        }
                    }
                }).build()
                INSTANCE = instance
                instance
            }
        }
    }
}