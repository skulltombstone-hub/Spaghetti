package net.perfectdreams.butterscotch.android

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import net.perfectdreams.butterscotch.android.library.GameEntry
import java.io.File

class HtmlGameActivity : ComponentActivity() {
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on while the HTML runtime is active.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        val gameIdAsString = intent.getStringExtra(EXTRA_GAME_ID)
        if (gameIdAsString == null) {
            finish()
            return
        }

        val gameId = runCatching { java.util.UUID.fromString(gameIdAsString) }.getOrNull()
        if (gameId == null) {
            finish()
            return
        }

        val gameLibrary = Libraries.loadGameLibrary(this.applicationContext)
        val layoutLibrary = Libraries.loadLayoutLibrary(this.applicationContext)

        val entry = gameLibrary.findById(gameId)
        if (entry == null) {
            finish()
            return
        }

        val htmlType = entry.gameType as? GameEntry.GameType.Html
        if (htmlType == null) {
            finish()
            return
        }

        val startUrl = htmlType.sourceUrl ?: gameLibrary.wadPath(entry).toURI().toString()
        val title = entry.title

        setContent {
            MaterialTheme {
                HtmlGameScreen(
                    title = title,
                    startUrl = startUrl,
                    portraitLayoutId = entry.portraitLayout.toString(),
                    landscapeLayoutId = entry.landscapeLayout.toString(),
                    onEditControls = {
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                action = MainActivity.ACTION_OPEN_LAYOUT_MANAGER
                            }
                        )
                    },
                    onExit = {
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_GAME_ID = "extra_game_id"
    }
}

@Composable
private fun HtmlGameScreen(
    title: String,
    startUrl: String,
    portraitLayoutId: String,
    landscapeLayoutId: String,
    onEditControls: () -> Unit,
    onExit: () -> Unit
) {
    val context = LocalContext.current
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val normalizedStartUrl = remember(startUrl) {
        normalizeHtmlUrl(startUrl)
    }

    BackHandler(enabled = true) {
        val currentWebView = webViewState.value
        if (currentWebView != null && currentWebView.canGoBack()) {
            currentWebView.goBack()
        } else {
            showMenu = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewState.value?.apply {
                stopLoading()
                loadUrl("about:blank")
                removeAllViews()
                destroy()
            }
            webViewState.value = null
        }
    }

    LaunchedEffect(normalizedStartUrl) {
        webViewState.value?.loadUrl(normalizedStartUrl)
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(
            modifier = Modifier.weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        javaScriptCanOpenWindowsAutomatically = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        builtInZoomControls = false
                        displayZoomControls = false

                        allowFileAccess = true
                        allowContentAccess = true

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                            allowFileAccessFromFileURLs = true
                            allowUniversalAccessFromFileURLs = true
                        }

                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return false
                            return shouldOverrideHtmlNavigation(uri.scheme, uri.toString(), context)
                        }

                        @Suppress("DEPRECATION")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            if (url == null) return false
                            val scheme = runCatching { android.net.Uri.parse(url).scheme }.getOrNull()
                            return shouldOverrideHtmlNavigation(scheme, url, context)
                        }
                    }

                    webViewState.value = this
                    loadUrl(normalizedStartUrl)
                }
            }
        )
    }

    if (showMenu) {
        AlertDialog(
            onDismissRequest = { showMenu = false },
            title = { Text(title) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("HTML game")
                    Text("Portrait layout: $portraitLayoutId")
                    Text("Landscape layout: $landscapeLayoutId")

                    Button(
                        onClick = {
                            showMenu = false
                            onEditControls()
                        }
                    ) {
                        Text("Edit Controls")
                    }

                    Button(
                        onClick = {
                            showMenu = false
                            webViewState.value?.reload()
                        }
                    ) {
                        Text("Reload")
                    }

                    Button(
                        onClick = {
                            showMenu = false
                            onExit()
                        }
                    ) {
                        Text("Exit Game")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {}
        )
    }
}

private fun shouldOverrideHtmlNavigation(
    scheme: String?,
    url: String,
    context: android.content.Context
): Boolean {
    return when (scheme?.lowercase()) {
        "http", "https", "file", "content" -> false
        else -> {
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                )
            }
            true
        }
    }
}

private fun normalizeHtmlUrl(raw: String): String {
    val trimmed = raw.trim()

    return when {
        trimmed.startsWith("http://", ignoreCase = true) -> trimmed
        trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        trimmed.startsWith("file://", ignoreCase = true) -> trimmed
        trimmed.startsWith("content://", ignoreCase = true) -> trimmed
        else -> File(trimmed).toURI().toString()
    }
}
