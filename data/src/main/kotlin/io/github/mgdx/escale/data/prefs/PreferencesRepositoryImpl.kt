package io.github.mgdx.escale.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import io.github.mgdx.escale.core.model.ClockFormat
import io.github.mgdx.escale.core.model.DisplayPreferences
import io.github.mgdx.escale.core.model.ElevationCosts
import io.github.mgdx.escale.core.model.PedestrianProfile
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.SearchPreferences
import io.github.mgdx.escale.core.model.ThemeChoice
import io.github.mgdx.escale.core.repository.PreferencesRepository
import io.github.mgdx.escale.core.result.EscaleError
import io.github.mgdx.escale.core.result.Outcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.time.Duration

/**
 * Réglages de SPEC.md § 5.6, persistés en DataStore Preferences.
 *
 * Écrit sur le modèle de [ServerRepositoryImpl], et pour les mêmes raisons : le fichier vit dans le
 * stockage privé de l'application, une lecture impossible rend les valeurs par défaut au lieu de
 * lever, et **rien n'est jamais journalisé**. Ce dépôt contient les habitudes de déplacement de
 * l'usager — vitesse de marche, fauteuil roulant, types de véhicules refusés : SPEC.md § 11
 * interdit qu'elles sortent d'ici, y compris dans une trace de débogage.
 *
 * Aucune valeur par défaut n'est écrite au premier lancement : tant que l'usager n'a rien réglé, le
 * fichier ne contient aucune de ces clés et les flux rendent `SearchPreferences()` et
 * `DisplayPreferences()` tels quels. La requête émise est alors, à l'octet près, celle d'avant
 * l'existence de cet écran.
 */
class PreferencesRepositoryImpl(private val dataStore: DataStore<Preferences>) : PreferencesRepository {

  override val searchPreferences: Flow<SearchPreferences> = readable()
    .map(::toSearchPreferences)
    .distinctUntilChanged()

  override val displayPreferences: Flow<DisplayPreferences> = readable()
    .map(::toDisplayPreferences)
    .distinctUntilChanged()

  override suspend fun updateSearchPreferences(preferences: SearchPreferences): Outcome<Unit> = write { stored ->
    stored[KEY_PEDESTRIAN_SPEED] = preferences.pedestrianSpeedMetersPerSecond
    stored[KEY_PEDESTRIAN_PROFILE] = preferences.pedestrianProfile.name
    stored[KEY_CYCLING_SPEED] = preferences.cyclingSpeedMetersPerSecond
    stored[KEY_ELEVATION_COSTS] = preferences.elevationCosts.name
    stored[KEY_ADDITIONAL_TRANSFER_SECONDS] = preferences.additionalTransferTime.seconds
    // Absence de clé et « sans limite » sont le même état : le paramètre n'est pas envoyé. Zéro,
    // lui, est un choix légitime — aucune correspondance — et doit donc rester distinct.
    val maxTransfers = preferences.maxTransfers
    if (maxTransfers == null) stored.remove(KEY_MAX_TRANSFERS) else stored[KEY_MAX_TRANSFERS] = maxTransfers
    stored[KEY_REQUIRE_BIKE_TRANSPORT] = preferences.requireBikeTransport
    stored[KEY_RENTAL_FORM_FACTORS] = preferences.allowedRentalFormFactors.map { it.name }.toSet()
  }

  override suspend fun updateDisplayPreferences(preferences: DisplayPreferences): Outcome<Unit> = write { stored ->
    stored[KEY_THEME] = preferences.theme.name
    stored[KEY_CLOCK_FORMAT] = preferences.clockFormat.name
    stored[KEY_SHOW_STOPS] = preferences.showStops
    stored[KEY_SHOW_RENTALS] = preferences.showRentals
    stored[KEY_SHOW_POINTS_OF_INTEREST] = preferences.showPointsOfInterest
    stored[KEY_HISTORY_ENABLED] = preferences.historyEnabled
  }

  /**
   * Retire les seules clés de cet écran.
   *
   * Le fichier DataStore est **partagé** avec le serveur configuré, le consentement au trafic en
   * clair et la dernière position de caméra : un `clear()` global déconnecterait l'usager de son
   * serveur pour un « rétablir les valeurs par défaut » d'un tout autre écran.
   */
  override suspend fun resetToDefaults(): Outcome<Unit> = write { stored ->
    OWNED_KEYS.forEach { key -> stored -= key }
  }

  private fun toSearchPreferences(stored: Preferences) = SearchPreferences(
    pedestrianSpeedMetersPerSecond = stored[KEY_PEDESTRIAN_SPEED] ?: DEFAULT_SEARCH.pedestrianSpeedMetersPerSecond,
    pedestrianProfile =
    enumOrDefault(stored[KEY_PEDESTRIAN_PROFILE], PedestrianProfile.entries, DEFAULT_SEARCH.pedestrianProfile),
    cyclingSpeedMetersPerSecond = stored[KEY_CYCLING_SPEED] ?: DEFAULT_SEARCH.cyclingSpeedMetersPerSecond,
    elevationCosts = enumOrDefault(stored[KEY_ELEVATION_COSTS], ElevationCosts.entries, DEFAULT_SEARCH.elevationCosts),
    additionalTransferTime = stored[KEY_ADDITIONAL_TRANSFER_SECONDS]
      ?.let(Duration::ofSeconds)
      ?: DEFAULT_SEARCH.additionalTransferTime,
    maxTransfers = stored[KEY_MAX_TRANSFERS],
    requireBikeTransport = stored[KEY_REQUIRE_BIKE_TRANSPORT] ?: DEFAULT_SEARCH.requireBikeTransport,
    allowedRentalFormFactors = stored[KEY_RENTAL_FORM_FACTORS]
      ?.mapNotNull { name -> RentalFormFactor.entries.firstOrNull { it.name == name } }
      ?.toSet()
      ?: DEFAULT_SEARCH.allowedRentalFormFactors,
  )

