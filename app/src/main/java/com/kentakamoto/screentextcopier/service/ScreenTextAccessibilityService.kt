package com.kentakamoto.screentextcopier.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
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
import kotlinx.coroutines.delay
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
            onButtonClick = ::onFloatingButtonClicked,
            onButtonLongPress = ::onFloatingButtonLongPressed
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
            // フローティングボタンを一時的に隠す（テキスト収集の邪魔にならないように）
            floatingButtonManager.hide()
            Toast.makeText(
                this@ScreenTextAccessibilityService,
                "テキスト取得中...",
                Toast.LENGTH_SHORT
            ).show()

            val extractedText = try {
                extractFullPageText()
            } catch (_: Exception) {
                ""
            }

            // フローティングボタンを再表示
            floatingButtonManager.show()

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

    /**
     * ページ全体のテキストを取得する
     * 1. まず全選択+コピーを試みる（高速）
     * 2. 失敗した場合、自動スクロールで収集（確実）
     */
    private suspend fun extractFullPageText(): String {
        // 方法1: 全選択+コピー（テキスト選択可能なページ用・高速）
        val selectAllResult = trySelectAllCopy()
        if (selectAllResult.isNotBlank()) return selectAllResult

        // 方法2: 自動スクロールで収集（フォールバック）
        return extractByScrolling()
    }

    /**
     * 全選択→コピーでテキスト取得を試みる（高速パス）
     * WebView等のテキスト選択可能なコンテンツで動作する
     */
    private suspend fun trySelectAllCopy(): String {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        // クリップボードを一旦クリア
        val oldClip = clipboard.primaryClip
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))

        val root = rootInActiveWindow ?: return ""

        // WebViewや選択可能なコンテンツノードを探す
        val targetNode = findSelectableContentNode(root) ?: return ""

        // ACTION_SET_SELECTION で全範囲を選択
        val selectArgs = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, Int.MAX_VALUE)
        }
        val selected = targetNode.performAction(
            AccessibilityNodeInfo.ACTION_SET_SELECTION, selectArgs
        )
        if (!selected) {
            // 元のクリップボードを復元
            if (oldClip != null) clipboard.setPrimaryClip(oldClip)
            return ""
        }

        delay(50)

        val copied = targetNode.performAction(AccessibilityNodeInfo.ACTION_COPY)
        if (!copied) {
            targetNode.performAction(AccessibilityNodeInfo.ACTION_CLEAR_SELECTION)
            if (oldClip != null) clipboard.setPrimaryClip(oldClip)
            return ""
        }

        delay(50)

        // コピー結果を取得
        val clip = clipboard.primaryClip
        val text = if (clip != null && clip.itemCount > 0) {
            clip.getItemAt(0).text?.toString() ?: ""
        } else {
            ""
        }

        // 選択状態を解除
        targetNode.performAction(AccessibilityNodeInfo.ACTION_CLEAR_SELECTION)

        // 取得失敗なら元のクリップボードを復元
        if (text.isBlank() && oldClip != null) {
            clipboard.setPrimaryClip(oldClip)
        }

        return text.trim()
    }

    /**
     * テキスト選択可能なコンテンツノードを探す（WebView等）
     */
    private fun findSelectableContentNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val className = node.className?.toString() ?: ""
        if (className.contains("WebView")) return node

        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            if (child != null) {
                val result = findSelectableContentNode(child)
                if (result != null) return result
            }
        }
        return null
    }

    /**
     * 自動スクロールでページ全体のテキストを収集する（フォールバック）
     * 収集後は元のスクロール位置に戻す
     */
    private suspend fun extractByScrolling(): String {
        val allLines = LinkedHashSet<String>()
        val maxScrolls = 100

        val windowList = windows ?: emptyList()
        if (windowList.isEmpty()) {
            return textExtractor.extractFromRoot(rootInActiveWindow)
        }

        val scrollableNode = textExtractor.findScrollableNode(windowList)
        if (scrollableNode == null) {
            return textExtractor.extractFromWindows(windowList)
        }

        // 先頭までスクロールバック（回数を記録して後で復元）
        var scrollsToTop = 0
        for (i in 0 until maxScrolls) {
            val scrolled = scrollableNode.performAction(
                AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            )
            if (!scrolled) break
            scrollsToTop++
            delay(10)
        }

        // 先頭からテキスト収集
        delay(60)
        val topLines = textExtractor.extractVisibleLines(windows ?: emptyList())
        allLines.addAll(topLines)

        // 下方向にスクロールしながら収集
        var totalForwardScrolls = 0
        var emptyScrollCount = 0 // 新テキストなしの連続回数
        for (i in 0 until maxScrolls) {
            val scrolled = scrollableNode.performAction(
                AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            )
            if (!scrolled) break // スクロール不可 = 本当に末端
            totalForwardScrolls++

            delay(60) // UI描画を確実に待つ

            val currentWindows = windows ?: break
            val newLines = textExtractor.extractVisibleLines(currentWindows)

            val previousSize = allLines.size
            allLines.addAll(newLines)
            if (allLines.size == previousSize) {
                emptyScrollCount++
                // 3回連続で新テキストなしなら停止
                if (emptyScrollCount >= 3) break
            } else {
                emptyScrollCount = 0
            }
        }

        // 元の位置に戻す（復元は待機不要で最速）
        val scrollsBack = totalForwardScrolls - scrollsToTop
        if (scrollsBack > 0) {
            for (i in 0 until scrollsBack) {
                if (!scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)) break
            }
        } else if (scrollsBack < 0) {
            for (i in 0 until -scrollsBack) {
                if (!scrollableNode.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) break
            }
        }

        return allLines.joinToString("\n")
    }

    /**
     * 長押し: フォーカス中のテキスト欄を空にする
     */
    private fun onFloatingButtonLongPressed() {
        val focusedNode = findFocusedEditableNode()
        if (focusedNode != null) {
            val args = Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                ""
            )
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Toast.makeText(this, "テキストをクリアしました", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "テキスト欄が見つかりません", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 現在フォーカスされている編集可能なノードを探す
     */
    private fun findFocusedEditableNode(): AccessibilityNodeInfo? {
        // まずINPUT_FOCUS_CLEARを試す
        val focused = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused

        // フォーカスがない場合、画面上の編集可能なノードを探す
        val root = rootInActiveWindow ?: return null
        return findEditableNode(root)
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isFocused) return node
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            if (child != null) {
                val result = findEditableNode(child)
                if (result != null) return result
            }
        }
        return null
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
