package com.rise.blescanner.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View

class SensorVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val maxDataPoints = 100
    private val xValues = FloatArray(maxDataPoints)
    private val yValues = FloatArray(maxDataPoints)
    private val zValues = FloatArray(maxDataPoints)
    private var writeIndex = 0
    private var pointsCount = 0

    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E53935")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val yPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4CAF50")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val zPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1E88E5")
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E1E3DF")
        strokeWidth = 2f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GRAY
        textSize = 24f
    }

    private var maxValue = 12f

    fun setMaxValue(max: Float) {
        maxValue = max
    }

    fun addValues(x: Float, y: Float, z: Float) {
        xValues[writeIndex] = x
        yValues[writeIndex] = y
        zValues[writeIndex] = z
        writeIndex = (writeIndex + 1) % maxDataPoints
        if (pointsCount < maxDataPoints) {
            pointsCount++
        }
        invalidate()
    }

    fun clear() {
        writeIndex = 0
        pointsCount = 0
        xValues.fill(0f)
        yValues.fill(0f)
        zValues.fill(0f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val midY = h / 2f

        canvas.drawLine(0f, midY, w, midY, gridPaint)
        canvas.drawLine(0f, midY - h / 4f, w, midY - h / 4f, gridPaint)
        canvas.drawLine(0f, midY + h / 4f, w, midY + h / 4f, gridPaint)

        canvas.drawText("+${String.format(java.util.Locale.US, "%.1f", maxValue)}", 16f, 36f, textPaint)
        canvas.drawText("0.0", 16f, midY - 6f, textPaint)
        canvas.drawText("-${String.format(java.util.Locale.US, "%.1f", maxValue)}", 16f, h - 16f, textPaint)

        if (pointsCount < 2) return

        val dx = w / (maxDataPoints - 1)

        val xPath = Path()
        val yPath = Path()
        val zPath = Path()

        for (i in 0 until pointsCount) {
            val dataIndex = (writeIndex - pointsCount + i + maxDataPoints) % maxDataPoints
            val px = i * dx
            val pyX = midY - (xValues[dataIndex] / maxValue) * midY
            val pyY = midY - (yValues[dataIndex] / maxValue) * midY
            val pyZ = midY - (zValues[dataIndex] / maxValue) * midY

            val pyXClamped = pyX.coerceIn(0f, h)
            val pyYClamped = pyY.coerceIn(0f, h)
            val pyZClamped = pyZ.coerceIn(0f, h)

            if (i == 0) {
                xPath.moveTo(px, pyXClamped)
                yPath.moveTo(px, pyYClamped)
                zPath.moveTo(px, pyZClamped)
            } else {
                xPath.lineTo(px, pyXClamped)
                yPath.lineTo(px, pyYClamped)
                zPath.lineTo(px, pyZClamped)
            }
        }

        canvas.drawPath(xPath, xPaint)
        canvas.drawPath(yPath, yPaint)
        canvas.drawPath(zPath, zPaint)
    }
}
