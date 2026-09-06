import java.util.Properties
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) load(f.inputStream())
}

// 本番APIの公開オリジン。秘匿値ではなく公開設定であり、
// PublicWebConfig.kt の productionWebOrigin と同じ扱いをする。
val productionApiOrigin = "https://rectime-api.rectime-project.workers.dev"

val httpsOriginPattern = Regex("^https://([A-Za-z0-9](?:[A-Za-z0-9.-]*[A-Za-z0-9])?)(?::[1-9][0-9]{0,4})?$")

// release buildへ開発用の接続先が紛れ込むと、アプリは起動するのにAPI通信だけが
// OSに遮断され、実機で触るまで気付けない。ここでbuildを止める。
// 判定は PublicWebConfig.resolvePublicWebUrl と同じ規則に揃えている。
fun requireProductionApiOrigin(value: String?): String {
    val origin = value?.trim()?.trimEnd('/').orEmpty()
    if (origin.isEmpty()) {
        throw GradleException(
            "release buildのAPI_BASE_URLが未設定です。-PRELEASE_API_BASE_URL=<本番origin> を指定してください。"
        )
    }
    val host = httpsOriginPattern.matchEntire(origin)?.groupValues?.get(1)?.lowercase()
        ?: throw GradleException(
            "release buildのAPI_BASE_URLはhttpsのoriginのみ指定できます: $origin"
        )
    val forbiddenHost = host == "localhost" ||
        host == "127.0.0.1" ||
        host == "10.0.2.2" ||
        host.endsWith(".local") ||
        host.endsWith(".invalid") ||
        "placeholder" in host ||
        host == "example.com" ||
        host.endsWith(".example.com") ||
        host.split('.').any { label ->
            label.startsWith("pr-") ||
                label == "preview" ||
                label == "develop" ||
                label == "development" ||
                label == "staging" ||
                label.endsWith("-preview") ||
                label.endsWith("-develop") ||
                label.endsWith("-development") ||
                label.endsWith("-staging")
        }
    // 承認済みの本番originだけを許可する。ホスト名の規則は取りこぼしがあり得るため、
    // PublicWebConfig.resolvePublicWebUrl と同じく完全一致も必須にする。
    if (forbiddenHost || origin != productionApiOrigin) {
        throw GradleException(
            "release buildへ非本番のAPI_BASE_URLは指定できません: $origin"
        )
    }
    return origin
}

fun resolveBuildProperty(name: String): String? =
    (findProperty(name) as String?)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

// buildとcommitの対応を追えるようにする。
fun resolveGitCommit(): String =
    providers.exec {
        commandLine("git", "rev-parse", "--short", "HEAD")
    }.standardOutput.asText.map { it.trim() }.orElse("unknown").get()

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.googleServices)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    jvm()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.androidx.compose.ui.tooling.preview)
            implementation(libs.androidx.activity.compose)
            // 直接は使わないが、play-services-basement が引き込む fragment 1.1.0 では
            // registerForActivityResult がlintのfatalエラーになりrelease buildが通らない。
            implementation(libs.androidx.fragment)
            implementation(libs.androidx.core.splashscreen)
            implementation(project.dependencies.platform(libs.firebase.bom))
            implementation(libs.firebase.messaging)
            implementation(libs.ktor.client.okhttp)
        }
        iosArm64Main.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        iosSimulatorArm64Main.dependencies {
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.compose.icon.collections.fontawesome)
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor)
            implementation(libs.coil.network.cache.control)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        androidUnitTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
        }
        androidInstrumentedTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.androidx.testExt.junit)
            implementation(libs.androidx.test.runner)
        }
        commonTest.dependencies {
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutinesSwing)
            implementation(libs.ktor.client.java)
        }
    }
}

android {
    namespace = "com.rectime.mobile"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        applicationId = "com.rectime.mobile"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = resolveBuildProperty("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = resolveBuildProperty("VERSION_NAME") ?: "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "GIT_COMMIT", "\"${resolveGitCommit()}\"")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("debug") {
            val apiBaseUrl = localProperties.getProperty("API_BASE_URL")
                ?: findProperty("API_BASE_URL") as String?
                ?: providers.environmentVariable("API_BASE_URL").orNull
                ?: "http://10.0.2.2:8787"
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        }
        getByName("release") {
            isMinifyEnabled = false
            val apiBaseUrl = requireProductionApiOrigin(
                resolveBuildProperty("RELEASE_API_BASE_URL") ?: productionApiOrigin
            )
            buildConfigField("String", "API_BASE_URL", "\"$apiBaseUrl\"")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    debugImplementation(libs.androidx.compose.ui.tooling)
}

compose.desktop {
    application {
        mainClass = "com.rectime.mobile.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "com.rectime.mobile"
            packageVersion = "1.0.0"
        }
    }
}
