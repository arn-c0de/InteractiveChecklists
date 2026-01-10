# Historical Border Data

This app supports displaying country borders from different historical epochs. Currently, the following epochs are supported:

## Supported Epochs

1. **Modern (2020+)** - `ne_110m_admin_0_countries.geojson` ✅ *Already included*
2. **Cold War (1947-1991)** - `borders_cold_war.geojson` ✅ *Included (derived from CShapes 0.6, CC BY-NC-SA)*
3. **World War II (1939-1945)** - `borders_ww2.geojson` ⚠️ *Needs to be added*
4. **World War I (1914-1918)** - `borders_ww1.geojson` ⚠️ *Needs to be added*
5. **Pre-WWI (1900)** - `borders_pre_ww1.geojson` ⚠️ *Needs to be added*

## File Format

All border files must be in GeoJSON format with the following structure:

```json
{
  "type": "FeatureCollection",
  "features": [
    {
      "type": "Feature",
      "properties": {
        "NAME": "Country Name"
      },
      "geometry": {
        "type": "Polygon" or "MultiPolygon",
        "coordinates": [...]
      }
    }
  ]
}
```

## Adding Historical Border Data

### Option 1: Natural Earth Data (Recommended)

1. Visit [Natural Earth Data](https://www.naturalearthdata.com/)
2. Download historical boundary datasets
3. Convert to GeoJSON if necessary (using QGIS or ogr2ogr)
4. Rename to match the expected filename (e.g., `borders_cold_war.geojson`)
5. Place in `app/src/main/assets/` directory

### Option 2: Custom Historical Sources

For specific historical periods (Cold War, WW2, WW1), you may need to:

1. Find historical GIS datasets from academic sources
2. Manually create/edit borders based on historical maps
3. Use tools like QGIS to create GeoJSON files

### Important Notes for Cold War Borders

The Cold War period (1947-1991) should include:
- **Divided Germany**: West Germany (BRD) and East Germany (DDR)
- **Divided Berlin**: West Berlin and East Berlin
- **Soviet Union (USSR)**: Before its dissolution in 1991
- **Yugoslavia**: As a single country (before breakup in 1990s)
- **Czechoslovakia**: Before split into Czech Republic and Slovakia (1993)
- **Iron Curtain boundaries**: Clear distinction between Eastern and Western Europe

### Example Sources

- **Cold War borders**: [CShapes Dataset](http://nils.weidmann.ws/projects/cshapes.html) - Historical country boundaries
- **Historical GIS data**: [HGIS Germany](https://www.digihist.de/html/hgis-en.html)
- **OpenHistoricalMap**: [OpenHistoricalMap.org](https://www.openhistoricalmap.org/)

## File Fallback

If a historical border file is not found, the app will automatically fall back to displaying modern borders (ne_110m_admin_0_countries.geojson).

## Testing

After adding new border files:

1. Open the Map Viewer
2. Enable "Country Borders" in Map Tools
3. Use the "Historical Period" dropdown to switch epochs
4. Verify borders display correctly for your region of interest

## File Size Considerations

- Keep GeoJSON files reasonably sized (< 10 MB recommended)
- Use simplified geometries for better performance
- Consider using Natural Earth's 110m (low resolution) datasets for global coverage
- Use 50m or 10m datasets only for specific regions if needed

## Updates

To update an existing border file:
1. Replace the file in `app/src/main/assets/`
2. Rebuild the app
3. The app will automatically reload the new data

## Contributing

If you create accurate historical border datasets, consider:
1. Documenting your sources
2. Sharing them with the community
3. Opening a pull request to include them in the app