  private fun toDisplayPreferences(stored: Preferences) = DisplayPreferences(
    theme = enumOrDefault(stored[KEY_THEME], ThemeChoice.entries, DEFAULT_DISPLAY.theme),
    clockFormat = enumOrDefault(stored[KEY_CLOCK_FORMAT], ClockFormat.entries, DEFAULT_DISPLAY.clockFormat),
    showStops = stored[KEY_SHOW_STOPS] ?: DEFAULT_DISPLAY.showStops,
    showRentals = stored[KEY_SHOW_RENTALS] ?: DEFAULT_DISPLAY.showRentals,
    showPointsOfInterest = stored[KEY_SHOW_POINTS_OF_INTEREST] ?: DEFAULT_DISPLAY.showPointsOfInterest,
    historyEnabled = stored[KEY_HISTORY_ENABLED] ?: DEFAULT_DISPLAY.historyEnabled,
  )

  /**
   * Une valeur d'énumération inconnue — écrite par une version future, ou par un fichier abîmé — se
   * lit comme la valeur par défaut, jamais comme une exception : un réglage illisible ne doit pas
   * empêcher l'application de démarrer.
   */
  private fun <T : Enum<T>> enumOrDefault(name: String?, values: List<T>, fallback: T): T =
    values.firstOrNull { it.name == name } ?: fallback

  /** Un fichier illisible ([IOException]) rend les valeurs par défaut, comme au premier lancement. */
  private fun readable(): Flow<Preferences> = dataStore.data.catch { cause ->
    if (cause is IOException) emit(emptyPreferences()) else throw cause
  }

  private suspend fun write(block: (MutablePreferences) -> Unit): Outcome<Unit> = try {
    dataStore.edit(block)
    Outcome.Success(Unit)
  } catch (failure: IOException) {
    // Le nom de la classe d'exception, et rien d'autre : aucune valeur de réglage ne doit
    // remonter dans une erreur affichable (SPEC.md § 11, docs/architecture.md § 6).
    Outcome.Failure(EscaleError.Unknown(cause = failure::class.simpleName))
  }

  private companion object {
    val DEFAULT_SEARCH = SearchPreferences()
    val DEFAULT_DISPLAY = DisplayPreferences()

    val KEY_PEDESTRIAN_SPEED = doublePreferencesKey("search_pedestrian_speed")
    val KEY_PEDESTRIAN_PROFILE = stringPreferencesKey("search_pedestrian_profile")
    val KEY_CYCLING_SPEED = doublePreferencesKey("search_cycling_speed")
    val KEY_ELEVATION_COSTS = stringPreferencesKey("search_elevation_costs")
    val KEY_ADDITIONAL_TRANSFER_SECONDS = longPreferencesKey("search_additional_transfer_seconds")
    val KEY_MAX_TRANSFERS = intPreferencesKey("search_max_transfers")
    val KEY_REQUIRE_BIKE_TRANSPORT = booleanPreferencesKey("search_require_bike_transport")
    val KEY_RENTAL_FORM_FACTORS = stringSetPreferencesKey("search_rental_form_factors")

    val KEY_THEME = stringPreferencesKey("display_theme")
    val KEY_CLOCK_FORMAT = stringPreferencesKey("display_clock_format")
    val KEY_SHOW_STOPS = booleanPreferencesKey("display_show_stops")
    val KEY_SHOW_RENTALS = booleanPreferencesKey("display_show_rentals")
    val KEY_SHOW_POINTS_OF_INTEREST = booleanPreferencesKey("display_show_points_of_interest")
    val KEY_HISTORY_ENABLED = booleanPreferencesKey("display_history_enabled")

    /** Les clés que ce dépôt possède, et les seules que [resetToDefaults] a le droit d'effacer. */
    val OWNED_KEYS: List<Preferences.Key<*>> = listOf(
      KEY_PEDESTRIAN_SPEED,
      KEY_PEDESTRIAN_PROFILE,
      KEY_CYCLING_SPEED,
      KEY_ELEVATION_COSTS,
      KEY_ADDITIONAL_TRANSFER_SECONDS,
      KEY_MAX_TRANSFERS,
      KEY_REQUIRE_BIKE_TRANSPORT,
      KEY_RENTAL_FORM_FACTORS,
      KEY_THEME,
      KEY_CLOCK_FORMAT,
      KEY_SHOW_STOPS,
      KEY_SHOW_RENTALS,
      KEY_SHOW_POINTS_OF_INTEREST,
      KEY_HISTORY_ENABLED,
    )
  }
}
