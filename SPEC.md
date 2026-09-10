# SPEC — Escale, client Android MOTIS

> Document de référence du projet. Il fait autorité : en cas de contradiction avec le code,
> c'est la spec qui a raison, ou bien la spec doit être amendée explicitement.

---

## 1. Objet

**Escale** est une application Android, cliente d'un serveur
[MOTIS](https://github.com/motis-project/motis) 2.x.

L'utilisateur saisit un point de départ et une destination ; l'application calcule et présente
les trajets possibles **par catégorie** (transport en commun, voiture, vélo, à pied, libre-service),
et pour chaque trajet le **détail portion par portion** : lignes empruntées, correspondances,
nombre d'arrêts intermédiaires, distances, durées, retards en temps réel, perturbations,
stations de vélos en libre-service et nombre de véhicules disponibles.

Toute l'intelligence de calcul est côté serveur. L'application est un client : elle interroge
l'API, met en forme, met en cache le strict nécessaire et affiche. Elle n'implémente **aucun**
algorithme de routage.

### 1.2 Non-objectifs de la v1

- Achat, réservation ou affichage de titres de transport (`withFares` est expérimental côté MOTIS).
- Guidage pas-à-pas type GPS avec suivi de position continu.
- Transport à la demande (`ODM`, `FLEX`, `RIDE_SHARING`) : les résultats renvoyés par le serveur
  sont affichés s'ils arrivent, mais aucune interface dédiée n'est développée.
- Fonctionnement hors ligne du calcul d'itinéraire (impossible : le calcul est serveur).
- Compte utilisateur, synchronisation, sauvegarde distante.

---

### 1.1 Identité

| | |
|---|---|
| Nom affiché | **Escale** |
| `applicationId` | `io.github.mgdx.escale` — figé, il ne pourra plus changer une fois l'application publiée sur F-Droid |
| Dépôt | `https://github.com/mgdx/escale` |
| Accroche (en) | *Get there, any way you like* |
| Accroche (fr) | *Aller là-bas, comme vous voulez* |

Le nom joue sur les deux sens : l'escale du voyageur, et la correspondance qui ponctue un trajet.
Il se prononce sans difficulté en anglais et tient sous une icône.

Icône retenue : un **point de correspondance** — un anneau posé entre deux segments de trajet,
teal avant l'escale, corail après, les segments s'arrêtant au bord de l'anneau. Même vocabulaire
graphique que la frise de trajet de l'écran de résultats. Fournie en vectoriel, avec calque
`monochrome` pour les icônes thématiques d'Android 13+, tout le motif tenant dans le cercle de
sécurité de 66 dp du gabarit adaptatif. Couleurs : `#1D9E75` (accent de l'application),
`#D85A30`, anneau `#0F6E56`, fond `#E1F5EE`. Fichiers et destinations : voir `docs/icone.md`.

Deux applications homonymes existent sur le Play Store (« Escales, Itinéraires culturels » et
« Prochaine Escale »), aucune sur F-Droid, aucune dans le champ du calcul d'itinéraire. Rien
n'empêche l'usage du nom ; seul l'`applicationId` doit être unique.

---

## 2. Principes non négociables

| Principe | Traduction concrète |
|---|---|
| Logiciel libre | Licence **GPLv3**, dépôt public, `LICENSE` à la racine |
| Diffusion | **F-Droid** (dépôt principal), donc aucune dépendance propriétaire, aucun binaire pré-compilé, build reproductible depuis les sources |
| Vie privée | **Aucune** télémétrie, aucun traqueur, aucun SDK publicitaire, aucun service Google Play. Aucune donnée ne quitte l'appareil sauf les requêtes vers le serveur MOTIS configuré |
| Légèreté | APK visé < 15 Mo **par APK d'architecture**, et non pour un APK universel : MapLibre apporte à lui seul une dizaine de mégaoctets de code natif incompressible, par architecture. L'objectif se vérifie sur l'APK de publication minifié, jamais sur celui de débogage. Pas de dépendance lourde inutile, pas de WebView |
| Fluidité | Démarrage à froid < 1,5 s jusqu'à la carte, 60 images/s en déplacement, rien de plus de 16 ms sur le fil principal. Critères vérifiés, pas seulement souhaités (§ 5.7) |
| Localisation | Aucune chaîne codée en dur : tout dans `res/values/strings.xml`, pluriels via `<plurals>`, formats via paramètres positionnels |
| Qualité | Aucun avertissement toléré sur `test`, `lint`, `ktlintCheck` |

**Langue :** interface en anglais par défaut (`values/`), traduction française fournie (`values-fr/`).
Les autres langues sont laissées aux contributeurs.

---

## 3. Pile technique

- **Kotlin**, **Jetpack Compose**, **Material 3** (couleurs dynamiques Android 12+, thème clair/sombre suivant le système).
- `minSdk 26` (Android 8.0), `compileSdk`/`targetSdk` : la version stable la plus récente. JDK 17.
- Réseau : **Ktor client (OkHttp engine)** ou **Retrofit + OkHttp**, au choix de l'agent, avec
  **kotlinx.serialization** pour le JSON. `ignoreUnknownKeys = true` obligatoire : l'API MOTIS
  ajoute des champs sans préavis.
- Carte : **MapLibre GL Android** (licence BSD, compatible F-Droid). Pas de Google Maps, pas de Mapbox.
- Persistance : **Room** (favoris, historique, serveurs) et **DataStore Preferences** (réglages).
- Navigation : Navigation Compose.
- **Injection de dépendances : manuelle. Hilt, Dagger, Koin et tout autre conteneur sont exclus.**
  Un unique `AppContainer`, construit dans la classe `Application`, instancie et détient les objets
  partagés : client HTTP, base Room, `DataStore`, dépôts, configuration du serveur. Les `ViewModel`
  les reçoivent par leur constructeur via une `ViewModelProvider.Factory` écrite à la main.
  Motif : le graphe de dépendances tient en une centaine de lignes lisibles d'un bloc ; un
  générateur de code ajouterait une étape de compilation, de la configuration Gradle et des erreurs
  opaques pour un gain nul à cette échelle. Cette règle prime sur toute habitude ou tout modèle de
  code par défaut.

### Découpage en modules Gradle

```
:core    Kotlin pur, AUCUN import android.* — modèles de domaine, mapping, calculs d'affichage,
         décodage des polylignes, formatage des durées. Testable en JVM.
:data    Client HTTP, DTO de l'API MOTIS, cache, DAO Room.
:app     Interface Compose, navigation, thème, ressources.
```

Toute logique métier testable vit dans `:core`. Une règle qui n'est pas testable en JVM est mal placée.

---

## 4. Le serveur MOTIS

### 4.1 Configuration

- Serveur par défaut : **`https://api.transitous.org`** (instance publique communautaire, couverture mondiale).
- L'utilisateur peut saisir l'URL de **son propre serveur** dans les réglages : instance
  auto-hébergée, ou serveur local en `http://` sur le réseau local. Le trafic en clair ne peut pas
  être restreint aux seuls hôtes saisis par l'utilisateur : depuis Android 7, la
  `network_security_config` est figée à la compilation, et un hôte saisi à l'exécution ne peut pas y
  être ajouté. Le dispositif retenu est donc **permissif au niveau de la plateforme, strict au niveau
  applicatif** : `cleartextTrafficPermitted="true"` sur la `base-config`, avec un commentaire en tête
  du fichier qui l'explique, et la restriction portée par le code — l'écran « Serveur MOTIS » refuse
  toute URL `http://` tant que l'utilisateur n'a pas confirmé un avertissement explicite. Ce
  consentement est demandé une fois par hôte et mémorisé ; aucune requête en clair, test de connexion
  compris, ne part sans lui.
- Un bouton **« Tester la connexion »** appelle `GET /api/v1/health` et affiche le résultat.
- Changer de serveur vide le cache des résultats mais conserve favoris et historique.
- Le comportement complet de l'écran de réglage du serveur est décrit au § 5.6.1 : normalisation de
  l'URL, test en trois étapes, mémorisation des serveurs déjà utilisés, retour au serveur par défaut.

### 4.2 Politique d'usage de Transitous — contraintes de conception

L'instance publique est fournie par des bénévoles, avec une politique d'usage à respecter :

1. **En-tête `User-Agent` obligatoire sur chaque requête**, contenant le nom de l'application,
   sa version et un moyen de contact. Format retenu :
   `Escale/1.0.0 (+https://github.com/mgdx/escale)`, la version étant lue depuis `BuildConfig`
   et jamais codée en dur
2. **Attribution visible** : écran « À propos » avec un lien vers `https://transitous.org/sources/`
   et vers `https://www.openstreetmap.org/copyright`. Un bouton d'attribution est également
   accessible depuis la carte.
3. **Usage non commercial**, code publié en open source.
4. **Sobriété** : le calcul d'itinéraire est un point d'API coûteux. Les règles du § 7 en découlent
   et ne sont pas négociables.
5. Avant toute publication grand public (F-Droid), prendre contact avec l'équipe Transitous
   (salon Matrix) — à faire par le mainteneur humain, hors périmètre de l'agent.

### 4.3 Versions d'API

MOTIS versionne ses points d'entrée. L'application cible :

| Usage | Point d'entrée |
|---|---|
| Calcul d'itinéraire | `GET /api/v6/plan` |
| Rafraîchir un itinéraire | `GET /api/v6/refresh-itinerary` |
| Détail d'une course | `GET /api/v6/trip` |
| Prochains départs | `GET /api/v6/stoptimes` |
| Détail d'un arrêt | `GET /api/v6/stop` |
| Autocomplétion | `GET /api/v1/geocode` |
| Géocodage inverse | `GET /api/v1/reverse-geocode` |
| Arrêts sur la carte | `GET /api/v6/map/stops` |
| Stations et véhicules en libre-service | `GET /api/v1/rentals` |
| Santé du serveur | `GET /api/v1/health` |

`v6` suppose MOTIS ≥ 2.9. Si un appel `v6` répond **404**, l'application ne plante pas : elle affiche
un message clair (« ce serveur utilise une version de MOTIS trop ancienne ») et propose de revenir
au serveur par défaut. Pas de repli automatique vers `v5`/`v3` en v1.

**Piège à ne pas rater :** les polylignes sont encodées en Google Polyline avec une **précision 6**
sur `/api/v2/*` et au-delà (donc sur `v6`), contre 7 sur `/api/v1/*`. Le décodeur doit prendre la
précision en paramètre, et un test unitaire doit couvrir les deux.

La spécification OpenAPI complète fait foi :
`https://raw.githubusercontent.com/motis-project/motis/master/openapi.yaml`.
L'agent la télécharge et s'y réfère pour tout champ non décrit ici, plutôt que de deviner.

---

## 5. Écrans

### 5.1 Écran d'accueil — la carte

L'écran d'ouverture **est la carte**, en plein écran, du bord haut au bord bas. Tout le reste flotte
par-dessus.

```
┌─────────────────────────────────┐
│ ╭─────────────────────────────╮ │  carte de recherche flottante
│ │ ○ Départ            ⇅       │ │  (élévation Material 3, coins arrondis,
│ │ ◉ Arrivée                   │ │   marges de 12 dp, sous la barre d'état)
│ │ 🕑 Maintenant               │ │
│ ╰─────────────────────────────╯ │
│                                 │
│         CARTE PLEIN ÉCRAN       │
│                                 │
│                          ╭───╮  │
│                          │ ⌖ │  │  bouton de position, bas à droite,
│                          ╰───╯  │  56 dp, au-dessus de la barre de navigation
└─────────────────────────────────┘
```

**Carte de recherche (en haut)**
- Champ **Départ**, champ **Arrivée**, bouton d'inversion à droite.
- Troisième ligne compacte : l'heure de départ, **« Maintenant » par défaut**. Un appui ouvre le
  sélecteur : « Partir maintenant », « Partir à… », « Arriver avant… » (`arriveBy`), avec date et
  heure. Le libellé de la ligne reflète toujours le choix en cours (« Départ jeu. 14:30 »).
- Sous les champs, quand ils sont vides : puces d'accès rapide **Domicile**, **Travail**,
  puis les dernières recherches (§ 5.5). Une puce absente n'est simplement pas affichée.
- Le champ actif passe en plein écran avec la liste d'autocomplétion, pour ne pas superposer
  un clavier et une liste au-dessus de la carte.
- Dès que Départ et Arrivée sont renseignés, la recherche se lance : pas de bouton « Rechercher ».
  Les résultats montent en feuille inférieure (§ 5.2) au-dessus de la carte, qui reste visible
  en haut et cadre le trajet sélectionné. **Le trajet sélectionné est tracé tel qu'il se parcourt**,
  et non en segments droits entre ses extrémités : sa géométrie est demandée au serveur pour lui
  seul (§ 7, règle 6).

**Autocomplétion**
- `/api/v1/geocode` avec `text`, `place` = centre de la carte pour le biais géographique
  (à défaut la dernière position connue), `language`, `numResults=20` ; la liste affichée en retient
  au plus dix, composée pour qu'aucun type de lieu ne masque les autres : au plus quatre arrêts
  homonymes situés hors de la commune du centre de la carte, et au moins une adresse et un lieu dès
  que le serveur en a rendu.
- Debounce de **350 ms**, longueur minimale de 3 caractères, annulation de la requête précédente.
- Les résultats distinguent visuellement adresse, arrêt et lieu (`LocationType`), avec le mode de
  transport desservi pour les arrêts.
- Trois entrées supplémentaires en tête de liste : **Ma position**, **Domicile**, **Travail**,
  et **Choisir sur la carte** (qui replie le clavier et fait choisir un point par appui long).
- Dès le premier caractère, et sans aucune requête, la liste propose d'abord les lieux enregistrés
  (Domicile, Travail, lieux favoris) et les points de départ et d'arrivée des dernières recherches
  dont le nom ou la description contient la saisie, au plus cinq, les plus récents d'abord. Les
  suggestions du serveur suivent, sans doublon avec ce bloc.

**Bouton de position (bas à droite)**
- Trois états : position inconnue, centrage sur la position, suivi actif.
- Au premier appui seulement, demande de `ACCESS_COARSE_LOCATION` puis, si l'utilisateur insiste
  pour un centrage précis, `ACCESS_FINE_LOCATION`. Via le `LocationManager` de la plateforme,
  jamais les services Google. `/api/v1/reverse-geocode` fournit le libellé lisible quand la
  position est utilisée comme point de départ.
- Permission refusée : le bouton reste, un appui explique en une phrase et propose d'ouvrir les
  réglages système. L'application reste pleinement utilisable sans localisation.
- Appui long sur la carte : menu « Partir d'ici » / « Aller ici ».

**Cadrage initial** : dernière position de caméra mémorisée ; à défaut la position de l'utilisateur
si elle est déjà connue sans demande de permission ; à défaut le cadrage renvoyé par
`GET /api/v1/map/initial` (le serveur indique le centre et le zoom de sa zone de données).

### 5.2 Résultats — l'écran central

Quatre onglets. L'ordre ci-dessous est celui d'origine, et **il est réglable** (§ 5.6) : l'usager
range les catégories comme il l'entend, et le premier onglet de l'ordre retenu est celui sur lequel
l'écran s'ouvre. Le réglage ne touche qu'à la disposition — aucune catégorie ne se retire, les
quatre sont toujours recherchées, avec les mêmes paramètres.

**Il n'y a pas d'onglet « Libre-service »** : les véhicules en libre-service sont intégrés à la
catégorie correspondant à leur type — un vélo partagé relève de l'onglet Vélo, une trottinette
aussi. L'onglet Transport en commun, lui, n'en emprunte aucun : on rejoint et on quitte un arrêt
à pied.

| Onglet | Requête `plan` correspondante |
|---|---|
| **Transport en commun** | `transitModes=TRANSIT`, `preTransitModes=WALK`, `postTransitModes=WALK`, `directModes=` (vide) |
| **Voiture** | `directModes=CAR`, `transitModes=` (vide) |
| **Vélo** | `directModes=BIKE,RENTAL` avec `directRentalFormFactors=BICYCLE,SCOOTER_STANDING,SCOOTER_SEATED`, `transitModes=` (vide) |
| **À pied** | `directModes=WALK`, `transitModes=` (vide) |

Conséquences à respecter :

- Dans l'onglet **Vélo**, les trajets à vélo personnel et à vélo en libre-service cohabitent dans la
  même liste. Chaque proposition indique clairement de laquelle il s'agit : pictogramme et libellé
  distincts, nom de l'exploitant et couleur du système (`rental.color`) sur les trajets partagés.
  Un filtre en tête de liste permet de n'afficher que l'un ou l'autre.
- Dans l'onglet **Transport en commun**, le rabattement se fait **à pied et seulement à pied** :
  ni voiture, ni vélo, ni trottinette partagés en premier ou dernier kilomètre. Un trajet motorisé
  ou à vélo n'est pas du transport en commun, et le ranger sous cet onglet mélangerait deux façons
  de se déplacer que l'usager a justement séparées en changeant d'onglet. Cette catégorie ne
  transporte donc aucun paramètre de types de véhicules.
- Le filtre des types de véhicules (`directRentalFormFactors`) est exposé dans les réglages :
  l'utilisateur qui ne veut pas de trottinettes doit pouvoir les exclure d'un seul endroit. Seuls
  les types que l'onglet Vélo peut proposer y figurent — vélo, trottinette, trottinette assise ;
  une case sans effet sur aucune recherche mentirait à l'usager. Un réglage antérieur qui nommait
  un autre type est oublié à la relecture. Les paramètres `preTransitRentalFormFactors` et
  `postTransitRentalFormFactors` ne sont plus émis.
- Ces paramètres sont marqués « expérimental » côté MOTIS : leur nom ou leur comportement peut
  changer sans changement de version. Les isoler dans une seule fonction de construction de requête,
  pour n'avoir qu'un endroit à corriger.

Règles de comportement :

- **Une requête par onglet**, et **les quatre onglets chargés au départ de la recherche, en série** :
  l'onglet consulté d'abord, les trois autres à sa suite dans l'ordre des languettes, une requête à
  la fois. Jamais quatre
  requêtes lancées ensemble : l'usager attend la réponse de l'onglet qu'il regarde, et trois
  requêtes parties en même temps que la sienne ne feraient que la retarder. Résultat mis en cache
  pour la durée de la recherche, et un onglet déjà chargé n'est jamais rechargé.
- Motif de ce chargement d'emblée : **chaque onglet annonce sous son libellé la durée du trajet le
  plus rapide de sa catégorie**, ce qui suppose de connaître les quatre. C'est ce qui permet de
  comparer les modes d'un coup d'œil, sans ouvrir les onglets un par un.
- Cette durée est celle du trajet **le plus court**, et non celle du premier de la liste, qui est
  ordonnée par heure de départ. Un onglet dont la réponse n'est pas arrivée l'annonce (« … ») ; un
  onglet sans trajet, comme un onglet en échec, affiche un tiret. Un trajet dont une portion est
  supprimée n'est jamais annoncé comme le plus rapide : ce serait une promesse fausse.
- Le rafraîchissement du temps réel (§ 7.4) ne porte que sur l'onglet consulté : les durées
  annoncées par les trois autres restent celles de leur chargement.
- Motif du découpage par onglet : côté MOTIS, les trajets en transport en commun plus lents que le trajet
  direct le plus rapide sont éliminés pendant la recherche. Mélanger transit et modes directs dans
  une seule requête fait donc disparaître des résultats. Les requêtes séparées sont la méthode
  recommandée par l'API elle-même.
- Les résultats arrivent dans `itineraries` (avec transport en commun) et dans `direct` (sans).
- `maxDirectTime` vaut 1800 s par défaut, ce qui coupe les trajets voiture ou vélo de plus de 30
  minutes. Pour les onglets Voiture / Vélo / À pied, l'envoyer explicitement (par ex. 4 h pour la
  voiture, 3 h pour le vélo, 2 h pour la marche), en sachant que le serveur peut plafonner.
  Si un onglet ne renvoie rien, afficher un état vide explicite (« aucun trajet trouvé dans la
  limite de durée »), pas une liste vide muette.

**Carte de résultat** (une par trajet proposé) :

- Heure de départ → heure d'arrivée, durée totale, nombre de correspondances (`transfers`).
- Frise horizontale des portions, à l'échelle de leur durée, colorée avec `routeColor` /
  `routeTextColor` quand ils existent, avec le pictogramme du mode et le `displayName` de la ligne.
- Retard éventuel : comparaison de `startTime`/`endTime` avec `scheduledStartTime`/`scheduledEndTime`,
  affiché en vert (à l'heure), orange, rouge. Un indicateur distinct signale une portion `cancelled`.
  Une portion sans donnée temps réel (`realTime = false`) est affichée sans coloration ni « à l'heure ».
- Badge de perturbation si la portion porte des `alerts`.
- Boutons **Plus tôt** / **Plus tard** en tête et pied de liste, via `previousPageCursor` /
  `nextPageCursor` (la requête d'origine est renvoyée telle quelle, seul le curseur change).
- Un sélecteur en tête de liste ordonne les trajets affichés par heure de départ (défaut), par durée
  croissante ou par nombre de correspondances puis durée. Le tri est local : il ne déclenche aucune
  requête, et la pagination continue de recoller les pages par heure de départ avant tri.

### 5.3 Détail d'un trajet

Liste verticale des portions, chacune dépliable. Pour chaque portion, selon son mode :

**Portion en transport en commun**
- Ligne (`routeShortName` / `displayName`), direction (`headsign`), transporteur (`agencyName`).
- Arrêt de montée et heure, arrêt de descente et heure, horaires théoriques si différents.
- **Nombre d'arrêts intermédiaires** (`intermediateStops.size`), avec pluriel correct, et la liste
  dépliable de ces arrêts avec leurs heures de passage.
- Quai / voie (`track`) quand il est renseigné, niveau d'accessibilité (`wheelchairAccessible`),
  transport de vélos autorisé (`bikesAllowed`).
- Perturbations (`alerts`) : titre, description, gravité, période, lien.
- Appui sur la ligne → écran **Détail de la course** (`/api/v6/trip`) avec la desserte complète.

**Portion à pied ou à vélo**
- Distance, durée, dénivelé si disponible.
- Instructions pas-à-pas (`steps`) : liste des manœuvres, dépliable, jamais dépliée par défaut.
- Demander `detailedLegs=true` et `detailedTransfers=true` uniquement quand l'écran de détail est
  ouvert : pour la liste de résultats, `detailedLegs=false` réduit nettement la charge et la taille
  des réponses.

**Portion en libre-service**
- Système et exploitant (`rental.systemName`, `color`), type de véhicule (`formFactor`),
  motorisation (`propulsionType`).
- Station de prise (`fromStationName`) et station de retour (`toStationName`) ; véhicule en
  free-floating si ces champs sont vides.
- **Disponibilité** : appel `GET /api/v1/rentals` avec `point` = coordonnées de la station et
  `radius` faible, pour obtenir `numVehiclesAvailable`, `vehicleTypesAvailable` (par type) et
  `vehicleDocksAvailable` (places libres au retour), plus `isRenting` / `isReturning`.
  Affichage : « 7 vélos disponibles · 4 places libres à l'arrivée ». Cette information est datée :
  afficher l'heure de récupération et un bouton de rafraîchissement.
- Contrainte de retour (`returnConstraint`) affichée en clair.
- Bouton d'ouverture de l'application de l'exploitant via `rentalUriAndroid` (intent externe,
  jamais de WebView interne).

**Portion en voiture** : distance, durée, instructions de conduite (`steps`).

**Carte du trajet** : polylignes de toutes les portions (`legGeometry`, précision 6), colorées par mode
et par ligne, marqueurs de départ, d'arrivée et de correspondance, cadrage automatique sur l'emprise
du trajet. Fond de carte : § 5.7.

**Actions** : partager le trajet en texte, ajouter aux favoris, rafraîchir
(`/api/v6/refresh-itinerary` avec l'`id` de l'itinéraire, qui recalcule avec les données temps réel
à jour sans relancer une recherche complète).

### 5.4 Prochains départs à un arrêt

Accessible depuis la recherche (en choisissant un arrêt), depuis la carte, ou depuis un favori.

- `GET /api/v6/stoptimes` avec `stopId`, `time`, `n`, `withAlerts=true`, `direction`, `mode`.
- Liste : heure, retard, ligne, direction, quai, annulations barrées.
- Filtres par mode de transport, boutons « plus tôt » / « plus tard » via `pageCursor`.
- Bandeau des perturbations en vigueur pour l'arrêt.
- Appui sur un départ → détail de la course.

### 5.5 Favoris et historique

- **Domicile** et **Travail** : deux emplacements nommés, distincts des autres favoris, avec leur
  propre icône et une place réservée en tête des suggestions et des puces de l'écran d'accueil.
  Tous deux sont **facultatifs** : tant que Domicile ou Travail n'est pas renseigné, **aucune
  puce ne lui correspond**. Une puce absente n'est simplement pas affichée, conformément au § 5.1 ;
  il n'existe pas de puce « Définir mon domicile ». Ces deux emplacements se créent depuis les
  réglages, ou par appui long sur un résultat ; une fois renseignés, ils sont modifiables et
  supprimables depuis les réglages comme depuis un appui long sur la puce. L'application ne les
  réclame jamais d'elle-même, en particulier pas à la première ouverture — et cette règle-ci le
  garantit, là où une invitation permanente en tête de l'écran d'accueil aurait été exactement la
  réclamation que l'on refuse.
- **Favoris** : autres lieux nommés, arrêts, et trajets complets (couple départ/arrivée,
  éventuellement avec préférences de modes).
- **Historique** : les N dernières recherches (N = 50), horodatées, effaçables une par une ou en bloc.
- Stockage **local uniquement** (Room). Un réglage permet de désactiver l'historique, et un bouton
  « Tout effacer » est présent dans les réglages.
- Aucune donnée de localisation n'est journalisée en dehors de ces deux mécanismes.

#### 5.5.1 Aucune surveillance de trajet

Une version antérieure de cette spec décrivait ici des **trajets surveillés** : l'application
vérifiait l'état d'un trajet favori une heure avant l'heure de départ habituelle et notifiait
l'usager si quelque chose avait changé. **La fonction est retirée, et elle ne doit pas revenir.**

Le motif est la confidentialité, et il est décisif : une requête envoyée au serveur à heure fixe,
la veille du même trajet, jour après jour, **dessine une habitude de déplacement** — l'heure à
laquelle quelqu'un part de chez lui et l'endroit où il se rend. Ce que la fonction demandait à
l'usager de consentir valait plus cher que ce qu'elle lui rendait, d'autant que l'information est
disponible à l'ouverture de l'application, gratuitement et sans rien révéler d'autre qu'une
consultation.

Le retrait est entier, et pas seulement au niveau de l'interface :

- Aucune tâche de fond, donc plus aucune dépendance à `androidx.work` (§ 7.7).
- Aucune notification, donc plus de `POST_NOTIFICATIONS`, ni de `WAKE_LOCK`, ni de permission de
  démarrage automatique ou de service à retirer de la fusion (§ 11).
- Aucun stockage : la table `watched_journeys` est **supprimée** par une migration Room, parce que
  ce qu'elle contenait — heure et jours de départ habituels — est de la même nature que ce que la
  fonction révélait au serveur. Les trajets favoris, eux, sont conservés intacts.

C'est une **interdiction**, au même titre que celles du § 11 : un test du dépôt échoue si le code
réintroduit une tâche de fond, une permission de fond, un service ou un récepteur.

### 5.6 Réglages

Première entrée de l'écran, avant toutes les autres : **Serveur MOTIS**, avec l'URL en cours affichée
en sous-titre. Elle ouvre un écran dédié (§ 5.6.1).

- Préférences de recherche : vitesse de marche (`pedestrianSpeed`), profil piéton
  (`pedestrianProfile`, pour l'accessibilité en fauteuil), vitesse à vélo (`cyclingSpeed`),
  coût du dénivelé (`elevationCosts`), temps de correspondance supplémentaire
  (`additionalTransferTime`), nombre maximal de correspondances (`maxTransfers`),
  exiger le transport des vélos (`requireBikeTransport`).
- Affichage : thème (système / clair / sombre), langue, format 12 h ou 24 h,
  couches visibles sur la carte (arrêts, libre-service, points d'intérêt),
  ordre des catégories de l'écran de résultats.
- **Ordre des catégories** : une entrée qui ouvre un écran dédié, où les quatre catégories du § 5.2
  se rangent au glissé-déposé. Le geste au doigt n'est jamais la seule voie d'accès : chaque ligne
  porte aussi les actions d'accessibilité « Monter » et « Descendre » (§ 9). L'ordre décide de la
  disposition des languettes, de l'onglet ouvert d'emblée et de l'ordre de chargement des trois
  autres ; il ne retire aucune catégorie et ne change aucune requête, si bien que le modifier ne
  périme pas les résultats affichés.
- **Langue** : ce n'est pas un réglage interne. L'entrée ouvre l'écran système « Langue de
  l'application » d'Android 13, alimenté par le `localeConfig` du manifeste, qui déclare les
  langues réellement fournies. **Elle n'est affichée qu'à partir d'Android 13** ; en deçà, elle est
  masquée et l'application suit la langue du système. Motif : la langue par application n'est pas
  accessible sous l'API 33 sans `androidx.appcompat`, et le coût de cette bibliothèque — son poids,
  son thème et ses ressources — n'est pas justifié pour une seule fonction dans un projet
  entièrement Compose. Si la surcouche du constructeur a retiré cet écran, l'appui ne reste pas
  sans réponse : l'application le dit.
- Données : effacer l'historique, effacer le cache des tuiles, effacer le cache des résultats.
- À propos : version, licence, lien vers le dépôt, attributions (§ 4.2).

#### 5.6.1 Écran « Serveur MOTIS »

- Champ de saisie de l'**URL de base** (`https://api.transitous.org` par défaut), clavier de type
  URI, sans correction automatique ni majuscule initiale, collage depuis le presse-papiers facilité.
- **Normalisation à la saisie** : ajout de `https://` si le schéma manque, suppression de la barre
  oblique finale et d'un éventuel suffixe `/api` collé par erreur. L'URL enregistrée est la racine,
  les chemins `/api/v6/...` et `/tiles/...` sont ajoutés par l'application.
- Bouton **« Tester la connexion »** : appelle `GET /api/v1/health`, puis une requête
  `GET /api/v6/map/stops` sur une petite emprise pour vérifier la version de l'API, puis une tuile
  pour savoir si le serveur sert un fond de carte. Trois résultats distincts affichés :
  serveur joignable, version d'API compatible, tuiles disponibles ou non. Un serveur sans tuiles
  reste utilisable (§ 5.7).
- **Rien n'est enregistré tant que le test n'a pas réussi**, sauf validation explicite par
  l'utilisateur (« Utiliser quand même »). Une URL invalide ne doit jamais laisser l'application
  dans un état où plus aucune recherche ne fonctionne sans que la cause soit visible.
- Bouton **« Rétablir le serveur par défaut »**, toujours accessible.
- Les serveurs déjà utilisés sont mémorisés et proposés en liste, avec suppression par balayage.
  Une bascule ne demande pas de ressaisir l'URL.
- **Serveur en clair (`http://`)** : accepté pour une instance sur le réseau local ou en
  développement, avec un avertissement explicite au moment de l'enregistrement. Le dispositif
  technique est celui décrit au § 4.1 : la plateforme ne sachant pas restreindre le clair aux seuls
  hôtes saisis à l'exécution, la `network_security_config` est permissive et **c'est cet écran qui
  porte la restriction** — aucune URL `http://` n'est enregistrée, ni même testée, sans un
  consentement explicite, demandé une fois par hôte et mémorisé.
- **Effets d'un changement de serveur**, annoncés avant confirmation : les caches de résultats, de
  géocodage et de tuiles sont vidés ; les favoris et l'historique sont conservés ; les identifiants
  d'arrêts enregistrés dans les favoris peuvent ne plus être reconnus par le nouveau serveur — dans
  ce cas le favori reste affiché avec ses coordonnées et un signalement discret, il n'est jamais
  supprimé automatiquement.
- L'écran rappelle en pied de page que l'instance publique par défaut est tenue par des bénévoles,
  avec le lien vers leur politique d'usage.

### 5.7 Carte

#### Fond de carte

- Rendu MapLibre GL avec les tuiles vectorielles servies par le serveur MOTIS lui-même :
  `{base}/tiles/{z}/{x}/{y}.mvt`, glyphes `{base}/glyphs/{fontstack}/{range}.pbf`,
  sprites `{base}/sprite_sdf`. Une feuille de style MapLibre minimaliste est embarquée dans
  l'application (routes, eau, bâti, libellés), déclinée en clair et en sombre.
- Si le serveur configuré ne sert pas de tuiles (404), la carte affiche un fond neutre avec les
  seules polylignes du trajet, et un message discret l'explique. Pas de repli sur un fournisseur tiers.
- Attribution OpenStreetMap visible en permanence sur la carte.

#### Contenu affiché selon le zoom

La carte se densifie progressivement. Rien ne s'affiche tant que l'échelle ne le justifie pas : à
petite échelle, mille points sont illisibles et coûtent cher à charger.

| Zoom | Ce qui apparaît | Origine de la donnée |
|---|---|---|
| < 11 | Rien d'autre que le fond de carte. **Aucune requête n'est émise.** | — |
| 11 → 13 | Gares et stations de métro : `RAIL`, `HIGHSPEED_RAIL`, `LONG_DISTANCE`, `SUBURBAN`, `SUBWAY` | `/api/v6/map/stops` |
| 13 → 15 | + tram, bus, cars, ferry, téléphériques, et **stations de véhicules en libre-service** | `/api/v6/map/stops` + `/api/v1/rentals` |
| ≥ 15 | + véhicules en libre-service isolés (free-floating), et **points d'intérêt** : services publics, monuments et sites remarquables, équipements (hôpitaux, écoles, bibliothèques, poste), parcs | `/api/v1/rentals` + couches du fond de carte |

**Le palier 15 est le dernier**, parce que le tuilage MOTIS s'arrête au zoom 15 : au-delà, le
serveur répond `501` et aucune tuile n'existe plus. La source déclare donc `"maxzoom": 15` — MapLibre
cesse alors de réclamer l'inexistant et agrandit lui-même la dernière tuile chargée, si bien que
zoomer plus loin reste possible sans émettre une seule requête (§ 4.2, point 4). Puisque plus aucun
détail n'arrive après 15, aucune couche de la feuille de style ne porte un `minzoom` supérieur : les
points d'intérêt paraissent tous à 15 et leur nom à 16, et rien ne se réserve pour un palier que le
fond de carte ne sait plus enrichir.

Points essentiels de mise en œuvre :

- **Les points d'intérêt ne font l'objet d'aucune requête** : ils sont déjà présents dans les
  tuiles vectorielles issues d'OpenStreetMap. Il suffit d'activer les couches correspondantes dans
  la feuille de style avec le bon `minzoom`. Aucune API à interroger, aucun coût réseau
  supplémentaire, et le comportement reste correct sur un serveur auto-hébergé.
- **Les commerces sont hors périmètre de la v1.** Ni boutiques, ni cafés, ni restaurants : ils
  saturent la carte et ne servent pas le propos, qui est de se déplacer. Les couches existent dans
  les tuiles et pourront être activées plus tard sans rien changer d'autre. La v1 s'en tient aux
  repères qui aident à s'orienter et à reconnaître un quartier : services publics, monuments,
  équipements, parcs.
- Les arrêts sont demandés par **emprise rectangulaire** (`min` / `max`), avec le paramètre `modes`
  restreint au palier de zoom courant, et `grouped=true` pour que le serveur regroupe lui-même les
  quais d'une même gare.
- Chaque palier de zoom conserve les couches des paliers inférieurs : on ajoute, on ne remplace pas.
- Un réglage permet de masquer complètement les arrêts, les stations en libre-service ou les
  points d'intérêt, indépendamment du zoom.
- Appui sur un arrêt : infobulle avec le nom et les lignes desservies, et un bouton menant aux
  prochains départs (§ 5.4). Appui sur une station de libre-service : nom, véhicules disponibles,
  places libres, lien vers l'exploitant.

#### Fluidité et fraîcheur — règles impératives

1. **Aucune requête pendant le mouvement.** Le chargement est déclenché à l'arrêt de la caméra
   (`onCameraIdle`), après un debounce de **300 ms**. Un déplacement suivi d'un autre n'émet qu'une
   requête, la dernière.
2. Toute requête en vol est **annulée** dès que la caméra bouge à nouveau.
3. L'emprise demandée est celle de l'écran **élargie de 30 %**, pour que les petits déplacements ne
   déclenchent rien du tout.
4. **Cache par emprise et par palier**, en mémoire, avec expiration : 10 minutes pour les arrêts
   (donnée quasi statique), **60 secondes** pour les disponibilités en libre-service (donnée
   volatile). Une emprise déjà couverte par une réponse en cache n'est pas redemandée.
5. Franchir un seuil de zoom vers le bas ne déclenche aucune requête : on masque des couches déjà
   chargées.
6. Les marqueurs sont dessinés comme **couches MapLibre alimentées par une source GeoJSON**, avec
   regroupement (`cluster`) au-delà de 200 points visibles — jamais comme des vues Android
   superposées à la carte, qui écroulent la fluidité dès la centaine de marqueurs.
7. Les réponses sont converties en GeoJSON hors du fil principal, et la source est mise à jour en
   une seule opération.
8. La carte n'est **jamais** détruite puis recréée lors d'un changement d'écran : une seule instance
   pour toute la durée de vie de l'application, à laquelle on ajoute et retire des couches.
9. Aucune animation de caméra de plus de 500 ms ; les cadrages de trajet utilisent un
   remplissage (`padding`) qui tient compte de la feuille inférieure ouverte.

**Objectifs mesurables**, à vérifier sur un appareil d'entrée de gamme :

- Démarrage à froid jusqu'à la première image de carte : **< 1,5 s**.
- Rendu à 60 images par seconde pendant les déplacements et les zooms, sans image perdue visible.
- Première donnée d'arrêts affichée moins de **400 ms** après l'arrêt de la caméra, réseau nominal.
- Aucune opération de plus de 16 ms sur le fil principal (à vérifier au profileur, macrobenchmark
  sur le parcours ouverture → déplacement → zoom).

---

## 6. Modèle de données

`:core` définit ses propres modèles de domaine ; les DTO de l'API restent dans `:data` et ne
remontent jamais dans l'interface. Ce découplage absorbe les changements de l'API MOTIS,
qui sont fréquents sur les champs marqués « Experimental ».

Types de domaine attendus : `Location`, `SearchQuery`, `Journey`, `JourneyLeg` (scellé : `Transit`,
`Walk`, `Bike`, `Car`, `Rental`), `Stop`, `StopTimeEntry`, `Disruption`, `RentalAvailability`,
`ServerConfig`.

Toutes les heures sont manipulées en `Instant` et converties à l'affichage avec le fuseau de
l'appareil. Les durées sont formatées par une fonction unique et testée de `:core`.

---

## 7. Sobriété réseau — règles impératives

1. Autocomplétion : debounce ≥ 350 ms, minimum 3 caractères, annulation de la requête précédente.
2. Une seule requête `plan` en vol à la fois pour un onglet donné ; toute nouvelle recherche annule
   les requêtes en cours.
3. Les quatre onglets d'une recherche sont chargés **en série**, l'onglet consulté d'abord (§ 5.2) ;
   au-delà de ce chargement initial, un onglet non consulté ne déclenche aucune requête — ni
   pagination, ni rafraîchissement.
4. **Aucun polling.** Le rafraîchissement du temps réel est déclenché par l'utilisateur
   (« tirer pour rafraîchir »), ou au retour au premier plan si les données ont plus de 60 secondes.
5. Cache mémoire des réponses `plan` pour la durée de la recherche ; cache disque de 24 h pour les
   résultats de géocodage.
6. `detailedLegs=false` sur la liste de résultats, `true` pour le **seul trajet mis en évidence**
   sur la carte (§ 5.1) et à l'ouverture d'un trajet. Sans géométrie, la carte ne saurait relier
   que le départ à l'arrivée en ligne droite : le trajet dessiné obtient donc son tracé réel en une
   requête de rafraîchissement d'itinéraire, mémorisée le temps de la recherche. Les autres trajets
   de la liste n'en déclenchent aucune.
7. **Aucun travail de fond, sans exception.** L'application ne fait de réseau que lorsqu'elle est
   au premier plan : aucun service, aucune tâche périodique, aucune synchronisation, aucune tâche
   différée. La seule exception qu'admettait une version antérieure de cette spec, les trajets
   surveillés, est retirée (§ 5.5.1) : une requête à heure fixe avant un trajet habituel trahit une
   habitude de déplacement.
8. Délai d'expiration de 30 s, une seule tentative de reprise, pas de reprise sur les erreurs 4xx.
9. Carte : aucune requête sous le zoom 11, déclenchement à l'arrêt de la caméra uniquement,
   emprise élargie de 30 %, cache par emprise, annulation systématique (§ 5.7).
10. Les tuiles du fond de carte sont mises en cache sur disque par MapLibre, plafond de 100 Mo,
    purgeable depuis les réglages.

---

## 8. Gestion des erreurs

| Situation | Comportement |
|---|---|
| Pas de réseau | Bandeau explicite, bouton « Réessayer », pas d'écran vide |
| Serveur injoignable / 5xx | Message distinguant clairement « le serveur ne répond pas » d'« aucun trajet trouvé » |
| 400 / 422 | Message issu du champ `error` de la réponse, avec l'action corrective quand elle est devinable |
| 404 sur un point d'entrée `v6` | Message sur la version du serveur (§ 4.3) |
| Aucun résultat | État vide illustré, avec suggestions : élargir la fenêtre, autoriser plus de correspondances, changer d'heure |

Aucune trace ne contient d'adresse, de coordonnée ou de requête utilisateur, y compris en
compilation de débogage.

---

## 9. Accessibilité

- Tous les éléments interactifs ont un `contentDescription` ; les pictogrammes de mode sont doublés
  d'un libellé textuel.
- Zone tactile minimale de 48 dp, contrastes conformes au niveau AA.
- Aucune information portée par la seule couleur : les retards et les annulations sont doublés d'un
  texte et d'une icône.
- Compatible avec l'agrandissement des polices jusqu'à 200 % sans troncature.
- Profil piéton `WHEELCHAIR` exposé dans les réglages, et signalement de l'accessibilité des
  portions (`wheelchairAccessible`).

---

## 10. Tests

- `:core` : tests JVM sur le décodage des polylignes (précisions 6 et 7), le mapping DTO → domaine,
  le calcul des retards, le formatage des durées et des pluriels, l'assemblage des paramètres de
  requête pour chacune des cinq catégories.
- `:data` : tests avec un serveur HTTP simulé et des réponses JSON réelles capturées depuis
  `api.transitous.org`, stockées dans `src/test/resources/fixtures/`. Au moins : un trajet en
  transport en commun avec correspondance, un trajet en libre-service, un trajet sans résultat,
  une réponse portant des `alerts`, une réponse contenant des champs inconnus.
- `:app` : tests Compose sur les états liste / vide / erreur / chargement.
- Aucun test ne tape sur le réseau réel.
- **Vérification sur appareil réel** à chaque jalon : mode clair et sombre, rotation, texte agrandi,
  états vide et erreur. La procédure d'installation, de capture d'écran et de lecture des journaux
  via adb est décrite dans `CLAUDE.md`, qui est le document d'outillage du dépôt.

---

## 11. Confidentialité et permissions

Permissions déclarées, et aucune autre :

- `INTERNET`
- `ACCESS_COARSE_LOCATION` et `ACCESS_FINE_LOCATION`, facultatives, demandées à l'usage
- `ACCESS_NETWORK_STATE`, exigée par MapLibre : son `ConnectivityReceiver` appelle
  `getActiveNetworkInfo()` pour suspendre le téléchargement des tuiles hors ligne, et lève une
  `SecurityException` sans elle. Permission de **niveau normal** : accordée à l'installation, sans
  écran de consentement, elle ne donne accès qu'à l'état « connecté ou non » de l'appareil et ne
  révèle rien sur l'usager ni sur ses déplacements.
- `io.github.mgdx.escale.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`, insérée dans le manifeste
  fusionné par `androidx.core` : `ContextCompat.registerReceiver` s'en sert pour émuler
  `RECEIVER_NOT_EXPORTED` sur les versions d'Android antérieures à 13, en protégeant par elle les
  récepteurs enregistrés à l'exécution. Ce n'est pas une permission de la plateforme mais une
  permission **définie par l'application elle-même**, de **niveau `signature`** : seule une
  application signée avec la même clé pourrait l'obtenir, c'est-à-dire aucune autre. Elle n'est
  donc accordée à personne, ne donne accès à aucune donnée, n'ouvre aucun échange hors de
  l'application et ne donne lieu à aucun écran de consentement. Elle est nommée ici parce qu'un
  relecteur qui compte les entrées du manifeste fusionné en trouve **cinq** et non quatre.

`RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE`, `WAKE_LOCK` et `POST_NOTIFICATIONS` entraient
autrefois par le manifeste d'`androidx.work`, dépendance des trajets surveillés. La fonction et la
bibliothèque étant retirées (§ 5.5.1), **aucune de ces quatre permissions n'apparaît plus dans le
manifeste fusionné**, et il n'y a plus rien à en retirer par `tools:node="remove"`. Le jour où une
bibliothèque en réintroduirait une, elle serait retirée de la fusion plutôt que tolérée.

L'application doit rester entièrement utilisable si la permission de localisation est refusée.
Aucune permission de stockage, de contacts, de démarrage automatique, de service en arrière-plan,
ni d'alarme exacte.
Un fichier `PRIVACY.md` documente précisément ce qui est envoyé au serveur (les coordonnées de
départ et d'arrivée, l'heure, les préférences de modes) et ce qui reste local. Il dit aussi ce
qu'Escale ne fait pas : **aucune requête n'est envoyée sans que l'usager ait l'application sous les
yeux**.

---

## 12. Livrables attendus dans le dépôt

```
SPEC.md              ce document
CLAUDE.md            instructions d'outillage et de méthode pour les agents Claude
README.md            présentation, captures, attributions
PRIVACY.md           politique de confidentialité
CONTRIBUTING.md      conventions de code, procédure de traduction
LICENSE              GPLv3
docs/architecture.md découpage des modules et flux de données
docs/icone.md         motif, couleurs, destinations des fichiers d'icône
docs/motis-api.md    points d'entrée utilisés, paramètres, pièges connus
fastlane/metadata/android/{en-US,fr-FR}/  description, captures, changelogs
```

Build : Gradle avec catalogue de versions (`libs.versions.toml`), `ktlint` et `detekt` branchés sur
la CI GitHub Actions (compilation, tests, lint sur chaque poussée).

---

## 13. Jalons

1. **Socle** : projet, modules, thème, client HTTP avec `User-Agent`, réglage du serveur, `health`.
2. **Carte** : MapLibre, tuiles du serveur, écran d'accueil plein écran, bouton de position,
   cadrage initial, attributions.
3. **Recherche** : géocodage, carte de recherche flottante, sélecteur d'heure, `plan` sur l'onglet
   transport en commun, feuille de résultats.
4. **Détail** : écran de détail, portions, arrêts intermédiaires, instructions pas-à-pas,
   tracé du trajet sur la carte.
5. **Catégories** : les quatre autres onglets, paramètres de durée maximale, états vides.
6. **Points sur la carte** : `map/stops`, paliers de zoom, couches de points d'intérêt, cache et
   annulation, mesures de fluidité.
7. **Temps réel** : retards, annulations, perturbations, `refresh-itinerary`, pagination.
8. **Libre-service** : `rentals`, disponibilités, intégration aux onglets Vélo et Transport en
   commun, filtres de types de véhicules, liens exploitants, marqueurs sur la carte.
9. **Départs** : `stoptimes`, détail de course.
10. **Favoris et historique** : domicile, travail, Room, réglages, effacement.
11. **Finition** : accessibilité, traduction française, métadonnées Fastlane, conformité F-Droid.

Chaque jalon se termine par une application qui compile, dont les tests passent, et qui est
utilisable sur un appareil réel.

---

## 14. Références

- OpenAPI MOTIS : `https://raw.githubusercontent.com/motis-project/motis/master/openapi.yaml`
- Dépôt MOTIS : `https://github.com/motis-project/motis`
- Politique d'usage Transitous : `https://transitous.org/api/`
- Sources de données Transitous : `https://transitous.org/sources/`

---

## 15. Points ouverts

À trancher par le mainteneur avant le jalon 1 :

1. Liste précise des catégories OpenStreetMap retenues pour les points d'intérêt au zoom ≥ 15,
   à établir en regardant le rendu réel sur une ville dense et une ville moyenne.
