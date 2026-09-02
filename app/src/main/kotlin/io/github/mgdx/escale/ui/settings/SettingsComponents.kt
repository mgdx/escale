package io.github.mgdx.escale.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.mgdx.escale.R
import io.github.mgdx.escale.core.model.RentalFormFactor
import io.github.mgdx.escale.core.model.RentalFormFactorSelection

// Les briques de l'écran de réglages.
//
// Toutes suivent la même règle d'accessibilité (SPEC.md § 9) : libellé, valeur et explication sont
// empilés verticalement, jamais posés à droite du titre. Une valeur alignée à droite est exactement
// ce qui se tronque en premier à 200 % d'agrandissement, et c'est le défaut classique d'un écran de
// réglages. Chaque ligne fait au moins 48 dp de haut et forme une seule cible tactile.

/** Titre de rubrique. Il n'est pas cliquable : il porte le rôle d'en-tête, pas celui de bouton. */
@Composable
internal fun SettingsSectionHeader(title: String, modifier: Modifier = Modifier) {
  Text(
    text = title,
    style = MaterialTheme.typography.titleSmall,
    color = MaterialTheme.colorScheme.primary,
    modifier = modifier
      .fillMaxWidth()
      .padding(start = HORIZONTAL_PADDING, end = HORIZONTAL_PADDING, top = SECTION_TOP_PADDING, bottom = 4.dp),
  )
}

/** Une entrée qui ouvre un choix. La valeur en cours se lit sous le titre, en couleur d'accent. */
@Composable
internal fun SettingsItem(
  title: String,
  description: String,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  value: String? = null,
) {
  Column(
    modifier = modifier
      .fillMaxWidth()
      .clickable(role = Role.Button, onClick = onClick)
      .heightIn(min = MIN_TOUCH_TARGET)
      .padding(horizontal = HORIZONTAL_PADDING, vertical = VERTICAL_PADDING),
    verticalArrangement = Arrangement.spacedBy(2.dp),
  ) {
    Text(text = title, style = MaterialTheme.typography.bodyLarge)
    if (value != null) {
      Text(text = value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
    Text(
      text = description,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

/** Une bascule. La ligne entière bascule : la cible n'est pas réduite au seul interrupteur. */
@Composable
internal fun SettingsSwitchItem(
  title: String,
  description: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier,
) {
  Row(
    modifier = modifier
      .fillMaxWidth()
      .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
      .heightIn(min = MIN_TOUCH_TARGET)
      .padding(horizontal = HORIZONTAL_PADDING, vertical = VERTICAL_PADDING),
    horizontalArrangement = Arrangement.spacedBy(HORIZONTAL_PADDING),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
      Text(text = title, style = MaterialTheme.typography.bodyLarge)
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    // L'interrupteur ne porte pas d'action propre : la ligne l'a déjà, le dupliquer ferait deux
    // cibles pour une seule commande et deux annonces au lecteur d'écran.
    Switch(checked = checked, onCheckedChange = null)
  }
}

/**
 * Un choix unique. La sélection est appliquée puis le dialogue se ferme : pas de bouton
 * « Valider », donc rien à perdre si l'usager sort de l'écran.
 */
@Composable
internal fun <T> SettingsChoiceDialog(
  title: String,
  description: String,
  options: List<T>,
  selected: T,
  label: @Composable (T) -> String,
  onChoose: (T) -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title) },
    text = {
      // À 200 % d'agrandissement, cinq choix ne tiennent plus dans la hauteur d'un dialogue :
      // sans défilement, les derniers seraient inatteignables.
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
          text = description,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = VERTICAL_PADDING),
        )
        options.forEach { option ->
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .selectable(
                selected = option == selected,
                role = Role.RadioButton,
                onClick = {
                  onChoose(option)
                  onDismiss()
                },
              )
              .heightIn(min = MIN_TOUCH_TARGET)
              .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(DIALOG_ROW_SPACING),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            RadioButton(selected = option == selected, onClick = null)
            Text(text = label(option), style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
    },
    confirmButton = {
      // « Fermer » et non « Annuler » : le choix est déjà appliqué, il n'y a rien à annuler.
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.settings_action_close)) }
    },
  )
}

/**
 * Le filtre unique des types de véhicules en libre-service (SPEC.md § 5.2).
 *
 * Ce dialogue est le seul choix multiple de l'écran, et il ne se généralise pas : ses cases se
 * cochent dans un ensemble dont **le vide signifie « aucun filtre »** et non « aucun véhicule ».
 * Toute la logique de cette conversion vit dans [RentalFormFactorSelection], à `:core`, avec ses
 * tests ; ici il n'y a que des cases.
 */
@Composable
internal fun RentalFormFactorsDialog(
  allowed: Set<RentalFormFactor>,
  onChanged: (Set<RentalFormFactor>) -> Unit,
  onDismiss: () -> Unit,
) {
  val selected = RentalFormFactorSelection.selected(allowed)
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = stringResource(R.string.settings_rental_form_factors_title)) },
    text = {
      // À 200 % d'agrandissement, sept cases ne tiennent plus dans la hauteur d'un dialogue :
      // sans défilement, les dernières seraient inatteignables.
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(
          text = stringResource(R.string.settings_rental_form_factors_description),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(bottom = VERTICAL_PADDING),
        )
        RentalFormFactorSelection.OFFERED.forEach { formFactor ->
          val checked = formFactor in selected
          // Décocher la dernière case est refusé : un filtre vide voudrait dire « tous les
          // véhicules », soit l'inverse de ce que l'usager demande.
          val enabled = !RentalFormFactorSelection.isLastAccepted(allowed, formFactor)
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { accepted ->
                  onChanged(RentalFormFactorSelection.toggled(allowed, formFactor, accepted))
                },
              )
              .heightIn(min = MIN_TOUCH_TARGET)
              .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(DIALOG_ROW_SPACING),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
            Text(text = stringResource(formFactor.labelRes()), style = MaterialTheme.typography.bodyLarge)
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.settings_action_done)) }
    },
  )
}

/** Confirmation avant une suppression : le texte dit exactement ce qui va disparaître. */
@Composable
internal fun SettingsConfirmDialog(
  title: String,
  message: String,
  confirmLabel: String,
  onConfirm: () -> Unit,
  onDismiss: () -> Unit,
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(text = title) },
    text = {
      Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text(text = message, style = MaterialTheme.typography.bodyMedium)
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirm) { Text(text = confirmLabel) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.settings_action_cancel)) }
    },
  )
}

/** Espace réservé, pour que la dernière entrée ne colle pas au bord bas de l'écran. */
@Composable
internal fun SettingsBottomSpacer() {
  Spacer(modifier = Modifier.height(SECTION_TOP_PADDING))
}

private val HORIZONTAL_PADDING = 16.dp
private val VERTICAL_PADDING = 12.dp
private val SECTION_TOP_PADDING = 24.dp
private val DIALOG_ROW_SPACING = 12.dp

/** SPEC.md § 9 : aucune commande sous 48 dp. */
private val MIN_TOUCH_TARGET = 48.dp
