import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun duntaId(name: String): Int {
    val value = providers.gradleProperty(name).orNull ?: localProperties.getProperty(name) ?: "0"
    return value.toIntOrNull()?.takeIf { it >= 0 }
        ?: error("$name must be a non-negative integer")
}

android {
    namespace = "com.ilya.rulivo"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.ilya.rulivo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("int", "DUNTA_PARTNER_ID", duntaId("DUNTA_PARTNER_ID").toString())
        buildConfigField("int", "DUNTA_APPLICATION_ID", duntaId("DUNTA_APPLICATION_ID").toString())
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
        isCoreLibraryDesugaringEnabled = true
    }
    androidResources {
        // Library translations must not make Android choose a language the app does not support.
        localeFilters += listOf("en", "in", "ur", "ha", "hi")
    }
    bundle {
        // The in-app picker must work offline, including languages absent from the device.
        language { enableSplit = false }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(files("libs/dunta_sdk-release.aar"))
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.work.multiprocess)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.android.installreferrer)
    implementation(libs.google.material)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.arcore)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

val compileLocalizationCheck by tasks.registering(JavaCompile::class) {
    source(rootProject.fileTree("tools/localization") { include("*.java") })
    classpath = files()
    destinationDirectory.set(layout.buildDirectory.dir("localization-check/classes"))
    options.release.set(11)
    options.encoding = "UTF-8"
}

val checkLocalization by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Check translation coverage, placeholders and hardcoded UI text using the Gradle JDK."
    classpath = files(compileLocalizationCheck.flatMap { it.destinationDirectory })
    mainClass.set("LocalizationCheck")
    args(rootProject.projectDir.absolutePath)
}

val testLocalizationCheck by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Verify the localization checker rejects broken translations and hardcoded UI text."
    classpath = files(compileLocalizationCheck.flatMap { it.destinationDirectory })
    mainClass.set("LocalizationCheckTest")
    args(rootProject.projectDir.absolutePath)
}

tasks.named("preBuild") { dependsOn(checkLocalization) }
tasks.named("check") { dependsOn(testLocalizationCheck) }
