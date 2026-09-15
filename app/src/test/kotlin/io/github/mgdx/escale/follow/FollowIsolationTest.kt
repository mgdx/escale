package io.github.mgdx.escale.follow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * SPEC.md § 5.3.1, vérifié sur les sources : le service du suivi de trajet **ne fait aucune requête
 * réseau, ne lit aucune position et n'écrit rien sur le disque**.
 *
 * Le paquet `follow` ne doit donc référencer ni client réseau, ni dépôt, ni source de position, ni
 * stockage. Cette isolation est ce qui permet au § 7.7 de dire que le service ne relève pas de la
 * règle « aucun réseau hors du premier plan » : il n'en a pas les moyens.
 */
class FollowIsolationTest {

  private val sources: List<File> =
    File("src/main/kotlin/io/github/mgdx/escale/follow").walkTopDown().filter { it.extension == "kt" }.toList()

  @Test
  fun `le paquet du suivi existe`() {
    assertTrue(sources.size >= MIN_SOURCES)
  }

  @Test
  fun `le suivi ne reference ni reseau, ni depot, ni position, ni disque`() {
    val offenders = sources
      .flatMap { file -> FORBIDDEN.filter(code(file)::contains).map { "${file.name} : $it" } }

    assertEquals("Le service du suivi ne fait que des calculs locaux (SPEC.md § 5.3.1)", emptyList<String>(), offenders)
  }

  private fun code(file: File): String = file.readText()
    .replace(BLOCK_COMMENT, "")
    .replace(LINE_COMMENT, "")

  private companion object {
    val FORBIDDEN = listOf(
      // Réseau.
      "io.ktor",
      "okhttp",
      "HttpClient",
      "java.net",
      "escale.data.net",
      // Dépôts : tout ce qui sait parler au serveur ou à la base. Seul `PreferencesRepository`
      // manque à la liste, et à dessein : le service y lit le format d'heure de SPEC.md § 5.6,
      // pour que la notification écrive « 13:30 » là où la fiche écrit « 13:30 ». Une lecture de
      // réglage n'est ni une requête, ni une position, ni une écriture.
      "PlanRepository",
      "TripRepository",
      "StopsRepository",
      "RentalsRepository",
      "GeocodeRepository",
      "MapRepository",
      "HistoryRepository",
      "FavoritesRepository",
      "ServerRepository",
      "escale.data.",
      // Position.
      "LocationSource",
      "LocationManager",
      "android.location",
      // Disque.
      "DataStore",
      "androidx.room",
      "openFileOutput",
      "java.io.File",
      "SharedPreferences",
    )

    const val MIN_SOURCES = 5

    val BLOCK_COMMENT = Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL)
    val LINE_COMMENT = Regex("//.*")
  }
}
