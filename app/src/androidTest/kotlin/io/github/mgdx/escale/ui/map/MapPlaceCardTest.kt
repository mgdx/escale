package io.github.mgdx.escale.ui.map

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.mgdx.escale.core.model.LatLon
import io.github.mgdx.escale.core.model.Location
import io.github.mgdx.escale.core.model.PlaceKind
import io.github.mgdx.escale.core.model.PoiCategory
import io.github.mgdx.escale.core.model.PoiComplement
import io.github.mgdx.escale.core.model.PoiTypeKey
import io.github.mgdx.escale.ui.theme.EscaleTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * La fiche d'un point d'intérêt, telle qu'elle s'affiche (SPEC.md § 5.7 et § 10).
 *
 * Quatre états valent d'être vus : avec nom et avec adresse, sans nom, sans adresse, et avec le
 * complément de type. Ce sont ceux où la fiche décide de montrer ou de taire une ligne — et taire
 * une ligne vide est ici une exigence, pas une commodité : « on n'affiche pas ce qu'on n'a pas ».
 *
 * Les libellés attendus sont ceux de `values/` : les cas d'essai instrumentés tournent en anglais
 * sauf si l'appareil impose une autre langue, et comparer un texte traduit reviendrait à vérifier
 * la langue de l'appareil plutôt que la fiche.
 */
@RunWith(AndroidJUnit4::class)
class MapPlaceCardTest {

  @get:Rule
  val compose = createComposeRule()

  private val point = LatLon(48.8566, 2.3522)

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
    compose.onNodeWithText("Bakery").assertIsDisplayed()
    compose.onNodeWithText("12 Rue de Rivoli").assertIsDisplayed()
  }

  @Test
  fun un_lieu_sans_nom_prend_son_type_pour_nom() {
    // « Le nom du lieu, ou le libellé de sa famille s'il n'en a pas » (SPEC.md § 5.7). Le type est
    // plus précis que la famille, et reste vrai : c'est lui qui sert de titre.
    show(SelectedPlace(point = point, category = PoiCategory.TOILETS, typeKey = PoiTypeKey.TOILETS))

    compose.onNodeWithText("Public toilets").assertIsDisplayed()
  }

  @Test
  fun un_commerce_que_la_table_ne_nomme_pas_garde_le_libelle_de_sa_categorie() {
    show(SelectedPlace(point = point, category = PoiCategory.OTHER_SHOPS))

    compose.onNodeWithText("Other shops").assertIsDisplayed()
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
  fun le_complement_suit_le_type() {
    show(
      SelectedPlace(
        point = point,
        name = "Chez Marcel",
        category = PoiCategory.DINING,
        typeKey = PoiTypeKey.RESTAURANT,
        complement = PoiComplement.Detail("italian"),
      ),
    )

    compose.onNodeWithText("Restaurant · italian").assertIsDisplayed()
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

    compose.onNodeWithText("Bank · cash machine").assertIsDisplayed()
  }

  @Test
  fun les_deux_boutons_deposent_le_point_choisi() {
    val picked = mutableListOf<MapPickPurpose>()
    show(SelectedPlace(point = point, name = "Au bon pain", typeKey = PoiTypeKey.BAKERY), onPick = { picked += it })

    compose.onNodeWithText("Start from here").performClick()
    compose.onNodeWithText("Go to here").performClick()

    assertEquals(listOf(MapPickPurpose.DEPARTURE, MapPickPurpose.DESTINATION), picked)
  }

  @Test
  fun la_croix_referme_la_fiche() {
    var dismissed = 0
    show(SelectedPlace(point = point, name = "Au bon pain", typeKey = PoiTypeKey.BAKERY), onDismiss = {
      dismissed += 1
    })

    compose.onNodeWithContentDescription("Close place details").performClick()

    assertEquals(1, dismissed)
  }
}
