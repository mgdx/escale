package io.github.mgdx.escale.ui.settings

import android.content.res.Configuration
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.mgdx.escale.R
import io.github.mgdx.escale.appContainer
import io.github.mgdx.escale.core.model.CategoryOrder
import io.github.mgdx.escale.core.model.JourneyCategory
import io.github.mgdx.escale.ui.results.iconRes
import io.github.mgdx.escale.ui.results.labelRes
import io.github.mgdx.escale.ui.theme.EscaleTheme

/**
 * L'écran « Ordre des catégories » (SPEC.md § 5.6), qui range les onglets de la feuille de
 * résultats (SPEC.md § 5.2).
 *
 * **Il ne change que la disposition.** Les quatre catégories restent les quatre catégories, leurs
 * requêtes sont identiques au paramètre près, et aucune ne peut être retirée : ce qui se règle ici
 * est l'ordre des languettes, et l'onglet qu'Escale ouvre d'emblée — le premier de la liste.
 *
 * Le geste attendu est le glissé-déposé, par la poignée de chaque ligne. Il n'est pas le seul :
 * un lecteur d'écran ne traîne rien, et chaque ligne porte donc les actions « Monter » et
 * « Descendre » (SPEC.md § 9). Une commande qui n'existerait qu'au doigt serait une commande
 * inatteignable pour une partie des usagers.
 */
@Composable
fun CategoryOrderScreen(
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(appContainer())),
) {
  val uiState by viewModel.uiState.collectAsStateWithLifecycle()
  CategoryOrderContent(
    order = uiState.display.categoryOrder,
    // Le nouvel ordre est enregistré au lâcher, comme tout réglage de cet écran : ni bouton
    // « Appliquer », ni état à restituer après une rotation.
    onOrderChanged = { order -> viewModel.updateDisplayPreferences(uiState.display.copy(categoryOrder = order)) },
    onBack = onBack,
    modifier = modifier,
  )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CategoryOrderContent(
  order: List<JourneyCategory>,
  onOrderChanged: (List<JourneyCategory>) -> Unit,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(text = stringResource(R.string.settings_category_order_title)) },
        navigationIcon = {
          IconButton(onClick = onBack) {
            Icon(
              painter = painterResource(R.drawable.ic_arrow_back),
              contentDescription = stringResource(R.string.action_back),
            )
          }
        },
      )
    },
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        // À 200 % d'agrandissement, la phrase d'aide et les quatre lignes ne tiennent plus dans la
        // hauteur de l'écran : sans défilement, la dernière serait inatteignable (SPEC.md § 9).
        .verticalScroll(rememberScrollState()),
    ) {
      Text(
        text = stringResource(R.string.settings_category_order_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = HORIZONTAL_PADDING, vertical = VERTICAL_PADDING),
      )
      ReorderableCategories(order = order, onOrderChanged = onOrderChanged)
      SettingsBottomSpacer()
    }
  }
}

/**
 * Les quatre lignes, et le geste qui les range.
 *
 * **Rien n'est déplacé tant que le doigt n'est pas levé.** Pendant le geste, la ligne saisie suit
 * le doigt et celles qu'elle enjambe s'écartent, mais l'ordre enregistré, lui, ne bouge pas : c'est
 * ce qui évite qu'un aller-retour du doigt laisse derrière lui une suite de déplacements à moitié
 * appliqués. Le calcul — quel rang vise le doigt, de combien chaque ligne s'écarte — est dans
 * [DragReorder], en Kotlin pur et vérifié en JVM.
 */
