package com.example.tortugram

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap

/**
 * Servidor HTTP local (127.0.0.1) que actúa de puente entre TDLib y ExoPlayer.
 *
 * Usa el tamaño total real del archivo (conocido de antemano por TDLib, sin
 * descargar nada) para saber con certeza cuándo termina el video, en vez de
 * adivinarlo por el tamaño de cada lectura parcial.
 *
 * isaac-maker 2026
 *
 */
object StreamingServer {

    private const val PORT = 8990
    private const val CHUNK_SIZE = 512 * 1024 // 512 KB por petición a TDLib
    private const val MAX_RETRIES_PER_CHUNK = 40
    private const val RETRY_DELAY_MS = 150L

    // Ventana que se pide "por adelantado" en segundo plano, y margen
    // (respecto a lo ya adelantado) a partir del cual se dispara la
    // siguiente ventana. No afecta la lectura síncrona existente: solo
    // hace que, cuando el loop llegue ahí, los datos ya estén en TDLib.
    private const val PREFETCH_WINDOW = 6L * 1024 * 1024 // 6 MB
    private const val PREFETCH_TRIGGER_MARGIN = 2L * 1024 * 1024 // 2 MB

    // offset hasta el cual ya se pidió prefetch, por fileId.
    private val prefetchedUpTo = ConcurrentHashMap<Int, Long>()

    private var server: EmbeddedServer<*, *>? = null

    fun start() {
        if (server != null) return

        server = embeddedServer(CIO, port = PORT, host = "127.0.0.1") {
            routing {
                get("/stream/{fileId}") {
                    val fileId = call.parameters["fileId"]?.toIntOrNull()
                    val totalSize = call.request.queryParameters["size"]?.toLongOrNull() ?: 0L

                    if (fileId == null) {
                        call.respond(HttpStatusCode.BadRequest)
                        return@get
                    }

                    val rangeHeader = call.request.headers[HttpHeaders.Range]
                    val startByte = rangeHeader
                        ?.removePrefix("bytes=")
                        ?.substringBefore("-")
                        ?.toLongOrNull() ?: 0L

                    val isPartial = rangeHeader != null && totalSize > 0
                    call.response.status(
                        if (isPartial) HttpStatusCode.PartialContent else HttpStatusCode.OK
                    )
                    call.response.header(HttpHeaders.AcceptRanges, "bytes")
                    call.response.header(HttpHeaders.ContentType, "video/mp4")

                    if (totalSize > 0) {
                        val remaining = totalSize - startByte
                        call.response.header(HttpHeaders.ContentLength, remaining.toString())
                        if (isPartial) {
                            call.response.header(
                                HttpHeaders.ContentRange,
                                "bytes $startByte-${totalSize - 1}/$totalSize"
                            )
                        }
                    }
                    // Si totalSize es 0 (no llegó el parámetro), se cae al modo
                    // anterior: sin Content-Length, chunked, mejor que nada.

                    call.respondBytesWriter {
                        var position = startByte

                        while (totalSize <= 0 || position < totalSize) {
                            val wantLength = if (totalSize > 0) {
                                minOf(CHUNK_SIZE.toLong(), totalSize - position)
                            } else {
                                CHUNK_SIZE.toLong()
                            }

                            // Adelantarse: si nos estamos acercando al borde
                            // de lo que ya se pidió con prefetch, disparamos
                            // (sin esperar) la siguiente ventana. La lectura
                            // de abajo sigue igual que antes.
                            val prefetchedTo = prefetchedUpTo.getOrDefault(fileId, position)
                            if (
                                (totalSize <= 0 || prefetchedTo < totalSize) &&
                                position + PREFETCH_TRIGGER_MARGIN >= prefetchedTo
                            ) {
                                val prefetchStart = maxOf(prefetchedTo, position)
                                val prefetchLength = if (totalSize > 0) {
                                    minOf(PREFETCH_WINDOW, totalSize - prefetchStart)
                                } else {
                                    PREFETCH_WINDOW
                                }
                                if (prefetchLength > 0) {
                                    TelegramManager.prefetchRange(
                                        fileId = fileId,
                                        offset = prefetchStart,
                                        length = prefetchLength
                                    )
                                    prefetchedUpTo[fileId] = prefetchStart + prefetchLength
                                }
                            }

                            var partialFile: dev.g000sha256.tdl.dto.File? = null
                            var attempts = 0
                            while (attempts < MAX_RETRIES_PER_CHUNK) {
                                partialFile = TelegramManager.downloadRange(
                                    fileId = fileId,
                                    offset = position,
                                    length = wantLength
                                )
                                if (partialFile != null && partialFile.local.path.isNotEmpty()) break
                                attempts++
                                delay(RETRY_DELAY_MS)
                            }

                            if (partialFile == null || partialFile.local.path.isEmpty()) break

                            val bytesWritten = withContext(Dispatchers.IO) {
                                RandomAccessFile(partialFile.local.path, "r").use { raf ->
                                    val available = raf.length() - position
                                    if (available <= 0) {
                                        0
                                    } else {
                                        raf.seek(position)
                                        val toRead = minOf(wantLength, available).toInt()
                                        val buffer = ByteArray(toRead)
                                        var total = 0
                                        while (total < toRead) {
                                            val n = raf.read(buffer, total, toRead - total)
                                            if (n <= 0) break
                                            total += n
                                        }
                                        if (total > 0) writeFully(buffer, 0, total)
                                        total
                                    }
                                }
                            }

                            if (bytesWritten <= 0) {
                                // TDLib aún no escribió estos bytes en disco: reintenta
                                // la MISMA posición en vez de cortar el stream.
                                delay(RETRY_DELAY_MS)
                                continue
                            }

                            position += bytesWritten


                            if (totalSize <= 0 && bytesWritten < CHUNK_SIZE) break
                        }
                    }
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(500, 1000)
        server = null
        prefetchedUpTo.clear()
    }

    fun urlFor(fileId: Int, totalSize: Long): String =
        "http://127.0.0.1:$PORT/stream/$fileId?size=$totalSize"
}
