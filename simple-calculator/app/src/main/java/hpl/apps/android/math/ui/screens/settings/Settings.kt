package hpl.apps.android.math.ui.screens.settings

import android.content.Context
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import hpl.apps.android.math.AppInfo
import hpl.apps.android.math.R
import hpl.apps.android.math.data.UserPreferencesRepository
import hpl.apps.android.math.ui.GetData

@Composable
fun Settings(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory)
){
    val context = LocalContext.current
    var showPrecisionDialog: Boolean by rememberSaveable { mutableStateOf(false) }
    var showThemeDialog: Boolean by rememberSaveable { mutableStateOf(false) }

    val precision by viewModel.precision.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val dynamicColor by viewModel.dynamicColor.collectAsState()
    val historySize by viewModel.historySize.collectAsState()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.settings),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier
                .padding(
                    vertical = dimensionResource(R.dimen.heading_vert_padding),
                    horizontal = dimensionResource(R.dimen.heading_horiz_padding)
                )
        )
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            SettingsItem(
                title = stringResource(R.string.appearance),
                style = MaterialTheme.typography.titleSmall,
                summary = null,
                isSectionHeader = true,
                onClick = {}
            )
            HorizontalDivider()
            SettingsItem(
                title = stringResource(R.string.theme),
                summary = themeLabel(themeMode),
                onClick = { showThemeDialog = true }
            )
            HorizontalDivider()
            SettingsItem(
                title = stringResource(R.string.dynamic_color),
                summary = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    stringResource(R.string.dynamic_color_description)
                } else {
                    stringResource(R.string.dynamic_color_unsupported)
                },
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        viewModel.setDynamicColor(!dynamicColor)
                    }
                },
                trailing = {
                    Switch(
                        checked = dynamicColor,
                        onCheckedChange = { viewModel.setDynamicColor(it) },
                        enabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    )
                }
            )
            HorizontalDivider()

            SettingsItem(
                title = stringResource(R.string.calculation),
                style = MaterialTheme.typography.titleSmall,
                summary = null,
                isSectionHeader = true,
                onClick = {}
            )
            HorizontalDivider()
            SettingsItem(
                title = stringResource(R.string.precision),
                summary = stringResource(R.string.precision_current, precision),
                onClick = { showPrecisionDialog = true }
            )
            HorizontalDivider()
            SettingsItem(
                title = stringResource(R.string.clear_history),
                summary = if (historySize == 0) {
                    stringResource(R.string.history_empty)
                } else {
                    stringResource(R.string.history_entries, historySize)
                },
                onClick = {
                    viewModel.clearHistory()
                    Toast.makeText(context, R.string.history_cleared, Toast.LENGTH_SHORT).show()
                }
            )
            HorizontalDivider()
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.heading_vert_padding)))
            Text(
                text = stringResource(R.string.version, AppInfo.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = dimensionResource(R.dimen.heading_horiz_padding),
                    vertical = dimensionResource(R.dimen.heading_vert_padding)
                )
            )
        }
    }

    if(showPrecisionDialog){
        PrecisionDialog(
            currentPrecision = precision,
            onDismiss = { showPrecisionDialog = false },
            onSave = { value ->
                viewModel.setPrecision(value)
                showPrecisionDialog = false
            }
        )
    }

    if(showThemeDialog){
        ThemeDialog(
            currentThemeMode = themeMode,
            onDismiss = { showThemeDialog = false },
            onSelect = { mode ->
                viewModel.setThemeMode(mode)
                showThemeDialog = false
            }
        )
    }
}

@Composable
private fun SettingsItem(
    title: String,
    summary: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleMedium,
    isSectionHeader: Boolean = false,
    trailing: (@Composable () -> Unit)? = null
){
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = !isSectionHeader, onClick = onClick)
            .padding(
                vertical = dimensionResource(R.dimen.licence_type_item_vert_padding),
                horizontal = dimensionResource(R.dimen.licence_type_item_horiz_padding)
            )
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = style,
                color = if (isSectionHeader) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onBackground
                }
            )
            if(!summary.isNullOrEmpty()){
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

@Composable
private fun PrecisionDialog(
    currentPrecision: Int,
    onDismiss: () -> Unit,
    onSave: (Int) -> Unit
){
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .clip(MaterialTheme.shapes.small)
                .background(color = MaterialTheme.colorScheme.background)
                .padding(
                    horizontal = dimensionResource(R.dimen.precision_dialog_horiz_padding),
                    vertical = dimensionResource(R.dimen.precision_dialog_vert_padding),
                )
        ) {
            var input by rememberSaveable { mutableStateOf("") }

            val done = {
                val x = input.toIntOrNull()
                if (x == null){
                    precisionConstraintsMessage(context)
                }else{
                    onSave(x)
                }
            }

            Text(
                text = stringResource(R.string.precision),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.precision_dialog_vert_padding)))
            HorizontalDivider()
            Text(
                text = stringResource(R.string.precision_description),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.precision_dialog_vert_padding)))
            TextField(
                value = input,
                placeholder = { Text(currentPrecision.toString()) },
                onValueChange = {
                    if(it.isEmpty()){
                        input  = it
                    }else{
                        val x = it.toIntOrNull()
                        if (x == null || x < GetData.MIN_PRECISION || x > GetData.MAX_PRECISION){
                            precisionConstraintsMessage(context)
                        }else{
                            input = it
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    showKeyboardOnFocus = true,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { done() }
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.precision_dialog_vert_padding)))
            Button(onClick = { done() }) { Text(text = stringResource(R.string.save)) }
        }
    }
}

@Composable
private fun ThemeDialog(
    currentThemeMode: String,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
){
    val options = listOf(
        UserPreferencesRepository.THEME_SYSTEM to R.string.theme_system,
        UserPreferencesRepository.THEME_LIGHT to R.string.theme_light,
        UserPreferencesRepository.THEME_DARK to R.string.theme_dark
    )
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .wrapContentSize()
                .clip(MaterialTheme.shapes.small)
                .background(color = MaterialTheme.colorScheme.background)
                .padding(
                    horizontal = dimensionResource(R.dimen.precision_dialog_horiz_padding),
                    vertical = dimensionResource(R.dimen.precision_dialog_vert_padding),
                )
        ) {
            Text(
                text = stringResource(R.string.theme),
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.precision_dialog_vert_padding)))
            HorizontalDivider()
            options.forEach { (mode, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = mode == currentThemeMode,
                            role = Role.RadioButton,
                            onClick = { onSelect(mode) }
                        )
                        .padding(vertical = dimensionResource(R.dimen.precision_dialog_vert_padding))
                ) {
                    RadioButton(
                        selected = mode == currentThemeMode,
                        onClick = null
                    )
                    Text(
                        text = stringResource(label),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(dimensionResource(R.dimen.precision_dialog_vert_padding)))
            Button(onClick = onDismiss) { Text(text = stringResource(R.string.cancel)) }
        }
    }
}

@Composable
private fun themeLabel(themeMode: String): String = when (themeMode) {
    UserPreferencesRepository.THEME_LIGHT -> stringResource(R.string.theme_light)
    UserPreferencesRepository.THEME_DARK -> stringResource(R.string.theme_dark)
    else -> stringResource(R.string.theme_system)
}

private fun precisionConstraintsMessage(context: Context){
    Toast.makeText(
        context,
        context.getString(
            R.string.precision_constraints,
            GetData.MIN_PRECISION,
            GetData.MAX_PRECISION
        ),
        Toast.LENGTH_LONG
    ).show()
}
