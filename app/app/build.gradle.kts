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
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 本地侧载产物：沿用 debug 签名，避免额外分发密钥。
            signingConfig = signingConfigs.getByName("debug")
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
