package com.example.checklist_interactive.ui.tactical

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.checklist_interactive.R

@Composable
fun DatabaseUpdateDialog(
    assetVersion: Int,
    currentVersion: Int,
    onMerge: () -> Unit,
    onClean: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.db_update_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.db_update_message, currentVersion, assetVersion),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                
                Text(
                    text = stringResource(R.string.db_update_options),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                
                Text(
                    text = "• " + stringResource(R.string.db_update_option_merge),
                    style = MaterialTheme.typography.bodySmall
                )
                
                Text(
                    text = "• " + stringResource(R.string.db_update_option_clean),
                    style = MaterialTheme.typography.bodySmall
                )
                
                Text(
                    text = "• " + stringResource(R.string.db_update_option_skip),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.db_update_btn_skip))
                }
                
                FilledTonalButton(onClick = onMerge) {
                    Text(stringResource(R.string.db_update_btn_merge))
                }
                
                Button(onClick = onClean) {
                    Text(stringResource(R.string.db_update_btn_clean))
                }
            }
        }
    )
}
