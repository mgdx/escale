plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "io.github.mgdx.escale"
  compileSdk {
    version = release(37)
  }

  defaultConfig {
    // Figé : il ne pourra plus changer une fois l'application publiée sur F-Droid (SPEC.md § 1.1).
    applicationId = "io.github.mgdx.escale"
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "1.0.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  buildTypes {
    release {
      optimization {
        enable = false
      }
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    // Le `User-Agent` reprend le `versionName` : il se lit dans BuildConfig, jamais en dur
    // (SPEC.md § 4.2).
    buildConfig = true
  }

  lint {
    // Un avertissement de lint est une erreur (CLAUDE.md).
    warningsAsErrors = true
    abortOnError = true
    // `checkDependencies` n'est pas activé : lint ne sait pas analyser un module Kotlin/JVM comme
    // :core et le signale par un avertissement à chaque exécution. Chaque module Android porte donc
    // sa propre tâche lint, et :core est couvert par ktlint, detekt et ses tests JVM.
  }
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    // Un avertissement du compilateur est une erreur (CLAUDE.md).
    allWarningsAsErrors.set(true)
  }
}

dependencies {
  implementation(project(":core"))
  implementation(project(":data"))

  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.kotlinx.serialization.json)

  testImplementation(libs.junit)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
