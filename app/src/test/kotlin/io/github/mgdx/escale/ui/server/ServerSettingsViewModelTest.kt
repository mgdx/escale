package io.github.mgdx.escale.ui.server

import androidx.lifecycle.SavedStateHandle
import io.github.mgdx.escale.MainDispatcherRule
import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.ServerUrl
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** SPEC.md § 5.6.1 : la machine à états de l'écran « Serveur MOTIS ». */
class ServerSettingsViewModelTest {

  @get:Rule
  val mainDispatcherRule = MainDispatcherRule()

  private val repository = FakeServerRepository()
  private val consentStore = FakeCleartextConsentStore()
  private val cacheReset = RecordingCacheReset()

  /**
   * Le même `SavedStateHandle` d'un `ViewModel` à l'autre : c'est ce que fait le système quand il
   * tue le processus en arrière-plan puis restitue l'écran.
   */
  private val savedState = SavedStateHandle()

  private fun viewModel() = ServerSettingsViewModel(repository, consentStore, cacheReset.reset, savedState)

  @Test
  fun `le champ reprend le serveur en service tant que rien n est saisi`() = runTest {
    val viewModel = viewModel()

    assertEquals(ServerUrl.DEFAULT_BASE_URL, viewModel.uiState.value.currentServerUrl)
    assertEquals(ServerUrl.DEFAULT_BASE_URL, viewModel.uiState.value.input)
  }

  @Test
  fun `la saisie est normalisee en racine de serveur`() = runTest {
    val viewModel = viewModel()

    viewModel.onInputChange("exemple.org/api/")

    assertEquals("https://exemple.org", viewModel.uiState.value.normalizedInput)
    assertFalse(viewModel.uiState.value.inputInvalid)
  }

  @Test
  fun `une saisie aberrante est signalee et ne lance aucun test`() = runTest {
    val viewModel = viewModel()

    viewModel.onInputChange("ftp://exemple.org")
    viewModel.onTestConnection()

    assertTrue(viewModel.uiState.value.inputInvalid)
    assertNull(viewModel.uiState.value.normalizedInput)
    assertEquals(emptyList<String>(), repository.testedUrls)
  }

  @Test
  fun `un test concluant ouvre l enregistrement`() = runTest {
    repository.programTest()
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()

    val state = viewModel.uiState.value
    assertEquals(CheckStepState.PASSED, state.connectionTest.reachable)
    assertEquals(CheckStepState.PASSED, state.connectionTest.apiVersion)
    assertEquals(CheckStepState.PASSED, state.connectionTest.tiles)
    assertTrue(state.canSave)
    assertFalse(state.canForce)
  }

  @Test
  fun `un serveur sans tuiles reste utilisable`() = runTest {
    repository.programTest(tilesAvailable = false)
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()

    val state = viewModel.uiState.value
    assertEquals(CheckStepState.ABSENT, state.connectionTest.tiles)
    assertTrue(state.canSave)
  }

  @Test
  fun `une version d API trop ancienne ferme l enregistrement direct`() = runTest {
    repository.programTest(apiCompatible = false, tilesAvailable = false)
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()

    val state = viewModel.uiState.value
    assertEquals(CheckStepState.FAILED, state.connectionTest.apiVersion)
    assertFalse(state.canSave)
    assertTrue(state.canForce)
  }

  @Test
  fun `un serveur injoignable expose son erreur`() = runTest {
    repository.programUnreachable(EscaleError.NoNetwork)
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()

    val state = viewModel.uiState.value
    assertEquals(CheckStepState.FAILED, state.connectionTest.reachable)
    assertEquals(EscaleError.NoNetwork, state.connectionTest.error)
    // Les deux étapes suivantes n'ont pas été menées : ni « réussi », ni « échec », sans objet.
    assertEquals(CheckStepState.SKIPPED, state.connectionTest.apiVersion)
    assertEquals(CheckStepState.SKIPPED, state.connectionTest.tiles)
    assertFalse(state.connectionTest.running)
    assertFalse(state.canSave)
    assertTrue(state.canForce)
  }

