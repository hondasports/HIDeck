package com.hondasports.hideck

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

class TouchpadView(context: Context, private val controller: HidDeckController) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var lastX = 0f
    private var lastY = 0f
    private var moved = false

    init {
        setBackgroundColor(Color.rgb(27, 34, 42))
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.rgb(102, 217, 239)
        canvas.drawRoundRect(RectF(12f, 12f, width - 12f, height - 12f), 18f, 18f, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.LTGRAY
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = 18f
        canvas.drawText("Touchpad  ·  drag to move  ·  tap to click", width / 2f, height / 2f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // The touchpad lives inside the screen's ScrollView. Keep the
                // parent from stealing vertical drags, which are mouse
                // movement here rather than app scrolling.
                parent?.requestDisallowInterceptTouchEvent(true)
                lastX = event.x
                lastY = event.y
                moved = false
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ((event.x - lastX) * 1.35f).roundToInt()
                val dy = ((event.y - lastY) * 1.35f).roundToInt()
                if (dx != 0 || dy != 0) {
                    moved = true
                    controller.mouse(0, dx, dy)
                }
                lastX = event.x
                lastY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!moved) {
                    controller.mouse(MouseReportEncoder.LEFT)
                    controller.mouse(0)
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
                return true
            }
        }
        return true
    }
}
