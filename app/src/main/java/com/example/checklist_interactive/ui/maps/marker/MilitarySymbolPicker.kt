package com.example.checklist_interactive.ui.maps.marker

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import com.example.checklist_interactive.R
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import com.example.checklist_interactive.ui.common.rememberWindowSize
import com.example.checklist_interactive.ui.common.rememberResponsiveDimensions
import com.example.checklist_interactive.ui.common.WindowWidthSizeClass

/**
 * Military Symbol Definition
 */
data class MilitarySymbol(
    val id: String,
    val name: String,
    val iconResId: Int,
    val symbolSet: String,
    val symbolEntity: String,
    val category: SymbolCategory
)

/**
 * Symbol Categories for organization
 */
enum class SymbolCategory(@StringRes val displayNameResId: Int) {
    GROUND_UNITS(R.string.military_symbols_category_ground_units),
    EQUIPMENT(R.string.military_symbols_category_equipment),
    INSTALLATIONS(R.string.military_symbols_category_installations),
    ACTIVITIES(R.string.military_symbols_category_activities),
    UNIT_SIZE(R.string.military_symbols_category_unit_size),
    AIRCRAFT(R.string.military_symbols_category_aircraft),
    HELICOPTER(R.string.military_symbols_category_helicopter),
    SHIP(R.string.military_symbols_category_ship),
    VEHICLE(R.string.military_symbols_category_vehicle)
}

/**
 * Affiliation for symbol coloring
 */
enum class SymbolAffiliation(@StringRes val displayNameResId: Int, val color: Color) {
    FRIENDLY(R.string.military_symbols_affiliation_friendly, Color(0xFF00A8FF)),  // Blue
    HOSTILE(R.string.military_symbols_affiliation_hostile, Color(0xFFFF4444)),    // Red
    NEUTRAL(R.string.military_symbols_affiliation_neutral, Color(0xFF00FF00)),    // Green
    UNKNOWN(R.string.military_symbols_affiliation_unknown, Color(0xFFFFFF80))     // Yellow
}

/**
 * Military Symbol Picker Dialog
 * Allows users to select NATO military symbols for placement on the map
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MilitarySymbolPickerDialog(
    onDismiss: () -> Unit,
    onSymbolSelected: (MilitarySymbol, SymbolAffiliation) -> Unit
) {
    // Store as ordinal (Int) since Enum can't be saved directly
    var selectedCategoryOrdinal by rememberSaveable { mutableIntStateOf(SymbolCategory.GROUND_UNITS.ordinal) }
    var selectedAffiliationOrdinal by rememberSaveable { mutableIntStateOf(SymbolAffiliation.UNKNOWN.ordinal) }

    val selectedCategory = SymbolCategory.entries[selectedCategoryOrdinal]
    val selectedAffiliation = SymbolAffiliation.entries[selectedAffiliationOrdinal]

    // Responsive sizing
    val windowSize = rememberWindowSize()
    val dimensions = rememberResponsiveDimensions(windowSize)

    // Dialog dimensions based on screen size
    val dialogWidth = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.COMPACT -> if (windowSize.widthDp < 360) 300.dp else dimensions.dialogWidth
        WindowWidthSizeClass.MEDIUM -> 480.dp
        WindowWidthSizeClass.EXPANDED -> 600.dp
    }

    val dialogHeightFraction = when {
        windowSize.isVerticallyConstrained -> 0.95f  // Maximize height in landscape phones
        windowSize.isCompact -> 0.9f
        else -> 0.85f
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(if (windowSize.isCompact) 0.95f else 0.9f)
                .fillMaxHeight(dialogHeightFraction)
                .widthIn(max = dialogWidth),
            shape = RoundedCornerShape(if (windowSize.isCompact) 12.dp else 16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimensions.contentPadding)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.military_symbols_title),
                        style = if (windowSize.isCompact) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(dimensions.minTouchTarget)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = stringResource(R.string.military_symbols_close),
                            modifier = Modifier.size(dimensions.iconSize)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensions.elementSpacing))

                // Affiliation Selector
                Text(
                    text = stringResource(R.string.military_symbols_affiliation_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SymbolAffiliation.entries.forEach { affiliation ->
                        FilterChip(
                            selected = selectedAffiliation == affiliation,
                            onClick = { selectedAffiliationOrdinal = affiliation.ordinal },
                            label = { Text(stringResource(affiliation.displayNameResId)) },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .background(affiliation.color, RoundedCornerShape(4.dp))
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Category Selector
                Text(
                    text = stringResource(R.string.military_symbols_category_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                ScrollableTabRow(
                    selectedTabIndex = selectedCategoryOrdinal,
                    edgePadding = 0.dp
                ) {
                    SymbolCategory.entries.forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { selectedCategoryOrdinal = category.ordinal },
                            text = { Text(stringResource(category.displayNameResId)) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimensions.elementSpacing))

                // Symbol Grid
                val symbols = getSymbolsForCategory(LocalContext.current, selectedCategory)

                // Adaptive grid sizing based on screen size
                val gridMinSize = when (windowSize.widthSizeClass) {
                    WindowWidthSizeClass.COMPACT -> when {
                        windowSize.widthDp < 360 -> 70.dp  // Small phones: 2-3 columns
                        windowSize.widthDp < 420 -> 80.dp  // Standard phones: 3-4 columns
                        else -> 90.dp                       // Large phones: 4 columns
                    }
                    WindowWidthSizeClass.MEDIUM -> 100.dp  // Tablets: 4-5 columns
                    WindowWidthSizeClass.EXPANDED -> 110.dp // Large tablets: 5-6 columns
                }

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = gridMinSize),
                    contentPadding = PaddingValues(dimensions.elementSpacing / 2),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.elementSpacing),
                    verticalArrangement = Arrangement.spacedBy(dimensions.elementSpacing),
                    modifier = Modifier.weight(1f)
                ) {
                    items(symbols) { symbol ->
                        SymbolCard(
                            symbol = symbol,
                            affiliationColor = selectedAffiliation.color,
                            windowSize = windowSize,
                            dimensions = dimensions,
                            onClick = {
                                onSymbolSelected(symbol, selectedAffiliation)
                                onDismiss()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SymbolCard(
    symbol: MilitarySymbol,
    affiliationColor: Color,
    windowSize: com.example.checklist_interactive.ui.common.WindowSize,
    dimensions: com.example.checklist_interactive.ui.common.ResponsiveDimensions,
    onClick: () -> Unit
) {
    // Responsive icon sizing
    val iconBoxSize = when (windowSize.widthSizeClass) {
        WindowWidthSizeClass.COMPACT -> when {
            windowSize.widthDp < 360 -> 40.dp  // Very small phones
            windowSize.widthDp < 420 -> 44.dp  // Small phones
            else -> 48.dp                       // Standard phones
        }
        WindowWidthSizeClass.MEDIUM -> 52.dp   // Tablets
        WindowWidthSizeClass.EXPANDED -> 56.dp // Large tablets
    }

    val iconSize = iconBoxSize * 0.85f  // Icon is 85% of box size

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = dimensions.minTouchTarget),  // Ensure minimum touch target
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.elementSpacing / 2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Symbol Icon with affiliation color background
            Box(
                modifier = Modifier
                    .size(iconBoxSize)
                    .background(
                        affiliationColor.copy(alpha = 0.3f),
                        RoundedCornerShape(if (windowSize.isCompact) 6.dp else 8.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = symbol.iconResId),
                    contentDescription = symbol.name,
                    modifier = Modifier.size(iconSize),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(dimensions.elementSpacing / 2))

            Text(
                text = symbol.name,
                style = if (windowSize.isCompact && windowSize.widthDp < 360) {
                    MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp)
                } else {
                    MaterialTheme.typography.labelSmall
                },
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

/**
 * Get symbols for a specific category dynamically by scanning drawables with prefix ic_mapicon_
 */
