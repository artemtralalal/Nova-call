package com.vibe.dialer

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.sin
import kotlin.random.Random

class DynamicCallBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class Mode { WAITING, ACTIVE, ENDED_NO_ANSWER, ENDED_REJECTED }

    private var mode = Mode.WAITING
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
    private var t = 0f
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 4000L; repeatCount = ValueAnimator.INFINITE; interpolator = LinearInterpolator()
        addUpdateListener { t = it.animatedFraction; invalidate() }
    }
    private var symbolProgress = 0f
    private var symbolAnimator: ValueAnimator? = null

    private data class Blob(var x: Float, var y: Float, var r: Float, var color: Int, var phase: Float)

    private val blobs = mutableListOf(
        Blob(0.3f,0.3f,0.35f,0xFFFFCC33.toInt(),0f),
        Blob(0.7f,0.6f,0.30f,0xFFFF9900.toInt(),1.2f),
        Blob(0.5f,0.8f,0.25f,0xFFFFE066.toInt(),2.1f)
    )

    init { setLayerType(LAYER_TYPE_SOFTWARE, null); animator.start() }

    fun setMode(m: Mode) {
        if (mode == m) return
        mode = m
        when (m) {
            Mode.WAITING -> setColors(0xFFFFCC33.toInt(), 0xFFFF9900.toInt(), 0xFFFFE066.toInt())
            Mode.ACTIVE -> setColors(0xFF4CD964.toInt(), 0xFF34C759.toInt(), 0xFF7CFFB2.toInt())
            Mode.ENDED_NO_ANSWER -> { setColors(0xFFFFCC33.toInt(), 0xFFFF9900.toInt(), 0xFFFFE066.toInt()); animateSymbol() }
            Mode.ENDED_REJECTED -> { setColors(0xFFFF5A6A.toInt(), 0xFFFF2D55.toInt(), 0xFFFF8A80.toInt()); animateSymbol() }
        }
        invalidate()
    }

    private fun setColors(c1:Int,c2:Int,c3:Int) { blobs[0].color=c1; blobs[1].color=c2; blobs[2].color=c3 }

    private fun animateSymbol() {
        symbolAnimator?.cancel()
        symbolProgress = 0f
        symbolAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900; addUpdateListener { symbolProgress = it.animatedValue as Float; invalidate() }; start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawColor(0xFF101218.toInt())
        for (b in blobs) {
            val ox = sin((t * Math.PI * 2 + b.phase).toDouble()).toFloat() * 0.08f
            val oy = sin((t * Math.PI * 2 * 0.8 + b.phase * 1.3).toDouble()).toFloat() * 0.08f
            val cx = (b.x + ox) * w
            val cy = (b.y + oy) * h
            val r = b.r * w.coerceAtMost(h)
            paint.color = b.color
            paint.alpha = 150
            paint.maskFilter = BlurMaskFilter(r * 0.45f, BlurMaskFilter.Blur.NORMAL)
            canvas.drawCircle(cx, cy, r, paint)
        }
        paint.maskFilter = null

        if (mode == Mode.ENDED_NO_ANSWER || mode == Mode.ENDED_REJECTED) {
            drawSymbol(canvas, w, h)
        }
        if (mode == Mode.ACTIVE && symbolProgress > 0f) drawSymbol(canvas, w, h)
    }

    fun showCheck() { mode = Mode.ACTIVE; setColors(0xFF4CD964.toInt(),0xFF34C759.toInt(),0xFF7CFFB2.toInt()); animateSymbol() }

    private fun drawSymbol(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f; val cy = h * 0.38f; val s = w * 0.12f
        symbolPaint.color = when(mode){ Mode.ENDED_REJECTED -> 0xFFFF5A6A.toInt(); Mode.ENDED_NO_ANSWER -> 0xFFFFCC33.toInt(); else -> 0xFF4CD964.toInt() }
        symbolPaint.alpha = (220 * symbolProgress).toInt().coerceIn(0,255)
        symbolPaint.strokeWidth = w * 0.018f
        symbolPaint.maskFilter = BlurMaskFilter(18f * (1f - symbolProgress).coerceAtLeast(0.2f), BlurMaskFilter.Blur.NORMAL)
        val p = Path()
        when (mode) {
            Mode.ENDED_REJECTED -> {
                p.moveTo(cx - s, cy - s); p.lineTo(cx - s + 2*s*symbolProgress, cy - s + 2*s*symbolProgress)
                val p2 = Path(); p2.moveTo(cx + s, cy - s); p2.lineTo(cx + s - 2*s*symbolProgress, cy - s + 2*s*symbolProgress)
                canvas.drawPath(p, symbolPaint); canvas.drawPath(p2, symbolPaint)
            }
            Mode.ENDED_NO_ANSWER -> {
                p.moveTo(cx, cy - s); p.lineTo(cx, cy + s * 0.4f * symbolProgress)
                canvas.drawPath(p, symbolPaint)
                symbolPaint.style = Paint.Style.FILL
                canvas.drawCircle(cx, cy + s * 0.75f, symbolPaint.strokeWidth * 0.5f * symbolProgress, symbolPaint)
                symbolPaint.style = Paint.Style.STROKE
            }
            else -> { // check
                p.moveTo(cx - s, cy); p.lineTo(cx - s*0.2f, cy + s*0.7f*symbolProgress); p.lineTo(cx - s*0.2f + (s*1.2f*symbolProgress), cy + s*0.7f*symbolProgress - (s*1.4f*symbolProgress))
                canvas.drawPath(p, symbolPaint)
            }
        }
        symbolPaint.maskFilter = null
    }

    override fun onDetachedFromWindow() { animator.cancel(); symbolAnimator?.cancel(); super.onDetachedFromWindow() }
}
