# Architecture d'Escale — contrat de développement

> Ce document est **contraignant**. `SPEC.md` dit *quoi* faire, ce document dit *où* et *sous quel
> nom*. Un agent qui développe sur ce dépôt le lit avant d'écrire une ligne et ne s'en écarte pas
> sans le signaler au superviseur. Il existe parce que plusieurs agents travaillent en parallèle
> sans se parler : c'est lui qui garantit que leur code s'assemble.

## 1. Modules

```
:core   Kotlin pur (plugin kotlin-jvm). AUCUN import android.*, androidx.*, kotlinx.serialization.
        Modèles de domaine, formatage, géométrie, construction des paramètres de requête.
        Entièrement testable en JVM. Dépendances autorisées : kotlinx-coroutines-core, java.time.
:data   Bibliothèque Android. Client HTTP Ktor, DTO, mapping, Room, DataStore.
        Dépend de :core. Ne dépend jamais de :app.
:app    Interface Compose, navigation, thème, ressources, AppContainer.
        Dépend de :core et de :data.
```

Règle d'or : **si une règle est testable sans Android, elle va dans `:core`**. De la logique dans
un composable est une erreur de placement, pas un raccourci.

## 2. Paquets

```
:core   io.github.mgdx.escale.core.model       types de domaine (§4)
        io.github.mgdx.escale.core.format      durées, heures, distances, retards, pluriels
        io.github.mgdx.escale.core.geo         polylignes, emprises, calculs géographiques
        io.github.mgdx.escale.core.query       paramètres de requête plan par catégorie
        io.github.mgdx.escale.core.repository  INTERFACES de dépôt (§5)
        io.github.mgdx.escale.core.result      Outcome et EscaleError (§6)

:data   io.github.mgdx.escale.data.net         MotisClient, moteur Ktor, en-têtes, erreurs HTTP
        io.github.mgdx.escale.data.dto         DTO kotlinx.serialization — ne sortent JAMAIS de :data
        io.github.mgdx.escale.data.mapper      DTO -> domaine, une fonction par type
        io.github.mgdx.escale.data.repository  implémentations des interfaces de :core
        io.github.mgdx.escale.data.db          Room : entités, DAO, base
        io.github.mgdx.escale.data.prefs       DataStore Preferences

:app    io.github.mgdx.escale                  EscaleApplication, AppContainer, MainActivity
        io.github.mgdx.escale.ui.theme         thème Material 3
        io.github.mgdx.escale.ui.common        composables réutilisables (états vides, erreurs…)
        io.github.mgdx.escale.ui.<ecran>       un paquet par écran : Screen, ViewModel, UiState, Route
        io.github.mgdx.escale.nav              EscaleNavHost
```

## 3. Règles anti-conflit — impératives

Plusieurs agents écrivent en parallèle. Ces règles existent pour qu'ils ne se marchent pas dessus.

1. **`gradle/libs.versions.toml` est figé** par le lot fondation, qui y déclare d'emblée *toutes*
   les bibliothèques du projet, y compris celles des jalons suivants. Un agent qui a besoin d'une
   bibliothèque déjà listée l'ajoute à son `build.gradle.kts` de module ; il ne touche pas au
   catalogue. S'il lui faut une bibliothèque absente du catalogue, il **s'arrête et demande**.
