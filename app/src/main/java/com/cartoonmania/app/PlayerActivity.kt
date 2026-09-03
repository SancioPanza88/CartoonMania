package com.cartoonmania.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
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
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicBoolean

class PlayerActivity : Activity() {

    private lateinit var playerView: PlayerView
    private lateinit var webContainer: FrameLayout
    private lateinit var loading: View
    // Barra di riserva per il fallback WebView (col player nativo i controlli
    // stanno dentro il controller ExoPlayer e si nascondono da soli)
    private lateinit var navBar: View
    private lateinit var navTitle: TextView
    private lateinit var btnPrev: Button
    private lateinit var btnNext: Button
    private lateinit var btnEpisodes: Button
    // Controlli dentro il controller ExoPlayer
    private lateinit var cTitle: TextView
    private lateinit var cPrev: Button
    private lateinit var cNext: Button
    private lateinit var cEpisodes: Button

    private var player: ExoPlayer? = null
    private var web: WebView? = null
    private val captured = AtomicBoolean(false)
    private var errors = 0

    // Contesto serie per la navigazione tra episodi senza tornare indietro
    private var series: CatalogRepo.Title? = null
    private var epIndex = 0
    private var playerIndex = 0

    // Generazione di caricamento: invalida i callback in ritardo dell'episodio precedente
    private var sessionId = 0

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
        navBar = findViewById(R.id.nav_bar)
        navTitle = findViewById(R.id.nav_title)
        btnPrev = findViewById(R.id.btn_prev)
        btnNext = findViewById(R.id.btn_next)
        btnEpisodes = findViewById(R.id.btn_episodes)
        cTitle = findViewById(R.id.c_title)
        cPrev = findViewById(R.id.c_prev)
        cNext = findViewById(R.id.c_next)
        cEpisodes = findViewById(R.id.c_episodes)

        // Il controller si nasconde da solo dopo pochi secondi e riappare
        // al tocco o coi tasti del telecomando
        playerView.controllerShowTimeoutMs = 3500

        findViewById<View>(R.id.btn_close).setOnClickListener { finish() }
        findViewById<View>(R.id.c_close).setOnClickListener { finish() }
        btnPrev.setOnClickListener { goPrev() }
        btnNext.setOnClickListener { goNext() }
        btnEpisodes.setOnClickListener { showEpisodePicker() }
        cPrev.setOnClickListener { goPrev() }
        cNext.setOnClickListener { goNext() }
        cEpisodes.setOnClickListener { showEpisodePicker() }

        hideSystemUi()

        val startUrl = intent.getStringExtra("url").orEmpty()
        if (startUrl.isEmpty()) { finish(); return }

        series = intent.getStringExtra("slug")?.let { s ->
            CatalogRepo.titles.firstOrNull { it.slug == s }
        }
        val s = series
        if (s != null && s.episodes.isNotEmpty()) {
            // Col player nativo i controlli stanno nel controller ExoPlayer;
            // la barra di riserva serve solo nel fallback WebView
            navBar.visibility = View.GONE
            playEpisodeAt(
                intent.getIntExtra("ep", 0).coerceIn(0, s.episodes.size - 1),
                intent.getIntExtra("pi", 0)
            )
        } else {
            // Avvio legacy senza contesto serie: nessuna navigazione episodi
            navBar.visibility = View.GONE
            embedUrl = startUrl
            title = intent.getStringExtra("label") ?: ""
            captureStream(embedUrl, visible = false)
        }
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    private fun hasPrev() = series != null && epIndex > 0

    private fun hasNext(): Boolean {
        val s = series ?: return false
        return epIndex < s.episodes.size - 1
    }

    /** Stesso server tra un episodio e l'altro quando disponibile. */
    private fun bestPlayerIdx(index: Int): Int {
        val s = series ?: return 0
        val n = s.episodes.getOrNull(index)?.players?.size ?: 0
        return if (playerIndex < n) playerIndex else 0
    }

    private fun goPrev() {
        if (hasPrev()) playEpisodeAt(epIndex - 1, bestPlayerIdx(epIndex - 1))
        else Toast.makeText(this, R.string.end_of_series, Toast.LENGTH_SHORT).show()
    }

    private fun goNext() {
        if (hasNext()) playEpisodeAt(epIndex + 1, bestPlayerIdx(epIndex + 1))
        else Toast.makeText(this, R.string.end_of_series, Toast.LENGTH_SHORT).show()
    }

    private fun updateNav() {
        val s = series ?: return
        val ep = s.episodes.getOrNull(epIndex)
        val label =
            if (ep == null) s.title
            else "${s.title} — ${ep.label.ifEmpty { getString(R.string.play) }}"
        navTitle.text = label
        cTitle.text = label
        btnPrev.isEnabled = hasPrev()
        btnNext.isEnabled = hasNext()
        btnPrev.alpha = if (btnPrev.isEnabled) 1f else 0.4f
        btnNext.alpha = if (btnNext.isEnabled) 1f else 0.4f
        cPrev.isEnabled = hasPrev()
        cNext.isEnabled = hasNext()
        cPrev.alpha = if (cPrev.isEnabled) 1f else 0.4f
        cNext.alpha = if (cNext.isEnabled) 1f else 0.4f
    }

