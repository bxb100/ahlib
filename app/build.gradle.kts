import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val productionApiBaseUrl = "https://www.lib.ah.cn/"
val releaseVersionCode = providers.gradleProperty("releaseVersionCode")
    .map { it.toInt() }
    .getOrElse(1)
val releaseVersionName = providers.gradleProperty("releaseVersionName")
    .getOrElse("1.0.0")
val readerQrNativeRoot = rootProject.layout.projectDirectory.dir("native/reader-qr-native")
val readerQrNativeManifest = readerQrNativeRoot.file("Cargo.toml")
val readerQrNativeOutput = layout.buildDirectory.dir("generated/readerQrNative/jniLibs")
val readerQrNativePrebuilt = layout.projectDirectory.dir("prebuilt/readerQrNative/jniLibs")
val cargoExecutable = providers.gradleProperty("cargo.bin")
    .orElse(
        providers.systemProperty("user.home").map { userHome ->
            file(userHome).resolve(".cargo/bin/cargo")
                .takeIf { it.isFile }
                ?.absolutePath
                ?: "cargo"
        },
    )
val forcePrebuiltReaderQrNative = providers.gradleProperty("usePrebuiltReaderQrNative")
    .map(String::toBoolean)
    .getOrElse(false)
val buildReaderQrNativeFromSource =
    readerQrNativeManifest.asFile.isFile && !forcePrebuiltReaderQrNative
val readerQrNativeJniLibs = if (buildReaderQrNativeFromSource) {
    readerQrNativeOutput.get().asFile
} else {
    readerQrNativePrebuilt.asFile
}
val rustlsPlatformVerifierAar = layout.projectDirectory.file(
    "libs/rustls-platform-verifier.aar",
)

fun String.asBuildConfigString(): String =
    "\"${replace("\\", "\\\\").replace("\"", "\\\"")}\""

kotlin {
    jvmToolchain(25)
    compilerOptions {
        jvmTarget = JvmTarget.fromTarget("25")
        freeCompilerArgs.add(
            "-opt-in=androidx.compose.foundation.style.ExperimentalFoundationStyleApi",
        )
        freeCompilerArgs.add(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi",
        )
    }
}

android {
    namespace = "cn.ahlib.reservation"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "cn.ahlib.reservation"
        minSdk = 35
        targetSdk = 37
        versionCode = releaseVersionCode
        versionName = releaseVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
        ndk {
            abiFilters += "arm64-v8a"
        }
        buildConfigField(
            "String",
            "API_BASE_URL",
            productionApiBaseUrl.asBuildConfigString(),
        )
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            isShrinkResources = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
                "proguard-debug-rules.pro",
            )
            val debugApiBaseUrl = providers.gradleProperty("debugApiBaseUrl")
                .orElse(productionApiBaseUrl)
                .get()
            buildConfigField(
                "String",
                "API_BASE_URL",
                debugApiBaseUrl.asBuildConfigString(),
            )
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    
    packaging {
        dex {
            useLegacyPackaging = true
        }
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }

    sourceSets.getByName("main").jniLibs.directories.add(
        readerQrNativeJniLibs.absolutePath,
    )
}

val buildReaderQrNative = if (buildReaderQrNativeFromSource) {
    val androidComponents = extensions.getByType<ApplicationAndroidComponentsExtension>()
    tasks.register<Exec>("buildReaderQrNative") {
        description = "cargo ndk build reader-qr-native"
        val nativeLibrary = readerQrNativeOutput.map { output ->
            output.file("arm64-v8a/libreader_qr_native.so")
        }
        inputs.files(
            fileTree(readerQrNativeRoot) {
                exclude("target/**")
            },
        )
        outputs.file(nativeLibrary)
        workingDir(readerQrNativeRoot)
        environment(
            "ANDROID_HOME",
            androidComponents.sdkComponents.sdkDirectory.get().asFile.absolutePath,
        )
        commandLine(
            cargoExecutable.get(),
            "ndk",
            "-t",
            "arm64-v8a",
            "-P",
            android.defaultConfig.minSdk ?: 35,
            "-o",
            readerQrNativeOutput.get().asFile.absolutePath,
            "build",
            "--release",
        )
    }
} else {
    null
}

tasks.named("preBuild").configure {
    if (buildReaderQrNative != null) {
        dependsOn(buildReaderQrNative)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.webkit)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.google.material)

    implementation(libs.retrofit.core)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.core)
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.kotlinx.coroutines.android)
    implementation(files(rustlsPlatformVerifierAar.asFile))

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.mlkit.vision)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.zxing.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
