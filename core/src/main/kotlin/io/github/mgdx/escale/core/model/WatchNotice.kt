package io.github.mgdx.escale.core.model

import io.github.mgdx.escale.core.format.transitLineLabel
import java.time.Duration
import java.time.Instant

/** Ce qu'une notification de surveillance a à annoncer (SPEC.md § 5.5.1). */
enum class WatchIssue {
  /** Retard supérieur au seuil réglable. */
  DELAYED,

  /** Une course du trajet est supprimée. */
  CANCELLED,

  /** Une perturbation touche une des portions à l'heure du départ. */
  DISRUPTED,

  /** Le serveur ne propose plus aucun trajet : « trajet devenu impossible ». */
  IMPOSSIBLE,

  /** Rien à signaler, et l'usager a demandé à être prévenu quand même. */
  NOTHING,
}

/**
 * La notification à émettre, ou plutôt sa matière : SPEC.md § 5.5.1 en fixe le contenu — « la ligne
 * concernée, la nature du problème, la nouvelle heure de départ conseillée s'il en existe une ».
 *
 * C'est un type de `:core` et non un texte : la mise en mots appartient à `:app` et à ses
 * ressources, qui existent en anglais et en français.
 */
data class WatchNotice(
  val issue: WatchIssue,
  /** La ligne concernée, `null` quand le serveur ne la nomme pas ou qu'aucune ne l'est. */
  val lineName: String? = null,
  /** L'écart constaté, `null` quand la nature du problème n'en est pas un. */
  val delay: Duration? = null,
  /** L'heure de départ conseillée, `null` quand il n'y en a pas de meilleure à proposer. */
  val suggestedDeparture: Instant? = null,
  /**
   * Le message du transporteur, quand c'est une perturbation qui motive la notification.
   *
   * C'est du **texte simple** venu du serveur, réduit à l'entrée par `:data` : l'interface
   * l'affiche tel quel et n'en interprète rien (voir `Disruption`).
   */
  val detail: String? = null,
)

/**
 * Les deux réglages de la notification (SPEC.md § 5.5.1).
 *
 * Ils sont dans `:core` avec leurs valeurs par défaut pour que la règle et son seuil ne puissent
 * pas diverger : l'interface les lit, elle ne les redéfinit pas.
 */
data class WatchAlertSettings(
  /** « Retard supérieur à un seuil réglable (5 minutes par défaut). » */
  val delayThreshold: Duration = DEFAULT_THRESHOLD,
  /** « Un réglage "me prévenir même si tout va bien" existe, désactivé par défaut. » */
  val notifyWhenNothingChanged: Boolean = false,
) {
  companion object {
    val DEFAULT_THRESHOLD: Duration = Duration.ofMinutes(5)

    /** Bornes proposées par l'interface : en deçà d'une minute le seuil n'a plus de sens. */
    val THRESHOLD_CHOICES: List<Duration> =
      listOf(1L, 2L, 5L, 10L, 15L, 30L).map(Duration::ofMinutes)
  }
}

/**
 * La décision de notifier, ou de se taire (SPEC.md § 5.5.1).
 *
 * **C'est le cœur de la fonction, et il tourne sans témoin** : personne ne regarde l'écran quand
 * cette comparaison a lieu. Elle est donc en Kotlin pur et couverte cas par cas en JVM
 * (docs/architecture.md § 1) — y compris le cas « rien à signaler », qui est le plus fréquent et
 * dont l'unique preuve est qu'aucune notification ne part.
 *
 * **Ce que « perturbation nouvelle » veut dire ici.** L'application ne conserve pas de photographie
 * du trajet enregistré : elle garde son heure de départ habituelle et l'identifiant de son
 * itinéraire, rien de plus (`WatchedJourney`). « Nouvelle » se lit donc **par rapport à
 * l'occurrence surveillée** : est retenue une perturbation en vigueur à l'heure du départ, sur une
 * des portions. Comme il n'y a **qu'une vérification par occurrence**, une même perturbation n'est
 * jamais annoncée deux fois pour le même départ ; une perturbation permanente d'un réseau, elle,
 * concerne bien chaque trajet qu'elle touche.
 */
object JourneyWatchComparison {

