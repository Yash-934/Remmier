package com.pocketforge.mobile.runtime

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF

object NotificationVisuals {
    const val ACCENT_COLOR: Int = 0xFF00F0FF.toInt()

    @Volatile
    private var cachedLargeIcon: Bitmap? = null

    fun getOrGenerateLargeIcon(context: Context): Bitmap {
        cachedLargeIcon?.let { return it }
        synchronized(this) {
            cachedLargeIcon?.let { return it }
            val density = context.resources.displayMetrics.density
            val size = (64 * density).toInt().coerceAtLeast(128)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Deep space obsidian squircle container
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF0A121E.toInt()
                style = Paint.Style.FILL
            }
            val cornerRadius = size * 0.22f
            val rect = RectF(0f, 0f, size.toFloat(), size.toFloat())
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, bgPaint)

            // Outer glowing neon cyan border
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF00F0FF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = size * 0.05f
            }
            val borderInset = borderPaint.strokeWidth / 2f
            val borderRect = RectF(borderInset, borderInset, size - borderInset, size - borderInset)
            canvas.drawRoundRect(borderRect, cornerRadius, cornerRadius, borderPaint)

            // Top title bar divider line
            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF1E3A5F.toInt()
                style = Paint.Style.STROKE
                strokeWidth = size * 0.025f
            }
            val headerY = size * 0.28f
            canvas.drawLine(size * 0.12f, headerY, size * 0.88f, headerY, dividerPaint)

            // 3 terminal window dots
            val dotRadius = size * 0.035f
            val dotY = size * 0.16f
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
            dotPaint.color = 0xFFFF5555.toInt(); canvas.drawCircle(size * 0.22f, dotY, dotRadius, dotPaint)
            dotPaint.color = 0xFFFFB800.toInt(); canvas.drawCircle(size * 0.34f, dotY, dotRadius, dotPaint)
            dotPaint.color = 0xFF00F0FF.toInt(); canvas.drawCircle(size * 0.46f, dotY, dotRadius, dotPaint)

            // Command prompt chevron '>' in Neon Cyan
            val promptPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF00F0FF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = size * 0.075f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            val promptPath = Path().apply {
                moveTo(size * 0.26f, size * 0.47f)
                lineTo(size * 0.44f, size * 0.60f)
                lineTo(size * 0.26f, size * 0.73f)
            }
            canvas.drawPath(promptPath, promptPaint)

            // Terminal cursor '_' in Neon Cyan
            val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF00F0FF.toInt()
                style = Paint.Style.STROKE
                strokeWidth = size * 0.075f
                strokeCap = Paint.Cap.ROUND
            }
            canvas.drawLine(size * 0.52f, size * 0.73f, size * 0.74f, size * 0.73f, cursorPaint)

            cachedLargeIcon = bitmap
            return bitmap
        }
    }
}
