[Home](../../README.md) | [Documentation Navigation](../docnavigation.md)

# Auto-importing Files from the Imports Folder

The app automatically loads all PDF and Markdown files from the `Imports/` folder at startup.

## Folder Locations

Create an `Imports/` folder in one of the following locations:

1. **App-specific** (recommended — no additional permission required):
    ```
    Android/data/com.example.checklist_interactive/files/Imports/
    ```

2. **Downloads folder**:
    ```
    Download/Imports/
    ```

3. **External storage root**:
    ```
    /storage/emulated/0/Imports/
    ```

## Category System

Each subfolder inside the `Imports/` folder is treated as a category in the "My Files" view. Nested folders are preserved and shown as collapsible folders (e.g., `Checklists/F-16_Viper`).

### Example Folder Structure:

```
Imports/
├── Checklists/
│   ├── Checklist1.pdf
│   └── Checklist2.pdf
├── RadioCommunications/
│   ├── Radio-Guide.pdf
│   └── Frequencies.pdf
├── Procedures/
│   ├── Emergency.pdf
│   └── Standard.pdf
└── Manuals/
      └── Aircraft-Manual.pdf
```

### Result in the App:

- **Checklists** (2 files)
   - Checklist1.pdf
   - Checklist2.pdf

- **RadioCommunications** (2 files)
   - Radio-Guide.pdf
   - Frequencies.pdf

- **Procedures** (2 files)
   - Emergency.pdf
   - Standard.pdf

- **Manuals** (1 file)
   - Aircraft-Manual.pdf

## Supported File Types

- **PDF** (.pdf)
- **Markdown** (.md, .markdown)

## Automatic Import Behavior

- Import happens **automatically at app startup**
- Files that were already imported are **not** imported again
- New files are detected on the next app restart
- Categories are **created dynamically** if they don't exist yet

## Notes

- Folder names are normalized to **lowercase**
- Default categories (Checklists, Comms, Charts, Procedures, Manuals) are preserved
- New categories can be added simply by creating subfolders in the `Imports/` folder
- Duplicate filenames are **not** overwritten
