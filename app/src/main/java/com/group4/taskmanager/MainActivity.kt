package com.group4.taskmanager

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.snackbar.Snackbar
import com.group4.taskmanager.data.Category
import com.group4.taskmanager.data.Task
import com.group4.taskmanager.databinding.ActivityMainBinding
import com.group4.taskmanager.databinding.SheetFilterBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: TaskViewModel by viewModels()
    private val adapter = TaskAdapter(
        onStatusClick = { viewModel.cycleStatus(it) },
        onSubtaskClick = { viewModel.toggleSubtask(it) },
        onCardClick = { openEditor(it.task.id) }
    )
    private var chipCategories: List<Category> = emptyList()
    private var searchShown: Boolean? = null
    private var shownKey: Int? = null

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            viewModel.setSearching(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        onBackPressedDispatcher.addCallback(this, backCallback)

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
        binding.rvTasks.layoutAnimation =
            AnimationUtils.loadLayoutAnimation(this, R.anim.layout_task_enter)
        ItemTouchHelper(SwipeToDeleteCallback(this) { deleteAt(it) })
            .attachToRecyclerView(binding.rvTasks)

        binding.tabToday.setOnClickListener { viewModel.setTab(Filters.TAB_TODAY) }
        binding.tabUpcoming.setOnClickListener { viewModel.setTab(Filters.TAB_UPCOMING) }
        binding.fabAdd.setOnClickListener { openEditor(-1) }
        binding.btnFilter.setOnClickListener { showFilterSheet() }

        if (savedInstanceState == null) {
            ObjectAnimator.ofPropertyValuesHolder(
                binding.fabAdd,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0f, 1f)
            ).apply {
                duration = 450
                startDelay = 300
                interpolator = OvershootInterpolator()
                start()
            }
        }

        binding.btnSearch.setOnClickListener {
            if (viewModel.searching.value) {
                viewModel.setSearching(false)
            } else {
                viewModel.setSearching(true)
                binding.etSearch.post {
                    binding.etSearch.requestFocus()
                    WindowCompat.getInsetsController(window, binding.etSearch)
                        .show(WindowInsetsCompat.Type.ime())
                }
            }
        }
        binding.etSearch.setText(viewModel.filters.value.query)
        binding.etSearch.doAfterTextChanged { viewModel.setQuery(it?.toString().orEmpty()) }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                hideKeyboard()
                true
            } else {
                false
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.tasks.collect { list ->
                        if (list == null) return@collect
                        val f = viewModel.filters.value
                        val key = listOf(f.tab, f.categoryId, f.status, f.priority).hashCode()
                        val replay = adapter.itemCount == 0 || key != shownKey
                        shownKey = key

                        adapter.submitList(list) {
                            if (replay && list.isNotEmpty()) {
                                binding.rvTasks.scheduleLayoutAnimation()
                            }
                        }
                        val narrowed = f.query.isNotBlank() || f.sheetActive || f.categoryId != Filters.ANY
                        binding.tvEmpty.setText(if (narrowed) R.string.empty_search else R.string.empty_tasks)
                        binding.emptyState.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
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
                        styleTabs(it)
                        highlightChip(it.categoryId)
                        val tint = if (it.sheetActive) R.color.primary else R.color.text_primary
                        binding.btnFilter.imageTintList =
                            ColorStateList.valueOf(ContextCompat.getColor(this@MainActivity, tint))
                    }
                }
                launch {
                    viewModel.progress.collect { (done, total) -> renderProgress(done, total) }
                }
                launch { viewModel.searching.collect { renderSearch(it) } }
            }
        }
    }

    private fun deleteAt(position: Int) {
        if (position == RecyclerView.NO_POSITION || position >= adapter.itemCount) return
        val item = adapter.taskAt(position)
        viewModel.delete(item)
        Snackbar.make(binding.main, R.string.task_deleted, Snackbar.LENGTH_LONG)
            .setAnchorView(binding.fabAdd)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.text_primary))
            .setTextColor(Color.WHITE)
            .setActionTextColor(ContextCompat.getColor(this, R.color.ring_track))
            .setAction(R.string.action_undo) { viewModel.restore(item) }
            .show()
    }

    private fun renderSearch(searching: Boolean) {
        binding.btnSearch.setImageResource(if (searching) R.drawable.ic_close else R.drawable.ic_search)
        binding.btnSearch.contentDescription =
            getString(if (searching) R.string.cd_close_search else R.string.cd_search)
        backCallback.isEnabled = searching
        binding.titleBlock.visibility = if (searching) View.GONE else View.VISIBLE

        val field = binding.etSearch
        if (searchShown != searching) {
            val animate = searchShown != null
            searchShown = searching
            field.animate().cancel()
            if (searching) {
                field.visibility = View.VISIBLE
                if (animate) {
                    field.alpha = 0f
                    field.translationX = dp(24).toFloat()
                    field.animate().alpha(1f).translationX(0f).setDuration(250).start()
                } else {
                    field.alpha = 1f
                    field.translationX = 0f
                }
            } else {
                field.visibility = View.GONE
                hideKeyboard()
                if (field.text.isNotEmpty()) field.setText("")
            }
        }
    }

    private fun hideKeyboard() {
        WindowCompat.getInsetsController(window, binding.etSearch)
            .hide(WindowInsetsCompat.Type.ime())
        binding.etSearch.clearFocus()
    }

    private fun styleTabs(f: Filters) {
        val dim = f.query.isNotBlank()
        styleTab(binding.tvTabToday, binding.indToday, !dim && f.tab == Filters.TAB_TODAY)
        styleTab(binding.tvTabUpcoming, binding.indUpcoming, !dim && f.tab == Filters.TAB_UPCOMING)
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
            styleChip(chip, i == selected)
        }
    }

    private fun styleChip(view: TextView, on: Boolean) {
        view.setBackgroundResource(if (on) R.drawable.bg_chip_on else R.drawable.bg_chip_off)
        view.setTextColor(
            ContextCompat.getColor(this, if (on) android.R.color.white else R.color.text_secondary)
        )
    }

    private fun showFilterSheet() {
        val sheet = SheetFilterBinding.inflate(layoutInflater)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheet.root)
        (sheet.root.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)

        fun paint() {
            val f = viewModel.filters.value
            styleChip(sheet.fsAny, f.status == Filters.ANY)
            styleChip(sheet.fsTodo, f.status == Task.STATUS_TODO)
            styleChip(sheet.fsProgress, f.status == Task.STATUS_PROGRESS)
            styleChip(sheet.fsDone, f.status == Task.STATUS_DONE)
            styleChip(sheet.fpAny, f.priority == Filters.ANY)
            styleChip(sheet.fpLow, f.priority == Task.PRIORITY_LOW)
            styleChip(sheet.fpMedium, f.priority == Task.PRIORITY_MEDIUM)
            styleChip(sheet.fpHigh, f.priority == Task.PRIORITY_HIGH)
            styleChip(sheet.soDue, f.sort == Filters.SORT_DUE)
            styleChip(sheet.soPriority, f.sort == Filters.SORT_PRIORITY)
            styleChip(sheet.soNewest, f.sort == Filters.SORT_NEWEST)
        }

        sheet.fsAny.setOnClickListener { viewModel.setStatusFilter(Filters.ANY); paint() }
        sheet.fsTodo.setOnClickListener { viewModel.setStatusFilter(Task.STATUS_TODO); paint() }
        sheet.fsProgress.setOnClickListener { viewModel.setStatusFilter(Task.STATUS_PROGRESS); paint() }
        sheet.fsDone.setOnClickListener { viewModel.setStatusFilter(Task.STATUS_DONE); paint() }

        sheet.fpAny.setOnClickListener { viewModel.setPriorityFilter(Filters.ANY); paint() }
        sheet.fpLow.setOnClickListener { viewModel.setPriorityFilter(Task.PRIORITY_LOW); paint() }
        sheet.fpMedium.setOnClickListener { viewModel.setPriorityFilter(Task.PRIORITY_MEDIUM); paint() }
        sheet.fpHigh.setOnClickListener { viewModel.setPriorityFilter(Task.PRIORITY_HIGH); paint() }

        sheet.soDue.setOnClickListener { viewModel.setSort(Filters.SORT_DUE); paint() }
        sheet.soPriority.setOnClickListener { viewModel.setSort(Filters.SORT_PRIORITY); paint() }
        sheet.soNewest.setOnClickListener { viewModel.setSort(Filters.SORT_NEWEST); paint() }

        sheet.btnReset.setOnClickListener { viewModel.resetSheetFilters(); paint() }
        sheet.btnApply.setOnClickListener { dialog.dismiss() }

        paint()
        dialog.show()
    }

    private fun openEditor(id: Int) {
        startActivity(Intent(this, EditTaskActivity::class.java).putExtra(EditTaskActivity.EXTRA_TASK_ID, id))
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}