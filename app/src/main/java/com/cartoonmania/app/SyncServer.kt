package com.cartoonmania.app

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException

/**
 * Mini server HTTP per la sincronizzazione tra dispositivi (stesso Wi-Fi,
 * nessuna nuvola). Il ricevente mostra QR + IP/porta/codice; il mittente
 * invia l'export del profilo con POST /up oppure scarica con GET /down.
 * Stesso scheletro di PhotoServer, ma corpo JSON invece di JPEG.
 */
object SyncServer {

    const val MAX_BYTES = 256 * 1024

    class Session(
        val server: ServerSocket,
        val token: String,
        val inbox: File,
        val exportJson: String
    ) {
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
            try {
                s.soTimeout = 30000
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
            fun respond(code: String, type: String, body: ByteArray, fileName: String? = null) {
                val disp = if (fileName != null) "Content-Disposition: attachment; filename=\"$fileName\"\r\n" else ""
                val head = "HTTP/1.1 $code\r\nContent-Type: $type\r\n" +
                    disp + "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
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
            val rawPath = parts[1]
            val path = rawPath.substringBefore("?")
            val query = if (rawPath.contains("?")) rawPath.substringAfter("?") else ""
            fun queryParam(name: String): String {
                for (kv in query.split("&")) {
                    val k = kv.substringBefore("=")
                    if (k == name) return kv.substringAfter("=", "")
                }
                return ""
            }
            if (method == "GET" && (path == "/" || path.isEmpty())) {
                respond("200 OK", "text/html; charset=utf-8", page(this.token).toByteArray(Charsets.UTF_8))
                return
            }
            if (method == "GET" && path == "/down") {
                if (queryParam("t") != this.token) {
                    respond("403 Forbidden", "text/plain", "codice errato".toByteArray())
                    return
                }
                respond(
                    "200 OK", "application/json",
                    exportJson.toByteArray(Charsets.UTF_8), "cartoonmania-sync.json"
                )
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
                    respond("403 Forbidden", "text/plain", "codice errato".toByteArray())
                    return
                }
                if (contentLength <= 0 || contentLength > MAX_BYTES) {
                    respond("413 Too Large", "text/plain", "dati troppo grandi".toByteArray())
                    return
                }
                val data = ByteArray(contentLength)
                var read = 0
                while (read < contentLength) {
                    val n = inp.read(data, read, contentLength - read)
                    if (n < 0) break
                    read += n
                }
                if (read != contentLength || !isSyncJson(data)) {
                    respond("400 Bad Request", "text/plain", "dati non validi".toByteArray())
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

        private fun isSyncJson(d: ByteArray): Boolean {
            return try {
                if (d.size < 10) return false
                val o = org.json.JSONObject(String(d, Charsets.UTF_8))
                o.optInt("v", 0) == 1 && (o.has("fav") || o.has("prog"))
            } catch (_: Exception) {
                false
            }
        }

        private fun page(token: String): String = """
            <!doctype html><html><head><meta charset="utf-8">
            <meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Sync CartoonMania</title></head>
            <body style="background:#0B0B10;color:#fff;font-family:sans-serif;text-align:center;padding:24px;margin:0">
            <h2>Sincronizza CartoonMania</h2>
            <p>Stesso Wi-Fi, nessuna nuvola. Profilo corrente del dispositivo che mostra il QR.</p>
            <p><a href="/down?t=$token" download="cartoonmania-sync.json"
              style="display:inline-block;font-size:18px;padding:12px 28px;background:#7C5CFC;color:#fff;text-decoration:none;border-radius:8px">Scarica i dati di questo dispositivo</a></p>
            <p style="color:#A0A4B8">Poi nell'app ricevente: Impostazioni &rarr; Sincronizza &rarr; Importa file.</p>
            <p style="color:#A0A4B8">Per inviare da un altro telefono a questo dispositivo usa invece
            l'app mittente: Impostazioni &rarr; Sincronizza &rarr; Invia / Scarica.</p>
            </body></html>
        """.trimIndent()
    }

    /** Apre il server su una porta libera; null se impossibile. */
    fun open(inbox: File, token: String, exportJson: String): Session? {
        return try {
            inbox.parentFile?.mkdirs()
            if (inbox.exists()) inbox.delete()
            Session(ServerSocket(0), token, inbox, exportJson)
        } catch (_: Exception) {
            null
        }
    }
}
