// AGP 9.0 supplies Kotlin 2.2.10; the Compose compiler uses that same version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
