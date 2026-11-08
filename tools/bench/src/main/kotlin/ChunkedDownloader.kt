package vetro.download

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

/** The downloaded file does not match what was declared (size or SHA-256). */
class IntegrityException(message: String) : IOException(message)

/** The server answered a range request with the whole file: a parallel download is impossible. */
internal class RangeIgnoredException(message: String) : IOException(message)

/** A chunk takes too long over one connection: continue it on a fresh one instead of waiting. */
private class SlowChunkException : IOException("chunk is too slow")

/** What to download and how to verify it. */
data class DownloadTarget(
    /** Identifies this download; it becomes part of the file name. */
    val id: String,
    val url: String,
    /** Expected size in bytes; 0 = unknown (a single stream is used and the size check is skipped). */
    val sizeBytes: Long,
    /** Expected SHA-256 as hex, or null to skip the check. */
    val sha256: String?,
)

/**
 * Chunk-queue downloader with resume and verification.
 *
 * A large file of known size is cut into small chunks ([chunkBytes]) that [connections] workers pull
 * from one shared queue ([downloadParallel]). The classic layout - N equal shares, one per
 * connection - leaves the tail to the slowest connection: the fast ones are long done while the last
 * percent crawls over one socket. With a queue, a worker that gets free simply takes the next chunk,
 * so a slow connection is only ever responsible for one chunk, not for a share of the file.
 *
 * Interrupted downloads continue from the first chunk that is not on disk (HTTP Range). The file gets
 * its final name only when it is complete and verified; until then it lives as `.part`.
 *
 * @param client must speak HTTP/1.1 (`protocols(listOf(Protocol.HTTP_1_1))`); otherwise parallel
 *  requests to one host are multiplexed over a single HTTP/2 connection and there is no gain.
 */
