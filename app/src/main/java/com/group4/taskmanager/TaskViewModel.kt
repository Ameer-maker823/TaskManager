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
    /** True when a status, priority or sort choice differs from the default. */
    val sheetActive: Boolean
        get() = status != ANY || priority != ANY || sort != SORT_DUE

    companion object {
        const val ANY = -1
        const val TAB_TODAY = 0
        const val TAB_UPCOMING = 1
        const val TAB_ALL = 2
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

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching

    @OptIn(ExperimentalCoroutinesApi::class)
    val tasks: StateFlow<List<TaskWithDetails>?> = _filters
        .flatMapLatest { f ->
            // While searching, look through every task, whichever tab is selected.
            val tab = if (f.query.isNotBlank()) Filters.TAB_ALL else f.tab
            dao.observeTasks(
                f.query.trim(), f.status, f.priority, f.categoryId,
                tab, Dates.endOfToday(), f.sort
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

    fun setStatusFilter(status: Int) = _filters.update { it.copy(status = status) }

    fun setPriorityFilter(priority: Int) = _filters.update { it.copy(priority = priority) }

    fun setSort(sort: Int) = _filters.update { it.copy(sort = sort) }

    fun resetSheetFilters() = _filters.update {
        it.copy(status = Filters.ANY, priority = Filters.ANY, sort = Filters.SORT_DUE)
    }

    fun setQuery(text: String) {
        if (text != _filters.value.query) _filters.update { it.copy(query = text) }
    }

    fun setSearching(open: Boolean) {
        _searching.value = open
        if (!open) _filters.update { it.copy(query = "") }
    }

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
}