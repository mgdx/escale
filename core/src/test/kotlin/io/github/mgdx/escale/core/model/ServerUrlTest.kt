package io.github.mgdx.escale.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** SPEC.md § 5.6.1 : normalisation de l'URL saisie par l'usager. */
class ServerUrlTest {

  @Test
  fun `le schema https est ajoute quand il manque`() {
    assertEquals("https://api.transitous.org", ServerUrl.normalize("api.transitous.org"))
  }

  @Test
  fun `la barre oblique finale est supprimee`() {
    assertEquals("https://api.transitous.org", ServerUrl.normalize("https://api.transitous.org/"))
  }

  @Test
  fun `un suffixe api colle par erreur est retire`() {
    assertEquals("https://api.transitous.org", ServerUrl.normalize("https://api.transitous.org/api"))
    assertEquals("https://api.transitous.org", ServerUrl.normalize("https://api.transitous.org/api/"))
  }

  @Test
  fun `un serveur local en clair est accepte avec son port`() {
    assertEquals("http://192.168.1.10:8080", ServerUrl.normalize("  http://192.168.1.10:8080/  "))
  }

  @Test
  fun `un chemin de base est conserve`() {
    assertEquals("https://exemple.org/motis", ServerUrl.normalize("https://exemple.org/motis/"))
  }

  @Test
  fun `une saisie vide ou aberrante est refusee`() {
    assertNull(ServerUrl.normalize(""))
    assertNull(ServerUrl.normalize("   "))
    assertNull(ServerUrl.normalize("ftp://exemple.org"))
    assertNull(ServerUrl.normalize("https:// exemple .org"))
  }

  @Test
  fun `le trafic en clair est reconnu`() {
    assertTrue(ServerUrl.isCleartext("http://192.168.1.10:8080"))
    assertFalse(ServerUrl.isCleartext("https://api.transitous.org"))
  }

  @Test
  fun `l hote est extrait sans port ni chemin`() {
    assertEquals("192.168.1.10", ServerUrl.hostOf("http://192.168.1.10:8080"))
    assertEquals("api.transitous.org", ServerUrl.hostOf("https://api.transitous.org"))
    assertEquals("exemple.org", ServerUrl.hostOf("https://exemple.org/motis"))
    assertNull(ServerUrl.hostOf("api.transitous.org"))
  }
}
