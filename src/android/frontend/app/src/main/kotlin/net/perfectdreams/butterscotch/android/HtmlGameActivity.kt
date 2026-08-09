package net.perfectdreams.butterscotch.android

import android.annotation.SuppressLint
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.viewinterop.AndroidView
import net.perfectdreams.butterscotch.android.components.ButterscotchTopBar
import net.perfectdreams.butterscotch.android.layouts.GamepadElement
import net.perfectdreams.butterscotch.android.layouts.GamepadLayout
import net.perfectdreams.butterscotch.android.layouts.InputBinding
import net.perfectdreams.butterscotch.android.library.GameEntry
import net.perfectdreams.butterscotch.android.library.GameLibrary
import net.perfectdreams.butterscotch.android.layouts.LayoutLibrary
import net.perfectdreams.butterscotch.android.theme.ButterscotchAndroidTheme
import java.io.File
import java.util.UUID

class HtmlGameActivity : ComponentActivity() {

    companion object {
        const val EXTRA_GAME_ID = "game_id"
    }

    private lateinit var gameLibrary: GameLibrary
    private lateinit var layoutLibrary: LayoutLibrary

    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        gameLibrary = Libraries.loadGameLibrary(applicationContext)
        layoutLibrary = Libraries.loadLayoutLibrary(applicationContext)

        val gameIdString = intent.getStringExtra(EXTRA_GAME_ID)

        if (gameIdString == null) {
            finish()
            return
        }

        val gameId = runCatching {
            UUID.fromString(gameIdString)
        }.getOrNull()

        if (gameId == null) {
            finish()
            return
        }

        val entry = gameLibrary.findById(gameId)

        if (entry == null || entry.gameType !is GameEntry.GameType.Html) {
            finish()
            return
        }

