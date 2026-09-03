# Politique de confidentialité d'Escale

Escale est un client d'un serveur [MOTIS](https://github.com/motis-project/motis). Elle ne possède
aucun serveur propre, ne crée aucun compte et ne collecte rien pour son compte.

Ce document décrit **l'application telle qu'elle est aujourd'hui**. Les fonctions qui ne sont pas
encore écrites sont annoncées comme telles, dans les deux dernières sections.

## Ce qui ne se produit jamais

- **Aucune télémétrie, aucune statistique d'usage, aucun rapport de plantage automatique.**
- **Aucun traqueur, aucune régie publicitaire, aucun service Google Play**, ni aucune bibliothèque
  qui en dépende.
- Aucune donnée n'est envoyée à l'éditeur de l'application, qui ne reçoit rien du tout.
- **Aucune adresse, coordonnée ni requête n'est écrite dans les journaux de l'appareil, y compris
  dans les versions de débogage.** Ce n'est pas un réglage : il n'existe aucune ligne de code qui
  les écrive.
- Rien n'est synchronisé, sauvegardé ni envoyé ailleurs que sur votre appareil et vers le serveur
  que vous avez choisi.

## Ce qui est envoyé au serveur MOTIS configuré

Le serveur par défaut est l'instance publique communautaire `https://api.transitous.org`. Vous
pouvez la remplacer par n'importe quel autre serveur MOTIS dans **Réglages → Serveur MOTIS** ;
tout ce qui suit s'applique alors au serveur que vous avez choisi.

Sont transmis, uniquement au moment où vous vous en servez :

| Quand | Ce qui part |
|---|---|
| Vous saisissez un point de départ ou d'arrivée | le texte saisi, et le centre de la carte pour classer les résultats par proximité |
| Vous lancez une recherche | le départ et l'arrivée — l'identifiant de l'arrêt quand c'en est un, ses coordonnées sinon —, l'heure demandée, la catégorie consultée et vos préférences de recherche (allure de marche, profil piéton, correspondances, types de véhicules) |
| Vous ouvrez le détail d'un trajet | l'identifiant du trajet consulté |
| Vous déplacez la carte | l'emprise rectangulaire affichée : une fois pour demander les **tuiles** du fond de carte, une fois pour demander les **arrêts** à y placer. Jamais votre position |
| Vous testez un serveur dans les réglages | une requête d'état, une petite emprise de carte et une tuile, pour savoir ce que ce serveur sait faire |
| Chaque requête | un en-tête `User-Agent` de la forme `Escale/<version> (+https://github.com/mgdx/escale)`, exigé par la politique d'usage de l'instance publique |

Le serveur voit également l'adresse IP de votre appareil, comme pour toute requête réseau. Escale
n'y ajoute aucun identifiant : ni numéro d'installation, ni cookie, ni jeton de session. Deux
sessions d'Escale ne sont reliables entre elles par rien que l'application transmette.

## Votre position

- Elle est **facultative** : l'application est pleinement utilisable sans elle, et le reste.
- La permission est demandée **à l'usage**, au premier appui sur le bouton de localisation —
  **jamais au démarrage**, jamais à la première ouverture.
- Elle est lue par le `LocationManager` de la plateforme Android, **jamais** par les services de
  localisation de Google, qu'Escale n'embarque pas.
- Elle ne part vers le serveur que si vous vous en servez comme point de départ ou d'arrivée, ou si
  vous demandez le nom du lieu où vous êtes. Centrer la carte sur vous n'envoie rien.
- Si vous refusez la permission, le bouton reste, un appui vous explique pourquoi en une phrase, et
  tout le reste de l'application fonctionne à l'identique.

## Ce qui reste sur l'appareil

Tout ce qui suit vit dans le **stockage privé** d'Escale, celui qu'Android réserve à l'application
et qu'aucune autre application ne peut lire. Rien n'en sort.

| Ce qui est gardé | Où, et combien de temps |
|---|---|
| Votre domicile, votre lieu de travail, vos lieux, arrêts et trajets favoris | base de données locale (Room), jusqu'à ce que vous les supprimiez. Consultables et effaçables un par un depuis **Réglages → Favoris et historique** |
| Vos cinquante dernières recherches, horodatées | base de données locale (Room). La cinquante-et-unième chasse la plus ancienne. Effaçables une par une ou en bloc depuis **Réglages → Favoris et historique**, et l'enregistrement se désactive entièrement depuis **Réglages → Données** |
| Le serveur configuré, les serveurs déjà utilisés, et votre accord éventuel pour un serveur en clair (`http://`) | fichier de préférences (DataStore), jusqu'à ce que vous le changiez |
| Vos préférences de recherche et d'affichage | fichier de préférences (DataStore) ; le bouton « Rétablir les réglages par défaut » les remet à zéro |
| La dernière position de la carte | fichier de préférences (DataStore), pour rouvrir la carte là où vous l'aviez laissée. Elle n'est jamais envoyée nulle part |
| Les résultats de recherche déjà obtenus | **en mémoire seulement**, jamais sur disque : ils disparaissent quand l'application se ferme. Effaçables depuis **Réglages → Données** |
| Les réponses de l'autocomplétion d'adresses | cache disque, **24 heures**, quelques mégaoctets au plus, dans le stockage privé. Effaçable depuis **Réglages → Données** |
| Les tuiles du fond de carte déjà téléchargées | cache disque **plafonné à 100 Mo**, au-delà duquel les plus anciennes sont évincées. Effaçable depuis **Réglages → Données** |

