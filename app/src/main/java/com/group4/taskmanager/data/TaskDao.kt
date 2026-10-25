package com.group4.taskmanager.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class TaskDao {

    @Insert
    abstract suspend fun insertTask(task: Task): Long

    @Update
    abstract suspend fun updateTask(task: Task)

    @Delete
    abstract suspend fun deleteTasks(tasks: List<Task>)

    @Insert
    abstract suspend fun insertSubtasks(subtasks: List<Subtask>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreTasks(tasks: List<Task>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun restoreSubtasks(subtasks: List<Subtask>)

    @Query("DELETE FROM subtasks WHERE taskId = :taskId")
    abstract suspend fun deleteSubtasksOf(taskId: Int)

    @Query("UPDATE tasks SET status = :status WHERE id = :id")
    abstract suspend fun setStatus(id: Int, status: Int)

    @Query("UPDATE subtasks SET done = :done WHERE id = :id")
    abstract suspend fun setSubtaskDone(id: Int, done: Boolean)

    @Query("UPDATE subtasks SET done = :done WHERE taskId = :taskId")
    abstract suspend fun setAllSubtasksDone(taskId: Int, done: Boolean)

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    abstract suspend fun getTask(id: Int): TaskWithDetails?

    @Query("SELECT COUNT(*) FROM tasks")
    abstract fun countAll(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 2")
    abstract fun countDone(): Flow<Int>

    /**
     * One query for the whole list screen.
     * tab: 0 = Today (due today or overdue), 1 = Upcoming (later or no date), 2 = everything.
     * status, priority, categoryId: -1 means "any".
     * sort: 0 = due date, 1 = priority, 2 = newest first. Done tasks always go last.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM tasks
        WHERE (:query = '' OR title LIKE '%' || :query || '%' OR notes LIKE '%' || :query || '%')
          AND (:status = -1 OR status = :status)
          AND (:priority = -1 OR priority = :priority)
          AND (:categoryId = -1 OR categoryId = :categoryId)
          AND (
                :tab = 2
                OR (:tab = 0 AND dueAt IS NOT NULL AND dueAt < :endOfToday)
                OR (:tab = 1 AND (dueAt IS NULL OR dueAt >= :endOfToday))
              )
        ORDER BY
          (status = 2) ASC,
          CASE WHEN :sort = 0 THEN COALESCE(dueAt, 9223372036854775807) END ASC,
          CASE WHEN :sort = 1 THEN priority END DESC,
          CASE WHEN :sort = 2 THEN createdAt END DESC,
          createdAt DESC
        """
    )
    abstract fun observeTasks(
        query: String,
        status: Int,
        priority: Int,
        categoryId: Int,
        tab: Int,
        endOfToday: Long,
        sort: Int
    ): Flow<List<TaskWithDetails>>

    @Transaction
    open suspend fun saveTask(task: Task, subtasks: List<Subtask>): Int {
        val id = if (task.id == 0) {
            insertTask(task).toInt()
        } else {
            updateTask(task)
            task.id
        }
        deleteSubtasksOf(id)
        insertSubtasks(
            subtasks.mapIndexed { index, s -> s.copy(id = 0, taskId = id, position = index) }
        )
        return id
    }

    @Transaction
    open suspend fun restore(items: List<TaskWithDetails>) {
        restoreTasks(items.map { it.task })
        restoreSubtasks(items.flatMap { it.subtasks })
    }
}