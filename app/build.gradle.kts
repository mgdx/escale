import com.android.build.api.variant.FilterConfiguration

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
}

// `versionCode` de base, celui de l'application. Il s'incrémente à chaque publication, et lui seul.
val baseVersionCode = 1

// Convention de `versionCode` par ABI — **à ne pas changer une fois l'application publiée.**
//
// Android et F-Droid exigent que deux APK d'une même application n'aient pas le même `versionCode`
// sur des ABI différentes : c'est ce nombre, et lui seul, qui départage les fichiers candidats.
// Le `versionCode` publié vaut donc `rang de l'ABI × 1000 + baseVersionCode` — 1001, 2001, 3001 et
// 4001 pour la version 1.
//
// Les rangs ne sont pas arbitraires : **une ABI doit avoir un rang supérieur à toutes celles qu'un
// appareil qui la porte sait également exécuter**, sans quoi l'appareil installerait la mauvaise.
// Un appareil `arm64-v8a` exécute aussi `armeabi-v7a`, un appareil `x86_64` exécute aussi `x86`, et
// un appareil x86 exécute souvent `armeabi-v7a` par traduction : d'où cet ordre, qui est celui
// recommandé par la documentation Android.
//
// Le multiplicateur 1000 laisse 999 publications avant que deux rangs se rejoignent, et garde le
// `versionCode` lisible : le rang se lit à gauche, la version de l'application à droite.
val abiVersionCodeRanks = mapOf(
  "armeabi-v7a" to 1,
  "arm64-v8a" to 2,
  "x86" to 3,
  "x86_64" to 4,
)

val abiVersionCodeMultiplier = 1000

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
    versionCode = baseVersionCode
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

  // Un APK par architecture (SPEC.md § 2 : moins de 15 Mo). Les bibliothèques natives de MapLibre
  // pèsent à elles seules une quarantaine de mégaoctets une fois les quatre architectures réunies ;
  // aucun appareil n'en exécute plus d'une. F-Droid sert à chaque appareil le fichier qui lui
  // convient, et les variantes x86 restent produites pour les émulateurs.
  splits {
    abi {
      isEnable = true
      reset()
      include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
      // Pas d'APK universel : il annulerait tout le gain, et personne ne l'installerait.
      isUniversalApk = false
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

// Applique la convention ci-dessus à chaque APK produit. La valeur est posée sur la sortie, jamais
// sur `defaultConfig` : `defaultConfig.versionCode` reste le numéro de version de l'application.
androidComponents {
  onVariants { variant ->
    variant.outputs.forEach { output ->
      val abi = output.filters
        .firstOrNull { it.filterType == FilterConfiguration.FilterType.ABI }
        ?.identifier
        ?: return@forEach
      val rank = abiVersionCodeRanks[abi] ?: error("ABI sans rang de versionCode : $abi")
      output.versionCode.set(rank * abiVersionCodeMultiplier + baseVersionCode)
    }
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
  // `SavedStateHandle` : le seul état qu'Android restitue après avoir tué le processus en
  // arrière-plan. Sans lui, la saisie de l'écran « Serveur MOTIS » est perdue.
  implementation(libs.androidx.lifecycle.viewmodel.savedstate)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.kotlinx.serialization.json)

  // Carte : MapLibre GL Android, licence BSD, compatible F-Droid (SPEC.md § 3). La carte est
  // rendue par le moteur natif, jamais par une WebView (SPEC.md § 2).
  implementation(libs.maplibre.android.sdk)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
}
