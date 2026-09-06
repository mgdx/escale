# L'API MOTIS vue par Escale

> Livrable de `SPEC.md` § 12. Ce document décrit **ce qu'Escale appelle réellement**, avec quels
> paramètres, ce qu'elle en fait, et les pièges rencontrés sur le terrain. Il ne redit ni la spec
> (qui dit *quoi* faire) ni `docs/architecture.md` (qui dit *où*). La référence de vérité sur les
> champs reste `docs/motis-openapi.yaml`, vendue dans le dépôt : en cas de doute, c'est elle qu'on
> lit, pas ce fichier.

Serveur de référence : `https://api.transitous.org`. L'usager peut en configurer un autre
(`SPEC.md` § 5.6.1). L'URL enregistrée est toujours la **racine** ; les chemins `/api/...` et
`/tiles/...` sont ajoutés par `data/net/MotisEndpoints.kt`, et par lui seul.

---

## 1. Les points d'entrée utilisés

| Point d'entrée | Appelé depuis | Rôle dans l'application |
|---|---|---|
| `GET /api/v1/health` | `MotisClient.health` | Étape 1 du test de connexion (§ 5.6.1) |
| `GET /api/v6/map/stops` | `MotisClient.probeApiVersion` | Étape 2 du test : le serveur connaît-il `v6` ? |
| `GET /tiles/{z}/{x}/{y}.mvt` | `MotisClient.probeTiles` | Étape 3 du test : le serveur sert-il un fond de carte ? |
| `GET /api/v1/geocode` | `GeocodeApi.geocode` | Autocomplétion du départ et de la destination |
| `GET /api/v1/reverse-geocode` | `GeocodeApi.reverseGeocode` | « Choisir sur la carte », position courante |
| `GET /api/v6/plan` | `MotisClient.plan` | La recherche d'itinéraire, une requête par onglet |
| `GET /api/v6/refresh-itinerary` | `MotisClient.refreshItinerary` | Recalcul temps réel d'un trajet déjà obtenu |
| `GET /api/v6/map/stops` | `StopsApi.mapStops` | Les arrêts affichés sur la carte, par emprise et par palier de zoom (§ 5.7) |
| `GET /api/v6/stop` | `StopsApi.stop` | Les lignes desservant un arrêt, pour l'infobulle de la carte (§ 5.7) |
| `GET /api/v6/stoptimes` | `TripApi.stopTimes` | Les prochains départs à un arrêt (§ 5.4) |
| `GET /api/v6/trip` | `TripApi.trip` | La desserte complète d'une course (§ 5.3) |
| `GET /api/v1/rentals` | `RentalsApi.around`, `RentalsApi.within` | Le libre-service : stations d'une portion, marqueurs de la carte (§ 5.3, § 5.7) |
| `GET /api/v1/map/initial` | `MapApi.initialCamera` | Dernier recours du cadrage initial de la carte (§ 5.1) |

La liste ci-dessus est **complète** : tout ce que `SPEC.md` § 4.3 prévoit est appelé, et rien
d'autre ne l'est. Aucun point d'entrée n'est en attente.

### `GET /api/v6/map/stops`, pour son vrai usage

Les paramètres sont assemblés par `core/query/StopsQueryBuilder.kt`.

| Paramètre | Valeur envoyée | Pourquoi |
|---|---|---|
| `min`, `max` | `latitude,longitude`, coin sud-ouest puis coin nord-est | l'emprise de l'écran **élargie de 30 %** (§ 5.7, règle 3) |
| `grouped` | `true` | le serveur regroupe lui-même les quais d'une même gare, ce qui divise d'autant le nombre de points à dessiner |
| `modes` | les modes du palier de zoom courant, séparés par des virgules | zoom 11 → 13 : les modes ferrés lourds ; à partir de 13 : tout le reste |

Le paramètre `modes` est **omis** quand aucun mode n'est retenu : une valeur vide n'a pas le même
sens qu'une absence — côté serveur, ne pas envoyer `modes` veut dire « tous les modes ».