  /**
   * L'anomalie A2 : les trois lignes passaient en « en cours » ensemble et recevaient leur verdict
   * ensemble. SPEC.md § 5.6.1 demande trois résultats distincts ; ils le sont maintenant aussi dans
   * le temps.
   */
  @Test
  fun `une etape rend son verdict sans attendre les suivantes`() = runTest {
    repository.programTest()
    // Après l'annonce et le verdict de la première étape, avant l'annonce de la deuxième.
    repository.pauseAfter = 2
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()

    val midway = viewModel.uiState.value
    assertEquals(CheckStepState.PASSED, midway.connectionTest.reachable)
    assertEquals(CheckStepState.IDLE, midway.connectionTest.apiVersion)
    assertTrue(midway.connectionTest.running)
    // Un test en cours n'ouvre ni l'enregistrement ni le « Utiliser quand même ».
    assertFalse(midway.canSave)
    assertFalse(midway.canForce)

    repository.resumeTest()

    val end = viewModel.uiState.value
    assertEquals(CheckStepState.PASSED, end.connectionTest.apiVersion)
    assertEquals(CheckStepState.PASSED, end.connectionTest.tiles)
    assertFalse(end.connectionTest.running)
    assertTrue(end.canSave)
  }

  @Test
  fun `retoucher l URL interrompt le test en cours`() = runTest {
    repository.programTest()
    repository.pauseAfter = 2
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()
    viewModel.onInputChange("https://autre.exemple.org")
    // Le flux repart, mais son abonné a été abandonné : plus rien ne doit toucher à l'écran.
    repository.resumeTest()

    val state = viewModel.uiState.value
    assertEquals(CheckStepState.IDLE, state.connectionTest.reachable)
    assertEquals(CheckStepState.IDLE, state.connectionTest.apiVersion)
    assertFalse(state.connectionTest.running)
  }

  @Test
  fun `retoucher l URL rend le test caduc`() = runTest {
    repository.programTest()
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()
    viewModel.onInputChange("https://autre.exemple.org")

    assertEquals(CheckStepState.IDLE, viewModel.uiState.value.connectionTest.reachable)
    assertFalse(viewModel.uiState.value.canSave)
  }

  @Test
  fun `rien n est enregistre avant l annonce des effets du changement`() = runTest {
    repository.programTest()
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()
    viewModel.onSave()

    assertEquals(emptyList<ServerConfig>(), repository.savedConfigs)
    val dialog = viewModel.uiState.value.dialog
    assertTrue(dialog is ServerDialog.SwitchEffects)
    assertFalse((dialog as ServerDialog.SwitchEffects).untested)

    viewModel.onConfirmSwitch()

    assertEquals(listOf("https://motis.exemple.org"), repository.savedConfigs.map { it.baseUrl })
    assertTrue(repository.savedConfigs.single().hasTiles)
    assertNull(viewModel.uiState.value.dialog)
  }

  @Test
  fun `utiliser quand meme enregistre un serveur non teste, en le signalant`() = runTest {
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onUseAnyway()
    val dialog = viewModel.uiState.value.dialog as ServerDialog.SwitchEffects
    assertTrue(dialog.untested)

    viewModel.onConfirmSwitch()

    assertEquals("https://motis.exemple.org", repository.savedConfigs.single().baseUrl)
    assertNull(repository.savedConfigs.single().lastCheckedAt)
  }

  @Test
  fun `aucune requete en clair ne part sans consentement`() = runTest {
    val viewModel = viewModel()

    viewModel.onInputChange("http://192.168.1.10:8080")
    viewModel.onTestConnection()

    assertEquals(emptyList<String>(), repository.testedUrls)
    val dialog = viewModel.uiState.value.dialog
    assertTrue(dialog is ServerDialog.CleartextWarning)
    assertEquals("192.168.1.10", (dialog as ServerDialog.CleartextWarning).host)
    assertEquals(PendingCleartextAction.TEST, dialog.pending)
  }

