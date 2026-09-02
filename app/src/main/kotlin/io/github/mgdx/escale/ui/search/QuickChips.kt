package io.github.mgdx.escale.ui.search

import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.ui.session.SearchDraft

/** Au-delà de quelques puces, la rangée devient un pense-bête illisible sous les champs. */
private const val RECENT_CHIP_LIMIT = 5

/**
 * Les puces d'accès rapide à afficher (SPEC.md § 5.1).
 *
 * Deux règles, et deux seulement :
 * 1. les puces ne s'affichent **que quand les champs sont vides** — dès qu'un point est choisi,
 *    elles laissent la place ;
 * 2. **une puce absente n'est simplement pas affichée** : pas de puce grisée, pas d'invite à
 *    renseigner son domicile. SPEC.md § 5.5 le redit — « l'application ne les réclame jamais
 *    d'elle-même, en particulier pas à la première ouverture ».
 *
 * C'est une décision d'affichage pure, donc une fonction ordinaire, donc testable sans rendu.
 */
fun quickChips(
  draft: SearchDraft,
  home: Location?,
  work: Location?,
  recentSearches: List<RecentSearch>,
): List<QuickChip> {
  if (draft.from != null || draft.to != null) return emptyList()
  return buildList {
    home?.let { add(QuickChip.Saved(SavedPlaceKind.HOME, it)) }
    work?.let { add(QuickChip.Saved(SavedPlaceKind.WORK, it)) }
    recentSearches.take(RECENT_CHIP_LIMIT).forEach { add(QuickChip.Recent(it)) }
  }
}

/**
 * Les entrées en tête de la liste d'autocomplétion (SPEC.md § 5.1).
 *
 * « Ma position » n'apparaît que si une position est déjà connue **sans rien demander à l'usager** :
 * la permission de localisation se demande au bouton de position de la carte, jamais depuis un
 * champ de saisie, et l'application reste pleinement utilisable sans elle (SPEC.md § 11).
 * Domicile et travail suivent la même règle que les puces : absents, ils ne s'affichent pas.
 */
fun searchShortcuts(myLocationKnown: Boolean, home: Location?, work: Location?): List<SearchShortcut> = buildList {
  if (myLocationKnown) add(SearchShortcut.MY_LOCATION)
  if (home != null) add(SearchShortcut.HOME)
  if (work != null) add(SearchShortcut.WORK)
  add(SearchShortcut.PICK_ON_MAP)
}
