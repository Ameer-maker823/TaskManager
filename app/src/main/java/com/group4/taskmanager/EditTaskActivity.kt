package com.group4.taskmanager

import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.group4.taskmanager.data.CategoryColors
import com.group4.taskmanager.data.Subtask
import com.group4.taskmanager.data.Task
import com.group4.taskmanager.databinding.ActivityEditTaskBinding
import com.group4.taskmanager.databinding.DialogNewCategoryBinding
import com.group4.taskmanager.databinding.ItemEditSubtaskBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditTaskActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditTaskBinding
    private val viewModel: EditTaskViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("EEE, d MMM yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditTaskBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            binding.content.setPadding(
                bars.left + dp(16), bars.top + dp(16), bars.right + dp(16),
                maxOf(bars.bottom, ime.bottom) + dp(16)
            )
            insets
        }

        val editing = viewModel.taskId != -1
        binding.tvHeading.setText(if (editing) R.string.title_edit_task else R.string.title_new_task)
        binding.btnDelete.visibility = if (editing) View.VISIBLE else View.GONE

        binding.btnBack.setOnClickListener { finish() }
        binding.btnSave.setOnClickListener { save() }
        binding.btnDelete.setOnClickListener { confirmDelete() }
        binding.tvDue.setOnClickListener { pickDate() }
        binding.btnClearDate.setOnClickListener { viewModel.setDue(null) }

        binding.priLow.setOnClickListener { viewModel.setPriority(Task.PRIORITY_LOW) }
        binding.priMedium.setOnClickListener { viewModel.setPriority(Task.PRIORITY_MEDIUM) }
        binding.priHigh.setOnClickListener { viewModel.setPriority(Task.PRIORITY_HIGH) }
        binding.stTodo.setOnClickListener { viewModel.setStatus(Task.STATUS_TODO) }
        binding.stProgress.setOnClickListener { viewModel.setStatus(Task.STATUS_PROGRESS) }
        binding.stDone.setOnClickListener { viewModel.setStatus(Task.STATUS_DONE) }

        binding.btnAddStep.setOnClickListener { addStep() }
        binding.etStep.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addStep()
                true
            } else {
                false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.draft.collect { render(it) } }
                launch { viewModel.categories.collect { renderCategories() } }
            }
        }

        if (savedInstanceState == null) {
            binding.content.alpha = 0f
            binding.content.translationY = dp(24).toFloat()
            binding.content.animate().alpha(1f).translationY(0f).setDuration(350).start()
        }
    }

    private fun render(draft: Draft) {
        if (!draft.loaded) return
        if (draft.missing) {
            finish()
            return
        }
        if (!viewModel.textsFilled) {
            viewModel.textsFilled = true
            binding.etTitle.setText(draft.title)
            binding.etNotes.setText(draft.notes)
        }

        val due = draft.dueAt
        binding.tvDue.text = if (due == null) getString(R.string.no_due_date) else dateFormat.format(Date(due))
        binding.btnClearDate.visibility = if (due == null) View.GONE else View.VISIBLE

        chip(binding.priLow, draft.priority == Task.PRIORITY_LOW)
        chip(binding.priMedium, draft.priority == Task.PRIORITY_MEDIUM)
        chip(binding.priHigh, draft.priority == Task.PRIORITY_HIGH)
        chip(binding.stTodo, draft.status == Task.STATUS_TODO)
        chip(binding.stProgress, draft.status == Task.STATUS_PROGRESS)
        chip(binding.stDone, draft.status == Task.STATUS_DONE)

        renderSteps(draft.steps)
        renderCategories()
    }

    private fun chip(view: TextView, on: Boolean) {
        view.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip_off)
        view.setTextColor(
            ContextCompat.getColor(this, if (on) android.R.color.white else R.color.text_secondary)
        )
    }

    private fun renderCategories() {
        val row = binding.categoryRow
        row.removeAllViews()
        val selected = viewModel.draft.value.categoryId

        viewModel.categories.value.forEach { category ->
            val on = category.id == selected
            val chip = TextView(this).apply {
                text = category.name
                textSize = 13f
                setPadding(dp(14), dp(7), dp(14), dp(7))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                if (on) {
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14).toFloat()
                        setColor(ColorUtils.setAlphaComponent(category.color, 0x30))
                        setStroke(dp(1), category.color)
                    }
                    setTextColor(category.color)
                } else {
                    setBackgroundResource(R.drawable.bg_chip_off)
                    setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
                }
                setOnClickListener { viewModel.setCategory(if (on) null else category.id) }
            }
            row.addView(chip)
        }

        val newChip = TextView(this).apply {
            setText(R.string.chip_new_category)
            textSize = 13f
            setPadding(dp(14), dp(7), dp(14), dp(7))
            setBackgroundResource(R.drawable.bg_chip_off)
            setTextColor(ContextCompat.getColor(context, R.color.primary))
            setOnClickListener { showNewCategoryDialog() }
        }
        row.addView(newChip)
    }

    private fun renderSteps(steps: List<Subtask>) {
        binding.stepsContainer.removeAllViews()
        steps.forEachIndexed { index, step ->
            val sb = ItemEditSubtaskBinding.inflate(layoutInflater, binding.stepsContainer, false)
            sb.tvStep.text = step.title
            sb.ivCheck.setBackgroundResource(if (step.done) R.drawable.checkbox_on else R.drawable.checkbox_off)
            if (step.done) sb.ivCheck.setImageResource(R.drawable.ic_check) else sb.ivCheck.setImageDrawable(null)
            sb.tvStep.setTextColor(
                ContextCompat.getColor(this, if (step.done) R.color.text_muted else R.color.text_primary)
            )
            sb.tvStep.paintFlags = if (step.done) {
                sb.tvStep.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            } else {
                sb.tvStep.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
            }
            sb.root.setOnClickListener { viewModel.toggleStep(index) }
            sb.btnRemove.setOnClickListener { viewModel.removeStep(index) }
            binding.stepsContainer.addView(sb.root)
        }
    }

    private fun addStep() {
        val text = binding.etStep.text.toString().trim()
        if (text.isEmpty()) return
        viewModel.addStep(text)
        binding.etStep.setText("")
    }

    private fun pickDate() {
        val current = viewModel.draft.value.dueAt ?: Dates.startOfToday()
        val picker = MaterialDatePicker.Builder.datePicker()
            .setSelection(Dates.toPicker(current))
            .build()
        picker.addOnPositiveButtonClickListener { viewModel.setDue(Dates.fromPicker(it)) }
        picker.show(supportFragmentManager, "due_date")
    }

    private fun showNewCategoryDialog() {
        val dialogBinding = DialogNewCategoryBinding.inflate(layoutInflater)
        var picked = CategoryColors.all[0]
        val textColor = ContextCompat.getColor(this, R.color.text_primary)

        fun paintDots() {
            for (i in 0 until dialogBinding.colorGrid.childCount) {
                val dot = dialogBinding.colorGrid.getChildAt(i)
                val swatch = CategoryColors.all[i]
                dot.background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(swatch)
                    if (swatch == picked) setStroke(dp(3), textColor)
                }
            }
        }

        CategoryColors.all.forEach { swatch ->
            val dot = View(this).apply {
                layoutParams = GridLayout.LayoutParams().apply {
                    width = dp(38)
                    height = dp(38)
                    setMargins(dp(8), dp(8), dp(8), dp(8))
                }
                setOnClickListener {
                    picked = swatch
                    paintDots()
                }
            }
            dialogBinding.colorGrid.addView(dot)
        }
        paintDots()

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_new_category)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_add, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val name = dialogBinding.etCategoryName.text.toString().trim()
                when {
                    name.isEmpty() ->
                        dialogBinding.etCategoryName.error = getString(R.string.msg_name_required)
                    viewModel.categories.value.any { it.name.equals(name, ignoreCase = true) } ->
                        dialogBinding.etCategoryName.error = getString(R.string.msg_category_exists)
                    else -> {
                        viewModel.addCategory(name, picked)
                        dialog.dismiss()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun save() {
        val title = binding.etTitle.text.toString().trim()
        if (title.isEmpty()) {
            binding.etTitle.error = getString(R.string.msg_title_required)
            return
        }
        binding.btnSave.isEnabled = false
        viewModel.save(title, binding.etNotes.text.toString()) { finish() }
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.delete { finish() }
            }
            .show()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TASK_ID = "task_id"
    }
}