    /** Ricomincia da zero il caricamento sul nuovo episodio (nativo o fallback web). */
    private fun playEpisodeAt(index: Int, playerIdx: Int) {
        val s = series ?: return
        if (index !in s.episodes.indices) return
        sessionId++

        epIndex = index
        val ep = s.episodes[index]
        playerIndex = if (playerIdx in ep.players.indices) playerIdx else 0
        val p = ep.players.getOrNull(playerIndex) ?: return

        embedUrl = p.url
        title = "${s.title} — ${ep.label.ifEmpty { getString(R.string.play) }}"

        releasePlayer()
        suspendWeb()
        synchronized(candidates) { candidates.clear() }
        candidateIndex = 0
        captured.set(false)
        errors = 0
        headerMode = 0
        currentUrl = ""

        loading.visibility = View.VISIBLE
        playerView.visibility = View.GONE
        webContainer.visibility = View.GONE
        navBar.visibility = View.GONE
        updateNav()
        captureStream(embedUrl, visible = false)
    }

    private fun showEpisodePicker() {
        val s = series ?: return
        val labels = s.episodes.mapIndexed { i, ep ->
            val l = ep.label.ifEmpty { getString(R.string.play) }
            if (i == epIndex) "▶ $l" else l
        }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.episode_list)
            .setItems(labels) { _, which -> playEpisodeAt(which, bestPlayerIdx(which)) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Autoplay diretto: finito un episodio parte subito il successivo. */
    private fun onEpisodeEnded() {
        if (hasNext()) goNext()
        else Toast.makeText(this, R.string.end_of_series, Toast.LENGTH_SHORT).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            onBackPressed()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_NEXT) {
            goNext()
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS) {
            goPrev()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    /** WebView unica riutilizzata tra gli episodi: crearla ogni volta costa
     *  1-2s e decine di MB, insostenibile sulle TV con poca RAM. */
    @SuppressLint("SetJavaScriptEnabled")
    private fun obtainWeb(): WebView {
        web?.let { return it }
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
        try { CookieManager.getInstance().setAcceptCookie(true) } catch (_: Exception) { }
        webContainer.addView(w, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        web = w
        return w
    }

    /** Mette in pausa la WebView senza distruggerla (risparmia CPU/RAM sulle TV). */
    private fun suspendWeb() {
        web?.let { w ->
            try {
                w.onPause()
                w.pauseTimers()
                w.stopLoading()
            } catch (_: Exception) { }
        }
        webContainer.visibility = View.GONE
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun captureStream(pageUrl: String, visible: Boolean) {
        val w = obtainWeb()
        try {
            w.onResume()
            w.resumeTimers()
        } catch (_: Exception) { }
        w.settings.userAgentString = if (pageUrl.contains("loonex")) mobileUa else desktopUa

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

        webContainer.visibility = if (visible) View.VISIBLE else View.INVISIBLE

        if (!visible) {
            val sid = sessionId
            w.postDelayed({
                if (sid == sessionId && !captured.get() && !isFinishing && !isDestroyed) {
                    loading.visibility = View.GONE
                    webContainer.visibility = View.VISIBLE
                    // Senza player nativo non c'e' il controller: mostra la barra di riserva
                    if (series != null) navBar.visibility = View.VISIBLE
                    web?.evaluateJavascript(AUTOPLAY_JS, null)
                }
            }, 25000)
        }

        w.stopLoading()
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
        var origin = "${uri.scheme ?: "https"}://${uri.host ?: ""}"
        // videoserver.loonex.eu ha hotlink protection: accetta solo Referer dal
        // sito, senza (o col Referer del videoserver stesso) risponde 403
        if ("loonex" in (uri.host ?: "")) origin = "https://loonex.eu"
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
        suspendWeb()
        navBar.visibility = View.GONE
        currentUrl = url

        val dsFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(desktopUa)
            .setDefaultRequestProperties(httpHeaders(url))
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(30000)

        // Buffer corti: le TV hanno poca RAM e i default (50s) rallentano l'avvio
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(8000, 25000, 1500, 2000)
            .build()
        // Max 1080p: i SoC delle TV arrancano oltre, e le fonti sono comunque <=1080p
        val trackSelector = DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSize(1920, 1080))
        }

        val p = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dsFactory))
            .setLoadControl(loadControl)
            .setTrackSelector(trackSelector)
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

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) runOnUiThread { onEpisodeEnded() }
            }
        })
        p.setMediaItem(MediaItem.fromUri(url))
        p.playWhenReady = true
        p.prepare()

        playerView.player = p
        playerView.visibility = View.VISIBLE
        playerView.showController()
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
            if (series != null) navBar.visibility = View.VISIBLE
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
