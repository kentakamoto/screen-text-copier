package com.kentakamoto.screentextcopier.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageButton
import com.kentakamoto.screentextcopier.R
import com.kentakamoto.screentextcopier.data.AppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class FloatingButtonManager(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onButtonClick: () -> Unit,
    private val onButtonLongPress: () -> Unit = {},
) {
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null

    // ドラッグ状態管理
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var initialX = 0
    private var initialY = 0
    private var isDragging = false
    private var isLongPress = false
    private var touchDownTime = 0L

    companion object {
        private const val LONG_PRESS_THRESHOLD_MS = 500L
    }

    fun show() {
        if (floatingView != null) return

        CoroutineScope(Dispatchers.Main).launch {
            val (posX, posY) = withContext(Dispatchers.IO) {
                AppPreferences.getButtonPositionOnce(context)
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = posX
                y = posY
            }
            layoutParams = params

            val view = LayoutInflater.from(context).inflate(R.layout.floating_button, null)
            val button = view.findViewById<ImageButton>(R.id.floating_btn)

            applyButtonAppearance(button)
            button.setOnTouchListener(createDragTouchListener(params))

            floatingView = view
            windowManager.addView(view, params)
        }
    }

    fun hide() {
        floatingView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
                // ビューが既に削除済みの場合を無視
            }
            floatingView = null
        }
    }

    fun updateAppearance(opacity: Float, sizeDp: Int) {
        floatingView?.let { view ->
            val button = view.findViewById<ImageButton>(R.id.floating_btn)
            button.alpha = opacity
            val sizePx = dpToPx(sizeDp)
            val lp = button.layoutParams
            lp.width = sizePx
            lp.height = sizePx
            button.layoutParams = lp
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createDragTouchListener(
        params: WindowManager.LayoutParams
    ): View.OnTouchListener {
        return View.OnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    initialX = params.x
                    initialY = params.y
                    isDragging = false
                    isLongPress = false
                    touchDownTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (abs(dx) > 5 || abs(dy) > 5) isDragging = true
                    if (isDragging) {
                        params.x = initialX + dx
                        params.y = initialY + dy
                        val parentView = view.parent as? View ?: view
                        windowManager.updateViewLayout(parentView, params)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        val pressDuration = System.currentTimeMillis() - touchDownTime
                        if (pressDuration >= LONG_PRESS_THRESHOLD_MS) {
                            // 長押し → テキスト欄クリア
                            onButtonLongPress()
                        } else {
                            // 短いタップ → 全文コピー
                            onButtonClick()
                        }
                    }
                    saveButtonPosition(params.x, params.y)
                    true
                }
                else -> false
            }
        }
    }

    private fun saveButtonPosition(x: Int, y: Int) {
        CoroutineScope(Dispatchers.IO).launch {
            AppPreferences.saveButtonPosition(context, x, y)
        }
    }

    private fun applyButtonAppearance(button: ImageButton) {
        CoroutineScope(Dispatchers.Main).launch {
            val opacity = withContext(Dispatchers.IO) {
                AppPreferences.getButtonOpacityOnce(context)
            }
            val sizeDp = withContext(Dispatchers.IO) {
                AppPreferences.getButtonSizeOnce(context)
            }
            button.alpha = opacity
            val sizePx = dpToPx(sizeDp)
            val lp = button.layoutParams ?: ViewGroup.LayoutParams(sizePx, sizePx)
            lp.width = sizePx
            lp.height = sizePx
            button.layoutParams = lp
        }
    }

    private fun dpToPx(dp: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp.toFloat(),
            context.resources.displayMetrics
        ).toInt()
    }
}
