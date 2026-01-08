plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kover)
    alias(libs.plugins.detekt)
    alias(libs.plugins.owasp.dependency.check)
}

// ===========================================
// Version Configuration (from gradle.properties)
// ===========================================
val versionMajor = (project.findProperty("VERSION_MAJOR") as String?)?.toIntOrNull() ?: 1
val versionMinor = (project.findProperty("VERSION_MINOR") as String?)?.toIntOrNull() ?: 0
val versionPatch = (project.findProperty("VERSION_PATCH") as String?)?.toIntOrNull() ?: 0

// versionCode formula: MAJOR * 10000 + MINOR * 100 + PATCH
// Supports up to version 214.74.83 (max Int is 2147483647)
val computedVersionCode = versionMajor * 10000 + versionMinor * 100 + versionPatch
val computedVersionName = "$versionMajor.$versionMinor.$versionPatch"

android {
    namespace = "com.example.atv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.atv"
        minSdk = 29
        targetSdk = 35
        versionCode = computedVersionCode
        versionName = computedVersionName

        // Set APK filename: atv-{versionName}-{buildType}.apk
        setProperty("archivesBaseName", "atv-$computedVersionName")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // ===========================================
    // Signing Configuration
    // ===========================================
    signingConfigs {
        create("release") {
            // Read from environment variables (for CI) or local properties
            val keystorePath = System.getenv("KEYSTORE_PATH")
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
            val keyAliasValue = System.getenv("KEY_ALIAS")
            val keyPasswordValue = System.getenv("KEY_PASSWORD")

            // Only configure signing if all values are present
            if (keystorePath != null && keystorePassword != null && 
                keyAliasValue != null && keyPasswordValue != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            
            // Use release signing if configured, otherwise build unsigned
            val releaseSigningConfig = signingConfigs.findByName("release")
            if (releaseSigningConfig?.storeFile != null) {
                signingConfig = releaseSigningConfig
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
        buildConfig = true
    }

    lint {
        lintConfig = file("$rootDir/config/lint.xml")
        xmlReport = true
        htmlReport = true
        abortOnError = false  // Warn only, don't fail build
        checkDependencies = true
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)

    // Compose BOM
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.foundation)
    debugImplementation(libs.compose.ui.tooling)

    // Compose for TV
    implementation(libs.tv.foundation)
    implementation(libs.tv.material)

    // Media3
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.exoplayer.dash)
    implementation(libs.media3.exoplayer.ffmpeg)
    implementation(libs.media3.exoplayer.rtsp)
    implementation(libs.media3.ui)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // DataStore
    implementation(libs.datastore.preferences)

    // Logging
    implementation(libs.timber)

    // Testing
    testImplementation(libs.junit5)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.room.testing)
    
    // Android Instrumented Testing (E2E)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.compose.ui.test.manifest)
}

// JUnit 5 configuration with XML reporting
tasks.withType<Test> {
    useJUnitPlatform()
    
    // Generate JUnit XML reports for CI consumption
    reports {
        junitXml.required.set(true)
        html.required.set(true)
    }
    
    // Better test output
    testLogging {
        events("passed", "skipped", "failed")
        showExceptions = true
        showCauses = true
        showStackTraces = true
    }
}

// Kover configuration for code coverage
kover {
    reports {
        filters {
            excludes {
                // Exclude generated code
                classes(
                    "*_Factory*",
                    "*_HiltModules*",
                    "*Hilt_*",
                    "*_Impl*",
                    "*_Dao_Impl*",
                    "*ComposableSingletons*",
                    "*BuildConfig*",
                    "*_MembersInjector*"
                )
                packages(
                    "hilt_aggregated_deps",
                    "dagger.hilt.internal.aggregatedroot.codegen"
                )
            }
        }
    }
}

// Detekt configuration
detekt {
    config.setFrom(files("$rootDir/config/detekt.yml"))
    buildUponDefaultConfig = true
    allRules = false
}

// OWASP Dependency Check configuration
dependencyCheck {
    // Fail build only on Critical vulnerabilities (CVSS >= 9.0)
    failBuildOnCVSS = 9.0f
    
    // Suppress false positives if needed
    suppressionFile = "$rootDir/config/owasp-suppressions.xml"
    
    // Output formats
    formats = listOf("HTML", "JSON")
    
    // Only analyze runtime dependencies
    skipConfigurations = listOf(
        "lintClassPath",
        "debugAndroidTestCompileClasspath",
        "releaseCompileClasspath"
    )
    
    // NVD API configuration (optional - improves scan speed)
    nvd {
        // To use NVD API, set OWASP_NVD_API_KEY environment variable
        apiKey = System.getenv("OWASP_NVD_API_KEY") ?: ""
    }
}
