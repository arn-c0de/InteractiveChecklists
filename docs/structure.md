[Home](../README.md) | [Documentation Navigation](docnavigation.md)

# Kotlin Code Structure

## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\MainActivity.kt

**Package:** com.example.checklist_interactive

### Classes

- **MainActivity** (class)
    - **Properties:** "softwareVersion", "prefsManager", "isDarkTheme", "toggleTheme", "openFile", "openPage", "showFileList", "showSettings", "backHandlerEnabled", "refreshTrigger", "fileManager", "repository", "scope", "context", "showImportDialog", "pickMultipleDocumentsLauncher", "pickDocumentTreeLauncher", "docFile", "requestMultiplePermissionsLauncher", "anyGranted", "importedAgain", "permissionsToRequest", "missing", "importedFromExternal", "lastImportedVersion", "currentVersion", "imported", "lastFilePath", "allFiles", "lastFile", "lifecycleOwner", "observer", "importedAgain", "rootPath", "observer", "p", "allFiles", "targetFile", "allFiles", "targetFile", "allFiles", "targetFile"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\checklist\AssetBrowser.kt

**Package:** com.example.checklist_interactive.data.checklist

### Classes

- **AssetBrowser** (class)
    - **Properties:** "normalized", "results", "children", "childPath", "sub", "isDir", "grouped", "normalized", "children", "childPath", "sub", "isDir", "folderName"
    - **Methods:** "list()", "scanAllFilesGrouped()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\checklist\AssetNode.kt

**Package:** com.example.checklist_interactive.data.checklist

### Classes

- **AssetNode** (class)
    - **Properties:** "name", "path", "isDirectory"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\checklist\Checklist.kt

**Package:** com.example.checklist_interactive.data.checklist

### Classes

- **Checklist** (class)
    - **Properties:** "id", "title", "sections"

- **ChecklistSection** (class)
    - **Properties:** "title", "items"

- **ChecklistItem** (class)
    - **Properties:** "id", "text", "indent", "isChecked"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\checklist\ChecklistRepository.kt

**Package:** com.example.checklist_interactive.data.checklist

### Classes

- **ChecklistRepository** (class)
    - **Properties:** "prefs", "prefs", "prefs", "prefs", "prefs"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\checklist\MarkdownChecklistParser.kt

**Package:** com.example.checklist_interactive.data.checklist

### Classes

- **MarkdownChecklistParser** (class)
    - **Properties:** "document", "checklistTitle", "sections", "currentSectionTitle", "currentItems", "itemCounter", "title", "itemId", "fallback", "title", "visitor", "title", "marker", "textNode", "text", "firstParagraph", "rawText", "regex", "match", "checked", "text", "items", "counter", "match", "checked", "text", "title"
    - **Methods:** "parse()", "ensureSection()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\checklist\PreferenceUtils.kt

**Package:** com.example.checklist_interactive.data.checklist

### Classes

- **PreferenceUtils** (object)
    - **Properties:** "safeId", "digest", "hashBytes"
    - **Methods:** "getPreferenceNameForChecklist()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\files\InternalFileManager.kt

**Package:** com.example.checklist_interactive.data.files

### Classes

- **FileInfo** (class)
    - **Properties:** "name", "displayName", "path", "category", "size", "lastModified", "extension", "isAsset", "tags"

- **InternalFileManager** (class)
    - **Properties:** "file", "relativePath", "tags", "hasSupportedFiles", "hasSubDirs", "internal", "assetTop", "arr", "categoryDir", "categoryDir", "results", "categoryDir", "ext", "nodes", "result", "allowedAssetFolders", "parts", "currentPath", "found", "listParent", "match", "allowedAssetFolders", "topLevel", "results", "list", "childPath", "sub", "lower"
    - **Methods:** "getRelativePath()", "enrichWithTags()", "enrichWithTags()", "getInternalRootPath()", "getCategories()", "createCategory()", "deleteCategory()", "getFilesInCategory()", "getAllFilesGrouped()", "getAllCategoryPaths()", "collect()"

- **FolderNode** (class)
    - **Properties:** "name", "relativePath", "children", "files", "internalNodes", "allowedAssetFolders", "assetNodes", "merged", "k", "existing", "children", "files", "allowedAssetFolders", "children", "childRel", "files", "l", "fullAssetPath", "nodeName", "childrenMap", "k", "existing", "filesMap", "categoryDir", "originalName", "extension", "destFile", "finalFile", "baseName", "ext", "fileInfo", "file", "sourceFile", "categoryDir", "destFile", "oldRelativePath", "newRelativePath", "fileInfo", "sourceFile", "extension", "newFileName", "destFile", "oldRelativePath", "newRelativePath", "category", "fileInfo", "fileName", "nameIndex", "file", "categoryDir", "fileName", "destFile", "fileInfo", "imported", "assetNames", "removed", "allowedAssetFolders", "list", "childPath", "sub", "nextRel", "destDir", "destFile", "names", "list", "childPath", "sub", "lower", "removed", "ext", "internalBase", "conflict", "imported", "importsFolder", "downloadsDir", "downloadImports", "externalStorage", "rootImports", "imported", "nextRel", "ext", "destDir", "destFile"
    - **Methods:** "getFolderTree()", "keyOf()", "importFile()", "deleteFile()", "moveFile()", "renameFile()", "getFile()", "importAssetFile()", "importAllBundledAssets()", "walker()", "walker()", "wipeInternalRoot()", "importFromExternalImportsFolder()", "walker()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\prefs\ContributorEntry.kt

