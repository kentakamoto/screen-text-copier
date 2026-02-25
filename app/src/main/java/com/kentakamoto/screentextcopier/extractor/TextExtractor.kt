package com.kentakamoto.screentextcopier.extractor

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

class TextExtractor {

    /**
     * 複数ウィンドウからテキストを抽出するメインエントリポイント（現在の画面のみ）
     */
    fun extractFromWindows(windows: List<AccessibilityWindowInfo>): String {
        val targetWindows = windows.filter { window ->
            window.type == AccessibilityWindowInfo.TYPE_APPLICATION
        }

        val allNodes = mutableListOf<TextNode>()

        for (window in targetWindows) {
            val root = window.root ?: continue
            try {
                collectTextNodes(root, allNodes)
            } catch (_: Exception) {
                // ノード取得に失敗した場合はスキップ
            }
        }

        return buildFinalText(allNodes)
    }

    /**
     * 単一ルートノードからテキストを抽出
     */
    fun extractFromRoot(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""
        val allNodes = mutableListOf<TextNode>()
        try {
            collectTextNodes(root, allNodes)
        } catch (_: Exception) {
            // ノード取得に失敗した場合は空文字列を返す
        }
        return buildFinalText(allNodes)
    }

    /**
     * 現在の画面からテキスト行のリストを抽出（スクロール収集用）
     * 順序を維持したまま個別の行として返す
     */
    fun extractVisibleLines(windows: List<AccessibilityWindowInfo>): List<String> {
        val targetWindows = windows.filter { window ->
            window.type == AccessibilityWindowInfo.TYPE_APPLICATION
        }

        val allNodes = mutableListOf<TextNode>()

        for (window in targetWindows) {
            val root = window.root ?: continue
            try {
                collectTextNodes(root, allNodes)
            } catch (_: Exception) {
                // skip
            }
        }

        if (allNodes.isEmpty()) return emptyList()

        val sorted = allNodes.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))
        val deduplicated = deduplicateNodes(sorted)

        return deduplicated.map { it.text }
    }

    /**
     * ルートノードからスクロール可能なノードを探す
     */
    fun findScrollableNode(windows: List<AccessibilityWindowInfo>): AccessibilityNodeInfo? {
        val targetWindows = windows.filter { window ->
            window.type == AccessibilityWindowInfo.TYPE_APPLICATION
        }
        for (window in targetWindows) {
            val root = window.root ?: continue
            val scrollable = findScrollableRecursive(root)
            if (scrollable != null) return scrollable
        }
        return null
    }

    private fun findScrollableRecursive(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val child = try { node.getChild(i) } catch (_: Exception) { null }
            if (child != null) {
                val result = findScrollableRecursive(child)
                if (result != null) return result
            }
        }
        return null
    }

    /**
     * ノードツリーを再帰的に探索してテキストノードを収集
     */
    private fun collectTextNodes(
        node: AccessibilityNodeInfo,
        result: MutableList<TextNode>
    ) {
        // 非表示ノードをスキップ
        if (!node.isVisibleToUser) return

        // パスワードフィールドをスキップ
        if (node.isPassword) return

        // テキストの抽出
        val text = extractTextFromNode(node)
        if (text.isNotBlank()) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            result.add(TextNode(text = text, bounds = bounds))
        }

        // 子ノードを再帰探索
        for (i in 0 until node.childCount) {
            val child = try {
                node.getChild(i)
            } catch (_: Exception) {
                null
            }
            if (child != null) {
                collectTextNodes(child, result)
            }
        }
    }

    /**
     * ノードからテキストを抽出（優先順位付き）
     */
    private fun extractTextFromNode(node: AccessibilityNodeInfo): String {
        // 1. テキストコンテンツ（TextView, EditText等）
        val text = node.text?.toString()?.trim()
        if (!text.isNullOrBlank()) return text

        // 2. ContentDescription（画像の代替テキスト等）
        val contentDesc = node.contentDescription?.toString()?.trim()
        if (!contentDesc.isNullOrBlank()) {
            if (!isSystemUiLabel(contentDesc)) return contentDesc
        }

        return ""
    }

    /**
     * システムUIラベルを除外するフィルター
     */
    private fun isSystemUiLabel(text: String): Boolean {
        val systemLabels = setOf(
            "Back", "Home", "Recents", "Overview",
            "戻る", "ホーム", "最近", "概要",
            "Navigate up", "More options", "Search",
            "ナビゲートアップ", "その他のオプション", "検索",
            "Close", "閉じる", "Open", "開く"
        )
        return systemLabels.contains(text)
    }

    /**
     * 収集したテキストノードを座標順にソートして最終テキストに組み立てる
     */
    private fun buildFinalText(nodes: List<TextNode>): String {
        if (nodes.isEmpty()) return ""

        // 上→下、左→右の順でソート
        val sorted = nodes.sortedWith(compareBy({ it.bounds.top }, { it.bounds.left }))

        // 重複テキストの除去
        val deduplicated = deduplicateNodes(sorted)

        return buildString {
            var lastBottom = -1
            var lastText = ""

            for (node in deduplicated) {
                // 連続する完全一致テキストをスキップ
                if (node.text == lastText) continue

                val isNewLine = lastBottom == -1 || node.bounds.top > lastBottom - 5
                if (isNotEmpty()) {
                    if (isNewLine) append("\n") else append(" ")
                }
                append(node.text)
                lastBottom = node.bounds.bottom
                lastText = node.text
            }
        }
    }

    /**
     * 親子関係で重複するテキストノードを除去
     * 親ノードが子ノードと同じテキスト・同じ領域を持つ場合、子ノードのみ採用
     */
    private fun deduplicateNodes(nodes: List<TextNode>): List<TextNode> {
        val result = mutableListOf<TextNode>()
        for (node in nodes) {
            val isDuplicate = result.any { existing ->
                existing.text == node.text &&
                    existing.bounds.contains(node.bounds)
            }
            if (!isDuplicate) result.add(node)
        }
        return result
    }

    private data class TextNode(
        val text: String,
        val bounds: Rect
    )
}
