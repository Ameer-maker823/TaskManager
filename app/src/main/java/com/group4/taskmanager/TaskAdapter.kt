package com.group4.taskmanager

import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import android.content.res.ColorStateList
import com.group4.taskmanager.data.Subtask
import com.group4.taskmanager.data.Task
import com.group4.taskmanager.data.TaskWithDetails
import com.group4.taskmanager.databinding.ItemSubtaskBinding
import com.group4.taskmanager.databinding.ItemTaskBinding

class TaskAdapter(
    private val onStatusClick: (TaskWithDetails) -> Unit = {},
    private val onSubtaskClick: (Subtask) -> Unit = {},
    private val onCardClick: (TaskWithDetails) -> Unit = {}
) : ListAdapter<TaskWithDetails, TaskAdapter.TaskViewHolder>(DiffCallback()) {

    private val expanded = mutableSetOf<Int>()

    class TaskViewHolder(val binding: ItemTaskBinding) : RecyclerView.ViewHolder(binding.root)

    fun taskAt(position: Int): TaskWithDetails = getItem(position)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemTaskBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        val item = getItem(position)
        val task = item.task
        val b = holder.binding
        val context = b.root.context
        val done = task.status == Task.STATUS_DONE

        b.tvTaskTitle.text = task.title
        b.tvTaskTitle.setTextColor(
            ContextCompat.getColor(context, if (done) R.color.text_muted else R.color.text_primary)
        )
        strike(b.tvTaskTitle, done)

        b.btnStatus.setBackgroundResource(
            when (task.status) {
                Task.STATUS_DONE -> R.drawable.status_done
                Task.STATUS_PROGRESS -> R.drawable.status_progress
                else -> R.drawable.status_todo
            }
        )
        if (done) b.btnStatus.setImageResource(R.drawable.ic_check) else b.btnStatus.setImageDrawable(null)
        b.btnStatus.setOnClickListener { view ->
            view.animate().scaleX(0.75f).scaleY(0.75f).setDuration(80).withEndAction {
                view.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
            }.start()
            onStatusClick(item)
        }

        val category = item.category
        if (category == null) {
            b.tvCategory.visibility = View.GONE
        } else {
            b.tvCategory.visibility = View.VISIBLE
            b.tvCategory.text = category.name
            b.tvCategory.setTextColor(category.color)
            b.tvCategory.background = GradientDrawable().apply {
                cornerRadius = 9 * context.resources.displayMetrics.density
                setColor(ColorUtils.setAlphaComponent(category.color, 0x24))
            }
        }

        val due = Dates.label(context, task.dueAt, done)
        b.tvDue.text = due.text
        b.tvDue.setTextColor(
            ContextCompat.getColor(context, if (due.overdue) R.color.danger else R.color.text_secondary)
        )

        val priorityColor = when (task.priority) {
            Task.PRIORITY_HIGH -> R.color.priority_high
            Task.PRIORITY_LOW -> R.color.priority_low
            else -> R.color.priority_medium
        }
        b.ivPriority.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, priorityColor))

        val subtasks = item.subtasks.sortedBy { it.position }
        val hasSubtasks = subtasks.isNotEmpty()
        b.tvSubtaskCount.visibility = if (hasSubtasks) View.VISIBLE else View.GONE
        b.tvSubtaskCount.text =
            context.getString(R.string.subtask_count, subtasks.count { it.done }, subtasks.size)
        b.btnExpand.visibility = if (hasSubtasks) View.VISIBLE else View.GONE

        b.subtaskContainer.removeAllViews()
        subtasks.forEach { subtask ->
            val sb = ItemSubtaskBinding.inflate(LayoutInflater.from(context), b.subtaskContainer, false)
            sb.tvSubtask.text = subtask.title
            sb.ivCheck.setBackgroundResource(if (subtask.done) R.drawable.checkbox_on else R.drawable.checkbox_off)
            if (subtask.done) sb.ivCheck.setImageResource(R.drawable.ic_check) else sb.ivCheck.setImageDrawable(null)
            sb.tvSubtask.setTextColor(
                ContextCompat.getColor(context, if (subtask.done) R.color.text_muted else R.color.text_primary)
            )
            strike(sb.tvSubtask, subtask.done)
            sb.root.setOnClickListener { onSubtaskClick(subtask) }
            b.subtaskContainer.addView(sb.root)
        }

        val open = hasSubtasks && task.id in expanded
        b.subtaskContainer.visibility = if (open) View.VISIBLE else View.GONE
        b.btnExpand.rotation = if (open) 180f else 0f

        b.btnExpand.setOnClickListener {
            val nowOpen = expanded.add(task.id)
            if (!nowOpen) expanded.remove(task.id)
            (b.root.parent as? ViewGroup)?.let {
                TransitionManager.beginDelayedTransition(it, AutoTransition().setDuration(220))
            }
            b.subtaskContainer.visibility = if (nowOpen) View.VISIBLE else View.GONE
            b.btnExpand.animate().rotation(if (nowOpen) 180f else 0f).setDuration(220).start()
        }
        b.taskCard.setOnClickListener { onCardClick(item) }
    }

    private fun strike(view: TextView, on: Boolean) {
        view.paintFlags = if (on) {
            view.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            view.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<TaskWithDetails>() {
        override fun areItemsTheSame(old: TaskWithDetails, new: TaskWithDetails) =
            old.task.id == new.task.id

        override fun areContentsTheSame(old: TaskWithDetails, new: TaskWithDetails) = old == new
    }
}