package net.perfectdreams.butterscotch.android

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import net.perfectdreams.butterscotch.android.components.VirtualGamepad
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameLibrary
import java.io.File

/**
 * HTML game runner.
 *
 * HTML games intentionally have their own Activity instead of sharing the
 * GameMaker/Butterscotch renderer. The surrounding application infrastructure
 * remains shared: library, import system, selected control layout and
 * application storage.
 */
class HtmlGameActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_GAME_ID = "game_id"
    }

    private var webView: WebView? = null

    private lateinit var gameLibrary: GameLibrary
    private lateinit var gameEntry: GameEntry

    private var showControls by mutableStateOf(true)
    private var showMenu by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val gameId = intent.getStringExtra(EXTRA_GAME_ID)

        if (gameId == null) {
            finish()
            return
        }

        gameLibrary = GameLibrary(this)

        val entry = gameLibrary.get(
            java.util.UUID.fromString(gameId)
        )

        if (entry == null || entry.gameType !is GameEntry.GameType.Html) {
            finish()
            return
        }

        gameEntry = entry

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    HtmlWebView(
                        entry = gameEntry,
                        library = gameLibrary,
                        onWebViewCreated = {
                            webView = it
                        }
                    )

                    if (showControls) {
                        VirtualGamepad(
                            // HTML currently receives keyboard-style input
                            // through JavaScript KeyboardEvents.
                            runner = null,
                            enabled = true,
                            onBindingDown = { binding ->
                                dispatchHtmlBinding(binding, true)
                            },
                            onBindingUp = { binding ->
                                dispatchHtmlBinding(binding, false)
                            },
                            onMenu = {
                                showMenu = true
                            }
                        )
                    }
                }

                if (showMenu) {
                    HtmlGameMenu(
                        onDismiss = {
                            showMenu = false
                        },

                        onEditControls = {
                            showMenu = false

                            /*
                             * The existing control-layout editor will be
                             * connected here once the HTML runner shares the
                             * exact same editor state as GameActivity.
                             */
                            startActivity(
                                android.content.Intent(
                                    this@HtmlGameActivity,
                                    ControlLayoutActivity::class.java
                                )
                            )
                        },

                        onExit = {
                            showMenu = false
                            finish()
                        }
                    )
                }
            }
        }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    showMenu = true
                }
            }
        )
    }

    override fun onDestroy() {
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

    /**
     * Sends a virtual control event to the HTML document.
     *
     * The event is dispatched as a normal KeyboardEvent so games that already
     * listen for keyboard input can work without requiring game-specific
     * JavaScript integration.
     */
    private fun dispatchHtmlBinding(
        binding: net.perfectdreams.butterscotch.android.layouts.InputBinding,
        pressed: Boolean
    ) {
        val keyboard = binding as? net.perfectdreams.butterscotch.android.layouts.InputBinding.Keyboard
            ?: return

        val keyData = keyboardKeyData(keyboard.vk) ?: return

        val type = if (pressed) "keydown" else "keyup"

        val script = """
            (() => {
                const event = new KeyboardEvent("$type", {
                    key: ${org.json.JSONObject.quote(keyData.key)},
                    code: ${org.json.JSONObject.quote(keyData.code)},
                    keyCode: ${keyData.keyCode},
                    which: ${keyData.keyCode},
                    bubbles: true,
                    cancelable: true
                });

                document.dispatchEvent(event);
                window.dispatchEvent(event);

                if (document.activeElement) {
                    document.activeElement.dispatchEvent(event);
                }
            })();
        """.trimIndent()

        webView?.post {
            webView?.evaluateJavascript(script, null)
        }
    }

    private data class KeyboardKeyData(
        val key: String,
        val code: String,
        val keyCode: Int
    )

    private fun keyboardKeyData(vk: Int): KeyboardKeyData? {
        return when (vk) {
            37 -> KeyboardKeyData("ArrowLeft", "ArrowLeft", 37)
            38 -> KeyboardKeyData("ArrowUp", "ArrowUp", 38)
            39 -> KeyboardKeyData("ArrowRight", "ArrowRight", 39)
            40 -> KeyboardKeyData("ArrowDown", "ArrowDown", 40)

            13 -> KeyboardKeyData("Enter", "Enter", 13)
            27 -> KeyboardKeyData("Escape", "Escape", 27)
            32 -> KeyboardKeyData(" ", "Space", 32)

            65 -> KeyboardKeyData("a", "KeyA", 65)
            66 -> KeyboardKeyData("b", "KeyB", 66)
            67 -> KeyboardKeyData("c", "KeyC", 67)
            68 -> KeyboardKeyData("d", "KeyD", 68)
            69 -> KeyboardKeyData("e", "KeyE", 69)
            70 -> KeyboardKeyData("f", "KeyF", 70)
            71 -> KeyboardKeyData("g", "KeyG", 71)
            72 -> KeyboardKeyData("h", "KeyH", 72)
            73 -> KeyboardKeyData("i", "KeyI", 73)
            74 -> KeyboardKeyData("j", "KeyJ", 74)
            75 -> KeyboardKeyData("k", "KeyK", 75)
            76 -> KeyboardKeyData("l", "KeyL", 76)
            77 -> KeyboardKeyData("m", "KeyM", 77)
            78 -> KeyboardKeyData("n", "KeyN", 78)
            79 -> KeyboardKeyData("o", "KeyO", 79)
            80 -> KeyboardKeyData("p", "KeyP", 80)
            81 -> KeyboardKeyData("q", "KeyQ", 81)
            82 -> KeyboardKeyData("r", "KeyR", 82)
            83 -> KeyboardKeyData("s", "KeyS", 83)
            84 -> KeyboardKeyData("t", "KeyT", 84)
            85 -> KeyboardKeyData("u", "KeyU", 85)
            86 -> KeyboardKeyData("v", "KeyV", 86)
            87 -> KeyboardKeyData("w", "KeyW", 87)
            88 -> KeyboardKeyData("x", "KeyX", 88)
            89 -> KeyboardKeyData("y", "KeyY", 89)
            90 -> KeyboardKeyData("z", "KeyZ", 90)

            else -> null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@androidx.compose.runtime.Composable
private fun HtmlWebView(
    entry: GameEntry,
    library: GameLibrary,
    onWebViewCreated: (WebView) -> Unit
) {
    val gameType = entry.gameType as? GameEntry.GameType.Html
        ?: return

    val rootDirectory = File(gameType.rootPath)

    var currentUrl by remember {
        mutableStateOf(
            "file://${File(rootDirectory, "index.html").absolutePath}"
        )
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),

        factory = { context ->
            WebView(context).apply {

                onWebViewCreated(this)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = false

                    allowFileAccess = true
                    allowContentAccess = true

                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true

                    mediaPlaybackRequiresUserGesture = false

                    cacheMode = WebSettings.LOAD_DEFAULT

                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)

                    loadsImagesAutomatically = true

                    javaScriptCanOpenWindowsAutomatically = true
                    setGeolocationEnabled(false)

                    // HTML games should behave as an application rather
                    // than as a normal browser page.
                    userAgentString =
                        "$userAgentString SpaghettiHTMLRunner/1.0"
                }

                webChromeClient = WebChromeClient()

                webViewClient = object : WebViewClient() {

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val uri = request.url

                        /*
                         * Keep local game resources inside the WebView.
                         * External HTTP/HTTPS pages are also allowed because
                         * some HTML games load libraries/assets remotely.
                         */
                        if (
                            uri.scheme == "file" ||
                            uri.scheme == "http" ||
                            uri.scheme == "https"
                        ) {
                            return false
                        }

                        return true
                    }

                    override fun shouldInterceptRequest(
                        view: WebView,
                        request: WebResourceRequest
                    ): WebResourceResponse? {
                        return super.shouldInterceptRequest(
                            view,
                            request
                        )
                    }
                }

                setBackgroundColor(
                    android.graphics.Color.BLACK
                )

                systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE

                loadUrl(currentUrl)
            }
        },

        update = { view ->
            if (view.url != currentUrl) {
                view.loadUrl(currentUrl)
            }
        }
    )

    LaunchedEffect(rootDirectory.absolutePath) {
        val indexFile = File(rootDirectory, "index.html")

        if (indexFile.exists()) {
            currentUrl = "file://${indexFile.absolutePath}"
        }
    }
}

@androidx.compose.runtime.Composable
private fun HtmlGameMenu(
    onDismiss: () -> Unit,
    onEditControls: () -> Unit,
    onExit: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,

        title = {
            androidx.compose.material3.Text("Game Menu")
        },

        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.TextButton(
                    onClick = onEditControls
                ) {
                    androidx.compose.material3.Text(
                        "Edit Control Layout"
                    )
                }

                androidx.compose.material3.TextButton(
                    onClick = onDismiss
                ) {
                    androidx.compose.material3.Text(
                        "Resume Game"
                    )
                }

                androidx.compose.material3.TextButton(
                    onClick = onExit
                ) {
                    androidx.compose.material3.Text(
                        "Exit Game"
                    )
                }
            }
        },

        confirmButton = {}
    )
}
