package sh.hnet.comfychair.ui.components.shared

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import sh.hnet.comfychair.R
import sh.hnet.comfychair.model.PromptPreset

/**
 * Dropdown menu for quick access to prompt presets.
 * Shows favorites, library access, and save option.
 */
@Composable
fun PromptPresetDropdown(
    favorites: List<PromptPreset>,
    activePresetId: String?,
    currentPromptIsEmpty: Boolean,
    onPresetSelected: (String) -> Unit,
    onOpenLibrary: () -> Unit,
    onSaveCurrentPrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = if (activePresetId != null) {
                    Icons.Filled.Bookmark
                } else {
                    Icons.Outlined.BookmarkBorder
                },
                contentDescription = stringResource(R.string.prompt_presets_button),
                tint = if (activePresetId != null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // Favorites section
            if (favorites.isNotEmpty()) {
                favorites.forEach { preset ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(preset.name)
                                Text(
                                    text = preset.prompt.take(60) + if (preset.prompt.length > 60) "…" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        },
                        onClick = {
                            onPresetSelected(preset.id)
                            expanded = false
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (preset.id == activePresetId) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Outlined.StarOutline
                                },
                                contentDescription = null,
                                tint = if (preset.id == activePresetId) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    )
                }
                HorizontalDivider()
            }

            // Open Library button
            DropdownMenuItem(
                text = { Text(stringResource(R.string.prompt_presets_open_library)) },
                onClick = {
                    onOpenLibrary()
                    expanded = false
                },
                leadingIcon = {
                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                }
            )

            // Save current prompt button
            DropdownMenuItem(
                text = { Text(stringResource(R.string.prompt_presets_save_current)) },
                onClick = {
                    onSaveCurrentPrompt()
                    expanded = false
                },
                enabled = !currentPromptIsEmpty,
                leadingIcon = {
                    Icon(Icons.Default.Save, contentDescription = null)
                }
            )
        }
    }
}
