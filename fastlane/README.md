# Métadonnées Fastlane — la fiche d'Escale sur F-Droid

Ce répertoire est la fiche de l'application telle que F-Droid l'affiche : le nom, les deux
descriptions, les nouveautés de la version, l'icône et les captures d'écran. Il n'entre pas dans
l'APK et ne change rien au code ; il est lu par `fdroid update` au moment de la publication.

**L'emplacement n'est pas négociable.** `fdroidserver` cherche `fastlane/metadata/android/<locale>/`
à partir de la **racine du dépôt**. Déplacer l'arborescence ailleurs — dans `app/`, par exemple —
la rend invisible, sans le moindre message d'erreur.

## Arborescence

```
fastlane/metadata/android/
├── en-US/                       ← anglais, la langue par défaut de l'application
│   ├── title.txt                    nom affiché
│   ├── short_description.txt        la phrase sous le nom, dans la liste
│   ├── full_description.txt         la description de la fiche
│   ├── changelogs/
│   │   ├── 4001.txt                 nouveautés de la version 1.0.0 — voir ci-dessous
│   │   └── default.txt              repli, même texte
│   └── images/
│       ├── icon.png                 512 × 512, exporté de docs/escale-icon-512.svg
│       └── phoneScreenshots/        01-*.png, 02-*.png… — à produire
└── fr-FR/                       ← français, même structure
```

## Limites de longueur — elles ne préviennent pas