  /**
   * Ce qu'il y a à dire du trajet [refreshed], ou `null` s'il n'y a rien à dire.
   *
   * @param expectedDeparture l'heure de départ surveillée, celle qu'a fixée l'usager. Elle sert de
   *   référence au décalage : le repli sur `plan` peut rendre **un autre trajet** que celui
   *   d'origine, et partir vingt minutes plus tard est une différence utile même quand le serveur
   *   n'annonce aucun retard sur ce trajet-là.
   * @param refreshed le trajet rafraîchi, ou `null` quand le serveur a répondu sans proposer aucun
   *   trajet — le cas « trajet devenu impossible ». Un **échec** de la requête ne passe pas par
   *   ici : il ne s'affiche jamais (SPEC.md § 5.5.1).
   *
   * L'ordre des cas est celui de leur gravité : une course supprimée prime sur le retard qu'elle
   * provoque, et un retard prime sur le message qui l'explique. L'usager doit lire d'abord ce qui
   * l'empêche de partir.
   */
  fun compare(
    expectedDeparture: Instant,
    refreshed: Journey?,
    settings: WatchAlertSettings = WatchAlertSettings(),
  ): WatchNotice? {
    if (refreshed == null) return WatchNotice(issue = WatchIssue.IMPOSSIBLE)
    val cancelled = refreshed.legs.firstOrNull { it.cancelled }
    val delay = departureDelay(refreshed, expectedDeparture)
    val disruption = disruption(refreshed)
    val suggestion = refreshed.startTime.takeIf { it != expectedDeparture }
    return when {
      cancelled != null -> WatchNotice(WatchIssue.CANCELLED, lineOf(cancelled), suggestedDeparture = suggestion)

      delay >= settings.delayThreshold ->
        WatchNotice(WatchIssue.DELAYED, lineOf(delayedLeg(refreshed)), delay, suggestion)

      disruption != null -> WatchNotice(
        issue = WatchIssue.DISRUPTED,
        lineName = lineOf(disruption.first),
        suggestedDeparture = suggestion,
        detail = disruption.second.headerText,
      )

      settings.notifyWhenNothingChanged -> WatchNotice(WatchIssue.NOTHING, suggestedDeparture = refreshed.startTime)

      else -> null
    }
  }

  /**
   * Le retard au départ : le plus grand de l'écart au temps réel et du décalage à l'heure
   * surveillée.
   *
   * **Le retard à l'arrivée n'entre pas dans la décision**, et c'est délibéré : une heure avant de
   * partir, ce qui se décide est l'heure à laquelle on sort de chez soi. Un trajet parti à l'heure
   * mais rallongé en route ne changerait rien à ce que l'usager ferait de la notification, et
   * l'aurait fait sonner pour rien.
   *
   * Un trajet en avance rend une durée négative, qui ne franchit aucun seuil : partir plus tôt que
   * prévu n'est pas un problème, et le trajet enregistré reste valable.
   */
  private fun departureDelay(journey: Journey, expectedDeparture: Instant): Duration = maxOf(
    Duration.between(journey.scheduledStartTime, journey.startTime),
    Duration.between(expectedDeparture, journey.startTime),
  )

  /** La portion qui porte le retard : la première en transport en commun, à défaut la première. */
  private fun delayedLeg(journey: Journey): JourneyLeg? =
    journey.legs.firstOrNull { it is JourneyLeg.Transit } ?: journey.legs.firstOrNull()

  /**
   * La première portion touchée par une perturbation en vigueur à son propre départ, et le message
   * qui l'annonce.
   *
   * Le tri des périodes d'impact n'est pas refait ici : `Disruptions.inEffect` le fait déjà pour
   * les écrans, et une règle appliquée à deux endroits est une règle qui finit par diverger. Les
   * perturbations sans effet sur le service — « service supplémentaire », « aucun effet » — ne
   * réveillent personne.
   */
  private fun disruption(journey: Journey): Pair<JourneyLeg, Disruption>? = journey.legs
    .firstNotNullOfOrNull { leg ->
      Disruptions.inEffect(leg.alerts, leg.startTime)
        .firstOrNull { it.effect !in HARMLESS_EFFECTS }
        ?.let { leg to it }
    }

  private fun lineOf(leg: JourneyLeg?): String? = (leg as? JourneyLeg.Transit)?.let(::transitLineLabel)

  /** Une perturbation qui n'annonce rien de gênant n'a pas à faire vibrer un téléphone. */
  private val HARMLESS_EFFECTS = setOf(
    DisruptionEffect.NO_EFFECT,
    DisruptionEffect.ADDITIONAL_SERVICE,
  )
}
