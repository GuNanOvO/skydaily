plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.nankki.skydaily"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.nankki.skydaily"
        minSdk = 28
        targetSdk = 32
        // 版本号由发布流程通过 -P 传入；本地构建沿用首个版本。
        versionCode = providers.gradleProperty("appVersionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("appVersionName").getOrElse("0.1.0")
    }

    // 四项签名参数齐全时使用正式签名，否则本地构建继续沿用 debug 签名。
    val envKeystorePath = System.getenv("ANDROID_KEYSTORE_PATH")
    val envKeystorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
    val envKeyAlias = System.getenv("ANDROID_KEY_ALIAS")
    val envKeyPassword = System.getenv("ANDROID_KEY_PASSWORD")
    val releaseSigning = if (!envKeystorePath.isNullOrBlank() && !envKeystorePassword.isNullOrBlank() && !envKeyAlias.isNullOrBlank() && !envKeyPassword.isNullOrBlank()) {
        signingConfigs.create("release") {
            storeFile = file(envKeystorePath)
            storePassword = envKeystorePassword
            keyAlias = envKeyAlias
            keyPassword = envKeyPassword
        }
    } else {
        null
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 本地侧载产物：没有签名密钥时沿用 debug 签名，避免额外分发密钥。
            signingConfig = releaseSigning ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        // 本地侧载，不上架 Google Play；targetSdk 32 按仓库约定固定。
        disable += "ExpiredTargetSdkVersion"
    }

    androidResources {
        noCompress += "webp"
    }

}

abstract class GenerateSeasonGuidePolicy : DefaultTask() {
    @get:InputFile
    abstract val policyFile: RegularFileProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @TaskAction
    fun generate() {
        val target = outputDirectory.file("policy/link-policy.json").get().asFile
        target.parentFile.mkdirs()
        policyFile.get().asFile.copyTo(target, overwrite = true)
    }
}

val generateSeasonGuidePolicyAsset = tasks.register<GenerateSeasonGuidePolicy>("generateSeasonGuidePolicyAsset") {
    policyFile.set(rootProject.layout.projectDirectory.file("../src/link-policy.json"))
    outputDirectory.set(layout.buildDirectory.dir("generated/seasonGuideAssets"))
}

androidComponents.onVariants { variant ->
    variant.sources.assets?.addGeneratedSourceDirectory(
        generateSeasonGuidePolicyAsset,
        GenerateSeasonGuidePolicy::outputDirectory,
    )
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.savedstate)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
