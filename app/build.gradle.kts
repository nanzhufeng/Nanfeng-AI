import java.io.File
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
val userGradleProperties = Properties().apply {
    val userGradlePropertiesFile = File(System.getProperty("user.home"), ".gradle/gradle.properties")
    if (userGradlePropertiesFile.isFile) userGradlePropertiesFile.inputStream().use(::load)
}
fun privateClientValue(name: String): String =
    providers.gradleProperty(name).orNull
        ?: privateClientProperties.getProperty(name)
        ?: userGradleProperties.getProperty(name)
        ?: ""
fun userGradleValue(name: String): String = userGradleProperties.getProperty(name).orEmpty()
fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

data class NanfengAiReleaseV2SigningConfig(
    val storeFilePath: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

fun nonBlank(value: String?): String? = value?.takeIf(String::isNotBlank)

fun resolveNanfengAiReleaseV2SigningConfig(
    source: String,
    storeFilePath: String?,
    storePassword: String?,
    keyAlias: String?,
    keyPassword: String?,
): NanfengAiReleaseV2SigningConfig? {
    val values = listOf(storeFilePath, storePassword, keyAlias, keyPassword)
    if (values.all { it.isNullOrBlank() }) return null
    if (values.any { it.isNullOrBlank() }) {
        throw GradleException(
            "南枫 AI release v2 签名的 $source 配置不完整；必须同时提供 storeFile、" +
                "storePassword、keyAlias 和 keyPassword。",
        )
    }
    return NanfengAiReleaseV2SigningConfig(
        storeFilePath = storeFilePath!!,
        storePassword = storePassword!!,
        keyAlias = keyAlias!!,
        keyPassword = keyPassword!!,
    )
}

val environmentReleaseV2SigningConfig = resolveNanfengAiReleaseV2SigningConfig(
    source = "环境变量",
    storeFilePath = nonBlank(providers.environmentVariable("NANFENG_AI_RELEASE_V2_KEYSTORE").orNull),
    storePassword = nonBlank(providers.environmentVariable("NANFENG_AI_RELEASE_V2_STORE_PASSWORD").orNull),
    keyAlias = nonBlank(providers.environmentVariable("NANFENG_AI_RELEASE_V2_KEY_ALIAS").orNull),
    keyPassword = nonBlank(providers.environmentVariable("NANFENG_AI_RELEASE_V2_KEY_PASSWORD").orNull),
)
val userGradleReleaseV2SigningConfig = resolveNanfengAiReleaseV2SigningConfig(
    source = "用户级 ~/.gradle/gradle.properties",
    storeFilePath = nonBlank(userGradleValue("nanfengAi.releaseV2.keystore")),
    storePassword = nonBlank(userGradleValue("nanfengAi.releaseV2.storePassword")),
    keyAlias = nonBlank(userGradleValue("nanfengAi.releaseV2.keyAlias")),
    keyPassword = nonBlank(userGradleValue("nanfengAi.releaseV2.keyPassword")),
)
val formalSigningConfig = environmentReleaseV2SigningConfig ?: userGradleReleaseV2SigningConfig
val formalKeystoreFile = formalSigningConfig?.let { file(it.storeFilePath) }
val formalSigningReady = formalKeystoreFile?.isFile == true

android {
    namespace = "com.nanzhufeng.ai"
    compileSdk = 36

    defaultConfig {
        buildConfigField("boolean", "P6E_ACCEPTANCE", "false")
        buildConfigField("boolean", "P6_V2_JOURNAL_INTERRUPT_ACCEPTANCE", "false")
        buildConfigField("boolean", "P6_V2_SCHEMA38_UPGRADE_ACCEPTANCE", "false")
        applicationId = "com.nanzhufeng.ai"
        minSdk = 26
        targetSdk = 36
        versionCode = 68
        versionName = "1.0.1"
        buildConfigField("long", "BUILD_TIME_EPOCH_SECONDS", "${System.currentTimeMillis() / 1000L}L")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    defaultConfig {
        buildConfigField(
            "String",
            "NANFENG_SUPABASE_URL",
            buildConfigString(privateClientValue("nanfeng.ai.cloud.url").ifBlank { privateClientValue("SUPABASE_URL") }),
        )
        buildConfigField(
            "String",
            "NANFENG_SUPABASE_PUBLISHABLE_KEY",
            buildConfigString(
                privateClientValue("nanfeng.ai.cloud.publishableKey").ifBlank {
                    privateClientValue("SUPABASE_PUBLISHABLE_KEY").ifBlank { privateClientValue("SUPABASE_ANON_KEY") }
                },
            ),
        )
        buildConfigField(
            "String",
            "NANFENG_GOOGLE_WEB_CLIENT_ID",
            buildConfigString(privateClientValue("nanfeng.ai.cloud.googleServerClientId").ifBlank { privateClientValue("GOOGLE_WEB_CLIENT_ID") }),
        )
    }

    signingConfigs {
        if (formalSigningReady) {
            create("formal") {
                storeFile = formalKeystoreFile
                storePassword = formalSigningConfig!!.storePassword
                keyAlias = formalSigningConfig.keyAlias
                keyPassword = formalSigningConfig.keyPassword
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
        create("acceptanceV2") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".p6eacceptancev2"
            versionNameSuffix = "-p6e-acceptance-v2"
            buildConfigField("boolean", "P6E_ACCEPTANCE", "true")
            signingConfig = signingConfigs.findByName("formal")
        }
        create("p6V2SafEmptyAcceptance") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".p6v2safemptyacceptance"
            versionNameSuffix = "-p6-v2-saf-empty-acceptance"
            buildConfigField("boolean", "P6E_ACCEPTANCE", "false")
            signingConfig = signingConfigs.findByName("formal")
        }
        create("p6V2FullOwnerAcceptance") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".p6v2fullowneracceptance"
            versionNameSuffix = "-p6-v2-full-owner-acceptance"
            buildConfigField("boolean", "P6E_ACCEPTANCE", "false")
            signingConfig = signingConfigs.findByName("formal")
        }
        create("p6V2JournalInterruptAcceptance") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".p6v2journalinterruptacceptance"
            versionNameSuffix = "-p6-v2-journal-interrupt-acceptance"
            buildConfigField("boolean", "P6E_ACCEPTANCE", "false")
            buildConfigField("boolean", "P6_V2_JOURNAL_INTERRUPT_ACCEPTANCE", "true")
            signingConfig = signingConfigs.findByName("formal")
        }
        create("p6V2Schema38UpgradeAcceptance") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".p6v2schema38upgradeacceptance"
            versionNameSuffix = "-p6-v2-schema38-upgrade-acceptance"
            buildConfigField("boolean", "P6E_ACCEPTANCE", "false")
            buildConfigField("boolean", "P6_V2_SCHEMA38_UPGRADE_ACCEPTANCE", "true")
            signingConfig = signingConfigs.findByName("formal")
        }
        create("p5dAcceptance") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".p5dacceptance"
            versionNameSuffix = "-p5d-acceptance"
            signingConfig = signingConfigs.findByName("formal")
        }
        create("searchAttachmentAcceptance") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".searchattachmentacceptance"
            versionNameSuffix = "-search-attachment-acceptance"
            signingConfig = signingConfigs.findByName("formal")
        }
    }
}

