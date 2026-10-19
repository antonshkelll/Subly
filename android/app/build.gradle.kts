plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("io.github.takahirom.roborazzi")
}

// Адрес сервера по умолчанию. Можно поменять прямо в приложении: экран входа → «Адрес сервера».
val apiUrl = providers.gradleProperty("subly.apiUrl").getOrElse("https://subly.antonshkel.ru")

android {
    namespace = "ru.antonshkel.subly"
    compileSdk = 35

    defaultConfig {
        applicationId = "ru.antonshkel.subly"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        buildConfigField("String", "API_URL", "\"$apiUrl\"")
    }

    signingConfigs {
        // Свой ключ — через переменные окружения (в CI это секреты репозитория).
        // Без них сборка подписывается открытым демо-ключом из keystore/: так APK из разных
        // сборок ставятся поверх друг друга. Для RuStore/Google Play нужен свой ключ, см. docs/ANDROID.md.
        create("release") {
            val ks = System.getenv("SUBLY_KEYSTORE")
            if (ks != null) {
                storeFile = file(ks)
                storePassword = System.getenv("SUBLY_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("SUBLY_KEY_ALIAS")
                keyPassword = System.getenv("SUBLY_KEY_PASSWORD")
            } else {
                storeFile = rootProject.file("keystore/subly-demo.jks")
                storePassword = "subly-demo"
                keyAlias = "subly"
                keyPassword = "subly-demo"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            manifestPlaceholders["cleartext"] = "false"
        }
        debug {
            applicationIdSuffix = ".debug"
            manifestPlaceholders["cleartext"] = "true"   // для локального сервера по http в эмуляторе
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true          // java.time на Android 7
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
        }
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/INDEX.LIST", "/META-INF/*.version", "DebugProbesKt.bin")
    }
}

composeCompiler {
    // Отчёты о стабильности классов: ./gradlew assembleRelease -PcomposeReports
    if (project.hasProperty("composeReports")) {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.05.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")

    val ktor = "3.1.3"
    implementation("io.ktor:ktor-client-okhttp:$ktor")
    implementation("io.ktor:ktor-client-content-negotiation:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")

    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.43.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.43.0")
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
