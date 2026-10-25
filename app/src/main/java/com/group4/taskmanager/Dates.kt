package com.group4.taskmanager

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DueLabel(val text: String, val overdue: Boolean)

object Dates {

    fun startOfToday(): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun daysFromToday(days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = startOfToday()
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    fun endOfToday(): Long = daysFromToday(1)

    fun label(context: Context, dueAt: Long?, done: Boolean): DueLabel {
        if (dueAt == null) {
            return DueLabel(context.getString(R.string.label_no_date), false)
        }
        val start = startOfToday()
        val overdue = !done && dueAt < start
        val date = SimpleDateFormat("EEE, d MMM", Locale.getDefault()).format(Date(dueAt))
        val text = when {
            overdue -> context.getString(R.string.label_overdue) + ", " + date
            dueAt >= start && dueAt < daysFromToday(1) -> context.getString(R.string.label_today)
            dueAt >= daysFromToday(1) && dueAt < daysFromToday(2) ->
                context.getString(R.string.label_tomorrow)
            else -> date
        }
        return DueLabel(text, overdue)
    }
}