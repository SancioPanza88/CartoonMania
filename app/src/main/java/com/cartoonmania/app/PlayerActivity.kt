package com.cartoonmania.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
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
import java.util.concurrent.atomic.AtomicBoolean

class PlayerActivity : Activity() {

    private lateinit var playerView: PlayerView
    private lateinit var webContainer: FrameLayout
    private lateinit var loading: View

    private var player: ExoPlayer? = null
    private var web: WebView? = null
    private val captured = AtomicBoolean(false)
    private val errorHandled = AtomicBoolean(false)
    private var errors = 0

    private val desktopUa =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

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

        val url = intent.getStringExtra("url").orEmpty()
        title = intent.getStringExtra("label") ?: ""
        if (url.isEmpty()) { finish(); return }

        captureStream(url, visible = false)
    }

    /**
     * Carica la pagina del player in un WebView e intercetta l'URL reale del video.
     * Con visible=false il WebView resta nascosto: appena catturato l'URL si passa ad ExoPlayer.
     * Con visible=true e' il fallback classico (pagina web a schermo).
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun captureStream(embedUrl: String, visible: Boolean) {
        val w = WebView(this)
        w.setBackgroundColor(0xFF000000.toInt())
        with(w.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = desktopUa
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            databaseEnabled = false
        }

        w.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val u = request.url.toString()
                if (isAd(u)) return blockedResponse()
                if (!visible) {
                    val low = u.substringBefore('?').lowercase()
                    if (!captured.get() &&
                        (low.endsWith(".m3u8") || low.endsWith(".mp4"))
                    ) {
                        if (captured.compareAndSet(false, true)) {
                            runOnUiThread { startNative(u, embedUrl) }
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
                view.postDelayed({
                    if (!captured.get()) view.evaluateJavascript(AUTOPLAY_JS, null)
                }, 2500)
            }
        }

        webContainer.addView(w, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        webContainer.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        web = w

        if (!visible) {
            // Se dopo 20 s non abbiamo catturato nulla, mostriamo la pagina web
            w.postDelayed({
                if (!captured.get() && !isFinishing && !isDestroyed) {
                    loading.visibility = View.GONE
                    webContainer.visibility = View.VISIBLE
                }
            }, 20000)
        }

        w.loadUrl(embedUrl)
    }

    private fun startNative(url: String, referer: String) {
        if (isFinishing || isDestroyed) return
        loading.visibility = View.GONE
        destroyWeb()

        val dsFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(desktopUa)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)
        if (referer.isNotEmpty()) {
            dsFactory.setDefaultRequestProperties(mapOf("Referer" to referer))
        }

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
                if (!errorHandled.compareAndSet(false, true)) return
                runOnUiThread {
                    releasePlayer()
                    errors++
                    val embed = intent.getStringExtra("url").orEmpty()
                    if (errors >= 2 || embed.isEmpty()) {
                        // Troppi fallimenti: mostriamo direttamente la pagina web
                        loading.visibility = View.GONE
                        captureStream(embed, visible = true)
                    } else {
                        captured.set(false)
                        loading.visibility = View.VISIBLE
                        captureStream(embed, visible = false)
                    }
                }
            }
        })
        p.setMediaItem(MediaItem.fromUri(url))
        p.playWhenReady = true
        p.prepare()

        playerView.player = p
        playerView.visibility = View.VISIBLE
        player = p
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
            (function(){try{
              var v=document.querySelector('video');
              if(v){try{v.play();}catch(e){}}
              var sels=['#vid_play','.jw-icon-display','.vjs-big-play-button',
                        '#play_button','#btnplay','.play-button','button[aria-label*=lay]'];
              for(var i=0;i<sels.length;i++){
                try{
                  var el=document.querySelector(sels[i]);
                  if(el){el.click();break;}
                }catch(e){}
              }
            }catch(e){}})();
        """
    }
}
