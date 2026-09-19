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
            input = target.inputStream().buffered()
        )
    }.getOrElse { error ->

        Log.e(
            "Ruffle",
            "Failed to open game resource ${target.absolutePath}",
            error
        )

        textResponse(
            statusCode = 500,
            reason = "Internal Server Error",
            text = "Could not open game resource."
        )
    }
}

/**
 * Creates a WebResourceResponse for binary/text resources.
 */
private fun binaryResponse(
    mimeType: String,
    input: java.io.InputStream
): WebResourceResponse {
    val encoding =
        if (
            mimeType.startsWith("text/") ||
            mimeType == "application/javascript" ||
            mimeType == "text/javascript" ||
            mimeType == "application/json"
        ) {
            "UTF-8"
        } else {
            null
        }

    return WebResourceResponse(
        mimeType,
        encoding,
        200,
        "OK",
        mapOf(
            "Cache-Control" to "no-store",
            "Access-Control-Allow-Origin" to "*"
        ),
        input
    )
}

/**
 * Plain-text error response.
 */
private fun textResponse(
    statusCode: Int,
    reason: String,
    text: String
): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        "UTF-8",
        statusCode,
        reason,
        mapOf(
            "Cache-Control" to "no-store",
            "Access-Control-Allow-Origin" to "*"
        ),
        text.byteInputStream(StandardCharsets.UTF_8)
    )
}

/**
 * MIME table for the Ruffle package and imported game files.
 */
private fun mimeTypeFor(
    fileName: String
): String {
    return when (
        fileName
            .substringAfterLast(
                '.',
                ""
            )
            .lowercase()
    ) {
        "js" ->
            "text/javascript"

        "json",
        "map" ->
            "application/json"

        "wasm" ->
            "application/wasm"

        "html",
        "htm" ->
            "text/html"

        "css" ->
            "text/css"

        "svg" ->
            "image/svg+xml"

        "png" ->
            "image/png"

        "jpg",
        "jpeg" ->
            "image/jpeg"

        "gif" ->
            "image/gif"

        "webp" ->
            "image/webp"

        "mp3" ->
            "audio/mpeg"

        "wav" ->
            "audio/wav"

        "ogg" ->
            "audio/ogg"

        "mp4" ->
            "video/mp4"

        "webm" ->
            "video/webm"

        "swf" ->
            "application/x-shockwave-flash"

        else ->
            "application/octet-stream"
    }
}

/**
 * Minimal Ruffle host page.
 *
 * Important:
 * - ruffle.js remains untouched.
 * - publicPath points to the whole Ruffle package directory.
 * - the SWF is presented as a sibling resource of the same synthetic origin.
 */
