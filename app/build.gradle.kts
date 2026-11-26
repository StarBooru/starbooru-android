import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.roborazzi)
}

// The version of the next release. versionCode grows with it, and stays above
// the reference's 1 so this app updates it in place.
val appVersion = "0.1.0"

fun versionCodeOf(version: String): Int {
    val (major, minor, patch) =
        version
            .substringBefore('-')
            .split('.')
            .map { it.toIntOrNull() ?: 0 }
            .plus(listOf(0, 0, 0))
    return major * 1_000_000 + minor * 1_000 + patch + 100
}

// The ABIs to build the core for: -Pstarbooru.abis=arm64-v8a for one.
val coreAbis =
    providers
        .gradleProperty("starbooru.abis")
        .getOrElse("arm64-v8a,x86_64")
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }

android {
    namespace = "eu.filipnone.starbooru"
    compileSdk = 37
    compileSdkMinor = 2
    ndkVersion = "30.0.16248370"

    defaultConfig {
        applicationId = "eu.filipnone.starbooru"
        // The reference's, so everyone it ran for can take the update.
        minSdk = 24
        targetSdk = 37
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += coreAbis }
    }

    buildTypes {
        release {
            // Signed outside Gradle, on the owner's machine (docs/development.md).
            signingConfig = null
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    packaging {
        jniLibs.useLegacyPackaging = false
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.systemProperty("robolectric.pixelCopyRenderMode", "hardware") }
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkGeneratedSources = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

/** Builds the Rust core for each ABI with cargo-ndk, into jniLibs' layout. */
abstract class CargoNdkTask : DefaultTask() {
    @get:Inject abstract val exec: ExecOperations

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Input abstract val release: Property<Boolean>

    @get:Input abstract val abis: ListProperty<String>

    @get:Internal abstract val workspace: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val args = mutableListOf("cargo", "ndk", "--platform", "24")
        abis.get().forEach { args += listOf("-t", it) }
        args += listOf("-o", out.absolutePath, "build", "-p", "starbooru-android-core", "--lib")
        if (release.get()) args += "--release"
        exec.exec {
            workingDir = workspace.get().asFile
            commandLine(args)
        }
    }
}

/** Generates the Kotlin bindings from a host build of the core. */
abstract class UniffiBindingsTask : DefaultTask() {
    @get:Inject abstract val exec: ExecOperations

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    @get:Internal abstract val workspace: DirectoryProperty

    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val ws = workspace.get().asFile
        // Its own target folder, so the bindgen feature doesn't rebuild the
        // libraries the APK takes.
        val target = ws.resolve("target/bindgen")
        exec.exec {
            workingDir = ws
            environment("CARGO_TARGET_DIR", target.absolutePath)
            commandLine("cargo", "build", "-p", "starbooru-android-core", "--lib")
        }
        exec.exec {
            workingDir = ws
            environment("CARGO_TARGET_DIR", target.absolutePath)
            commandLine(
                "cargo", "run", "--features", "bindgen", "--bin", "uniffi-bindgen", "--",
                "generate", "--library", target.resolve("debug/libstarbooru_android_core.so").absolutePath,
                "--language", "kotlin", "--out-dir", out.absolutePath, "--no-format"
            )
        }
    }
}

val rustSources =
    files(
        rootProject.file("Cargo.toml"),
        rootProject.file("Cargo.lock"),
        rootProject.fileTree("core") { include("**/*.rs", "Cargo.toml", "uniffi.toml") }
    )

val uniffiBindings =
    tasks.register<UniffiBindingsTask>("uniffiBindings") {
        sources.from(rustSources)
        workspace.set(rootProject.layout.projectDirectory)
        outputDir.set(layout.buildDirectory.dir("generated/uniffi/kotlin"))
    }

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val core =
            tasks.register<CargoNdkTask>("cargoNdk$name") {
                sources.from(rustSources)
                release.set(variant.buildType == "release")
                abis.set(coreAbis)
                workspace.set(rootProject.layout.projectDirectory)
                outputDir.set(layout.buildDirectory.dir("generated/rust/jniLibs/${variant.name}"))
            }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(core, CargoNdkTask::outputDir)
        variant.sources.kotlin?.addGeneratedSourceDirectory(uniffiBindings, UniffiBindingsTask::outputDir)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.window)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.foundation)
    implementation(libs.compose.animation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material3.adaptive)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)

    // UniFFI's Kotlin bindings call the core through JNA.
    implementation(libs.jna) { artifact { type = "aar" } }
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    coreLibraryDesugaring(libs.desugar.jdk.libs)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
}
