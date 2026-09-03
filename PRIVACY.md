# Politique de confidentialité d'Escale

Escale est un client d'un serveur [MOTIS](https://github.com/motis-project/motis). Elle ne possède
aucun serveur propre, ne crée aucun compte et ne collecte rien pour son compte.

Ce document décrit **l'application telle qu'elle est aujourd'hui**. Les fonctions qui ne sont pas
encore écrites sont annoncées comme telles.

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
| Une heure avant un trajet que **vous avez mis sous surveillance** | l'identifiant de l'itinéraire enregistré ; et si le serveur le refuse, la même requête de recherche que si vous l'aviez relancée vous-même. C'est **la seule requête qu'Escale envoie sans que vous ayez l'application sous les yeux**, et elle est décrite en détail plus bas |
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
| Le serveur configuré, les serveurs déjà utilisés, et votre accord éventuel pour un serveur en clair (`http://`) | fichier de préférences (DataStore), jusqu'à ce que vous le changiez |
| Vos préférences de recherche et d'affichage | fichier de préférences (DataStore) ; le bouton « Rétablir les réglages par défaut » les remet à zéro |
| La dernière position de la carte | fichier de préférences (DataStore), pour rouvrir la carte là où vous l'aviez laissée. Elle n'est jamais envoyée nulle part |
| Les résultats de recherche déjà obtenus | **en mémoire seulement**, jamais sur disque : ils disparaissent quand l'application se ferme. Effaçables depuis **Réglages → Données** |
| Les réponses de l'autocomplétion d'adresses | cache disque, **24 heures**, quelques mégaoctets au plus, dans le stockage privé. Effaçable depuis **Réglages → Données** |
| Les tuiles du fond de carte déjà téléchargées | cache disque **plafonné à 100 Mo**, au-delà duquel les plus anciennes sont évincées. Effaçable depuis **Réglages → Données** |
| Les trajets que vous surveillez : heure, jours, identifiant d'itinéraire | base locale (Room), jusqu'à ce que vous arrêtiez la surveillance ou supprimiez le favori |
| Le résultat de la dernière vérification d'un trajet surveillé | fichier de préférences (DataStore), effacé dès que vous arrêtez la surveillance |

Les résultats gardés en mémoire sont classés par le serveur qui les a produits : changer d'instance
dans les réglages n'en fait jamais ressortir un obtenu ailleurs.

## Permissions

Escale déclare **six** permissions, et aucune autre :

| Permission | Pourquoi | Quand elle est demandée |
|---|---|---|
| `INTERNET` | interroger le serveur MOTIS | à l'installation, obligatoire |
| `ACCESS_COARSE_LOCATION`, `ACCESS_FINE_LOCATION` | vous situer sur la carte et partir de votre position | facultatives, au premier appui sur le bouton de localisation |
| `ACCESS_NETWORK_STATE` | savoir si l'appareil est connecté, pour que la carte cesse de réclamer des tuiles hors ligne | à l'installation, exigée par la bibliothèque de carte |
| `POST_NOTIFICATIONS` | vous prévenir qu'un trajet surveillé est perturbé | facultative, au moment où vous activez votre **première** surveillance, jamais avant |
| `WAKE_LOCK` | exigée par la bibliothèque de tâches d'Android (`androidx.work`), qui tient l'appareil éveillé le temps d'une vérification de quelques secondes | à l'installation |

`ACCESS_NETWORK_STATE` est une permission de niveau *normal* : elle ne vous est pas soumise et ne
donne accès qu'au fait que l'appareil soit connecté ou non — ni le nom du réseau, ni son adresse,
ni rien qui vous concerne. La bibliothèque de carte réclamait également l'accès à l'état du Wi-Fi ;
elle ne s'en sert nulle part, et Escale la retire de son manifeste.

`WAKE_LOCK` est également de niveau *normal* : elle ne donne accès à aucune donnée, elle empêche
seulement l'appareil de se rendormir pendant qu'une tâche s'exécute.