private fun createRuffleHtml(
    swfFileName: String
): String {
    val encodedSwf =
        Uri.encode(
            swfFileName
        )

    val gameUrl =
        "$GAMES_URL$encodedSwf"

    val safeRuffleUrl =
        RUFFLE_URL
            .replace("\\", "\\\\")
            .replace("'", "\\'")

    val safeGameUrl =
        gameUrl
            .replace("\\", "\\\\")
            .replace("'", "\\'")

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8">

            <meta
                name="viewport"
                content="width=device-width,
                         initial-scale=1.0,
                         maximum-scale=1.0,
                         user-scalable=no,
                         viewport-fit=cover"
            >

            <style>
                html,
                body {
                    width: 100%;
                    height: 100%;
                    margin: 0;
                    padding: 0;
                    overflow: hidden;
                    background: #000;
                }

                body {
                    touch-action: none;
                }

                #ruffle-container {
                    position: fixed;
                    inset: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: #000;
                }
            </style>

            <script>
                window.RufflePlayer =
                    window.RufflePlayer || {};

                window.RufflePlayer.config =
                    window.RufflePlayer.config || {};

                window.RufflePlayer.config.publicPath =
                    '$safeRuffleUrl';

                window.RufflePlayer.config.polyfills =
                    false;
            </script>

            <script
                src="${RUFFLE_URL}ruffle.js">
            </script>

            <script>
                window.addEventListener(
                    "DOMContentLoaded",
                    function () {
                        try {
                            const source =
                                window.RufflePlayer.newest();

                            if (!source) {
                                throw new Error(
                                    "RufflePlayer.newest() returned null."
                                );
                            }

                            const player =
                                source.createPlayer();

                            player.style.width =
                                "100%";

                            player.style.height =
                                "100%";

                            player.style.display =
                                "block";

                            const container =
                                document.getElementById(
                                    "ruffle-container"
                                );

                            if (!container) {
                                throw new Error(
                                    "Ruffle container not found."
                                );
                            }

                            container.appendChild(
                                player
                            );

                            player
                                .ruffle()
                                .load('$safeGameUrl')
                                .catch(function (error) {
                                    console.error(
                                        "Ruffle failed to load SWF:",
                                        error
                                    );
                                });

                        } catch (error) {
                            console.error(
                                "Failed to create Ruffle player:",
                                error
                            );
                        }
                    }
                );
            </script>
        </head>

        <body>
            <div id="ruffle-container"></div>
        </body>
        </html>
    """.trimIndent()
}

/**
 * Keeps the HTML viewport stable after the WebView loads.
 */
private fun injectFlashViewportFixes(
    webView: WebView
) {
    webView.evaluateJavascript(
        """
        (() => {
            const html =
                document.documentElement;

            const body =
                document.body;

            if (html) {
                html.style.width = "100%";
                html.style.height = "100%";
                html.style.margin = "0";
                html.style.padding = "0";
                html.style.overflow = "hidden";
            }

            if (body) {
                body.style.width = "100%";
                body.style.height = "100%";
                body.style.margin = "0";
                body.style.padding = "0";
                body.style.overflow = "hidden";
            }
        })();
        """.trimIndent(),
        null
    )
}

/**
 * Tracks currently pressed Android keys and converts them to DOM keyboard
 * events consumed by the Ruffle/WebView environment.
 */
private class FlashKeyState {

    private val pressedKeys =
        mutableSetOf<Int>()

    fun press(
        webView: WebView,
        keyCode: Int,
        repeat: Boolean
    ) {
        val mapped =
            androidKeyCodeToJavascript(
                keyCode
            )

        if (!repeat) {
            if (!pressedKeys.add(keyCode)) {
                return
            }
        } else {
            if (!pressedKeys.contains(keyCode)) {
                pressedKeys.add(keyCode)
            }
        }

        webView.evaluateJavascript(
            createKeyboardEventScript(
                type = "keydown",
                key = mapped.key,
                code = mapped.code,
                repeat = repeat
            ),
            null
        )
    }

    fun release(
        webView: WebView,
        keyCode: Int
    ) {
        if (!pressedKeys.remove(keyCode)) {
            return
        }

        val mapped =
            androidKeyCodeToJavascript(
                keyCode
            )

        webView.evaluateJavascript(
            createKeyboardEventScript(
                type = "keyup",
                key = mapped.key,
                code = mapped.code,
                repeat = false
            ),
            null
        )
    }

    fun releaseAll(
        webView: WebView?
    ) {
        if (webView == null) {
            pressedKeys.clear()
            return
        }

        val keys =
            pressedKeys.toList()

        for (keyCode in keys) {
            release(
                webView = webView,
                keyCode = keyCode
            )
        }
    }
}

/**
 * Simple DOM keyboard mapping.
 */
private data class JavascriptKey(
    val key: String,
    val code: String
)

private fun androidKeyCodeToJavascript(
    keyCode: Int
): JavascriptKey {

    if (
        keyCode >= KeyEvent.KEYCODE_A &&
        keyCode <= KeyEvent.KEYCODE_Z
    ) {
        val letter =
            ('A'.code +
                (
                    keyCode -
                        KeyEvent.KEYCODE_A
                    )
                ).toChar()

        return JavascriptKey(
            key = letter.lowercase(),
            code = "Key$letter"
        )
    }

    if (
        keyCode >= KeyEvent.KEYCODE_0 &&
        keyCode <= KeyEvent.KEYCODE_9
    ) {
        val digit =
            ('0'.code +
                (
                    keyCode -
                        KeyEvent.KEYCODE_0
                    )
                ).toChar()

        return JavascriptKey(
            key = digit.toString(),
            code = "Digit$digit"
        )
    }

    return when (keyCode) {

        KeyEvent.KEYCODE_DPAD_UP ->
            JavascriptKey("ArrowUp", "ArrowUp")

        KeyEvent.KEYCODE_DPAD_DOWN ->
            JavascriptKey("ArrowDown", "ArrowDown")

        KeyEvent.KEYCODE_DPAD_LEFT ->
            JavascriptKey("ArrowLeft", "ArrowLeft")

        KeyEvent.KEYCODE_DPAD_RIGHT ->
            JavascriptKey("ArrowRight", "ArrowRight")

        KeyEvent.KEYCODE_ENTER ->
            JavascriptKey("Enter", "Enter")

        KeyEvent.KEYCODE_ESCAPE ->
            JavascriptKey("Escape", "Escape")

        KeyEvent.KEYCODE_SPACE ->
            JavascriptKey(" ", "Space")

        KeyEvent.KEYCODE_TAB ->
            JavascriptKey("Tab", "Tab")

        KeyEvent.KEYCODE_DEL ->
            JavascriptKey("Backspace", "Backspace")

        KeyEvent.KEYCODE_FORWARD_DEL ->
            JavascriptKey("Delete", "Delete")

        KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_SHIFT_RIGHT ->
            JavascriptKey("Shift", "ShiftLeft")

        KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT ->
            JavascriptKey("Control", "ControlLeft")

        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_ALT_RIGHT ->
            JavascriptKey("Alt", "AltLeft")

        KeyEvent.KEYCODE_META_LEFT,
        KeyEvent.KEYCODE_META_RIGHT ->
            JavascriptKey("Meta", "MetaLeft")

        KeyEvent.KEYCODE_COMMA ->
            JavascriptKey(",", "Comma")

        KeyEvent.KEYCODE_PERIOD ->
            JavascriptKey(".", "Period")

        KeyEvent.KEYCODE_SLASH ->
            JavascriptKey("/", "Slash")

        KeyEvent.KEYCODE_BACKSLASH ->
            JavascriptKey("\\", "Backslash")

        KeyEvent.KEYCODE_SEMICOLON ->
            JavascriptKey(";", "Semicolon")

        KeyEvent.KEYCODE_APOSTROPHE ->
            JavascriptKey("'", "Quote")

        KeyEvent.KEYCODE_LEFT_BRACKET ->
            JavascriptKey("[", "BracketLeft")

        KeyEvent.KEYCODE_RIGHT_BRACKET ->
            JavascriptKey("]", "BracketRight")

        KeyEvent.KEYCODE_MINUS ->
            JavascriptKey("-", "Minus")

        KeyEvent.KEYCODE_EQUALS ->
            JavascriptKey("=", "Equal")

        KeyEvent.KEYCODE_GRAVE ->
            JavascriptKey("`", "Backquote")

        KeyEvent.KEYCODE_PAGE_UP ->
            JavascriptKey("PageUp", "PageUp")

        KeyEvent.KEYCODE_PAGE_DOWN ->
            JavascriptKey("PageDown", "PageDown")

        KeyEvent.KEYCODE_MOVE_HOME ->
            JavascriptKey("Home", "Home")

        KeyEvent.KEYCODE_MOVE_END ->
            JavascriptKey("End", "End")

        KeyEvent.KEYCODE_INSERT ->
            JavascriptKey("Insert", "Insert")

        KeyEvent.KEYCODE_F1 ->
            JavascriptKey("F1", "F1")

        KeyEvent.KEYCODE_F2 ->
            JavascriptKey("F2", "F2")

        KeyEvent.KEYCODE_F3 ->
            JavascriptKey("F3", "F3")

        KeyEvent.KEYCODE_F4 ->
            JavascriptKey("F4", "F4")

        KeyEvent.KEYCODE_F5 ->
            JavascriptKey("F5", "F5")

        KeyEvent.KEYCODE_F6 ->
            JavascriptKey("F6", "F6")

        KeyEvent.KEYCODE_F7 ->
            JavascriptKey("F7", "F7")

        KeyEvent.KEYCODE_F8 ->
            JavascriptKey("F8", "F8")

        KeyEvent.KEYCODE_F9 ->
            JavascriptKey("F9", "F9")

        KeyEvent.KEYCODE_F10 ->
            JavascriptKey("F10", "F10")

        KeyEvent.KEYCODE_F11 ->
            JavascriptKey("F11", "F11")

        KeyEvent.KEYCODE_F12 ->
            JavascriptKey("F12", "F12")

        else ->
            JavascriptKey(
                key = KeyEvent.keyCodeToString(keyCode),
                code = "AndroidKeyCode$keyCode"
            )
    }
}

/**
 * Builds a browser KeyboardEvent.
 */
private fun createKeyboardEventScript(
    type: String,
    key: String,
    code: String,
    repeat: Boolean
): String {
    val safeType =
        type.replace(
            "'",
            "\\'"
        )

    val safeKey =
        key.replace(
            "\\",
            "\\\\"
        ).replace(
            "'",
            "\\'"
        )

    val safeCode =
        code.replace(
            "\\",
            "\\\\"
        ).replace(
            "'",
            "\\'"
        )

    return """
        (() => {
            const event =
                new KeyboardEvent(
                    '$safeType',
                    {
                        key: '$safeKey',
                        code: '$safeCode',
                        bubbles: true,
                        cancelable: true,
                        composed: true,
                        repeat: $repeat
                    }
                );

            window.dispatchEvent(event);
            document.dispatchEvent(event);

            const active =
                document.activeElement;

            if (
                active &&
                active !== document.body
            ) {
                active.dispatchEvent(event);
            }
        })();
    """.trimIndent()
}
