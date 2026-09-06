package io.github.mgdx.escale.core.model

import java.time.Duration
import kotlin.math.abs

// Les valeurs proposées, écrites une seule fois. Les nommer ici plutôt que dans l'écran de réglages
// permet de vérifier en JVM ce qui, sinon, ne se vérifierait qu'à l'œil : que la valeur par défaut
// de [SearchPreferences] figure bien parmi les choix offerts, et qu'un réglage laissé intact
// produise donc exactement la requête d'aujourd'hui (SPEC.md § 5.6).

private const val PEDESTRIAN_SPEED_VERY_SLOW = 0.8
private const val PEDESTRIAN_SPEED_SLOW = 1.0
private const val PEDESTRIAN_SPEED_FAST = 1.4
private const val PEDESTRIAN_SPEED_VERY_FAST = 1.7

private const val CYCLING_SPEED_VERY_SLOW = 3.3
private const val CYCLING_SPEED_SLOW = 4.2
private const val CYCLING_SPEED_FAST = 6.1
private const val CYCLING_SPEED_VERY_FAST = 7.2

private const val TRANSFER_MARGIN_SHORT_MINUTES = 2L
private const val TRANSFER_MARGIN_MEDIUM_MINUTES = 5L
private const val TRANSFER_MARGIN_LONG_MINUTES = 10L

/** Au-delà, la limite ne change plus rien : les serveurs plafonnent d'eux-mêmes. */
private const val MAX_TRANSFERS_CEILING = 4

/**
 * Vitesse de marche proposée par l'écran de réglages (`pedestrianSpeed`, SPEC.md § 5.6).
 *
 * L'usager choisit une allure, pas un nombre de mètres par seconde : l'API raisonne en m/s, mais
 * personne ne connaît sa vitesse de marche sous cette forme.
 */
enum class PedestrianSpeedOption(val metersPerSecond: Double) {
  VERY_SLOW(PEDESTRIAN_SPEED_VERY_SLOW),
  SLOW(PEDESTRIAN_SPEED_SLOW),
  NORMAL(SearchPreferences.DEFAULT_PEDESTRIAN_SPEED),
  FAST(PEDESTRIAN_SPEED_FAST),
  VERY_FAST(PEDESTRIAN_SPEED_VERY_FAST),
  ;

  companion object {
    /** L'allure retenue tant que l'usager n'a rien réglé. */
    val DEFAULT: PedestrianSpeedOption = NORMAL

    /**
     * L'allure la plus proche d'une valeur persistée. Une préférence écrite par une version
     * antérieure, ou un fichier abîmé, se rattache au choix voisin au lieu de n'en cocher aucun.
     */
    fun nearest(metersPerSecond: Double): PedestrianSpeedOption =
      entries.minByOrNull { abs(it.metersPerSecond - metersPerSecond) } ?: DEFAULT
  }
}

/** Vitesse à vélo proposée par l'écran de réglages (`cyclingSpeed`, SPEC.md § 5.6). */
enum class CyclingSpeedOption(val metersPerSecond: Double) {
  VERY_SLOW(CYCLING_SPEED_VERY_SLOW),
  SLOW(CYCLING_SPEED_SLOW),
  NORMAL(SearchPreferences.DEFAULT_CYCLING_SPEED),
  FAST(CYCLING_SPEED_FAST),
  VERY_FAST(CYCLING_SPEED_VERY_FAST),
  ;

  companion object {
    val DEFAULT: CyclingSpeedOption = NORMAL

    fun nearest(metersPerSecond: Double): CyclingSpeedOption =
      entries.minByOrNull { abs(it.metersPerSecond - metersPerSecond) } ?: DEFAULT
  }
}

/** Marge ajoutée à chaque correspondance (`additionalTransferTime`, SPEC.md § 5.6). */
object AdditionalTransferTimeOptions {

  val VALUES: List<Duration> = listOf(
    Duration.ZERO,
    Duration.ofMinutes(TRANSFER_MARGIN_SHORT_MINUTES),
    Duration.ofMinutes(TRANSFER_MARGIN_MEDIUM_MINUTES),
    Duration.ofMinutes(TRANSFER_MARGIN_LONG_MINUTES),
  )

  val DEFAULT: Duration = SearchPreferences().additionalTransferTime

