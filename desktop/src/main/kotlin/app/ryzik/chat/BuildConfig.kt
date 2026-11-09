package app.ryzik.chat

/** То же, что BuildConfig в Android-приложении. Версию подставляет сборка (build.gradle.kts). */
object BuildConfig {
    val DEFAULT_SERVER: String = System.getenv("RYZIKCHAT_SERVER") ?: "https://ryzik3489-ryzikchat.hf.space"
    val VERSION_NAME: String = BuildInfo.VERSION_NAME
    val VERSION_CODE: Int = BuildInfo.VERSION_CODE
}
