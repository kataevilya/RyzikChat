package app.ryzik.chat.data

import app.ryzik.chat.BuildConfig
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Запасной путь до сервера. Туннель иногда не пускает отдельные сети (отвечает 502 или не открывается),
 * а сайт на Vercel достаёт до него всегда: веб-версия ходит через его /rc. Если прямой адрес не ответил,
 * повторяем запрос через сайт и следующие 30 минут сразу идём туда (это помним и между запусками).
 * Прямую попытку ждём недолго: туннель может не отвечать вовсе, и тогда запуск висел бы минуту.
 * WebSocket так не проходит: без него ChatRepository обновляется опросом.
 */
object ServerFallback {
    val PROXY: String = System.getenv("RYZIKCHAT_PROXY") ?: "https://ryzikchat-website.vercel.app/rc"
    private const val STICKY_MS = 30 * 60_000L

    private val stateFile by lazy { File(app.ryzik.chat.platform.Platform.dataDir, "server-fallback") }
    @Volatile private var until = -1L
    val active: Boolean
        get() {
            if (until < 0) until = runCatching { stateFile.readText().trim().toLong() }.getOrDefault(0L)
            return System.currentTimeMillis() < until
        }

    private fun remember() {
        until = System.currentTimeMillis() + STICKY_MS
        runCatching { stateFile.writeText(until.toString()) }
    }

    val interceptor = Interceptor { chain ->
        val req = chain.request()
        val primary = BuildConfig.DEFAULT_SERVER.toHttpUrlOrNull()
        if (primary == null || req.url.host != primary.host || req.header("Upgrade") != null) return@Interceptor chain.proceed(req)
        if (active) return@Interceptor chain.proceed(viaProxy(req))
        val direct = try {
            chain.withConnectTimeout(5, TimeUnit.SECONDS)
                .withReadTimeout(if (req.method == "GET") 10 else 60, TimeUnit.SECONDS)
                .proceed(req)
        } catch (e: IOException) {
            if (chain.call().isCanceled()) throw e
            null
        }
        if (direct != null && direct.code !in 502..504) return@Interceptor direct
        direct?.close()
        chain.proceed(viaProxy(req)).also { if (it.code !in 502..504) remember() }
    }

    private fun viaProxy(req: Request): Request {
        val url = PROXY.toHttpUrl().newBuilder()
            .addEncodedPathSegments(req.url.encodedPath.removePrefix("/"))
            .encodedQuery(req.url.encodedQuery)
            .build()
        return req.newBuilder().url(url).build()
    }
}
