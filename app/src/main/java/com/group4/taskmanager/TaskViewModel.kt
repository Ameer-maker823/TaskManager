package com.group4.taskmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.group4.taskmanager.data.AppDatabase
import com.group4.taskmanager.data.Category
import com.group4.taskmanager.data.Subtask
import com.group4.taskmanager.data.Task
import com.group4.taskmanager.data.TaskWithDetails
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Filters(
    val tab: Int = TAB_TODAY,
    val categoryId: Int = ANY,
    val status: Int = ANY,
    val priority: Int = ANY,
    val sort: Int = SORT_DUE,
    val query: String = ""
) {
    companion object {
        const val ANY = -1
        const val TAB_TODAY = 0
        const val TAB_UPCOMING = 1
        const val SORT_DUE = 0
        const val SORT_PRIORITY = 1
        const val SORT_NEWEST = 2
    }
}

class TaskViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.getInstance(app)
    private val dao = db.taskDao()
    private val categoryDao = db.categoryDao()

    private val _filters = MutableStateFlow(Filters())
    val filters: StateFlow<Filters> = _filters

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<List<TaskWithDetails>?> = _filters
        .flatMapLatest { f ->
            dao.observeTasks(
                f.query.trim(), f.status, f.priority, f.categoryId,
                f.tab, Dates.endOfToday(), f.sort
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val categories: StateFlow<List<Category>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** done to total, across all tasks. */
    val progress: StateFlow<Pair<Int, Int>> =
        combine(dao.countAll(), dao.countDone()) { total, done -> done to total }
            .stateIn(viewModelScope, SharingStarted.Eagerly, 0 to 0)

    fun setTab(tab: Int) = _filters.update { it.copy(tab = tab) }

    fun setCategory(id: Int) = _filters.update { it.copy(categoryId = id) }

    fun cycleStatus(item: TaskWithDetails) {
        val task = item.task
        val next = (task.status + 1) % 3
        viewModelScope.launch {
            dao.setStatus(task.id, next)
            if (next == Task.STATUS_DONE) {
                dao.setAllSubtasksDone(task.id, true)
            } else if (next == Task.STATUS_TODO && task.status == Task.STATUS_DONE) {
                dao.setAllSubtasksDone(task.id, false)
            }
        }
    }

    fun toggleSubtask(subtask: Subtask) {
        viewModelScope.launch {
            dao.setSubtaskDone(subtask.id, !subtask.done)
            val item = dao.getTask(subtask.taskId) ?: return@launch
            val total = item.subtasks.size
            val done = item.subtasks.count { it.done }
            val status = when {
                done == total -> Task.STATUS_DONE
                done > 0 -> Task.STATUS_PROGRESS
                else -> Task.STATUS_TODO
            }
            if (status != item.task.status) dao.setStatus(item.task.id, status)
        }
    }

    // Temporary: lets us test the list before the editor exists.
    fun addSample() {
        viewModelScope.launch {
            val titles = listOf(
                "Plan the week", "Write lab report", "Call the dentist",
                "Water the plants", "Review notes", "Pay the bills"
            )
            val steps = listOf("Gather materials", "Do the first part", "Check and finish")
            dao.saveTask(
                Task(
                    title = titles.random(),
                    dueAt = Dates.daysFromToday((-1..3).random()),
                    priority = (0..2).random(),
                    categoryId = categories.value.randomOrNull()?.id
                ),
                steps.shuffled().take((0..3).random()).map { Subtask(taskId = 0, title = it) }
            )
        }
    }
}