@Composable
private fun ReorderableCategories(order: List<JourneyCategory>, onOrderChanged: (List<JourneyCategory>) -> Unit) {
  val drag = remember { DragState() }
  // Ce que le geste doit connaître à l'instant où le doigt se lève, et non à l'instant où il s'est
  // posé : l'ordre affiché et le moyen de l'enregistrer. Réécrits à chaque composition, comme la
  // feuille de résultats le fait de la place dont elle dispose (`ResultsSheetSlot`).
  drag.order = order
  drag.commit = onOrderChanged
  val from = order.indexOf(drag.category)
  val heights = order.map { drag.heights[it] ?: 0 }
  val target = DragReorder.targetIndex(heights, from, drag.offset)

  Column(modifier = Modifier.fillMaxWidth()) {
    order.forEachIndexed { index, category ->
      val dragged = index == from
      // Les lignes enjambées glissent au lieu de sauter : sans l'animation, on ne voit pas ce qui
      // s'est passé, seulement un ordre qui a changé.
      val settled by animateFloatAsState(
        targetValue = if (dragged) 0f else DragReorder.shift(heights, from, target, index),
        label = "ecart",
      )
      CategoryRow(
        placement = CategoryPlacement(category = category, position = index, count = order.size),
        dragged = dragged,
        translation = if (dragged) drag.offset else settled,
        onMeasured = { height -> drag.heights[category] = height },
        onMoved = { to -> onOrderChanged(CategoryOrder.moved(order, index, to)) },
        drag = drag,
      )
    }
  }
}

/**
 * Une ligne : sa poignée, le pictogramme de sa catégorie et son libellé.
 *
 * Le lecteur d'écran entend une annonce unique — « Vélo, position 3 sur 4 » — et dispose des deux
 * actions de déplacement. La poignée, elle, ne s'annonce pas : elle ferait doublon avec la ligne,
 * et un lecteur d'écran ne peut de toute façon rien traîner (SPEC.md § 9).
 */
