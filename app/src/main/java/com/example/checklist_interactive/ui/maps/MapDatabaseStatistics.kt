package com.example.checklist_interactive.ui.maps

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.data.tactical.TacticalDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Database statistics data class
 */
data class DatabaseStats(
    val locations: Int = 0,
    val airports: Int = 0,
    val waypoints: Int = 0,
    val tacticalMarkers: Int = 0,
    val runways: Int = 0,
    val borders: Int = 0,
    val routes: Int = 0,
    val routeWaypoints: Int = 0,
    val services: Int = 0,
    val media: Int = 0,
    val tags: Int = 0,
    val navaids: Int = 0,
    val mapDrawings: Int = 0,
    val tacticalUnits: Int = 0,
    val tacticalUnitsActive: Int = 0,
    val tacticalUnitsHistory: Int = 0,
    val databaseSizeMB: Double = 0.0,
    val databasePath: String = ""
)

/**
 * Repository for database statistics
 */
class DatabaseStatisticsRepository(private val context: Context) {

    /**
     * Get statistics from internal database
     */
    suspend fun getInternalDatabaseStats(): DatabaseStats = withContext(Dispatchers.IO) {
        try {
            val db = TacticalDatabase.getInstance(context, useExternalPath = false, allowDestructiveMigration = false)
            val sqlite = db.openHelper.readableDatabase

            val stats = DatabaseStats(
                locations = queryCount(sqlite, "SELECT COUNT(*) FROM locations"),
                airports = queryCount(sqlite, "SELECT COUNT(*) FROM locations WHERE marker_type = 'airport'"),
                waypoints = queryCount(sqlite, "SELECT COUNT(*) FROM locations WHERE marker_type = 'waypoint'"),
                tacticalMarkers = queryCount(sqlite, "SELECT COUNT(*) FROM locations WHERE marker_type = 'tactical_unit'"),
                runways = queryCount(sqlite, "SELECT COUNT(*) FROM runways"),
                borders = queryCount(sqlite, "SELECT COUNT(*) FROM borders"),
                routes = queryCount(sqlite, "SELECT COUNT(*) FROM routes"),
                routeWaypoints = queryCount(sqlite, "SELECT COUNT(*) FROM route_waypoints"),
                services = queryCount(sqlite, "SELECT COUNT(*) FROM services"),
                media = queryCount(sqlite, "SELECT COUNT(*) FROM media"),
                tags = queryCount(sqlite, "SELECT COUNT(*) FROM tags"),
                navaids = queryCount(sqlite, "SELECT COUNT(*) FROM navaids"),
                mapDrawings = queryCount(sqlite, "SELECT COUNT(*) FROM map_drawings"),
                tacticalUnits = queryCount(sqlite, "SELECT COUNT(*) FROM tactical_units"),
                tacticalUnitsActive = queryCount(sqlite, "SELECT COUNT(*) FROM tactical_units WHERE is_active = 1"),
                tacticalUnitsHistory = queryCount(sqlite, "SELECT COUNT(*) FROM tactical_unit_history"),
                databaseSizeMB = getDatabaseSize(context.getDatabasePath("map_data.db")),
                databasePath = TacticalDatabase.getDatabasePath(context, useExternalPath = false)
            )

            stats
        } catch (e: Exception) {
            android.util.Log.e("DatabaseStats", "Failed to get internal database stats", e)
            DatabaseStats()
        }
    }