Forme des `modes` vérifiée sur `api.transitous.org` : la même emprise du centre de Paris rend onze
arrêts sans filtre et cinq avec les modes ferrés lourds. Les deux captures sont les fixtures
`map_stops_paris.json` et `map_stops_rail_only.json`.

Le corps est un tableau de `Place`. Un `Place` **sans `stopId`** est écarté par le mapping : sans
identifiant, le marqueur ne mènerait ni à l'infobulle ni aux prochains départs. Pour le champ
`modes` d'un arrêt, voir le piège n° 8.

### `GET /api/v6/stop`

`stopId`, et rien d'autre. Le corps rend `place` et `routes` ; chaque `Route` porte `routeShortName`,
`routeLongName`, `mode`, `agencyName`, et les couleurs `routeColor` / `routeTextColor` quand le
réseau les publie. Le serveur les rend déjà dédoublonnées — trente lignes distinctes à Châtelet —
mais dans un ordre alphabétique où le bus 21 précède le métro 4 : `StopMapper` les réordonne par
mode puis par numéro. Fixture : `stop_chatelet.json`.

### `GET /api/v6/stoptimes`, les prochains départs

Les paramètres sont assemblés par `core/query/StopTimesQueryBuilder.kt`, comme ceux de `plan` et de
`map/stops`. Les noms viennent de l'opération `stoptimes` de l'OpenAPI, qui écrit `mode` **au
singulier** là où `map/stops` écrit `modes` : recopier le nom de l'opération voisine fait
silencieusement disparaître le filtre.

| Paramètre | Valeur envoyée | Pourquoi |
|---|---|---|
| `stopId` | l'identifiant rendu par le serveur | jamais des coordonnées, voir le piège n° 4 |
| `n` | le nombre d'événements voulus | c'est un **minimum**, pas un maximum : le serveur complète la dernière minute atteinte et rend souvent une dizaine d'entrées de plus |
| `withAlerts` | `true`, toujours | § 5.4 le demande ; c'est déjà le défaut serveur, mais un défaut n'est pas un engagement |
| `time` | l'instant demandé | **omis** quand `pageCursor` est fourni : une page suivante s'ancre sur son seul curseur |
| `direction` | `LATER`, ou `EARLIER` sur une recherche d'arrivées | voir le piège n° 9 — **omis** quand `pageCursor` est fourni |
| `arriveBy` | `true` pour lister des arrivées | absent sinon |
| `mode` | les feuilles de la puce de filtre choisie | **omis** quand aucune puce n'est active : un `mode` vide n'a pas le même sens qu'une absence |
| `pageCursor` | pagination | `previousPageCursor` / `nextPageCursor` d'une page déjà obtenue |

Les valeurs de `mode` sont des **feuilles**, jamais un parapluie : c'est `DepartureModeFilter`, dans
`:core`, qui tient les deux listes — celle qu'on envoie et celle à laquelle on compare un mode reçu
(piège n° 8). Fixtures : `stoptimes_hamburg.json`, `stoptimes_rail_only.json` et
`stoptimes_alerts.json`.

**Les perturbations arrivent dans le `place` de chaque `StopTime`**, et non à la racine de l'entrée.
C'est ce que dit à sa façon la description de `withAlerts` (« alerts are omitted in the metadata of
place »). Un DTO qui ne lirait `alerts` qu'au niveau de l'entrée n'en trouverait jamais aucune : ce
sont `StopTimePlaceDto` et son champ `alerts` qui les portent.

### `GET /api/v6/trip`, la desserte d'une course

Deux paramètres, et rien d'autre : `tripId`, et `detailedLegs` que l'application met délibérément à
`false` — l'écran liste des arrêts, il ne trace rien, et la géométrie divise par sept la taille de la
réponse (55 ko contre 8 ko sur un ICE Hambourg → Nuremberg, fixture `trip_ice91.json`).

