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
strings_map.xml         carte
strings_search.xml      recherche et autocomplétion
strings_results.xml     liste de résultats
strings_detail.xml      détail d'un trajet
strings_departures.xml  prochains départs
strings_settings.xml    réglages, dont l'écran « Serveur MOTIS »
strings_favorites.xml   favoris et historique
strings_alerts.xml      perturbations
```

Android fusionne tous les fichiers `res/values/*.xml` : le découpage n'a aucun effet à l'exécution.

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

### Vérifier une traduction

```bash
./gradlew lint     # signale les chaînes non traduites et les paramètres de format incohérents
```

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
