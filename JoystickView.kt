package com.example.tunnel2d

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.sqrt

class JoystickView : View {

    interface Listener {
        fun onPausePressed()
        fun shouldShowPauseText(): Boolean
    }

    private var listener: Listener? = null

    fun setListener(l: Listener) {
        listener = l
    }

    // ▪ Pauza tlačítko
    private var buttonRect = RectF()
    private var isPressed = false

    private val paintButton = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintButtonPressed = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintText = Paint().apply {
        color = Color.WHITE
        textSize = 28f
        typeface = Typeface.DEFAULT_BOLD
        isAntiAlias = true
    }

    // ▪ TrackPoint
    private val tpCenter = PointF()
    private var tpRadius = 0f

    private val paintTrackPoint = Paint().apply {
        color = Color.RED
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintTrackPointDots = Paint().apply {
        color = Color.rgb(90, 0, 0) // tmavší červená pro texturu
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    private fun init() {
        // případně něco do budoucna
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // ▪ Pauza tlačítko
        val btnSize = minOf(w, h) / 30f
//      val left = w - btnSize - 10f
//      val top = 10f
//      val right = w - 10f
//      val bottom = top + btnSize
        val left = tpCenter.x + tpRadius + btnSize
        val top = tpCenter.y + btnSize
        val right = left + btnSize
        val bottom = top + btnSize
        buttonRect.set(left, top, right, bottom)

        // ▪ TrackPoint pozice a velikost
        tpRadius = minOf(w, h) / 12f
        tpCenter.set(tpRadius + 20f, h - tpRadius - 20f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (buttonRect.contains(event.x, event.y)) {
                    isPressed = true
                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isPressed && buttonRect.contains(event.x, event.y)) {
                    listener?.onPausePressed()
                }
                isPressed = false
                invalidate()
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // ▪ Pauza tlačítko
        val paint = if (isPressed) paintButtonPressed else paintButton
        canvas.drawRoundRect(buttonRect, 15f, 15f, paint)

        val text = "||"
        val textWidth = paintText.measureText(text)
        val textX = buttonRect.centerX() - textWidth / 2
        val textY = buttonRect.centerY() + paintText.textSize / 3
        canvas.drawText(text, textX, textY, paintText)

        // ▪ PAUSE text
        if (listener?.shouldShowPauseText() == true) {
            val pauseText = "PAUSE"
            canvas.drawText(pauseText, 50f, 150f, paintText)
        }

        // ▪ TrackPoint: základní červený kruh
        canvas.drawCircle(tpCenter.x, tpCenter.y, tpRadius, paintTrackPoint)

        // ▪ TrackPoint textura: tmavé tečky
        val dotSpacing = 15f
        for (dx in -tpRadius.toInt()..tpRadius.toInt() step dotSpacing.toInt()) {
            for (dy in -tpRadius.toInt()..tpRadius.toInt() step dotSpacing.toInt()) {
                val fx = dx.toFloat()
                val fy = dy.toFloat()
                val distance = sqrt(fx * fx + fy * fy)
                if (distance < tpRadius * 0.85f) {
                    val x = tpCenter.x + fx
                    val y = tpCenter.y + fy
                    canvas.drawCircle(x, y, 2f, paintTrackPointDots)
                }
            }
        }
    }
}
