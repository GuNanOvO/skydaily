buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // AGP 9 自带 Kotlin 编译；把内置 KGP 提升到与 Compose / serialization 插件一致的版本。
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
