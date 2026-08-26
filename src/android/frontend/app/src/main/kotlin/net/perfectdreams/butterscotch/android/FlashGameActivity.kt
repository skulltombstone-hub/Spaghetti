package net.perfectdreams.butterscotch.android

import android.annotation.SuppressLint
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameLibrary
import net.perfectdreams.butterscotch.android.theme.ButterscotchAndroidTheme
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Activity dedicated exclusively to Adobe Flash / Ruffle games.
 *
 * Architecture:
 *
 * FlashGameActivity
 *       |
 *       +-- WebView
 *              |
 *              +-- virtual local HTTP origin
 *                       |
 *                       +-- /ruffle/*
 *                       |      |
 *                       |      +-- ruffle.js
 *                       |      +-- core*.js
 *                       |      +-- *.wasm
 *                       |
 *                       +-- /games/*
 *                              |
 *                              +-- imported .swf
 *
 * The Ruffle self-hosted package is kept completely intact.
 * We do not rename, merge or select its WASM files manually.
 *
 * The WebView simply exposes the package through one coherent origin.
 */
class FlashGameActivity : ComponentActivity() {

    companion object {
        const val EXTRA_GAME_ID = "game_id"

        private const val TAG = "FlashGameActivity"

        /**
         * Synthetic origin used exclusively by this WebView.
         *
         * Nothing is actually served by an HTTP server. All requests to this
         * origin are intercepted by FlashWebView and resolved directly from
         * APK assets or the app's private game directory.
         */
        private const val INTERNAL_HOST = "spaghetti.local"
        private const val INTERNAL_BASE_URL = "http://$INTERNAL_HOST/"

        private const val RUFFLE_URL =
            "${INTERNAL_BASE_URL}ruffle/"

        private const val GAMES_URL =
            "${INTERNAL_BASE_URL}games/"
    }

    private var webView: WebView? = null

    private val pressedKeys = FlashKeyState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        hideSystemBars()

        val gameLibrary =
            Libraries.loadGameLibrary(applicationContext)

        val gameId = intent
            .getStringExtra(EXTRA_GAME_ID)
            ?.let { value ->
                runCatching {
                    UUID.fromString(value)
                }.getOrNull()
            }

        if (gameId == null) {
            Log.e(TAG, "Missing or invalid game_id")
            finish()
            return
        }

        val entry = gameLibrary.findById(gameId)

        if (
            entry == null ||
            entry.gameType !is GameEntry.GameType.Flash
        ) {
            Log.e(TAG, "Game $gameId is missing or is not a Flash game")
            finish()
            return
        }

        val gameType =
            entry.gameType as GameEntry.GameType.Flash

        val gameFile =
            File(
                gameLibrary.bundleDir(entry),
                gameType.filename
            )

        if (!gameFile.exists() || !gameFile.isFile) {
            Log.e(
                TAG,
                "Flash game file does not exist: ${gameFile.absolutePath}"
            )

            finish()
            return
        }

        setContent {
            ButterscotchAndroidTheme {
                FlashGameContent(
                    gameFile = gameFile,
                    gameBundleDirectory = gameLibrary.bundleDir(entry),
                    onWebViewCreated = { view ->
                        webView = view
                    },
                    onLoadError = { message ->
                        Log.e(TAG, message)
                    },
                    onExit = {
                        pressedKeys.releaseAll(webView)
                        finish()
                    }
                )
            }
        }
    }

    override fun dispatchKeyEvent(
        event: KeyEvent
    ): Boolean {

        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            return super.dispatchKeyEvent(event)
        }

        val view = webView
            ?: return super.dispatchKeyEvent(event)

        when (event.action) {

            KeyEvent.ACTION_DOWN -> {
                pressedKeys.press(
                    webView = view,
                    keyCode = event.keyCode,
                    repeat = event.repeatCount > 0
                )

                return true
            }

            KeyEvent.ACTION_UP -> {
                pressedKeys.release(
                    webView = view,
                    keyCode = event.keyCode
                )

                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        pressedKeys.releaseAll(webView)

        webView?.onPause()

        super.onPause()
    }

    override fun onResume() {
        super.onResume()

        hideSystemBars()

        webView?.onResume()
    }

    override fun onDestroy() {
        pressedKeys.releaseAll(webView)

        webView?.apply {
            stopLoading()
            loadUrl("about:blank")
            clearHistory()
            removeAllViews()
            destroy()
        }

        webView = null

        super.onDestroy()
    }

    override fun onWindowFocusChanged(
        hasFocus: Boolean
    ) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            hideSystemBars()
        }
    }

    private fun hideSystemBars() {
        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }
}

