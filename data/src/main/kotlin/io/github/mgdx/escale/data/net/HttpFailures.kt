package io.github.mgdx.escale.data.net

import io.github.mgdx.escale.core.result.EscaleError
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.UnknownHostException

/** Traduction des échecs de transport et des statuts HTTP en [EscaleError] (SPEC.md § 8). */
internal object HttpFailures {

  private const val CLIENT_ERROR_START = 400
  private const val SERVER_ERROR_START = 500
  private const val NOT_FOUND = 404
  private const val BAD_REQUEST = 400
  private const val UNPROCESSABLE = 422

  /**
   * Traduit une exception levée par le transport.
   *
   * Aucun message d'exception n'est repris tel quel : il pourrait contenir l'URL appelée, donc les
   * coordonnées de l'usager (SPEC.md § 8 et § 11). Seul le nom de la classe est conservé.
   */
  fun fromThrowable(cause: Throwable): EscaleError = when (cause) {
    is HttpRequestTimeoutException, is SocketTimeoutException, is ConnectTimeoutException ->
      EscaleError.Timeout

    // Le nom d'hôte ne se résout pas. Ne pas trancher : la cause est aussi bien une adresse
    // fautive qu'une absence de réseau, et deviner l'une des deux se paie par un message faux.
    is UnknownHostException -> EscaleError.HostNotFound

    // Aucune route vers l'hôte : là, c'est bien la connectivité de l'appareil qui manque.
    is NoRouteToHostException -> EscaleError.NoNetwork

    // L'hôte existe et répond, mais refuse la connexion : c'est le serveur qui est en cause.
    is ConnectException -> EscaleError.ServerUnreachable(statusCode = null)

    is SerializationException -> EscaleError.Unknown(cause = "SerializationException")

    is IOException -> EscaleError.ServerUnreachable(statusCode = null)

    else -> EscaleError.Unknown(cause = cause::class.simpleName)
  }

  /**
   * Traduit un statut HTTP d'échec.
   *
   * @param endpoint chemin relatif appelé, par exemple `/api/v6/plan`, sans hôte ni paramètre.
   * @param serverMessage champ `error` du corps de réponse, quand il a pu être lu.
   */
  fun fromStatus(status: Int, endpoint: String, serverMessage: String?): EscaleError = when {
    // SPEC.md § 4.3 : un 404 sur un point d'entrée v6 veut dire « MOTIS antérieur à la 2.9 ».
    status == NOT_FOUND && endpoint.startsWith(MotisEndpoints.V6_PREFIX) ->
      EscaleError.ApiVersionTooOld(endpoint)

    status == BAD_REQUEST || status == UNPROCESSABLE -> EscaleError.BadRequest(serverMessage)

    status >= SERVER_ERROR_START -> EscaleError.ServerUnreachable(status)

    status >= CLIENT_ERROR_START -> EscaleError.ServerUnreachable(status)

    else -> EscaleError.Unknown(cause = "HTTP $status")
  }
}
