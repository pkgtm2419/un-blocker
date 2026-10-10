plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.ksp)
}

val stableTestKeystorePath = System.getenv("UB_BLOCKER_KEYSTORE_FILE")
val stableTestKeystorePassword = System.getenv("UB_BLOCKER_KEYSTORE_PASSWORD")
val stableTestKeyAlias = System.getenv("UB_BLOCKER_KEY_ALIAS")
val stableTestKeyPassword = System.getenv("UB_BLOCKER_KEY_PASSWORD")

val stableTestSigningValues = listOf(
    stableTestKeystorePath,
    stableTestKeystorePassword,
    stableTestKeyAlias,
    stableTestKeyPassword
)

val hasAnyStableTestSigningValue = stableTestSigningValues.any { !it.isNullOrBlank() }
val hasStableTestSigning = stableTestSigningValues.all { !it.isNullOrBlank() }

if (hasAnyStableTestSigningValue && !hasStableTestSigning) {
    throw GradleException("Stable test signing requires all UB_BLOCKER_KEYSTORE_* environment variables.")
}

val releaseKeystorePath = System.getenv("UB_RELEASE_KEYSTORE_FILE")
val releaseKeystorePassword = System.getenv("UB_RELEASE_KEYSTORE_PASSWORD")
val releaseKeyAlias = System.getenv("UB_RELEASE_KEY_ALIAS")
val releaseKeyPassword = System.getenv("UB_RELEASE_KEY_PASSWORD")

val releaseSigningValues = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword
)
val hasAnyReleaseSigningValue = releaseSigningValues.any { !it.isNullOrBlank() }
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }

if (hasAnyReleaseSigningValue && !hasReleaseSigning) {
    throw GradleException("Production release signing requires all UB_RELEASE_KEYSTORE_* environment variables.")
}

android {
    namespace = "com.unblocker.app"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.unblocker.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 9
        versionName = "2.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasStableTestSigning) {
            create("stableTest") {
                storeFile = file(stableTestKeystorePath)
                storePassword = stableTestKeystorePassword
                keyAlias = stableTestKeyAlias
                keyPassword = stableTestKeyPassword
            }
        }
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            if (hasStableTestSigning) {
                signingConfig = signingConfigs.getByName("stableTest")
            }
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        create("releaseTest") {
            initWith(getByName("release"))
            matchingFallbacks += listOf("release")
            if (hasStableTestSigning) {
                signingConfig = signingConfigs.getByName("stableTest")
            } else {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("main").assets.srcDir("${layout.buildDirectory.get()}/generated/legal-assets")
        getByName("main").assets.srcDir("${layout.buildDirectory.get()}/generated/dns-assets")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        noCompress += listOf("bin")
    }

    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.ApkVariantOutputImpl
            output.outputFileName = "ub-blocker-${variant.versionName}.apk"
        }
    }
}

val copyLegalAssets by tasks.registering(Copy::class) {
    from(rootProject.files("LICENSE", "NOTICE", "PRIVACY.md", "DATASETS.md", "THIRD_PARTY_NOTICES.md"))
    into("${layout.buildDirectory.get()}/generated/legal-assets/licenses")
}
tasks.named("preBuild").configure { dependsOn(copyLegalAssets) }

val compileDnsRules by tasks.registering(Exec::class) {
    inputs.dir(rootProject.file("tools/filter-compiler")).withPropertyName("filterCompiler")
    inputs.files(fileTree("src/main/assets") { include("*.txt", "*.dat") }).withPropertyName("assets")
    outputs.dir("${layout.buildDirectory.get()}/generated/dns-assets")
    outputs.dir("${layout.buildDirectory.get()}/generated/dns-test")

    val pythonExec = System.getenv("UB_BLOCKER_PYTHON") ?: if (System.getProperty("os.name").lowercase().contains("windows")) "python" else "python3"
    
    commandLine(
        pythonExec,
        rootProject.file("tools/filter-compiler/compiler.py").absolutePath,
        "--root", rootProject.projectDir.absolutePath,
        "--output-assets", file("${layout.buildDirectory.get()}/generated/dns-assets").absolutePath,
        "--output-test", file("${layout.buildDirectory.get()}/generated/dns-test").absolutePath
    )
}
tasks.named("preBuild").configure { dependsOn(compileDnsRules) }

dependencies {
    implementation(project(":common"))
    implementation(project(":data-store"))
    implementation(project(":vpn-engine"))
    implementation(project(":ml-engine"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.material3.windowSizeClass)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.arch.core.testing)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
