package cn.ahlib.reservation.ui

import android.graphics.Typeface
import android.text.Html
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.ImageSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import android.util.Base64
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import coil3.asDrawable
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.crossfade
import java.io.ByteArrayOutputStream
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun RoomHtmlContent(
    html: String,
    imageContentDescription: String,
    modifier: Modifier = Modifier,
    imageModifier: Modifier = Modifier,
    imageShareName: String? = null,
    textStyle: TextStyle = MaterialTheme.typography.bodyMedium,
) {
    val content by produceState<ParsedHtmlContent?>(initialValue = null, html) {
        value = withContext(Dispatchers.Default) {
            parseHtmlContent(html)
        }
    }
    val parsedContent = content ?: return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (parsedContent.text.text.isNotBlank()) {
            Text(
                text = parsedContent.text,
                style = textStyle,
            )
        }

        parsedContent.imageUrls.forEach { imageUrl ->
            RoomContentImage(
                imageUrl = imageUrl,
                contentDescription = imageContentDescription,
                shareName = imageShareName,
                modifier = imageModifier,
            )
        }
    }
}

@Composable
private fun RoomContentImage(
    imageUrl: String,
    contentDescription: String,
    shareName: String?,
    modifier: Modifier = Modifier,
) {
    val isNetworkImage = imageUrl.startsWith("http", ignoreCase = true)
    var retryAttempt by remember(imageUrl) { mutableIntStateOf(0) }
    var state by remember(imageUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val shape = RoundedCornerShape(12.dp)
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = LocalResources.current
    val imageModel by produceState<Any?>(
        initialValue = if (isNetworkImage) imageUrl else null,
        imageUrl,
    ) {
        if (!isNetworkImage) {
            value = withContext(Dispatchers.Default) {
                imageUrl.toCoilModel()
            }
        }
    }
    val request = remember(context, imageModel, imageUrl, retryAttempt) {
        val model = imageModel
        when {
            isNetworkImage -> buildLibraryImageRequest(
                context = context,
                url = imageUrl,
                retryAttempt = retryAttempt,
            )

            model != null -> ImageRequest.Builder(context)
                .data(model)
                .crossfade(true)
                .allowHardware(false)
                .build()

            else -> null
        }
    }

    val isErrorState = state is AsyncImagePainter.State.Error
    LaunchedEffect(isErrorState, isNetworkImage, retryAttempt) {
        if (
            isNetworkImage &&
            isErrorState &&
            retryAttempt < MAX_LIBRARY_IMAGE_AUTO_RETRIES
        ) {
            delay(350)
            state = AsyncImagePainter.State.Empty
            retryAttempt += 1
        }
    }
    val shareDrawable =
        (state as? AsyncImagePainter.State.Success)
            ?.result
            ?.image
            ?.asDrawable(resources)

    ShareableRoomImageBox(
        drawable = shareDrawable,
        shareName = shareName,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = 420.dp)
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        if (request != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier
                    .matchParentSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit,
                onState = { state = it },
            )
        }

        when (state) {
            AsyncImagePainter.State.Empty,
            is AsyncImagePainter.State.Loading,
            -> LoadingIndicator(modifier = Modifier.padding(24.dp))

            is AsyncImagePainter.State.Error -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isNetworkImage) {
                        TextButton(
                            onClick = {
                                state = AsyncImagePainter.State.Empty
                                retryAttempt += 1
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                            )
                            Text(stringResource(cn.ahlib.reservation.R.string.retry))
                        }
                    }
                }
            }

            is AsyncImagePainter.State.Success -> Unit
        }
    }
}

private data class ParsedHtmlContent(
    val text: AnnotatedString,
    val imageUrls: List<String>,
)

