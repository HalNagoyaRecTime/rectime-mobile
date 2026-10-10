package com.rectime.mobile.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import coil3.compose.AsyncImagePainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mikepenz.markdown.model.rememberMarkdownState
import com.mikepenz.markdown.model.ImageData
import com.mikepenz.markdown.model.ImageTransformer
import com.mikepenz.markdown.model.markdownAnnotator
import com.mikepenz.markdown.model.markdownAnnotatorConfig
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.rectime.mobile.ui.theme.AppTheme
import io.ktor.http.Url

// 単独改行は段落内として扱い、強制改行は行末の空白2個など標準構文に従う。
private val appMarkdownAnnotator = markdownAnnotator(
    config = markdownAnnotatorConfig(eolAsNewLine = false),
)

/** APIの本文をそのまま解釈する。保存形式は変更せず、画像は共通キャッシュ・ビューアを使う。 */
@Composable
fun AppMarkdown(
    content: String,
    modifier: Modifier = Modifier,
    imageTitle: String? = null,
    bodyStyle: TextStyle = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
) {
    var selectedImage by remember(content) { mutableStateOf<Pair<String, String?>?>(null) }
    val body = bodyStyle
    val markdownState = rememberMarkdownState(content)
    val imageTransformer = object : ImageTransformer {
        @Composable
        override fun transform(link: String): ImageData? {
            val url = runCatching { Url(link) }.getOrNull()
            if (url == null || url.host.isBlank() || url.protocol.name !in setOf("https", "http")) return null
            return ImageData(
                painter = rememberCachedImagePainter(link),
                contentDescription = imageTitle ?: "説明画像",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp, max = 360.dp)
                    .clickable(onClickLabel = "画像を拡大") { selectedImage = link to imageTitle },
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
    Markdown(
        markdownState = markdownState,
        modifier = modifier.fillMaxWidth(),
        colors = markdownColor(text = AppTheme.colors.textDetailsScreenBody),
        typography = markdownTypography(
            text = body, paragraph = body, ordered = body, bullet = body, list = body,
            h1 = body.copy(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            h2 = body.copy(fontSize = 22.sp, fontWeight = FontWeight.Bold),
            h3 = body.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
        ),
        imageTransformer = imageTransformer,
        annotator = appMarkdownAnnotator,
        // 解釈中や解釈不能でも保存済み本文を消さない。
        loading = { Text(content, color = AppTheme.colors.textDetailsScreenBody, style = body) },
        error = { Text(content, color = AppTheme.colors.textDetailsScreenBody, style = body) },
    )
    selectedImage?.let { (url, title) ->
        ImageViewerDialog(
            imageUrl = url,
            title = title,
            contentDescription = title ?: "説明画像",
            onDismiss = { selectedImage = null },
        )
    }
}
