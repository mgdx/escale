package io.github.mgdx.escale.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SPEC.md § 7.7 et § 5.3.1, vérifiés sur le code lui-même : **aucun réseau hors du premier plan**,
 * aucune tâche planifiée, aucune alarme, aucun récepteur, et **un seul service** — celui du suivi
 * de trajet, dans les limites que le § 5.3.1 lui fixe.
 *
 * La règle des trajets surveillés reste sans exception : une requête envoyée au serveur à heure
 * fixe, avant un trajet habituel, dessine des habitudes de déplacement, et la fonction a été
 * supprimée pour cela. Le service du suivi n'y déroge pas parce qu'il **ne touche pas au réseau**,
 * ce que `FollowIsolationTest` vérifie sur ses sources.
 *
 * Une règle de ce genre ne se prouve pas en exécutant l'application : elle se prouve en constatant
 * que **le code qui la violerait n'existe pas**. Ce test lit les sources du module et le manifeste,
 * et échoue si quelqu'un y réintroduit une tâche de fond — sous `WorkManager`, sous `AlarmManager`,
 * ou déguisée en second service.
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

  @Test
  fun `seul le paquet du suivi demarre un service`() {
    val offenders = sources
      .filter { file -> !file.path.contains(FOLLOW_PACKAGE) && SERVICE_STARTERS.any(code(file)::contains) }
      .map { it.name }

    assertEquals("Un seul service, celui du suivi de trajet (SPEC.md § 5.3.1)", emptyList<String>(), offenders)
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
  fun `le manifeste ne declare ni recepteur, ni alarme, ni demarrage automatique`() {
    val manifest = manifest()

    for (permission in FORBIDDEN_PERMISSIONS) {
      assertFalse(
        "$permission ne doit être demandée nulle part (SPEC.md § 11)",
        manifest.contains(permission(permission)),
      )
    }
    assertFalse("Aucun récepteur (SPEC.md § 7.7)", manifest.contains("<receiver"))
  }

  @Test
  fun `le manifeste declare exactement les permissions du suivi de trajet`() {
    val manifest = manifest()

    for (permission in FOLLOW_PERMISSIONS) {
      assertTrue(
        "$permission est déclarée par l'application elle-même (SPEC.md § 11)",
        manifest.contains(permission(permission)),
      )
    }
    // Aucune autre permission de fond : le type de service est le seul, et il est explicite.
    val serviceTypes = Regex("android:foregroundServiceType=\"([^\"]+)\"").findAll(manifest).map {
      it.groupValues[1]
    }.toList()
    assertEquals(listOf("specialUse"), serviceTypes)
  }

  @Test
  fun `le manifeste declare un seul service, celui du suivi, non exporte`() {
    val manifest = manifest()

    val services = Regex("<service[^>]*android:name=\"([^\"]+)\"").findAll(manifest).map { it.groupValues[1] }.toList()
    assertEquals(listOf(".follow.FollowService"), services)
    val declaration = manifest.substring(manifest.indexOf("<service"), manifest.indexOf("</service>"))
    assertTrue("Le service n'est pas exporté", declaration.contains("android:exported=\"false\""))
  }

  private fun manifest(): String = File("src/main/AndroidManifest.xml").readText()

  private fun permission(name: String) = "<uses-permission android:name=\"android.permission.$name\""

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
      "setExactAndAllowWhileIdle",
    )

    /** Ce qui démarre un service : permis dans le paquet du suivi, et nulle part ailleurs. */
    val SERVICE_STARTERS = listOf(
      "startForegroundService",
      "startService(",
      "startForeground(",
    )

    const val FOLLOW_PACKAGE = "/io/github/mgdx/escale/follow/"

    /** Les permissions qui n'ont de sens que pour du travail planifié, et que rien ne justifie. */
    val FORBIDDEN_PERMISSIONS = listOf(
      "RECEIVE_BOOT_COMPLETED",
      "SCHEDULE_EXACT_ALARM",
      "USE_EXACT_ALARM",
      "FOREGROUND_SERVICE_LOCATION",
      "ACCESS_BACKGROUND_LOCATION",
    )

    /** Les quatre permissions du suivi de trajet (SPEC.md § 11), et pas une de plus. */
    val FOLLOW_PERMISSIONS = listOf(
      "POST_NOTIFICATIONS",
      "FOREGROUND_SERVICE",
      "FOREGROUND_SERVICE_SPECIAL_USE",
      "WAKE_LOCK",
    )

    const val MIN_SOURCES = 10

    val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    val LINE_COMMENT = Regex("//.*")
  }
}
