package io.github.mgdx.escale

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Remplace le répartiteur principal le temps d'un test.
 *
 * `viewModelScope` s'exécute sur `Dispatchers.Main`, absent d'une machine virtuelle de test : sans
 * cette règle, tout `ViewModel` lèverait au premier `launch`. Le répartiteur non confiné exécute les
 * coroutines dès leur lancement, ce qui rend les assertions déterministes sans attente.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()) : TestWatcher() {

  override fun starting(description: Description) {
    Dispatchers.setMain(dispatcher)
  }

  override fun finished(description: Description) {
    Dispatchers.resetMain()
  }
}
