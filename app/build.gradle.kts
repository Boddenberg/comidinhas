import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
    id("org.jetbrains.kotlin.kapt")
}

// ── Copia os logos PNG da raiz do projeto para res/drawable-nodpi ───────────
val copyLogoPng by tasks.registering(Copy::class) {
    val dst = file("src/main/res/drawable-nodpi")
    from(rootProject.file("images/logo-comidinhas.png")) { rename { "logo_comidinhas_full.png" } }
    from(rootProject.file("images/comidinhas-bellsp.png")) { rename { "logo_comidinhas_bell.png" } }
    into(dst)
}
afterEvaluate {
    tasks.matching { it.name.startsWith("generate") && it.name.contains("Resources") }.configureEach {
        dependsOn(copyLogoPng)
    }
    tasks.matching { it.name == "preBuild" }.configureEach {
        dependsOn(copyLogoPng)
    }
}
// ─────────────────────────────────────────────────────────────────────────────

android {
    namespace = "br.com.boddenb.comidinhas"
    compileSdk = 36

    defaultConfig {
        applicationId = "br.com.boddenb.comidinhas"
        minSdk = 28
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val localProperties = Properties()
        val localPropertiesFile = rootProject.file("local.properties")
        if (localPropertiesFile.exists()) {
            localProperties.load(FileInputStream(localPropertiesFile))
        }
        val openaiApiKey = localProperties.getProperty("OPENAI_API_KEY", "")
        buildConfigField("String", "OPENAI_API_KEY", "\"$openaiApiKey\"")

        // Inject Google Maps API key as string resource
        val mapsKey = localProperties.getProperty("GOOGLE_MAPS_API_KEY", "")
        resValue("string", "google_maps_key", mapsKey)

        // Supabase config via BuildConfig (do not hardcode secrets)
        val supabaseUrl = localProperties.getProperty("SUPABASE_URL", "")
        val supabaseAnonKey = localProperties.getProperty("SUPABASE_ANON_KEY", "")
        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")

        val braveApiKey = localProperties.getProperty("BRAVE_API_KEY", "")
        buildConfigField("String", "BRAVE_API_KEY", "\"$braveApiKey\"")

        val unsplashClientId = localProperties.getProperty("UNSPLASH_CLIENT_ID", "")
        buildConfigField("String", "UNSPLASH_CLIENT_ID", "\"$unsplashClientId\"")
    }

    buildTypes {
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
        buildConfig = true
        compose = true
    }

    lint {
        // Desabilitar verificações incompatíveis com Kotlin 2.0+
        disable.add("RememberInComposition")
        disable.add("NullSafeMutableLiveData")
        // Suprimir o warning do compileSdk 36
        disable.add("OldTargetApi")
        abortOnError = false
    }

    packaging {
        resources.excludes.add("META-INF/INDEX.LIST")
        resources.excludes.add("META-INF/io.netty.versions.properties")
        resources.excludes.add("META-INF/DEPENDENCIES")
        resources.excludes.add("META-INF/LICENSE")
        resources.excludes.add("META-INF/LICENSE.txt")
        resources.excludes.add("META-INF/license.txt")
        resources.excludes.add("META-INF/NOTICE")
        resources.excludes.add("META-INF/NOTICE.txt")
        resources.excludes.add("META-INF/notice.txt")
        resources.excludes.add("META-INF/ASL2.0")
        resources.excludes.add("META-INF/*.kotlin_module")
    }
}

kapt {
    correctErrorTypes = true
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    // ✅ precisa desta
    implementation("io.ktor:ktor-client-logging:3.0.1")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.compose.material:material-icons-extended:1.7.5")
    // (opcional) para ver logs SLF4J no console:
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("io.ktor:ktor-client-android:3.0.1")
    implementation("io.ktor:ktor-server-netty:3.0.1")
    implementation("io.ktor:ktor-server-core:3.0.1")
    implementation("io.ktor:ktor-server-cors:3.0.1")
    implementation("io.ktor:ktor-server-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")
    implementation("io.ktor:ktor-server-status-pages:3.0.1") // Dependência para StatusPages
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

    // Ktor Client (para o app consumir a API)
    implementation("io.ktor:ktor-client-core:3.0.1")
    implementation("io.ktor:ktor-client-android:3.0.1")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.1")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.1")

    // Navigation Compose
    implementation("androidx.navigation:navigation-compose:2.8.2")

    // Retrofit + OkHttp (estáveis) - já estamos usando Ktor, mas mantendo por enquanto
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // Kotlinx Serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    // Coil (compose)
    implementation("io.coil-kt:coil-compose:2.7.0")

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.foundation)

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.52")
    kapt("com.google.dagger:hilt-compiler:2.52")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // Google Places, Maps, Location
    implementation("com.google.android.libraries.places:places:5.0.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.maps.android:maps-compose:2.11.4")

    // Accompanist - Swipe Refresh
    implementation("com.google.accompanist:accompanist-swiperefresh:0.36.0")

    // Accompanist - Permissions
    implementation("com.google.accompanist:accompanist-permissions:0.36.0")

    // Accompanist - Navigation Animation
    implementation("com.google.accompanist:accompanist-navigation-animation:0.36.0")

    // Lifecycle ViewModel Compose
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.4")

    // Google Fonts (Nunito)
    implementation("androidx.compose.ui:ui-text-google-fonts:1.7.5")

    // AWS SDK para Android - REMOVIDAS (migrado para Supabase)
    // implementation("com.amazonaws:aws-android-sdk-core:2.77.0")
    // implementation("com.amazonaws:aws-android-sdk-ddb:2.77.0")
    // implementation("com.amazonaws:aws-android-sdk-s3:2.77.0")

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Forçar JavaPoet consistente para Hilt/Processors
    implementation("com.squareup:javapoet:1.13.0")
    kapt("com.squareup:javapoet:1.13.0")

    // Supabase (Kotlin, multiplatform)
    implementation(platform("io.github.jan-tennert.supabase:bom:3.0.3"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")

    // Jsoup (HTML scraping - TudoGostoso)
    implementation("org.jsoup:jsoup:1.18.3")
}
