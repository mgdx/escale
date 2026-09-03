# Contribuer à Escale

Merci de vouloir aider. Ce document dit comment le code est écrit et comment ajouter une langue.

## Avant de commencer

Trois documents font autorité, dans cet ordre :

1. [`SPEC.md`](SPEC.md) — le cahier des charges. En cas de contradiction avec le code, c'est la spec
   qui a raison ; c'est le code qui se corrige, ou bien la spec qui s'amende explicitement.
2. [`docs/architecture.md`](docs/architecture.md) — où va quoi, et sous quel nom. Modules, paquets,
   types de domaine, interfaces de dépôt.
3. [`CLAUDE.md`](CLAUDE.md) — l'outillage : commandes, vérification sur appareil, méthode.

Une idée hors périmètre de la spec se propose dans une issue ; elle ne s'implémente pas d'abord.

## Conventions de code

- **Kotlin**, Jetpack Compose, Material 3. Deux espaces d'indentation, style ktlint `intellij_idea`,
  lignes de 120 caractères au plus. Le fichier [`.editorconfig`](.editorconfig) fait foi et votre
  éditeur devrait le lire tout seul.
- **Commentaires en français, noms de symboles en anglais.** Un commentaire dit *pourquoi*, pas
  *quoi* : le code dit déjà ce qu'il fait.
- **Trois modules.** Une règle métier testable sans Android va dans `:core`. De la logique dans un
  composable est une erreur de placement, pas un raccourci.
- **Injection de dépendances manuelle.** Un `AppContainer` construit dans la classe `Application`,
  des `ViewModel` alimentés par une fabrique écrite à la main. Hilt, Dagger et Koin sont exclus.
- **Aucune chaîne codée en dur**, y compris dans les `contentDescription` et les aperçus.
- Les DTO de l'API restent dans `:data` et ne remontent jamais dans l'interface. `ignoreUnknownKeys`
  est obligatoire : l'API MOTIS ajoute des champs sans préavis.
- Le temps se manipule en `java.time`. Ni `kotlinx-datetime`, ni `kotlin.time.Duration`.

### Ajouter une bibliothèque

`gradle/libs.versions.toml` déclare d'avance toutes les bibliothèques prévues par la spec. Si celle
qu'il vous faut y est déjà, ajoutez-la simplement aux dépendances de votre module. Si elle n'y est
pas, **ouvrez une issue avant d'écrire du code** : chaque dépendance est un engagement de taille
d'APK, de maintenance et de conformité F-Droid.

Sont exclues d'office : toute bibliothèque non libre, tout service Google Play, tout SDK d'analyse,
tout traqueur, tout rapporteur de plantage automatique.

### Avant d'ouvrir une pull request

```bash
./gradlew assembleDebug
./gradlew test
./gradlew ktlintCheck detekt
./gradlew lint
```

Les quatre doivent passer **sans le moindre avertissement**. Un avertissement n'est pas un détail à
traiter plus tard : soit il se corrige, soit il se supprime explicitement avec un commentaire qui
explique pourquoi il est légitime.

L'intégration continue rejoue ces quatre commandes sur chaque poussée et chaque pull request
([`.github/workflows/ci.yml`](.github/workflows/ci.yml)), et y ajoute
`./gradlew :app:assembleRelease`. Cette dernière est la seule à faire tourner R8, donc la seule à pouvoir signaler une règle de
conservation devenue fausse ou un APK sorti des 15 Mo par architecture. Si vous touchez aux
dépendances, aux ressources ou à `app/src/main/keepRules/`, lancez-la aussi en local : son échec ne
se voit nulle part ailleurs.

Vérifiez également le rendu sur un appareil ou un émulateur, en **thème clair et en thème sombre**,
après rotation, et avec la taille de texte poussée à 200 %.

### Commits

Un commit par changement cohérent, message en français, à l'impératif :
« Ajouter le sélecteur d'heure », « Corriger le décodage des polylignes en précision 7 ».
Pas de commit fourre-tout en fin de session, et jamais un état qui ne compile pas.

## Traduire l'application

L'interface est écrite en anglais dans `app/src/main/res/values/`, et traduite en français dans
`app/src/main/res/values-fr/`. Les autres langues sont les bienvenues.

### Où sont les chaînes

Les chaînes sont éclatées par écran, pour que plusieurs contributions ne se gênent pas :

```
strings.xml             commun : nom, accroche, actions génériques, erreurs générales
strings_map.xml         écran d'accueil et carte
strings_search.xml      recherche et autocomplétion
strings_results.xml     liste de résultats, libellés de mode, retards et perturbations
strings_detail.xml      détail d'un trajet
strings_trip.xml        détail d'une course
strings_departures.xml  prochains départs
strings_settings.xml    réglages, dont l'écran « Serveur MOTIS »
strings_favorites.xml   favoris et historique
strings_about.xml       écran « À propos » et attributions
```

