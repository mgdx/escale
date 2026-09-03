package io.github.mgdx.escale.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * La surveillance d'un trajet favori (SPEC.md § 5.5.1).
 *
 * Table séparée de `favorite_journeys`, et non colonnes ajoutées à celle-ci : **l'existence de la
 * ligne est l'état « surveillé »**. Un booléen sur le favori aurait laissé traîner une heure et des
 * jours de surveillance derrière une bascule éteinte, alors que la fonction est explicitement
 * opt-in et qu'une configuration abandonnée ne doit rien laisser derrière elle.
 *
 * La clé étrangère est en `CASCADE` : supprimer un trajet favori supprime sa surveillance dans la
 * même transaction. Aucune tâche de fond ne peut donc rester programmée pour un trajet qui
 * n'existe plus.
 *
 * Rien ici ne programme quoi que ce soit : `WorkManager`, les notifications et la permission
 * `POST_NOTIFICATIONS` appartiennent au lot suivant.
 */
@Entity(
  tableName = "watched_journeys",
  foreignKeys = [
    ForeignKey(
      entity = FavoriteJourneyEntity::class,
      parentColumns = ["id"],
      childColumns = ["journeyId"],
      onDelete = ForeignKey.CASCADE,
    ),
  ],
)
internal data class WatchedJourneyEntity(
  @PrimaryKey val journeyId: Long,
  /**
   * Heure de départ habituelle, en minutes depuis minuit.
   *
   * Une heure locale, pas un instant : « 8 h 10 » reste 8 h 10 après un changement d'heure d'été
   * ou de fuseau, ce qu'un horodatage absolu ne saurait pas rendre.
   */
  val departureMinuteOfDay: Int,
  /** Noms des valeurs de `DayOfWeek`, séparés par des virgules. Vide pour une date unique. */
  val daysOfWeek: String,
  /** Jour depuis l'époque Unix, pour une surveillance sans récurrence. Nul quand il y a récurrence. */
  val date: Long?,
  /** L'`id` d'itinéraire de `refresh-itinerary`, expérimental et périssable. Nul tant qu'inconnu. */
  val itineraryId: String?,
  val itineraryCapturedAt: Long?,
  /** Dernière consultation dans l'application : règle des 30 minutes de SPEC.md § 5.5.1. */
  val lastViewedAt: Long?,
  val createdAt: Long,
)
