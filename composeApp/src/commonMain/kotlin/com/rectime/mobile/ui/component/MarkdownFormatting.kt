package com.rectime.mobile.ui.component

import org.intellij.markdown.MarkdownElementTypes as Elements
import org.intellij.markdown.MarkdownTokenTypes as Tokens
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMTokenTypes

// 正規表現で記号を推測せず、表示にも使うパーサーが解釈した構文で判断する。
private val formattingTypes = setOf(
    Elements.ATX_1, Elements.ATX_2, Elements.ATX_3,
    Elements.ATX_4, Elements.ATX_5, Elements.ATX_6,
    Elements.SETEXT_1, Elements.SETEXT_2,
    Elements.EMPH, Elements.STRONG, Elements.CODE_SPAN,
    Elements.ORDERED_LIST, Elements.UNORDERED_LIST, Elements.BLOCK_QUOTE,
    Elements.CODE_FENCE, Elements.CODE_BLOCK,
    Elements.INLINE_LINK, Elements.FULL_REFERENCE_LINK, Elements.SHORT_REFERENCE_LINK,
    Elements.IMAGE, Elements.AUTOLINK,
    Tokens.HORIZONTAL_RULE, Tokens.HARD_LINE_BREAK,
    GFMElementTypes.TABLE, GFMElementTypes.STRIKETHROUGH, GFMTokenTypes.GFM_AUTOLINK,
)

internal fun ASTNode.hasMarkdownFormatting(): Boolean {
    val pending = ArrayDeque<ASTNode>()
    pending.add(this)
    while (pending.isNotEmpty()) {
        val node = pending.removeLast()
        if (node.type in formattingTypes) return true
        pending.addAll(node.children)
    }
    return false
}
