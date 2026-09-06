package io.github.mgdx.escale.ui.map

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.PoiCategory
import io.github.mgdx.escale.core.model.PoiComplement
import io.github.mgdx.escale.core.model.PoiDetailKey
import io.github.mgdx.escale.core.model.PoiTypeKey
import io.github.mgdx.escale.ui.theme.EscaleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La fiche d'un point d'intérêt, telle qu'elle s'affiche (SPEC.md § 5.7 et § 10).
 *
 * Les états qui valent d'être vus sont ceux où la fiche décide de montrer ou de taire une ligne :
 * avec nom, sans nom, avec adresse, avec le seul numéro de la tuile, sans rien — taire une ligne
 * vide est ici une exigence et non une commodité, « on n'affiche pas ce qu'on n'a pas ».
 *
 * **Aucun libellé n'est écrit en dur** : ils sont relus dans les ressources, comme le composable le
 * fait. Un cas d'essai qui comparerait « Bakery » vérifierait la langue de l'appareil, pas la
 * fiche, et échouerait sur un téléphone réglé en français.
 */
@RunWith(AndroidJUnit4::class)
class MapPlaceCardTest {

  @get:Rule
  val compose = createComposeRule()

  private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

  private fun label(id: Int, vararg arguments: Any): String = context.getString(id, *arguments)

  private val point = LatLon(lat = 48.8566, lon = 2.3522)

  private val address = Location(
    id = null,
    name = "12 Rue de Rivoli",
    description = "Paris",
    coordinates = point,
    kind = PlaceKind.ADDRESS,
  )

  private fun show(place: SelectedPlace, onPick: (MapPickPurpose) -> Unit = {}, onDismiss: () -> Unit = {}) {
    compose.setContent {
      EscaleTheme(dynamicColor = false) {
        MapPlaceCard(place = place, onPick = onPick, onDismiss = onDismiss)
      }
    }
  }

  @Test
  fun un_lieu_nomme_montre_son_nom_son_type_et_son_adresse() {
    show(
      SelectedPlace(
        point = point,
        name = "Au bon pain",
        category = PoiCategory.FOOD,
        typeKey = PoiTypeKey.BAKERY,
        address = address,
        addressLoading = false,
      ),
    )

    compose.onNodeWithText("Au bon pain").assertIsDisplayed()
    compose.onNodeWithText(label(R.string.map_place_type_bakery)).assertIsDisplayed()
    compose.onNodeWithText("12 Rue de Rivoli").assertIsDisplayed()
  }

  @Test
  fun un_lieu_sans_nom_prend_son_type_pour_nom() {
    // « Le nom du lieu, ou le libellé de sa famille s'il n'en a pas » (SPEC.md § 5.7). Le type est
    // plus précis que la famille, et reste vrai : c'est lui qui sert de titre.
    show(SelectedPlace(point = point, category = PoiCategory.TOILETS, typeKey = PoiTypeKey.TOILETS))

    compose.onNodeWithText(label(R.string.map_place_type_toilets)).assertIsDisplayed()
  }

  @Test
  fun un_commerce_que_la_table_ne_nomme_pas_garde_le_libelle_de_sa_categorie() {
    show(SelectedPlace(point = point, category = PoiCategory.OTHER_SHOPS))

    compose.onNodeWithText(label(R.string.map_place_category_other_shops)).assertIsDisplayed()
  }

  @Test
  fun le_numero_de_la_tuile_tient_lieu_d_adresse_en_attendant() {
    show(SelectedPlace(point = point, name = "Au bon pain", typeKey = PoiTypeKey.BAKERY, houseNumber = "12"))

    compose.onNodeWithText("12").assertIsDisplayed()
  }

  @Test
  fun sans_adresse_ni_numero_la_ligne_disparait() {
    // Un « Adresse inconnue » n'apprendrait rien : mieux vaut une fiche plus courte.
    show(SelectedPlace(point = point, name = "Au bon pain", typeKey = PoiTypeKey.BAKERY, addressLoading = false))

    compose.onNodeWithText("Au bon pain").assertIsDisplayed()
    compose.onNodeWithText("12 Rue de Rivoli").assertDoesNotExist()
    compose.onNodeWithText("12").assertDoesNotExist()
  }

  @Test
  fun l_adresse_rendue_remplace_le_numero_de_la_tuile() {
    show(
      SelectedPlace(
        point = point,
        name = "Au bon pain",
        typeKey = PoiTypeKey.BAKERY,
        houseNumber = "12",
        address = address,
        addressLoading = false,
      ),
    )

    compose.onNodeWithText("12 Rue de Rivoli").assertIsDisplayed()
  }

  @Test
  fun le_complement_suit_le_type_et_se_traduit() {
    // La tuile porte « italian » ; la fiche affiche le libellé de la langue de l'appareil.
    show(
      SelectedPlace(
        point = point,
        name = "Chez Marcel",
        category = PoiCategory.DINING,
        typeKey = PoiTypeKey.RESTAURANT,
        complement = PoiComplement.Named(PoiDetailKey.ITALIAN),
      ),
    )

    val expected = label(
      R.string.map_place_type_detail,
      label(R.string.map_place_type_restaurant),
      label(R.string.map_place_detail_italian),
    )
    compose.onNodeWithText(expected).assertIsDisplayed()
  }

  @Test
  fun une_valeur_que_la_table_ne_nomme_pas_reste_lisible() {
    show(
      SelectedPlace(
        point = point,
        name = "Chez Marcel",
        typeKey = PoiTypeKey.RESTAURANT,
        complement = PoiComplement.Unnamed("Soul food"),
      ),
    )

    val expected = label(R.string.map_place_type_detail, label(R.string.map_place_type_restaurant), "Soul food")
    compose.onNodeWithText(expected).assertIsDisplayed()
  }

  @Test
  fun le_distributeur_complete_la_banque() {
    show(
      SelectedPlace(
        point = point,
        name = "Banque de l'Ouest",
        category = PoiCategory.MONEY,
        typeKey = PoiTypeKey.BANK,
        complement = PoiComplement.CashMachine,
      ),
    )

    val expected = label(
      R.string.map_place_type_detail,
      label(R.string.map_place_type_bank),
      label(R.string.map_place_detail_atm),
    )
    compose.onNodeWithText(expected).assertIsDisplayed()
  }

  @Test
  fun les_deux_boutons_deposent_le_point_choisi() {
    val picked = mutableListOf<MapPickPurpose>()
    show(SelectedPlace(point = point, name = "Au bon pain", typeKey = PoiTypeKey.BAKERY), onPick = { picked += it })

    compose.onNodeWithText(label(R.string.map_pick_departure)).performClick()
    compose.onNodeWithText(label(R.string.map_pick_destination)).performClick()

    assertEquals(listOf(MapPickPurpose.DEPARTURE, MapPickPurpose.DESTINATION), picked)
  }

  @Test
  fun la_croix_referme_la_fiche() {
    var dismissed = 0
    show(SelectedPlace(point = point, name = "Au bon pain", typeKey = PoiTypeKey.BAKERY), onDismiss = {
      dismissed += 1
    })

    compose.onNodeWithContentDescription(label(R.string.map_place_close)).performClick()

    assertEquals(1, dismissed)
  }
}
