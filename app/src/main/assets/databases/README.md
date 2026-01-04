**Important — Fresh install required (v1.0.21)** ⚠️  
The pre-packaged map database (`assets/databases/map_data.db`) was reset to **version 1** in **v1.0.21**. If you are upgrading from an earlier build, you **must fully uninstall the app (including app data)** and then install the new APK to avoid schema mismatch errors and potential data loss. Back up any local database files if you need to preserve custom markers.

This folder stores the shared database used by the Android app and Python tools.

This folder stores the shared marker database used by the Android app and Python tools.

Do not check large runtime DB files into source control; use this folder to keep a pre-populated development DB named `map_data.db` if needed.

To copy the DB to a connected Android device (external files dir):

    adb push app/database/map_data.db /sdcard/Android/data/com.example.checklist_interactive/files/map_data.db

Or, to import it into the app during development, use the app's import utility if present.