    /**
     * Get statistics from asset database (read-only)
     */
    suspend fun getAssetDatabaseStats(): DatabaseStats = withContext(Dispatchers.IO) {
        try {
            // Copy asset database to temp location for reading
            val tempFile = File(context.cacheDir, "temp_asset_db.db")
            context.assets.open("databases/map_data.db").use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Open the temp database
            val sqlite = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
                .create(
                    androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                        .builder(context)
                        .name(tempFile.absolutePath)
                        .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                            override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                            override fun onUpgrade(db: androidx.sqlite.db.SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                        })
                        .build()
                ).readableDatabase

            val stats = DatabaseStats(
                locations = queryCount(sqlite, "SELECT COUNT(*) FROM locations"),
                airports = queryCount(sqlite, "SELECT COUNT(*) FROM locations WHERE marker_type = 'airport'"),
                waypoints = queryCount(sqlite, "SELECT COUNT(*) FROM locations WHERE marker_type = 'waypoint'"),
                tacticalMarkers = queryCount(sqlite, "SELECT COUNT(*) FROM locations WHERE marker_type = 'tactical_unit'"),
                runways = queryCount(sqlite, "SELECT COUNT(*) FROM runways"),
                borders = queryCount(sqlite, "SELECT COUNT(*) FROM borders"),
                routes = queryCount(sqlite, "SELECT COUNT(*) FROM routes"),
                routeWaypoints = queryCount(sqlite, "SELECT COUNT(*) FROM route_waypoints"),
                services = queryCount(sqlite, "SELECT COUNT(*) FROM services"),
                media = queryCount(sqlite, "SELECT COUNT(*) FROM media"),
                tags = queryCount(sqlite, "SELECT COUNT(*) FROM tags"),
                navaids = queryCount(sqlite, "SELECT COUNT(*) FROM navaids"),
                mapDrawings = queryCount(sqlite, "SELECT COUNT(*) FROM map_drawings"),
                tacticalUnits = queryCount(sqlite, "SELECT COUNT(*) FROM tactical_units"),
                tacticalUnitsActive = queryCount(sqlite, "SELECT COUNT(*) FROM tactical_units WHERE is_active = 1"),
                tacticalUnitsHistory = queryCount(sqlite, "SELECT COUNT(*) FROM tactical_unit_history"),
                databaseSizeMB = getDatabaseSize(tempFile),
                databasePath = "assets/databases/map_data.db"
            )

            sqlite.close()
            tempFile.delete()

            stats
        } catch (e: Exception) {
            android.util.Log.e("DatabaseStats", "Failed to get asset database stats", e)
            DatabaseStats()
        }
    }

    private fun queryCount(db: androidx.sqlite.db.SupportSQLiteDatabase, sql: String): Int {
        return try {
            db.query(sql).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        } catch (e: Exception) {
            android.util.Log.e("DatabaseStats", "Query failed: $sql", e)
            0
        }
    }

    private fun getDatabaseSize(file: File): Double {
        return try {
            if (file.exists()) {
                file.length() / (1024.0 * 1024.0)
            } else {
                0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
}

/**
 * Database statistics UI composable
 */
@Composable
fun MapDatabaseStatistics(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember { DatabaseStatisticsRepository(context) }

    var selectedDatabase by remember { mutableStateOf(0) } // 0 = Internal, 1 = Asset
    var internalStats by remember { mutableStateOf(DatabaseStats()) }
    var assetStats by remember { mutableStateOf(DatabaseStats()) }
    var isLoading by remember { mutableStateOf(false) }

    // Load statistics when database selection changes
    LaunchedEffect(selectedDatabase) {
        isLoading = true
        if (selectedDatabase == 0) {
            internalStats = repository.getInternalDatabaseStats()
        } else {
            assetStats = repository.getAssetDatabaseStats()
        }
        isLoading = false
    }

    val currentStats = if (selectedDatabase == 0) internalStats else assetStats

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Database selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Database:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            FilterChip(
                selected = selectedDatabase == 0,
                onClick = { selectedDatabase = 0 },
                label = { Text("Internal DB") }
            )

            FilterChip(
                selected = selectedDatabase == 1,
                onClick = { selectedDatabase = 1 },
                label = { Text("Asset DB") }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Database path
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "Database Path",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentStats.databasePath,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Size: ${String.format("%.2f", currentStats.databaseSizeMB)} MB",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Statistics grid
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val isTablet = configuration.screenWidthDp >= 600

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Header
                item {
                    Text(
                        text = "Database Statistics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                // Statistics items in grid (2 columns on tablet, 1 on phone)
                val items = getStatisticsItems(currentStats)
                val chunkedItems = if (isTablet) items.chunked(2) else items.chunked(1)

                items(chunkedItems) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowItems.forEach { item ->
                            StatisticCard(
                                label = item.first,
                                value = item.second,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Add spacer for incomplete rows on tablet
                        if (isTablet && rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/**
 * Get list of statistics items
 */
private fun getStatisticsItems(stats: DatabaseStats): List<Pair<String, String>> {
    return listOf(
        "Total Locations" to stats.locations.toString(),
        "Airports" to stats.airports.toString(),
        "Waypoints" to stats.waypoints.toString(),
        "Tactical Markers" to stats.tacticalMarkers.toString(),
        "Runways" to stats.runways.toString(),
        "Borders" to stats.borders.toString(),
        "Routes" to stats.routes.toString(),
        "Route Waypoints" to stats.routeWaypoints.toString(),
        "Services" to stats.services.toString(),
        "Media" to stats.media.toString(),
        "Tags" to stats.tags.toString(),
        "Navaids" to stats.navaids.toString(),
        "Map Drawings" to stats.mapDrawings.toString(),
        "Tactical Units (Total)" to stats.tacticalUnits.toString(),
        "Tactical Units (Active)" to stats.tacticalUnitsActive.toString(),
        "Tactical Unit History" to stats.tacticalUnitsHistory.toString()
    )
}

/**
 * Statistic card component - compact design
 */
@Composable
private fun StatisticCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = value,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
