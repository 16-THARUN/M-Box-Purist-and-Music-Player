plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.mbox.core.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:audio"))
    implementation(project(":core:cast"))
    implementation(project(":core:lyrics"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.ext)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation("androidx.mediarouter:mediarouter:1.7.0")

    implementation(libs.glide)
    implementation(libs.glide.compose)
    implementation(libs.androidx.palette)

    implementation(libs.coroutines.android)
    implementation(libs.core.ktx)
}