  /** Le choix le plus proche d'une valeur persistée, pour qu'un des choix soit toujours coché. */
  fun nearest(margin: Duration): Duration = VALUES.minByOrNull { abs(it.seconds - margin.seconds) } ?: DEFAULT
}

/**
 * Nombre maximal de correspondances (`maxTransfers`, SPEC.md § 5.6).
 *
 * `null` n'est pas un trou dans la liste : c'est le choix « sans limite », celui qui n'envoie pas
 * le paramètre et laisse le serveur appliquer la sienne.
 */
object MaxTransfersOptions {

  val VALUES: List<Int?> = listOf<Int?>(null) + (0..MAX_TRANSFERS_CEILING).toList()

  val DEFAULT: Int? = SearchPreferences().maxTransfers

  /** Ramène une valeur persistée dans la liste offerte, sans jamais rendre un choix absent. */
  fun nearest(maxTransfers: Int?): Int? = when {
    maxTransfers == null -> null
    maxTransfers < 0 -> null
    maxTransfers > MAX_TRANSFERS_CEILING -> MAX_TRANSFERS_CEILING
    else -> maxTransfers
  }
}

/**
 * Le filtre unique des types de véhicules en libre-service (SPEC.md § 5.2).
 *
 * « L'utilisateur qui ne veut pas de trottinettes doit pouvoir les exclure partout, d'un seul
 * endroit » : ce filtre alimente à lui seul `directRentalFormFactors`, par
 * [io.github.mgdx.escale.core.query.RentalFormFactorQuery].
 *
 * **Un ensemble vide signifie « aucun filtre », et non « aucun véhicule »** : c'est la convention de
 * l'API, reprise telle quelle par [SearchPreferences]. Toute la subtilité de cet objet est là — ce
 * que l'écran affiche coché et ce qui est persisté ne coïncident pas quand tout est accepté.
 */
object RentalFormFactorSelection {

  /**
   * **Les types de véhicules qu'Escale peut proposer**, dans l'ordre imposé par SPEC.md § 5.2, et
   * du même coup les cases à cocher de l'écran de réglages.
   *
   * La liste est celle de l'onglet Vélo, seul onglet qui emprunte encore un véhicule partagé
   * depuis que le rabattement du transport en commun se fait à pied : proposer une case qui ne
   * changerait rien à aucune recherche mentirait à l'usager. Voiture, cyclomoteur, vélo cargo et
   * « autre véhicule » n'y figurent donc plus — un trajet motorisé sous un pictogramme de vélo
   * n'aurait de toute façon pas sa place dans cet onglet.
   */
  val OFFERED: List<RentalFormFactor> = listOf(
    RentalFormFactor.BICYCLE,
    RentalFormFactor.SCOOTER_STANDING,
    RentalFormFactor.SCOOTER_SEATED,
  )

  /**
   * Ce que l'écran coche : rien de filtré signifie que tout est accepté.
   *
   * Un type persisté par une version antérieure mais qui n'est plus offert est ignoré ici ; s'il
   * ne reste rien, le réglage vaut « aucun filtre », comme un réglage neuf.
   */
  fun selected(allowed: Set<RentalFormFactor>): Set<RentalFormFactor> {
    val retained = allowed.intersect(OFFERED.toSet())
    return if (retained.isEmpty()) OFFERED.toSet() else retained
  }

  /**
   * Le réglage à persister après avoir coché ou décoché une case.
   *
   * Deux garde-fous, tous deux imposés par la convention de l'API :
   * - tout accepter se persiste en ensemble vide, pour ne pas envoyer un filtre qui n'en est pas un
   *   et pour continuer à profiter des types que MOTIS ajouterait plus tard ;
   * - décocher la dernière case est refusé : un ensemble vide signifierait « tous les véhicules »,
   *   soit exactement l'inverse de ce que l'usager vient de demander.
   */
  fun toggled(allowed: Set<RentalFormFactor>, formFactor: RentalFormFactor, accepted: Boolean): Set<RentalFormFactor> {
    val updated = selected(allowed).let { if (accepted) it + formFactor else it - formFactor }
    return when {
      updated.isEmpty() -> allowed
      updated == OFFERED.toSet() -> emptySet()
      else -> updated
    }
  }

  /** Vrai quand décocher cette case reviendrait à n'accepter aucun véhicule : la case reste figée. */
  fun isLastAccepted(allowed: Set<RentalFormFactor>, formFactor: RentalFormFactor): Boolean =
    selected(allowed) == setOf(formFactor)
}