2. **Les chaînes sont éclatées par écran.** Jamais un unique `strings.xml` géant :
   `strings.xml` (commun : nom de l'appli, actions génériques, erreurs générales),
   puis `strings_map.xml`, `strings_search.xml`, `strings_results.xml`, `strings_detail.xml`,
   `strings_departures.xml`, `strings_settings.xml`, `strings_favorites.xml`, `strings_alerts.xml`.
   Android fusionne tous les `res/values/*.xml` : un lot n'écrit que dans le fichier de son écran,
   et dans son équivalent `values-fr/`. **Toute chaîne ajoutée en anglais l'est aussi en français,
   dans le même commit.**
3. **Pas de fichier de fabrique partagé.** Chaque `ViewModel` porte sa propre fabrique :
   ```kotlin
   class SearchViewModel(...) : ViewModel() {
     companion object {
       fun factory(container: AppContainer) = viewModelFactory {
         initializer { SearchViewModel(container.geocodeRepository, ...) }
       }
     }
   }
   ```
4. **Navigation typée.** Chaque écran déclare sa propre route, dans son propre paquet :
   `@Serializable data class DetailRoute(val journeyId: String)`. `EscaleNavHost.kt` est le seul
   fichier partagé : un lot n'y ajoute que le `composable<XRoute> { }` de son écran, jamais une
   réorganisation.
5. **Un agent ne modifie `AndroidManifest.xml`, `build.gradle.kts` racine, le thème ou
   `AppContainer` que si sa tâche le nomme explicitement**, et il le signale dans son rapport.
6. **Interdiction absolue d'utiliser `adb`.** Il n'y a qu'un téléphone pour tous les agents. La
   vérification sur appareil est faite par le superviseur, en fin de sprint. Les agents vérifient
   par la compilation et les tests JVM.

## 4. Types de domaine (`:core.model`)

Noms et emplacements figés. Les champs sont dérivés de `docs/motis-openapi.yaml`, qui fait foi.

| Type | Nature | Notes |
|---|---|---|
| `LatLon` | `data class(lat: Double, lon: Double)` | jamais `Pair<Double, Double>` |
| `BoundingBox` | `data class(min: LatLon, max: LatLon)` | emprise carte, `expandBy(ratio)` |
| `TransitMode` | `enum` | reprend `Mode` de l'OpenAPI, **sans** les `DEBUG_*` ni les valeurs dépréciées (`AREAL_LIFT`, `METRO`, `CABLE_CAR`, `REGIONAL_FAST_RAIL`) |
| `PlaceKind` | `enum ADDRESS, PLACE, STOP` | `LocationType` de l'API |
| `Location` | point de départ / d'arrivée saisi | `id: String?` (stopId), `name`, `description: String?`, `coordinates`, `kind`, `servedModes: List<TransitMode>` |
| `Place` | point d'une portion de trajet | `name`, `coordinates`, `stopId: String?`, `track: String?`, `scheduledTime: Instant`, `time: Instant`, `level: Double?` |
| `TimeChoice` | `sealed interface` | `Now`, `DepartAt(Instant)`, `ArriveBy(Instant)` |
| `JourneyCategory` | `enum TRANSIT, CAR, BIKE, WALK` | un onglet de résultats = une valeur |
| `SearchPreferences` | réglages de recherche (§5.6 de la spec) | vitesses, profil piéton, dénivelé, correspondances, types de véhicules partagés |
| `SearchQuery` | `from`, `to`, `time`, `category`, `preferences` | |
| `Journey` | un trajet proposé | `id: String?`, `startTime`, `endTime`, `scheduledStartTime`, `scheduledEndTime`, `duration`, `transfers`, `legs` |
| `JourneyLeg` | `sealed interface` | sous-types **exactement** : `Transit`, `Walk`, `Bike`, `Car`, `Rental` |
| `StopVisit` | arrêt intermédiaire | `place`, `arrival`, `departure`, `cancelled` |
| `Stop` | arrêt sur la carte / en favori | `id`, `name`, `coordinates`, `modes: List<TransitMode>` |
| `StopTimeEntry` | un départ à un arrêt | ligne, direction, quai, heures théorique et réelle, `cancelled` |
| `Disruption` | une `Alert` de l'API | `headerText`, `descriptionText`, `severity`, `cause`, `effect`, `periods`, `url` |
| `RentalAvailability` | état d'une station | `numVehiclesAvailable`, `vehicleTypesAvailable`, `vehicleDocksAvailable`, `isRenting`, `isReturning`, `retrievedAt: Instant` |
| `ServerConfig` | serveur configuré | `baseUrl`, `label`, `hasTiles: Boolean`, `lastCheckedAt: Instant?` |
| `Delay` | écart temps réel | calculé dans `:core.format`, jamais dans l'UI |

**Champs communs à toutes les `JourneyLeg`** (déclarés sur l'interface) :
`startTime`, `endTime`, `scheduledStartTime`, `scheduledEndTime`, `from: Place`, `to: Place`,
`distanceMeters: Double?`, `geometry: List<LatLon>`, `realTime: Boolean`, `cancelled: Boolean`,
`alerts: List<Disruption>`.

**Le temps se manipule en `java.time`** — `Instant`, `Duration`, `ZoneId`, `LocalDate`, `LocalTime`.
Disponible dès l'API 26, qui est notre `minSdk` : aucune dépendance, aucun desugaring. Ne pas
introduire `kotlinx-datetime`, ne pas mélanger avec `kotlin.time.Duration`.

## 5. Interfaces de dépôt (`:core.repository`)

Déclarées dans `:core` (donc sans type Android), implémentées dans `:data.repository`. Toutes les
fonctions sont `suspend` et rendent un `Outcome<T>` ; celles qui observent un état rendent un `Flow`.

```
ServerRepository      serveur courant (Flow), enregistrer, tester (3 étapes du §5.6.1), serveurs connus
GeocodeRepository     autocomplétion, géocodage inverse
PlanRepository        recherche d'itinéraire par catégorie, pagination par curseur, rafraîchissement
TripRepository        détail d'une course, prochains départs à un arrêt
StopsRepository       arrêts par emprise et par palier de zoom
RentalsRepository     stations et véhicules en libre-service, disponibilités
PreferencesRepository réglages (Flow), écriture
FavoritesRepository   domicile, travail, lieux, arrêts, trajets
HistoryRepository     dernières recherches, effacement
```

## 6. Résultat et erreurs (`:core.result`)

`SPEC.md` §8 impose de distinguer les cas d'erreur à l'écran. Le type qui le permet est unique :

```kotlin
sealed interface Outcome<out T> {
  data class Success<T>(val value: T) : Outcome<T>
  data class Failure(val error: EscaleError) : Outcome<Nothing>
}

sealed interface EscaleError {
  data object NoNetwork : EscaleError
  data object Timeout : EscaleError
  data class ServerUnreachable(val statusCode: Int?) : EscaleError
  data class ApiVersionTooOld(val endpoint: String) : EscaleError   // 404 sur un point d'entrée v6
  data class BadRequest(val serverMessage: String?) : EscaleError   // 400/422, champ `error`
  data class Unknown(val cause: String?) : EscaleError
}
```

`EscaleError` ne porte **jamais** de coordonnée, d'adresse ni d'URL de requête : la spec §8 et §11
interdisent qu'une donnée de localisation se retrouve dans une trace, y compris en débogage.

## 7. Réseau (`:data.net`)

- **Ktor client, moteur OkHttp**, `kotlinx.serialization` avec `ignoreUnknownKeys = true` — non
  négociable, l'API MOTIS ajoute des champs sans préavis.
- En-tête `User-Agent` sur **chaque** requête : `Escale/<versionName> (+https://github.com/mgdx/escale)`,
  la version lue depuis `BuildConfig`, jamais codée en dur (spec §4.2).
- Délai d'expiration 30 s, **une seule** reprise, aucune reprise sur 4xx (spec §7.8).
- Les chemins sont construits à partir de la racine du serveur : l'URL enregistrée est la racine,
  `/api/v6/...` et `/tiles/...` sont ajoutés par le client.
- Un 404 sur un point d'entrée `v6` produit `ApiVersionTooOld`, jamais un plantage (spec §4.3).
- Aucune journalisation de corps de requête ni de réponse, même en `debug`.

## 8. Interface (`:app`)

- Un paquet par écran, contenant `XScreen.kt` (composables), `XViewModel.kt`, `XUiState.kt`,
  `XRoute.kt`. Le `ViewModel` n'importe rien de `androidx.compose.*`.
- L'état d'écran est une `data class` unique exposée en `StateFlow`, collectée avec
  `collectAsStateWithLifecycle()`.
- Material 3, couleurs dynamiques Android 12+, thème clair / sombre suivant le système.
- Accessibilité (spec §9) : `contentDescription` sur tout élément interactif, cible tactile 48 dp,
  aucune information portée par la seule couleur, lisible à 200 % d'agrandissement.
- **Aucune chaîne codée en dur**, y compris dans les `contentDescription` et les aperçus.

## 9. Injection de dépendances

`AppContainer`, instancié une fois dans `EscaleApplication`, détient les objets partagés et les
expose en `val` paresseux. **Hilt, Dagger, Koin et tout autre conteneur sont exclus** (spec §3).
Les `ViewModel` reçoivent leurs dépendances par constructeur, via la fabrique de la règle 3.

## 10. Tests

- `:core` — tests JVM obligatoires sur toute règle ajoutée. Le décodage de polyligne est testé aux
  **deux** précisions, 6 et 7 (spec §4.3).
- `:data` — moteur Ktor simulé (`MockEngine`), réponses JSON réelles capturées, rangées dans
  `data/src/test/resources/fixtures/`. Aucun test ne touche le réseau réel.
- `:app` — tests Compose sur les états liste / vide / erreur / chargement.

## 11. Décisions d'arbitrage (validées par le mainteneur)

Ces points ont été tranchés en cours de projet. Ils ont la même force que le reste du contrat.

### 11.1 Trafic en clair et serveur auto-hébergé

La spec §4.1 demandait une `network_security_config` n'autorisant le clair que pour les hôtes
saisis par l'utilisateur. **C'est impossible sur Android** : depuis Android 7 cette configuration
est figée à la compilation, un hôte saisi à l'exécution ne peut pas y être ajouté.

Décision retenue : **permissif au niveau plateforme, strict au niveau applicatif.**

- `network_security_config` : `cleartextTrafficPermitted="true"` sur la `base-config`.
- **La garantie est portée par le code** : l'écran « Serveur MOTIS » refuse toute URL `http://`
  tant que l'utilisateur n'a pas confirmé l'avertissement explicite décrit au §5.6.1. Aucune
  requête en clair ne part sans ce consentement, et il est demandé une fois par hôte.
- Le commentaire en tête du fichier XML doit dire que la restriction est applicative et pointer
  vers le code qui l'applique — sans quoi un relecteur F-Droid conclura à une négligence.
- `SPEC.md` §4.1 est amendé en conséquence, dans le même commit que le code.

### 11.2 Icônes

**Aucune bibliothèque d'icônes.** Chaque icône nécessaire est ajoutée à `app/src/main/res/drawable/`
sous forme de `VectorDrawable` XML, reprise du jeu officiel **Material Symbols** (Apache 2.0).

- Nommage : `ic_<sujet>.xml` en anglais — `ic_directions_bus.xml`, `ic_swap_vert.xml`.
- Style unique pour tout le projet : Material Symbols **Outlined**, graisse 400, `viewportWidth`
  et `viewportHeight` à 24, `android:tint="?attr/colorControlNormal"` jamais codé en dur.
- Un lot qui a besoin d'une icône déjà présente la réutilise ; il ne la redessine pas.
- Motif : zéro dépendance, aucun poids mort dans l'APK (spec §2 vise moins de 15 Mo),
  et rien qu'un relecteur F-Droid puisse reprocher.

### 11.3 Envoyer l'identifiant d'arrêt, pas ses coordonnées

Constat de terrain fait au sprint 1 contre `api.transitous.org` : une requête `plan` exprimée
**par coordonnées** autour d'une gare rendait zéro résultat, là où la même requête exprimée
**par `stopId`** en rendait cinq.

Conséquence contraignante pour l'écran de recherche : quand l'autocomplétion rend un `Location`
dont le `kind` vaut `PlaceKind.STOP`, c'est son `id` qui part dans `fromPlace`/`toPlace`, jamais
sa position. `PlanQueryBuilder` applique déjà cette règle ; l'interface ne doit pas la contourner
en reconstruisant un point à partir des coordonnées affichées.

### 11.4 Composition de l'écran d'accueil

L'écran d'accueil (spec §5.1) superpose trois choses écrites par trois lots différents : la carte
plein écran, la carte de recherche flottante, et la feuille de résultats. Pour qu'ils ne se
marchent pas dessus, la composition passe par des **emplacements**, jamais par des appels directs
d'un lot à l'autre :

```kotlin
@Composable
fun HomeScreen(
  modifier: Modifier = Modifier,
  searchCard: @Composable (PaddingValues) -> Unit = {},   // rempli par le lot « recherche »
  resultsSheet: @Composable (PaddingValues) -> Unit = {}, // rempli par le lot « résultats »
)
```

- Le lot **carte** possède `HomeScreen` et l'instance MapLibre. Il fournit les emplacements avec
  une valeur par défaut vide, de sorte que l'écran compile et s'affiche avant que les deux autres
  lots existent.
- Les lots **recherche** et **résultats** écrivent chacun leur composable dans leur propre paquet
  et ne modifient pas `HomeScreen`. Le branchement se fait dans `EscaleNavHost.kt`, à raison d'une
  ligne par lot.
- Le `PaddingValues` transmis porte les encarts système et la hauteur de la feuille ouverte :
  c'est ce qui permet au cadrage de trajet de tenir compte de la feuille (spec §5.7, règle 9).
