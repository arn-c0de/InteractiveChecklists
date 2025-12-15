package com.example.checklist_interactive.ui.datapad

import androidx.compose.runtime.staticCompositionLocalOf
import com.example.checklist_interactive.data.datapad.DataPadManager

/**
 * CompositionLocal for accessing DataPadManager throughout the UI tree
 */
val LocalDataPadManager = staticCompositionLocalOf<DataPadManager> {
    error("DataPadManager not provided")
}