private fun getSymbolsForCategory(ctx: Context, category: SymbolCategory): List<MilitarySymbol> {
    // Discover all drawable resource names starting with ic_mapicon_
    val drawables = mutableListOf<Triple<String, String, Int>>() // (entity, displayName, resId)
    val fields = R.drawable::class.java.fields
    for (f in fields) {
        val name = f.name
        if (name.startsWith("ic_mapicon_")) {
            val entity = name.removePrefix("ic_mapicon_")
            val resId = ctx.resources.getIdentifier(name, "drawable", ctx.packageName)
            if (resId != 0) {
                // Extract category from prefix if present (e.g., equipment_mortar -> mortar)
                val categoryPrefixes = listOf("equipment_", "groundunit_", "installations_", "activities_", "unitsize_", "aircraft_", "helicopter_", "ship_", "vehicle_")
                val displayName = categoryPrefixes.fold(entity) { acc, prefix -> acc.removePrefix(prefix) }
                val display = displayName.replace('_', ' ').split(' ').joinToString(" ") { it.replaceFirstChar { ch -> ch.uppercase() } }
                drawables.add(Triple(entity, display, resId))
            }
        }
    }

    // Automatic categorization by prefix
    fun classify(entity: String): SymbolCategory {
        return when {
            entity.startsWith("equipment_") -> SymbolCategory.EQUIPMENT
            entity.startsWith("groundunit_") -> SymbolCategory.GROUND_UNITS
            entity.startsWith("installations_") -> SymbolCategory.INSTALLATIONS
            entity.startsWith("activities_") -> SymbolCategory.ACTIVITIES
            entity.startsWith("unitsize_") -> SymbolCategory.UNIT_SIZE
            entity.startsWith("aircraft_") -> SymbolCategory.AIRCRAFT
            entity.startsWith("helicopter_") -> SymbolCategory.HELICOPTER
            entity.startsWith("ship_") -> SymbolCategory.SHIP
            entity.startsWith("vehicle_") -> SymbolCategory.VEHICLE
            else -> SymbolCategory.EQUIPMENT // default fallback
        }
    }

    // Build MilitarySymbol list filtered by requested category
    return drawables.map { (entity, display, resId) ->
        val cat = classify(entity)
        MilitarySymbol(entity, display, resId, cat.name.lowercase(), entity, cat)
    }.filter { it.category == category }.sortedBy { it.name }
}
