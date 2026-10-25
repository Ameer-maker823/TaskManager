package com.group4.taskmanager

import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.group4.taskmanager.data.Category
import com.group4.taskmanager.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TaskViewModel by viewModels()
    private val adapter = TaskAdapter(
        onStatusClick = { viewModel.cycleStatus(it) },
        onSubtaskClick = { viewModel.toggleSubtask(it) },
        onCardClick = { openEditor(it.task.id) }
    )
    private var chipCategories: List<Category> = emptyList()

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
        binding.tabToday.setOnClickListener { viewModel.setTab(Filters.TAB_TODAY) }
        binding.tabUpcoming.setOnClickListener { viewModel.setTab(Filters.TAB_UPCOMING) }
        binding.fabAdd.setOnClickListener { openEditor(-1) }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tasks.collect { list ->
                        if (list == null) return@collect
                        adapter.submitList(list)
                        binding.emptyState.visibility =
                            if (list.isEmpty()) View.VISIBLE else View.GONE
                    }
                }
                launch {
                    viewModel.categories.collect {
                        chipCategories = it
                        buildChips(it)
                    }
                }
                launch {
                    viewModel.filters.collect {
                        styleTabs(it.tab)
                        highlightChip(it.categoryId)
                    }
                }
                launch {
                    viewModel.progress.collect { (done, total) -> renderProgress(done, total) }
                }
            }
        }
    }

    private fun styleTabs(tab: Int) {
        val todayOn = tab == Filters.TAB_TODAY
        styleTab(binding.tvTabToday, binding.indToday, todayOn)
        styleTab(binding.tvTabUpcoming, binding.indUpcoming, !todayOn)
    }

    private fun styleTab(label: TextView, indicator: View, on: Boolean) {
        label.setTextColor(
            ContextCompat.getColor(this, if (on) R.color.primary else R.color.text_secondary)
        )
        indicator.visibility = if (on) View.VISIBLE else View.INVISIBLE
    }

    private fun renderProgress(done: Int, total: Int) {
        val percent = if (total == 0) 0 else done * 100 / total
        ObjectAnimator.ofInt(binding.ringProgress, "progress", binding.ringProgress.progress, percent)
            .apply {
                duration = 600
                start()
            }
        binding.tvPercent.text = "$percent%"
        binding.tvProgressTitle.text = getString(R.string.progress_done, done, total)
        binding.tvProgressSub.text = when {
            total == 0 -> getString(R.string.empty_tasks)
            done == total -> getString(R.string.progress_complete)
            else -> getString(R.string.progress_left, total - done)
        }
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
                setOnClickListener {
                    viewModel.setCategory(if (index == 0) Filters.ANY else chipCategories[index - 1].id)
                }
            }
            binding.chipRow.addView(chip)
        }
        highlightChip(viewModel.filters.value.categoryId)
    }

    private fun highlightChip(categoryId: Int) {
        val selected =
            if (categoryId == Filters.ANY) 0
            else chipCategories.indexOfFirst { it.id == categoryId } + 1
        for (i in 0 until binding.chipRow.childCount) {
            val chip = binding.chipRow.getChildAt(i) as TextView
            val on = i == selected
            chip.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip_off)
            chip.setTextColor(
                ContextCompat.getColor(this, if (on) android.R.color.white else R.color.text_secondary)
            )
        }
    }

    private fun openEditor(id: Int) {
        startActivity(Intent(this, EditTaskActivity::class.java).putExtra(EditTaskActivity.EXTRA_TASK_ID, id))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}