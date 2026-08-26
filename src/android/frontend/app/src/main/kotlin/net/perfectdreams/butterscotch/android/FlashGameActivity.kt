package net.perfectdreams.butterscotch.android

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameLibrary
import net.perfectdreams.butterscotch.android.theme.ButterscotchAndroidTheme
import java.io.File
import java.util.UUID

/**
 * Activity responsible for running imported Flash games.
 *
 * Architecture:
 *
 * FlashGameActivity
 *       |
 *       +-- WebView
 *              |
 *              +-- Ruffle Web Runtime
 *                        |
 *                        +-- imported .swf
 *
 * The Activity is deliberately separate from HtmlGameActivity.
 *
 * HTML games are executed by HtmlGameActivity directly.
 * Flash games are executed by Ruffle through this dedicated Activity.
 */
class FlashGameActivity : ComponentActivity() {

    companion object {
        const val EXTRA_GAME_ID = "game_id"

        private const val TAG = "FlashGameActivity"

        /**
         * Directory inside app/src/main/assets containing the
         * self-hosted Ruffle web package.
         *
         * Expected files include at least:
         *
         * assets/ruffle/ruffle.js
         * assets/ruffle/*.wasm
         */
        private const val RUFFLE_ASSET_DIRECTORY = "ruffle"

        private const val RUFFLE_SCRIPT =
            "ruffle/ruffle.js"
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
            finish()
            return
        }

        val entry = gameLibrary.findById(gameId)

        if (
            entry == null ||
            entry.gameType !is GameEntry.GameType.Flash
        ) {
            finish()
            return
        }

