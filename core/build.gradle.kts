plugins {
  alias(libs.plugins.kotlin.jvm)
}

// Bytecode 17 produit avec le JDK courant : pas de `jvmToolchain(...)`, la machine de compilation
// n'a pas forcément le JDK correspondant et Gradle irait le télécharger.
java {
  sourceCompatibility = JavaVersion.VERSION_17
  targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
  compilerOptions {
    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    // Un avertissement du compilateur est une erreur (CLAUDE.md).
    allWarningsAsErrors.set(true)
  }
  sourceSets["main"].kotlin.srcDir("src/main/kotlin")
  sourceSets["test"].kotlin.srcDir("src/test/kotlin")
}

dependencies {
  // :core est du Kotlin pur : ni Android, ni androidx, ni kotlinx-serialization.
  implementation(libs.kotlinx.coroutines.core)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
}

tasks.withType<Test>().configureEach {
  useJUnit()
}
