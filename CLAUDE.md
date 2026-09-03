# CLAUDE.md

Instructions pour tout agent Claude travaillant dans ce dépôt.

## Le projet

**Escale** est un client Android d'un serveur MOTIS : recherche d'itinéraire multimodale, détail
des trajets, carte, temps réel. Kotlin, Jetpack Compose, GPLv3, destiné à F-Droid.

**`SPEC.md` fait autorité.** Lis-le en entier avant de toucher au code. En cas de contradiction
entre le code et la spec, c'est la spec qui a raison. Si la spec est muette ou ambiguë sur un point
qui bloque, ne tranche pas seul : pose la question, et si la réponse fait jurisprudence,
propose l'amendement de la spec dans le même commit que le code.

## Commandes

```bash
./gradlew assembleDebug            # compiler
./gradlew assembleRelease          # compiler la publication minifiée (R8), non signée : quatre APK
./gradlew :app:assembleRelease -Pabi=arm64-v8a   # la même, mais une seule architecture (docs/fdroid.md § 6)
./gradlew test                     # tests JVM (:core, :data)
./gradlew connectedAndroidTest     # tests instrumentés (appareil branché requis)
./gradlew ktlintCheck detekt lint  # qualité
./gradlew installDebug             # compiler et installer l'APK de l'ABI de l'appareil branché
```

**Aucun avertissement n'est toléré** sur `test`, `lint`, `ktlintCheck`, `detekt`. Un avertissement
n'est pas un détail à traiter plus tard : soit tu le corriges, soit tu expliques pourquoi il est
légitime et tu le supprimes explicitement avec un commentaire.

## Vérifier sur un vrai téléphone

Un appareil Android est branché en USB, débogage activé, écran déverrouillé. **Tu es autorisé et
attendu à t'en servir** : rien n'est considéré comme terminé tant que ce n'est pas vu à l'écran.

```bash
adb devices                                   # vérifier la présence de l'appareil
# L'APK est découpé par architecture (un fichier par ABI, pas d'APK universel) : il n'y a plus
# d'app-debug.apk. `adb shell getprop ro.product.cpu.abi` dit laquelle prendre :
adb install -r app/build/outputs/apk/debug/app-arm64-v8a-debug.apk    # téléphone 64 bits, le cas courant
adb install -r app/build/outputs/apk/debug/app-armeabi-v7a-debug.apk  # téléphone 32 bits
adb install -r app/build/outputs/apk/debug/app-x86_64-debug.apk       # émulateur
adb install -r app/build/outputs/apk/debug/app-x86-debug.apk          # émulateur 32 bits
# Au doute, `./gradlew installDebug` choisit tout seul le bon fichier.
adb shell am start -n io.github.mgdx.escale/.MainActivity
adb exec-out screencap -p > /tmp/ecran.png    # capture à regarder avec l'outil de lecture d'image
adb logcat -c && adb logcat -s Escale:* AndroidRuntime:E   # journal filtré
adb shell pm clear io.github.mgdx.escale      # repartir d'un état vierge
```

Boucle de travail attendue : écrire, compiler, installer, **prendre une capture et la regarder**,
corriger, recommencer. Une capture d'écran vaut mieux qu'une supposition sur le rendu. Pour un
écran nouveau ou retouché, vérifie systématiquement : mode clair, mode sombre, rotation, texte
agrandi, état vide, état d'erreur.

Ne modifie jamais les réglages du téléphone au-delà de ce que l'application demande, ne désinstalle
pas d'autres applications, et n'utilise pas l'appareil pour autre chose que ce projet.

## Mesurer la publication

Les objectifs de SPEC.md § 2 (moins de 15 Mo par APK) et § 5.7 (démarrage à froid sous 1,5 s)
**ne se mesurent que sur la publication minifiée**. L'APK de débogage n'est ni minifié ni optimisé
et embarque l'outillage Compose : il pèse le double et démarre plusieurs fois plus lentement.
Une mesure faite dessus ne dit rien.

`assembleRelease` produit des APK **non signés** — c'est voulu, F-Droid signe lui-même et aucune
clé n'entre dans le dépôt. Pour disposer quand même d'une publication installable, il existe un
type de compilation `releaseTest` : **même configuration R8 que `release`**, signé avec la clé de
débogage du SDK (`~/.android/debug.keystore`, hors du dépôt) et `profileable`. Il ne se publie
jamais.

```bash
# Compiler et installer la publication minifiée sur l'appareil branché (choisit la bonne ABI).
./gradlew :app:installReleaseTest

# Ou installer un fichier précis :
adb install -r app/build/outputs/apk/releaseTest/app-arm64-v8a-releaseTest.apk

# Démarrage à froid, trois essais. `-S` tue le processus avant de le relancer : sans lui, on mesure
# un démarrage tiède. `TotalTime` est le délai jusqu'à la première image de **l'activité**.
adb shell am force-stop io.github.mgdx.escale
for i in 1 2 3; do
  adb shell am start -W -S -n io.github.mgdx.escale/.MainActivity | grep TotalTime
  sleep 3
done
```

**`TotalTime` n'est pas ce que mesure le § 5.7.** La spec vise le délai « jusqu'à la première image
de carte », et MapLibre s'initialise après la première image de l'activité. Le délai que la spec
demande est celui que le système journalise sous le nom de *fully drawn*, à condition que
l'application appelle `reportFullyDrawn()` au bon moment — c'est-à-dire quand MapLibre a rendu sa
première image, pas avant :

