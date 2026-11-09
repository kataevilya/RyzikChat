import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.serialization") version "2.1.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.1.0"
    id("org.jetbrains.compose") version "1.7.3"
}

group = "app.ryzik.chat"

// Версия общая с приложением для Android: берём её из android/app/build.gradle.kts.
val androidGradle = file("../android/app/build.gradle.kts").readText()
val appVersionCode = Regex("""versionCode\s*=\s*(\d+)""").find(androidGradle)!!.groupValues[1].toInt()
val appVersionName = Regex("""versionName\s*=\s*"([^"]+)"""").find(androidGradle)!!.groupValues[1]

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
    sourceSets.main { kotlin.srcDir(layout.buildDirectory.dir("generated/buildinfo")) }
}
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

val generateBuildInfo by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildinfo")
    inputs.property("code", appVersionCode)
    inputs.property("name", appVersionName)
    outputs.dir(out)
    doLast {
        val f = out.get().file("app/ryzik/chat/BuildInfo.kt").asFile
        f.parentFile.mkdirs()
        f.writeText(
            "package app.ryzik.chat\n\ninternal object BuildInfo {\n" +
                "    const val VERSION_NAME = \"$appVersionName\"\n" +
                "    const val VERSION_CODE = $appVersionCode\n}\n"
        )
    }
}
tasks.named("compileKotlin") { dependsOn(generateBuildInfo) }

// JavaFX нужен для звука и видео; берём сборку под систему, на которой собираем.
val fxPlatform = System.getProperty("os.name").let {
    when {
        it.startsWith("Windows") -> "win"
        it.startsWith("Mac") -> if (System.getProperty("os.arch") == "aarch64") "mac-aarch64" else "mac"
        else -> "linux"
    }
}

dependencies {
    for (m in listOf("base", "graphics", "media", "swing")) implementation("org.openjfx:javafx-$m:21.0.5:$fxPlatform")
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(compose.animation)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.crypto.tink:tink:1.15.0")
    implementation("net.java.dev.jna:jna-platform:5.15.0")
}

compose.desktop {
    application {
        mainClass = "app.ryzik.chat.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "-Xms256m", "-Xmx1g", "-XX:+UseG1GC", "-XX:+UseStringDeduplication")
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "RyzikChat"
            // Установщик Windows требует версию вида 1.2.3 с первой цифрой больше нуля.
            packageVersion = "1.0.$appVersionCode"
            description = "RyzikChat messenger"
            vendor = "RyzikChat"
            copyright = "RyzikChat"
            modules("java.naming", "java.net.http", "jdk.crypto.ec", "jdk.unsupported")
            windows {
                iconFile.set(project.file("icon.ico"))
                menuGroup = "RyzikChat"
                shortcut = true
                menu = true
                dirChooser = true
                perUserInstall = true
                // Постоянный код продукта: новая версия ставится поверх старой.
                upgradeUuid = "6c1f8f0e-3b8e-4f5c-9a51-6b8d2a7f4c21"
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
                packageName = "ryzikchat"
                debMaintainer = "ryzikchat@users.noreply.github.com"
                menuGroup = "Network;InstantMessaging"
                appCategory = "net"
                shortcut = true
            }
        }
    }
}

// Все библиотеки для запуска в одну папку (нужно только для отладки без доступа к Google Maven).
tasks.register<Copy>("copyRuntimeDeps") {
    from(configurations.runtimeClasspath)
    into(layout.buildDirectory.dir("runtime-deps"))
}
