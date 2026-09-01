package io.github.mgdx.escale

import android.app.Application

/** Point d'entrée du processus : il ne fait qu'une chose, construire l'unique [AppContainer]. */
class EscaleApplication : Application() {

  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    container = AppContainer(this)
  }
}
