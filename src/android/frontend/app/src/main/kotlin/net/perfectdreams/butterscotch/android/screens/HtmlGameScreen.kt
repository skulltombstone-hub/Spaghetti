package net.perfectdreams.butterscotch.android.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.viewinterop.AndroidViewBinding
import java.io.File

/**
 * HTML runtime screen.
 *
 * This is intentionally separate from the native GameMaker runner.
 * It is a first usable step for HTML imports and HTML-based games/sites.
 *
 * The screen:
 * - loads a remote URL or a local file URL
 * - keeps JavaScript and local storage enabled
 * - intercepts back presses
 * - shows a small native menu when the user is at the top-level page
 *
 * The menu is intentionally minimal here:
 * - Edit controls (hook for the same control-layout editor used by the native app)
 * - Reload
 * - Exit game
 *
 * This file is designed to be wired later from the import flow and library flow.
 */
@Composable
fun HtmlGameScreen(
    startUrl: String,
    title: String? = null,
    controlsLayoutLabel: String? = null,
    onEditControls: (() -> Unit)? = null,
    onExit: () -> Unit,
) {
    val context = LocalContext.current

    val normalizedStartUrl = remember(startUrl) {
        normalizeHtmlUrl(startUrl)
    }

    val webView = remember {
        createHtmlWebView(
            context = context,
            onOpenExternal = { uriString ->
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW).apply {
                            data = android.net.Uri.parse(uriString)
                        }
                    )
                }
            }
        )
    }

    var showMenu by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            showMenu = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.removeAllViews()
            webView.destroy()
        }
    }

    LaunchedEffect(normalizedStartUrl) {
        webView.loadUrl(normalizedStartUrl)
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize()
        )

        if (showMenu) {
            HtmlExitMenuDialog(
                title = title,
                controlsLayoutLabel = controlsLayoutLabel,
                canEditControls = onEditControls != null,
                onEditControls = {
                    showMenu = false
                    onEditControls?.invoke()
                },
                onReload = {
                    showMenu = false
                    webView.reload()
                },
                onExit = {
                    showMenu = false
                    onExit()
                },
                onDismiss = {
                    showMenu = false
                }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createHtmlWebView(
    context: android.content.Context,
    onOpenExternal: (String) -> Unit
): WebView {
    return WebView(context).apply {
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

            // HTML bundles often need sibling file access for CSS/JS/assets.
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
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val uri = request?.url ?: return false
                return shouldOverrideHtmlNavigation(uri.scheme, uri.toString(), onOpenExternal)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                url: String?
            ): Boolean {
                if (url == null) return false
                val scheme = runCatching { android.net.Uri.parse(url).scheme }.getOrNull()
                return shouldOverrideHtmlNavigation(scheme, url, onOpenExternal)
            }
        }
    }
}

private fun shouldOverrideHtmlNavigation(
    scheme: String?,
    url: String,
    onOpenExternal: (String) -> Unit
): Boolean {
    return when (scheme?.lowercase()) {
        "http", "https" -> false
        "file", "content" -> false
        null -> false
        else -> {
            onOpenExternal(url)
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

@Composable
private fun HtmlExitMenuDialog(
    title: String?,
    controlsLayoutLabel: String?,
    canEditControls: Boolean,
    onEditControls: () -> Unit,
    onReload: () -> Unit,
    onExit: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(title ?: "HTML Game")
        },
        text = {
            val layoutLabel = controlsLayoutLabel ?: "Current control layout"
            Text(
                if (canEditControls) {
                    "$layoutLabel\n\nChoose what to do next."
                } else {
                    "$layoutLabel\n\nControl editing is not available for this HTML session."
                }
            )
        },
        confirmButton = {
            if (canEditControls) {
                Button(onClick = onEditControls) {
                    Text("Edit Controls")
                }
            }
        },
        dismissButton = {
            Button(onClick = onReload) {
                Text("Reload")
            }
        },
        icon = null
    )

    // Separate exit action so the destructive option stays obvious.
    // Kept outside the dialog's built-in slots so we can keep the button set minimal and readable.
    // In the future this can be replaced by a fuller in-game menu component.
    HtmlExitButtonRow(
        onExit = onExit
    )
}

@Composable
private fun HtmlExitButtonRow(
    onExit: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { },
        title = { Text(" ") },
        text = { Text(" ") },
        confirmButton = {
            Button(onClick = onExit) {
                Text("Exit Game")
            }
        },
        dismissButton = {
            Button(onClick = { }) {
                Text(" ")
            }
        }
    )
}
