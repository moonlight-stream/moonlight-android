package com.limelight.handbook

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.ActionMode
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.limelight.LimeLog
import com.limelight.R
import com.limelight.utils.BrowserOnlyLauncher
import com.limelight.utils.UiHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.ByteArrayInputStream

class HandbookActivity : ComponentActivity() {
    private val repository by lazy { HandbookRepository(applicationContext) }

    private val navigationHistory = mutableListOf<HandbookPageRef>()
    private var navigationIndex = 0
    private val currentPage: HandbookPageRef
        get() = navigationHistory[navigationIndex]

    private var loadJob: Job? = null
    private var prepareWebViewJob: Job? = null
    private var handbookWebView: LockedHandbookWebView? = null
    private var pendingContent: HandbookLoadResult.Success? = null

    private lateinit var contentContainer: FrameLayout
    private lateinit var loadingView: View
    private lateinit var errorView: View
    private lateinit var errorMessage: TextView
    private lateinit var rootView: View
    private lateinit var previousPageButton: ImageButton
    private lateinit var nextPageButton: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiHelper.setLocale(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val systemBarColor = 0xFF16162A.toInt()
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor

        setContentView(R.layout.activity_handbook)
        rootView = findViewById(R.id.handbook_root)
        contentContainer = findViewById(R.id.handbook_content)
        loadingView = findViewById(R.id.handbook_loading)
        errorView = findViewById(R.id.handbook_error)
        errorMessage = findViewById(R.id.handbook_error_message)
        previousPageButton = findViewById(R.id.handbook_previous_button)
        nextPageButton = findViewById(R.id.handbook_next_button)

        ViewCompat.setOnApplyWindowInsetsListener(rootView) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(rootView)

        findViewById<Button>(R.id.handbook_retry).setOnClickListener {
            loadPage(currentPage)
        }
        findViewById<ImageButton>(R.id.handbook_exit_button).setOnClickListener {
            finish()
        }
        previousPageButton.setOnClickListener {
            navigateHistoryBy(-1)
        }
        nextPageButton.setOnClickListener {
            navigateHistoryBy(1)
        }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        })

        restoreNavigationHistory(savedInstanceState)
        updateNavigationButtons()
        UiHelper.notifyNewRootView(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putStringArrayList(
            STATE_NAVIGATION_HISTORY,
            ArrayList(navigationHistory.map(HandbookUrlPolicy::canonicalUrl))
        )
        outState.putInt(STATE_NAVIGATION_INDEX, navigationIndex)
        super.onSaveInstanceState(outState)
    }

    override fun onStart() {
        super.onStart()
        loadPage(currentPage)
        rootView.doOnPreDraw {
            rootView.post {
                if (isFinishing ||
                    isDestroyed ||
                    !lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) ||
                    handbookWebView != null
                ) {
                    return@post
                }
                prepareWebViewJob = lifecycleScope.launch {
                    ensureWebView()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        releaseBackgroundResources()
    }

    override fun onDestroy() {
        releaseBackgroundResources()
        super.onDestroy()
    }

    private fun releaseBackgroundResources() {
        loadJob?.cancel()
        loadJob = null
        prepareWebViewJob?.cancel()
        prepareWebViewJob = null
        pendingContent = null
        handbookWebView?.let { webView ->
            handbookWebView = null
            contentContainer.removeView(webView)
            webView.apply {
                onPause()
                setOnTouchListener(null)
                setDownloadListener(null)
                webChromeClient = null
                webViewClient = WebViewClient()
                onDocumentRendered = null
                renderStartedAtMs = 0L
                stopLoading()
                clearHistory()
                removeAllViews()
                destroy()
            }
            LimeLog.info("Handbook WebView released while backgrounded")
        }
    }

    private fun navigateTo(page: HandbookPageRef) {
        if (page == currentPage) return

        if (navigationIndex < navigationHistory.lastIndex) {
            navigationHistory.subList(
                navigationIndex + 1,
                navigationHistory.size
            ).clear()
        }
        navigationHistory += page
        navigationIndex = navigationHistory.lastIndex
        if (navigationHistory.size > MAX_NAVIGATION_HISTORY) {
            navigationHistory.removeAt(0)
            navigationIndex--
        }
        updateNavigationButtons()
        loadPage(currentPage)
    }

    private fun navigateHistoryBy(offset: Int) {
        val targetIndex = navigationIndex + offset
        if (targetIndex !in navigationHistory.indices) return

        navigationIndex = targetIndex
        updateNavigationButtons()
        loadPage(currentPage)
    }

    private fun restoreNavigationHistory(savedInstanceState: Bundle?) {
        val restoredUrls = savedInstanceState
            ?.getStringArrayList(STATE_NAVIGATION_HISTORY)
        val restoredPages = restoredUrls?.map(HandbookUrlPolicy::parse)
        val restoredIndex = savedInstanceState
            ?.getInt(STATE_NAVIGATION_INDEX, -1)
            ?: -1
        if (!restoredPages.isNullOrEmpty() &&
            restoredPages.size <= MAX_NAVIGATION_HISTORY &&
            restoredPages.all { it != null } &&
            restoredIndex in restoredPages.indices
        ) {
            navigationHistory += restoredPages.filterNotNull()
            navigationIndex = restoredIndex
            return
        }

        navigationHistory += HandbookLauncher.pageFromIntent(intent)
        navigationIndex = 0
    }

    private fun updateNavigationButtons() {
        setNavigationButtonEnabled(
            previousPageButton,
            navigationIndex > 0
        )
        setNavigationButtonEnabled(
            nextPageButton,
            navigationIndex < navigationHistory.lastIndex
        )
    }

    private fun setNavigationButtonEnabled(button: ImageButton, enabled: Boolean) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else DISABLED_NAVIGATION_ALPHA
    }

    private fun loadPage(page: HandbookPageRef) {
        loadJob?.cancel()
        showLoading()
        loadJob = lifecycleScope.launch {
            when (val result = repository.load(page)) {
                is HandbookLoadResult.Success -> showContent(result)
                is HandbookLoadResult.Failure -> showError(result.reason)
            }
        }
    }

    private fun showLoading() {
        pendingContent = null
        handbookWebView?.visibility = View.INVISIBLE
        contentContainer.visibility = View.INVISIBLE
        errorView.visibility = View.GONE
        loadingView.visibility = View.VISIBLE
    }

    private fun showError(reason: HandbookFailureReason) {
        pendingContent = null
        handbookWebView?.visibility = View.INVISIBLE
        contentContainer.visibility = View.INVISIBLE
        loadingView.visibility = View.GONE
        errorMessage.setText(
            when (reason) {
                HandbookFailureReason.NETWORK -> R.string.handbook_error_network
                HandbookFailureReason.TIMEOUT -> R.string.handbook_error_timeout
                HandbookFailureReason.UNAVAILABLE -> R.string.handbook_error_unavailable
            }
        )
        errorView.visibility = View.VISIBLE
    }

    private fun showContent(content: HandbookLoadResult.Success) {
        pendingContent = content
        handbookWebView?.let { displayContent(it, content) }
    }

    private fun displayContent(
        webView: LockedHandbookWebView,
        content: HandbookLoadResult.Success
    ) {
        pendingContent = null
        webView.renderStartedAtMs = SystemClock.elapsedRealtime()
        webView.visibility = View.VISIBLE
        contentContainer.visibility = View.VISIBLE
        errorView.visibility = View.GONE
        webView.loadDataWithBaseURL(
            content.baseUrl,
            content.html,
            "text/html",
            "UTF-8",
            content.baseUrl
        )
    }

    private fun ensureWebView(): LockedHandbookWebView {
        handbookWebView?.let { return it }

        val startedAt = SystemClock.elapsedRealtime()
        return createLockedHandbookWebView(
            context = this,
            onNavigate = ::navigateTo,
            onOpenExternal = { BrowserOnlyLauncher.open(this, it) }
        ).also { webView ->
            handbookWebView = webView
            webView.onDocumentRendered = {
                loadingView.visibility = View.GONE
                errorView.visibility = View.GONE
            }
            webView.visibility = View.INVISIBLE
            contentContainer.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            )
            LimeLog.info(
                "Handbook WebView prepared in " +
                    "${SystemClock.elapsedRealtime() - startedAt} ms"
            )
            pendingContent?.let { displayContent(webView, it) }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
private fun createLockedHandbookWebView(
    context: android.content.Context,
    onNavigate: (HandbookPageRef) -> Unit,
    onOpenExternal: (String) -> Unit
): LockedHandbookWebView {
    val legacyLinkTapTracker = LegacyLinkTapTracker(context)
    return LockedHandbookWebView(context).apply {
        setBackgroundColor(AndroidColor.WHITE)
        isFocusable = true
        isFocusableInTouchMode = true
        isLongClickable = false
        setOnTouchListener { _, event ->
            legacyLinkTapTracker.record(this, event)
            false
        }
        applyLockedSettings(this)

        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                if (!request.isForMainFrame || request.method != "GET") return true
                val hasGesture = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    legacyLinkTapTracker.clear()
                    request.hasGesture()
                } else {
                    legacyLinkTapTracker.consume(request.url.toString())
                }
                return routeNavigation(
                    view.url,
                    request.url.toString(),
                    hasGesture,
                    onNavigate,
                    onOpenExternal
                )
            }

            @Deprecated("Deprecated in Android")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return routeNavigation(
                    view.url,
                    url,
                    legacyLinkTapTracker.consume(url),
                    onNavigate,
                    onOpenExternal
                )
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse = emptyResponse()

            @Deprecated("Deprecated in Android")
            override fun shouldInterceptRequest(view: WebView, url: String): WebResourceResponse {
                return emptyResponse()
            }

            override fun onPageFinished(view: WebView, url: String) {
                val lockedView = view as? LockedHandbookWebView ?: return
                val startedAt = lockedView.renderStartedAtMs
                if (startedAt > 0L) {
                    LimeLog.info(
                        "Handbook document rendered in " +
                            "${SystemClock.elapsedRealtime() - startedAt} ms"
                    )
                    lockedView.renderStartedAtMs = 0L
                    lockedView.onDocumentRendered?.invoke()
                }
            }

            override fun onReceivedSslError(
                view: WebView,
                handler: SslErrorHandler,
                error: SslError
            ) {
                handler.cancel()
            }
        }

        webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message
            ): Boolean {
                if (!isUserGesture) return false
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                val popup = createNavigationCaptureWebView(
                    context,
                    onNavigate,
                    onOpenExternal
                )
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }

        setDownloadListener { _, _, _, _, _ -> Unit }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun applyLockedSettings(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = false
        javaScriptCanOpenWindowsAutomatically = false
        domStorageEnabled = false
        databaseEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        @Suppress("DEPRECATION")
        allowFileAccessFromFileURLs = false
        @Suppress("DEPRECATION")
        allowUniversalAccessFromFileURLs = false
        blockNetworkLoads = true
        cacheMode = WebSettings.LOAD_NO_CACHE
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        mediaPlaybackRequiresUserGesture = true
        setGeolocationEnabled(false)
        setSupportMultipleWindows(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            safeBrowsingEnabled = true
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun createNavigationCaptureWebView(
    context: android.content.Context,
    onNavigate: (HandbookPageRef) -> Unit,
    onOpenExternal: (String) -> Unit
): WebView {
    val popup = WebView(context)
    applyLockedSettings(popup)
    var handled = false

    fun handle(url: String?) {
        if (handled || url == null) return
        handled = true
        HandbookUrlPolicy.parse(url)?.let(onNavigate)
            ?: if (HandbookUrlPolicy.isExternalHttps(url)) onOpenExternal(url) else Unit
        popup.stopLoading()
        popup.destroy()
    }

    popup.webViewClient = object : WebViewClient() {
        override fun shouldOverrideUrlLoading(
            view: WebView,
            request: WebResourceRequest
        ): Boolean {
            if (request.isForMainFrame && request.method == "GET") {
                handle(request.url.toString())
            }
            return true
        }

        @Deprecated("Deprecated in Android")
        override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
            handle(url)
            return true
        }

        override fun shouldInterceptRequest(
            view: WebView,
            request: WebResourceRequest
        ): WebResourceResponse = emptyResponse()
    }

    Handler(Looper.getMainLooper()).postDelayed({
        if (!handled) {
            handled = true
            popup.stopLoading()
            popup.destroy()
        }
    }, POPUP_CAPTURE_TIMEOUT_MS)
    return popup
}

private fun routeNavigation(
    currentUrl: String?,
    targetUrl: String,
    hasUserGesture: Boolean,
    onNavigate: (HandbookPageRef) -> Unit,
    onOpenExternal: (String) -> Unit
): Boolean {
    if (!hasUserGesture) return true

    val currentPage = HandbookUrlPolicy.parse(currentUrl)
    val targetPage = HandbookUrlPolicy.parse(targetUrl)
    if (currentPage != null && targetPage != null &&
        currentPage.copy(encodedFragment = null) == targetPage.copy(encodedFragment = null)
    ) {
        return targetPage.encodedFragment == null
    }

    if (targetPage != null) {
        onNavigate(targetPage)
    } else if (HandbookUrlPolicy.isExternalHttps(targetUrl)) {
        onOpenExternal(targetUrl)
    }
    return true
}

private fun emptyResponse(): WebResourceResponse {
    return WebResourceResponse(
        "text/plain",
        "UTF-8",
        ByteArrayInputStream(ByteArray(0))
    )
}

private class LegacyLinkTapTracker(context: Context) {
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
    private val touchSlopSquared = touchSlop * touchSlop

    private var downX = 0f
    private var downY = 0f
    private var isTapCandidate = false
    private var pendingTap: PendingLinkTap? = null

    fun record(view: WebView, event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                isTapCandidate = true
                pendingTap = null
            }

            MotionEvent.ACTION_MOVE -> {
                if (movedBeyondTouchSlop(event)) {
                    isTapCandidate = false
                }
            }

            MotionEvent.ACTION_POINTER_DOWN,
            MotionEvent.ACTION_CANCEL -> {
                isTapCandidate = false
                pendingTap = null
            }

            MotionEvent.ACTION_UP -> {
                pendingTap = if (isTapCandidate && !movedBeyondTouchSlop(event)) {
                    view.hitTestResult
                        .takeIf {
                            it.type == WebView.HitTestResult.SRC_ANCHOR_TYPE ||
                                it.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                        }
                        ?.extra
                        ?.let { PendingLinkTap(it, event.eventTime) }
                } else {
                    null
                }
                isTapCandidate = false
            }
        }
    }

    fun consume(targetUrl: String): Boolean {
        val tap = pendingTap ?: return false
        pendingTap = null
        val age = SystemClock.uptimeMillis() - tap.eventTime
        return age in 0..LEGACY_LINK_TAP_TIMEOUT_MS && urlsMatch(tap.url, targetUrl)
    }

    fun clear() {
        pendingTap = null
    }

    private fun movedBeyondTouchSlop(event: MotionEvent): Boolean {
        val deltaX = event.x - downX
        val deltaY = event.y - downY
        return deltaX * deltaX + deltaY * deltaY > touchSlopSquared
    }

    private fun urlsMatch(first: String, second: String): Boolean {
        val firstUri = android.net.Uri.parse(first)
        val secondUri = android.net.Uri.parse(second)
        return firstUri.scheme.equals(secondUri.scheme, ignoreCase = true) &&
            firstUri.host.equals(secondUri.host, ignoreCase = true) &&
            firstUri.port == secondUri.port &&
            firstUri.encodedPath == secondUri.encodedPath &&
            firstUri.encodedQuery == secondUri.encodedQuery &&
            firstUri.encodedFragment == secondUri.encodedFragment
    }
}

private data class PendingLinkTap(
    val url: String,
    val eventTime: Long
)

private class LockedHandbookWebView(context: Context) : WebView(context) {
    var renderStartedAtMs = 0L
    var onDocumentRendered: (() -> Unit)? = null

    override fun startActionMode(callback: ActionMode.Callback): ActionMode? = null

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? = null
}

private const val LEGACY_LINK_TAP_TIMEOUT_MS = 1_000L
private const val POPUP_CAPTURE_TIMEOUT_MS = 1_500L
private const val MAX_NAVIGATION_HISTORY = 50
private const val DISABLED_NAVIGATION_ALPHA = 0.35f
private const val STATE_NAVIGATION_HISTORY = "handbook_navigation_history"
private const val STATE_NAVIGATION_INDEX = "handbook_navigation_index"
