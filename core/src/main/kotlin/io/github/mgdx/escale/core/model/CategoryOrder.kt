package io.github.mgdx.escale.core.model

/**
 * L'ordre des onglets de l'écran de résultats, tel que l'usager l'a réglé (SPEC.md § 5.2 et § 5.6).
 *
 * L'ordre livré est celui de la spec ; l'écran « Ordre des catégories » permet de le changer, parce
 * que l'onglet le plus utile n'est pas le même pour qui prend le train tous les jours et pour qui
 * ne se déplace qu'à vélo. Il ne change **que la disposition** : les requêtes envoyées, leurs
 * paramètres et le contenu de chaque onglet sont exactement les mêmes.
 *
 * Toutes les fonctions rendent une liste **complète et sans doublon** : la persistance peut
 * contenir n'importe quoi — une valeur écrite par une version future, un fichier tronqué, une
 * catégorie disparue — et une catégorie absente de l'ordre serait un onglet inatteignable.
 */
object CategoryOrder {

  /** L'ordre par défaut, celui de SPEC.md § 5.2. */
  val DEFAULT: List<JourneyCategory> = JourneyCategory.entries

  /**
   * Relit un ordre persisté, écrit sous forme de noms d'énumération.
   *
   * Un nom inconnu est ignoré, les manquants sont ajoutés à la fin dans l'ordre par défaut : une
   * catégorie ajoutée par une version ultérieure apparaît donc en dernier plutôt que de disparaître.
   */
  fun of(names: List<String>): List<JourneyCategory> =
    sanitized(names.mapNotNull { name -> JourneyCategory.entries.firstOrNull { it.name == name } })

  /** Complète un ordre partiel et en retire les doublons. */
  fun sanitized(order: List<JourneyCategory>): List<JourneyCategory> {
    val kept = order.distinct()
    return kept + DEFAULT.filterNot { it in kept }
  }

  /**
   * Déplace la catégorie de rang [from] au rang [to], les autres se resserrant derrière elle.
   *
   * C'est exactement ce que fait un glissé-déposé : un retrait puis une insertion, jamais un
   * échange deux à deux — permuter ferait sauter la catégorie survolée par-dessus les autres.
   * Un rang hors de la liste rend l'ordre inchangé : le geste n'a alors rien déplacé.
   */
  fun moved(order: List<JourneyCategory>, from: Int, to: Int): List<JourneyCategory> {
    if (from !in order.indices || to !in order.indices || from == to) return order
    return order.toMutableList().apply { add(to, removeAt(from)) }
  }
}
