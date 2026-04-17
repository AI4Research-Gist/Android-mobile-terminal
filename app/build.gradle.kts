import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val localEnv = Properties().apply {
    val envFile = rootProject.file(".env.local")
    if (envFile.exists()) {
        envFile.reader().use(::load)
    }
}

fun resolveSecret(name: String, defaultValue: String = ""): String {
    return System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localEnv.getProperty(name)?.takeIf { it.isNotBlank() }
        ?: defaultValue
}

fun escapeBuildConfigValue(value: String): String {
    return value.replace("\\", "\\\\").replace("\"", "\\\"")
}

android {
    namespace = "com.example.ai4research"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.ai4research"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        buildConfigField(
            "String",
            "NOCO_BASE_URL",
            "\"${escapeBuildConfigValue(resolveSecret("AI4RESEARCH_NOCO_BASE_URL"))}\""
        )
        buildConfigField(
            "String",
            "NOCO_TOKEN",
            "\"${escapeBuildConfigValue(resolveSecret("AI4RESEARCH_NOCO_TOKEN"))}\""
        )
        buildConfigField(
            "String",
            "SILICONFLOW_BASE_URL",
            "\"${escapeBuildConfigValue(resolveSecret("AI4RESEARCH_SILICONFLOW_BASE_URL", "https://api.siliconflow.cn/v1/"))}\""
        )
        buildConfigField(
            "String",
            "SILICONFLOW_API_KEY",
            "\"${escapeBuildConfigValue(resolveSecret("AI4RESEARCH_SILICONFLOW_API_KEY"))}\""
        )
        buildConfigField(
            "String",
            "FIRECRAWL_BASE_URL",
            "\"${escapeBuildConfigValue(resolveSecret("AI4RESEARCH_FIRECRAWL_BASE_URL", "https://api.firecrawl.dev/v1/"))}\""
        )
        buildConfigField(
            "String",
            "FIRECRAWL_API_KEY",
            "\"${escapeBuildConfigValue(resolveSecret("AI4RESEARCH_FIRECRAWL_API_KEY"))}\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            resValue("string", "app_name", "Gist Debug")
        }
        create("recovery") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".recovery"
            versionNameSuffix = "-recovery"
            resValue("string", "app_name", "Gist Recovery")
            matchingFallbacks += listOf("debug")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended:1.6.0")

    implementation(libs.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.compose.markdown)

    implementation(libs.androidx.biometric)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
