# Escale

> *Get there, any way you like* — *Aller là-bas, comme vous voulez*

[![CI](https://github.com/mgdx/escale/actions/workflows/ci.yml/badge.svg)](https://github.com/mgdx/escale/actions/workflows/ci.yml)

**Escale** est un client Android libre pour un serveur
[MOTIS](https://github.com/motis-project/motis) 2.x : recherche d'itinéraire multimodale, détail des
trajets portion par portion, carte, horaires en temps réel et véhicules en libre-service.

Le nom joue sur deux sens : l'escale du voyageur, et la correspondance qui ponctue un trajet.

## Ce que fait l'application

- Recherche d'itinéraire **par catégorie** : transport en commun, voiture, vélo, à pied — les
  véhicules en libre-service étant intégrés à la catégorie de leur type de véhicule.
- Détail de chaque trajet : lignes empruntées, correspondances, arrêts intermédiaires, distances,
  durées, retards en temps réel, perturbations, disponibilité des stations de libre-service.
- Carte plein écran avec les arrêts, les stations et les points d'intérêt utiles à l'orientation.
- Prochains départs à un arrêt, favoris et historique, le tout stocké **localement**.

Toute l'intelligence de calcul est côté serveur : l'application n'implémente aucun algorithme de
routage. Le cahier des charges complet est dans [`SPEC.md`](SPEC.md), qui fait autorité.

## Ce que l'application ne fait pas

Aucune télémétrie, aucun traqueur, aucun SDK publicitaire, aucun service Google Play. Aucune donnée
ne quitte l'appareil en dehors des requêtes vers le serveur MOTIS configuré. Voir
[`PRIVACY.md`](PRIVACY.md).

## Serveur

Par défaut, l'application interroge l'instance publique communautaire
[Transitous](https://transitous.org) (`https://api.transitous.org`), qui couvre une grande partie du
monde. L'adresse de n'importe quel autre serveur MOTIS — une instance auto-hébergée, par exemple —
se saisit dans **Réglages → Serveur MOTIS**.

L'instance publique est tenue par des bénévoles : l'application respecte leur politique d'usage
(en-tête `User-Agent` identifiant, requêtes économes, aucun sondage périodique).

## Compilation

Prérequis : un JDK 21 et le SDK Android (`compileSdk` 37). Le dépôt embarque le wrapper Gradle.

```bash
./gradlew assembleDebug            # compiler
./gradlew test                     # tests JVM (:core, :data)
./gradlew ktlintCheck detekt lint  # qualité, sans aucun avertissement toléré
./gradlew installDebug             # installer sur l'appareil branché
```

Le projet est découpé en trois modules Gradle :

| Module | Contenu |
|---|---|
| `:core` | Kotlin pur : modèles de domaine, géométrie, formatage, contrats de dépôt. Testable en JVM. |
| `:data` | Client HTTP Ktor, DTO de l'API MOTIS, Room, DataStore. |
| `:app` | Interface Jetpack Compose, navigation, thème, ressources. |

Le découpage et les règles de contribution entre modules sont décrits dans
[`docs/architecture.md`](docs/architecture.md).

Ces mêmes commandes tournent sur chaque poussée et chaque *pull request*
([`.github/workflows/ci.yml`](.github/workflows/ci.yml)), avec en plus `assembleRelease` : c'est la
seule étape où R8 s'exécute, donc la seule qui puisse voir une règle de conservation disparue ou un
APK sorti du budget de taille. Le travail n'emploie aucun secret et aucune clé de signature.

## Publication

Escale est destinée au dépôt principal de **F-Droid**, qui compile depuis les sources et signe
lui-même : le dépôt ne contient aucune clé, et `assembleRelease` produit volontairement des APK non
signés, un par architecture.

- L'état de conformité à la politique d'inclusion, dépendance par dépendance et permission par
  permission, est dans [`docs/fdroid.md`](docs/fdroid.md).
- La fiche affichée par F-Droid — descriptions, nouveautés, icône, captures — est dans
  [`fastlane/`](fastlane/README.md), en anglais et en français.

## Attributions

- Données d'itinéraire et de fond de carte fournies par le serveur MOTIS configuré.
  Pour l'instance par défaut : [sources de données Transitous](https://transitous.org/sources/).
- Données cartographiques © les contributeurs
  [OpenStreetMap](https://www.openstreetmap.org/copyright), sous licence ODbL.
- Rendu cartographique par [MapLibre GL Native](https://github.com/maplibre/maplibre-native)
  (licence BSD).
- Serveur d'itinéraires [MOTIS](https://github.com/motis-project/motis) (licence MIT).

## Licence

Escale est un logiciel libre publié sous **GNU General Public License v3.0 ou ultérieure**.
Le texte complet est dans [`LICENSE`](LICENSE).

## Contribuer

Les conventions de code et la procédure de traduction sont dans
[`CONTRIBUTING.md`](CONTRIBUTING.md).