class ChunkedDownloader(
    private val client: OkHttpClient,
    private val dir: File,
    private val filePrefix: String = "download",
    private val fileExtension: String = "bin",
    private val bufferSize: Int = 64 * 1024,
    private val connections: Int = CONNECTIONS,
    private val parallelMinBytes: Long = PARALLEL_MIN_BYTES,
    private val retryDelayMs: Long = RETRY_DELAY_MS,
    private val chunkBytes: Long = CHUNK_BYTES,
    /** How long one attempt on a chunk may take before the connection is considered slow and replaced. */
    private val slowChunkMs: Long = SLOW_CHUNK_MS,
) {

    /** Where the finished file of this download ends up. */
    fun fileFor(id: String): File = File(dir, "$filePrefix-${safe(id)}.$fileExtension")

    private fun partFor(id: String): File = File(dir, "$filePrefix-${safe(id)}.$fileExtension.part")

    /** Which chunks of `.part` are fully on disk (see [downloadParallel]). */
    private fun mapFor(id: String): File = File(dir, "$filePrefix-${safe(id)}.$fileExtension.part.map")

    /** The finished file, if this download is already complete (it only appears under its name after verification). */
    fun downloaded(id: String): File? = fileFor(id).takeIf { it.isFile && it.length() > 0 }

    /** Delete everything except the file of [keepId]: old versions and partial files must not pile up. */
    fun cleanExcept(keepId: String?) {
        val keep = keepId?.let { fileFor(it).name }
        dir.listFiles()?.forEach { file -> if (file.name != keep) file.delete() }
    }

    /**
     * @param onProgress called while downloading with (bytes done, bytes total); at most once per
     *  [PROGRESS_STEP_BYTES] (single stream) or [PROGRESS_TICK_MS] (several connections) and never
     *  from several threads at once.
     * @throws IntegrityException if the result does not match [target]; the bad file is deleted.
     */
    suspend fun download(target: DownloadTarget, onProgress: (done: Long, total: Long) -> Unit): File =
        withContext(Dispatchers.IO) {
            dir.mkdirs()
            downloaded(target.id)?.let { return@withContext it }
            val part = partFor(target.id)
            val parallel = connections > 1 && target.sizeBytes >= parallelMinBytes &&
                try {
                    downloadParallel(target, part, onProgress)
                    true
                } catch (e: RangeIgnoredException) {
                    // The server does not understand Range: chunks are useless, download in one piece.
                    part.delete()
                    mapFor(target.id).delete()
                    false
                }
            if (!parallel) downloadSequential(target, part, onProgress)
            verify(part, target)
            val file = fileFor(target.id)
            if (file.exists()) file.delete()
            if (!part.renameTo(file)) {
                part.delete()
                throw IOException("cannot move the downloaded file")
            }
            file
        }

    /**
     * `.part` is created at full length up front, chunks are written at their own offsets, and finished
     * chunks are recorded in `.part.map` (header `v1 <size> <chunk>`, then one line per finished chunk).
     * An interrupted download resumes from the first unrecorded chunk; a half-written chunk starts over.
     * There is no final stitching pass over the file.
     *
     * Release links usually redirect to a signed CDN address. Going through the redirect for every
     * chunk is expensive, so the address is resolved once by a probe request and then used directly.
     * The signature is short-lived: on an access error the address is resolved again.
     * @throws RangeIgnoredException if the server answered a chunk request with the whole file.
     */
    private suspend fun downloadParallel(
        target: DownloadTarget,
        part: File,
        onProgress: (done: Long, total: Long) -> Unit,
    ) = coroutineScope {
        val total = target.sizeBytes
        val chunkCount = ((total + chunkBytes - 1) / chunkBytes).toInt()
        val map = mapFor(target.id)
        val header = "v1 $total $chunkBytes"

        // Chunks of an older layout (`.part.N`) and foreign state are not resumed: clean up and start over.
        deleteLegacySegments(target.id)
        val finished = BooleanArray(chunkCount)
        val resumable = part.isFile && part.length() == total && map.isFile &&
            map.readLines().firstOrNull() == header
        if (resumable) {
            map.readLines().drop(1).forEach { line ->
                line.trim().toIntOrNull()?.takeIf { it in 0 until chunkCount }?.let { finished[it] = true }
            }
        } else {
            part.delete()
            RandomAccessFile(part, "rw").use { it.setLength(total) }
            map.writeText("$header\n")
        }
        fun chunkSize(i: Int) = minOf(chunkBytes, total - i * chunkBytes)
        val done = AtomicLong((0 until chunkCount).filter { finished[it] }.sumOf { chunkSize(it) })

        val resolver = UrlResolver(target.url)
        val queue = Channel<Int>(Channel.UNLIMITED)
        val pending = (0 until chunkCount).filter { !finished[it] }
        pending.forEach { queue.trySend(it) }
        queue.close()
        val mapLock = Any()

        // Progress is reported by one collector, not by the workers: the consumer is not thread-safe.
        val ticker = launch {
            while (true) {
                onProgress(done.get(), total)
                delay(PROGRESS_TICK_MS)
            }
        }
        try {
            if (pending.isNotEmpty()) resolver.probe()
            RandomAccessFile(part, "rw").use { raf ->
                val channel = raf.channel
                (0 until minOf(connections, maxOf(pending.size, 1))).map {
                    async {
                        for (index in queue) {
                            val start = index * chunkBytes
                            fetchChunk(resolver, start, start + chunkSize(index) - 1, channel, done)
                            synchronized(mapLock) { map.appendText("$index\n") }
                        }
                    }
                }.awaitAll()
            }
        } finally {
            ticker.cancel()
        }
        onProgress(done.get(), total)
        map.delete()
    }

    /** The address used for chunk requests: the final one (after redirects), re-resolvable on demand. */
    private inner class UrlResolver(private val original: String) {
        @Volatile
        var url: HttpUrl = original.toHttpUrl()
            private set
        private val lock = Mutex()

        /** Probe request for the first byte: finds the final address and checks that the server honours Range. */
        suspend fun probe() = lock.withLock {
            var failures = 0
            while (true) {
                try {
                    val request = Request.Builder().url(original)
                        .header("Accept", "application/octet-stream")
                        .header("Range", "bytes=0-0")
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (response.code == 200) throw RangeIgnoredException("HTTP 200 on a range request")
                        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                        url = response.request.url
                    }
                    return@withLock
                } catch (e: RangeIgnoredException) {
                    throw e
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    if (++failures >= CHUNK_ATTEMPTS) throw e
                    delay(retryDelayMs * failures)
                }
            }
        }
    }

    /**
     * One chunk [start]..[end] (inclusive) is written into [channel] at its own offset; a broken
     * connection continues from the same byte. An attempt longer than [slowChunkMs] is aborted and
     * continued on a new connection: a CDN's per-connection speed varies a lot, and a slow socket must
     * not hold back the last percent of the download.
     */
    private suspend fun fetchChunk(
        resolver: UrlResolver,
        start: Long,
        end: Long,
        channel: FileChannel,
        done: AtomicLong,
    ) {
        val wanted = end - start + 1
        var have = 0L
        var failures = 0
        var slowRestarts = 0
        while (have < wanted) {
            val attemptStartedAt = System.nanoTime()
            try {
                val request = Request.Builder().url(resolver.url)
                    .header("Accept", "application/octet-stream")
                    .header("Range", "bytes=${start + have}-$end")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code == 200) throw RangeIgnoredException("HTTP 200 on a range request")
                    if (response.code in EXPIRED_LINK_CODES) {
                        // The CDN signature expired: resolve the address again; this attempt counts as failed.
                        resolver.probe()
                        throw IOException("HTTP ${response.code}")
                    }
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                    val body = response.body
                    val buffer = ByteArray(bufferSize)
                    body.byteStream().use { input ->
                        while (have < wanted) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer, 0, minOf(buffer.size.toLong(), wanted - have).toInt())
                            if (n < 0) break
                            writeFully(channel, ByteBuffer.wrap(buffer, 0, n), start + have)
                            have += n
                            done.addAndGet(n.toLong())
                            val elapsedMs = (System.nanoTime() - attemptStartedAt) / 1_000_000
                            if (have < wanted && elapsedMs > slowChunkMs) throw SlowChunkException()
                        }
                    }
                }
                if (have == wanted) return
                throw IOException("chunk cut short")
            } catch (e: RangeIgnoredException) {
                throw e
            } catch (e: CancellationException) {
                throw e
            } catch (e: SlowChunkException) {
                if (++slowRestarts > MAX_SLOW_RESTARTS) throw IOException("chunk is too slow")
            } catch (e: IOException) {
                // Weak mobile networks drop connections: a couple of paused retries beat starting over.
                if (++failures >= CHUNK_ATTEMPTS) throw e
                delay(retryDelayMs * failures)
            }
        }
    }

    private fun writeFully(channel: FileChannel, buffer: ByteBuffer, position: Long) {
        var at = position
        while (buffer.hasRemaining()) at += channel.write(buffer, at)
    }

    /** Remove `.part.N` pieces of the older layout (equal shares, one per connection). */
    private fun deleteLegacySegments(id: String) {
        val prefix = "${partFor(id).name}."
        dir.listFiles()?.forEach { f ->
            val suffix = f.name.takeIf { it.startsWith(prefix) }?.removePrefix(prefix)
            if (suffix?.toIntOrNull() != null) f.delete()
        }
    }

    /** Plain single-stream download with resume of one `.part`. */
    private suspend fun downloadSequential(
        target: DownloadTarget,
        part: File,
        onProgress: (done: Long, total: Long) -> Unit,
    ) {
        // A full-length `.part` from a parallel download (with a chunk map) cannot be resumed from its end.
        if (mapFor(target.id).exists()) {
            part.delete()
            mapFor(target.id).delete()
        }
        var have = if (part.isFile) part.length() else 0L
        // A partial file larger than the declared size is garbage from an earlier attempt.
        if (target.sizeBytes > 0 && have > target.sizeBytes) {
            part.delete()
            have = 0L
        }

        val request = Request.Builder().url(target.url)
            .header("Accept", "application/octet-stream")
            .apply { if (have > 0) header("Range", "bytes=$have-") }
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 416 && have > 0) {
                // The server says there is nothing to resume or the file differs: start over.
                part.delete()
                throw IOException("range not satisfiable")
            }
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val resumed = response.code == 206
            if (!resumed) have = 0L
            val total = when {
                target.sizeBytes > 0 -> target.sizeBytes
                body.contentLength() > 0 -> have + body.contentLength()
                else -> 0L
            }
            RandomAccessFile(part, "rw").use { out ->
                if (resumed) out.seek(have) else out.setLength(0)
                val buffer = ByteArray(bufferSize)
                var done = have
                var reported = done
                onProgress(done, total)
                body.byteStream().use { input ->
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        done += n
                        if (done - reported >= PROGRESS_STEP_BYTES) {
                            reported = done
                            onProgress(done, total)
                        }
                    }
                }
                onProgress(done, total)
            }
        }
    }

    private fun verify(file: File, target: DownloadTarget) {
        if (target.sizeBytes > 0 && file.length() != target.sizeBytes) {
            file.delete()
            throw IntegrityException("size ${file.length()} != ${target.sizeBytes}")
        }
        val expected = target.sha256 ?: return
        val actual = sha256(file)
        if (!actual.equals(expected, ignoreCase = true)) {
            file.delete()
            throw IntegrityException("sha256 mismatch")
        }
    }

    companion object {
        const val PROGRESS_STEP_BYTES = 128L * 1024

        /** How often the collector reports progress during a multi-connection download. */
        const val PROGRESS_TICK_MS = 250L

        /** How many connections pull the file at once. */
        const val CONNECTIONS = 6

        /**
         * Chunk size in the shared queue. Smaller = shorter tail (a slow connection gets little) but
         * more requests; 1 MB chunks on a 33 MB file mean ~30 requests in total.
         */
        const val CHUNK_BYTES = 1L * 1024 * 1024

        /** Below this size splitting is pointless: handshakes eat the gain. */
        const val PARALLEL_MIN_BYTES = 4L * 1024 * 1024

        /** An attempt on one chunk longer than this marks the connection as slow; it is replaced. */
        const val SLOW_CHUNK_MS = 15_000L

        private const val MAX_SLOW_RESTARTS = 8
        private const val CHUNK_ATTEMPTS = 4
        private const val RETRY_DELAY_MS = 1_000L

        /** Responses with which a signed CDN address says it has expired. */
        private val EXPIRED_LINK_CODES = setOf(400, 401, 403, 410)

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    digest.update(buffer, 0, n)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        /** The id goes into the file name: everything except letters, digits, dot and hyphen is replaced. */
        private fun safe(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