/**
 * Compose host for the Flash WebView.
 */
@Composable
private fun FlashGameContent(
    gameFile: File,
    gameBundleDirectory: File,
    onWebViewCreated: (WebView) -> Unit,
    onLoadError: (String) -> Unit,
    onExit: () -> Unit
) {
    var loadError by remember(
        gameFile.absolutePath
    ) {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(Unit) {
        onDispose {
            // The Activity owns actual WebView destruction.
        }
    }

    BackHandler {
        onExit()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        FlashWebView(
            gameFile = gameFile,
            gameBundleDirectory = gameBundleDirectory,
            onWebViewCreated = onWebViewCreated,
            onLoadError = {
                loadError = it
                onLoadError(it)
            }
        )

        if (loadError != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Ruffle failed to load the Flash game.",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = loadError ?: "Unknown error",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

/**
 * WebView that hosts Ruffle.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FlashWebView(
    gameFile: File,
    gameBundleDirectory: File,
    onWebViewCreated: (WebView) -> Unit,
    onLoadError: (String) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),

        factory = { context ->

            WebView(context).apply {

                onWebViewCreated(this)

                setBackgroundColor(
                    AndroidColor.BLACK
                )

                isFocusable = true
                isFocusableInTouchMode = true
                requestFocus()

                settings.apply {
                    javaScriptEnabled = true

                    domStorageEnabled = true

                    databaseEnabled = true

                    /**
                     * We intentionally do not use file:// URLs.
                     *
                     * Ruffle and the imported game are exposed through the
                     * synthetic internal origin handled below.
                     */
                    allowFileAccess = false
                    allowContentAccess = false

                    allowFileAccessFromFileURLs = false
                    allowUniversalAccessFromFileURLs = false

                    javaScriptCanOpenWindowsAutomatically =
                        true

                    mediaPlaybackRequiresUserGesture =
                        false

                    useWideViewPort = true

                    loadWithOverviewMode = false

                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)

                    cacheMode =
                        WebSettings.LOAD_DEFAULT

                    userAgentString =
                        "$userAgentString SpaghettiFlashRunner/2.0"
                }

                webChromeClient =
                    object : WebChromeClient() {

                        override fun onConsoleMessage(
                            consoleMessage: ConsoleMessage
                        ): Boolean {
                            Log.d(
                                "Ruffle",
                                "${consoleMessage.message()} " +
                                    "(${consoleMessage.sourceId()}:" +
                                    "${consoleMessage.lineNumber()})"
                            )

                            return true
                        }
                    }

                webViewClient =
                    object : WebViewClient() {

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val url =
                                request.url

                            if (
                                !url.host.equals(
                                    INTERNAL_HOST,
                                    ignoreCase = true
                                )
                            ) {
                                return super.shouldInterceptRequest(
                                    view,
                                    request
                                )
                            }

                            return interceptInternalRequest(
                                context = context,
                                request = request,
                                gameBundleDirectory = gameBundleDirectory
                            )
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            /**
                             * Internal Ruffle/game URLs are allowed to load.
                             */
                            if (
                                request.url.host.equals(
                                    INTERNAL_HOST,
                                    ignoreCase = true
                                )
                            ) {
                                return false
                            }

                            /**
                             * Do not let a Flash game replace the whole
                             * launcher Activity with an arbitrary location.
                             *
                             * Ruffle itself handles Flash URL operations.
                             */
                            return true
                        }

                        override fun onPageFinished(
                            view: WebView,
                            url: String?
                        ) {
                            super.onPageFinished(
                                view,
                                url
                            )

                            injectFlashViewportFixes(
                                view
                            )
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: WebResourceError
                        ) {
                            super.onReceivedError(
                                view,
                                request,
                                error
                            )

                            if (request.isForMainFrame) {
                                onLoadError(
                                    error.description?.toString()
                                        ?: "Unknown WebView error"
                                )
                            }
                        }
                    }

                systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                        View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_LAYOUT_STABLE

                loadDataWithBaseURL(
                    INTERNAL_BASE_URL,
                    createRuffleHtml(
                        gameFile.name
                    ),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
    )
}

