# Icône d'Escale

Motif : un **point de correspondance**. Un anneau posé entre deux segments de trajet, teal avant
l'escale, corail après. Le même vocabulaire graphique que la frise de trajet de l'écran de résultats.

## Couleurs

| Rôle | Valeur |
|---|---|
| Segment avant l'escale, accent de l'application | `#1D9E75` |
| Segment après l'escale | `#D85A30` |
| Anneau | `#0F6E56` |
| Fond de l'icône | `#E1F5EE` |

`#1D9E75` sert de couleur d'accent au thème Material 3 en mode clair ; le mode sombre en dérive
une variante plus claire via le générateur de palette, il n'y a pas de seconde valeur à figer ici.

## Emplacement des fichiers

| Fichier | Emplacement dans le dépôt |
|---|---|
| Descripteur adaptatif | `app/src/main/res/mipmap-anydpi/ic_launcher.xml` et `ic_launcher_round.xml`, même contenu |
| Calque avant | `app/src/main/res/drawable/ic_launcher_foreground.xml` |
| Calque de fond | `app/src/main/res/drawable/ic_launcher_background.xml` |
| Calque thématique | `app/src/main/res/drawable/ic_launcher_monochrome.xml` |
| Source vectorielle | `docs/escale-icon-512.svg` |

Le descripteur est en `mipmap-anydpi` et non en `mipmap-anydpi-v26` : le `minSdk` du projet est 26,
les deux qualificateurs sont donc équivalents et un seul dossier évite d'entretenir deux copies.

Restent à produire, au jalon de finition :

- l'export PNG 512 × 512 de `docs/escale-icon-512.svg` vers
  `fastlane/metadata/android/en-US/images/icon.png` et son équivalent `fr-FR` ;
- les `mipmap-*/ic_launcher.webp` de repli aux densités habituelles, qui portent encore l'icône
  par défaut d'Android Studio. Le `minSdk` étant 26, ce repli ne sert qu'aux lanceurs qui ignorent
  les icônes adaptatives.

## Contraintes respectées

- **Zone de sécurité.** Le gabarit adaptatif fait 108 × 108 dp, mais le lanceur peut rogner jusqu'au
  cercle central de 66 dp de diamètre. Tout le motif tient dans un rayon de 32 dp autour du centre :
  extrémités des segments à 27 et 81, demi-épaisseur de trait comprise.
- **Icône thématique.** Le calque `monochrome` est déclaré, comme l'attend Android 13 et suivants.
  Le motif reste lisible en aplat d'une seule couleur : c'est la raison pour laquelle les segments
  s'arrêtent au bord de l'anneau au lieu de le traverser.
- **Format vectoriel** partout : aucun PNG dans l'APK pour l'icône principale, donc quelques kilo-octets.
- **Licence.** Ces fichiers font partie du dépôt et sont couverts par la GPLv3 du projet, comme
  l'exige F-Droid pour tout élément graphique embarqué.

## Vérifications à faire une fois posée sur l'appareil

1. Lanceur en icônes rondes, en squircle et en carré : le motif ne doit jamais être rogné.
2. Icône thématique activée sur Android 13+, fond clair puis fond sombre.
3. Rendu à petite taille dans les paramètres système et dans la liste des applications récentes.
4. Icône en niveaux de gris : le contraste entre les deux segments disparaît, la lecture doit
   tenir sur la forme seule.
