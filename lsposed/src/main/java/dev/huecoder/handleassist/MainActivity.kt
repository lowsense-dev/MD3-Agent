package dev.huecoder.handleassist

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.rounded.Apps
import com.composables.icons.materialsymbols.rounded.Assistant
import com.composables.icons.materialsymbols.rounded.Block
import com.composables.icons.materialsymbols.rounded.Check_circle
import com.composables.icons.materialsymbols.rounded.Gesture
import com.composables.icons.materialsymbols.rounded.Gesture_select
import com.composables.icons.materialsymbols.rounded.History
import com.composables.icons.materialsymbols.rounded.Power_settings_new
import com.composables.icons.materialsymbols.rounded.Search
import com.composables.icons.materialsymbols.rounded.Settings_power
import com.composables.icons.materialsymbols.rounded.Warning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val (prefs, active) = openPrefs()
        setContent { HandleAssistTheme { SettingsScreen(prefs, active) } }
    }

    /** LSPosed only allows MODE_WORLD_READABLE while the module is enabled, which doubles as the status check. */
    @SuppressLint("WorldReadableFiles")
    @Suppress("DEPRECATION")
    private fun openPrefs(): Pair<SharedPreferences, Boolean> = try {
        getSharedPreferences(Prefs.FILE, Context.MODE_WORLD_READABLE) to true
    } catch (_: SecurityException) {
        getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE) to false
    }
}

private data class ActionOption(val key: String, @StringRes val title: Int, @StringRes val hint: Int, val icon: ImageVector)

private val ASSISTANT = ActionOption(Prefs.ACTION_ASSISTANT, R.string.action_assistant, R.string.action_assistant_hint, MaterialSymbols.Rounded.Assistant)
private val CIRCLE_SEARCH = ActionOption(Prefs.ACTION_CIRCLE_SEARCH, R.string.action_circle_search, R.string.action_circle_search_hint, MaterialSymbols.Rounded.Gesture_select)
private val APP = ActionOption(Prefs.ACTION_APP, R.string.action_app, R.string.action_app_hint, MaterialSymbols.Rounded.Apps)
private val NONE = ActionOption(Prefs.ACTION_NONE, R.string.action_none, R.string.action_none_hint, MaterialSymbols.Rounded.Block)

private val HANDLE_ACTIONS = listOf(
    ASSISTANT, CIRCLE_SEARCH, APP,
    ActionOption(Prefs.ACTION_STOCK, R.string.action_stock_handle, R.string.action_stock_handle_hint, MaterialSymbols.Rounded.History),
    NONE,
)

