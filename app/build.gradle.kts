import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "app.lexa"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "app.lexa"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 4
        versionName = "0.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Modern phones only: ML Kit ships a ~16 MB native library per ABI, and the other three
        // (armeabi-v7a, x86, x86_64) would add ~45 MB for devices the owner doesn't use.
        ndk { abiFilters += "arm64-v8a" }
    }

    // CI (GitHub release workflow) passes the key in LEXA_KEYSTORE; locally the release build uses the
    // debug key from .tools/android-user-home. Both are the same key, so a GitHub build installs over a
    // local one and the in-app updater works.
    val keystore = System.getenv("LEXA_KEYSTORE")
    signingConfigs {
        if (keystore != null) create("ci") {
            storeFile = file(keystore)
            storePassword = System.getenv("LEXA_STORE_PASSWORD") ?: "android"
            keyAlias = System.getenv("LEXA_KEY_ALIAS") ?: "androiddebugkey"
            keyPassword = System.getenv("LEXA_KEY_PASSWORD") ?: "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("ci") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

roborazzi {
    outputDir.set(file("src/test/snapshots"))
}

// Robolectric gets its android-all jar through Gradle (cached in .tools/gradle-home) and runs offline,
// so nothing is downloaded into ~/.m2 (docs/ARCHITECTURE.md §18).
val robolectricRuntime: Configuration = configurations.create("robolectricRuntime") {
    isTransitive = false
}
val robolectricJarsDir = rootProject.layout.projectDirectory.dir(".tools/robolectric")
val syncRobolectricJars = tasks.register<Sync>("syncRobolectricJars") {
    from(robolectricRuntime)
    into(robolectricJarsDir)
}

tasks.withType<Test>().configureEach {
    dependsOn(syncRobolectricJars)
    maxParallelForks = 1
    maxHeapSize = "1536m"
    // Robolectric's SDK 36 runtime touches FileDescriptor internals.
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED", "--add-opens=java.base/java.io=ALL-UNNAMED")
    systemProperty("robolectric.offline", "true")
    systemProperty("robolectric.dependency.dir", robolectricJarsDir.asFile.absolutePath)
    // Robolectric's native graphics runtime unpacks into the temp dir: keep it inside build/.
    val tmp = layout.buildDirectory.dir("tmp/robolectric").get().asFile
    doFirst { tmp.mkdirs() }
    systemProperty("java.io.tmpdir", tmp.absolutePath)
}

dependencies {
    implementation(project(":domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.mlkit.translate)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.work.testing)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)

    robolectricRuntime(libs.robolectric.android.all)
}
