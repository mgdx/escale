package io.github.mgdx.escale.core.model

import java.time.Instant

/**
 * Ce que l'interface a le droit d'annoncer sur les perturbations d'un trajet (SPEC.md § 5.2).
 *
 * Une `Alert` porte ses **périodes d'impact** : le service n'est perturbé que pendant celles-ci.
 * Afficher « perturbation en vigueur » pour des travaux prévus le mois prochain serait faux, et
 * les taire complètement le serait tout autant — d'où la séparation, faite ici, entre ce qui est
 * en vigueur et le reste.
 *
 * La règle est dans `:core` parce qu'elle est calculatoire et vérifiable en JVM
 * (docs/architecture.md § 1). L'interface choisit la couleur et le pictogramme, jamais le tri.
 */
object Disruptions {

  /**
   * Les perturbations de [alerts] effectivement en vigueur à [at], sans doublon.
   *
   * Deux règles, tranchées ici pour ne pas l'être dans chaque écran :
   * - **une perturbation sans période d'impact est en vigueur.** L'API autorise l'absence de
   *   période, et le serveur n'attache l'alerte à la portion que parce qu'elle la concerne ;
   * - **[at] nul ne permet pas de juger** : tout est alors rendu. Cacher une perturbation faute de
   *   savoir l'heure serait le mauvais côté de l'erreur.
   *
   * Le dédoublonnage n'est pas cosmétique : un trajet dont deux portions empruntent le même réseau
   * porte deux fois le même message, et l'annoncer deux fois ferait croire à deux perturbations.
   */
  fun inEffect(alerts: List<Disruption>, at: Instant?): List<Disruption> {
    val distinct = alerts.distinct()
    if (at == null) return distinct
    return distinct.filter { it.isInEffect(at) }
  }

  /** Les perturbations de [alerts] qui ne sont **pas** en vigueur à [at], sans doublon. */
  fun upcoming(alerts: List<Disruption>, at: Instant?): List<Disruption> {
    if (at == null) return emptyList()
    return alerts.distinct().filterNot { it.isInEffect(at) }
  }

  /**
   * La gravité la plus forte de [alerts], ou `null` si la liste est vide.
   *
   * C'est elle qu'un bandeau annonce en toutes lettres : SPEC.md § 9 interdit de la confier à la
   * seule couleur.
   */
  fun worstSeverity(alerts: List<Disruption>): DisruptionSeverity? = alerts.maxByOrNull { it.severity.rank() }?.severity
}

/** Vrai si le service est perturbé à [instant]. Sans période d'impact, la perturbation vaut toujours. */
fun Disruption.isInEffect(instant: Instant): Boolean = periods.isEmpty() || periods.any { it.contains(instant) }

/**
 * Vrai si [instant] tombe dans la fenêtre.
 *
 * Une borne nulle est ouverte — « depuis toujours », « jusqu'à nouvel ordre » — comme l'autorise
 * le schéma `TimeRange`. La fin est exclue : une perturbation qui se termine à 10:00 n'est plus en
 * vigueur à 10:00.
 */
fun TimeWindow.contains(instant: Instant): Boolean {
  if (start != null && instant.isBefore(start)) return false
  if (end != null && !instant.isBefore(end)) return false
  return true
}

/**
 * L'ordre de gravité, du plus anodin au plus grave, **écrit à la main** plutôt que déduit de
 * l'ordre de déclaration : l'énumération décalque le schéma de l'API, dont l'ordre n'est pas un
 * engagement.
 */
private val SEVERITY_ORDER = listOf(
  DisruptionSeverity.UNKNOWN_SEVERITY,
  DisruptionSeverity.INFO,
  DisruptionSeverity.WARNING,
  DisruptionSeverity.SEVERE,
)

private fun DisruptionSeverity.rank(): Int = SEVERITY_ORDER.indexOf(this)
