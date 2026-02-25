package com.kentakamoto.screentextcopier.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.kentakamoto.screentextcopier.R
import com.kentakamoto.screentextcopier.data.AppPreferences
import com.kentakamoto.screentextcopier.data.CopyMode
import com.kentakamoto.screentextcopier.extractor.TextExtractor
import com.kentakamoto.screentextcopier.overlay.FloatingButtonManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ScreenTextAccessibilityService : AccessibilityService() {

    private lateinit var floatingButtonManager: FloatingButtonManager
    private lateinit var textExtractor: TextExtractor
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    companion object {
        val isRunning = MutableStateFlow(false)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning.value = true

        textExtractor = TextExtractor()
        floatingButtonManager = FloatingButtonManager(
            context = this,
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager,
            onButtonClick = ::onFloatingButtonClicked
        )
        floatingButtonManager.show()

        // 設定の変更をリアルタイムで監視してボタン外観に反映
        serviceScope.launch {
            AppPreferences.buttonOpacityFlow(this@ScreenTextAccessibilityService)
                .collect { opacity ->
                    val sizeDp = AppPreferences.getButtonSizeOnce(
                        this@ScreenTextAccessibilityService
                    )
                    floatingButtonManager.updateAppearance(opacity, sizeDp)
                }
        }
        serviceScope.launch {
            AppPreferences.buttonSizeFlow(this@ScreenTextAccessibilityService)
                .collect { sizeDp ->
                    val opacity = AppPreferences.getButtonOpacityOnce(
                        this@ScreenTextAccessibilityService
                    )
                    floatingButtonManager.updateAppearance(opacity, sizeDp)
                }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // イベント駆動処理は不要。ボタンクリック時にのみテキスト抽出する
    }

    override fun onInterrupt() {
        // サービスが中断された場合の処理
    }

    override fun onDestroy() {
        isRunning.value = false
        floatingButtonManager.hide()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun onFloatingButtonClicked() {
        serviceScope.launch {
            val extractedText = withContext(Dispatchers.Default) {
                try {
                    val windowList = windows
                    if (windowList != null && windowList.isNotEmpty()) {
                        textExtractor.extractFromWindows(windowList)
                    } else {
                        // fallback: rootInActiveWindow を使用
                        textExtractor.extractFromRoot(rootInActiveWindow)
                    }
                } catch (_: Exception) {
                    ""
                }
            }

            if (extractedText.isBlank()) {
                Toast.makeText(
                    this@ScreenTextAccessibilityService,
                    R.string.no_text_found,
                    Toast.LENGTH_SHORT
                ).show()
                return@launch
            }

            val copyMode = withContext(Dispatchers.IO) {
                AppPreferences.getCopyModeOnce(this@ScreenTextAccessibilityService)
            }
            when (copyMode) {
                CopyMode.CLIPBOARD -> copyToClipboard(extractedText)
                CopyMode.SHARE -> openShareMenu(extractedText)
            }
        }
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Screen Text", text))
        Toast.makeText(
            this,
            R.string.copied_to_clipboard,
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun openShareMenu(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(
            Intent.createChooser(intent, getString(R.string.share_text)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        )
    }
}
