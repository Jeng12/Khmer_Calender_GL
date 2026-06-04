// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  // org.jetbrains.kotlin.android intentionally omitted: AGP 9 built-in Kotlin
  // replaces it. Only the Compose compiler plugin is declared here.
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
}
