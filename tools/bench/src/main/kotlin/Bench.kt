import com.example.myapplication.update.UpdateDownloader
import com.example.myapplication.update.UpdateRelease
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class Sample(val ms: Long, val bytes: Long)
class Result(val method: String, val run: Int, val seconds: Double, val timeline: List<Sample>)

val client: OkHttpClient = OkHttpClient.Builder()
    .protocols(listOf(Protocol.HTTP_1_1))
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(60, TimeUnit.SECONDS)
    .build()

fun nowMs() = System.nanoTime() / 1_000_000

/** Обычная загрузка: один GET, один поток, пишем в файл. */
suspend fun single(url: String, out: File, counter: AtomicLong) = withContext(Dispatchers.IO) {
    client.newCall(Request.Builder().url(url).build()).execute().use { r ->
        check(r.isSuccessful) { "HTTP ${r.code}" }
        out.outputStream().use { o ->
            val buf = ByteArray(64 * 1024)
            val input = r.body.byteStream()
            while (true) {
                val n = input.read(buf); if (n < 0) break
                o.write(buf, 0, n); counter.addAndGet(n.toLong())
            }
        }
    }
}

/** Классическая параллельная: файл делится на N равных долей, по одной на соединение. */
suspend fun equalParts(url: String, size: Long, parts: Int, out: File, counter: AtomicLong) = withContext(Dispatchers.IO) {
    RandomAccessFile(out, "rw").use { it.setLength(size) }
    val share = (size + parts - 1) / parts
    (0 until parts).map { i ->
        async {
            val start = i * share
            val end = minOf(size - 1, start + share - 1)
            client.newCall(Request.Builder().url(url).header("Range", "bytes=$start-$end").build()).execute().use { r ->
                check(r.code == 206) { "HTTP ${r.code}" }
                RandomAccessFile(out, "rw").use { raf ->
                    raf.seek(start)
                    val buf = ByteArray(64 * 1024)
                    val input = r.body.byteStream()
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        raf.write(buf, 0, n); counter.addAndGet(n.toLong())
                    }
                }
            }
        }
    }.awaitAll()
}

suspend fun timed(method: String, run: Int, size: Long, sha: String, work: suspend (AtomicLong) -> File): Result = coroutineScope {
    val counter = AtomicLong()
    val samples = ArrayList<Sample>()
    val t0 = nowMs()
    val sampler = launch(Dispatchers.Default) {
        while (true) { samples += Sample(nowMs() - t0, counter.get()); delay(100) }
    }
    val file = work(counter)
    val secs = (nowMs() - t0) / 1000.0
    sampler.cancel()
    samples += Sample(nowMs() - t0, size)
    check(file.length() == size) { "$method: size ${file.length()} != $size" }
    check(UpdateDownloader.sha256(file).equals(sha, true)) { "$method: sha mismatch" }
    file.delete()
    Result(method, run, secs, samples)
}

fun main(args: Array<String>) = runBlocking {
    val url = args[0]; val size = args[1].toLong(); val sha = args[2]; val runs = args[3].toInt(); val outJson = File(args[4])
    val dir = File(args[5]).apply { mkdirs() }
    val results = ArrayList<Result>()
    val methods = listOf("single", "equal6", "vetro")
    for (run in 1..runs) {
        // порядок вращается, чтобы ни один способ не получал «лучшее время суток»
        val order = methods.drop((run - 1) % 3) + methods.take((run - 1) % 3)
        for (m in order) {
            val r = try {
                when (m) {
                    "single" -> timed(m, run, size, sha) { c -> File(dir, "s.bin").also { single(url, it, c) } }
                    "equal6" -> timed(m, run, size, sha) { c -> File(dir, "e.bin").also { equalParts(url, size, 6, it, c) } }
                    else -> timed(m, run, size, sha) { c ->
                        val dl = UpdateDownloader(client, File(dir, "v"))
                        val rel = UpdateRelease("bench$run", url, url, size, sha, null)
                        dl.download(rel) { done, _ -> c.set(done) }
                    }
                }
            } catch (e: Exception) { System.err.println("$m run $run FAILED: $e"); continue }
            println("run $run  %-7s %.2f s  %.2f MB/s".format(m, r.seconds, size / 1048576.0 / r.seconds))
            results += r
            File(dir, "v").deleteRecursively()
        }
    }
    val sb = StringBuilder("[")
    results.forEachIndexed { i, r ->
        if (i > 0) sb.append(",")
        sb.append("{\"method\":\"${r.method}\",\"run\":${r.run},\"seconds\":${r.seconds},\"timeline\":[")
        sb.append(r.timeline.joinToString(",") { "[${it.ms},${it.bytes}]" }).append("]}")
    }
    outJson.writeText(sb.append("]").toString())
}