**Le corps n'est pas une réponse de recherche : c'est un `Itinerary` seul**, sans enveloppe, sans
`itineraries` ni `direct`. Le mapping réemployé est donc exactement celui des trajets
(`PlanItineraryDto.toDomain`) ; écrire un second mapping pour le même schéma serait la garantie que
les deux divergent au premier changement d'API.

Conséquence pour l'écran : les arrêts de la course sont **répartis** entre le `from`, les
`intermediateStops` et le `to` de la portion, et `intermediateStops` exclut les deux extrémités —
onze arrêts desservis pour neuf arrêts intermédiaires sur l'ICE 91. Le recollement est une règle de
`:core` (`Journey.calls`), pas un morceau de composable.

Le schéma `Place` porte lui aussi un tableau `alerts` : `/api/v6/trip` rend donc des perturbations
**par arrêt**, en plus de celles de la course. Ce sont deux niveaux distincts, et les confondre fait
disparaître une perturbation qui ne concerne qu'un quai.

### Politique de transport, commune à tous les appels

Tenue en un seul endroit, `data/net/MotisTransport.kt` :

- en-tête `User-Agent` sur **chaque** requête, au format `Escale/<versionName> (+https://github.com/mgdx/escale)`,
  la version venant de `BuildConfig` (§ 4.2, obligation de la politique d'usage de Transitous) ;
- `ignoreUnknownKeys = true` : MOTIS ajoute des champs sans préavis, et une réponse enrichie ne doit
  jamais faire échouer un décodage ;
- expiration 30 s, **une seule** reprise, **aucune reprise sur 4xx** (§ 7.8) ;
- **aucune journalisation** de corps de requête ni de réponse, même en débogage (§ 8, § 11) : le
  greffon `Logging` de Ktor n'est délibérément pas installé ;
- cache disque de 24 h **sur le seul géocodage** (§ 7.5), porté par un client OkHttp distinct. Le
  reste n'est jamais écrit sur disque : les réponses `plan` (`PlanCache`), les arrêts de la carte et
  les disponibilités de libre-service sont mis en cache **en mémoire seulement**, et pour des durées
  très différentes — dix minutes pour un arrêt, **soixante secondes** pour une disponibilité de
  libre-service, qui n'est pas une donnée un peu ancienne au-delà mais une donnée fausse (§ 5.7,
  règle 4). Un relevé demandé explicitement par l'usager contourne le cache.

---

## 2. `GET /api/v6/plan`

Les paramètres sont assemblés par `core/query/PlanQueryBuilder.kt`, en Kotlin pur, et posés tels
quels sur la requête par `:data`. Tous les noms ci-dessous ont été vérifiés un à un dans
`docs/motis-openapi.yaml`, dans l'opération `plan` et non dans une opération voisine.

### Toujours envoyés

| Paramètre | Valeur envoyée | Pourquoi |
|---|---|---|
| `fromPlace`, `toPlace` | un `stopId` si le lieu est un arrêt, sinon `lat,lon` | voir le piège n° 4 |
| `transitModes` | `TRANSIT` sur l'onglet Transport, **vide** sur les autres | vide = « aucune correspondance calculée » |
| `directModes` | **vide** sur l'onglet Transport, `CAR` / `BIKE[,RENTAL]` / `WALK` sur les autres | voir le piège n° 3 |
| `detailedLegs` | `false` sur la liste, `true` à l'ouverture d'un trajet | § 7.6, la géométrie coûte cher |
| `maxMatchingDistance` | `1000` (mètres) | le défaut de 250 m n'accroche à aucune rue un point posé au milieu d'un parc, sur un quai ou sur une plage : la recherche échoue sans explication. Envoyé sur les quatre onglets, une adresse géocodée au fond d'un lotissement ayant le même problème. Plafonné par `max_max_matching_distance` |

### Envoyés selon le cas