**Package:** com.example.checklist_interactive.data.prefs

### Classes

- **ContributorEntry** (class)
    - **Properties:** "name", "website", "role"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\prefs\InvertColorPrefManager.kt

**Package:** com.example.checklist_interactive.data.prefs

### Classes

- **InvertColorPrefManager** (class)
    - **Methods:** "isInverted()", "setInverted()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\prefs\PreferencesManager.kt

**Package:** com.example.checklist_interactive.data.prefs

### Classes

- **PreferencesManager** (class)
    - **Properties:** "set", "current", "json", "raw", "json", "raw"
    - **Methods:** "setCategoryExpanded()", "isCategoryExpanded()", "getAllCategoryStates()", "setDarkModeEnabled()", "isDarkModeEnabled()", "isFirstLaunch()", "setFirstLaunchComplete()", "setImportFolderUri()", "getImportFolderUri()", "hasShownImportDialog()", "setImportDialogShown()", "setInt()", "getInt()", "setMarkdownFontSize()", "getMarkdownFontSize()", "setMarkdownSectionsExpandedByDefault()", "areMarkdownSectionsExpandedByDefault()", "setVisibleAircrafts()", "getVisibleAircrafts()", "isAircraftVisible()", "setAircraftVisible()", "resetVisibleAircrafts()", "setActiveTagFilters()", "getActiveTagFilters()", "clearTagFilters()", "setTagFilterMode()", "getTagFilterMode()", "registerOnChangeListener()", "unregisterOnChangeListener()", "getLastImportedVersion()", "setLastImportedVersion()", "setDocumentSources()", "getDocumentSources()", "resetDocumentSourcesToDefaults()", "setContributors()", "getContributors()", "resetContributorsToDefaults()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\prefs\SourceEntry.kt

**Package:** com.example.checklist_interactive.data.prefs

### Classes

- **SourceEntry** (class)
    - **Properties:** "name", "website", "license"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\quicknotes\QuickNoteManager.kt

**Package:** com.example.checklist_interactive.data.quicknotes

### Classes

- **LinkedDocument** (class)
    - **Properties:** "id", "filePath", "fileName", "pageNumber", "timestamp"

- **QuickNote** (class)
    - **Properties:** "id", "title", "content", "linkedDocuments", "timestamp"

- **QuickNoteManager** (class)
    - **Properties:** "notes", "activeNoteId", "noteContent", "linkedDocuments", "loadedNotes", "notesToUse", "defaultNote", "loadedActiveId", "notes", "activeId", "note", "activeId", "activeNote", "json", "array", "list", "larr", "obj", "json", "array", "obj", "id", "current", "idx", "note", "id", "id", "targetId", "current", "idx", "note", "docs", "targetId", "current", "idx", "note", "docs", "id", "current", "idx", "note", "array", "obj", "lad", "dobj", "json", "array", "obj", "linkedDocs", "la", "d", "oldContent", "oldLinked", "linked", "arr", "obj", "note", "id", "current", "current", "current", "idx", "note", "exists"
    - **Methods:** "saveNoteContent()", "saveNote()", "clearActiveNote()", "clearNote()", "addLinkedDocument()", "removeLinkedDocument()", "removeLinkedDocument()", "clearActiveNoteLinkedDocuments()", "clearLinkedDocuments()", "addNote()", "removeNote()", "renameNote()", "setActiveNote()", "clearAllNotes()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\shortcuts\LastPageManager.kt

**Package:** com.example.checklist_interactive.data.shortcuts

### Classes

- **LastPageRecord** (class)
    - **Properties:** "filePath", "pageNumber", "lastAccessedAt"

- **LastPageManager** (class)
    - **Properties:** "jsonString", "jsonString", "lastPages", "lastPages", "cutoffTime", "lastPages"
    - **Methods:** "saveLastPage()", "getLastPage()", "clearLastPage()", "clearOldEntries()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\shortcuts\PageHighlightManager.kt

**Package:** com.example.checklist_interactive.data.shortcuts

### Classes

- **PageHighlight** (class)
    - **Properties:** "filePath", "pageNumber", "color", "createdAt"

- **PageHighlightManager** (class)
    - **Properties:** "jsonString", "jsonString", "highlights", "existing", "highlights"
    - **Methods:** "loadHighlights()", "togglePageHighlight()", "isPageHighlighted()", "getHighlightsForFile()", "clearHighlightsForFile()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\shortcuts\PageShortcut.kt