gradle.taskGraph.whenReady {
    val needsInstallableApp = allTasks.any { task ->
        task.path.startsWith(":app:") && (
            task.name.matches(Regex("(?i)(assemble|bundle|install).+")) ||
                (
                    task.name.matches(Regex("(?i)package.+")) &&
                        !task.name.endsWith("Resources", ignoreCase = true)
                    )
            )
    }
    if (needsInstallableApp && !formalSigningReady) {
        throw GradleException(
            "南枫 AI 的可安装构建缺少 release v2 签名配置。优先配置全部环境变量 " +
                "NANFENG_AI_RELEASE_V2_KEYSTORE、NANFENG_AI_RELEASE_V2_STORE_PASSWORD、" +
                "NANFENG_AI_RELEASE_V2_KEY_ALIAS、NANFENG_AI_RELEASE_V2_KEY_PASSWORD；" +
                "或配置用户级 ~/.gradle/gradle.properties 的 nanfengAi.releaseV2.*。" +
                "不会访问 macOS Keychain、不会重试、不会生成替代签名。",
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
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.work:work-runtime-ktx:2.10.5")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    ksp("androidx.room:room-compiler:$roomVersion")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("androidx.room:room-testing:$roomVersion")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.16.1")
    // Executes the same FTS5 DDL and triggers on host SQLite; Robolectric's SQLite omits FTS5.
    testImplementation("org.xerial:sqlite-jdbc:3.41.2.2")
}
