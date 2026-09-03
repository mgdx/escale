package io.github.mgdx.escale.core.model

import java.time.Instant

/**
 * Un lieu nommé mis en favori (SPEC.md § 5.5 : « autres **lieux nommés** »).
 *
 * Le nom donné par l'usager est porté ici, et non dans [Location] : « Chez Maman » est un nom
 * privé, attaché à un favori, là où `Location.name` est le nom que le serveur rend et dont dépend
 * le repli de SPEC.md § 5.6.1. Écraser le second par le premier ferait perdre le nom réel de
 * l'arrêt ; ne garder que le second ferait ressortir « Chez Maman » sous son adresse, ce qui vidait
 * de son sens la notion de lieu nommé.
 *
 * [id] est l'identifiant attribué par le stockage. C'est lui qui désigne la ligne à supprimer :
 * deux favoris homonymes au même point — le café et l'appartement au-dessus — restent distincts.
 */
data class FavoritePlace(
  val id: Long,
  /** Nom donné par l'usager, ou nul quand il s'est contenté du nom du lieu. */
  val label: String?,
  val location: Location,
  val createdAt: Instant,
) {
  /** Ce qu'il faut afficher : le nom de l'usager s'il en a donné un, celui du serveur sinon. */
  val displayName: String get() = label?.takeIf { it.isNotBlank() } ?: location.name
}

/**
 * Un trajet mis en favori : un couple départ / arrivée, éventuellement avec des préférences.
 *
 * Le jalon des trajets surveillés (SPEC.md § 5.5.1) viendra y accrocher sa configuration de
 * surveillance ; ce type ne la porte pas encore.
 */
data class FavoriteJourney(
  val id: Long,
  /** Nom donné par l'usager, ou nul pour laisser l'interface composer « Départ → Arrivée ». */
  val label: String?,
  val from: Location,
  val to: Location,
  val category: JourneyCategory = JourneyCategory.TRANSIT,
  val createdAt: Instant,
)

/**
 * Une recherche passée, conservée localement (SPEC.md § 5.5, 50 entrées au plus).
 *
 * [time] en fait partie **parce qu'une puce de dernière recherche relance la recherche**
 * (SPEC.md § 5.1) : rejouer « Bastille → Gare de Lyon » sans « arriver avant 9 h 00 » ne rejoue pas
 * la même recherche, cela en lance une autre sous le même libellé.
 *
 * Les préférences de recherche, elles, n'y sont **pas** : ce sont des réglages globaux de
 * SPEC.md § 5.6, et les figer dans une entrée d'historique ferait diverger une recherche rejouée
 * des réglages en cours sans que rien ne l'annonce — même raison qu'un trajet favori n'en porte pas.
 */
data class SearchHistoryEntry(
  val id: Long,
  val from: Location,
  val to: Location,
  val time: TimeChoice,
  val searchedAt: Instant,
)