| Paramètre | Quand | Note |
|---|---|---|
| `time` | heure choisie explicitement | ISO-8601 UTC, secondes toujours écrites ; **absent** si « maintenant », pour que le serveur prenne son heure et non celle de la saisie |
| `arriveBy` | « arriver avant » | `true` |
| `preTransitModes`, `postTransitModes` | onglet Transport | `WALK,RENTAL` : rabattement à pied ou en libre-service |
| `maxDirectTime` | onglets sans transport en commun | voir le piège n° 2 |
| `maxPreTransitTime`, `maxPostTransitTime` | onglet Transport | 1800 s : le défaut serveur de 900 s vide l'onglet hors ville dense, dès que le premier arrêt est à plus d'un quart d'heure de marche. Plafonné par `street_routing_max_prepost_transit_seconds` |
| `detailedTransfers` | écran de détail seulement | hérite de `detailedLegs` quand il est absent — c'est pourquoi il n'est envoyé que là où on le veut explicitement à `true` (§ 5.3) |
| `pageCursor` | pagination | `previousPageCursor` / `nextPageCursor` d'une page déjà obtenue, **le reste de la requête à l'identique** |
| `pedestrianSpeed`, `pedestrianProfile` | tous les onglets sauf Voiture | `pedestrianProfile=WHEELCHAIR` est le réglage d'accessibilité du § 9 |
| `cyclingSpeed`, `elevationCosts` | onglets Vélo et Transport | l'OpenAPI ne fait jouer `elevationCosts` que sur le mode `BIKE` |
| `additionalTransferTime` | onglet Transport | **en minutes**, pas en secondes (`docs/motis-openapi.yaml`, « Additional transfer time reserved for each transfer in minutes ») |
| `maxTransfers` | onglet Transport | absent = valeur serveur, volontairement très haute |
| `requireBikeTransport` | onglet Transport | vélo embarqué dans les véhicules |
| `language` | tous les onglets, dès que la langue de l'interface est connue | tags OpenStreetMap / GTFS (BCP-47 / ISO 639-1) : sans lui, noms d'arrêts et `headsign` arrivent dans la langue par défaut du flux. Renseigné par `SearchSession` avec `Locale.getDefault().language`, comme le fait déjà `/api/v1/geocode` |
| `directRentalFormFactors`, `preTransitRentalFormFactors`, `postTransitRentalFormFactors` | l'usager a restreint les types de véhicules partagés | assemblés par `core/query/RentalFormFactorQuery.kt` — voir le piège n° 6, un filtre vide n'y veut pas dire « aucun véhicule » |

Une préférence laissée à sa valeur par défaut **n'est pas envoyée** : la valeur par défaut du
domaine est celle du serveur, et une requête plus courte est une requête moins fragile.

### `GET /api/v6/refresh-itinerary`

Trois paramètres, et rien d'autre : `itineraryId`, `detailedLegs`, `detailedTransfers`. Le point
d'entrée reconstruit le trajet à partir de son seul identifiant — ni lieux, ni heure, ni modes n'ont
à être renvoyés.

---

## 3. Les pièges connus

Tous ont été rencontrés pour de vrai sur ce projet. Ils sont classés du plus coûteux au moins
coûteux à diagnostiquer.

### 1. Les polylignes n'ont pas la même précision selon la version du point d'entrée

**Précision 6 sur `/api/v2/*` et au-delà — donc sur tout le `v6` — contre 7 sur `/api/v1/*`.**

Conséquence pratique : décodée à la mauvaise précision, une position de Berlin tombe au large de
l'Afrique. Le facteur dix déplace la virgule, la latitude passe de 52,52 à 5,25 et la longitude de
13,37 à 1,34 — un point du golfe de Guinée. Aucune erreur, aucune exception : un tracé simplement
faux, à des milliers de kilomètres.

Remède : ne **jamais** supposer la précision. Le schéma `EncodedPolyline` porte un champ `precision`
(« The precision of the returned polyline (7 for /v1, 6 for /v2) ») ; c'est lui que
`data/mapper/PlanPartMapper.kt` lit et transmet à `PolylineDecoder.decode`. Le décodeur prend la
précision en paramètre et est testé aux deux valeurs (`SPEC.md` § 4.3 et § 10).

