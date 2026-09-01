// Fichier de compilation racine : il n'apporte aucun code, il branche la chaîne de qualité sur
// les trois modules. Voir docs/architecture.md § 3, règle 5 : un lot n'y touche que si sa tâche
// le nomme explicitement.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.android.library) apply false
  alias(libs.plugins.kotlin.jvm) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.ktlint) apply false
  alias(libs.plugins.detekt) apply false
}

// Avertissement connu et laissé tel quel : detekt 1.23.8, la dernière version stable publiée,
// appelle `ReportingExtension.file(String)`, que Gradle 9.5 signale comme dépréciée. Gradle affiche
// donc « Deprecated Gradle features were used in this build » à chaque exécution. L'appel vient du
// greffon, pas de ce dépôt : rien ici ne peut le supprimer, et masquer l'avertissement (
// `org.gradle.warning.mode=none`) masquerait aussi les nôtres. À réévaluer à la sortie de detekt 2.
subprojects {
  apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)
  apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)

  configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
    version.set(rootProject.libs.versions.ktlintTool.get())
    // Un avertissement de style est une erreur : CLAUDE.md n'en tolère aucun.
    ignoreFailures.set(false)
    // Les sources générées (BuildConfig, Room, Compose) ne suivent pas notre style.
    filter {
      exclude { it.file.path.contains("${File.separator}build${File.separator}") }
    }
  }

  configure<io.gitlab.arturbosch.detekt.extensions.DetektExtension> {
    config.setFrom(rootProject.files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
    ignoreFailures = false
    basePath = rootProject.projectDir.absolutePath
  }

  tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = JavaVersion.VERSION_17.toString()
    // La sortie console suffit ; les rapports fichiers ne servent qu'à une CI qui les consomme.
    reports {
      html.required.set(false)
      xml.required.set(false)
      txt.required.set(false)
      sarif.required.set(false)
      md.required.set(false)
    }
  }
  tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = JavaVersion.VERSION_17.toString()
  }
}
