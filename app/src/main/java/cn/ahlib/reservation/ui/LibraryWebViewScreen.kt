package cn.ahlib.reservation.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import cn.ahlib.reservation.R
import androidx.core.net.toUri

internal const val LIBRARY_RESERVATIONS_URL =
    "https://www.lib.ah.cn/myLibrary?menuIndex=1"
internal const val LIBRARY_WEB_VIEW_CLOSE_TEST_TAG = "library-web-view-close"
internal const val LIBRARY_WEB_VIEW_MENU_TEST_TAG = "library-web-view-menu"
internal const val LIBRARY_WEB_VIEW_REFRESH_TEST_TAG = "library-web-view-refresh"
internal const val LIBRARY_WEB_VIEW_TEST_TAG = "library-web-view"

private val BrowserChrome = Color(0xFFEDEDED)
private val BrowserChromeText = Color(0xFF151515)
private val BrowserChromeSecondary = Color(0xFF777777)

@Composable
fun LibraryWebViewScreen(
    sessionCookies: List<String>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    pageUrl: String = LIBRARY_RESERVATIONS_URL,
) {
    BackHandler(onBack = onClose)
    val webView = remember(pageUrl, sessionCookies) {
        WebViewHolder(pageUrl, sessionCookies)
    }
    Scaffold(
        modifier = modifier,
        containerColor = Color.White,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LibraryWebViewBrowserBar(
                onClose = onClose,
                onRefresh = webView::reload,
            )
        },
    ) { innerPadding ->
        AuthenticatedLibraryWebView(
            webView = webView,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        )
    }
}

@Composable
private fun LibraryWebViewBrowserBar(
    onClose: () -> Unit,
    onRefresh: () -> Unit,
) {
    var menuExpanded by rememberSaveable { mutableStateOf(false) }

    Surface(
        color = BrowserChrome,
        shadowElevation = 1.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(50.dp)
                .padding(horizontal = 8.dp),
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .testTag(LIBRARY_WEB_VIEW_CLOSE_TEST_TAG),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = stringResource(R.string.library_web_view_close),
                    modifier = Modifier.size(24.dp),
                    tint = BrowserChromeText,
                )
            }
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(R.string.library_web_view_title),
                    color = BrowserChromeText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 21.sp,
                )
                Text(
                    text = stringResource(R.string.library_web_view_host),
                    color = BrowserChromeSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp),
            ) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag(LIBRARY_WEB_VIEW_MENU_TEST_TAG),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreHoriz,
                        contentDescription = stringResource(
                            R.string.library_web_view_more_options,
                        ),
                        modifier = Modifier.size(24.dp),
                        tint = BrowserChromeText,
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.library_web_view_refresh)) },
                        onClick = {
                            menuExpanded = false
                            onRefresh()
                        },
                        modifier = Modifier.testTag(LIBRARY_WEB_VIEW_REFRESH_TEST_TAG),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Preview(showSystemUi = true)
@Composable
private fun LibraryWebviewBrowserBar() {
    LibraryWebViewBrowserBar(
        onClose = {},
        onRefresh = {},
    )
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun AuthenticatedLibraryWebView(
    webView: WebViewHolder,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(webView) {
        onDispose {
            webView.value?.stopLoading()
            webView.value?.webViewClient = WebViewClient()
            webView.value?.destroy()
            webView.value = null
        }
    }
    AndroidView(
        factory = { context ->
            webView.create(context)
        },
        modifier = modifier.testTag(LIBRARY_WEB_VIEW_TEST_TAG),
    )
}

private class WebViewHolder(
    private val pageUrl: String,
    private val sessionCookies: List<String>,
) {
    var value: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun create(context: Context): WebView = WebView(context).also { webView ->
        value = webView
        webView.setBackgroundColor(AndroidColor.WHITE)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadsImagesAutomatically = true
            useWideViewPort = true
            loadWithOverviewMode = false
            builtInZoomControls = false
            displayZoomControls = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            safeBrowsingEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        val webViewClient = LibraryWebViewClient(context)
        webView.webViewClient = webViewClient
        webViewClient.installRequestOverride(webView)
        loadWithSessionCookies(webView, pageUrl, sessionCookies)
    }

    fun reload() {
        value?.reload()
    }
}

private class LibraryWebViewClient(
    private val context: Context,
) : WebViewClient() {
    private var injectRequestOverrideOnPageFinished = false

    private val extScript: String by lazy {
        context.resources.openRawResource(R.raw.webview_ext)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    private val requestOverrideScript: String by lazy {
        context.resources.openRawResource(R.raw.webview_request_override)
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }
    }

    fun installRequestOverride(webView: WebView) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(
                webView,
                requestOverrideScript,
                setOf("https://www.lib.ah.cn"),
            )
        } else {
            injectRequestOverrideOnPageFinished = true
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        super.onPageFinished(view, url)
        if (url?.toUri()?.isTrustedLibraryUrl() != true) return

        if (injectRequestOverrideOnPageFinished) {
            view.evaluateJavascript(requestOverrideScript, null)
        }
        view.evaluateJavascript(extScript, null)
    }

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean = !request.url.isTrustedLibraryUrl()
}

private fun Uri.isTrustedLibraryUrl(): Boolean =
    scheme.equals("https", ignoreCase = true) &&
        host?.lowercase()?.let { hostName ->
            hostName == "lib.ah.cn" || hostName.endsWith(".lib.ah.cn")
        } == true

private fun loadWithSessionCookies(
    webView: WebView,
    pageUrl: String,
    sessionCookies: List<String>,
) {
    if (!pageUrl.toUri().isTrustedLibraryUrl()) {
        webView.loadUrl(pageUrl)
        return
    }
    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)
    cookieManager.setAcceptThirdPartyCookies(webView, false)
    cookieManager.removeAllCookies {
        if (sessionCookies.isEmpty()) {
            cookieManager.flush()
            webView.post { webView.loadUrl(pageUrl) }
            return@removeAllCookies
        }
        var pendingCookies = sessionCookies.size
        sessionCookies.forEach { cookie ->
            cookieManager.setCookie(pageUrl, cookie) {
                pendingCookies -= 1
                if (pendingCookies == 0) {
                    cookieManager.flush()
                    webView.post { webView.loadUrl(pageUrl) }
                }
            }
        }
    }
}