À noter au passage, toujours d'après le schéma : en précision 7, les coordonnées de longitude
supérieure à 107,37° débordent. Hors sujet pour un serveur européen, pas pour Transitous, qui a une
couverture mondiale.

### 2. `maxDirectTime` vaut 1800 s par défaut et coupe silencieusement les trajets longs

`docs/motis-openapi.yaml` : « Optional. Default is 30min which is `1800`. Maximum time in seconds
for direct connections. »

Conséquence pratique : sans ce paramètre, **tout trajet direct de plus de trente minutes disparaît
sans le moindre message**. Un trajet en voiture d'une heure ou un trajet à vélo de quarante minutes
rendent une réponse parfaitement valide dont le tableau `direct` est vide. L'application n'a aucun
moyen de distinguer « pas de trajet » de « trajet tronqué par un défaut de paramétrage ».

La fixture `data/src/test/resources/fixtures/plan_empty.json` en est la trace : la même requête,
entre deux points de Berlin distants de trois kilomètres, rend cinq résultats avec le paramètre et
zéro sans lui.

Remède : `PlanQueryBuilder` envoie un plafond explicite sur chaque onglet sans transport en commun —
4 h en voiture, 3 h à vélo, 2 h à pied. Le serveur reste libre de rabaisser ces valeurs, il a pour
cela la variable de configuration `street_routing_max_direct_seconds`.

### 3. Mélanger `transitModes` et `directModes` fait disparaître des résultats

L'OpenAPI le dit sans détour, sur `directModes` : « Transit connections that are slower than the
fastest direct connection will not show up. This is being used as a cut-off during transit routing
to speed up the search. To prevent this, it's possible to send two separate requests (one with only
`transitModes` and one with only `directModes`). »

Conséquence pratique : une requête unique demandant à la fois le transport en commun et la voiture
renvoie la voiture et **presque rien** en transport en commun, puisque le trajet en voiture sert de
plafond de durée pendant la recherche. L'usager conclut que sa ville n'est pas desservie.

Remède : **une requête par onglet** (`SPEC.md` § 5.2). L'onglet Transport envoie `directModes=`
vide, les autres envoient `transitModes=` vide. La valeur vide part telle quelle sur la requête :
c'est elle qui porte la garantie, l'omettre laisserait le serveur appliquer son défaut (`WALK` pour
`directModes`, `TRANSIT` pour `transitModes`).

### 4. Une requête par coordonnées peut rendre zéro résultat là où un `stopId` en rend cinq

Constaté sur `api.transitous.org`, autour d'une gare : la même recherche exprimée par la position
affichée de la gare rendait zéro trajet, et cinq quand elle était exprimée par l'identifiant de
l'arrêt.

Conséquence pratique : quand l'autocomplétion rend un lieu de type `STOP`, c'est son `id` qui doit
partir dans `fromPlace` / `toPlace`, **jamais** sa position. Passer les coordonnées d'une gare oblige
le serveur à un appariement approximatif sur le réseau de rues, dont le résultat est au mieux moins
bon, au pire vide.

Remède : `PlanQueryBuilder.place()` applique la règle, et `docs/architecture.md` § 11.3 en fait un
arbitrage contraignant : l'interface ne doit pas la contourner en reconstruisant un point à partir
des coordonnées affichées.

### 5. Le serveur ne sert aucune feuille de style de carte

`GET /tiles/style.json` rend **501**, `GET /style.json` rend **404**, alors que les tuiles
(`/tiles/{z}/{x}/{y}.mvt`), les glyphes (`/glyphs/{fontstack}/{range}.pbf`) et les sprites
(`/sprite_sdf`) répondent normalement.

