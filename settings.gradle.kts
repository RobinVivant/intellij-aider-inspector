pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
    plugins {
        id("org.jetbrains.kotlin.jvm") version "1.9.24"
        id("org.jetbrains.intellij") version "1.17.3"
    }
}

rootProject.name = "aider-inspect"
