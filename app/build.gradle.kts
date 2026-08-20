import java.util.Properties

plugins {
    id("com.android.application")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
}

val privateClientProperties = Properties().apply {
    val local = rootProject.file("local.properties")
    if (local.isFile) local.inputStream().use(::load)
}
fun privateClientValue(name: String): String =
    providers.gradleProperty(name).orNull ?: privateClientProperties.getProperty(name).orEmpty()
fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

val formalSigningAlias = "nanfeng-ai"
val formalSigningService = "com.nanzhufeng.ai.signing"
val formalSigningAccount = "keystore-password"
val formalKeystorePath = providers.environmentVariable("NANFENG_AI_KEYSTORE")
    .orElse(providers.gradleProperty("nanfengAi.keystore"))
    .orElse(
        "${System.getProperty("user.home")}/Library/Application Support/" +
            "NanzhufengSigning/NanfengAI-Android/nanfeng-ai-release.jks",
    )
    .get()

fun readFormalSigningPasswordFromMacKeychain(): String? {
    if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return null
    return runCatching {
        val result = providers.exec {
            commandLine(
                "/usr/bin/security",
                "find-generic-password",
                "-w",
                "-a",
                formalSigningAccount,
                "-s",
                formalSigningService,
            )
            isIgnoreExitValue = true
        }
        result.standardOutput.asText.get().trim()
            .takeIf { result.result.get().exitValue == 0 && it.isNotEmpty() }
    }.getOrNull()
}

val formalSigningPassword = providers.environmentVariable("NANFENG_AI_KEYSTORE_PASSWORD")
    .orElse(providers.gradleProperty("nanfengAi.storePassword"))
    .orNull
    ?: readFormalSigningPasswordFromMacKeychain()
val formalKeystoreFile = file(formalKeystorePath)
val formalSigningReady = formalKeystoreFile.isFile && !formalSigningPassword.isNullOrBlank()

android {
    namespace = "com.nanzhufeng.ai"
    compileSdk = 36

    defaultConfig {
        buildConfigField("boolean", "P6E_ACCEPTANCE", "false")
        applicationId = "com.nanzhufeng.ai"
        minSdk = 26
        targetSdk = 36
        versionCode = 51
        versionName = "0.3.0-p10a"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        buildConfigField("String", "NANFENG_SUPABASE_URL", buildConfigString(privateClientValue("SUPABASE_URL")))
        buildConfigField("String", "NANFENG_SUPABASE_PUBLISHABLE_KEY", buildConfigString(privateClientValue("SUPABASE_PUBLISHABLE_KEY").ifBlank { privateClientValue("SUPABASE_ANON_KEY") }))
        buildConfigField("String", "NANFENG_GOOGLE_WEB_CLIENT_ID", buildConfigString(privateClientValue("GOOGLE_WEB_CLIENT_ID")))
    }

    signingConfigs {
        if (formalSigningReady) {
            create("formal") {
                storeFile = formalKeystoreFile
                storePassword = formalSigningPassword
                keyAlias = formalSigningAlias
                keyPassword = formalSigningPassword
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
                enableV4Signing = true
            }
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.findByName("formal")
        }
        getByName("release") {
            signingConfig = signingConfigs.findByName("formal")
            isDebuggable = false
            isMinifyEnabled = false
        }
        create("acceptance") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".p6eacceptance"
            versionNameSuffix = "-p6e-acceptance"
            buildConfigField("boolean", "P6E_ACCEPTANCE", "true")
            signingConfig = signingConfigs.findByName("formal")
        }
    }
}

gradle.taskGraph.whenReady {
    val needsInstallableApp = allTasks.any { task ->
        task.name.matches(Regex("(?i)(assemble|bundle|package|install)(Debug|Release)"))
    }
    if (needsInstallableApp && !formalSigningReady) {
        throw GradleException(
            "南枫 AI 的可安装构建必须使用正式签名。请恢复仓库外 keystore 与 macOS 钥匙串口令，" +
                "或配置 NANFENG_AI_KEYSTORE / NANFENG_AI_KEYSTORE_PASSWORD。",
        )
    }
}

androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            output.outputFileName.set(
                if (variant.buildType == "release") "南枫AI.apk" else "南枫AI-开发验收.apk",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    val roomVersion = "2.8.4"

    implementation(platform("androidx.compose:compose-bom:2026.06.01"))
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    ksp("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
}
