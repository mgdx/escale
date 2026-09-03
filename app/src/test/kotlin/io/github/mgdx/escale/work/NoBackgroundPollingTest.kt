package io.github.mgdx.escale.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SPEC.md § 7.7, vérifié sur le code lui-même : « **aucun travail de fond, à une exception près et
 * une seule** […] aucun service, aucune tâche périodique, aucune synchronisation ».
 *
 * Une règle de ce genre ne se prouve pas en exécutant l'application : elle se prouve en constatant
 * que **le code qui la violerait n'existe pas**. Ce test lit les sources du module et échoue si
 * quelqu'un y introduit une tâche périodique — y compris déguisée en « toutes les 24 heures », qui
 * serait la même violation sous un autre nom.
 *
 * Il échoue aussi si la programmation cesse de passer par `WatchScheduler` : c'est le seul endroit
 * du projet autorisé à mettre une tâche en file d'attente.
 */
class NoBackgroundPollingTest {

  private val sources: List<File> =
    File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

  @Test
  fun `le module contient bien des sources a inspecter`() {
    assertTrue("Sources introuvables : le test ne prouverait rien.", sources.size > MIN_SOURCES)
  }

  @Test
  fun `aucune tache periodique n'est declaree`() {
    val offenders = sources
      .filter { file -> PERIODIC.any(code(file)::contains) }
      .map { it.name }

    assertEquals("Tâche périodique interdite par SPEC.md § 7.7", emptyList<String>(), offenders)
  }

  @Test
  fun `seule la programmation des trajets surveilles met une tache en file`() {
    val offenders = sources
      .filter { code(it).contains("enqueue") && it.name != "WatchScheduler.kt" }
      .map { it.name }

    assertEquals("Une seule classe programme des tâches", emptyList<String>(), offenders)
  }

  /**
   * Le code d'un fichier, commentaires retirés.
   *
   * Sans cela, la phrase « jamais de `PeriodicWorkRequest` » écrite dans une documentation ferait
   * échouer le test qu'elle explique — et la tentation serait alors de ne plus l'écrire.
   */
  private fun code(file: File): String = file.readText()
    .replace(BLOCK_COMMENT, "")
    .replace(LINE_COMMENT, "")

  @Test
  fun `le manifeste retire le demarrage automatique et ne demande aucune alarme exacte`() {
    val manifest = File("src/main/AndroidManifest.xml").readText()

    assertTrue(manifest.contains(BOOT_COMPLETED))
    assertTrue("RECEIVE_BOOT_COMPLETED doit être retirée du manifeste fusionné", manifest.contains(REMOVE))
    assertFalse("SCHEDULE_EXACT_ALARM ne doit être demandée nulle part", manifest.contains(EXACT_ALARM))
  }

  private companion object {
    /** Tout ce qui, dans l'API de `WorkManager`, produit une répétition. */
    val PERIODIC = listOf("PeriodicWorkRequest", "enqueueUniquePeriodicWork", "setPeriodic")

    const val MIN_SOURCES = 10
    const val BOOT_COMPLETED = "android.permission.RECEIVE_BOOT_COMPLETED"
    const val REMOVE = "tools:node=\"remove\""

    /** La déclaration interdite par SPEC.md § 5.5.1, et non la mention qu'en fait un commentaire. */
    const val EXACT_ALARM = "<uses-permission android:name=\"android.permission.SCHEDULE_EXACT_ALARM\""

    val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    val LINE_COMMENT = Regex("//.*")
  }
}
