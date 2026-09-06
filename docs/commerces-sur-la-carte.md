# Commerces et services sur la carte

> Document de travail. SPEC.md § 5.7 dit aujourd'hui que « les commerces sont hors périmètre de
> la v1 » : le lot 0 amende cette phrase avant tout code.

## 1. Le constat

Tout ce qu'il faut est **déjà dans les tuiles du serveur MOTIS** et dans son géocodeur :

| Besoin | État vérifié (septembre 2026) |
|---|---|
| Commerces sur la carte | La couche `pois` des tuiles (profil `full.lua`) porte `shop` (supermarché, boulangerie, vêtements, pharmacie de parapharmacie…), `amenity` (restaurant, café, bar, banque, distributeur, toilettes, point d'eau, hôpital…), `tourism` (hôtel, camping, office de tourisme…), `leisure`, `name`, `cuisine`, `atm`, `religion`, `housenumber`, dès le zoom 14. Le style embarqué les **filtre volontairement** (`poi-landmarks`). |
| Nom des commerces dans la recherche | `/api/v1/geocode` rend déjà les commerces en `type: PLACE` (essai « Carrefour City » près de Paris : six réponses). L'application leur réserve déjà une place dans la liste (`composeSuggestions`). **Rien à faire.** |
| Horaires, site, téléphone | **Absents des tuiles**, absents de tout jeu de tuiles public, absents de l'API MOTIS. Impossible sans produire ses propres données. Hors périmètre de ce plan. |
| Adresse complète | Les tuiles portent le numéro, pas la rue. `/api/v1/reverse-geocode` la donne en une requête, déjà utilisée par l'application pour « Ma position ». |

Conséquence : **aucun serveur nouveau, aucune dépendance, aucune
permission.** C'est une feuille de style, un réglage, une fiche, et un peu de `:core` testable.

## 2. Ce qu'on fait

### 2.1 Le réglage

> **Décision du mainteneur, septembre 2026.** Ce paragraphe décrivait une bascule unique
> « Commerces et services ». Le périmètre a été élargi : c'est un écran entier, et chaque catégorie
> se règle séparément.

Réglages → Affichage ne porte plus aucune bascule de couche : une seule entrée, **« Couches de la
carte »**, ouvre un écran dédié, sur le modèle de l'écran d'ordre des catégories. **Quatorze
bascules** s'y règlent une par une, en trois sections :

| Section | Nombre | Contenu |
|---|---|---|
| Transport | 2 | Arrêts, Libre-service — les deux allumées, elles quittent la liste « Affichage » |
| Repères | 4 | les points d'intérêt de la v1, découpés — les quatre allumées |
| Commerces et services | 8 | Toilettes allumée, les sept autres éteintes |

Soit **douze catégories de points d'intérêt** (4 + 8), et c'est ce nombre-là qui compte pour la
feuille de style. La carte de déplacement reste le propos : à part les toilettes, rien de nouveau
n'apparaît sans un geste de l'usager.

### 2.2 Les paliers de zoom et les douze catégories

> **Décision du mainteneur, septembre 2026.** Ce paragraphe ne décrivait que sept familles de
> commerces. Les repères de la v1 se découpent eux aussi en catégories réglables, et les toilettes
> sortent de « Services du quotidien » pour devenir une catégorie allumée par défaut. Une catégorie
> « Parcs et jardins » a été envisagée puis **abandonnée** : voir plus bas.

| Zoom | Ce qui apparaît |
|---|---|
| < 15 | Rien de plus qu'aujourd'hui. |
| ≥ 15 | Les **repères** des catégories activées, sans nom. |
| ≥ 16 | Le nom des repères, et le **pictogramme** des catégories de commerces activées — dont les toilettes, allumées d'emblée. Pictogramme discret, sous les arrêts dans l'ordre de placement. |
| ≥ 17 | Le nom des commerces, sous le pictogramme, `text-optional` : le nom s'efface avant le pictogramme quand ça se bouscule. |

Chaque catégorie porte un pictogramme des sprites `basics` déjà servis par le serveur (aucune image
nouvelle dans l'APK, déjà à 13,9 Mo sur 15).

**Repères — zoom ≥ 15, nom à partir de 16, les quatre allumées par défaut.** Elles reprennent
exactement le filtre `poi-landmarks` de la v1 : à réglages par défaut, le rendu ne change pas.

| Catégorie | Valeurs OSM | Par défaut |
|---|---|---|
| Services publics | `amenity` = townhall, post_office, police, fire_station, courthouse, community_centre, place_of_worship | allumée |
| Enseignement | `amenity` = school, university, college, library | allumée |
| Culture et monuments | `amenity` = theatre, cinema, arts_centre ; `historic` = castle, monument, memorial, fort, ruins, archaeological_site, battlefield ; `tourism` = artwork, viewpoint ; `man_made` = lighthouse | allumée |
| Santé publique | `amenity` = hospital, clinic, doctors, dentist, pharmacy | allumée |

Les **lieux de culte** sont rangés dans « Services publics » et n'ont pas de catégorie propre :
décision du mainteneur.

**Parcs et jardins — catégorie abandonnée, et voici pourquoi, pour qu'on n'y revienne pas.**
Une catégorie « Parcs et jardins » a été demandée, puis retirée après vérification dans
`profile/full.lua` de `motis-project/tiles` : elle n'aurait contenu ni parcs ni jardins.

- La couche `pois` n'accepte, pour `leisure`, que playground, dog_park, sports_centre, pitch,
  swimming_pool, water_park, golf_course, stadium, ice_rink (`poi_leisure_values`).
- `park` et `garden` partent dans la couche `land` sous `kind = park` / `kind = garden`
  (`process_land`, `elseif leisure == "golf_course" or leisure == "park" or …` → `kind = leisure`),
  et `process_land` **n'appelle jamais `set_names`** : la couche `land` ne porte pas de `name`.
- `nature_reserve` n'est retenu nulle part.

Un pictogramme, et surtout un **nom**, sur un parc est donc **impossible** sans une donnée que ni
les tuiles ni l'API MOTIS ne fournissent. **Décision du mainteneur : « on les laisse affichés sur
la carte et pas d'option ».** Les parcs, jardins et espaces verts restent ce qu'ils sont
aujourd'hui — des surfaces vertes dessinées par la couche `land-park` du fond de carte dès le
zoom 11, sans pictogramme, sans nom et sans réglage. Rien à faire côté code.

**Commerces et services — zoom ≥ 16, nom à partir de 17, huit catégories dont sept éteintes par
défaut.**

| Catégorie | Valeurs OSM | Par défaut |
|---|---|---|
| Toilettes | `amenity` = toilets | **allumée** |
| Alimentation | `shop` = supermarket, convenience, bakery, butcher, greengrocer, alcohol, beverages, kiosk, general, department_store, mall, marketplace | éteinte |
| Restauration | `amenity` = restaurant, fast_food, cafe, pub, bar, biergarten | éteinte |
| Santé | `amenity` = veterinary ; `shop` = chemist, optician | éteinte |
| Argent | `amenity` = bank, atm | éteinte |
| Hébergement | `tourism` = hotel, motel, hostel, guest_house, bed_and_breakfast, camp_site | éteinte |
| Services du quotidien | `amenity` = drinking_water, post_box, telephone, car_rental, car_sharing, bicycle_rental, vending_machine ; `shop` = hairdresser, laundry, dry_cleaning, travel_agency | éteinte |
| Autres commerces | toute autre valeur de `shop` acceptée par le profil | éteinte |

Trois pièges à ne pas rater :

- **« Toilettes » est la seule catégorie de cette section allumée par défaut**, et donc la seule
  chose qu'un usager qui met à jour voit apparaître sans avoir rien réglé. C'est assumé.
- `toilets` **ne figure plus** dans « Services du quotidien » ; `drinking_water` y reste.
- « Santé » ne reprend pas les valeurs de « Santé publique » : pharmacie, hôpital, clinique,
  cabinet médical et dentiste restent des **repères** visibles dès le zoom 15, et ne doivent pas
  apparaître deux fois. Il ne reste à « Santé » que vétérinaire, droguerie et opticien.

### 2.3 La fiche

> **Décision du mainteneur, septembre 2026.** La fiche s'ouvre sur **tout** point d'intérêt, pas
> seulement sur les commerces : les repères de la v1 (mairie, monument, lieu de culte, école,
> théâtre…) affichaient un pictogramme sans que rien ne se passe à l'appui, ce qui n'a jamais été
> voulu. C'est ce qui rend cohérent l'exemple « Lieu de culte · catholique » ci-dessous, puisque
> `place_of_worship` est un repère et non un commerce.

Appui sur un point d'intérêt, quelle que soit sa catégorie : une carte à la manière de l'infobulle
d'arrêt, avec :

- le **nom**, ou le libellé de la catégorie s'il n'en a pas ;
- le **type** traduit (« Boulangerie », « Restaurant · italien » avec `cuisine`, « Banque ·
  distributeur » avec `atm`, « Lieu de culte · catholique » avec `religion`/`denomination`) ;
- l'**adresse**, obtenue par géocodage inverse à l'ouverture de la fiche, en une requête,
  annulée si la fiche se ferme, mémorisée dans le cache de 24 h du géocodage ; en attendant, le
  numéro de voie s'il est dans la tuile ;
- **« Partir d'ici »** et **« Aller ici »**, comme l'appui long.

Pas de ligne d'horaires, pas de site, pas de téléphone : on n'affiche pas ce qu'on n'a pas.

### 2.4 La recherche

Rien à changer. À vérifier au téléphone seulement : « boulangerie » près du centre de la carte
propose bien des commerces, et la place réservée au premier `PLACE` fonctionne quand les arrêts
homonymes sont nombreux.

## 3. Lots

```
Lot 0  Amendement de la spec        humain + agent, bloque le reste
Lot 1  Code                         un seul développeur : :core, style, réglage, fiche, chaînes, tests
Lot 2  Recette sur le téléphone     superviseur
```

Un seul lot de code : tout tient dans une branche `jalon-12-commerces`, et découper ferait
plus de coordination que de travail.

---

## 4. Prompts

> **Avertissement.** Les prompts qui suivent décrivent **l'état initial du plan**, celui d'avant les
> décisions du mainteneur. Ils parlent encore d'une bascule unique, de sept familles et d'une fiche
> réservée aux commerces. **Le § 2 prime** : c'est lui qui décrit ce qui est à faire. Les prompts
> sont conservés tels quels pour garder trace de la façon dont le travail a été découpé, pas pour
> être suivis à la lettre.

### Lot 0 — Amendement de la spec

```
Tu travailles sur le dépôt Escale (lis CLAUDE.md, SPEC.md en entier, docs/architecture.md et
docs/commerces-sur-la-carte.md). Tu ne modifies PAS SPEC.md : tu produis
docs/propositions/spec-commerces.diff, un diff unifié applicable par `git apply`, que le
mainteneur validera à part.

L'amendement, dans le ton et la forme de la spec (phrases au présent, tableaux quand elle en
utilise) :
1. § 5.6, « Affichage » : la liste des couches visibles gagne « commerces et services », désactivée
   par défaut.
2. § 5.7, tableau « Contenu affiché selon le zoom » : deux lignes, « ≥ 16 : pictogrammes des
   commerces et services, si le réglage est activé » et « ≥ 17 : leurs noms », origine « couches
   du fond de carte ».
3. § 5.7, le paragraphe « Les commerces sont hors périmètre de la v1 » est remplacé par : les
   commerces existent dans les tuiles et s'affichent sur demande, jamais sous le zoom 16, avec
   les familles du § 2.2 de docs/commerces-sur-la-carte.md, et le motif (carte de déplacement
   d'abord, densification au choix de l'usager).
4. § 5.7, après « Appui sur un arrêt » : « Appui sur un commerce » avec le contenu de la fiche
   (§ 2.3), y compris la requête de géocodage inverse et son annulation.
5. § 7, règle 9 ou nouvelle règle : la fiche de commerce est la seule requête que les commerces
   déclenchent, à l'ouverture de la fiche, une par fiche, annulée à la fermeture, servie par le
   cache de 24 h du géocodage.
6. § 15, point ouvert 1 : le noter comme tranché par ces familles.

Dans ton rapport, cite chaque phrase existante que l'amendement contredit.
```

### Lot 1 — Code

```
Tu travailles sur le dépôt Escale. Lis CLAUDE.md, SPEC.md (surtout § 5.6, § 5.7, § 7, § 9),
docs/architecture.md en entier, docs/commerces-sur-la-carte.md en entier, puis tout le paquet
app/src/main/kotlin/io/github/mgdx/escale/ui/map/ (en particulier MapCanvas.kt, MapInstance.kt,
MapStopCard.kt, MapViewModel.kt, MapUiState.kt, MapSelection.kt), MapStyles.kt,
app/src/main/res/raw/map_style_light.json et map_style_dark.json, et la partie « couches » de
l'écran Réglages. Branche jalon-12-commerces. Invoque le skill android-ui pour la fiche et les
pictogrammes. Tu n'as pas adb (docs/architecture.md § 3 règle 6) : compilation, tests JVM,
tests Compose, aperçus.

Aucune dépendance, aucune permission, aucune image nouvelle : les pictogrammes viennent des
sprites `basics` du serveur (liste-les depuis <base>/sprites/basics/sprites.json et choisis un
pictogramme par famille ; note dans ton rapport ceux qui manquent et le repli utilisé).

À faire :

1. :core — un fichier core/model/PlaceCategory.kt (ou nom voisin) : enum des sept familles du
   § 2.2 et une fonction pure `placeCategory(shop, amenity, tourism) : PlaceCategory?` qui
   applique exactement le tableau ; une fonction `placeTypeKey(...)` qui rend la clé de
   traduction du type (une clé par valeur retenue, repli sur la famille). Tests JVM sur chaque
   famille, sur une valeur inconnue de `shop` (→ Autres commerces) et sur un `amenity` hors
   liste (→ null).

2. Réglage — `showShops` (nom à ta convenance) dans DisplayPreferences / PreferencesRepository,
   faux par défaut, bascule « Commerces et services » dans Réglages → couches, à côté des trois
   existantes, chaînes dans strings_settings.xml en anglais et en français.

3. Style — dans les DEUX feuilles, deux couches symbol sur la source `motis`, couche `pois` :
   `poi-shops-icons` (minzoom 16, `icon-image` par famille via une expression `match` sur
   `shop`/`amenity`/`tourism`, `icon-size` modeste, couleur de `colors_map.xml` en clair et en
   sombre, `symbol-sort-key` supérieur à celui des arrêts pour passer dessous) et
   `poi-shops-labels` (minzoom 17, `text-field` name, `text-optional` true, taille inférieure aux
   libellés d'arrêts). Filtre = exactement les valeurs du tableau § 2.2 ; les valeurs qui sont
   déjà dans `poi-landmarks` (pharmacie, hôpital, clinique…) ne doivent PAS apparaître deux fois :
   retire-les d'un côté ou de l'autre en justifiant. Visibilité pilotée par le réglage comme
   `pointsOfInterestVisible` pilote `poi-landmarks` (MapCanvas.kt ~l. 226).

4. Fiche — étends `MapInstance.tapAt` / `MapTap` avec un `OnShop` portant nom, valeurs OSM,
   `cuisine`, `atm`, `religion`, `denomination`, `housenumber`, position. MapViewModel ouvre
   une `MapShopCard` (calque de MapStopCard : mêmes marges, même comportement à 200 % de texte,
   même fermeture) et lance UNE requête de géocodage inverse via GeocodeRepository, annulée si
   la fiche se ferme avant la réponse ; la ligne d'adresse montre le numéro de la tuile en
   attendant, puis l'adresse rendue, et rien si les deux manquent. Boutons « Partir d'ici » /
   « Aller ici » par MapSelection, comme l'appui long. Un appui sur un arrêt ou un point de
   libre-service garde la priorité sur un commerce au même endroit.

5. Chaînes — strings_map.xml en anglais et en français : les sept familles, une chaîne par
   valeur retenue de `shop`/`amenity`/`tourism` (nommée d'après la valeur OSM), les compléments
   « · distributeur », « · %s » pour la cuisine, et les libellés de la fiche. Aucune chaîne en
   dur, y compris pour les noms de sprites, qui vont dans une constante Kotlin documentée.

6. Tests — :core comme au point 1 ; MapStyles : les deux feuilles restent du JSON valide et
   contiennent les deux couches avec les bons minzoom (étends le test existant) ; MapViewModel :
   l'ouverture d'une fiche déclenche une requête et une seule, la fermeture l'annule (faux
   dépôt) ; test Compose de la fiche avec et sans nom, avec et sans adresse.

Interdits : journaliser un nom, une position ou une adresse ; toute requête autre que le
géocodage inverse de la fiche ; recréer la carte. `test`, `ktlintCheck`, `detekt`, `lint` sans
avertissement. Rapport : pictogrammes choisis et manquants, chaînes ajoutées, et la liste de ce
que le superviseur doit regarder au téléphone.
```

### Lot 2 — Recette

```
Tu es le superviseur (skill android-supervision, puis android-test et android-ui). Le lot 1 est
sur sa branche, compilé et testé. Tu as le téléphone.

1. installDebug, puis réglage désactivé : la carte est strictement identique à avant, à tous
   les zooms. Capture au zoom 15, 16, 17 sur une rue commerçante d'une ville dense (Lyon,
   Presqu'île) et sur une ville moyenne (Bourg-en-Bresse, centre), en clair et en sombre.
2. Réglage activé : au zoom 15 rien de plus ; à 16 les pictogrammes, lisibles mais discrets,
   toujours sous les arrêts ; à 17 les noms, qui s'effacent avant les pictogrammes quand ils se
   chevauchent. Aucune image perdue en zoomant de 14 à 18 (règle 6 de § 5.7, observe au doigt et
   au profileur GPU si un doute).
3. Fiche : appui sur une boulangerie, un restaurant avec cuisine, une banque avec distributeur,
   un commerce sans nom ; l'adresse arrive après le numéro ; fermer la fiche avant la réponse
   ne déclenche rien après coup (journal réseau) ; « Aller ici » remplit bien l'arrivée ; texte
   à 200 % ; rotation fiche ouverte ; mode avion : la fiche s'ouvre sans adresse et sans erreur
   bruyante.
4. Recherche : « boulangerie », « carrefour », « pharmacie » près du centre de la carte donnent
   des commerces ; rien n'a changé dans la composition de la liste.
5. releaseTest : « Fully drawn » inchangé sur trois essais, quatre APK sous 15 Mo.
6. Traduction : skill android-traduction, écran par écran, anglais et français.
7. Chaque anomalie devient un prompt de correction ; quand tout est vert, fusion dans main en
   avance rapide, poussée, changelog fastlane en/fr.
```