**Package:** com.example.checklist_interactive.data.shortcuts

### Classes

- **PageShortcut** (class)
    - **Properties:** "id", "name", "filePath", "pageNumber", "isHighlighted", "createdAt", "fileName"

- **ShortcutManager** (class)
    - **Properties:** "jsonString", "jsonString", "shortcuts", "shortcut", "shortcuts", "shortcuts", "index", "shortcuts", "index"
    - **Methods:** "loadShortcuts()", "createShortcut()", "deleteShortcut()", "renameShortcut()", "updateHighlightStatus()", "getShortcutsForFile()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\data\tags\FileTagManager.kt

**Package:** com.example.checklist_interactive.data.tags

### Classes

- **FileTag** (class)
    - **Properties:** "filePath", "tags"

- **FileTagManager** (class)
    - **Properties:** "assetTags", "s", "internal", "key", "existing", "union", "list", "childPath", "sub", "lower", "assetPath", "key", "heurTags", "name", "tags", "jsonString", "jsonString", "normalized", "all", "exact", "fileName", "matches", "s", "normalized", "allTags", "existingIndex", "fileTag", "currentTags", "currentTags", "tags", "p", "SUGGESTED_TAGS"
    - **Methods:** "walker()", "loadFileTags()", "getTagsForFile()", "setTagsForFile()", "addTagToFile()", "removeTagFromFile()", "getAllUsedTags()", "getFilesWithTag()", "getFilesWithAnyTag()", "getFilesWithAllTags()", "removeFileFromTags()", "updateFilePath()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\Annotation.kt

**Package:** com.example.checklist_interactive.ui.checklist

### Classes

- **AnnotationStroke** (class)
    - **Properties:** "page", "color", "strokeWidth", "points", "isHighlight"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\AnnotationsRepository.kt

**Package:** com.example.checklist_interactive.ui.checklist

### Classes

- **AnnotationsRepository** (object)
    - **Properties:** "dir", "name", "file", "arr", "o", "pts", "pp", "file", "s", "arr", "out", "o", "page", "color", "strokeWidth", "isHighlight", "pts", "points", "p"
    - **Methods:** "save()", "load()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\BrowseScreen.kt

**Package:** com.example.checklist_interactive.ui.checklist


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\CategorizedFilesScreen.kt

**Package:** com.example.checklist_interactive.ui.checklist


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\ChecklistScreen.kt

**Package:** com.example.checklist_interactive.ui.checklist


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\ChecklistViewModel.kt

**Package:** com.example.checklist_interactive.ui.checklist

### Classes

- **ChecklistViewModel** (class)
    - **Properties:** "checklistState", "savedStates", "updatedSections", "updatedItems", "updatedSections", "updatedItems", "updatedSections", "updatedItems", "checklist", "sb", "checkbox", "indent"
    - **Methods:** "onCheckboxChange()", "resetChecklist()", "exportChecklistMarkdown()"

- **ChecklistViewModelFactory** (class)


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\FolderSelectionDialog.kt

**Package:** com.example.checklist_interactive.ui.checklist


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\MarkdownViewer.kt

**Package:** com.example.checklist_interactive.ui.checklist

### Classes

- **MarkdownSection** (class)


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\MarkdownViewerScreen.kt

**Package:** com.example.checklist_interactive.ui.checklist


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\PdfTextExtractor.kt

**Package:** com.example.checklist_interactive.ui.checklist

### Classes

- **PdfTextBlock** (class)
    - **Properties:** "text", "x", "y", "width", "height", "fontSize"

- **PdfTextExtractor** (class)
    - **Properties:** "currentPath", "textBlocks", "document", "stripper", "currentText", "minX", "minY", "maxX", "maxY", "fontSize", "x", "y", "width", "height", "document", "stripper"
    - **Methods:** "cleanup()"


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\PdfViewer.kt

**Package:** com.example.checklist_interactive.ui.checklist


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\checklist\UnifiedViewer.kt

**Package:** com.example.checklist_interactive.ui.checklist


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\files\InternalFilesScreen.kt

**Package:** com.example.checklist_interactive.ui.files


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\files\InternalFileViewer.kt

**Package:** com.example.checklist_interactive.ui.files


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\quickaccess\QuickAccessSheet.kt

**Package:** com.example.checklist_interactive.ui.quickaccess


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\settings\SettingsScreen.kt

**Package:** com.example.checklist_interactive.ui.settings


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\tags\TagComponents.kt

**Package:** com.example.checklist_interactive.ui.tags


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\theme\Color.kt

**Package:** com.example.checklist_interactive.ui.theme


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\theme\Theme.kt

**Package:** com.example.checklist_interactive.ui.theme


## C:\Users\arn\AndroidStudioProjects\ChecklistInteractive\app\src\main\java\com\example\checklist_interactive\ui\theme\Type.kt

**Package:** com.example.checklist_interactive.ui.theme

