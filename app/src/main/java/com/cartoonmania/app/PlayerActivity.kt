package com.cartoonmania.app

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import java.io.ByteArrayInputStream

class PlayerActivity : Activity() {

    private lateinit var web: WebView
    private var customView: View? = null
    private var originalParent: ViewGroup? = null

    private val desktopUa =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    // Domini pubblicitari / tracker bloccati a livello di rete
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

    // Rimuove i contenitori pubblicitari tipici dalla pagina caricata
    private val cleanupJs = """
        (function(){try{
          var sel=['div[id^="div-gpt-ad"]','div[id^="google_ads"]',
                   'iframe[src*="doubleclick"]','iframe[src*="googlesyndication"]',
                   'iframe[src*="advert"]','iframe[id*="aswift"]',
                   '[class*="advertisement"]','[id*="advertisement"]',
                   '[class*="ad-banner"]','[class*="ad_banner"]',
                   'div[class*="banner-ad"]','div[id*="banner_ad"]'];
          for(var i=0;i<sel.length;i++){try{
            var els=document.querySelectorAll(sel[i]);
            for(var j=0;j<els.length;j++){els[j].remove()}
          }catch(e){}}
          if(document.body){document.body.style.background='#000'}
        }catch(e){}})();
    """.trimIndent()

    private fun isAd(url: String): Boolean {
        val host = url.substringAfter("://").substringBefore('/').lowercase()
        if (host.isEmpty()) return false
        for (bad in adHosts) if (bad in host) return true
        return false
    }

    private fun blockedResponse(): WebResourceResponse =
        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        web = WebView(this)
        web.setBackgroundColor(0xFF000000.toInt())
        web.isVerticalScrollBarEnabled = false
        setContentView(web)

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = desktopUa
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = false
            displayZoomControls = false
            setSupportMultipleWindows(false)
            setGeolocationEnabled(false)
            databaseEnabled = false
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                customView = view
                originalParent = view.parent as? ViewGroup
                (window.decorView as ViewGroup).addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
                actionBar?.hide()
            }

            override fun onHideCustomView() {
                customView?.let {
                    (it.parent as? ViewGroup)?.removeView(it)
                    originalParent?.addView(it)
                }
                customView = null
                actionBar?.show()
            }
        }

        web.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? =
                if (isAd(request.url.toString())) blockedResponse() else null

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val u = request.url.toString()
                // Blocca schemi non web (intent, market, tg, javascript, ecc.)
                if (!(u.startsWith("http://") || u.startsWith("https://"))) return true
                // Blocca redirect verso domini pubblicitari
                return isAd(u)
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (!(url.startsWith("http://") || url.startsWith("https://"))) return true
                return isAd(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                view.evaluateJavascript(cleanupJs, null)
            }
        }

        val url = intent.getStringExtra("url").orEmpty()
        title = intent.getStringExtra("label") ?: ""
        if (url.isNotEmpty()) web.loadUrl(url) else finish()
    }

    override fun onBackPressed() {
        val cv = customView
        if (cv != null) {
            (cv.parent as? ViewGroup)?.removeView(cv)
            originalParent?.addView(cv)
            customView = null
            return
        }
        if (::web.isInitialized && web.canGoBack()) {
            web.goBack()
            return
        }
        super.onBackPressed()
    }

    override fun onPause() {
        if (::web.isInitialized) web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (::web.isInitialized) web.onResume()
    }

    override fun onDestroy() {
        if (::web.isInitialized) {
            web.apply {
                loadUrl("about:blank")
                (parent as? ViewGroup)?.removeView(this)
                destroy()
            }
        }
        super.onDestroy()
    }
}