  @Test
  fun `le consentement accepte relance l action et n est demande qu une fois par hote`() = runTest {
    repository.programTest(tilesAvailable = false)
    val viewModel = viewModel()

    viewModel.onInputChange("http://192.168.1.10:8080")
    viewModel.onTestConnection()
    viewModel.onAcceptCleartext()

    assertEquals(listOf("192.168.1.10"), consentStore.acceptedHosts)
    assertEquals(listOf("http://192.168.1.10:8080"), repository.testedUrls)
    assertNull(viewModel.uiState.value.dialog)

    // Deuxième requête vers le même hôte : plus aucun avertissement.
    viewModel.onTestConnection()

    assertEquals(1, consentStore.acceptedHosts.size)
    assertEquals(2, repository.testedUrls.size)
    assertNull(viewModel.uiState.value.dialog)
  }

  @Test
  fun `un consentement deja memorise n interrompt plus rien`() = runTest {
    val store = FakeCleartextConsentStore(initial = setOf("192.168.1.10"))
    repository.programTest(tilesAvailable = false)
    val viewModel = ServerSettingsViewModel(repository, store, cacheReset.reset, savedState)

    viewModel.onInputChange("http://192.168.1.10:8080")
    viewModel.onTestConnection()

    assertEquals(listOf("http://192.168.1.10:8080"), repository.testedUrls)
    assertNull(viewModel.uiState.value.dialog)
  }

  @Test
  fun `une bascule vers un serveur connu ne demande pas de ressaisir l URL`() = runTest {
    val known = ServerConfig(baseUrl = "https://motis.exemple.org", label = "motis.exemple.org")
    repository.addKnown(known)
    val viewModel = viewModel()

    viewModel.onSelectKnownServer(known)

    assertEquals("https://motis.exemple.org", viewModel.uiState.value.input)
    val dialog = viewModel.uiState.value.dialog as ServerDialog.SwitchEffects
    assertEquals("https://motis.exemple.org", dialog.baseUrl)

    viewModel.onConfirmSwitch()

    assertEquals("https://motis.exemple.org", repository.savedConfigs.single().baseUrl)
  }

  @Test
  fun `une bascule sans nouveau test conserve le drapeau des tuiles`() = runTest {
    val known = ServerConfig(
      baseUrl = "https://motis.exemple.org",
      label = "motis.exemple.org",
      hasTiles = true,
    )
    repository.addKnown(known)
    val viewModel = viewModel()

    viewModel.onSelectKnownServer(known)
    viewModel.onConfirmSwitch()

    assertTrue(repository.savedConfigs.single().hasTiles)
  }

  @Test
  fun `le balayage retire un serveur des serveurs connus`() = runTest {
    val viewModel = viewModel()

    viewModel.onForgetServer("https://motis.exemple.org")

    assertEquals(listOf("https://motis.exemple.org"), repository.forgottenUrls)
  }

  @Test
  fun `le retour au serveur par defaut passe par la meme confirmation`() = runTest {
    val other = ServerConfig(baseUrl = "https://motis.exemple.org", label = "motis.exemple.org")
    repository.save(other)
    val viewModel = viewModel()

    viewModel.onResetToDefault()
    val dialog = viewModel.uiState.value.dialog as ServerDialog.SwitchEffects
    assertEquals(ServerUrl.DEFAULT_BASE_URL, dialog.baseUrl)

    viewModel.onConfirmSwitch()

    assertEquals(ServerUrl.DEFAULT_BASE_URL, repository.savedConfigs.last().baseUrl)
  }

  @Test
  fun `annuler une boite de dialogue n enregistre rien`() = runTest {
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onUseAnyway()
    viewModel.onDismissDialog()

    assertNull(viewModel.uiState.value.dialog)
    assertEquals(emptyList<ServerConfig>(), repository.savedConfigs)
  }

  @Test
  fun `un changement de serveur vide les trois caches`() = runTest {
    // SPEC.md § 5.6.1 : la boîte de confirmation annonce à l'usager que « les caches de résultats,
    // de géocodage et de tuiles sont vidés ». Elle l'annonçait sans que rien ne le fasse.
    repository.programTest()
    val viewModel = viewModel()

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onTestConnection()
    viewModel.onSave()

    // Rien n'est vidé tant que l'usager n'a pas confirmé : il peut encore renoncer.
    assertEquals(Triple(0, 0, 0), cacheReset.counts())

    viewModel.onConfirmSwitch()

    assertEquals(Triple(1, 1, 1), cacheReset.counts())
  }

