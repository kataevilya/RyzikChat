pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
    }
}
dependencyResolutionManagement {
    repositories {
        maven("https://maven-central.storage-download.googleapis.com/maven2/")
        mavenCentral()
        // Часть библиотек Compose (androidx.collection, lifecycle) лежит только здесь.
        google()
    }
}
rootProject.name = "RyzikChatDesktop"
