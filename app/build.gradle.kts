import org.gradle.api.GradleException
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import javax.xml.parsers.DocumentBuilderFactory

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kover)
}

kover {
    reports {
        filters {
            excludes {
                packages(
                    "org.gradle.*",
                    "com.android.*",
                    "org.jetbrains.kotlin.*"
                )
                classes(
                    "org.gradle.*",
                    "com.android.*",
                    "org.jetbrains.kotlin.*"
                )
            }
        }
    }
}

android {
    namespace = "com.mazzlabs.sentinel"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mazzlabs.sentinel"
        minSdk = 34
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            enableUnitTestCoverage = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.gson)
    implementation(libs.lifecycle.service)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.mlkit.text.recognition)

    // Gateway + Network
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.security.crypto)

    // Jetpack Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.datastore.preferences)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.truth)
}

tasks.register("koverCoverageGate") {
    dependsOn("koverXmlReport")

    doLast {
        val reportFile = file("${layout.buildDirectory.get()}/reports/kover/report.xml")
        if (!reportFile.exists()) {
            throw GradleException("Kover XML report not found at ${reportFile.path}")
        }

        val includePrefixes = listOf(
            "com/mazzlabs/sentinel/core",
            "com/mazzlabs/sentinel/graph",
            "com/mazzlabs/sentinel/tools",
            "com/mazzlabs/sentinel/security",
            "com/mazzlabs/sentinel/model"
        )

        val excludePrefixes = listOf(
            "com/mazzlabs/sentinel/databinding",
            "org/gradle",
            "com/android",
            "org/jetbrains/kotlin"
        )

        val doc = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(reportFile)

        val packages = doc.getElementsByTagName("package")
        var covered = 0L
        var missed = 0L

        for (i in 0 until packages.length) {
            val pkg = packages.item(i) as? org.w3c.dom.Element ?: continue
            val name = pkg.getAttribute("name")

            val included = includePrefixes.any { name.startsWith(it) }
            val excluded = excludePrefixes.any { name.startsWith(it) }
            if (!included || excluded) continue

            val counters = pkg.getElementsByTagName("counter")
            for (j in 0 until counters.length) {
                val counter = counters.item(j) as? org.w3c.dom.Element ?: continue
                if (counter.getAttribute("type") == "LINE") {
                    missed += counter.getAttribute("missed").toLong()
                    covered += counter.getAttribute("covered").toLong()
                }
            }
        }

        val total = missed + covered
        if (total == 0L) {
            throw GradleException("No LINE coverage counters found for selected packages.")
        }

        val coverage = covered.toDouble() / total.toDouble()
        val threshold = 0.60
        if (coverage < threshold) {
            throw GradleException(
                "Coverage gate failed: ${"%.2f".format(coverage * 100)}% < ${"%.0f".format(threshold * 100)}%"
            )
        }
    }
}

tasks.named("check") {
    dependsOn("koverCoverageGate")
}

