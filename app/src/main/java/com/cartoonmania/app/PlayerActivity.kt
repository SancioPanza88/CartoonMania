package com.cartoonmania.app

import android.annotation.SuppressLint
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean

class PlayerActivity : Activity() {

    private lateinit var playerView: PlayerView
    private lateinit var webContainer: FrameLayout
    private lateinit var loading: View

    private var player: ExoPlayer? = null
    private var web: WebView? = null
    private val captured = AtomicBoolean(false)
    private var errors = 0

    private val candidates = ArrayList<String>()
    private var candidateIndex = 0
    private var embedUrl = ""
    private var currentUrl = ""
    private var headerMode = 0

    private val desktopUa =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val mobileUa =
        "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val adHosts = arrayOf(
        "doubleclick.net", "googlesyndication", "google-analytics", "googletagmanager",
        "adservice.google", "amazon-adsystem", "adnxs.com", "adsystem.com",
        "popads", "popcash", "popunder", "popmycdn", "onclickalgo", "onclckds",
        "onclickperformance", "propellerads", "propellerclick", "monetag", "zeropark",
        "exoclick", "exosrv", "realsrv", "juicyads", "trafficjunky", "trafficfactory",
        "hilltopads", "clickadu", "adcash", "galaksion", "a-ads.com",
        "taboola", "outbrain", "criteo", "pubmatic", "rubiconproject", "openx.net",
        "smartadserver", "sharethrough", "media.net", "scorecardresearch",
        "quantserve", "casalemedia", "yieldmo", "gumgum", "sovrn.com", "triplelift",
        "indexww.com", "bidswitch", "adroll", "revcontent", "mgid.com",
        "adskeeper", "servenobid", "hotjar", "histats", "statcounter",
        "addthis", "onesignal", "pushnami", "pushwoosh", "sendpulse",
        "coinzilla", "bitmedia"
    )

    private fun isAd(url: String): Boolean {
        val host = url.substringAfter("://").substringBefore('/').lowercase()
        if (host.isEmpty()) return false
        for (bad in adHosts) if (bad in host) return true
        return false
    }

    private fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", java.io.ByteArrayInputStream(ByteArray(0)))

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_player)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        playerView = findViewById(R.id.player_view)
        webContainer = findViewById(R.id.web_container)
        loading = findViewById(R.id.loading)

        embedUrl = intent.getStringExtra("url").orEmpty()
        title = intent.getStringExtra("label") ?: ""
        if (embedUrl.isEmpty()) { finish(); return }

        captureStream(embedUrl, visible = false)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onBackPressed()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun captureStream(pageUrl: String, visible: Boolean) {
        val w = WebView(this)
        w.setBackgroundColor(0xFF000000.toInt())
        with(w.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = if (pageUrl.contains("loonex")) mobileUa else desktopUa
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            databaseEnabled = false
        }
        try { CookieManager.getInstance().setAcceptCookie(true) } catch (_: Exception) { }

        w.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val u = request.url.toString()
                if (isAd(u)) return blockedResponse()
                if (!visible && request.method == "GET" && looksLikeVideo(u)) {
                    synchronized(candidates) {
                        if (u !in candidates) {
                            candidates.add(u)
                            if (captured.compareAndSet(false, true)) {
                                runOnUiThread { startNative(candidates[0]) }
                            }
                        }
                    }
                }
                return null
            }

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val u = request.url.toString()
                if (!(u.startsWith("http://") || u.startsWith("https://"))) return true
                return isAd(u)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (!(url.startsWith("http://") || url.startsWith("https://"))) return true
                return isAd(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(AUTOPLAY_JS, null)
            }
        }

        webContainer.addView(w, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        webContainer.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        web = w

        if (!visible) {
            w.postDelayed({
                if (!captured.get() && !isFinishing && !isDestroyed) {
                    loading.visibility = View.GONE
                    webContainer.visibility = View.VISIBLE
                    web?.evaluateJavascript(AUTOPLAY_JS, null)
                }
            }, 25000)
        }

        w.loadUrl(pageUrl)
    }

    private fun looksLikeVideo(u: String): Boolean {
        val low = u.substringBefore('#').lowercase()
        return low.contains(".m3u8") || low.contains(".mpd") ||
            ".mp4?" in low || low.endsWith(".mp4") || low.endsWith(".mkv") ||
            "/hls/" in low || "playlist.m3u8" in low
    }

    private fun httpHeaders(videoUrl: String): MutableMap<String, String> {
        val m = HashMap<String, String>()
        if (headerMode == 1) return m
        val uri = Uri.parse(embedUrl)
        val origin = "${uri.scheme ?: "https"}://${uri.host ?: ""}"
        m["Referer"] = "$origin/"
        m["Origin"] = origin
        try {
            CookieManager.getInstance().apply { setAcceptCookie(true); flush() }
                .getCookie(videoUrl)?.let { if (it.isNotEmpty()) m["Cookie"] = it }
        } catch (_: Exception) { }
        return m
    }

    private fun startNative(url: String) {
        if (isFinishing || isDestroyed) return
        loading.visibility = View.GONE
        destroyWeb()
        currentUrl = url

        val dsFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(desktopUa)
            .setDefaultRequestProperties(httpHeaders(url))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .build()
        p.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        p.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                runOnUiThread { onNativeError() }
            }
        })
        p.setMediaItem(MediaItem.fromUri(url))
        p.playWhenReady = true
        p.prepare()

        playerView.player = p
        playerView.visibility = View.VISIBLE
        player = p
    }

    private fun onNativeError() {
        releasePlayer()
        if (headerMode == 0) {
            headerMode = 1
            loading.visibility = View.VISIBLE
            startNative(currentUrl)
            return
        }
        headerMode = 0
        val next = synchronized(candidates) {
            candidateIndex++
            if (candidateIndex < candidates.size) candidates[candidateIndex] else null
        }
        if (next != null) {
            loading.visibility = View.VISIBLE
            startNative(next)
            return
        }
        errors++
        if (errors >= 2 || embedUrl.isEmpty()) {
            loading.visibility = View.GONE
            captureStream(embedUrl, visible = true)
        } else {
            captured.set(false)
            loading.visibility = View.VISIBLE
            captureStream(embedUrl, visible = false)
        }
    }

    private fun destroyWeb() {
        web?.let { w ->
            try {
                w.loadUrl("about:blank")
                w.stopLoading()
                (w.parent as? ViewGroup)?.removeView(w)
                w.destroy()
            } catch (_: Exception) { }
        }
        web = null
    }

    private fun releasePlayer() {
        player?.let { p ->
            playerView.player = null
            p.release()
        }
        player = null
    }

    override fun onPause() {
        web?.onPause()
        player?.pause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web?.onResume()
    }

    override fun onDestroy() {
        releasePlayer()
        destroyWeb()
        super.onDestroy()
    }

    companion object {
        private const val AUTOPLAY_JS = """
            (function(){
              var n=0;
              function clickAll(){
                try{
                  var sels=['#vid_play','.jw-icon-display','.vjs-big-play-button','#play_button',
                    '#btnplay','.play-button','.play_btn','button[aria-label*=lay]','[class*=play]',
                    '[id*=lay]','[id*=Play]','div[class*=overlay]','.pljshclick'];
                  for(var i=0;i<sels.length;i++){
                    var els=document.querySelectorAll(sels[i]);
                    for(var j=0;j<els.length;j++){try{els[j].click();}catch(e){}}
                  }
                  var els=document.querySelectorAll('a,button,div,span,i,b');
                  for(var k=0;k<els.length;k++){
                    var el=els[k];
                    var tx=(el.innerText||'').trim().toLowerCase();
                    if(el.offsetParent!==null&&(tx==='play'||tx==='guarda'||tx==='▶'||tx.indexOf('play')===0)){
                      try{el.click();}catch(e){}
                    }
                  }
                  var v=document.querySelector('video');
                  if(v){try{v.play();}catch(e){}if(!v.paused)n=999;}
                }catch(e){}
              }
              var t=setInterval(function(){n++;clickAll();if(n>=40||n===999){clearInterval(t);}},600);
              clickAll();
            })();
        """
    }
}
