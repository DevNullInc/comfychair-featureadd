package sh.hnet.comfychair.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import sh.hnet.comfychair.R
import sh.hnet.comfychair.model.PromptPreset
import sh.hnet.comfychair.ui.components.shared.NoOverscrollContainer

/**
 * Full-screen dialog for managing the prompt library.
 * Shows search, tag filters, and a list of all presets.
 * User selects a preset then confirms with OK button.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PromptLibraryDialog(
    presets: List<PromptPreset>,
    availableTags: Set<String>,
    searchQuery: String,
    selectedTags: Set<String>,
    filterFavoritesOnly: Boolean,
    activePresetId: String?,
    onSearchQueryChange: (String) -> Unit,
    onTagToggle: (String) -> Unit,
    onToggleFavoritesFilter: () -> Unit,
    onPresetSelected: (String) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onEditPreset: (String) -> Unit,
    onDuplicatePreset: (String) -> Unit,
    onDeletePreset: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Local selection state - initialize with currently active preset
    var selectedPresetId by remember { mutableStateOf(activePresetId) }

    // Tag filter expansion state
    var tagsExpanded by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                // Title
                Text(
                    text = stringResource(R.string.prompt_library_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                // Search bar with favorites filter toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = searchQuery,
                                onQueryChange = onSearchQueryChange,
                                onSearch = { },
                                expanded = false,
                                onExpandedChange = { },
                                placeholder = { Text(stringResource(R.string.prompt_library_search_placeholder)) },
                                leadingIcon = {
                                    Icon(Icons.Default.Search, contentDescription = null)
                                },
                                trailingIcon = if (searchQuery.isNotEmpty()) {
                                    {
                                        IconButton(onClick = { onSearchQueryChange("") }) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = stringResource(R.string.clear_search)
                                            )
                                        }
                                    }
                                } else null
                            )
                        },
                        expanded = false,
                        onExpandedChange = { },
                        modifier = Modifier.weight(1f)
                    ) { }

                    // Favorites filter toggle
                    FilledTonalIconToggleButton(
                        checked = filterFavoritesOnly,
                        onCheckedChange = { onToggleFavoritesFilter() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = if (filterFavoritesOnly) {
                                Icons.Filled.Star
                            } else {
                                Icons.Outlined.StarOutline
                            },
                            contentDescription = stringResource(R.string.prompt_library_filter_favorites)
                        )
                    }
                }

                // Tag filter chips (expandable)
                if (availableTags.isNotEmpty()) {
                    val sortedTags = availableTags.sorted()
                    val canExpand = sortedTags.size > 3

                    if (tagsExpanded) {
                        // Expanded mode: wrapping flow layout with collapse button first
                        FlowRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Collapse button first
                            if (canExpand) {
                                FilledTonalIconButton(
                                    onClick = { tagsExpanded = false }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.KeyboardArrowUp,
                                        contentDescription = null
                                    )
                                }
                            }
                            sortedTags.forEach { tag ->
                                key(tag) {
                                    FilterChip(
                                        selected = tag in selectedTags,
                                        onClick = { onTagToggle(tag) },
                                        label = { Text(tag) },
                                        leadingIcon = if (tag in selectedTags) {
                                            {
                                                Icon(
                                                    Icons.Default.Done,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    } else {
                        // Collapsed mode: horizontal scrolling with expand button first
                        NoOverscrollContainer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Expand button first
                                if (canExpand) {
                                    item(key = "expand_chip") {
                                        FilledTonalIconButton(
                                            onClick = { tagsExpanded = true }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.KeyboardArrowDown,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                }
                                items(
                                    items = sortedTags,
                                    key = { "chip_$it" }
                                ) { tag ->
                                    FilterChip(
                                        selected = tag in selectedTags,
                                        onClick = { onTagToggle(tag) },
                                        label = { Text(tag) },
                                        leadingIcon = if (tag in selectedTags) {
                                            {
                                                Icon(
                                                    Icons.Default.Done,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                                )
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }

                // Preset list
                if (presets.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.prompt_library_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    NoOverscrollContainer(modifier = Modifier.weight(1f)) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            items(presets, key = { it.id }) { preset ->
                                PresetListItem(
                                    preset = preset,
                                    isSelected = preset.id == selectedPresetId,
                                    onSelect = { selectedPresetId = preset.id },
                                    onToggleFavorite = { onToggleFavorite(preset.id) },
                                    onEdit = { onEditPreset(preset.id) },
                                    onDuplicate = { onDuplicatePreset(preset.id) },
                                    onDelete = { onDeletePreset(preset.id) }
                                )
                            }
                        }
                    }
                }

                // Action buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(onClick = onDismiss) {
                        Text(stringResource(R.string.button_cancel))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            selectedPresetId?.let { onPresetSelected(it) }
                        },
                        enabled = selectedPresetId != null
                    ) {
                        Text(stringResource(R.string.button_confirm))
                    }
                }
            }
        }
    }
}

/**
 * Individual preset item in the library list.
 */
@Composable
private fun PresetListItem(
    preset: PromptPreset,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        onClick = onSelect,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = if (isSelected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Favorite star toggle
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (preset.isFavorite) {
                        Icons.Filled.Star
                    } else {
                        Icons.Outlined.StarOutline
                    },
                    contentDescription = stringResource(R.string.prompt_preset_toggle_favorite),
                    tint = if (preset.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = preset.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = if (isSelected) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = preset.prompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = if (isSelected) Int.MAX_VALUE else 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (preset.tags.isNotEmpty()) {
                    Text(
                        text = preset.tags.sorted().joinToString(", "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = if (isSelected) Int.MAX_VALUE else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Overflow menu
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_options)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.prompt_preset_edit)) },
                        onClick = {
                            showMenu = false
                            onEdit()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, contentDescription = null)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.prompt_preset_duplicate)) },
                        onClick = {
                            showMenu = false
                            onDuplicate()
                        },
                        leadingIcon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null)
                        }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.prompt_preset_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            showMenu = false
                            onDelete()
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
            }
        }
    }
}
