package io.github.mgdx.escale.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SPEC.md § 7.7, vérifié sur le code lui-même : « **aucun travail de fond**, aucun service, aucune
 * tâche périodique, aucune synchronisation ».
 *
 * La règle n'a plus d'exception depuis le retrait des trajets surveillés : une requête envoyée au
 * serveur à heure fixe, avant un trajet habituel, dessine des habitudes de déplacement, et la
 * fonction a été supprimée pour cela. Une règle de ce genre ne se prouve pas en exécutant
 * l'application : elle se prouve en constatant que **le code qui la violerait n'existe pas**. Ce
 * test lit les sources du module et échoue si quelqu'un y réintroduit une tâche de fond — sous
 * `WorkManager`, sous `AlarmManager`, ou déguisée en service.
 */
class NoBackgroundWorkTest {

  private val sources: List<File> =
    File("src/main/kotlin").walkTopDown().filter { it.extension == "kt" }.toList()

  @Test
  fun `le module contient bien des sources a inspecter`() {
    assertTrue("Sources introuvables : le test ne prouverait rien.", sources.size > MIN_SOURCES)
  }

  @Test
  fun `aucune tache de fond n'est programmee`() {
    val offenders = sources
      .filter { file -> FORBIDDEN.any(code(file)::contains) }
      .map { it.name }

    assertEquals("Travail de fond interdit par SPEC.md § 7.7", emptyList<String>(), offenders)
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
  fun `le manifeste ne declare ni service, ni recepteur, ni permission de fond`() {
    val manifest = File("src/main/AndroidManifest.xml").readText()

    for (permission in FORBIDDEN_PERMISSIONS) {
      assertFalse(
        "$permission ne doit être demandée nulle part (SPEC.md § 11)",
        manifest.contains("<uses-permission android:name=\"android.permission.$permission\""),
      )
    }
    assertFalse("Aucun service (SPEC.md § 7.7)", manifest.contains("<service"))
    assertFalse("Aucun récepteur (SPEC.md § 7.7)", manifest.contains("<receiver"))
  }

  private companion object {
    /**
     * Ce qui, dans les API d'Android, met du travail en file hors de l'écran. `androidx.work` n'est
     * plus une dépendance de l'application : le mentionner ici garde la porte fermée le jour où
     * quelqu'un l'ajouterait de nouveau.
     */
    val FORBIDDEN = listOf(
      "androidx.work",
      "WorkManager",
      "PeriodicWorkRequest",
      "OneTimeWorkRequest",
      "AlarmManager",
      "JobScheduler",
      "startForegroundService",
    )

    /** Les permissions qui n'ont de sens que pour du travail de fond ou son résultat. */
    val FORBIDDEN_PERMISSIONS = listOf(
      "RECEIVE_BOOT_COMPLETED",
      "SCHEDULE_EXACT_ALARM",
      "USE_EXACT_ALARM",
      "FOREGROUND_SERVICE",
      "WAKE_LOCK",
      "POST_NOTIFICATIONS",
    )

    const val MIN_SOURCES = 10

    val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    val LINE_COMMENT = Regex("//.*")
  }
}