private fun parseHtmlContent(html: String): ParsedHtmlContent {
    val imageSources = mutableListOf<String>()
    val transparentImage = android.graphics.Color.TRANSPARENT.toDrawable().apply {
        setBounds(0, 0, 1, 1)
    }
    val imageGetter = Html.ImageGetter { source ->
        source?.let(imageSources::add)
        transparentImage
    }
    val spanned = Html.fromHtml(
        html,
        Html.FROM_HTML_MODE_LEGACY,
        imageGetter,
        null,
    )

    return ParsedHtmlContent(
        text = spanned.toReadableAnnotatedString(),
        imageUrls = imageSources.toSafeImageUrls(),
    )
}

private fun Spanned.toReadableAnnotatedString(): AnnotatedString {
    val text = SpannableStringBuilder(this)

    text.getSpans(0, text.length, ImageSpan::class.java)
        .sortedByDescending(text::getSpanStart)
        .forEach { span ->
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            if (start in 0 until end && end <= text.length) {
                text.delete(start, end)
            }
        }

    text.getSpans(0, text.length, BulletSpan::class.java)
        .map(text::getSpanStart)
        .filter { it in 0..text.length }
        .distinct()
        .sortedDescending()
        .forEach { start ->
            text.insert(start, "\u2022 ")
        }

    val firstContent = text.indexOfFirst { !it.isWhitespace() }
    if (firstContent < 0) {
        return AnnotatedString("")
    }
    val lastContent = text.indexOfLast { !it.isWhitespace() }
    if (lastContent + 1 < text.length) {
        text.delete(lastContent + 1, text.length)
    }
    if (firstContent > 0) {
        text.delete(0, firstContent)
    }

    val builder = AnnotatedString.Builder(text.toString())

    text.getSpans(0, text.length, StyleSpan::class.java).forEach { span ->
        val style = when (span.style) {
            Typeface.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
            Typeface.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
            Typeface.BOLD_ITALIC -> SpanStyle(
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
            )

            else -> null
        }
        if (style != null) {
            builder.addSpanSafely(text, span, style)
        }
    }

    text.getSpans(0, text.length, UnderlineSpan::class.java).forEach { span ->
        builder.addSpanSafely(
            text,
            span,
            SpanStyle(textDecoration = TextDecoration.Underline),
        )
    }
    text.getSpans(0, text.length, StrikethroughSpan::class.java).forEach { span ->
        builder.addSpanSafely(
            text,
            span,
            SpanStyle(textDecoration = TextDecoration.LineThrough),
        )
    }
    text.getSpans(0, text.length, ForegroundColorSpan::class.java).forEach { span ->
        builder.addSpanSafely(text, span, SpanStyle(color = Color(span.foregroundColor)))
    }
    text.getSpans(0, text.length, BackgroundColorSpan::class.java).forEach { span ->
        builder.addSpanSafely(
            text,
            span,
            SpanStyle(background = Color(span.backgroundColor)),
        )
    }
    text.getSpans(0, text.length, RelativeSizeSpan::class.java).forEach { span ->
        builder.addSpanSafely(text, span, SpanStyle(fontSize = span.sizeChange.em))
    }
    text.getSpans(0, text.length, SuperscriptSpan::class.java).forEach { span ->
        builder.addSpanSafely(
            text,
            span,
            SpanStyle(baselineShift = BaselineShift.Superscript),
        )
    }
    text.getSpans(0, text.length, SubscriptSpan::class.java).forEach { span ->
        builder.addSpanSafely(
            text,
            span,
            SpanStyle(baselineShift = BaselineShift.Subscript),
        )
    }
    text.getSpans(0, text.length, TypefaceSpan::class.java).forEach { span ->
        val fontFamily = when (span.family?.lowercase(Locale.ROOT)) {
            "monospace" -> FontFamily.Monospace
            "serif" -> FontFamily.Serif
            "sans", "sans-serif" -> FontFamily.SansSerif
            "cursive" -> FontFamily.Cursive
            else -> null
        }
        if (fontFamily != null) {
            builder.addSpanSafely(text, span, SpanStyle(fontFamily = fontFamily))
        }
    }
    text.getSpans(0, text.length, URLSpan::class.java).forEach { span ->
        val start = text.getSpanStart(span)
        val end = text.getSpanEnd(span)
        if (start >= 0 && end > start && end <= text.length) {
            builder.addStyle(
                SpanStyle(textDecoration = TextDecoration.Underline),
                start,
                end,
            )
            builder.addStringAnnotation(
                tag = "URL",
                annotation = span.url.orEmpty(),
                start = start,
                end = end,
            )
        }
    }

    return builder.toAnnotatedString()
}

