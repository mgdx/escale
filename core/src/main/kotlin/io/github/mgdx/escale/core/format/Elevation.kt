package io.github.mgdx.escale.core.format

import io.github.mgdx.escale.core.model.TravelStep

/**
 * Le dénivelé cumulé d'un cheminement, en mètres (SPEC.md § 5.3, « dénivelé si disponible »).
 *
 * Les deux valeurs sont positives : [upMeters] est ce qui monte, [downMeters] ce qui descend.
 */
data class Elevation(val upMeters: Int, val downMeters: Int) {
  /**
   * Vrai quand le cheminement présente un relief à annoncer.
   *
   * Un serveur sans modèle de terrain renvoie des zéros plutôt que rien : afficher « +0 m / −0 m »
   * n'apprendrait rien et encombrerait la portion. L'interface n'affiche la ligne que si elle a
   * quelque chose à dire.
   */
  val hasRelief: Boolean
    get() = upMeters > 0 || downMeters > 0
}

/**
 * Le dénivelé cumulé de [steps], ou `null` si aucune manœuvre ne porte l'information.
 *
 * MOTIS ne remplit `elevationUp` et `elevationDown` que lorsque le serveur dispose d'un modèle de
 * terrain : `null` signifie « le serveur ne sait pas », ce qui n'est pas la même chose que « c'est
 * plat ». La distinction est faite ici, une fois, plutôt que devinée à l'affichage.
 */
fun elevationOf(steps: List<TravelStep>): Elevation? {
  if (steps.none { it.elevationUpMeters != null || it.elevationDownMeters != null }) return null
  return Elevation(
    upMeters = steps.sumOf { it.elevationUpMeters ?: 0 },
    downMeters = steps.sumOf { it.elevationDownMeters ?: 0 },
  )
}
