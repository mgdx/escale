package io.github.mgdx.escale.core.model

/**
 * Filtre par mode de transport de l'écran « prochains départs » (SPEC.md § 5.4).
 *
 * **Ce fichier est l'endroit exact où se joue le piège des parapluies** (docs/architecture.md
 * § 11.5, docs/motis-api.md piège n° 8). Deux valeurs de l'enum `Mode` de MOTIS n'en sont pas :
 *
 * ```
 * TRANSIT : TRAM,FERRY,AIRPLANE,BUS,COACH,RAIL,ODM,RIDE_SHARING,FUNICULAR,AERIAL_LIFT,OTHER
 * RAIL    : HIGHSPEED_RAIL,LONG_DISTANCE,NIGHT_RAIL,REGIONAL_RAIL,SUBURBAN,SUBWAY
 * ```
 *
 * (`docs/motis-openapi.yaml`, schéma `Mode`, lignes 3838 et 3845.)
 *
 * D'où les deux ensembles distincts portés par chaque filtre :
 *
 * - [requestModes] part **au serveur**, dans le paramètre `mode` de `/api/v6/stoptimes`. Il ne
 *   contient que des **feuilles**, jamais un parapluie. Envoyer `RAIL` pour un filtre « train »
 *   ferait remonter le métro avec, puisque `RAIL` le couvre — vérifié sur `api.transitous.org` :
 *   la même requête à Hamburg Hbf rend `SUBWAY` avec `mode=RAIL` et ne le rend pas avec les cinq
 *   feuilles listées une à une (fixture `stoptimes_rail_only.json`) ;
 * - [matchedModes] compare des modes **reçus** du serveur, déjà développés par lui. Il vaut
 *   [requestModes] plus le parapluie lui-même : rien n'interdit à un serveur de renvoyer `RAIL`
 *   tel quel sur un arrêt, et le laisser tomber ferait disparaître la puce « train ».
 *
 * Le défaut que cette distinction évite ne produit aucune erreur, seulement des départs absents :
 * un filtre « train » qui chercherait littéralement `RAIL` raterait `REGIONAL_RAIL`, c'est-à-dire
 * la moitié des trains d'une gare régionale. C'est le défaut qui a déjà coûté un aller-retour sur
 * ce projet, à Châtelet - Les Halles (docs/architecture.md § 11.5).
 *
 * L'ordre de déclaration est celui des puces à l'écran : du mode le plus structurant au plus rare.
 */
enum class DepartureModeFilter(
  /** Les feuilles envoyées au serveur dans `mode`. Jamais un parapluie. */
  val requestModes: Set<TransitMode>,
  /** Les parapluies acceptés en plus, à la seule comparaison d'un mode **reçu**. */
  private val umbrellas: Set<TransitMode> = emptySet(),
) {
  /**
   * Le train, métro exclu.
   *
   * Les cinq feuilles de `RAIL` moins `SUBWAY`, qui a sa propre puce : un usager qui demande
   * « train » à Hamburg Hbf ne demande pas le U-Bahn.
   */
  TRAIN(
    requestModes = setOf(
      TransitMode.HIGHSPEED_RAIL,
      TransitMode.LONG_DISTANCE,
      TransitMode.NIGHT_RAIL,
      TransitMode.REGIONAL_RAIL,
      TransitMode.SUBURBAN,
    ),
    umbrellas = setOf(TransitMode.RAIL),
  ),

  SUBWAY(requestModes = setOf(TransitMode.SUBWAY)),

  TRAM(requestModes = setOf(TransitMode.TRAM)),

  /** Bus urbain et autocar : l'API les distingue, l'usager qui attend au quai non. */
  BUS(requestModes = setOf(TransitMode.BUS, TransitMode.COACH)),

  FERRY(requestModes = setOf(TransitMode.FERRY)),

  /**
   * Tout le reste du transport en commun : avion, funiculaire, câble, transport à la demande.
   *
   * `TRANSIT` figure parmi les parapluies acceptés en lecture, pas parmi les modes envoyés : le
   * demander au serveur reviendrait à ne rien filtrer du tout.
   */
  OTHER(
    requestModes = setOf(
      TransitMode.AIRPLANE,
      TransitMode.FUNICULAR,
      TransitMode.AERIAL_LIFT,
      TransitMode.ODM,
      TransitMode.RIDE_SHARING,
      TransitMode.FLEX,
      TransitMode.OTHER,
    ),
    umbrellas = setOf(TransitMode.TRANSIT),
  ),
  ;

  /** Les modes **reçus** que cette puce recouvre : les feuilles, plus le parapluie correspondant. */
  val matchedModes: Set<TransitMode>
    get() = requestModes + umbrellas

  /** Vrai si un mode rendu par le serveur relève de cette puce. */
  fun matches(mode: TransitMode): Boolean = mode in matchedModes
}

/**
 * Le choix des puces de filtre offertes à un arrêt (SPEC.md § 5.4).
 *
 * La règle vit dans `:core` parce qu'elle est calculatoire et vérifiable en JVM
 * (docs/architecture.md § 1) : l'interface choisit le libellé et le pictogramme, jamais la liste.
 */
object DepartureFilters {

  /** En deçà, filtrer n'a aucun sens : une seule puce ne trie rien. */
  private const val MINIMUM_USEFUL_FILTERS = 2

  /**
   * Les puces à offrir pour un arrêt dont le serveur annonce [modes].
   *
   * @param modes modes **reçus** — ceux de `/api/v6/stop`, de `/api/v6/map/stops` ou des départs
   *   déjà affichés. Ce sont des feuilles, et c'est [DepartureModeFilter.matchedModes] qui les
   *   reconnaît : un arrêt annoncé en `REGIONAL_RAIL` seul, comme Châtelet - Les Halles, obtient
   *   bien la puce « train ».
   * @return les puces dans l'ordre de déclaration, ou une liste **vide** quand il y en aurait moins
   *   de deux : un arrêt desservi par un seul mode n'a rien à filtrer.
   */
  fun available(modes: Collection<TransitMode>): List<DepartureModeFilter> {
    val distinct = modes.toSet()
    val filters = DepartureModeFilter.entries.filter { filter -> distinct.any(filter::matches) }
    return if (filters.size < MINIMUM_USEFUL_FILTERS) emptyList() else filters
  }

  /** La puce dont relève un mode reçu, ou `null` pour un mode de rue, qui n'a rien à faire ici. */
  fun of(mode: TransitMode): DepartureModeFilter? = DepartureModeFilter.entries.firstOrNull { it.matches(mode) }
}