Conséquence pratique : il n'y a rien à télécharger pour styler la carte. La feuille de style
MapLibre est **embarquée dans l'application**, déclinée en clair et en sombre, et ne pointe vers le
serveur que pour les trois ressources ci-dessus (`SPEC.md` § 5.7).

Corollaire : un serveur qui ne sert pas de tuiles reste parfaitement utilisable. L'étape 3 du test
de connexion rend `false`, pas une erreur ; la carte affiche alors un fond neutre avec les seules
polylignes du trajet. Aucun repli sur un fournisseur tiers.

### 6. Les paramètres de types de véhicules en libre-service sont « expérimental »

`directRentalFormFactors`, `preTransitRentalFormFactors` et `postTransitRentalFormFactors` sont
marqués *Experimental* par l'OpenAPI : leur nom comme leur comportement peuvent changer **sans
changement de version d'API**. Un serveur mis à jour peut donc cesser de les comprendre du jour au
lendemain, sans que `/api/v6/...` cesse pour autant de répondre.

Remède : ces trois chaînes ne sont écrites qu'à un seul endroit,
`core/query/RentalFormFactorQuery.kt`, tests compris — les tests passent par les constantes, pour
rester justes après un renommage côté serveur. Le jour où MOTIS les renomme, il y a un fichier à
corriger.

Attention aussi à leur sémantique : un filtre **vide** signifie « aucun filtre », c'est-à-dire *tous*
les véhicules, et non « aucun véhicule ». Quand l'usager exclut tous les types de véhicules de
l'onglet Vélo, il ne faut donc pas envoyer un filtre vide mais retirer `RENTAL` de `directModes`.

### 7. Un 404 sur un point d'entrée `v6` veut dire « serveur trop ancien », pas « ressource absente »

`v6` suppose MOTIS ≥ 2.9. Un serveur plus ancien ne connaît pas le chemin et répond 404 — le même
code qu'une ressource introuvable, alors que le problème est la version du serveur.

Conséquence pratique : annoncer « introuvable » à quelqu'un dont le serveur auto-hébergé est
simplement à mettre à jour lui fait chercher le problème du mauvais côté pendant longtemps.

Remède : `data/net/HttpFailures.kt` transforme un 404 en `EscaleError.ApiVersionTooOld` **si et
seulement si** le chemin commence par `/api/v6/`. Un 404 sur `/api/v1/geocode` reste un
`ServerUnreachable` ordinaire. L'écran propose alors de revenir au serveur par défaut. Pas de repli
automatique vers `v5` ou `v3` en v1 (`SPEC.md` § 4.3).

### 8. Certaines valeurs de `Mode` sont des **parapluies**, et les confondre avec des feuilles fausse tout filtrage côté client

`docs/motis-openapi.yaml`, schéma `Mode` (ligne 3838 et suivantes), définit deux valeurs qui n'en
sont pas :

```
- `TRANSIT`: translates to `TRAM,FERRY,AIRPLANE,BUS,COACH,RAIL,ODM,RIDE_SHARING,FUNICULAR,AERIAL_LIFT,OTHER`
- `RAIL`:    translates to `HIGHSPEED_RAIL,LONG_DISTANCE,NIGHT_RAIL,REGIONAL_RAIL,SUBURBAN,SUBWAY`
```

**Côté requête, il n'y a aucun piège** : le serveur développe le parapluie lui-même. Envoyer
`modes=RAIL,…` ou envoyer les six feuilles une par une rend exactement le même jeu d'arrêts, ce qui
a été vérifié sur `api.transitous.org` — mêmes cinq arrêts, mêmes `stopId`.

**Le piège est côté client, quand un parapluie sert de filtre d'affichage.** Le tableau de
`SPEC.md` § 5.7 écrit, pour le palier 11 → 13 : « `RAIL`, `HIGHSPEED_RAIL`, `LONG_DISTANCE`,
`SUBURBAN`, `SUBWAY` ». Recopié littéralement dans un `Set<TransitMode>` puis comparé aux modes que
le serveur **renvoie** sur chaque arrêt, cet ensemble laisse tomber `NIGHT_RAIL` et `REGIONAL_RAIL`,
qui sont pourtant dans `RAIL`.