Les résultats gardés en mémoire sont classés par le serveur qui les a produits : changer d'instance
dans les réglages n'en fait jamais ressortir un obtenu ailleurs.

### Aucune sauvegarde automatique — et ce que cela vous coûte

Escale **désactive la sauvegarde automatique d'Android**, celle qui copie d'ordinaire les données
d'une application vers le compte Google de l'appareil. Elle désactive également le transfert
d'appareil à appareil. Rien de ce qui précède ne part donc chez Google, chez le constructeur de
votre téléphone, ni chez qui que ce soit : votre domicile, votre lieu de travail et vos recherches
n'ont rien à faire dans un fichier de sauvegarde que vous ne contrôlez pas.

**Cela a un prix, et il est comptant : si vous changez de téléphone, si vous réinitialisez le
vôtre, ou si vous désinstallez Escale, tout est perdu** — domicile, travail, lieux et arrêts
favoris, trajets favoris, historique de recherche, serveurs enregistrés et préférences. Il n'existe
aujourd'hui **aucune fonction d'exportation** dans l'application, donc aucun moyen de les
rattraper. C'est un choix assumé : la seule sauvegarde possible aurait été une copie hors de votre
appareil, ce que ce document promet précisément de ne jamais faire.

## Permissions

Escale déclare **quatre** permissions, et aucune autre :

| Permission | Pourquoi | Quand elle est demandée |
|---|---|---|
| `INTERNET` | interroger le serveur MOTIS | à l'installation, obligatoire |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | vous situer sur la carte et partir de votre position | facultatives, au premier appui sur le bouton de localisation |
| `ACCESS_NETWORK_STATE` | savoir si l'appareil est connecté, pour que la carte cesse de réclamer des tuiles hors ligne | à l'installation, exigée par la bibliothèque de carte |

`ACCESS_NETWORK_STATE` est une permission de niveau *normal* : elle ne vous est pas soumise et ne
donne accès qu'au fait que l'appareil soit connecté ou non — ni le nom du réseau, ni son adresse,
ni rien qui vous concerne. La bibliothèque de carte réclamait également l'accès à l'état du Wi-Fi ;
elle ne s'en sert nulle part, et Escale la retire de son manifeste.

Aucune permission de stockage, de contacts, de démarrage automatique, de service en arrière-plan,
de notification ni d'alarme exacte n'est demandée aujourd'hui.

## Favoris et historique

Escale conserve, **sur votre appareil et nulle part ailleurs** :

- un **domicile** et un **lieu de travail**, tous deux facultatifs. L'application ne vous les
  réclame jamais : tant que vous ne les avez pas renseignés, elle n'en dit rien du tout, ni à
  l'ouverture, ni ailleurs. Vous les définissez, les modifiez et les supprimez depuis
  **Réglages → Favoris et historique**, ou par un appui long sur leur raccourci ;
- vos **lieux, arrêts et trajets favoris**, avec le nom que vous leur donnez ;
- vos **cinquante dernières recherches**, horodatées. La cinquante-et-unième efface la plus
  ancienne : rien ne s'accumule indéfiniment.

Tout cela vit dans le stockage privé de l'application. Rien n'est envoyé au serveur MOTIS, sauf,
évidemment, le jour où vous relancez une recherche à partir d'un favori — c'est alors une recherche
comme une autre, décrite plus haut.

L'enregistrement des recherches se désactive d'un interrupteur dans **Réglages → Données**, et
« Effacer les recherches récentes », au même endroit, vide la liste immédiatement. Désactiver
l'enregistrement n'efface pas ce qui est déjà là : c'est le bouton qui s'en charge, pour que vous
sachiez toujours ce qui a été supprimé et quand.

## Trajets surveillés — à venir

Cette fonction **n'existe pas encore**. Elle est décrite ici parce que ce sera la seule activité
réseau d'Escale en dehors de votre usage direct, et qu'elle mérite d'être annoncée avant d'arriver.

Un trajet mis en favori pourra être **surveillé** : l'application interrogera alors le serveur
**une seule fois, une heure avant l'heure de départ prévue**, pour vous prévenir si quelque chose a
changé. La fonction sera désactivée par défaut, limitée à cinq trajets, et ne sera jamais proposée
d'elle-même. C'est à ce moment-là, et pas avant, que la permission de notification
(`POST_NOTIFICATIONS`) vous sera demandée ; elle n'est pas déclarée aujourd'hui.

Ce que cela impliquera, et qui doit être dit clairement : ces requêtes partiront à heure fixe, les
jours que vous aurez choisis. Elles révèlent donc une **habitude de déplacement** à qui observe le
serveur ou le réseau, ce que votre usage manuel de l'application ne fait pas. C'est la raison pour
laquelle la surveillance restera un choix explicite, révocable à tout moment.

## Questions

Le code est public : <https://github.com/mgdx/escale>. Toute question sur ce document peut y être
posée sous forme d'issue.
