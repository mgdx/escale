package io.github.mgdx.escale.core.geo

import io.github.mgdx.escale.core.model.LatLon

/**
 * Décodeur de polylignes encodées au format Google.
 *
 * **Piège signalé par SPEC.md § 4.3 :** MOTIS n'utilise pas la même précision partout. Les points
 * d'entrée `/api/v1/…` encodent avec une précision de 7 décimales, `/api/v2/…` et au-delà — donc
 * `/api/v6/…` — avec une précision de 6. Le schéma `EncodedPolyline` porte d'ailleurs le champ
 * `precision` : c'est lui qu'il faut passer ici, plutôt que de le supposer.
 */
object PolylineDecoder {

  /** Précision des points d'entrée `/api/v2/…` et au-delà, dont tout le `v6`. */
  const val PRECISION_V6 = 6

  /** Précision des points d'entrée `/api/v1/…`. */
  const val PRECISION_V1 = 7

  private const val CHUNK_BITS = 5
  private const val CHUNK_MASK = 0x1f
  private const val CONTINUATION_BIT = 0x20
  private const val ASCII_OFFSET = 63
  private const val SIGN_BIT = 1
  private const val ZIGZAG_SHIFT = 1
  private const val DECIMAL_BASE = 10.0

  /**
   * Décode [encoded] en une liste de points.
   *
   * Une chaîne vide rend une liste vide : c'est le cas normal quand la requête a demandé
   * `detailedLegs=false`, le serveur renvoyant alors une polyligne vide.
   *
   * @param precision nombre de décimales avec lequel la chaîne a été encodée, à lire dans le champ
   *   `precision` du schéma `EncodedPolyline`.
   */
  fun decode(encoded: String, precision: Int): List<LatLon> {
    if (encoded.isEmpty()) return emptyList()
    val scale = pow10(precision)
    val points = ArrayList<LatLon>()
    var index = 0
    var lat = 0
    var lon = 0
    while (index < encoded.length) {
      val latResult = readValue(encoded, index)
      lat += latResult.value
      index = latResult.nextIndex
      if (index >= encoded.length) break
      val lonResult = readValue(encoded, index)
      lon += lonResult.value
      index = lonResult.nextIndex
      points.add(LatLon(lat = lat / scale, lon = lon / scale))
    }
    return points
  }

  /** Une valeur décodée et l'indice du caractère suivant. */
  private class Chunk(val value: Int, val nextIndex: Int)

  /**
   * Lit un entier en zigzag à partir de [start] : groupes de 5 bits, poids faibles d'abord, le
   * sixième bit signalant qu'un groupe suit.
   */
  private fun readValue(encoded: String, start: Int): Chunk {
    var index = start
    var shift = 0
    var accumulator = 0
    var byte: Int
    do {
      byte = encoded[index].code - ASCII_OFFSET
      index++
      accumulator = accumulator or ((byte and CHUNK_MASK) shl shift)
      shift += CHUNK_BITS
    } while (byte >= CONTINUATION_BIT && index < encoded.length)
    // Zigzag : le bit de poids faible porte le signe.
    val value =
      if (accumulator and SIGN_BIT != 0) {
        (accumulator shr ZIGZAG_SHIFT).inv()
      } else {
        accumulator shr ZIGZAG_SHIFT
      }
    return Chunk(value, index)
  }

  private fun pow10(exponent: Int): Double {
    var result = 1.0
    repeat(exponent) { result *= DECIMAL_BASE }
    return result
  }
}
