package io.github.mgdx.escale.core.repository

import io.github.mgdx.escale.core.model.ServerConfig
import io.github.mgdx.escale.core.model.ServerTestProgress
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow

/** Le serveur MOTIS courant et les serveurs déjà utilisés (SPEC.md § 4.1 et § 5.6.1). */
interface ServerRepository {
  /** Serveur en cours d'utilisation. Émet dès l'abonnement, avec le serveur par défaut à défaut. */
  val current: Flow<ServerConfig>

  /** Serveurs déjà utilisés, les plus récents d'abord, serveur courant compris. */
  val knownServers: Flow<List<ServerConfig>>

  /**
   * Enregistre [config] comme serveur courant et l'ajoute aux serveurs connus.
   *
   * L'appelant a la responsabilité de n'appeler cette fonction qu'après un test réussi, ou après
   * un « Utiliser quand même » explicite de l'usager (SPEC.md § 5.6.1). [ServerConfig.baseUrl] doit
   * déjà être normalisée par `ServerUrl.normalize`.
   */
  suspend fun save(config: ServerConfig): Outcome<Unit>

  /** Retire un serveur de la liste des serveurs connus. Sans effet sur le serveur courant. */
  suspend fun forget(baseUrl: String): Outcome<Unit>

  /** Rétablit l'instance publique par défaut, action toujours accessible (SPEC.md § 5.6.1). */
  suspend fun resetToDefault(): Outcome<Unit>

  /**
   * Test de connexion en trois étapes : santé, version d'API, tuiles (SPEC.md § 5.6.1).
   *
   * Rend un flux **froid** : rien ne part avant la collecte, et chaque étape signale son départ
   * puis son verdict au fur et à mesure, pour que l'écran fasse progresser ses trois lignes
   * séparément. Le flux se termine de lui-même quand la troisième étape a rendu son verdict ;
   * l'abandonner interrompt le test.
   *
   * Aucune émission ne vaut échec du test : c'est l'appelant qui décide de ce que valent les trois
   * verdicts réunis — une API trop ancienne ferme l'enregistrement direct, un serveur sans tuiles
   * reste utilisable (SPEC.md § 5.7).
   */
  fun test(baseUrl: String): Flow<ServerTestProgress>
}