        window.addFlags(
            android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        window.decorView.systemUiVisibility =
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE

        setContent {
            ButterscotchAndroidTheme {
                HtmlGameContent(
                    entry = entry,
                    library = gameLibrary,
                    layoutLibrary = layoutLibrary,
                    onWebViewCreated = {
                        webView = it
                    },
                    onExit = {
                        finish()
                    }
                )
            }
        }
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
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlGameContent(
    entry: GameEntry,
    library: GameLibrary,
    layoutLibrary: LayoutLibrary,
    onWebViewCreated: (WebView) -> Unit,
    onExit: () -> Unit
) {
    val gameType = entry.gameType as? GameEntry.GameType.Html
        ?: return

    val rootDirectory = library.bundleDir(entry)
    val entryFile = File(rootDirectory, gameType.entryPoint)

    var showControls by remember { mutableStateOf(true) }
    var showMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        HtmlWebView(
            entryFile = entryFile,
            onWebViewCreated = onWebViewCreated
        )

        if (showControls) {
            HtmlControls(
                entry = entry,
                layoutLibrary = layoutLibrary,
                onMenu = {
                    showMenu = true
                }
            )
        }

        if (showMenu) {
            HtmlMenu(
                onResume = {
                    showMenu = false
                },
                onToggleControls = {
                    showControls = !showControls
                    showMenu = false
                },
                controlsVisible = showControls,
                onExit = {
                    showMenu = false
                    onExit()
                }
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun HtmlWebView(
    entryFile: File,
    onWebViewCreated: (WebView) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),

        factory = { context ->
            WebView(context).apply {
                onWebViewCreated(this)

                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true

                    allowFileAccess = true
                    allowContentAccess = true

                    allowFileAccessFromFileURLs = true
                    allowUniversalAccessFromFileURLs = true

                    javaScriptCanOpenWindowsAutomatically = true
                    mediaPlaybackRequiresUserGesture = false

                    useWideViewPort = true
                    loadWithOverviewMode = true

                    builtInZoomControls = false
                    displayZoomControls = false
                    setSupportZoom(false)

                    cacheMode = WebSettings.LOAD_DEFAULT

                    userAgentString =
                        "$userAgentString SpaghettiHTMLRunner/1.0"
                }

                webChromeClient = WebChromeClient()

                webViewClient = object : WebViewClient() {

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: WebResourceRequest
                    ): Boolean {
                        val scheme = request.url.scheme?.lowercase()

                        return when (scheme) {
                            "file",
                            "http",
                            "https" -> false

                            else -> true
                        }
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

                if (entryFile.exists()) {
                    loadUrl(entryFile.toURI().toString())
                }
            }
        }
    )
}

@Composable
private fun HtmlControls(
    entry: GameEntry,
    layoutLibrary: LayoutLibrary,
    onMenu: () -> Unit
) {
    val configuration = LocalConfiguration.current

    val isLandscape =
        configuration.orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE ||
            configuration.screenWidthDp > configuration.screenHeightDp

    val layoutId =
        if (isLandscape) {
            entry.landscapeLayout
        } else {
            entry.portraitLayout
        }

    val layout = layoutLibrary.findById(layoutId)
        ?: layoutLibrary.findById(
            if (isLandscape) {
                LayoutLibrary.DEFAULT_LANDSCAPE_LAYOUT
            } else {
                LayoutLibrary.DEFAULT_PORTRAIT_LAYOUT
            }
        )
        ?: return

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        layout.elements.forEach { element ->
            when (element) {
                is GamepadElement.Key -> {
                    HtmlKeyButton(
                        element = element,
                        modifier = placementForElement(element)
                    )
                }

                is GamepadElement.Joystick -> {
                    HtmlJoystick(
                        element = element,
                        modifier = placementForElement(element)
                    )
                }

                is GamepadElement.Menu -> {
                    HtmlMenuButton(
                        element = element,
                        onClick = onMenu,
                        modifier = placementForElement(element)
                    )
                }

                is GamepadElement.AnalogJoystick -> {
                    // HTML games currently use keyboard input.
                    // Analog controller input is intentionally ignored.
                }

                is GamepadElement.MouseButton -> {
                    // HTML mouse emulation is not part of the 1.0 input layer.
                }

                is GamepadElement.FastForward -> {
                    // Fast-forward is specific to the native GameMaker runner.
                }
            }
        }
    }
}

@Composable
private fun BoxWithConstraintsScope.placementForElement(
    element: GamepadElement
): Modifier {
    val referenceSize =
        if (maxWidth < maxHeight) {
            maxWidth
        } else {
            maxHeight
        }

    val size = referenceSize * element.scale.toFloat()

    val x =
        maxWidth * element.positionX.toFloat() -
            size / 2f

    val y =
        maxHeight * element.positionY.toFloat() -
            size / 2f

    return Modifier
        .offset(
            x = x,
            y = y
        )
        .size(size)
        .alpha(element.opacity.toFloat())
}

@Composable
private fun HtmlKeyButton(
    element: GamepadElement.Key,
    modifier: Modifier
) {
    val binding = element.binding as? InputBinding.Keyboard
        ?: return

    val label =
        element.label
            ?: htmlKeyLabel(binding.vk)

    Box(
        modifier = modifier
            .background(
                Color(0xAA222222),
                CircleShape
            )
            .pointerInput(binding.vk) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false
                    )

                    HtmlInput.dispatchKey(
                        binding.vk,
                        true
                    )

                    try {
                        waitForUpOrCancellation()
                    } finally {
                        HtmlInput.dispatchKey(
                            binding.vk,
                            false
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun HtmlJoystick(
    element: GamepadElement.Joystick,
    modifier: Modifier
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            HtmlDirectionalButton(
                binding = element.up,
                label = "▲",
                modifier = Modifier.size(42.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                HtmlDirectionalButton(
                    binding = element.left,
                    label = "◀",
                    modifier = Modifier.size(42.dp)
                )

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .background(
                            Color(0xAA222222),
                            CircleShape
                        )
                )

                HtmlDirectionalButton(
                    binding = element.right,
                    label = "▶",
                    modifier = Modifier.size(42.dp)
                )
            }

            HtmlDirectionalButton(
                binding = element.down,
                label = "▼",
                modifier = Modifier.size(42.dp)
            )
        }
    }
}

@Composable
private fun HtmlDirectionalButton(
    binding: InputBinding,
    label: String,
    modifier: Modifier
) {
    val keyboard =
        binding as? InputBinding.Keyboard
            ?: return

    Box(
        modifier = modifier
            .background(
                Color(0xAA222222),
                CircleShape
            )
            .pointerInput(keyboard.vk) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false
                    )

                    HtmlInput.dispatchKey(
                        keyboard.vk,
                        true
                    )

                    try {
                        waitForUpOrCancellation()
                    } finally {
                        HtmlInput.dispatchKey(
                            keyboard.vk,
                            false
                        )
                    }
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 18.sp
        )
    }
}

@Composable
private fun HtmlMenuButton(
    element: GamepadElement.Menu,
    onClick: () -> Unit,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .background(
                Color(0xAA222222),
                CircleShape
            )
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false
                    )

