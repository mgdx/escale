plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.kotlin.serialization)
  // Room passe par KSP, jamais par kapt : kapt fait tourner un compilateur Java complet pour rien
  // et ralentit chaque compilation du module.
  alias(libs.plugins.ksp)
}

// Room écrit ici le schéma de chaque version de la base, et ce répertoire est **commité**. Sans
// schéma exporté, aucune migration future n'est vérifiable : la première mise à jour publiée qui
// changerait une table effacerait les favoris de l'usager sans que rien ne l'ait signalé.
val roomSchemas: String = layout.projectDirectory.dir("schemas").asFile.path

ksp {
  arg("room.schemaLocation", roomSchemas)
}

android {
  namespace = "io.github.mgdx.escale.data"
  compileSdk {
    version = release(37)
  }

  defaultConfig {
    minSdk = 26
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    consumerProguardFiles("consumer-rules.pro")
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  testOptions {
    unitTests {
      isReturnDefaultValues = true
      // Robolectric fait tourner les tests Room en JVM sur une base en mémoire : il lui faut les
      // ressources et le manifeste fusionnés du module, sans quoi il ne sait pas construire de
      // contexte d'application.
      isIncludeAndroidResources = true
    }
  }

  // Le schéma exporté par Room (voir `ksp` plus bas) est lisible par les tests : c'est ce qui
  // permettra à `MigrationTestHelper` de vérifier une migration future contre le schéma réellement
  // publié, et non contre celui que le code du moment décrit.
  @Suppress("UNCHECKED_CAST")
  val sets = sourceSets as org.gradle.api.NamedDomainObjectContainer<com.android.build.api.dsl.AndroidSourceSet>
  sets.getByName("test").assets.srcDir(roomSchemas)
  sets.getByName("androidTest").assets.srcDir(roomSchemas)

  lint {
    // Un avertissement de lint est une erreur (CLAUDE.md).
    warningsAsErrors = true
    abortOnError = true
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

  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  // Moteur du client Ktor, déclaré explicitement pour son cache disque : SPEC.md § 7.5 impose
  // 24 h de cache sur les résultats de géocodage, et c'est `okhttp3.Cache` qui le porte.
  implementation(libs.okhttp)
  implementation(libs.androidx.datastore.preferences)
  // Persistance locale des favoris et de l'historique (SPEC.md § 5.5).
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  ksp(libs.androidx.room.compiler)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.turbine)
  testImplementation(libs.ktor.client.mock)
  // Room a besoin de SQLite : en JVM, c'est Robolectric qui le fournit, sur une base en mémoire.
  testImplementation(libs.robolectric)
  testImplementation(libs.androidx.junit)
}