private fun AnnotatedString.Builder.addSpanSafely(
    text: Spanned,
    span: Any,
    style: SpanStyle,
) {
    val start = text.getSpanStart(span)
    val end = text.getSpanEnd(span)
    if (start >= 0 && end > start && end <= text.length) {
        addStyle(style, start, end)
    }
}

private fun List<String>.toSafeImageUrls(): List<String> {
    val seen = mutableSetOf<String>()
    return mapNotNull(::normalizeImageUrl)
        .filter(seen::add)
}

private fun normalizeImageUrl(source: String): String? {
    val trimmed = source.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isISOControl)) {
        return null
    }

    val candidate = if (trimmed.startsWith("//")) {
        "https:$trimmed"
    } else {
        trimmed
    }
    val uri = runCatching { candidate.toUri() }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
    val normalizedSchemeUrl = uri.buildUpon()
        .scheme(scheme)
        .build()
        .toString()

    return when (scheme) {
        "http", "https" -> normalizedSchemeUrl.takeIf {
            uri.isHierarchical && !uri.host.isNullOrBlank()
        }

        "data" -> normalizedSchemeUrl.takeIf(::isImageDataUrl)
        else -> null
    }
}

private fun isImageDataUrl(url: String): Boolean {
    if (url.length > MAX_DATA_IMAGE_URL_LENGTH) {
        return false
    }

    val commaIndex = url.indexOf(',')
    if (commaIndex <= "data:".length) {
        return false
    }

    val metadata = url
        .substring("data:".length, commaIndex)
        .substringBefore(';')
        .trim()
        .lowercase(Locale.ROOT)
    return metadata.startsWith("image/") && metadata.length > "image/".length
}

internal fun String.toCoilModel(): Any {
    if (!startsWith("data:", ignoreCase = true)) {
        return this
    }

    if (length > MAX_DATA_IMAGE_URL_LENGTH) {
        return ByteArray(0)
    }
    return decodeDataImageUrl(this) ?: ByteArray(0)
}

private fun decodeDataImageUrl(url: String): ByteArray? {
    val commaIndex = url.indexOf(',')
    if (commaIndex < 0) {
        return null
    }

    val metadata = url.substring("data:".length, commaIndex)
    val encodedData = url.substring(commaIndex + 1)
    return runCatching {
        if (metadata.split(';').any { it.equals("base64", ignoreCase = true) }) {
            Base64.decode(decodePercentEncodedBytes(encodedData), Base64.DEFAULT)
        } else {
            decodePercentEncodedBytes(encodedData)
        }
    }.getOrNull()
}

private fun decodePercentEncodedBytes(value: String): ByteArray {
    val output = ByteArrayOutputStream(value.length)
    var index = 0

    while (index < value.length) {
        val percentIndex = value.indexOf('%', index).let { found ->
            if (found < 0) value.length else found
        }
        if (percentIndex > index) {
            output.write(
                value.substring(index, percentIndex).toByteArray(Charsets.UTF_8),
            )
            index = percentIndex
        }
        if (index < value.length) {
            require(index + 2 < value.length)
            val high = value[index + 1].digitToIntOrNull(16)
            val low = value[index + 2].digitToIntOrNull(16)
            require(high != null && low != null)
            output.write((high shl 4) or low)
            index += 3
        }
    }

    return output.toByteArray()
}

private const val MAX_DATA_IMAGE_URL_LENGTH = 8 * 1024 * 1024
