package io.github.mgdx.escale.ui.server

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Les hôtes joignables en clair pour lesquels l'usager a confirmé l'avertissement.
 *
 * docs/architecture.md § 11.1 : la `network_security_config` d'Android est figée à la compilation,
 * elle ne peut donc pas autoriser au cas par cas un hôte saisi à l'exécution. Le trafic en clair est
 * en conséquence permis au niveau de la plateforme, et **c'est ce consentement qui porte la
 * garantie** : aucune requête `http://` ne part, test de connexion compris, tant que l'usager n'a
 * pas accepté l'avertissement pour l'hôte concerné. Il n'est demandé qu'une fois par hôte.
 *
 * Seul l'hôte est mémorisé, jamais l'URL complète ni un chemin : SPEC.md § 8 et § 11 interdisent de
 * conserver une requête de l'usager.
 */
interface CleartextConsentStore {
  /** Les hôtes déjà acceptés. Émet dès l'abonnement. */
  val consentedHosts: Flow<Set<String>>

  /** Mémorise l'acceptation de [host], en minuscules et sans port. */
  suspend fun accept(host: String)
}

/**
 * Mise en œuvre sur DataStore Preferences, adossée au même fichier que le reste des réglages.
 *
 * Elle vit dans le paquet de l'écran parce que c'est lui qui porte la garantie, et parce que le lot
 * qui l'a écrite n'avait pas la main sur `:core` ni sur `:data` ; sa place définitive est
 * `:data.prefs`, avec son interface dans `:core.repository`.
 */
class DataStoreCleartextConsentStore(private val dataStore: DataStore<Preferences>) : CleartextConsentStore {

  /** Un fichier de préférences illisible ne doit pas valoir consentement : la liste est alors vide. */
  override val consentedHosts: Flow<Set<String>> = dataStore.data
    .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }
    .map { preferences -> preferences[KEY_HOSTS].orEmpty() }

  // `SwallowedException` est levée en connaissance de cause : une écriture DataStore impossible
  // n'est pas rattrapable ici et ne doit pas faire tomber l'écran. La conséquence est sûre —
  // l'avertissement sera redemandé au prochain lancement — et l'exception ne peut pas être
  // journalisée, une trace ne devant contenir aucune donnée de l'usager (SPEC.md § 8 et § 11).
  @Suppress("SwallowedException")
  override suspend fun accept(host: String) {
    val normalized = host.lowercase()
    try {
      dataStore.edit { preferences ->
        preferences[KEY_HOSTS] = preferences[KEY_HOSTS].orEmpty() + normalized
      }
    } catch (failure: IOException) {
      return
    }
  }

  private companion object {
    val KEY_HOSTS = stringSetPreferencesKey("cleartext_accepted_hosts")
  }
}
