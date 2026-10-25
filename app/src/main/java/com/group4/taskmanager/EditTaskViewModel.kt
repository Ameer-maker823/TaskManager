package com.group4.taskmanager

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.group4.taskmanager.data.AppDatabase
import com.group4.taskmanager.data.Category
import com.group4.taskmanager.data.Subtask
import com.group4.taskmanager.data.Task
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class Draft(
    val loaded: Boolean = false,
    val missing: Boolean = false,
    val title: String = "",
    val notes: String = "",
    val dueAt: Long? = null,
    val categoryId: Int? = null,
    val priority: Int = Task.PRIORITY_MEDIUM,
    val status: Int = Task.STATUS_TODO,
    val steps: List<Subtask> = emptyList()
)

class EditTaskViewModel(app: Application, handle: SavedStateHandle) : AndroidViewModel(app) {

    val taskId: Int = handle.get<Int>(EditTaskActivity.EXTRA_TASK_ID) ?: -1

    private val db = AppDatabase.getInstance(app)
    private val dao = db.taskDao()
    private val categoryDao = db.categoryDao()

    private val _draft = MutableStateFlow(Draft())
    val draft: StateFlow<Draft> = _draft

    val categories: StateFlow<List<Category>> = categoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** The title and notes boxes are filled once; after that Android restores them. */
    var textsFilled = false

    private var original: Task? = null

    init {
        if (taskId == -1) {
            _draft.value = Draft(loaded = true, dueAt = Dates.startOfToday())
        } else {
            viewModelScope.launch {
                val item = dao.getTask(taskId)
                if (item == null) {
                    _draft.value = Draft(loaded = true, missing = true)
                } else {
                    original = item.task
                    _draft.value = Draft(
                        loaded = true,
                        title = item.task.title,
                        notes = item.task.notes,
                        dueAt = item.task.dueAt,
                        categoryId = item.task.categoryId,
                        priority = item.task.priority,
                        status = item.task.status,
                        steps = item.subtasks.sortedBy { it.position }
                    )
                }
            }
        }
    }

    fun setDue(dueAt: Long?) = _draft.update { it.copy(dueAt = dueAt) }

    fun setCategory(id: Int?) = _draft.update { it.copy(categoryId = id) }

    fun setPriority(priority: Int) = _draft.update { it.copy(priority = priority) }

    fun setStatus(status: Int) = _draft.update { d ->
        val steps = when {
            status == Task.STATUS_DONE -> d.steps.map { it.copy(done = true) }
            d.status == Task.STATUS_DONE && status == Task.STATUS_TODO ->
                d.steps.map { it.copy(done = false) }
            else -> d.steps
        }
        d.copy(status = status, steps = steps)
    }

    fun addStep(title: String) = _draft.update { d ->
        val status = if (d.status == Task.STATUS_DONE) {
            if (d.steps.isEmpty()) Task.STATUS_TODO else Task.STATUS_PROGRESS
        } else {
            d.status
        }
        d.copy(steps = d.steps + Subtask(taskId = 0, title = title), status = status)
    }

    fun toggleStep(index: Int) = _draft.update { d ->
        val steps = d.steps.mapIndexed { i, s -> if (i == index) s.copy(done = !s.done) else s }
        d.copy(steps = steps, status = statusFor(steps, d.status))
    }

    fun removeStep(index: Int) = _draft.update { d ->
        val steps = d.steps.filterIndexed { i, _ -> i != index }
        d.copy(steps = steps, status = statusFor(steps, d.status))
    }

    fun addCategory(name: String, color: Int) {
        viewModelScope.launch {
            val id = categoryDao.insert(Category(name = name, color = color)).toInt()
            _draft.update { it.copy(categoryId = id) }
        }
    }

    fun save(title: String, notes: String, onDone: () -> Unit) {
        val d = _draft.value
        val task = (original ?: Task(title = title)).copy(
            title = title.trim(),
            notes = notes.trim(),
            dueAt = d.dueAt,
            status = d.status,
            priority = d.priority,
            categoryId = d.categoryId
        )
        viewModelScope.launch {
            dao.saveTask(task, d.steps)
            onDone()
        }
    }

    fun delete(onDone: () -> Unit) {
        val task = original ?: return
        viewModelScope.launch {
            dao.deleteTasks(listOf(task))
            onDone()
        }
    }

    private fun statusFor(steps: List<Subtask>, current: Int): Int {
        if (steps.isEmpty()) return current
        val done = steps.count { it.done }
        return when {
            done == steps.size -> Task.STATUS_DONE
            done > 0 -> Task.STATUS_PROGRESS
            else -> Task.STATUS_TODO
        }
    }
}