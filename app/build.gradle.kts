import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing. Nothing secret lives in the repo. Sources, first match wins:
//  1. env POCKETOTP_KEYSTORE_FILE / POCKETOTP_KEYSTORE_PASSWORD / POCKETOTP_KEY_ALIAS / POCKETOTP_KEY_PASSWORD (CI)
//  2. a properties file (storeFile, storePassword, keyAlias, keyPassword) at env POCKETOTP_KEYSTORE_PROPERTIES,
//     or keystore.properties in the repo root (gitignored)
// Without either, assembleRelease still builds, but the APK is unsigned.
val releaseSigning: Map<String, String>? = run {
    val env = System.getenv()
    val envFile = env["POCKETOTP_KEYSTORE_FILE"]
    if (!envFile.isNullOrBlank()) {
        return@run mapOf(
            "storeFile" to envFile,
            "storePassword" to env["POCKETOTP_KEYSTORE_PASSWORD"].orEmpty(),
            "keyAlias" to (env["POCKETOTP_KEY_ALIAS"] ?: "pocketotp"),
            "keyPassword" to (env["POCKETOTP_KEY_PASSWORD"] ?: env["POCKETOTP_KEYSTORE_PASSWORD"]).orEmpty(),
        )
    }
    val propsFile = env["POCKETOTP_KEYSTORE_PROPERTIES"]?.let { file(it) } ?: rootProject.file("keystore.properties")
    if (!propsFile.isFile) return@run null
    val p = Properties().apply { propsFile.inputStream().use { load(it) } }
    listOf("storeFile", "storePassword", "keyAlias", "keyPassword").associateWith { p.getProperty(it).orEmpty() }
}

// R8 is off by default (not yet verified on a device); opt in with -Ppocketotp.minify=true.
val minifyRelease = (findProperty("pocketotp.minify") as String?)?.toBoolean() == true

android {
    namespace = "com.pocketotp.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.pocketotp.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 12
        versionName = "2.5.1"
    }

    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = file(releaseSigning.getValue("storeFile"))
                storePassword = releaseSigning.getValue("storePassword")
                keyAlias = releaseSigning.getValue("keyAlias")
                keyPassword = releaseSigning.getValue("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildTypes {
        release {
            if (releaseSigning != null) signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = minifyRelease
            isShrinkResources = minifyRelease
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isDebuggable = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")

    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
}