  @Test
  fun `les quatre chemins de changement de serveur vident les caches`() = runTest {
    val known = ServerConfig(baseUrl = "https://connu.exemple.org", label = "connu.exemple.org")
    repository.addKnown(known)
    val viewModel = viewModel()

    // 1. « Utiliser quand même », sans test concluant.
    viewModel.onInputChange("https://force.exemple.org")
    viewModel.onUseAnyway()
    viewModel.onConfirmSwitch()
    assertEquals(Triple(1, 1, 1), cacheReset.counts())

    // 2. Bascule vers un serveur déjà mémorisé, sans ressaisie.
    viewModel.onSelectKnownServer(known)
    viewModel.onConfirmSwitch()
    assertEquals(Triple(2, 2, 2), cacheReset.counts())

    // 3. Retour au serveur par défaut.
    viewModel.onResetToDefault()
    viewModel.onConfirmSwitch()
    assertEquals(Triple(3, 3, 3), cacheReset.counts())

    // 4. « Enregistrer » après un test concluant.
    repository.programTest()
    viewModel.onInputChange("https://teste.exemple.org")
    viewModel.onTestConnection()
    viewModel.onSave()
    viewModel.onConfirmSwitch()
    assertEquals(Triple(4, 4, 4), cacheReset.counts())

    assertEquals(4, repository.savedConfigs.size)
  }

  @Test
  fun `un cache recalcitrant n empeche ni les autres purges ni le changement de serveur`() = runTest {
    val stubborn = RecordingCacheReset(
      resultsOutcome = Outcome.Failure(EscaleError.Unknown(cause = null)),
      geocodeOutcome = Outcome.Failure(EscaleError.Unknown(cause = null)),
      tilesPurged = false,
    )
    val viewModel = ServerSettingsViewModel(repository, consentStore, stubborn.reset, savedState)

    viewModel.onInputChange("https://motis.exemple.org")
    viewModel.onUseAnyway()
    viewModel.onConfirmSwitch()

    // Les trois sont tentées, aucune n'interrompt les suivantes...
    assertEquals(Triple(1, 1, 1), stubborn.counts())
    // ...et le serveur est bel et bien changé, sans dialogue d'erreur.
    assertEquals("https://motis.exemple.org", repository.savedConfigs.single().baseUrl)
    assertNull(viewModel.uiState.value.dialog)
  }

  @Test
  fun `oublier un serveur ne vide aucun cache`() = runTest {
    // Le balayage retire une fiche de la liste ; il ne change pas le serveur en service, et
    // SPEC.md § 5.6.1 ne rattache la purge qu'au changement de serveur.
    val viewModel = viewModel()

    viewModel.onForgetServer("https://motis.exemple.org")

    assertEquals(Triple(0, 0, 0), cacheReset.counts())
  }

  @Test
  fun `la saisie survit a la mort du processus`() = runTest {
    // Anomalie A3 : le système tue l'application en arrière-plan, et le champ revenait vide.
    viewModel().onInputChange("exemple.org/api/")

    val restored = viewModel()

    assertEquals("exemple.org/api/", restored.uiState.value.input)
    assertEquals("https://exemple.org", restored.uiState.value.normalizedInput)
  }

  @Test
  fun `une saisie aberrante est de nouveau signalee apres la mort du processus`() = runTest {
    viewModel().onInputChange("ftp://exemple.org")

    val restored = viewModel()

    assertTrue(restored.uiState.value.inputInvalid)
    assertNull(restored.uiState.value.normalizedInput)
  }

  @Test
  fun `sans saisie, le champ reprend le serveur en service apres la mort du processus`() = runTest {
    viewModel()

    val restored = viewModel()

    assertEquals(ServerUrl.DEFAULT_BASE_URL, restored.uiState.value.input)
  }
}
