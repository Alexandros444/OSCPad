buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        // AGP 9 has built-in Kotlin (defaults to KGP 2.2.10). Pin a newer KGP so it runs on
        // JDK 25 and matches the Compose compiler plugin version below.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.4.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
}
