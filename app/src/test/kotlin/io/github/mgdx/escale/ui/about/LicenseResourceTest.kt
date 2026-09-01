package io.github.mgdx.escale.ui.about

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * La licence embarquée est-elle bien celle du dépôt, en entier ?
 *
 * SPEC.md § 12 : `LICENSE` est un livrable du dépôt, et `res/raw/gpl_3_0.txt` en est la copie que
 * l'application affiche hors ligne. Les deux doivent rester identiques — une copie tronquée ou
 * retouchée serait un défaut de conformité que personne ne verrait avant la revue F-Droid.
 */
class LicenseResourceTest {

  private val embedded = File("src/main/res/raw/gpl_3_0.txt")

  private val repositoryLicense: File
    get() {
      var directory: File? = File("").absoluteFile
      while (directory != null) {
        val candidate = File(directory, "LICENSE")
        if (candidate.isFile) return candidate
        directory = directory.parentFile
      }
      error("LICENSE introuvable à la racine du dépôt")
    }

  @Test
  fun `la licence embarquee est celle du depot, octet pour octet`() {
    assertTrue("res/raw/gpl_3_0.txt est absent", embedded.isFile)
    assertEquals(repositoryLicense.readText(), embedded.readText())
  }

  @Test
  fun `la licence embarquee n est pas tronquee`() {
    val text = embedded.readText()
    assertTrue(text.startsWith("                    GNU GENERAL PUBLIC LICENSE"))
    // Dernière phrase de l'annexe « How to Apply These Terms to Your New Programs ».
    assertTrue(text.trimEnd().endsWith("<https://www.gnu.org/licenses/why-not-lgpl.html>."))
  }
}
