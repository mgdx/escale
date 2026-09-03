# Conformité F-Droid — état du dossier

Ce document sert à **une** chose : permettre au mainteneur humain de déposer la demande d'inclusion
d'Escale au dépôt principal de F-Droid sans avoir à tout revérifier lui-même. Il dit ce qui a été
vérifié, **comment**, et il dit aussi ce qui ne l'a pas été.

Établi le 3 septembre 2026, sur la branche `main` à la version `1.0.0` (`baseVersionCode` 1).

## En un coup d'œil

| | |
|---|---|
| Licence, dépendances, permissions, vie privée | **Conformes.** Rien à corriger. |
| Anti-features à déclarer | **Aucun**, selon la lettre de la politique et le précédent Bimba (§ 5). À défendre dans la *merge request*. |
| Recette de compilation `fdroiddata` | **Un point bloquant, à traiter avant la demande** — voir ci-dessous. |
| Captures d'écran | À produire (§ 7). |
| Icônes de repli `mipmap-*` | À remplacer (§ 7). |

> ### Le point bloquant : quatre APK, un seul attendu
>
> `fdroid build` **échoue si `assembleRelease` laisse plus d'un APK** dans
> `build/outputs/apk/release/` : `fdroidserver/build.py` lève alors
> `BuildException('More than one resulting apks found in …')`. Or `app/build.gradle.kts` active
> `splits { abi { … } }` et en produit **quatre**. Ce n'est pas un défaut du projet — F-Droid
> encourage explicitement le découpage par ABI — mais **sa recette de compilation ne se déduit pas
> de la configuration Gradle actuelle**, et le sujet est traité au § 6.

## Ce qui a été vérifié, et ce qui ne l'a pas été

**Vérifié en compilant et en lisant les artefacts produits**, pas en relisant la spec :

- `./gradlew :app:assembleRelease` a été exécuté, et il réussit. Les quatre APK et le manifeste
  fusionné de publication ont été ouverts et lus ; le `mapping.txt` de R8 a été contrôlé par la
  tâche `verifyReleaseKeepRules`, qui fait partie de la compilation.
- Les licences de chaque dépendance sont lues dans le **POM Maven** de l'artefact réellement
  résolu, et non dans la documentation de la bibliothèque.
- La composition des APK (`unzip -l`) et la liste des permissions du manifeste fusionné de
  publication ont été relevées sur le fichier produit.

**Non vérifié, et il faut le savoir :**

- **Rien n'a été observé sur un appareil.** L'auteur de ce document n'avait pas accès à `adb`. Le
  comportement à l'exécution — un `logcat` propre pendant un usage réel, la liste des connexions
  réseau effectivement ouvertes, le rendu de l'icône dans le lanceur — reste à confirmer par
  quelqu'un qui a le téléphone en main. Les points concernés sont signalés au fil du texte.
- La demande n'a pas été soumise, et aucun relecteur F-Droid n'a donné son avis. Les conclusions
  ci-dessous sont argumentées, pas validées.

---

## 1. Licence

| Point | État |
|---|---|
| Licence | **GNU GPL v3 ou ultérieure** |
| `LICENSE` à la racine | Oui, texte complet et non modifié de la GPLv3 |
| Annoncée dans le `README` | Oui |
| Annoncée dans l'application | Oui, écran « À propos » (SPEC.md § 5.6) |
| Texte complet embarqué dans l'APK | Oui, `app/src/main/res/raw/gpl_3_0.txt`, lisible depuis l'écran « À propos » sans connexion |
| Champ `License:` de `fdroiddata` | `GPL-3.0-or-later` (identifiant SPDX) |

**Point à trancher par le mainteneur : les en-têtes de fichier.** Aucun des 366 fichiers `.kt` du
dépôt ne porte d'en-tête de licence ni de ligne `SPDX-License-Identifier`. Ce n'est **pas** un
obstacle à l'inclusion : F-Droid demande un fichier de licence identifiable et une licence libre,
pas un en-tête par fichier. C'est en revanche ce que recommandent la FSF et la spécification REUSE,
et l'absence d'en-tête rend un fichier isolé, extrait du dépôt, juridiquement muet. Si le choix est
de les ajouter, c'est un changement mécanique à faire **avant** la première publication, sur les
366 fichiers d'un coup, et pas au fil de l'eau.

Les éléments graphiques suivent la licence du dépôt : `docs/icone.md` le dit explicitement, et
l'icône est un fichier vectoriel du dépôt, sans dépendance externe.

---

## 2. Dépendances

Toutes sont résolues depuis **Maven Central** et **Google Maven** (`settings.gradle.kts`), les deux
dépôts publics usuels. Aucun JitPack, aucun dépôt privé, aucun artefact déposé à la main dans le
dépôt.

La liste ci-dessous est celle du `releaseRuntimeClasspath` — c'est-à-dire **ce qui entre réellement
dans l'APK publié**. La licence de chaque ligne vient du POM Maven de l'artefact résolu.

### Ce qui est embarqué dans l'APK