/**
 * Intercepts the synthetic local web origin.
 *
 * /ruffle/* -> APK assets/ruffle/*
 * /games/*  -> private imported game bundle
 */
private fun interceptInternalRequest(
    context: android.content.Context,
    request: WebResourceRequest,
    gameBundleDirectory: File
): WebResourceResponse {
    val path =
        request.url.path ?: "/"

    return when {

        path.startsWith("/ruffle/") -> {
            serveRuffleAsset(
                context = context,
                path = path
            )
        }

        path.startsWith("/games/") -> {
            serveGameFile(
                rootDirectory = gameBundleDirectory,
                path = path.removePrefix("/games/")
            )
        }

        else -> {
            textResponse(
                statusCode = 404,
                reason = "Not Found",
                text = "Unknown Spaghetti internal resource."
            )
        }
    }
}

/**
 * Serves one file from the APK's assets/ruffle directory.
 */
private fun serveRuffleAsset(
    context: android.content.Context,
    path: String
): WebResourceResponse {
    val relative =
        path
            .removePrefix("/ruffle/")
            .let {
                URLDecoder.decode(
                    it,
                    StandardCharsets.UTF_8.name()
                )
            }

    if (
        relative.isBlank() ||
        relative.contains("..") ||
        relative.startsWith("/") ||
        relative.contains("\\")
    ) {
        return textResponse(
            statusCode = 403,
            reason = "Forbidden",
            text = "Invalid Ruffle asset path."
        )
    }

    val assetPath =
        "ruffle/$relative"

    return runCatching {
        val input =
            context.assets.open(assetPath)

        binaryResponse(
            mimeType = mimeTypeFor(relative),
            input = input
        )
    }.getOrElse { error ->

        Log.e(
            "Ruffle",
            "Failed to open asset $assetPath",
            error
        )

        textResponse(
            statusCode = 404,
            reason = "Not Found",
            text = "Ruffle asset not found: $relative"
        )
    }
}

/**
 * Serves a file from the imported game's private bundle.
 *
 * Canonical-path validation prevents ../ traversal.
 */
private fun serveGameFile(
    rootDirectory: File,
    path: String
): WebResourceResponse {

    val relative =
        URLDecoder.decode(
            path,
            StandardCharsets.UTF_8.name()
        )

    if (
        relative.isBlank() ||
        relative.startsWith("/") ||
        relative.contains("\\")
    ) {
        return textResponse(
            statusCode = 403,
            reason = "Forbidden",
            text = "Invalid game resource path."
        )
    }

    val root =
        runCatching {
            rootDirectory.canonicalFile
        }.getOrElse {
            return textResponse(
                statusCode = 500,
                reason = "Internal Server Error",
                text = "Could not resolve game bundle root."
            )
        }

    val target =
        runCatching {
            File(
                root,
                relative
            ).canonicalFile
        }.getOrElse {
            return textResponse(
                statusCode = 400,
                reason = "Bad Request",
                text = "Invalid game resource."
            )
        }

    val rootPath =
        root.path + File.separator

    if (
        target.path != root.path &&
        !target.path.startsWith(rootPath)
    ) {
        return textResponse(
            statusCode = 403,
            reason = "Forbidden",
            text = "Game resource escaped the bundle directory."
        )
    }

    if (!target.exists() || !target.isFile) {
        return textResponse(
            statusCode = 404,
            reason = "Not Found",
            text = "Game resource not found."
        )
    }

    return runCatching {
        binaryResponse(
            mimeType = mimeTypeFor(target.name),
            input = target.inputStr