Android fusionne tous les fichiers `res/values/*.xml` : le découpage n'a aucun effet à l'exécution.

Les libellés de **mode, de durée, de retard et de suppression** vivent tous dans
`strings_results.xml`, et les autres écrans les réutilisent tels quels : un métro doit s'appeler
pareil dans l'infobulle de la carte, dans un résultat et sur un tableau de départs. N'en
redéclarez pas une copie locale dans le fichier de votre écran.

### Glossaire

Dix personnes ont écrit ces chaînes, sprint après sprint. Ce tableau existe pour qu'elles finissent
par dire la même chose. **Un terme source donne un seul terme cible, partout.** Une entrée ne se
change pas sans repasser sur toutes ses occurrences.

| Anglais | Français | Note |
|---|---|---|
| journey, trip | trajet | ce que l'usager cherche : un départ, une arrivée, des portions |
| itinerary | itinéraire | le tracé retenu ; jamais employé pour parler d'une proposition de la liste |
| leg | portion | un segment d'un trajet, d'un mode donné |
| trip *(API MOTIS)*, service | course | un passage précis d'un véhicule ; l'écran « Détail de la course » |
| transfer | correspondance | jamais « change » ni « connection » en anglais |
| stop | arrêt | vaut aussi pour une gare ou une station de métro |
| station *(libre-service)* | station | le point d'attache d'un véhicule partagé, jamais « borne » |
| platform | quai | jamais « voie », y compris pour un train, et jusque dans les commentaires |
| departure / arrival | départ / arrivée | |
| operator *(transport)* | transporteur | « Transporteur : X », qui n'a pas de genre |
| operator *(libre-service)* | exploitant | |
| rental, shared | libre-service, partagé | jamais « libre accès » |
| free-floating | sans station | s'oppose à « station » |
| service alert, disruption | perturbation | en anglais toujours « service alert » |
| cancelled *(course, arrêt)* | supprimé | un arrêt sauté se dit « non desservi » / « stop skipped » |
| delay | retard | |
| refresh | actualiser | jamais « rafraîchir » : « Actualiser » est le verbe qu'emploie Android en français |
| scheduled *(heure)* | prévu à | « Prévu à 8:12 » ; « théorique » reste au code et aux commentaires, jamais à l'écran |
| metro | métro | jamais « underground » ni « subway » en anglais |
| regional train | train régional | jamais « TER », qui est une marque SNCF |
| moped | cyclomoteur | GBFS `moped` ; « scooter » désignerait aussi la trottinette |
| standing scooter | trottinette | GBFS `scooter_standing` |
| settings | réglages | Android français dit « Paramètres » ; le projet garde « Réglages », c'est un choix assumé et constant sur les sept écrans concernés, pas un oubli : ne le « corrigez » pas |

### Ce que le lecteur d'écran entend

Les libellés d'accessibilité obéissent aux mêmes règles de vocabulaire que le reste, plus trois qui
leur sont propres. Elles ont été payées deux fois, ne les redécouvrez pas :

- **Un `onClickLabel` est un verbe et son objet, jamais une phrase.** Le système annonce déjà
  « Appuyer deux fois pour… » : l'étiquette complète cette phrase-là. « Afficher le détail », pas
  « Afficher le détail de cette portion ».
- **Une annonce principale emploie le mot de l'usager, pas celui du code.** La description d'une
  carte de trajet s'ouvre sur « Trajet&nbsp;: … » ; « Portions&nbsp;: … » est du vocabulaire interne
  et ne dit rien à qui écoute. Le mot « portion » reste juste dans le code et dans ce glossaire.
- **Se taire plutôt que se taire longuement.** Une valeur qui n'apprend rien ne s'annonce pas :
  une perturbation de gravité inconnue n'affiche aucune ligne de gravité, plutôt que
  « Gravité&nbsp;: perturbation ».
- **Une phrase qui décrit un geste nomme aussi l'action d'accessibilité** qui en tient lieu, dès
  qu'il en existe une : un balayage n'est jamais la seule voie d'accès, et l'aide ne doit pas
  laisser croire le contraire.

**Registre.** L'application vouvoie. Un bouton ou une entrée de liste est à l'**infinitif**
(« Ajouter un lieu », « Rétablir le serveur par défaut ») ; une phrase adressée à l'usager est à
l'**impératif de politesse** (« Réessayez plus tard. », « Saisissez la racine du serveur. »). Les
titres portent une majuscule au premier mot seulement — « Prochains départs », jamais « Prochains
Départs ». Pas de point final sur un libellé court ; point final sur une phrase.

### Typographie française

Deux conventions, tenues sans exception dans `values-fr/` :

- **Espace insécable** avant `: ; ? !` et à l'intérieur des guillemets `« … »`. Dans un fichier de
  ressources Android elle s'écrit **`&#160;`**, en entité XML plutôt qu'en caractère invisible :
  `<string name="results_alert_severity">Gravité&#160;: %1$s</string>`.
