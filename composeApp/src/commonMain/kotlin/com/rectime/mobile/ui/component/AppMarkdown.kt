package com.rectime.mobile.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.findRootCoordinates
import com.rectime.mobile.core.attachment.attachmentKind
import com.rectime.mobile.core.attachment.AttachmentKind
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.model.markdownExtendedSpans
import com.mikepenz.markdown.compose.extendedspans.ExtendedSpans
import com.mikepenz.markdown.compose.extendedspans.RoundedCornerSpanPainter
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.compose.elements.MarkdownCheckBox
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownTable
import com.mikepenz.markdown.compose.elements.MarkdownTableHeader
import com.mikepenz.markdown.compose.elements.MarkdownTableRow
import com.mikepenz.markdown.model.markdownDimens
import androidx.compose.ui.text.style.TextOverflow
import com.rectime.mobile.ui.theme.AppTheme
import io.ktor.http.Url

// 単独改行は段落内として扱い、強制改行は行末の空白2個など標準構文に従う。
private val appMarkdownAnnotator = markdownAnnotator(
    config = markdownAnnotatorConfig(eolAsNewLine = false),
)
private val MarkdownBlockCornerSize = 12.dp

/** APIの本文をそのまま解釈する。保存形式は変更せず、画像は共通キャッシュ・ビューアを使う。 */
@Composable
fun AppMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    imageTitle: String? = null,
    bodyStyle: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
) {
    var selectedImage by remember(content) { mutableStateOf<Pair<String, String?>?>(null) }
    var selectedDocument by remember(content) { mutableStateOf<String?>(null) }
    val originalUriHandler = LocalUriHandler.current
    val attachmentUriHandler = remember(originalUriHandler, content) { object : UriHandler {
        override fun openUri(uri: String) {
            if ((uri.startsWith("https://") || uri.startsWith("http://")) && attachmentKind(uri) == AttachmentKind.PDF) selectedDocument = uri
            else originalUriHandler.openUri(uri)
        }
    } }
    val body = bodyStyle
    val markdownState = rememberMarkdownState(content)
    val markdownBackground = MaterialTheme.colorScheme.onBackground.copy(alpha = .1f)
    val imageTransformer = object : ImageTransformer {
        @Composable
        override fun transform(link: String): ImageData? {
            val url = runCatching { Url(link) }.getOrNull()
            if (url == null || url.host.isBlank() || url.protocol.name !in setOf("https", "http")) return null
            var requested by remember(link) { mutableStateOf(false) }
            val painter = rememberCachedImagePainter(link, requested)
            return ImageData(
                painter = painter,
                contentDescription = imageTitle ?: "説明画像",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 360.dp)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow(clipBounds = false)
                        val height = coordinates.findRootCoordinates().size.height
                        // 表示後は保持し、スクロール外で画像の高さが変わるのを防ぐ。
                        if (bounds.bottom > -160f && bounds.top < height + 160f) requested = true
                    }
                    .clickable(onClickLabel = "画像を拡大") {
                        selectedImage = link to imageTitle
                    },
            )
        }

        @Composable
        override fun intrinsicSize(painter: Painter): Size {
            if (painter is AsyncImagePainter) {
                val state by painter.state.collectAsState()
                return state.painter?.intrinsicSize ?: Size.Unspecified
            }
            return painter.intrinsicSize
        }
    }
    CompositionLocalProvider(LocalUriHandler provides attachmentUriHandler) {
        SelectionContainer {
            Markdown(
                markdownState = markdownState,
                modifier = modifier.fillMaxWidth(),
                colors = markdownColor(
                    text = AppTheme.colors.textDetailsScreenBody,
                    codeBackground = markdownBackground,
                    inlineCodeBackground = markdownBackground,
                    tableBackground = markdownBackground,
                ),
                dimens = markdownDimens(
                    tableCornerSize = MarkdownBlockCornerSize,
                    codeBackgroundCornerSize = MarkdownBlockCornerSize,
                    tableCellPadding = 12.dp,
                ),
                components = markdownComponents(checkbox = { model ->
                    // 表のカスタマイズ時も、Materialの閲覧専用チェックボックスを維持する。
                    MarkdownCheckBox(model.content, model.node, model.typography.text,
                        checkedIndicator = { checked, indicatorModifier ->
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                modifier = indicatorModifier.semantics {
                                    role = Role.Checkbox
                                    stateDescription = if (checked) "チェック済み" else "未チェック"
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = AppTheme.colors.themeColorSecond,
                                    checkmarkColor = AppTheme.colors.textThemeColorSecond,
                                ),
                            )
                        },
                    )
                }, table = { model ->
                    // 背景だけでなくスクロールする内容も同じ角丸で切り抜く。
                    Box(Modifier.clip(RoundedCornerShape(MarkdownBlockCornerSize))) {
                        MarkdownTable(model.content, model.node, model.typography.table,
                            headerBlock = { text, node, width, style ->
                                MarkdownTableHeader(text, node, width, style, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
                            },
                            rowBlock = { text, node, width, style ->
                                MarkdownTableRow(text, node, width, style, maxLines = Int.MAX_VALUE, overflow = TextOverflow.Clip)
                            },
                        )
                    }
                }),
                typography = markdownTypography(
                    text = body, paragraph = body, ordered = body, bullet = body, list = body,
                    h1 = body.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
                    h2 = body.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
                    h3 = body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                ),
                imageTransformer = imageTransformer,
                annotator = appMarkdownAnnotator,
                extendedSpans = markdownExtendedSpans {
                    remember { ExtendedSpans(RoundedCornerSpanPainter(cornerRadius = 4.sp)) }
                },
                // 解釈中や解釈不能でも保存済み本文を消さない。
                loading = { Text(content, color = AppTheme.colors.textDetailsScreenBody, style = body) },
                error = { Text(content, color = AppTheme.colors.textDetailsScreenBody, style = body) },
            )
        }
    }
    selectedDocument?.let { AttachmentViewerDialog(it) { selectedDocument = null } }
    selectedImage?.let { (url, title) ->
        ImageViewerDialog(
            imageUrl = url,
            title = title,
            contentDescription = title ?: "説明画像",
            onDismiss = { selectedImage = null },
        )
    }
}