                    waitForUpOrCancellation()
                    onClick()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "☰",
            color = Color.White,
            fontSize = 18.sp
        )
    }
}

private fun htmlKeyLabel(vk: Int): String {
    return when (vk) {
        37 -> "←"
        38 -> "↑"
        39 -> "→"
        40 -> "↓"

        13 -> "Enter"
        27 -> "Esc"
        32 -> "Space"

        65 -> "A"
        66 -> "B"
        67 -> "C"
        68 -> "D"
        69 -> "E"
        70 -> "F"
        71 -> "G"
        72 -> "H"
        73 -> "I"
        74 -> "J"
        75 -> "K"
        76 -> "L"
        77 -> "M"
        78 -> "N"
        79 -> "O"
        80 -> "P"
        81 -> "Q"
        82 -> "R"
        83 -> "S"
        84 -> "T"
        85 -> "U"
        86 -> "V"
        87 -> "W"
        88 -> "X"
        89 -> "Y"
        90 -> "Z"

        else -> vk.toString()
    }
}

private object HtmlInput {

    fun dispatchKey(vk: Int, pressed: Boolean) {
        val activity = currentActivity ?: return
        val view = activity.currentWebView ?: return

        val keyData = keyData(vk) ?: return

        val eventType =
            if (pressed) {
                "keydown"
            } else {
                "keyup"
            }

        val script = """
            (() => {
                const event = new KeyboardEvent("$eventType", {
                    key: ${org.json.JSONObject.quote(keyData.key)},
                    code: ${org.json.JSONObject.quote(keyData.code)},
                    bubbles: true,
                    cancelable: true
                });

                try {
                    Object.defineProperty(event, "keyCode", {
                        get: () => ${keyData.keyCode}
                    });

                    Object.defineProperty(event, "which", {
                        get: () => ${keyData.keyCode}
                    });

                    Object.defineProperty(event, "charCode", {
                        get: () => ${keyData.keyCode}
                    });
                } catch (_) {}

                document.dispatchEvent(event);
                window.dispatchEvent(event);

                if (document.body) {
                    document.body.dispatchEvent(event);
                }

                if (document.activeElement) {
                    document.activeElement.dispatchEvent(event);
                }
            })();
        """.trimIndent()

        view.post {
            view.evaluateJavascript(script, null)
        }
    }

    private fun keyData(vk: Int): KeyData? {
        return when (vk) {
            37 -> KeyData("ArrowLeft", "ArrowLeft", 37)
            38 -> KeyData("ArrowUp", "ArrowUp", 38)
            39 -> KeyData("ArrowRight", "ArrowRight", 39)
            40 -> KeyData("ArrowDown", "ArrowDown", 40)

            13 -> KeyData("Enter", "Enter", 13)
            27 -> KeyData("Escape", "Escape", 27)
            32 -> KeyData(" ", "Space", 32)

            in 65..90 -> {
                val letter = ('A'.code + (vk - 65)).toChar()
                KeyData(
                    letter.toString().lowercase(),
                    "Key$letter",
                    vk
                )
            }

            else -> null
        }
    }

    private data class KeyData(
        val key: String,
        val code: String,
        val keyCode: Int
    )

    private var currentActivity: HtmlGameActivity? = null
        get() = field

    private val HtmlGameActivity.currentWebView: WebView?
        get() = webViewForActivity[this]

    private val webViewForActivity =
        java.util.WeakHashMap<HtmlGameActivity, WebView?>()

    private val HtmlGameActivity.webViewForInput: WebView?
        get() = webViewForActivity[this]

    fun register(
        activity: HtmlGameActivity,
        webView: WebView
    ) {
        currentActivity = activity
        webViewForActivity[activity] = webView
    }

    fun unregister(
        activity: HtmlGameActivity
    ) {
        webViewForActivity.remove(activity)

        if (currentActivity === activity) {
            c