        setContent {
            ButterscotchAndroidTheme {
                FlashGameContent(
                    entry = entry,
                    library = gameLibrary,
                    onWebViewCreated = { view ->
                        webView = view
                    },
                    onKeyDown = { keyCode ->
                        webView?.let { view ->
                            pressedKeys.press(
                                view,
                                keyCode
                            )
                        }
                    },
                    onKeyUp = { keyCode ->
                        webView?.let { view ->
                            pressedKeys.release(
                                view,
                                keyCode
                            )
                        }
                    },
                    onReleaseAllKeys = {
                        webView?.let {
                            pressedKeys.releaseAll(it)
                        } ?: pressedKeys.clear()
                    }
                )
            }
        }
    }

    override fun dispatchKeyEvent(
        event: android.view.KeyEvent
    ): Boolean {
        if (
            event.keyCode ==
            android.view.KeyEvent.KEYCODE_BACK
        ) {
            return super.dispatchKeyEvent(event)
        }

        when (event.action) {
            android.view.KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    pressedKeys.press(
                        webView,
                        event.keyCode
                    )
                }

                return true
            }

            android.view.KeyEvent.ACTION_UP -> {
                pressedKeys.release(
                    webView,
                    event.keyCode
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
 * JavaScript keyboard bridge for Flash/Ruffle.
 *
 * Ruffle runs inside a WebView, so Android key events have to be
 * translated into browser KeyboardEvent objects.
 */
private class FlashKeyState {

    private val pressedKeys =
        mutableSetOf<Int>()

    fun press(
        webView: WebView?,
        keyCode: Int
    ) {
        if (webView == null) return

        if (!pressedKeys.add(keyCode)) {
            return
        }

        val jsKey =
            androidKeyCodeToJavascriptKey(keyCode)

        val jsCode =
            androidKeyCodeToJavascriptCode(keyCode)

        webView.evaluateJavascript(
            createKeyboardEventScript(
                type = "keydown",
                key = jsKey,
                code = jsCode,
                repeat = false
            ),
            null
        )
    }

    fun release(
        webView: WebView?,
        keyCode: Int
    ) {
        if (webView == null) {
            pressedKeys.remove(keyCode)
            return
        }

        if (!pressedKeys.remove(keyCode)) {
            return
        }

        val jsKey =
            androidKeyCodeToJavascriptKey(keyCode)

        val jsCode =
            androidKeyCodeToJavascriptCode(keyCode)

        webView.evaluateJavascript(
            createKeyboardEventScript(
                type = "keyup",
                key = jsKey,
                code = jsCode,
                repeat = false
            ),
            null
        )
    }

    fun releaseAll(
        webView: WebView?
    ) {
        if (webView == null) {
            clear()
            return
        }

        val keys = pressedKeys.toList()

        for (keyCode in keys) {
            release(webView, keyCode)
        }
    }

    fun clear() {
        pressedKeys.clear()
    }
}

/**
 * Compose host for the Flash WebView.
 */
@Composable
private fun FlashGameContent(
    entry: GameEntry,
    library: GameLibrary,
    onWebViewCreated: (WebView) -> Unit,
    onKeyDown: (Int) -> Unit,
    onKeyUp: (Int) -> Unit,
    onReleaseAllKeys: () -> Unit
) {
    val gameType =
        entry.gameType as? GameEntry.GameType.Flash
            ?: return

    val gameFile =
        File(
            library.bundleDir(entry),
            gameType.filename
        )

    var loadError by remember {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(Unit) {
        onReleaseAllKeys()
    }

    DisposableEffect(Unit) {
        onDispose {
            onReleaseAllKeys()
        }
    }

    BackHandler {
        onReleaseAllKeys()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        FlashWebView(
            gameFile = gameFile,
            onWebViewCreated = onWebViewCreated,
            onLoadError = {
                loadError = it
            }
        )
    }
}

/**
 * WebView hosting Ruffle.
 *
 * Ruffle is loaded from app assets so Flash games remain usable
 * without requiring an external Ruffle server.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun FlashWebView(
    gameFile: File,
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

                settings.apply {
                    javaScriptEnabled = true

                    domStorageEnabled = true

                    databaseEnabled = true

                    allowFileAccess = true

                    allowContentAccess = true

                    allowFileAccessFromFileURLs = true

                    allowUniversalAccessFromFileURLs = true

                    javaScriptCanOpenWindowsAutomatically =
                        true

                    mediaPlaybackRequiresUserGesture =
                        false

                    useWideViewPort = true

                    loadWithOverviewMode = true

                    builtInZoomControls = false

                    displayZoomControls = false

                    setSupportZoom(false)

                    cacheMode =
                        WebSettings.LOAD_DEFAULT

                    userAgentString =
                        "$userAgentString SpaghettiFlashRunner/1.0"
                }

                webChromeClient =
                    object : WebChromeClient() {

                        override fun onConsoleMessage(
                            consoleMessage: ConsoleMessage
                        ): Boolean {
                            android.util.Log.d(
                                "Ruffle",
                                "${consoleMessage.message()} " +
                                    "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                            )

                            return true
                        }
                    }

                webViewClient =
                    object : WebViewClient() {

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

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            return when (
                                request.url.scheme?.lowercase()
                            ) {
                                "file",
                                "http",
                                "https" -> false

                                else -> true
                            }
                        }

                        override fun onReceivedError(
                            view: WebView,
                            request: WebResourceRequest,
                            error: android.webkit.WebResourceError
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

                if (!gameFile.exists()) {
                    onLoadError(
                        "Flash game file does not exist: " +
                            gameFile.absolutePath
                    )

                    return@apply
                }

                loadDataWithBaseURL(
                    gameFile.parentFile
                        ?.toURI()
                        ?.toString(),
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
 * Generates the minimal host page used to boot Ruffle.
 *
 * The actual SWF is still stored in the game's private bundle.
 */
private fun createRuffleHtml(
    swfFileName: String
): String {
    val escapedSwf =
        swfFileName
            .replace("\\", "\\\\")
            .replace("'", "\\'")

    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta
                name="viewport"
                content="width=device-width,
                         initial-scale=1.0,
                         maximum-scale=1.0,
                         user-scalable=no,
                         viewport-fit=cover"
            >

            <meta
                charset="utf-8"
            >

            <style>
                html,
                body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: #000;
                }

                #ruffle-container {
                    position: fixed;
                    inset: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: #000;
                }

                ruffle-player {
                    display: block;
                    width: 100%;
                    height: 100%;
                }
            </style>

            <script>
                window.RufflePlayer =
                    window.RufflePlayer || {};

                window.addEventListener(
                    "DOMContentLoaded",
                    function () {

                        const factory =
                            window.RufflePlayer.newest();

                        if (!factory) {
                            console.error(
                                "RufflePlayer.newest() is unavailable."
                            );
                            return;
                        }

                        const player =
                            factory.createPlayer();

                        player.style.width =
                            "100%";

                        player.style.height =
                            "100%";

                        const container =
                            document.getElementById(
                                "ruffle-container"
                            );

                        container.appendChild(
                            player
                        );

                        const ruffle =
                            player.ruffle();

                        ruffle.load(
                            "${escapedSwf}"
                        );
                    }
                );
            </script>

            <script
                src="file:///android_asset/ruffle/ruffle.js">
            </script>
        </head>

        <body>
            <div id="ruffle-container"></div>
        </body>
        </html>
    """.trimIndent()
}

/**
 * Keeps the Ruffle player correctly sized on Android.
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
 * Creates a browser KeyboardEvent inside the Ruffle/WebView document.
 */
private fun createKeyboardEventScript(
    type: String,
    key: String,
    code: String,
    repeat: Boolean
): String {
    val safeType =
        type.replace("'", "\\'")

    val safeKey =
        key.replace("'", "\\'")

    val safeCode =
        code.replace("'", "\\'")

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
                        repeat: ${repeat}
                    }
                );

            window.dispatchEvent(event);

            document.dispatchEvent(event);
        })();
    """.trimIndent()
}

/**
 * Basic Android -> DOM KeyboardEvent mapping.
 */
private fun androidKeyCodeToJavascriptKey(
    keyCode: Int
): String {
    return when (keyCode) {

        android.view.KeyEvent.KEYCODE_DPAD_UP ->
            "ArrowUp"

        android.view.KeyEvent.KEYCODE_DPAD_DOWN ->
            "ArrowDown"

        android.view.KeyEvent.KEYCODE_DPAD_LEFT ->
            "ArrowLeft"

        android.view.KeyEvent.KEYCODE_DPAD_RIGHT ->
            "ArrowRight"

        android.view.KeyEvent.KEYCODE_