private val POWER_ACTIONS = listOf(
    ASSISTANT, CIRCLE_SEARCH, APP,
    ActionOption(Prefs.ACTION_POWER_MENU, R.string.action_power_menu, R.string.action_power_menu_hint, MaterialSymbols.Rounded.Power_settings_new),
    ActionOption(Prefs.ACTION_STOCK, R.string.action_stock_power, R.string.action_stock_power_hint, MaterialSymbols.Rounded.History),
    NONE,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HandleAssistTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    MaterialExpressiveTheme(
        colorScheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context),
        motionScheme = MotionScheme.expressive(),
        content = content,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SettingsScreen(prefs: SharedPreferences, active: Boolean) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = { LargeFlexibleTopAppBar(title = { Text(stringResource(R.string.app_name)) }, scrollBehavior = scrollBehavior) },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { StatusCard(active) }
            item { TriggerSection(prefs, Prefs.TRIGGER_HANDLE, R.string.trigger_handle, MaterialSymbols.Rounded.Gesture, HANDLE_ACTIONS) }
            item { TriggerSection(prefs, Prefs.TRIGGER_POWER, R.string.trigger_power, MaterialSymbols.Rounded.Settings_power, POWER_ACTIONS) }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun StatusCard(active: Boolean) {
    val colors = MaterialTheme.colorScheme
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (active) colors.primaryContainer else colors.errorContainer,
            contentColor = if (active) colors.onPrimaryContainer else colors.onErrorContainer,
        ),
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapedIcon(
                icon = if (active) MaterialSymbols.Rounded.Check_circle else MaterialSymbols.Rounded.Warning,
                shape = if (active) MaterialShapes.Cookie9Sided.toShape() else MaterialShapes.Burst.toShape(),
                container = if (active) colors.primary else colors.error,
                content = if (active) colors.onPrimary else colors.onError,
                size = 52,
            )
            Column(Modifier.padding(start = 16.dp)) {
                Text(stringResource(if (active) R.string.status_active else R.string.status_inactive), style = MaterialTheme.typography.titleLargeEmphasized)
                Text(stringResource(if (active) R.string.status_active_hint else R.string.status_inactive_hint), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TriggerSection(prefs: SharedPreferences, trigger: String, @StringRes title: Int, icon: ImageVector, options: List<ActionOption>) {
    var action by remember { mutableStateOf(prefs.getString(Prefs.actionKey(trigger), Prefs.ACTION_ASSISTANT)) }
    var app by remember { mutableStateOf(prefs.getString(Prefs.appKey(trigger), null)) }
    var picking by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    Row(Modifier.padding(start = 8.dp, top = 24.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.primary)
        Text(stringResource(title), style = MaterialTheme.typography.titleMediumEmphasized, color = colors.primary, modifier = Modifier.padding(start = 8.dp))
    }
    Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
        options.forEachIndexed { index, option ->
            val selected = option.key == action
            val chosenApp = if (option.key == Prefs.ACTION_APP) rememberAppInfo(app) else null
            SegmentedListItem(
                onClick = {
                    if (option.key == Prefs.ACTION_APP) picking = true
                    else {
                        action = option.key
                        prefs.edit().putString(Prefs.actionKey(trigger), option.key).apply()
                    }
                },
                selected = selected,
                shapes = ListItemDefaults.segmentedShapes(index, options.size),
                colors = ListItemDefaults.segmentedColors(
                    containerColor = colors.surfaceContainer,
                    selectedContainerColor = colors.secondaryContainer,
                ),
                leadingContent = {
                    ShapedIcon(
                        icon = option.icon,
                        shape = if (selected) MaterialShapes.Cookie7Sided.toShape() else CircleShape,
                        container = if (selected) colors.primary else colors.surfaceContainerHighest,
                        content = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                    )
                },
                supportingContent = { Text(chosenApp?.label ?: stringResource(option.hint)) },
                trailingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        chosenApp?.let { Image(it.icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp).size(32.dp)) }
                        RadioButton(selected = selected, onClick = null)
                    }
                },
            ) { Text(stringResource(option.title), style = MaterialTheme.typography.titleMedium) }
        }
    }

    if (picking) {
        AppPickerSheet(
            onDismiss = { picking = false },
            onPick = { pkg ->
                picking = false
                app = pkg
                action = Prefs.ACTION_APP
                prefs.edit().putString(Prefs.appKey(trigger), pkg).putString(Prefs.actionKey(trigger), Prefs.ACTION_APP).apply()
            },
        )
    }
}

@Composable
private fun ShapedIcon(icon: ImageVector, shape: Shape, container: Color, content: Color, size: Int = 40) {
    Box(Modifier.size(size.dp).background(container, shape), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size((size * 0.55f).dp))
    }
}

private class AppEntry(val pkg: String, val label: String, val icon: ImageBitmap)

private fun PackageManager.appEntry(pkg: String): AppEntry? = try {
    val info = getApplicationInfo(pkg, 0)
    AppEntry(pkg, getApplicationLabel(info).toString(), getApplicationIcon(info).toBitmap(96, 96).asImageBitmap())
} catch (_: PackageManager.NameNotFoundException) {
    null
}

@Composable
private fun rememberAppInfo(pkg: String?): AppEntry? {
    val pm = LocalContext.current.packageManager
    return remember(pkg) { pkg?.let(pm::appEntry) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AppPickerSheet(onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val pm = LocalContext.current.packageManager
    val apps by produceState<List<AppEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .mapNotNull(pm::appEntry)
                .sortedBy { it.label.lowercase() }
        }
    }
    var query by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            stringResource(R.string.picker_title),
            style = MaterialTheme.typography.headlineSmallEmphasized,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.picker_search)) },
            leadingIcon = { Icon(MaterialSymbols.Rounded.Search, contentDescription = null) },
            singleLine = true,
            shape = CircleShape,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        )
        val list = apps
        if (list == null) {
            Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
        } else {
            val filtered = list.filter { query.isBlank() || it.label.contains(query, ignoreCase = true) || it.pkg.contains(query, ignoreCase = true) }
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(filtered, key = { it.pkg }) { entry ->
                    ListItem(
                        headlineContent = { Text(entry.label) },
                        supportingContent = { Text(entry.pkg, style = MaterialTheme.typography.bodySmall) },
                        leadingContent = { Image(entry.icon, contentDescription = null, modifier = Modifier.size(40.dp)) },
                        modifier = Modifier.clickable { onPick(entry.pkg) },
                    )
                }
            }
        }
    }
}