- **Apostrophe droite échappée `\'`**, jamais l'apostrophe typographique `’`. Les deux se valent en
  soi ; c'est le mélange qui se voit. Le dossier emploie la première partout.

Ces règles valent pour le français seul. L'anglais de `values/` emploie les guillemets courbes
`“ … ”` et l'orthographe britannique (*favourites*, *centred*, *anticlockwise*, *lift*).

### Ajouter une langue

1. Créez `app/src/main/res/values-<code>/` — par exemple `values-de/` pour l'allemand, ou
   `values-pt-rBR/` pour le portugais du Brésil.
2. Copiez-y les fichiers `strings*.xml` que vous traduisez et remplacez le contenu des balises.
   Ne traduisez que les chaînes ; ne renommez jamais un attribut `name`.
3. Laissez de côté les chaînes marquées `translatable="false"` : ce sont des noms propres.
4. Respectez les `<plurals>` de votre langue. Le nombre de formes varie : l'anglais en a deux, le
   français deux, le polonais quatre, l'arabe six. Renseignez celles que votre langue utilise.
5. Conservez les paramètres de format (`%1$s`, `%2$d`) et leur numéro. Vous pouvez les réordonner
   dans la phrase, c'est précisément à cela que sert leur numérotation.
6. Échappez l'apostrophe : `\'` dans un fichier de ressources Android.
7. **Ajoutez votre langue à [`app/src/main/res/xml/locales_config.xml`](app/src/main/res/xml/locales_config.xml)**,
   une ligne `<locale android:name="de" />` par langue, dans le même format que le nom du dossier
   (`pt-rBR` s'y écrit `pt-BR`). C'est ce fichier, et lui seul, qui remplit l'écran « Langue de
   l'application » d'Android 13 et des versions suivantes : une traduction absente de cette liste
   n'y est jamais proposée, et reste inatteignable autrement qu'en changeant la langue de tout
   l'appareil.
8. Si vous le souhaitez, traduisez aussi la **fiche F-Droid** : copiez
   `fastlane/metadata/android/fr-FR/` sous le code de votre langue au format fastlane (`de-DE`,
   `pt-BR` — pas `values-de` ni `values-pt-rBR`) et traduisez les quatre fichiers texte. Attention,
   ces fichiers ont des limites de longueur que rien ne signale : elles sont expliquées, avec la
   façon de les vérifier, dans [`fastlane/README.md`](fastlane/README.md). C'est facultatif : une
   langue sans fiche traduite retombe simplement sur l'anglais.

### Vérifier une traduction

```bash
./gradlew lint     # signale les chaînes non traduites et les paramètres de format incohérents
```

Les règles qui comptent ici sont `MissingTranslation`, `ExtraTranslation`, `StringFormatInvalid`,
`ImpliedQuantity` et `Typos`. **Si l'une se déclenche, corrigez-la ; ne la désactivez pas.**

Deux défauts que `lint` attrape mal, et qui ne se voient qu'à l'exécution, chez l'usager :

- **Le nombre et le type des paramètres doivent concorder** entre `values/` et votre langue. Une
  chaîne qui gagne ou perd un `%2$s` en traduction lève une `IllegalFormatException` au moment où
  l'écran s'affiche, sans que rien n'ait échoué à la compilation.
- **Les catégories de `<plurals>` sont celles de votre langue, pas celles de l'anglais.** En
  français, `one` couvre 0 **et** 1 — « 0 correspondance » — là où l'anglais range 0 dans `other`.
  La catégorie `many` du français ne vaut que pour les millions et sert la construction
  « 2 millions **de** X » : elle ne s'écrit avec « de » que si le nombre est en toutes lettres.
  Nos pluriels sont rendus avec `%d`, donc `many` y reprend mot pour mot `other`.

Aucun `resourceConfigurations` ni `localeFilters` n'est déclaré dans `app/build.gradle.kts`, et il
ne faut pas en ajouter : ces réglages *filtrent* les langues embarquées au lieu de les protéger, et
une liste oubliée ferait disparaître silencieusement une traduction du paquet. Ce qui restreint la
liste offerte à l'usager est `locales_config.xml`, pas la configuration de compilation.

Puis regardez vos écrans sur un appareil réglé dans votre langue. Une traduction correcte qui
déborde de son bouton reste une traduction à revoir : préférez une formulation courte, dans le
vocabulaire qu'emploient déjà le système Android et les applications de transport de votre pays.

## Signaler un bug

Indiquez la version d'Escale, la version d'Android, le serveur MOTIS utilisé, et les étapes exactes.
**N'incluez ni adresse personnelle ni coordonnée réelle** dans un rapport public : une gare voisine
suffit à reproduire la plupart des problèmes.
