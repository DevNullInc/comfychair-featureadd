package sh.hnet.comfychair.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import sh.hnet.comfychair.R
import sh.hnet.comfychair.model.PromptPreset
import sh.hnet.comfychair.model.ScreenType
import sh.hnet.comfychair.model.displayName
import sh.hnet.comfychair.storage.PromptPresetStorage

/**
 * Dialog for saving or editing a prompt preset.
 *
 * @param editingPreset The preset being edited, or null for creating a new preset
 * @param currentPrompt The current prompt text to pre-fill when creating a new preset
 * @param existingTags Tags from other presets to show as suggestions
 * @param isNameTaken Function to check if a name is already taken (used when showScreenTypeSelector = false)
 * @param onDismiss Called when the dialog should be dismissed
 * @param onSave Called with name, prompt, and tags when save is confirmed (used when showScreenTypeSelector = false)
 * @param showScreenTypeSelector When true, shows a dropdown to select the target screen type
 * @param storage Required when showScreenTypeSelector = true, used for internal name validation
 * @param onSaveWithScreenType Called when showScreenTypeSelector = true, includes the selected screen type
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PromptPresetDialog(
    editingPreset: PromptPreset?,
    currentPrompt: String,
    existingTags: Set<String>,
    isNameTaken: (String, String?) -> Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, prompt: String, tags: List<String>) -> Unit,
    showScreenTypeSelector: Boolean = false,
    storage: PromptPresetStorage? = null,
    onSaveWithScreenType: ((name: String, prompt: String, tags: List<String>, ScreenType) -> Unit)? = null
) {
    val isEditMode = editingPreset != null

    // Form state
    var name by remember { mutableStateOf(editingPreset?.name ?: "") }
    var prompt by remember { mutableStateOf(editingPreset?.prompt ?: currentPrompt) }
    var tags by remember { mutableStateOf(editingPreset?.tags ?: emptyList()) }
    var tagInput by remember { mutableStateOf("") }

    // Screen type selector state (only used when showScreenTypeSelector = true)
    var selectedScreenType by remember { mutableStateOf(ScreenType.TEXT_TO_IMAGE) }
    var screenTypeExpanded by remember { mutableStateOf(false) }

    // When showing screen type selector, derive tags from storage based on selected type
    val effectiveExistingTags = if (showScreenTypeSelector && storage != null) {
        remember(selectedScreenType) {
            storage.getTagsForScreen(selectedScreenType)
        }
    } else {
        existingTags
    }

    // Error state
    var nameError by remember { mutableStateOf<String?>(null) }

    // Focus
    val nameFocusRequester = remember { FocusRequester() }

    // String resources for validation
    val errorRequired = stringResource(R.string.error_required)
    val errorNameTaken = stringResource(R.string.prompt_preset_name_taken)

    fun validate(): Boolean {
        nameError = null
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            nameError = errorRequired
            return false
        }
        // Use storage for validation when screen type selector is shown,
        // otherwise use the passed isNameTaken function
        val nameTaken = if (showScreenTypeSelector && storage != null) {
            storage.isNameTaken(trimmedName, selectedScreenType, editingPreset?.id)
        } else {
            isNameTaken(trimmedName, editingPreset?.id)
        }
        if (nameTaken) {
            nameError = errorNameTaken
            return false
        }
        return true
    }

    fun addTag(tag: String) {
        val trimmed = tag.trim().lowercase()
        if (trimmed.isNotEmpty() && trimmed !in tags.map { it.lowercase() }) {
            tags = tags + trimmed
        }
        tagInput = ""
    }

    LaunchedEffect(Unit) {
        nameFocusRequester.requestFocus()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(
                    if (isEditMode) R.string.prompt_preset_edit_title
                    else R.string.prompt_preset_save_title
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Screen type selector (only shown when showScreenTypeSelector = true)
                if (showScreenTypeSelector) {
                    ExposedDropdownMenuBox(
                        expanded = screenTypeExpanded,
                        onExpandedChange = { screenTypeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedScreenType.displayName(),
                            onValueChange = { },
                            readOnly = true,
                            label = { Text(stringResource(R.string.prompt_preset_screen_type_label)) },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = screenTypeExpanded)
                            },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        )
                        ExposedDropdownMenu(
                            expanded = screenTypeExpanded,
                            onDismissRequest = { screenTypeExpanded = false }
                        ) {
                            ScreenType.entries.forEach { screenType ->
                                DropdownMenuItem(
                                    text = { Text(screenType.displayName()) },
                                    onClick = {
                                        selectedScreenType = screenType
                                        screenTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Name field
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        nameError = null
                    },
                    label = { Text(stringResource(R.string.prompt_preset_name_label)) },
                    isError = nameError != null,
                    supportingText = nameError?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(nameFocusRequester)
                )

                // Prompt field
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text(stringResource(R.string.prompt_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 5
                )

                // Tags section
                Text(
                    text = stringResource(R.string.prompt_preset_tags_label),
                    style = MaterialTheme.typography.labelMedium
                )

                // Tag input with comma-to-chip behavior
                OutlinedTextField(
                    value = tagInput,
                    onValueChange = { newValue ->
                        if (newValue.endsWith(",") || newValue.endsWith("\n")) {
                            addTag(newValue.dropLast(1))
                        } else {
                            tagInput = newValue
                        }
                    },
                    label = { Text(stringResource(R.string.prompt_preset_add_tag_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (tagInput.isNotBlank()) {
                                addTag(tagInput)
                            }
                        }
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Current tags as removable chips
                if (tags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        tags.forEach { tag ->
                            InputChip(
                                selected = true,
                                onClick = { /* no-op, use trailing icon to remove */ },
                                label = { Text(tag) },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { tags = tags - tag },
                                        modifier = Modifier.size(InputChipDefaults.IconSize)
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = stringResource(R.string.prompt_preset_remove_tag),
                                            modifier = Modifier.size(InputChipDefaults.IconSize)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }

                // Suggested tags from existing presets
                val suggestedTags = effectiveExistingTags - tags.map { it.lowercase() }.toSet()
                if (suggestedTags.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.prompt_preset_suggested_tags),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        suggestedTags.take(10).forEach { tag ->
                            SuggestionChip(
                                onClick = { tags = tags + tag },
                                label = { Text(tag) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (validate()) {
                        if (showScreenTypeSelector && onSaveWithScreenType != null) {
                            onSaveWithScreenType(name.trim(), prompt.trim(), tags, selectedScreenType)
                        } else {
                            onSave(name.trim(), prompt.trim(), tags)
                        }
                    }
                }
            ) {
                Text(stringResource(R.string.button_save))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.button_cancel))
            }
        }
    )
}