Conséquence pratique : la requête du palier 11 rend bien « Châtelet - Les Halles » — la plus grande
gare souterraine d'Europe —, MOTIS l'annonce avec le seul mode `REGIONAL_RAIL`, et le filtre
d'affichage la faisait disparaître jusqu'au zoom 13. Aucune erreur, aucune requête en trop : une
gare simplement invisible. La fixture `map_stops_rail_only.json` en est la trace : elle est obtenue
avec `modes=RAIL,HIGHSPEED_RAIL,LONG_DISTANCE,SUBURBAN,SUBWAY` et contient un arrêt dont le seul
mode est `REGIONAL_RAIL`.

Remède : **développer le parapluie avant de s'en servir comme filtre**. `TransitMode.HEAVY_RAIL_MODES`
énumère les six feuilles de `RAIL`, plus `RAIL` lui-même — rien n'interdit à un serveur de le
renvoyer tel quel —, et sa documentation dit pourquoi la liste est plus longue que celle de la spec.
Un `Mode` renvoyé par le serveur se compare toujours à des feuilles ; un `Mode` **envoyé** au serveur
peut être un parapluie, et c'est même ce que fait `PlanQueryBuilder` avec `transitModes=TRANSIT`.

Le second endroit où le piège se joue est `core/model/DepartureFilter.kt`, arrivé depuis avec les
prochains départs : chaque puce de filtre y porte **deux** ensembles, `requestModes` — des feuilles,
envoyées au serveur — et `matchedModes` — les mêmes feuilles plus le parapluie, pour comparer un
mode reçu. Envoyer `RAIL` pour la puce « train » ferait remonter le métro avec, ce qui a été vérifié
à Hamburg Hbf ; ne comparer qu'à `RAIL` raterait `REGIONAL_RAIL`, c'est-à-dire la moitié des trains
d'une gare régionale.

Vérifié pour le reste du projet : partout ailleurs où `RAIL` et `TRANSIT` apparaissent
(`ResultsFormatting`, `SearchLabels`, `JourneyTrace`, `StopIcon`), ils servent à choisir une icône,
une couleur ou un libellé de repli pour une valeur reçue — jamais à filtrer. Aucune autre confusion
feuille / parapluie dans le dépôt à ce jour.

### 9. La documentation de `direction` sur `/api/v6/stoptimes` inverse `EARLIER` et `LATER`

`docs/motis-openapi.yaml`, opération `stoptimes`, paramètre `direction` :

> The response will contain the next `n` arrivals / departures in case `EARLIER` is selected and the
> previous `n` arrivals / departures if `LATER` is selected.

**Cette phrase est fausse.** Vérifié sur `api.transitous.org`, à Hambourg, autour de 08:00 :
`direction=EARLIER` rend 07:58 → 08:00, `direction=LATER` rend 08:00 → 08:01. `EARLIER` rend donc
les événements **avant** `time`, `LATER` ceux d'**après** — l'inverse de ce que la prose annonce.

L'OpenAPI se contredit d'ailleurs lui-même trois lignes plus haut, et c'est cette moitié-là qui a
raison : « Default is `LATER` for `arriveBy=false`, `EARLIER` for `arriveBy=true` ». Un tableau de
départs par défaut regarde bien vers l'avenir. Les curseurs de pagination lèvent le dernier doute :
ils s'appellent littéralement `EARLIER|…` et `LATER|…`.

Conséquence pratique : suivre la prose fait afficher **le passé** sur un tableau de départs. Aucune
erreur, aucun code HTTP anormal — juste un écran qui annonce des trains partis il y a dix minutes,
et un « page suivante » qui remonte le temps. Le défaut est d'autant plus vicieux qu'à une gare
fréquentée les deux réponses se ressemblent : il faut lire les heures pour s'en apercevoir.

