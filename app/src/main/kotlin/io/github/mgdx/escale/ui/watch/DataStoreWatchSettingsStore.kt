package io.github.mgdx.escale.ui.watch

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.github.mgdx.escale.core.model.WatchAlertSettings
import io.github.mgdx.escale.core.model.WatchIssue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.time.Duration
import java.time.Instant

/**
 * Les réglages de la surveillance, dans le fichier de préférences de l'application.
 *
 * Ils vivent dans le **fichier de préférences** de l'application, à côté du serveur configuré et du
 * consentement au trafic en clair, et non dans la base : ce sont des réglages, pas des données de
 * trajet. Le magasin ne possède que ses propres clés, comme `MapCameraStore` ou
 * `DataStoreCleartextConsentStore` (docs/architecture.md § 3, règle 3).
 *
 * **Rien n'en sort de l'appareil et rien n'est journalisé** (SPEC.md § 11) : un fichier de
 * préférences illisible ne fait que rendre les valeurs par défaut.
 */
class DataStoreWatchSettingsStore(private val dataStore: DataStore<Preferences>) : WatchSettingsStore {

  private val preferences: Flow<Preferences> = dataStore.data
    .catch { cause -> if (cause is IOException) emit(emptyPreferences()) else throw cause }

  override val settings: Flow<WatchAlertSettings> = preferences.map { stored ->
    WatchAlertSettings(
      delayThreshold = stored[KEY_THRESHOLD]?.let(Duration::ofMinutes) ?: WatchAlertSettings.DEFAULT_THRESHOLD,
      notifyWhenNothingChanged = stored[KEY_NOTIFY_ALWAYS] == true,
    )
  }

  override suspend fun update(settings: WatchAlertSettings) {
    write { stored ->
      stored[KEY_THRESHOLD] = settings.delayThreshold.toMinutes()
      stored[KEY_NOTIFY_ALWAYS] = settings.notifyWhenNothingChanged
    }
  }

  override fun lastCheck(journeyId: Long): Flow<WatchCheckRecord?> =
    preferences.map { stored -> stored[recordKey(journeyId)]?.let(::decode) }

  override suspend fun record(journeyId: Long, record: WatchCheckRecord) {
    write { stored -> stored[recordKey(journeyId)] = encode(record) }
  }

  override suspend fun forget(journeyId: Long) {
    write { stored -> stored.remove(recordKey(journeyId)) }
  }

  /** Une écriture perdue ne coûte qu'un réglage à refaire : elle ne doit pas faire échouer l'appel. */
  private suspend fun write(block: (MutablePreferences) -> Unit) {
    try {
      dataStore.edit(block)
    } catch (_: IOException) {
      // Le fichier de préférences est inaccessible : les valeurs par défaut s'appliqueront.
    }
  }

  private fun encode(record: WatchCheckRecord): String = json.encodeToString(
    StoredCheck(
      checkedAt = record.checkedAt.toEpochMilli(),
      issue = record.issue?.name,
      lineName = record.lineName,
      delayMinutes = record.delay?.toMinutes(),
      suggestedDeparture = record.suggestedDeparture?.toEpochMilli(),
      notified = record.notified,
    ),
  )

  /** Une valeur illisible — format changé, écriture interrompue — vaut « aucune vérification ». */
  private fun decode(stored: String): WatchCheckRecord? = try {
    json.decodeFromString<StoredCheck>(stored).let { saved ->
      WatchCheckRecord(
        checkedAt = Instant.ofEpochMilli(saved.checkedAt),
        issue = saved.issue?.let { name -> WatchIssue.entries.firstOrNull { it.name == name } },
        lineName = saved.lineName,
        delay = saved.delayMinutes?.let(Duration::ofMinutes),
        suggestedDeparture = saved.suggestedDeparture?.let(Instant::ofEpochMilli),
        notified = saved.notified,
      )
    }
  } catch (_: SerializationException) {
    null
  }

  @Serializable
  private data class StoredCheck(
    val checkedAt: Long,
    val issue: String?,
    val lineName: String?,
    val delayMinutes: Long?,
    val suggestedDeparture: Long?,
    val notified: Boolean,
  )

  private companion object {
    val KEY_THRESHOLD = longPreferencesKey("watch_delay_threshold_minutes")
    val KEY_NOTIFY_ALWAYS = booleanPreferencesKey("watch_notify_when_nothing_changed")

    fun recordKey(journeyId: Long) = stringPreferencesKey("watch_last_check_$journeyId")

    /** `ignoreUnknownKeys` par principe de projet : une valeur écrite par une version antérieure se relit. */
    val json = Json { ignoreUnknownKeys = true }
  }
}
