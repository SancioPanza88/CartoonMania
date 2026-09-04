package com.cartoonmania.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Mini server HTTP sulla TV: il telefono (stesso Wi-Fi) apre l'URL del QR
 * e invia la foto profilo con un POST di byte grezzi (niente multipart).
 * Nessuna dipendenza, nessuna nuvola.
 */
object PhotoServer {

    const val MAX_BYTES = 6 * 1024 * 1024

    class Session(val server: ServerSocket, val token: String, val inbox: File) {
        val port: Int get() = server.localPort

        @Volatile
        private var running = true

        init {
            Thread { loop() }.start()
        }

        fun stop() {
            running = false
            try {
                server.close()
            } catch (_: Exception) {
            }
        }

        private fun loop() {
            try {
                server.soTimeout = 1000
            } catch (_: Exception) {
            }
            while (running) {
                try {
                    val s = server.accept()
                    try {
                        handle(s)
                    } catch (_: Exception) {
                    } finally {
                        try {
                            s.close()
                        } catch (_: Exception) {
                        }
                    }
                } catch (_: SocketTimeoutException) {
                } catch (_: Exception) {
                    if (!running) return
                }
            }
        }

        private fun handle(s: Socket) {
            // Timeout lungo: foto da 6MB su Wi-Fi lento ci mettono anche un minuto
            try {
                s.soTimeout = 120000
            } catch (_: Exception) {
            }
            val inp = s.getInputStream()
            val reqLine = readLineRaw(inp) ?: return
            var contentLength = 0
            var token = ""
            var expectContinue = false
            while (true) {
                val h = readLineRaw(inp) ?: return
                if (h.isEmpty()) break
                val idx = h.indexOf(':')
                if (idx > 0) {
                    val name = h.substring(0, idx).trim().lowercase()
                    val value = h.substring(idx + 1).trim()
                    if (name == "content-length") contentLength = value.toIntOrNull() ?: 0
                    if (name == "x-token") token = value
                    if (name == "expect" && value.lowercase().startsWith("100")) expectContinue = true
                }
            }
            val out = s.getOutputStream()
            fun respond(code: String, type: String, body: ByteArray) {
                val head = "HTTP/1.1 $code\r\nContent-Type: $type\r\n" +
                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                out.write(head.toByteArray(Charsets.US_ASCII))
                out.write(body)
                out.flush()
            }
            val parts = reqLine.split(" ")
            if (parts.size < 2) {
                respond("400 Bad Request", "text/plain", "no".toByteArray())
                return
            }
            val method = parts[0]
            val path = parts[1]
            if (method == "GET") {
                // La pagina deve contenere il token VERO della sessione,
                // non quello (vuoto) arrivato negli header della GET
                respond("200 OK", "text/html; charset=utf-8", page(this.token).toByteArray(Charsets.UTF_8))
                return
            }
            if (method == "POST" && path.startsWith("/up")) {
                if (expectContinue) {
                    try {
                        out.write("HTTP/1.1 100 Continue\r\n\r\n".toByteArray(Charsets.US_ASCII))
                        out.flush()
                    } catch (_: Exception) {
                        return
                    }
                }
                if (token != this.token) {
                    respond("403 Forbidden", "text/plain", "token errato".toByteArray())
                    return
                }
                if (contentLength <= 0 || contentLength > MAX_BYTES) {
                    respond("413 Too Large", "text/plain", "file troppo grande".toByteArray())
                    return
                }
                val data = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = inp.read(data, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                if (read != contentLength || !isJpeg(data)) {
                    respond("400 Bad Request", "text/plain", "foto non valida (serve JPEG)".toByteArray())
                    return
                }
                try {
                    val tmp = File(inbox.parent, inbox.name + ".part")
                    tmp.writeBytes(data)
                    inbox.delete()
                    tmp.renameTo(inbox)
                } catch (_: Exception) {
                    respond("500 Error", "text/plain", "no".toByteArray())
                    return
                }
                respond("200 OK", "text/plain", "ok".toByteArray())
                return
            }
            respond("404 Not Found", "text/plain", "no".toByteArray())
        }

        /** Legge una riga byte a byte: niente buffering oltre \n (il body binario
         *  resterebbe intrappolato nel buffer di un Reader). */
        private fun readLineRaw(inp: InputStream): String? {
            val buf = ByteArrayOutputStream(256)
            while (true) {
                val b = try { inp.read() } catch (_: Exception) { -1 }
                if (b < 0) return if (buf.size() == 0) null else buf.toString("US-ASCII")
                if (b == '\n'.code) break
                if (b != '\r'.code) buf.write(b)
                if (buf.size() > 8192) return null
            }
            return buf.toString("US-ASCII")
        }

        private fun isJpeg(d: ByteArray): Boolean =
            d.size > 2 && d[0] == 0xFF.toByte() && d[1] == 0xD8.toByte()

        private fun page(token: String): String = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Foto profilo TV</title></head>
            <body style="background:#0B0B10;color:#fff;font-family:sans-serif;text-align:center;padding:24px;margin:0">
            <h2>Foto profilo per la TV</h2>
            <input type="file" id="f" accept="image/*" style="font-size:18px">
            <div id="crop" style="display:none;margin:16px auto;position:relative;width:300px;height:300px;overflow:hidden;background:#222;border-radius:8px;touch-action:none;-webkit-user-select:none;user-select:none">
            <img id="im" style="position:absolute;max-width:none">
            <div style="position:absolute;inset:0;border-radius:50%;box-shadow:0 0 0 999px rgba(0,0,0,.6);pointer-events:none;border:2px solid #7C5CFC;box-sizing:border-box"></div>
            </div>
            <div id="zoomrow" style="display:none;margin:8px">
            <span style="font-size:22px">-</span>
            <input type="range" id="z" min="1" max="3" step="0.05" value="1" style="width:200px">
            <span style="font-size:22px">+</span>
            </div>
            <button id="sendbtn" onclick="send()" style="display:none;font-size:20px;padding:12px 32px;background:#7C5CFC;color:#fff;border:0;border-radius:8px;margin-top:8px">Invia alla TV</button>
            <p id="m"></p>
            <script>
            var S=300, W=0, H=0, k=1, dx=0, dy=0, z=1, objUrl=null;
            var im=document.getElementById('im');
            document.getElementById('f').addEventListener('change', function(){
              var f=this.files[0]; if(!f) return;
              if(objUrl) URL.revokeObjectURL(objUrl);
              objUrl=URL.createObjectURL(f);
              var tmp=new Image();
              tmp.onload=function(){
                W=tmp.naturalWidth; H=tmp.naturalHeight;
                if(!W||!H){document.getElementById('m').textContent='Foto non leggibile';return;}
                z=1; document.getElementById('z').value=1;
                dx=(S-W*base())/2; dy=(S-H*base())/2;
                im.src=objUrl;
                document.getElementById('crop').style.display='block';
                document.getElementById('zoomrow').style.display='block';
                document.getElementById('sendbtn').style.display='inline-block';
                document.getElementById('m').textContent='Trascina e zooma: nel cerchio vedi come viene';
                layout();
              };
              tmp.onerror=function(){document.getElementById('m').textContent='Foto non leggibile';};
              tmp.src=objUrl;
            });
            function base(){ return Math.max(S/W, S/H); }
            function layout(){
              k=base()*z;
              var w=W*k, h=H*k;
              if(w<=S){ dx=(S-w)/2; } else { dx=Math.min(0,Math.max(S-w,dx)); }
              if(h<=S){ dy=(S-h)/2; } else { dy=Math.min(0,Math.max(S-h,dy)); }
              im.style.width=w+'px'; im.style.height=h+'px';
              im.style.left=dx+'px'; im.style.top=dy+'px';
            }
            document.getElementById('z').addEventListener('input', function(){ z=parseFloat(this.value); layout(); });
            (function(){
              var box=document.getElementById('crop'), pid=null, lx=0, ly=0;
              box.addEventListener('pointerdown', function(e){ pid=e.pointerId; lx=e.clientX; ly=e.clientY; try{box.setPointerCapture(pid);}catch(err){} e.preventDefault(); });
              box.addEventListener('pointermove', function(e){
                if(e.pointerId!==pid) return;
                dx+=e.clientX-lx; dy+=e.clientY-ly; lx=e.clientX; ly=e.clientY; layout();
              });
              function up(e){ if(e.pointerId===pid) pid=null; }
              box.addEventListener('pointerup', up); box.addEventListener('pointercancel', up);
            })();
            function send(){
              document.getElementById('m').textContent='Preparo...';
              try{
                var sx=-dx/k, sy=-dy/k, ss=S/k;
                var cv=document.createElement('canvas'); cv.width=512; cv.height=512;
                cv.getContext('2d').drawImage(im, sx, sy, ss, ss, 0, 0, 512, 512);
                cv.toBlob(function(blob){
                  if(!blob){document.getElementById('m').textContent='Foto non leggibile';return;}
                  upload(blob);
                },'image/jpeg',0.9);
              }catch(e){ document.getElementById('m').textContent='Foto non leggibile'; }
            }
            function upload(blob){
              document.getElementById('m').textContent='Invio...';
              var ctrl=new AbortController();
              var to=setTimeout(function(){ctrl.abort();},110000);
              fetch('/up',{method:'POST',headers:{'X-Token':'$token','Content-Type':'application/octet-stream'},body:blob,signal:ctrl.signal})
                .then(function(r){clearTimeout(to);return r.text().then(function(t){document.getElementById('m').textContent=(t==='ok')?'Fatto! Guarda la TV':'Errore HTTP '+r.status+': '+t;});})
                .catch(function(e){clearTimeout(to);document.getElementById('m').textContent='Errore di rete: controlla stesso Wi-Fi e riprova';});
            }
            </script></body></html>
        """.trimIndent()
    }

    /** Apre il server su una porta libera; null se impossibile. */
    fun open(inbox: File, token: String): Session? {
        return try {
            inbox.parentFile?.mkdirs()
            if (inbox.exists()) inbox.delete()
            Session(ServerSocket(0), token, inbox)
        } catch (_: Exception) {
            null
        }
    }

    /** IP locale della TV (stesso Wi-Fi del telefono). */
    fun localIp(): String? = localIps().firstOrNull()

    /** Tutti gli IP locali candidati (se il primo non va, provare gli altri). */
    fun localIps(): List<String> {
        val out = ArrayList<String>()
        try {
            val ifs = NetworkInterface.getNetworkInterfaces() ?: return out
            for (nic in ifs) {
                try {
                    if (!nic.isUp || nic.isLoopback) continue
                } catch (_: Exception) {
                    continue
                }
                for (addr in nic.interfaceAddresses) {
                    val ip = addr.address
                    if (ip is Inet4Address && ip.isSiteLocalAddress) {
                        ip.hostAddress?.let { if (it !in out) out.add(it) }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return out
    }
}
