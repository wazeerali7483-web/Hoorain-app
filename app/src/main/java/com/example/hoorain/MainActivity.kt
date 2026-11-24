package com.example.hoorain

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private val PAGE = "https://wazeer123jarvis.neocities.org/jarvis.html"

    private lateinit var web: WebView
    private var sr: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var listening = false
    private var speaking = false
    private var lang = "hi-IN"
    private val h = Handler(Looper.getMainLooper())

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CALL_PHONE,
                Manifest.permission.POST_NOTIFICATIONS
            ),
            1
        )
        web = WebView(this)
        setContentView(web)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.webViewClient = WebViewClient()
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(r: PermissionRequest) {
                runOnUiThread { r.grant(r.resources) }
            }
        }
        web.addJavascriptInterface(Bridge(), "Android")
        tts = TextToSpeech(this) { }
        web.loadUrl(PAGE)
    }

    override fun onRequestPermissionsResult(c: Int, p: Array<out String>, r: IntArray) {
        super.onRequestPermissionsResult(c, p, r)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) {
            ContextCompat.startForegroundService(this, Intent(this, JarvisService::class.java))
        }
    }

    inner class Bridge {
        @JavascriptInterface fun startListen(l: String) { h.post { lang = l; listening = true; listen() } }
        @JavascriptInterface fun stopListen() { h.post { listening = false; sr?.cancel() } }
        @JavascriptInterface fun speak(t: String, l: String) { h.post { say(t, l) } }
        @JavascriptInterface fun open(url: String) { h.post { openUrl(url) } }
    }

    private fun js(s: String) { web.post { web.evaluateJavascript(s, null) } }

    private fun listen() {
        if (!listening || speaking) return
        if (sr == null) {
            sr = SpeechRecognizer.createSpeechRecognizer(this)
            sr!!.setRecognitionListener(object : RecognitionListener {
                override fun onResults(r: Bundle?) {
                    val t = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!t.isNullOrBlank()) js("window.onNativeSpeech(" + JSONObject.quote(t) + ")")
                    h.postDelayed({ listen() }, 300)
                }
                override fun onPartialResults(p: Bundle?) {
                    val t = p?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    if (!t.isNullOrBlank()) js("window.onNativePartial(" + JSONObject.quote(t) + ")")
                }
                override fun onError(e: Int) { h.postDelayed({ listen() }, 800) }
                override fun onReadyForSpeech(p: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(v: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onEvent(t: Int, p: Bundle?) {}
            })
        }
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang)
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        try { sr!!.startListening(i) } catch (e: Exception) { }
    }

    private fun say(t: String, l: String) {
        speaking = true
        sr?.cancel()
        js("window.avOn()")
        tts?.language = Locale.forLanguageTag(if (l.isBlank()) "hi-IN" else l)
        tts?.setPitch(1.25f)
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { h.post { speaking = false; js("window.avOff()"); listen() } }
            override fun onError(id: String?) { h.post { speaking = false; js("window.avOff()"); listen() } }
        })
        tts?.speak(t, TextToSpeech.QUEUE_FLUSH, null, "u1")
    }

    private fun openUrl(u: String) {
        try {
            if (u.startsWith("tel:")) {
                val ok = ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) ==
                    PackageManager.PERMISSION_GRANTED
                startActivity(Intent(if (ok) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse(u)))
            } else {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(u)))
            }
        } catch (e: Exception) { }
    }

    override fun onDestroy() {
        sr?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }
}
