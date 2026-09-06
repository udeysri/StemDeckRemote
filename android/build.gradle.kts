// Top-level build file: plugin versions are declared here (via `apply false`)
// and actually applied in module build files — the standard Android Gradle
// project layout (mirrors what `xcodegen`/`project.yml` does for the iOS
// side: one place that pins tool versions for the whole project).
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
}
