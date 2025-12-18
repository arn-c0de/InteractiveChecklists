package com.example.checklist_interactive.ui.maps

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
import android.content.Context
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import com.example.checklist_interactive.R

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
enum class SymbolCategory(val displayName: String) {
    GROUND_UNITS("Ground Units"),
    EQUIPMENT("Equipment"),
    INSTALLATIONS("Installations"),
    ACTIVITIES("Activities"),
    UNIT_SIZE("Unit Size"),
    AIRCRAFT("Aircraft"),
    HELICOPTER("Helicopter"),
    SHIP("Ship"),
    VEHICLE("Vehicle")
}

/**
 * Affiliation for symbol coloring
 */
enum class SymbolAffiliation(val displayName: String, val color: Color) {
    FRIENDLY("Friendly", Color(0xFF00A8FF)),  // Blue
    HOSTILE("Hostile", Color(0xFFFF4444)),    // Red
    NEUTRAL("Neutral", Color(0xFF00FF00)),    // Green
    UNKNOWN("Unknown", Color(0xFFFFFF80))     // Yellow
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
    var selectedCategory by remember { mutableStateOf(SymbolCategory.GROUND_UNITS) }
    var selectedAffiliation by remember { mutableStateOf(SymbolAffiliation.UNKNOWN) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Military Symbols",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Affiliation Selector
                Text(
                    text = "Affiliation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SymbolAffiliation.values().forEach { affiliation ->
                        FilterChip(
                            selected = selectedAffiliation == affiliation,
                            onClick = { selectedAffiliation = affiliation },
                            label = { Text(affiliation.displayName) },
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
                    text = "Category",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                ScrollableTabRow(
                    selectedTabIndex = selectedCategory.ordinal,
                    edgePadding = 0.dp
                ) {
                    SymbolCategory.values().forEach { category ->
                        Tab(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            text = { Text(category.displayName) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Symbol Grid
                val symbols = getSymbolsForCategory(LocalContext.current, selectedCategory)
                
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(symbols) { symbol ->
                        SymbolCard(
                            symbol = symbol,
                            affiliationColor = selectedAffiliation.color,
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
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Symbol Icon with affiliation color background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(affiliationColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = symbol.iconResId),
                    contentDescription = symbol.name,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = symbol.name,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth()
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