```bash
adb logcat -c
adb shell am force-stop io.github.mgdx.escale
adb shell am start -W -S -n io.github.mgdx.escale/.MainActivity
adb logcat -d | grep -i "Fully drawn"      # « Fully drawn io.github.mgdx.escale/.MainActivity: +1s042ms »
```

Le premier chiffre à comparer aux 1,5 s de la spec est celui-là. Mesurer toujours **appareil
déverrouillé, application déjà lancée une première fois** (le profil ART embarqué n'est installé
qu'après le premier lancement) et sur trois essais au moins.

Vérifier la taille, qui est contrôlée automatiquement à chaque `assembleRelease` :

```bash
./gradlew :app:assembleRelease     # échoue si un APK dépasse 15 Mo, ou si R8 a renommé une classe
ls -l app/build/outputs/apk/release/   # sur laquelle une bibliothèque compte par son nom
```

**La propriété `-Pabi`** restreint la compilation à **une seule** architecture, et n'existe que
pour la recette de compilation de F-Droid, qui refuse de trouver plus d'un APK à la fois
(docs/fdroid.md § 6). Sans elle, rien ne change : les quatre APK sont produits comme avant, et
toutes les commandes ci-dessus restent valables telles quelles. Elle ne touche jamais au
`versionCode`, qui reste celui du rang de l'ABI dans les deux modes — c'est ce que vérifie la tâche
`verifyReleaseVersionCodes`, qui finalise `assembleRelease`. Une valeur inconnue échoue en nommant
les quatre valeurs acceptées.

```bash
./gradlew :app:assembleRelease -Pabi=arm64-v8a   # armeabi-v7a, arm64-v8a, x86 ou x86_64
```

## Skills à utiliser

Ce dépôt bénéficie de skills dédiés. Invoque-les au lieu d'improviser :

| Quand | Skill |
|---|---|
| Nouvel écran, rendu qui ne va pas, thème, accessibilité visuelle | `android-ui` |
| Chasse aux bugs, cycle de vie, rotation, réseau coupé, permissions révoquées | `android-test` |
| Revue de sécurité avant publication, secrets, TLS, composants exportés | `android-securite` |
| Ajout ou correction d'une langue, chaînes qui traînent en dur | `android-traduction` |
| Vérifier que l'appli passerait la revue F-Droid | `android-fdroid` |
| Travail trop gros pour une session, à découper entre plusieurs agents | `android-supervision` |

## Conventions de code

- **Kotlin**, Compose, Material 3. Deux espaces d'indentation, conventions ktlint par défaut.
- **Trois modules** : `:core` (Kotlin pur, aucun import `android.*`), `:data` (réseau, Room),
  `:app` (interface). Une règle métier testable en JVM va dans `:core`. Si tu écris de la logique
  dans un composable, tu t'es trompé d'endroit.
- **Injection de dépendances manuelle uniquement.** Un `AppContainer` créé dans la classe
  `Application`, des `ViewModel` alimentés par une factory écrite à la main. **N'ajoute jamais
  Hilt, Dagger ou Koin**, même si c'est ce que suggère le modèle de code que tu as en tête.
- **Aucune chaîne codée en dur.** Tout dans `strings.xml`, pluriels via `<plurals>`. Anglais dans
  `values/`, français dans `values-fr/`. Une chaîne ajoutée en anglais sans sa traduction française
  est un travail incomplet.
- Les DTO de l'API restent dans `:data` et ne remontent jamais dans l'interface. `ignoreUnknownKeys`
  est obligatoire : l'API MOTIS ajoute des champs sans préavis.
- Commentaires en français, noms de symboles en anglais.

## À ne jamais faire sans demander

- Ajouter une dépendance. Chaque bibliothèque est un engagement de taille d'APK, de maintenance et
  de conformité F-Droid. Propose, argumente, attends la réponse.
- Toucher à `applicationId`, il est figé (`io.github.mgdx.escale`) et immuable une fois l'appli
  publiée sur F-Droid.
- Introduire un service Google Play, un SDK d'analyse, un traqueur, un rapport de plantage
  automatique, ou toute dépendance non libre. C'est rédhibitoire pour le projet.
- Ajouter une permission au manifeste.
- Mettre en place une tâche de fond, un service, une synchronisation ou du polling. La seule
  exception autorisée est décrite au § 5.5.1 de la spec.
- Élargir le périmètre. Une idée en dehors de la spec se note dans une issue, elle ne s'implémente pas.
- Journaliser une adresse, une coordonnée ou une requête utilisateur, même en débogage.

## Git

- Une branche par lot de travail, nommée `jalon-N-sujet` ou `fix/sujet`.
- Commits en français, à l'impératif, un commit par changement cohérent. Pas de commit fourre-tout
  en fin de session.
- Ne committe jamais un état qui ne compile pas ou dont les tests échouent.
- Ne pousse pas sur `main` directement.
- Ne modifie pas `SPEC.md` de ta propre initiative : propose la modification, elle se valide à part.

## Terminé, ça veut dire quoi

Une tâche est finie quand tout ceci est vrai :

1. Le code compile, `test`, `ktlintCheck`, `detekt` et `lint` passent sans avertissement.
2. Les nouvelles règles de `:core` sont couvertes par des tests JVM.
3. Le comportement a été **vu sur le téléphone**, en clair et en sombre.
4. Aucune chaîne en dur, français et anglais à jour.
5. Le résultat correspond à ce que dit la spec, pas à une interprétation commode.

Si l'un des cinq manque, dis-le explicitement plutôt que d'annoncer que c'est terminé.