| Artefact | Origine | Licence |
|---|---|---|
| `androidx.*` — activity, annotation, arch.core, autofill, collection, compose.\*, concurrent, core, customview, datastore, documentfile, dynamicanimation, emoji2, fragment, graphics, interpolator, legacy, lifecycle, loader, localbroadcastmanager, navigation, navigationevent, print, profileinstaller, room, savedstate, sqlite, startup, tracing, transition, versionedparcelable, viewpager, window, work | Google Maven | **Apache-2.0** |
| `androidx.datastore:datastore-preferences-external-protobuf` | Google Maven | **BSD-3-Clause** (protobuf-javalite reconditionné) |
| `org.maplibre.gl:android-sdk-opengl` | Maven Central | **BSD-2-Clause** |
| `org.maplibre.gl:maplibre-android-gestures` | Maven Central | **BSD-2-Clause** |
| `org.maplibre.gl:android-sdk-geojson`, `android-sdk-turf` | Maven Central | **Apache-2.0** |
| `io.ktor:*` — client-core, client-okhttp, client-content-negotiation, serialization-kotlinx-json, http, io, network, events, utils, sse, websockets, websocket-serialization | Maven Central | **Apache-2.0** |
| `com.squareup.okhttp3:okhttp`, `okhttp-android` | Maven Central | **Apache-2.0** |
| `com.squareup.okio:okio`, `okio-jvm` | Maven Central | **Apache-2.0** |
| `org.jetbrains.kotlin:kotlin-stdlib` | Maven Central | **Apache-2.0** |
| `org.jetbrains.kotlinx:kotlinx-coroutines-core`, `-android`, `-slf4j` | Maven Central | **Apache-2.0** |
| `org.jetbrains.kotlinx:kotlinx-serialization-core`, `-json`, `-json-io` | Maven Central | **Apache-2.0** |
| `org.jetbrains.kotlinx:kotlinx-io-core`, `-bytestring` | Maven Central | **Apache-2.0** |
| `org.jetbrains:annotations` | Maven Central | **Apache-2.0** |
| `org.jspecify:jspecify` | Maven Central | **Apache-2.0** |
| `com.google.code.gson:gson` — transitive de MapLibre | Maven Central | **Apache-2.0** |
| `com.google.guava:listenablefuture` — transitive d'`androidx.work` | Maven Central | **Apache-2.0** (son POM ne déclare pas de `<licenses>` ; la licence est celle du projet Guava, dont il est un extrait) |
| `com.jakewharton.timber:timber` — transitive de MapLibre | Maven Central | **Apache-2.0** |
| `org.slf4j:slf4j-api` — transitive de Ktor | Maven Central | **MIT** (déclarée dans `slf4j-parent` et `slf4j-bom`, pas dans le POM de l'artefact lui-même) |

Aucune licence non libre, aucune licence à clause de non-commercialisation, aucune licence
propriétaire. Toutes sont compatibles avec la GPLv3 en tant que dépendances d'une œuvre GPLv3.

Les deux noms qui font sursauter à la lecture méritent d'être dits : **`com.google.code.gson` et
`com.google.guava` sont du logiciel libre sous Apache-2.0**, publié par Google, et n'ont aucun
rapport avec les services Google Play. Aucun artefact `com.google.android.gms`,
`com.google.firebase` ni `play-services-*` n'apparaît nulle part dans le graphe de dépendances.

### Ce qui n'est pas embarqué

- **Tests** : `junit` (EPL-1.0), `org.robolectric` (MIT), `app.cash.turbine` (Apache-2.0),
  `io.ktor:ktor-client-mock` (Apache-2.0), `androidx.test.*` et `androidx.test.espresso.*`
  (Apache-2.0). EPL-1.0 est libre ; JUnit ne quitte pas la phase de test.
- **Compilation** : Android Gradle Plugin et greffons Kotlin/KSP (Apache-2.0),
  `com.pinterest.ktlint` (**MIT**), `io.gitlab.arturbosch.detekt` (**Apache-2.0**).

### Bibliothèques natives et « blobs »

Trois fichiers `.so` entrent dans l'APK. Relevé sur `app-arm64-v8a-release-unsigned.apk` :

```
lib/arm64-v8a/libmaplibre.so                 10 844 248 o   org.maplibre.gl:android-sdk-opengl
lib/arm64-v8a/libandroidx.graphics.path.so       10 096 o   androidx.graphics:graphics-path
lib/arm64-v8a/libdatastore_shared_counter.so      7 784 o   androidx.datastore:datastore-core
```

Les trois viennent d'**AAR publiés sur Maven Central ou Google Maven**, aucun n'est versionné dans
ce dépôt. `git ls-files` ne trouve **qu'un seul** fichier binaire dans tout le dépôt :
`gradle/wrapper/gradle-wrapper.jar`, celui du wrapper Gradle, dont la somme de contrôle est
vérifiée à chaque passage de la CI (`validate-wrappers` de `gradle/actions/setup-gradle`).

`libmaplibre.so` pèse 10 Mo et personne ne le recompile pendant le travail de F-Droid : c'est le
moteur de rendu MapLibre Native, livré compilé par le projet MapLibre. La question mérite d'être
posée franchement — *un AAR Maven contenant des `.so` est-il une dépendance acceptable, ou un blob
binaire ?* — et elle a une réponse écrite. La
[politique d'inclusion](https://f-droid.org/docs/Inclusion_Policy/), § *Build Transparency and
Reproducibility*, dit :

> « Applications can download prebuilt FLOSS binaries with specific conditions from trusted Maven
> repositories. Those include Maven Central, Google Maven, OSS Sonatype, OSS JFrog, JitPack.io and
> Clojars. Those binaries must still be freely licensed, simply being included in one of those
> repositories is not enough. »

Le critère n'est donc pas « aucun binaire » mais « binaire librement licencié, depuis un dépôt de
confiance ». MapLibre Native est BSD-2-Clause et vient de Maven Central : les deux conditions sont
remplies.

Le mécanisme le confirme. Le scanner de `fdroidserver` traite bien un `.so` comme une erreur
fatale — mais **il ne scanne que l'arbre source extrait du dépôt git**, et il tourne **avant**
l'exécution de Gradle : les `.so` de l'AAR sont téléchargés après, dans le cache de dépendances,
que le scanner ne visite pas. Un `.so` versionné dans le dépôt ferait échouer la compilation ; un
`.so` à l'intérieur d'un AAR résolu depuis Maven Central est invisible pour lui. **Aucun
`scanignore:` n'est donc à prévoir.**

Deux précisions utiles :

- Le POM de `org.maplibre.gl:android-sdk-opengl:13.6.0` ne dépend d'aucun artefact
  `com.google.android.gms`. Ce n'est pas anodin : les branches 9.x de MapLibre embarquaient un
  moteur de localisation adossé aux services Google Play : c'est la raison pour laquelle les
  recettes F-Droid d'Element et de Delta Chat recompilent encore MapLibre depuis les sources, et
  non parce que l'AAR Maven serait interdit. La branche 13 n'a plus cette dépendance.
- `settings.gradle.kts` ne déclare que `mavenCentral()` et `google()`, tous deux dans la liste des
  dépôts autorisés en dur par `fdroidserver/scanner.py`. Une URL de dépôt hors de cette liste
  ferait échouer le scan ; il n'y en a aucune.

---

## 3. Permissions

Le manifeste fusionné de **publication** (`processReleaseManifest`) déclare exactement ceci.

### Les six permissions de plateforme

| Permission | Niveau | Origine | Justification |
|---|---|---|---|
| `INTERNET` | normal | Escale | Interroger le serveur MOTIS. Sans elle l'application ne fait rien. |
| `ACCESS_COARSE_LOCATION` | dangereuse | Escale | Facultative. Centrer la carte, proposer « Ma position » comme départ. Demandée **à l'usage**, au premier appui sur le bouton de position, jamais au démarrage. |
| `ACCESS_FINE_LOCATION` | dangereuse | Escale | Facultative. Demandée seulement si l'usager insiste pour un centrage précis, après la précédente. |
| `POST_NOTIFICATIONS` | dangereuse | Escale | Facultative. Demandée au moment où l'usager active sa **première** surveillance de trajet, jamais avant. |
| `ACCESS_NETWORK_STATE` | **normal** | MapLibre | Le `ConnectivityReceiver` de la bibliothèque appelle `getActiveNetworkInfo()` et lève une `SecurityException` sans elle. Accordée à l'installation, sans écran de consentement ; elle ne donne accès qu'à l'état « connecté ou non ». |
| `WAKE_LOCK` | **normal** | `androidx.work` | La bibliothèque tient l'appareil éveillé le temps d'exécuter la tâche de surveillance. Accordée à l'installation ; elle ne donne accès à aucune donnée. |

**L'application reste entièrement utilisable si les quatre permissions facultatives sont refusées.**
C'est une exigence de SPEC.md § 11, pas une intention.

### Deux permissions explicitement retirées — c'est un point fort du dossier

`androidx.work` déclare quatre permissions dans son propre manifeste. Deux sont retenues ci-dessus.
Les **deux autres sont retirées** du manifeste fusionné par `tools:node="remove"` :

- **`RECEIVE_BOOT_COMPLETED`** — permission de *démarrage automatique*. Elle sert au
  `RescheduleReceiver` d'`androidx.work`. Conséquence assumée, écrite dans la spec (§ 5.5.1) et
  annoncée à l'usager dans l'écran d'activation : après un redémarrage du téléphone, une
  surveillance ne se replanifie qu'à la prochaine ouverture de l'application, et une occurrence
  peut être manquée.
- **`FOREGROUND_SERVICE`** — permission de *service en arrière-plan*. Escale n'appelle jamais
  `setForeground`, et le service correspondant est désactivé dès la fusion des manifestes par une
  valeur booléenne redéfinie (`app/src/main/res/values/bools_watch.xml`).

`ACCESS_WIFI_STATE` et `<uses-feature android:name="android.hardware.wifi">`, apportées par
MapLibre qui ne s'en sert nulle part, sont retirées de la même manière. Le manifeste fusionné ne
contient **aucun `<uses-feature>`**.

Ne sont demandées, et ne doivent jamais l'être : aucune permission de stockage, de contacts,
d'appareil photo, de journal d'appels, ni `SCHEDULE_EXACT_ALARM`.

### Une septième entrée, qui n'est pas une permission Android

Le manifeste de publication déclare aussi :

```xml
<permission android:name="io.github.mgdx.escale.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
            android:protectionLevel="signature" />
```

C'est une permission **définie par l'application elle-même**, insérée automatiquement par
`androidx.core`, de niveau `signature` : seule une application signée avec la même clé peut la
détenir, c'est-à-dire aucune autre. Elle sert à `ContextCompat.registerReceiver` pour émuler
`RECEIVER_NOT_EXPORTED` sur les versions d'Android antérieures à 13. Elle n'ouvre aucun accès et ne
donne lieu à aucun écran de consentement. Elle est signalée ici parce qu'un relecteur qui compte les
lignes du manifeste en trouvera sept et non six, et qu'il vaut mieux que l'explication soit écrite
d'avance qu'improvisée.

### Composants exportés

| Composant | Origine | Protection |
|---|---|---|
| `io.github.mgdx.escale.MainActivity` | Escale | Point d'entrée du lanceur. Aucun `intent-filter` autre que `MAIN`/`LAUNCHER` : ni deep link, ni schéma personnalisé, ni surface d'attaque. |
| `androidx.work.impl.background.systemjob.SystemJobService` | `androidx.work` | `android.permission.BIND_JOB_SERVICE` |
| `androidx.work.impl.diagnostics.DiagnosticsReceiver` | `androidx.work` | `android.permission.DUMP` |
| `androidx.profileinstaller.ProfileInstallReceiver` | `androidx.profileinstaller` | `android.permission.DUMP` |

Aucun composant exporté sans protection. Aucun `ContentProvider` exporté, aucun `FileProvider`.

---

## 4. Vie privée

Ce que le projet affirme est dans `PRIVACY.md`. Ce qui suit dit **comment le vérifier soi-même**,
parce qu'une affirmation invérifiable ne vaut rien dans un dossier de conformité.

| Affirmation | Comment on la contrôle |
|---|---|
| Aucun traqueur, aucune régie, aucun service Google Play | `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` : aucun `com.google.android.gms`, aucun `com.google.firebase`, aucun `play-services-*`, aucun SDK d'analyse. Le graphe complet tient sur deux écrans et se relit à l'œil. |
| Aucune télémétrie, aucun rapport de plantage | Même liste : ni Sentry, ni ACRA, ni Crashlytics, ni Matomo. |
| Aucune journalisation de donnée de localisation | `grep -rn "android.util.Log\|Timber\.\|println(" app/src/main data/src/main core/src/main` ne renvoie **rien**. Le `Logging` du client Ktor n'est pas installé, et le code source le dit à l'endroit où il aurait pu l'être (`data/.../MotisTransport.kt`). |
| `allowBackup="false"` | Lu dans le manifeste fusionné de publication. `data_extraction_rules.xml` coupe en plus le transfert d'appareil à appareil, qu'Android 12 laisse actif malgré l'attribut. |
| Aucune adresse de service en dur autre que le serveur par défaut | Les feuilles de style de carte (`res/raw/map_style_*.json`) n'ont **aucune** URL en dur : leurs sources, glyphes et sprites pointent sur un jeton `__BASE_URL__` remplacé à l'exécution par le serveur configuré. Il n'existe donc **aucun serveur de tuiles tiers** : le fond de carte vient du même serveur MOTIS que les itinéraires. |
| Aucun identifiant transmis | Le seul en-tête ajouté est le `User-Agent` exigé par la politique de Transitous, construit depuis `BuildConfig` : `Escale/1.0.0 (+https://github.com/mgdx/escale)`. Aucun cookie, aucun jeton, aucun numéro d'installation. |

**Deux vérifications qui demandent un appareil, et qui n'ont pas été faites ici :**

1. **`adb logcat` pendant un usage réel.** L'affirmation « aucune ligne de code n'écrit d'adresse
   dans les journaux » est exacte pour le code d'Escale, et se vérifie par le `grep` ci-dessus. Elle
   ne dit rien de ce que **MapLibre** écrit de son côté : la bibliothèque journalise par
   `android.util.Log` et par Timber, qu'elle amène en dépendance. Rien n'indique qu'elle journalise
   une coordonnée saisie par l'usager, mais cela n'a pas été observé. Un `adb logcat` complet, sans
   filtre, pendant une recherche d'itinéraire, tranche la question en cinq minutes.
2. **La capture du trafic réseau.** La liste des hôtes réellement contactés pendant une session
   complète — qui devrait se réduire au seul serveur configuré — se relève avec un proxy ou
   `mitmproxy`. C'est ce que fait un testeur F-Droid ; autant l'avoir fait avant lui.

---

## 5. Anti-features

**Conclusion : aucun anti-feature à déclarer.** Elle demande à être argumentée, parce que c'est
la question sur laquelle un relecteur peut légitimement ne pas être d'accord.

Les dix clés valides sont celles du fichier [`config/antiFeatures.yml`][antifeatures] de
`fdroiddata` : `Ads`, `DisabledAlgorithm`, `KnownVuln`, `NoSourceSince`, `NonFreeAdd`,
`NonFreeAssets`, `NonFreeDep`, `NonFreeNet`, `TetheredNet`, `Tracking`.

### `NonFreeNet` — le vrai sujet

Définition officielle : « This app promotes or depends entirely on a **non-free** network service. »

L'important est la scission de juillet 2024, annoncée dans
[le journal hebdomadaire du 25 juillet 2024](https://f-droid.org/2024/07/25/twif.html) :

> « Now we split the old confusing "Non-Free Network Services" AntiFeature into two different
> Anti-Features: **"Non-Free Network Services" is only for apps relying on proprietary services**
> and "Tethered Network Services" is only for apps relying on unchangeable services. »

Escale dépend d'un serveur **MOTIS**, publié sous licence **MIT**, auto-hébergeable, dont
l'instance par défaut (`api.transitous.org`) fait tourner ce même logiciel libre. Le service n'est
pas propriétaire : la lettre de la définition ne s'applique pas.

**Le précédent qui compte** : `xyz.apiote.bimba.czwek` (**Bimba**) est au dépôt principal et **ne
déclare aucun anti-feature**, alors que c'est un client de Transitous — sa page d'accueil dit
« Route planning and geocoding is provided by Transitous », et l'une de ses versions se décrit
comme « Transitous-only ». C'est le cas le plus proche d'Escale qu'on puisse trouver dans
`fdroiddata`, et il tranche dans le sens de l'absence d'anti-feature.

**Le risque, et il est réel.** Les applications de transport public du dépôt portent presque toutes
`NonFreeNet`, et le motif inscrit dans leurs métadonnées ne parle jamais du logiciel serveur mais
des **données** :

| Application | Motif inscrit dans `fdroiddata` |
|---|---|
| Transportr `de.grobox.liberario` | « Uses the data of various local public transport agencies » |
| Öffi `de.schildbach.oeffi` | « Some data sources for public transport are not libre. » |
| KTrip `org.kde.ktrip` | « Depends on different public transport data providers. » |
| TransportYou `net.youapps.transport` | « Most sources for public transport data are not libre. » |

Un relecteur peut vouloir appliquer le même raisonnement à Escale. La différence à faire valoir :
ces quatre applications interrogent **directement** les API d'exploitants (HAFAS, EFA…), qui sont
des services propriétaires ; Escale interroge un serveur libre, qui agrège des flux GTFS publiés en
ouvert et dont Transitous documente la liste. Ce n'est pas la même chose.

Le contre-exemple le plus instructif est `com.eden.livewidget` (*Transport Widgets*), qui déclare
les deux et **sépare les motifs** : `TetheredNet` pour Transitous, `NonFreeNet` uniquement pour les
API de Transport for London et du Rail Delivery Group. La grille de lecture des mainteneurs est
donc bien celle de la documentation.

### `TetheredNet` — non plus, et pour une raison écrite

Définition : « depends entirely on a service which is impossible (or not easy) to replace », suivie
d'une exception explicite :

> « This Anti-Feature would not be applied if there is a simple configuration option that allows
> pointing the app to a running instance of an alternative, publicly available, self-hostable
> server solution. »

C'est exactement l'écran « Serveur MOTIS », **première entrée des réglages** (SPEC.md § 5.6.1), qui
normalise l'URL, teste la connexion en trois étapes et mémorise les serveurs déjà utilisés. La
condition d'exception est remplie de la manière la plus littérale possible.

À noter que `com.eden.livewidget` déclare pourtant `TetheredNet` pour Transitous : c'est parce que
cette application-là ne laisse pas changer l'adresse. La différence est donc bien la fonction, pas
le service.

### `Tracking` — non, mais c'est la clé dont la définition est la plus large

Définition : « user or activity data is tracked or leaks, **by default** […] or doing requests to a
data collecting network service (regardless if the service is based on free software, or not) », et
l'anti-feature n'est **pas** appliqué si la fonction est optionnelle, désactivée par défaut et
soumise à un consentement éclairé.

- Escale ne collecte rien pour son compte, n'a pas de serveur propre, n'émet aucun identifiant.
- La seule requête envoyée sans que l'usager ait l'application sous les yeux est celle des trajets
  surveillés (§ 5.5.1) : **désactivée par défaut**, activée trajet par trajet, plafonnée à cinq
  trajets, une seule requête par occurrence, et l'écran d'activation dit en toutes lettres qu'une
  requête à heure fixe révèle une habitude de déplacement. C'est précisément le régime que la
  définition exempte.
- Aucune des applications de transport citées plus haut ne porte `Tracking`.

### `NonFreeAssets`, `NonFreeDep`, `NonFreeAdd` — non

- **`NonFreeAssets`** (images, sons, polices sous licence non libre) : tous les éléments graphiques
  sont produits pour le projet et couverts par sa GPLv3 (`docs/icone.md`). Aucune police embarquée,
  aucun son, aucune vidéo. Les glyphes de la carte sont servis par le serveur MOTIS, ils ne sont pas
  dans l'APK.
- **`NonFreeDep`** (exige qu'un logiciel propriétaire soit installé sur l'appareil) : non. La
  position vient du `LocationManager` de la plateforme, jamais des services Google. Le seul renvoi
  vers une application tierce est le bouton `rentalUriAndroid` d'une station de libre-service, qui
  ouvre l'application de l'exploitant **si elle est installée** ; l'application fonctionne
  entièrement sans, et cela ne constitue pas une dépendance au sens de la définition.
- **`NonFreeAdd`** (fait la promotion d'extensions propriétaires) : non, il n'existe aucune
  extension.

Pour mémoire, `UpstreamNonFree` n'existe pas : cette clé n'est ni dans `config/antiFeatures.yml`,
ni dans la référence des métadonnées, et `fdroid lint` refuse une clé inconnue.

### Ce qu'il faut écrire dans la *merge request*

Ne pas se contenter d'omettre le bloc `AntiFeatures:`. Écrire le raisonnement dans la description
de la MR, en citant nommément : la scission de juillet 2024, l'exception de `TetheredNet` sur la
configurabilité, le précédent `xyz.apiote.bimba.czwek`, et la licence MIT de MOTIS. Un relecteur qui
voit « application de transport public » sans explication appliquera le réflexe Transportr.

**Réserve honnête.** L'attribution des anti-features est discrétionnaire et, de l'aveu même de
l'annonce de 2024 (« This is just an initial work »), inégalement appliquée : `de.tu_chemnitz.…
openstop` reçoit `TetheredNet` pour OpenStreetMap tandis que `de.hbch.traewelling` reçoit
`NonFreeNet` en citant OpenStreetMap dans son motif. Il est donc possible que le relecteur tranche
autrement. Ce serait un désaccord d'appréciation, pas un défaut du projet, et cela n'empêcherait
pas l'inclusion.

---

## 6. Compilation par F-Droid, et reproductibilité

### Ce qui est déjà en règle

| Point | État |
|---|---|
| APK de publication **non signés** | Oui, vérifié : `assembleRelease` produit `app-<abi>-release-unsigned.apk`. Le type `release` n'a **aucune** `signingConfig`, et le dépôt ne contient ni clé, ni mot de passe, ni fichier de clés. C'est exactement ce que F-Droid cherche : ses trois motifs de recherche sont `*-release-unsigned.apk`, `*-unsigned.apk`, `*.apk`, dans cet ordre. **Aucun `prebuild:` de suppression de `signingConfig` n'est nécessaire** — c'est le correctif le plus fréquent de `fdroiddata`, et il ne s'applique pas ici. |
| APK non `debuggable` | Oui. F-Droid refuse un APK débogable ; le type `release` ne l'est pas, et la variante `releaseTest` — la seule qui soit signée, avec la clé de débogage — n'est ni `debuggable` ni destinée à la publication. |
| Aucun secret dans le dépôt ni dans la CI | Le travail GitHub Actions n'utilise aucun `secrets.*` et n'a que la permission `contents: read`. |
| R8 déterministe | R8 l'est à configuration et entrées identiques. Les règles du projet vivent dans `app/src/main/keepRules/`, versionnées. Deux tâches (`verifyReleaseKeepRules`, `verifyReleaseApkSize`) finalisent `assembleRelease` et échouent si R8 a supprimé ou renommé une classe désignée par son nom. |
| Dépôts de dépendances | `mavenCentral()` et `google()` seuls, tous deux dans la liste autorisée en dur par le scanner. |
| Wrapper Gradle versionné | Sans conséquence : le scanner de `fdroidserver` **supprime lui-même** `gradlew`, `gradlew.bat`, `gradle-wrapper.jar` et `gradle-daemon-jvm.properties`, sans compter d'erreur, puis lance son propre `gradlew-fdroid`. Rien à déclarer, aucun `scanignore:`. |

### Le point bloquant : `assembleRelease` produit quatre APK

`fdroid build` cherche **un** APK par bloc `Builds:` et lève
`BuildException('More than one resulting apks found in …')` s'il en trouve plusieurs. Or le
découpage par ABI de `app/build.gradle.kts` en produit quatre d'un seul coup. En l'état, **la
recette échouerait**.

Ce n'est pas un reproche fait au projet : F-Droid encourage explicitement le découpage par ABI,
et la convention de `versionCode` du projet est **déjà celle que F-Droid demande** — chiffres
d'ABI aux poids faibles, ordre `armeabi-v7a < arm64-v8a < x86 < x86_64`, ce que le commentaire de
`app/build.gradle.kts` justifie exactement dans les mêmes termes. Ce qui manque est le moyen de
**demander une seule ABI à la fois**.

Deux voies, à trancher par le mainteneur :

1. **La propre, côté projet** — ajouter à `app/build.gradle.kts` une propriété Gradle qui restreint
   le découpage à une ABI, puis quatre blocs `Builds:` portant `gradleprops: [abi=arm64-v8a]`, etc.
   C'est ce que fait `com.graphhopper.maps`, l'application de `fdroiddata` la plus proche d'Escale
   (Gradle, Kotlin, MapLibre, deux ABI) :

   ```yaml
   VercodeOperation:
     - 1000 * %c + 1     # armeabi-v7a
     - 1000 * %c + 2     # arm64-v8a
     - 1000 * %c + 3     # x86
     - 1000 * %c + 4     # x86_64
   ```

   Cette voie **modifie un fichier de compilation** et sort du périmètre du présent document ;
   elle est signalée, pas appliquée.

2. **La contournante, côté recette** — quatre blocs `Builds:` dont le `prebuild:` remplace par
   `sed` la liste `include(...)` du bloc `splits`, comme le fait `org.videolan.vlc`. Rien à changer
   dans le projet, mais la recette devient sensible à la mise en forme du fichier Gradle : une
   reformulation du bloc `splits` casserait silencieusement la recette.

La première voie est plus solide. Elle demande une décision, car la convention de `versionCode`
est **figée** une fois l'application publiée : la changer casserait le chemin de mise à jour de
tous les appareils installés.

### Deux autres réserves

**L'outillage est très récent.** AGP 9.3.2, Gradle 9.5, `compileSdk` et `targetSdk` 37. Le serveur
de compilation de F-Droid doit disposer de cette version de Gradle — `gradlew-fdroid` la choisit
d'après `gradle/wrapper/gradle-wrapper.properties`, qui, lui, n'est pas supprimé — et de cette
plateforme Android. Si l'une manque, la recette échouera pour cette raison et non pour un défaut du
projet. À éprouver avec `fdroid build` **avant** d'ouvrir la *merge request*.

**Le téléchargement de JDK n'est un problème que hors de F-Droid.** `gradle-daemon-jvm.properties`
fixe `toolchainVersion=21` et liste des URL `api.foojay.io` ; `settings.gradle.kts` applique le
greffon `foojay-resolver-convention`. Sur une machine sans JDK 21 détectable, Gradle irait en
télécharger un au milieu du travail. Sur le serveur de F-Droid, le fichier est supprimé par le
scanner avant la compilation, et aucun module ne déclare de `jvmToolchain` : le risque ne s'y
matérialise donc pas. Il reste valable pour la CI et pour les contributeurs — c'est pourquoi le
travail GitHub Actions installe le JDK 21 explicitement.

### Reproductibilité : une décision à prendre maintenant, ou jamais

F-Droid est clair : les compilations reproductibles ne sont **pas** une condition d'inclusion, mais
elles ne se rattrapent pas après coup, parce qu'Android interdit de mettre à jour une application
avec une clé de signature différente — les usagers devraient désinstaller et réinstaller. C'est
donc à trancher **avant la première publication**.

Une application Kotlin/Compose sans code natif propre est un bon candidat ; ici le seul code natif
vient d'AAR Maven, identiques octet pour octet des deux côtés, ce qui joue en faveur. Si le
mainteneur veut s'y engager, il lui faut publier ses propres APK signés, renseigner `Binaries:` et
`AllowedAPKSigningKeys:` dans la recette, et signer avec l'`apksigner` de **build-tools 34** — les
versions 35 et suivantes produisent des APK que l'outil de vérification de F-Droid ne sait pas
recopier. Le profil de référence ART (`baseline.prof`) est également connu pour être parfois
non déterministe.

Sinon, on ne renseigne rien : F-Droid compile et signe avec sa propre clé, ce qui est le cas le plus
courant et le plus simple. **Mais on ne pourra plus changer d'avis.**

### Mesures relevées

Les quatre APK produits, tous sous le budget de 15 Mo de SPEC.md § 2 :

```
app-armeabi-v7a-release-unsigned.apk   11,13 Mo
app-arm64-v8a-release-unsigned.apk     14,06 Mo
app-x86-release-unsigned.apk           14,21 Mo
app-x86_64-release-unsigned.apk        14,42 Mo
```

Le type de compilation `releaseTest`, signé avec la clé de débogage, n'existe que pour mesurer la
taille et le démarrage sur un binaire identique à celui qui sera publié. **La recette `fdroiddata`
doit compiler `release`, jamais `releaseTest`.**

## 7. Métadonnées de la fiche

`fastlane/metadata/android/{en-US,fr-FR}/` est en place : titre, résumé, description longue,
changelog et icône 512 × 512. Les limites de longueur sont mesurées et respectées, le format HTML
attendu par F-Droid est employé, et l'attribution de SPEC.md § 4.2 — Transitous et OpenStreetMap —
figure dans les deux descriptions. Le détail, les pièges et la marche à suivre à chaque publication
sont dans [`fastlane/README.md`](../fastlane/README.md).

**Manquent les captures d'écran.** Les répertoires existent, la convention de nommage et l'ordre
attendu sont écrits, mais aucun fichier n'a pu être produit faute d'accès à un appareil. La liste
des huit écrans à photographier, dans les deux langues, est dans `fastlane/README.md`.

**Un défaut à corriger avant publication, hors du périmètre de ce document.** Les icônes de repli
`app/src/main/res/mipmap-*/ic_launcher*.webp` portent encore **l'icône par défaut du modèle
Android Studio** — le robot vert — depuis le premier commit du dépôt. `docs/icone.md` le note déjà
comme un reste à produire. Le `minSdk` valant 26, l'icône adaptative en XML est celle qu'affichent
les lanceurs, et ce repli ne sert en principe à rien ; mais certains outils qui extraient l'icône
d'un APK ne savent pas rendre une icône adaptative et retombent sur le PNG de plus haute densité.
À vérifier sur la fiche une fois publiée, et de toute façon à corriger : laisser le robot d'Android
Studio dans un APK publié est le genre de détail qu'un relecteur relève.

---

## 8. Ce qui reste à faire par le mainteneur humain

Par ordre. Le premier n'est pas négociable, le deuxième est bloquant.

1. **Prendre contact avec l'équipe Transitous avant publication.** SPEC.md § 4.2 le prescrit :
   l'instance publique `api.transitous.org` est tenue par des bénévoles, à leurs frais, avec une
   politique d'usage. Une application publiée sur F-Droid peut multiplier leur trafic sans qu'ils
   en aient été avertis. Le message doit dire ce qu'Escale envoie, à quelle fréquence, ce qu'elle
   ne fait pas (aucun sondage périodique, une seule requête par occurrence de surveillance,
   cinq surveillances au plus, la surveillance désactivée par défaut), et donner le `User-Agent`
   qui permettra de l'identifier dans leurs journaux :
   `Escale/1.0.0 (+https://github.com/mgdx/escale)`.

2. **Régler la question des quatre APK** (§ 6). C'est le seul point qui empêche aujourd'hui la
   recette de compiler. Décider entre la propriété Gradle et le `prebuild: sed`, et figer la
   convention de `versionCode` — elle ne pourra plus changer ensuite.

3. **Trancher la reproductibilité** (§ 6). Oui ou non, mais avant la première publication.

4. **Produire les huit captures d'écran** en anglais et en français, selon la liste et les
   consignes de [`fastlane/README.md`](../fastlane/README.md), et les déposer dans
   `fastlane/metadata/android/<langue>/images/phoneScreenshots/`.

5. **Remplacer les icônes de repli `mipmap-*/ic_launcher*.webp`** (§ 7).

6. **Poser un tag git par version** (`v1.0.0`), sans quoi `UpdateCheckMode: Tags` n'a rien à lire.

7. **Éprouver la recette localement** avant de proposer quoi que ce soit :
   `fdroid rewritemeta`, `fdroid lint`, puis `fdroid build io.github.mgdx.escale` dans l'image
   `registry.gitlab.com/fdroid/fdroidserver:buildserver`. C'est là que se verront les réserves du
   § 6.

8. **Ouvrir la *merge request* sur `fdroiddata`** avec `metadata/io.github.mgdx.escale.yml`. La
   documentation F-Droid recommande la MR directe plutôt que la demande de paquetage quand on est
   l'auteur de l'application : « Consider packaging it yourself, then opening a merge request with
   the required metadata which will save you and us a lot of time. » Branche nommée comme
   l'`applicationId`, commit et libellé `New App: io.github.mgdx.escale`.
   **Y écrire l'argumentaire anti-features du § 5** : c'est le seul endroit où il sera lu.

9. **Faire la revue de testeur** que fera de toute façon un relecteur : installer l'APK, vérifier
   que l'application démarre, que les fonctions annoncées dans la description existent bien — la
   politique d'inclusion l'exige nommément —, que l'interface anglaise est complète, et capturer le
   trafic réseau (§ 4).

10. **Trancher la question des en-têtes de licence** (§ 1) : soit on les ajoute sur les 366 fichiers
    avant la première publication, soit on acte par écrit qu'on ne le fait pas.

---

## Sources

Les affirmations de ce document viennent de trois endroits, et de nulle part ailleurs :

- **Le dépôt lui-même**, compilé et lu : APK de publication, manifeste fusionné, POM Maven des
  dépendances résolues, sortie de `./gradlew :app:dependencies`.
- **La documentation F-Droid** :
  [politique d'inclusion](https://f-droid.org/docs/Inclusion_Policy/),
  [anti-features](https://f-droid.org/docs/Anti-Features/),
  [référence des métadonnées de compilation](https://f-droid.org/docs/Build_Metadata_Reference/),
  [guide de soumission](https://f-droid.org/docs/Submitting_to_F-Droid_Quick_Start_Guide/),
  [compilations reproductibles](https://f-droid.org/docs/Reproducible_Builds/), et le
  [journal du 25 juillet 2024](https://f-droid.org/2024/07/25/twif.html) sur la scission
  `NonFreeNet` / `TetheredNet`.
- **Le code et les données de F-Droid** : `fdroidserver/scanner.py`, `build.py`, `common.py`,
  `update.py`, et les fichiers `metadata/*.yml` de `fdroiddata` cités nommément.

Ces sources ont été relevées le 3 septembre 2026. La politique d'inclusion et la forme des
métadonnées **changent** : avant de déposer, relire les pages citées plutôt que ce document.

[antifeatures]: https://gitlab.com/fdroid/fdroiddata/-/blob/master/config/antiFeatures.yml
