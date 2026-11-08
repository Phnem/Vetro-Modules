import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import vetro.download.*
import java.io.File

fun main() = runBlocking {
    val url = "https://github.com/Phnem/Vetro/releases/download/v3.4.9-Stable/app-release.apk"
    val target = DownloadTarget("v3.4.9", url, 38370427, "89432026cdeafa00b425f622fad28955d9069426dbea66c90063f55806a0209b")
    val client = OkHttpClient.Builder().protocols(listOf(Protocol.HTTP_1_1)).build()
    val dir = File("dl_verify").apply { deleteRecursively(); mkdirs() }
    val dl = ChunkedDownloader(client, dir, filePrefix = "demo", fileExtension = "apk")

    // 1) interrupt in the middle (cancel once > 12 MB are on disk)
    var firstDone = 0L
    val job = launch { try { dl.download(target) { d, _ -> firstDone = d; if (d > 12L * 1048576) cancel() } } catch (e: CancellationException) { } }
    job.join()
    println("interrupted at ${firstDone / 1048576} MB; part exists=${File(dir, "demo-v3.4.9.apk.part").exists()} map exists=${File(dir, "demo-v3.4.9.apk.part.map").exists()}")

    // 2) resume: the first progress report must start from what was already on disk
    var first = -1L
    val t0 = System.nanoTime()
    val f = dl.download(target) { d, _ -> if (first < 0) first = d }
    println("resumed from ${first / 1048576} MB, finished in %.1f s, size ok=${f.length() == target.sizeBytes}, sha ok=${ChunkedDownloader.sha256(f) == target.sha256}".format((System.nanoTime() - t0) / 1e9))
    println("final name=${f.name}; leftovers=${dir.list()?.toList()}")
    f.delete()
    dir.deleteRecursively(); Unit
}