@Composable
private fun CategoryRow(
  placement: CategoryPlacement,
  dragged: Boolean,
  translation: Float,
  onMeasured: (Int) -> Unit,
  onMoved: (Int) -> Unit,
  drag: DragState,
) {
  val label = stringResource(placement.category.labelRes())
  val description =
    stringResource(R.string.settings_category_order_position, label, placement.position + 1, placement.count)
  val actions = moveActions(placement, onMoved)
  Surface(
    // La ligne saisie passe au-dessus des autres et se détache du fond : sans cela, elle se
    // confondrait avec celles qu'elle survole.
    tonalElevation = if (dragged) DRAGGED_ELEVATION else 0.dp,
    shadowElevation = if (dragged) DRAGGED_ELEVATION else 0.dp,
    modifier = Modifier
      .fillMaxWidth()
      .zIndex(if (dragged) 1f else 0f)
      .graphicsLayer { translationY = translation }
      .onSizeChanged { onMeasured(it.height) }
      .semantics(mergeDescendants = true) {
        contentDescription = description
        customActions = actions
      },
  ) {
    Row(
      modifier = Modifier
        .heightIn(min = MIN_TOUCH_TARGET)
        .padding(horizontal = HORIZONTAL_PADDING, vertical = VERTICAL_PADDING),
      horizontalArrangement = Arrangement.spacedBy(HORIZONTAL_PADDING),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      DragHandle(category = placement.category, drag = drag)
      Icon(painter = painterResource(placement.category.iconRes()), contentDescription = null)
      Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
  }
}

/**
 * Les deux actions d'accessibilité de la ligne : « Monter » et « Descendre » (SPEC.md § 9).
 *
 * Elles ne sont pas une commodité, mais **la seule voie d'accès** pour qui navigue au lecteur
 * d'écran : on ne traîne rien au doigt quand on ne voit pas où l'on traîne. La première ligne n'a
 * pas de « Monter », la dernière pas de « Descendre » : une action sans effet s'annoncerait quand
 * même, et laisserait croire à une commande cassée.
 */
@Composable
private fun moveActions(placement: CategoryPlacement, onMoved: (Int) -> Unit): List<CustomAccessibilityAction> {
  val position = placement.position
  val moveUp = stringResource(R.string.settings_category_order_move_up)
  val moveDown = stringResource(R.string.settings_category_order_move_down)
  return buildList {
    if (position > 0) {
      add(
        CustomAccessibilityAction(moveUp) {
          onMoved(position - 1)
          true
        },
      )
    }
    if (position < placement.count - 1) {
      add(
        CustomAccessibilityAction(moveDown) {
          onMoved(position + 1)
          true
        },
      )
    }
  }
}

/**
 * La poignée, et le geste qu'elle porte.
 *
 * Elle occupe 48 dp de haut comme de large (SPEC.md § 9) sans que son dessin grossisse pour autant :
 * c'est la zone qui doit être atteignable, pas le trait. Le geste part d'elle et non de la ligne
 * entière : une ligne entièrement traînable rendrait le défilement de l'écran impraticable.
 */
@Composable
private fun DragHandle(category: JourneyCategory, drag: DragState) {
  val haptics = LocalHapticFeedback.current
  Box(
    modifier = Modifier
      .pointerInput(drag, category) {
        detectDragGestures(
          onDragStart = {
            drag.start(category)
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
          },
          // Consommé : sans quoi le défilement de l'écran emporterait le geste avec lui.
          onDrag = { change, amount ->
            change.consume()
            drag.offset += amount.y
          },
          onDragEnd = { drag.drop() },
          onDragCancel = { drag.release() },
        )
      }
      .size(MIN_TOUCH_TARGET),
    contentAlignment = Alignment.Center,
  ) {
    Icon(
      painter = painterResource(R.drawable.ic_drag_handle),
      contentDescription = null,
      modifier = Modifier.alpha(HANDLE_ALPHA),
    )
  }
}

/** Une catégorie et la place qu'elle occupe : ce que le lecteur d'écran annonce, « Vélo, 3 sur 4 ». */
private data class CategoryPlacement(val category: JourneyCategory, val position: Int, val count: Int)

/**
 * L'état du geste en cours, et lui seul.
 *
 * Un objet mémorisé plutôt que trois variables locales : les fonctions du détecteur de gestes
 * survivent aux recompositions, et c'est la seule façon qu'elles aient de lire des valeurs à jour
 * plutôt que celles de la composition où elles ont été créées.
 */
private class DragState {
  /** La catégorie sous le doigt, ou `null` quand aucun geste ne court. */
  var category by mutableStateOf<JourneyCategory?>(null)

  /** Le chemin parcouru par le doigt depuis la saisie, en pixels, positif vers le bas. */
  var offset by mutableFloatStateOf(0f)

  /** La hauteur mesurée de chaque ligne : un libellé long ne fait pas la même qu'un libellé court. */
  val heights = mutableStateMapOf<JourneyCategory, Int>()

  /** L'ordre affiché, réécrit à chaque composition. */
  var order: List<JourneyCategory> = CategoryOrder.DEFAULT

  /** Ce qui enregistre un nouvel ordre, réécrit de même. */
  var commit: (List<JourneyCategory>) -> Unit = {}

  fun start(category: JourneyCategory) {
    this.category = category
    offset = 0f
  }

  /**
   * Le doigt se lève : c'est **maintenant** que l'ordre change, et pas avant.
   *
   * Les rangs sont relus ici plutôt que retenus au début du geste : le doigt a bougé depuis, et
   * c'est sa position d'arrivée qui décide.
   */
  fun drop() {
    val start = order.indexOf(category)
    val landing = DragReorder.targetIndex(order.map { heights[it] ?: 0 }, start, offset)
    val moved = CategoryOrder.moved(order, start, landing)
    release()
    if (landing != start) commit(moved)
  }

  fun release() {
    category = null
    offset = 0f
  }
}

private val HORIZONTAL_PADDING = 16.dp
private val VERTICAL_PADDING = 12.dp
private val DRAGGED_ELEVATION = 6.dp

/** SPEC.md § 9 : aucune commande sous 48 dp, la poignée comprise. */
private val MIN_TOUCH_TARGET = 48.dp

/** La poignée est une indication, pas le contenu : elle s'efface derrière le libellé. */
private const val HANDLE_ALPHA = 0.6f

@Preview(showBackground = true, name = "Ordre des catégories, thème clair")
@Preview(
  showBackground = true,
  name = "Ordre des catégories, thème sombre",
  uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Preview(showBackground = true, name = "Ordre des catégories, texte à 200 %", fontScale = 2f, heightDp = 900)
@Composable
private fun CategoryOrderScreenPreview() {
  EscaleTheme(dynamicColor = false) {
    CategoryOrderContent(order = CategoryOrder.DEFAULT, onOrderChanged = {}, onBack = {})
  }
}
