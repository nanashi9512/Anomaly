package com.example.anomaly

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.annotation.DrawableRes
import kotlin.math.max
import kotlin.math.min

class SceneView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val missPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 8f
        strokeCap = Paint.Cap.ROUND
    }
    private val foundCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
        strokeWidth = 2f // Reduced stroke width
    }

    private var sceneRect = RectF()
    private var sceneWidth = 0f
    private var sceneHeight = 0f

    private val itemCache = mutableMapOf<Int, CroppedBitmap>()
    private val backgroundCache = mutableMapOf<Int, Bitmap>()
    private val alphaMasks = mutableMapOf<String, Bitmap>()

    private var currentStage: Stage? = null
    private var itemsToDraw: List<ItemDrawState> = emptyList()

    private var missPoint: PointF? = null
    private var missAlpha = 0
    private val missDuration = 400L
    private var missStartTime = 0L

    private var onCorrectTap: ((String) -> Unit)? = null
    private var onWrongTap: (() -> Unit)? = null

    class CroppedBitmap(val bitmap: Bitmap, val offsetLeft: Int, val offsetTop: Int)

    fun setData(stage: Stage, items: List<ItemDrawState>) {
        val stageChanged = this.currentStage?.id != stage.id
        this.currentStage = stage
        this.itemsToDraw = items
        
        if (stageChanged) {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeResource(resources, stage.bgRes, options)
            sceneWidth = options.outWidth.toFloat()
            sceneHeight = options.outHeight.toFloat()
            updateSceneRect()
        }
        invalidate()
    }

    fun setCallbacks(onCorrect: (String) -> Unit, onWrong: () -> Unit) {
        this.onCorrectTap = onCorrect
        this.onWrongTap = onWrong
    }

    private fun updateSceneRect() {
        if (width == 0 || height == 0 || sceneWidth == 0f) return
        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val scale = min(viewW / sceneWidth, viewH / sceneHeight)
        val dw = sceneWidth * scale
        val dh = sceneHeight * scale
        sceneRect.set((viewW - dw) / 2f, (viewH - dh) / 2f, (viewW + dw) / 2f, (viewH + dh) / 2f)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateSceneRect()
    }

    private fun getCroppedBitmap(@DrawableRes resId: Int): CroppedBitmap? {
        itemCache[resId]?.let { return it }
        val full = BitmapFactory.decodeResource(resources, resId) ?: return null
        
        var minX = full.width
        var minY = full.height
        var maxX = -1
        var maxY = -1
        
        for (y in 0 until full.height) {
            for (x in 0 until full.width) {
                if (Color.alpha(full.getPixel(x, y)) > 0) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        
        if (maxX == -1) {
            full.recycle()
            return null
        }
        
        val cropped = Bitmap.createBitmap(full, minX, minY, maxX - minX + 1, maxY - minY + 1)
        if (full != cropped) full.recycle()
        
        val cb = CroppedBitmap(cropped, minX, minY)
        itemCache[resId] = cb
        return cb
    }

    private fun getBackgroundBitmap(@DrawableRes resId: Int): Bitmap? {
        backgroundCache[resId]?.let { return it }
        val bitmap = BitmapFactory.decodeResource(resources, resId) ?: return null
        backgroundCache[resId] = bitmap
        return bitmap
    }

    private fun drawLayer(canvas: Canvas, resId: Int, alpha: Float = 1.0f, mode: PorterDuff.Mode? = null) {
        val bitmap = getBackgroundBitmap(resId) ?: return
        paint.alpha = (alpha * 255).toInt()
        val oldMode = paint.xfermode
        if (mode != null) paint.xfermode = PorterDuffXfermode(mode)
        canvas.drawBitmap(bitmap, null, sceneRect, paint)
        paint.xfermode = oldMode
        paint.alpha = 255
    }

    override fun onDraw(canvas: Canvas) {
        val stage = currentStage ?: return
        if (sceneRect.isEmpty) return

        drawLayer(canvas, stage.bgRes)
        stage.below.forEach { drawLayer(canvas, it) }

        for (drawState in itemsToDraw) {
            val cb = getCroppedBitmap(drawState.resId) ?: continue
            val rw = sceneRect.width() / sceneWidth
            val rh = sceneRect.height() / sceneHeight
            
            val l = sceneRect.left + cb.offsetLeft * rw
            val t = sceneRect.top + cb.offsetTop * rh
            val r = l + cb.bitmap.width * rw
            val b = t + cb.bitmap.height * rh
            
            canvas.drawBitmap(cb.bitmap, null, RectF(l, t, r, b), paint)
            
            if (drawState.isFound) {
                val cx = (l + r) / 2
                val cy = (t + b) / 2
                // radius = max dimension / 20 to make circles very small
                canvas.drawCircle(cx, cy, max(r - l, b - t) / 20f, foundCirclePaint)
            }
        }

        stage.above.forEach { resId ->
            drawLayer(canvas, resId, stage.aboveAlpha[resId] ?: 1.0f, stage.aboveBlendMode[resId])
        }

        if (missPoint != null) {
            val elapsed = System.currentTimeMillis() - missStartTime
            if (elapsed < missDuration) {
                missAlpha = (255 * (1 - elapsed.toFloat() / missDuration)).toInt()
                missPaint.alpha = missAlpha
                val s = 30f
                canvas.drawLine(missPoint!!.x - s, missPoint!!.y - s, missPoint!!.x + s, missPoint!!.y + s, missPaint)
                canvas.drawLine(missPoint!!.x + s, missPoint!!.y - s, missPoint!!.x - s, missPoint!!.y + s, missPaint)
                invalidate()
            } else { missPoint = null }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            if (!sceneRect.contains(x, y)) return true
            val sx = (x - sceneRect.left) * (sceneWidth / sceneRect.width())
            val sy = (y - sceneRect.top) * (sceneHeight / sceneRect.height())
            val hitItem = checkHit(sx, sy)
            if (hitItem != null) onCorrectTap?.invoke(hitItem)
            else {
                showMiss(x, y)
                onWrongTap?.invoke()
            }
        }
        return true
    }

    private fun checkHit(sx: Float, sy: Float): String? {
        val stage = currentStage ?: return null
        val density = resources.displayMetrics.density
        val hr = 24 * density * (sceneWidth / sceneRect.width())
        for (item in stage.items.reversed()) {
            val mask = getAlphaMask(item)
            if (isPixelHit(mask, sx, sy, hr)) return item.name
        }
        return null
    }

    private fun getAlphaMask(item: GameItem): Bitmap {
        val key = "${currentStage?.id}_${item.name}"
        alphaMasks[key]?.let { return it }
        val options = BitmapFactory.Options().apply { inSampleSize = 4 }
        val bmA = BitmapFactory.decodeResource(resources, item.diffRes, options)
        val bmN = item.normalRes?.let { BitmapFactory.decodeResource(resources, it, options) }
        
        val width = bmA.width
        val height = bmA.height
        val merged = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        val canvas = Canvas(merged)
        val p = Paint()
        canvas.drawBitmap(bmA, 0f, 0f, p)
        bmN?.let { canvas.drawBitmap(it, 0f, 0f, p) }

        bmA.recycle()
        bmN?.recycle()
        alphaMasks[key] = merged
        return merged
    }

    private fun isPixelHit(mask: Bitmap, sx: Float, sy: Float, r: Float): Boolean {
        val mx = (sx / 4).toInt()
        val my = (sy / 4).toInt()
        val mr = (r / 4).toInt()
        for (ix in max(0, mx - mr)..min(mask.width - 1, mx + mr)) {
            for (iy in max(0, my - mr)..min(mask.height - 1, my + mr)) {
                if (Color.alpha(mask.getPixel(ix, iy)) > 0) return true
            }
        }
        return false
    }

    private fun showMiss(x: Float, y: Float) {
        missPoint = PointF(x, y); missStartTime = System.currentTimeMillis(); invalidate()
    }

    fun release() {
        itemCache.values.forEach { it.bitmap.recycle() }
        itemCache.clear()
        backgroundCache.values.forEach { it.recycle() }
        backgroundCache.clear()
        alphaMasks.values.forEach { it.recycle() }
        alphaMasks.clear()
    }
}
