// Top-level build file - plugin versions only, no per-module config here.
//
// AGP pinned at 8.2.2 deliberately conservative - it only needs Gradle 8.2
// minimum (see https://developer.android.com/build/releases/about-agp),
// which any reasonably current Android Studio already has bundled, so this
// builds using whatever Gradle Studio already has rather than needing to
// download a specific version via a Gradle wrapper.
plugins {
    id("com.android.application") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}