`fdroidserver` **tronque en silence** ce qui dépasse : ni `fdroid lint` ni `fdroid update`
n'émettent le moindre avertissement, et la fiche est publiée amputée. Les valeurs viennent de
`char_limits` dans [`fdroidserver/common.py`](https://gitlab.com/fdroid/fdroidserver/-/blob/master/fdroidserver/common.py#L216) :

| Fichier | Limite | en-US | fr-FR |
|---|---:|---:|---:|
| `title.txt` | 50 | 6 | 6 |
| `short_description.txt` | 80 | 69 | 75 |
| `full_description.txt` | 4000 | 2603 | 2974 |
| `changelogs/*.txt` | 500 | 459 | 466 |

À vérifier après chaque modification, en **caractères** et non en octets :

```bash
find fastlane -name '*.txt' -exec sh -c 'printf "%-62s %4d\n" "$1" "$(wc -m < "$1")"' _ {} \;
```

## Le format de `full_description.txt` : du HTML, pas du Markdown

C'est le piège le plus courant. Un `**gras**` ou un `- item` s'afficherait **littéralement**.
Les seules balises conservées sont celles de l'allowlist de
[`jekyll-fdroid`](https://gitlab.com/fdroid/jekyll-fdroid/-/blob/master/lib/fdroid/Package.rb) :
`a` (attribut `href` seul), `b`, `big`, `blockquote`, `br`, `cite`, `em`, `i`, `li`, `ol`, `small`,
`strike`, `strong`, `sub`, `sup`, `tt`, `u`, `ul`.

Ce qui n'y est **pas** et disparaît sans bruit : `p`, `h1`…`h6`, `code`, `pre`, `table`, `div`,
`span`, `hr`, `img`. Les sauts de ligne sont convertis en `<br>` automatiquement : une ligne vide
suffit à séparer deux paragraphes, il n'y a rien à baliser. C'est aussi pourquoi chaque liste
`<ul>…</ul>` tient ici **sur une seule ligne** : un retour à la ligne entre deux `<li>` insérerait
un `<br>` parasite à l'intérieur de la liste.

## Le nom du fichier de changelog — le piège du `versionCode` par ABI

`app/build.gradle.kts` ne publie pas un `versionCode` mais **quatre**, un par architecture, valant
`rang de l'ABI × 1000 + baseVersionCode`. Pour la version 1.0.0 : `1001` (armeabi-v7a), `2001`
(arm64-v8a), `3001` (x86) et `4001` (x86_64).

Or `fdroidserver` n'affiche comme « nouveautés » que le fichier nommé d'après le
**`CurrentVersionCode`**, et celui-ci est **le plus grand des quatre**
([`checkupdates.py`](https://gitlab.com/fdroid/fdroidserver/-/blob/master/fdroidserver/checkupdates.py#L547)
trie les `VercodeOperation` et retient le dernier). Le fichier attendu est donc :

> **`changelogs/4001.txt`** — et non `1.txt`, qui est le `versionCode` de `defaultConfig` et
> n'apparaît dans aucun APK publié.

`changelogs/default.txt` porte le même texte : c'est le filet de sécurité si le mainteneur fixe un
autre `CurrentVersionCode` à la main dans `fdroiddata`.

**À chaque publication**, `baseVersionCode` augmente de 1 : la version 1.0.1 se numérote 1002 /
2002 / 3002 / 4002, et le changelog s'appelle alors `4002.txt`. Ne mettez pas de zéro de tête :
`04002.txt` ne serait pas reconnu.

## Les captures d'écran

Extensions acceptées : **`png`, `jpg`, `jpeg` uniquement** — ni WebP, ni SVG. Taille maximale
d'environ 16,7 mégapixels (`Image.MAX_IMAGE_PIXELS` dans `fdroidserver/update.py`) ; une capture de
téléphone en est très loin. Les métadonnées EXIF sont retirées à la publication.

**L'ordre d'affichage est l'ordre d'un `sorted()` Python sur le nom de fichier**, c'est-à-dire un
tri *lexicographique* et non numérique
([`update.py`](https://gitlab.com/fdroid/fdroidserver/-/blob/master/fdroidserver/update.py#L1419)).
Dix fichiers nommés `1.png` … `10.png` s'afficheraient donc dans l'ordre 1, 10, 2, 3… D'où la
convention retenue ici, avec **zéro de tête et libellé** :

```
01-recherche.png
02-resultats.png
…
```

L'ordre attendu, identique dans les deux langues :

| Nom du fichier | Écran | Ce qu'il doit montrer |
|---|---|---|
| `01-recherche.png` | Accueil (§ 5.1) | La carte plein écran et la carte de recherche flottante, avec un départ et une arrivée saisis. |
| `02-resultats.png` | Résultats (§ 5.2) | L'onglet « Transport en commun » ouvert, plusieurs trajets, la frise des portions colorée, un retard visible. |
| `03-detail.png` | Détail d'un trajet (§ 5.3) | Les portions dépliées : ligne, direction, quai, arrêts intermédiaires. |
| `04-carte-trajet.png` | Carte d'un trajet (§ 5.3) | Les polylignes du trajet cadrées, les marqueurs de départ, d'arrivée et de correspondance. |
| `05-departs.png` | Prochains départs (§ 5.4) | La liste d'un arrêt réel, avec retards et perturbations. |
| `06-libre-service.png` | Portion en libre-service (§ 5.3) | La disponibilité d'une station : véhicules disponibles, places libres. |
| `07-favoris.png` | Favoris (§ 5.5) | Domicile, travail et quelques trajets enregistrés. |
| `08-serveur.png` | Serveur MOTIS (§ 5.6.1) | L'écran de réglage du serveur, test de connexion réussi. C'est lui qui montre que l'application n'est liée à personne. |

Consignes de prise de vue :

- **Un appareil, une définition** pour toute la série. Le Fairphone 3 de recette convient
  (1080 × 2160). Ne mélangez pas deux appareils : les captures s'affichent côte à côte.
- **Thème clair** pour la série publiée. Une seule capture en thème sombre est acceptable si elle
  est en fin de série ; ne panachez pas.
- **`en-US` en anglais, `fr-FR` en français.** Ce sont deux séries distinctes, pas la même série
  recopiée : F-Droid montre à chaque usager la série de sa langue, et une capture en français dans
  `en-US/` est un défaut que les relecteurs signalent.
- **Aucune donnée personnelle.** Pas d'adresse réelle, pas de domicile, pas de trajet quotidien.
  Deux gares connues d'une grande ville suffisent, et c'est aussi ce que demande `CONTRIBUTING.md`
  pour les rapports de bug.
- Barre d'état propre : pas de notification en attente, batterie et heure quelconques.
- Pas de cadre de téléphone ajouté après coup, pas de texte incrusté, pas de flèche : F-Droid
  affiche la capture telle quelle.

Les répertoires `images/phoneScreenshots/` sont créés et vides ; leur `.gitkeep` n'est pas lu par
`fdroidserver`, qui ne considère que les fichiers `*.png`, `*.jpg` et `*.jpeg`. Il est à supprimer
quand les captures arrivent.

## Ajouter une langue

Copiez `fastlane/metadata/android/fr-FR/` sous le code de la nouvelle langue, traduisez les quatre
fichiers texte, recopiez `images/icon.png` tel quel et fournissez votre propre série de captures.
Le code de langue suit la convention de fastlane (`de-DE`, `pt-BR`), pas celle des répertoires de
ressources Android (`values-de`, `values-pt-rBR`). Voir `CONTRIBUTING.md`.
