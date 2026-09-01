# Politique de confidentialité d'Escale

Escale est un client d'un serveur [MOTIS](https://github.com/motis-project/motis). Elle ne possède
aucun serveur propre, ne crée aucun compte et ne collecte rien pour son compte.

## Ce qui ne se produit jamais

- **Aucune télémétrie, aucune statistique d'usage, aucun rapport de plantage automatique.**
- **Aucun traqueur, aucune régie publicitaire, aucun service Google Play.**
- Aucune donnée n'est envoyée à l'éditeur de l'application, qui ne reçoit rien du tout.
- Aucune adresse, coordonnée ni requête n'est écrite dans les journaux de l'appareil, y compris
  dans les versions de débogage.

## Ce qui est envoyé au serveur MOTIS configuré

Le serveur par défaut est l'instance publique communautaire `https://api.transitous.org`. Vous
pouvez la remplacer par n'importe quel autre serveur MOTIS dans **Réglages → Serveur MOTIS** ;
tout ce qui suit s'applique alors au serveur que vous avez choisi.

Sont transmis, uniquement au moment où vous vous en servez :

| Quand | Ce qui part |
|---|---|
| Vous saisissez un point de départ ou d'arrivée | le texte saisi, et le centre de la carte pour classer les résultats par proximité |
| Vous lancez une recherche | les coordonnées ou l'identifiant d'arrêt du départ et de l'arrivée, l'heure demandée, la catégorie consultée et vos préférences de recherche |
| Vous ouvrez un trajet, un arrêt ou une station | l'identifiant de l'élément consulté |
| Vous déplacez la carte | l'emprise rectangulaire affichée, jamais votre position |
| Chaque requête | un en-tête `User-Agent` de la forme `Escale/<version> (+https://github.com/mgdx/escale)`, exigé par la politique d'usage de l'instance publique |

Le serveur voit également l'adresse IP de votre appareil, comme pour toute requête réseau. Escale
n'y ajoute aucun identifiant : ni numéro d'installation, ni cookie, ni jeton de session.

Votre position n'est envoyée que si vous appuyez sur le bouton de localisation ou choisissez
« Ma position » comme point de départ. L'application reste entièrement utilisable si vous refusez
la permission de localisation.

## Ce qui reste sur l'appareil

- Le serveur configuré et les serveurs déjà utilisés.
- Vos préférences de recherche et d'affichage.
- Vos favoris : domicile, travail, lieux, arrêts, trajets. Domicile et travail sont facultatifs et
  ne vous sont jamais réclamés.
- L'historique des cinquante dernières recherches, désactivable et effaçable à tout moment depuis
  les réglages.
- Les caches : résultats de recherche en mémoire, géocodage sur disque pour 24 heures, tuiles de
  fond de carte pour 100 Mo au plus, purgeables depuis les réglages.

Rien de tout cela n'est synchronisé ni sauvegardé ailleurs que sur votre appareil.

## Permissions

| Permission | Pourquoi | Quand elle est demandée |
|---|---|---|
| `INTERNET` | interroger le serveur MOTIS | à l'installation, obligatoire |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | vous situer sur la carte et partir de votre position | facultatives, au premier appui sur le bouton de localisation |
| `POST_NOTIFICATIONS` | vous prévenir avant un trajet surveillé | facultative, à l'activation de votre première surveillance |

La position est lue par le `LocationManager` de la plateforme, jamais par les services de
localisation de Google. Aucune permission de stockage, de contacts, de démarrage automatique, de
service en arrière-plan ni d'alarme exacte n'est demandée.

## Trajets surveillés

> Cette section sera complétée au jalon qui introduit la fonction ; elle est décrite ici parce que
> c'est la seule activité réseau qu'Escale exercera en dehors de votre usage direct.

Un trajet mis en favori pourra être **surveillé** : l'application interrogera alors le serveur
**une seule fois, une heure avant l'heure de départ prévue**, pour vous prévenir si quelque chose a
changé. La fonction sera désactivée par défaut, limitée à cinq trajets, et ne sera jamais proposée
d'elle-même.

Ce que cela implique, et qui doit être dit clairement : ces requêtes partent à heure fixe, les jours
que vous avez choisis. Elles révèlent donc une **habitude de déplacement** à qui observe le serveur
ou le réseau, ce que votre usage manuel de l'application ne fait pas. C'est la raison pour laquelle
la surveillance reste un choix explicite, révocable à tout moment.

## Questions

Le code est public : <https://github.com/mgdx/escale>. Toute question sur ce document peut y être
posée sous forme d'issue.
