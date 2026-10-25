package com.group4.taskmanager

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.group4.taskmanager.data.Category
import com.group4.taskmanager.data.CategoryColors
import com.group4.taskmanager.data.Subtask
import com.group4.taskmanager.data.Task
import com.group4.taskmanager.data.TaskWithDetails
import com.group4.taskmanager.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val adapter = TaskAdapter()
    private val samples by lazy { sampleTasks() }
    private var tab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.header.setPadding(
                bars.left + dp(16), bars.top + dp(12), bars.right + dp(16), dp(8)
            )
            binding.rvTasks.setPadding(
                bars.left + dp(14), dp(10), bars.right + dp(14), bars.bottom + dp(96)
            )
            (binding.fabAdd.layoutParams as FrameLayout.LayoutParams).apply {
                rightMargin = bars.right + dp(16)
                bottomMargin = bars.bottom + dp(16)
            }
            binding.fabAdd.requestLayout()
            insets
        }

        binding.rvTasks.adapter = adapter
        binding.tabToday.setOnClickListener { showTab(0) }
        binding.tabUpcoming.setOnClickListener { showTab(1) }

        buildChips(sampleCategories())
        updateProgress()
        showTab(0)
    }

    private fun showTab(index: Int) {
        tab = index
        val todayOn = index == 0
        styleTab(binding.tvTabToday, binding.indToday, todayOn)
        styleTab(binding.tvTabUpcoming, binding.indUpcoming, !todayOn)

        // Temporary: the real filtering comes from the database query in the next step.
        val end = Dates.endOfToday()
        val list = samples.filter {
            val due = it.task.dueAt
            if (todayOn) due != null && due < end else due == null || due >= end
        }
        adapter.submitList(list)
        binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun styleTab(label: TextView, indicator: View, on: Boolean) {
        label.setTextColor(
            ContextCompat.getColor(this, if (on) R.color.primary else R.color.text_secondary)
        )
        indicator.visibility = if (on) View.VISIBLE else View.INVISIBLE
    }

    private fun updateProgress() {
        val total = samples.size
        val done = samples.count { it.task.status == Task.STATUS_DONE }
        val percent = if (total == 0) 0 else done * 100 / total

        ObjectAnimator.ofInt(binding.ringProgress, "progress", 0, percent).apply {
            duration = 700
            start()
        }
        binding.tvPercent.text = "$percent%"
        binding.tvProgressTitle.text = getString(R.string.progress_done, done, total)
        binding.tvProgressSub.text =
            if (done == total) getString(R.string.progress_complete)
            else getString(R.string.progress_left, total - done)
    }

    private fun buildChips(categories: List<Category>) {
        binding.chipRow.removeAllViews()
        val names = listOf(getString(R.string.filter_all)) + categories.map { it.name }
        names.forEachIndexed { index, name ->
            val chip = TextView(this).apply {
                text = name
                textSize = 12f
                setPadding(dp(12), dp(6), dp(12), dp(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = dp(8) }
                setOnClickListener { highlightChip(index) }
            }
            binding.chipRow.addView(chip)
        }
        highlightChip(0)
    }

    private fun highlightChip(selected: Int) {
        for (i in 0 until binding.chipRow.childCount) {
            val chip = binding.chipRow.getChildAt(i) as TextView
            val on = i == selected
            chip.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip_off)
            chip.setTextColor(
                ContextCompat.getColor(this, if (on) android.R.color.white else R.color.text_secondary)
            )
        }
    }

    // Temporary sample data, replaced by the real database in the next step.
    private fun sampleCategories() = listOf(
        Category(1, "School", CategoryColors.all[0]),
        Category(2, "Home", CategoryColors.all[1]),
        Category(3, "Work", CategoryColors.all[2]),
        Category(4, "Personal", CategoryColors.all[3])
    )

    private fun sampleTasks(): List<TaskWithDetails> {
        val c = sampleCategories().associateBy { it.id }
        val hour = 3_600_000L
        val today = Dates.startOfToday()
        return listOf(
            TaskWithDetails(
                Task(1, "Call Bethel", dueAt = Dates.daysFromToday(-1) + 10 * hour,
                    priority = Task.PRIORITY_HIGH, categoryId = 4),
                c[4], emptyList()
            ),
            TaskWithDetails(
                Task(2, "Make flashcards", dueAt = today + 19 * hour,
                    status = Task.STATUS_PROGRESS, categoryId = 1),
                c[1],
                listOf(
                    Subtask(1, 2, "Biology terms", true, 0),
                    Subtask(2, 2, "Chemistry formulas", false, 1),
                    Subtask(3, 2, "Practice questions", false, 2)
                )
            ),
            TaskWithDetails(
                Task(3, "Tidy the room", dueAt = today + 17 * hour,
                    status = Task.STATUS_DONE, priority = Task.PRIORITY_LOW, categoryId = 2),
                c[2], emptyList()
            ),
            TaskWithDetails(
                Task(4, "Send project report", dueAt = Dates.daysFromToday(1) + 12 * hour,
                    priority = Task.PRIORITY_HIGH, categoryId = 3),
                c[3],
                listOf(
                    Subtask(4, 4, "Final edit", false, 0),
                    Subtask(5, 4, "Add charts", false, 1)
                )
            ),
            TaskWithDetails(
                Task(5, "Buy groceries", dueAt = Dates.daysFromToday(3),
                    priority = Task.PRIORITY_LOW, categoryId = 2),
                c[2],
                listOf(
                    Subtask(6, 5, "Eggs", true, 0),
                    Subtask(7, 5, "Rice", false, 1),
                    Subtask(8, 5, "Fruit", false, 2),
                    Subtask(9, 5, "Milk", false, 3)
                )
            ),
            TaskWithDetails(
                Task(6, "Read chapter 4", categoryId = 1),
                c[1], emptyList()
            )
        )
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}