Remède : les deux constantes sont écrites **une seule fois**, dans
`core/query/StopTimesQueryBuilder.kt`, avec ce constat de terrain en commentaire juste au-dessus.
**Ne « corrigez » pas ce code d'après `docs/motis-openapi.yaml`** : la documentation est le côté
faux, et l'échange se fait sans que rien ne casse — c'est exactement le genre de correction qu'un
relecteur pressé fait de bonne foi. Si le doute revient, la vérification tient en une requête :
`?stopId=…&time=…&direction=EARLIER` doit rendre des heures **antérieures** à `time`.

### 10. `min` et `max` de `/api/v1/rentals` ne sont pas les coins que la prose annonce

L'OpenAPI décrit `min` comme le coin « lower right » et `max` comme le coin « upper left », sur
`rentals` comme sur `map/stops`. Le comportement observé sur `api.transitous.org` est celui de la
convention habituelle : `min` est le coin **sud-ouest**, `max` le coin **nord-est**.

Conséquence pratique : croire la prose fait envoyer une emprise inversée, à laquelle le serveur
répond une liste vide — donc « aucune station dans ce quartier », sans erreur ni indice. C'est la
même convention que `map/stops`, et `RentalsQueryBuilder` la documente à l'endroit où elle
s'applique.

---

## 4. Deux comportements à connaître, qui ne sont pas des pièges

- **`GET /api/v1/health` répond 400 avec le même corps** tant que le serveur n'a pas parcouru un
  cycle complet de ses flux temps réel. Ce n'est pas une erreur : Escale le traite en succès, avec
  `fullyStarted = false`, et l'écran de réglage le signale. Refuser un serveur là-dessus rejetterait
  une instance qui vient de démarrer et fonctionnera dans deux minutes.
- **`GET /api/v1/reverse-geocode` ne prend pas de paramètre `language`.** Il ne connaît que `place`,
  `type` et `numResults` — contrairement à `/api/v1/geocode`, qui accepte `language`. Le libellé
  rendu est celui d'OpenStreetMap.

---

## 5. Erreurs

`SPEC.md` § 8 impose de distinguer les cas à l'écran. La traduction est faite une seule fois, dans
`data/net/HttpFailures.kt`, vers `core/result/EscaleError.kt` :

| Ce qui arrive | `EscaleError` produite | Ce que l'usager lit |
|---|---|---|
| `UnknownHostException` | `HostNotFound` | « Serveur introuvable. Vérifiez l'adresse ou votre connexion. » — le cas est ambigu, le libellé nomme les deux hypothèses |
| `NoRouteToHostException` | `NoNetwork` | « Pas de connexion réseau. » |
| `ConnectException`, autre `IOException` | `ServerUnreachable(null)` | « Le serveur ne répond pas. » — l'hôte existe, il refuse la connexion |
| expiration | `Timeout` | « Le serveur a mis trop de temps à répondre. » |
| `SerializationException` | `Unknown` | « Une erreur est survenue. » — le nom de la classe seul est conservé, jamais son message |
| 400 / 422 | `BadRequest` | le champ `error` du corps quand il est exploitable, « Une erreur est survenue. » sinon |
| 404 sur `/api/v6/*` | `ApiVersionTooOld` | « Ce serveur utilise une version de MOTIS trop ancienne pour Escale. » |
| 4xx / 5xx | `ServerUnreachable(code)` | « Le serveur ne répond pas. » |

`EscaleError.Superseded` ne figure pas dans ce tableau : elle ne vient pas du réseau. C'est le cas
d'une requête supplantée par une plus récente, que l'appelant ignore au lieu de l'afficher.

**Aucun message d'exception n'est repris tel quel** : il pourrait contenir l'URL appelée, donc les
coordonnées de l'usager. Seul le nom de la classe est conservé, et `EscaleError` ne transporte
jamais de coordonnée, d'adresse ni d'URL de requête (`SPEC.md` § 8 et § 11).