Aucune permission de stockage, de contacts, de **démarrage automatique** ni de **service en
arrière-plan** n'est demandée : la bibliothèque de tâches déclarait les deux dernières, Escale les
retire de son manifeste. Conséquence assumée : après un redémarrage de l'appareil, les
surveillances programmées sont remises en place à la prochaine ouverture de l'application, et non
au démarrage du téléphone.

**Aucune alarme exacte** (`SCHEDULE_EXACT_ALARM`) n'est demandée non plus. C'est un choix : la
vérification peut être décalée de quelques minutes par le système, ce qui est sans conséquence pour
une requête émise une heure à l'avance, et évite une permission intrusive.

## Favoris et historique — à venir

Le stockage des favoris — domicile, travail, lieux, arrêts, trajets — et celui de l'historique de
recherche **ne sont pas encore écrits**. Aucune recherche n'est donc conservée d'une session à
l'autre, et le réglage « Enregistrer l'historique » que vous voyez dans les réglages n'a pas encore
d'effet.

Quand ils existeront, ils resteront **locaux et uniquement locaux** : rien ne sera envoyé nulle
part, l'historique sera limité aux cinquante dernières recherches, désactivable et effaçable en un
appui. Domicile et travail resteront facultatifs et ne vous seront jamais réclamés : tant qu'ils ne
sont pas renseignés, l'application n'en dit rien du tout.

## Trajets surveillés

Un trajet mis en favori peut être **surveillé**. C'est la seule activité réseau d'Escale en dehors
de votre usage direct, elle est **désactivée par défaut**, elle ne vous est **jamais proposée
d'elle-même**, et elle s'arrête d'un appui.

Ce qui se passe alors, exactement :

- Vous choisissez une heure de départ habituelle et, si vous le voulez, des jours de la semaine.
  Sans jour choisi, la surveillance ne vaut que pour une date et s'éteint ensuite d'elle-même.
- **Une heure avant** ce départ, l'application envoie **une** requête au serveur MOTIS que vous avez
  configuré, pour savoir si quelque chose a changé. Une seule : ni vérification intermédiaire, ni
  boucle. Si elle échoue, elle est retentée **une fois** cinq minutes plus tard, puis abandonnée
  sans rien afficher. Si le serveur refuse l'identifiant enregistré — il est marqué expérimental et
  se périme —, la recherche d'origine est rejouée, ce qui fait au plus deux requêtes.
- Si vous avez consulté ce trajet dans l'application dans la demi-heure, **aucune requête n'est
  envoyée** : la donnée est déjà fraîche.
- Vous n'êtes prévenu que s'il y a quelque chose à dire : un retard au-delà du seuil que vous
  réglez (cinq minutes par défaut), une course supprimée, une perturbation en cours, ou un trajet
  devenu impossible. Sinon, rien. Un réglage permet de demander une notification même quand tout va
  bien ; il est désactivé par défaut.
- **Cinq trajets surveillés au maximum**, soit au plus cinq requêtes par jour où vous voyagez.

**Ce que cela implique, et qui doit être dit clairement** : ces requêtes partent à heure fixe, les
jours que vous avez choisis. Elles révèlent donc une **habitude de déplacement** à qui observe le
serveur ou le réseau, ce que votre usage manuel de l'application ne fait pas. C'est la raison pour
laquelle la surveillance est un choix explicite, annoncé en toutes lettres sur l'écran d'activation
et révocable à tout moment.

Ce qui n'a pas lieu : aucun service permanent, aucune tâche périodique, aucune synchronisation,
aucun relevé de votre position, et rien qui parte ailleurs que vers le serveur que vous avez choisi.
Si vous refusez la permission de notification, la surveillance reste utilisable : le résultat de la
dernière vérification s'affiche sur le trajet, à l'ouverture de l'application.

## Questions

Le code est public : <https://github.com/mgdx/escale>. Toute question sur ce document peut y être
posée sous forme d'issue.
