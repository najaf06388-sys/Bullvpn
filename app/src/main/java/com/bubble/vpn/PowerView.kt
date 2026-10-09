package com.bubble.vpn

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.min

class PowerView(context: Context) : View(context) {

    var isConnected: Boolean = false
        set(value) {
            field = value
            invalidate()
        }

    private val outerRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val circleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        strokeCap = Paint.Cap.ROUND
    }

    private val arcRect = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f - 16f

        val activeColor = Color.parseColor("#10B981")
        val inactiveColor = Color.parseColor("#334155")
        val fillColor = if (isConnected) Color.parseColor("#0D2822") else Color.parseColor("#0F172A")

        circleFillPaint.color = fillColor
        canvas.drawCircle(cx, cy, radius, circleFillPaint)

        outerRingPaint.color = if (isConnected) activeColor else inactiveColor
        canvas.drawCircle(cx, cy, radius, outerRingPaint)

        iconPaint.color = if (isConnected) activeColor else Color.parseColor("#94A3B8")
        val iconRadius = radius * 0.34f
        arcRect.set(cx - iconRadius, cy - iconRadius, cx + iconRadius, cy + iconRadius)
        canvas.drawArc(arcRect, -55f, 290f, false, iconPaint)
        canvas.drawLine(cx, cy - iconRadius * 1.15f, cx, cy - iconRadius * 0.15f, iconPaint)
    }
}
