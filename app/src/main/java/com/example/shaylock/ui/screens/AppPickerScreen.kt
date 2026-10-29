package com.example.shaylock.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.shaylock.ui.components.AppIcon
import com.example.shaylock.ui.theme.InkAccent
import com.example.shaylock.ui.theme.InkAccentContainer
import com.example.shaylock.ui.theme.InkHairline
import com.example.shaylock.ui.theme.InkOnAccent
import com.example.shaylock.ui.theme.InkSurface
import com.example.shaylock.ui.theme.InkSurfaceHigh
import com.example.shaylock.ui.theme.InkTextPrimary
import com.example.shaylock.ui.theme.InkTextSecondary
import com.example.shaylock.ui.theme.InkTextTertiary
import com.example.shaylock.util.AppCatalog
import com.example.shaylock.util.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The full-screen app picker.
 *
 * Every installed, launchable app is listed with a search field and a multi-select checkbox.
 * Apps that are already protected are filtered out so the list only ever contains actionable
 * choices. Picking happens locally; the selected packages are handed to
 * [com.example.shaylock.core.BlockController.addApps] by the caller.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    modifier: Modifier = Modifier,
    trackedPackages: Set<String>,
    onNavigateBack: () -> Unit,
    onAddApps: (List<String>) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val installed by produceState<List<InstalledApp>?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            AppCatalog.installedLaunchableApps(context)
        }
    }

    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(emptySet<String>()) }

    val candidates = remember(installed, trackedPackages, query) {
        val all = installed.orEmpty()
            .filter { it.packageName !in trackedPackages }
        if (query.isBlank()) {
            all
        } else {
            all.filter {
                it.label.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Add apps", color = InkTextPrimary)
                        Text(
                            text = if (installed == null) "Reading installed apps…"
                            else "${candidates.size} available",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkTextTertiary,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = InkTextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InkSurface)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Button(
                    onClick = { onAddApps(selected.toList()) },
                    enabled = selected.isNotEmpty(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = InkAccent,
                        contentColor = InkOnAccent,
                        disabledContainerColor = InkSurfaceHigh,
                        disabledContentColor = InkTextTertiary,
                    ),
                ) {
                    Text(
                        text = when (selected.size) {
                            0 -> "Select apps to protect"
                            1 -> "Protect 1 app"
                            else -> "Protect ${selected.size} apps"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Each app gets its own daily allowance, usage counter and " +
                        "PIN override budget.",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkTextTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        containerColor = Color.Transparent,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Search apps", color = InkTextTertiary) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = InkTextTertiary,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = InkTextTertiary,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = InkAccent,
                    unfocusedBorderColor = InkHairline,
                    focusedContainerColor = InkSurface,
                    unfocusedContainerColor = InkSurface,
                    cursorColor = InkAccent,
                    focusedTextColor = InkTextPrimary,
                    unfocusedTextColor = InkTextPrimary,
                ),
            )

            when {
                installed == null -> LoadingState()
                candidates.isEmpty() -> EmptyState(
                    message = when {
                        query.isNotBlank() -> "No installed app matches \"$query\"."
                        installed.orEmpty().isEmpty() -> "No launchable apps were found on this device."
                        else -> "Every launchable app is already protected."
                    },
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = 8.dp,
                        bottom = 16.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(candidates, key = { it.packageName }) { app ->
                        AppPickerRow(
                            app = app,
                            selected = app.packageName in selected,
                            onToggle = {
                                selected = if (app.packageName in selected) {
                                    selected - app.packageName
                                } else {
                                    selected + app.packageName
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppPickerRow(
    app: InstalledApp,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) InkAccentContainer else InkSurface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            packageName = app.packageName,
            label = app.label,
            modifier = Modifier.size(40.dp),
            cornerRadius = 12.dp,
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.label,
                style = MaterialTheme.typography.titleSmall,
                color = InkTextPrimary,
                maxLines = 1,
            )
            Text(
                text = app.packageName,
                style = MaterialTheme.typography.labelSmall,
                color = InkTextTertiary,
                maxLines = 1,
            )
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) InkAccent else InkSurfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = InkOnAccent,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = InkAccent,
            modifier = Modifier.size(28.dp),
            strokeWidth = 3.dp,
        )
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = InkTextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
