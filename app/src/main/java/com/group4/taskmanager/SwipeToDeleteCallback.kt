package com.group4.taskmanager

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class SwipeToDeleteCallback(
    context: Context,
    private val onSwipedAt: (Int) -> Unit
) : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {

    private val density = context.resources.displayMetrics.density
    private val icon = ContextCompat.getDrawable(context, R.drawable.ic_delete)!!
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.danger)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ) = false

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        onSwipedAt(viewHolder.bindingAdapterPosition)
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        val item = viewHolder.itemView
        if (dX < 0) {
            val radius = 16 * density
            c.drawRoundRect(
                RectF(item.right + dX, item.top.toFloat(), item.right.toFloat(), item.bottom.toFloat()),
                radius, radius, paint
            )
            val size = (24 * density).toInt()
            val right = item.right - (20 * density).toInt()
            if (-dX > size + 20 * density) {
                val top = item.top + (item.height - size) / 2
                icon.setBounds(right - size, top, right, top + size)
                icon.draw(c)
            }
        }
        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
    }
}