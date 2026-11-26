package com.thomaskuenneth.c64watchface

import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantColors
import com.thomaskuenneth.common.C64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                MainAppScreen()
            }
        }
    }
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

@Serializable
data object WatchFaceRoute : NavKey

@Serializable
data object SettingsRoute : NavKey

@Serializable
data object InfoRoute : NavKey

@Composable
fun MainAppScreen(viewModel: WatchFaceViewModel = viewModel()) {
    val backStack = rememberNavBackStack(WatchFaceRoute)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<WatchFaceRoute> {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                WatchFaceScreen(
                    c64 = viewModel.c64,
                    hintAcknowledged = settings.hintAcknowledged,
                    onHintAcknowledged = viewModel::acknowledgeHint,
                    onNavigateSettings = dropUnlessResumed { backStack.add(SettingsRoute) },
                    onNavigateInfo = dropUnlessResumed { backStack.add(InfoRoute) }
                )
            }
            entry<SettingsRoute> {
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                SettingsScreen(
                    settings = settings,
                    onDate = viewModel::setDate,
                    onSeconds = viewModel::setSeconds,
                    onUppercase = viewModel::setUppercase,
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<InfoRoute> {
                InfoScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
}

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun WatchFaceScreen(
    c64: C64,
    hintAcknowledged: Boolean,
    onHintAcknowledged: () -> Unit,
    onNavigateSettings: () -> Unit,
    onNavigateInfo: () -> Unit
) {
    var showTopBar by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val hintMessage = stringResource(R.string.toast_toggle_fullscreen)
    val hintAction = stringResource(R.string.got_it)

    LaunchedEffect(hintAcknowledged) {
        if (!hintAcknowledged) {
            val result = snackbarHostState.showSnackbar(
                message = hintMessage,
                actionLabel = hintAction,
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                onHintAcknowledged()
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = Color.Black,
        contentColor = Color.White
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .clickable(
                    interactionSource = null,
                    indication = null
                ) {
                    showTopBar = !showTopBar
                }
        ) {
            val context = LocalContext.current
            val watchFace = remember(c64, context) { C64WatchFace(context).apply { data = c64 } }

            LifecycleResumeEffect(c64, watchFace) {
                var blinking = true
                c64.pulse {
                    if (blinking && watchFace.isAttachedToWindow) {
                        watchFace.postInvalidate()
                    }
                    blinking
                }
                onPauseOrDispose {
                    blinking = false
                    c64.stopPulse()
                }
            }

            AndroidView(
                factory = { watchFace },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    view.data?.last = -1f
                    view.invalidate()
                }
            )

            AnimatedVisibility(
                visible = showTopBar,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.7f),
                        titleContentColor = Color.White,
                        actionIconContentColor = Color.White
                    ),
                    actions = {
                        IconButton(onClick = onNavigateSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings)
                            )
                        }
                        IconButton(onClick = onNavigateInfo) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = stringResource(R.string.info)
                            )
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: WatchFaceSettings,
    onDate: (Boolean) -> Unit,
    onSeconds: (Boolean) -> Unit,
    onUppercase: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeContent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.appearance),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            PreferenceSwitch(
                title = stringResource(R.string.date),
                checked = settings.date,
                onCheckedChange = onDate
            )
            PreferenceSwitch(
                title = stringResource(R.string.seconds),
                checked = settings.seconds,
                onCheckedChange = onSeconds
            )
            PreferenceSwitch(
                title = stringResource(R.string.uppercase),
                checked = settings.uppercase,
                onCheckedChange = onUppercase
            )
        }
    }
}

@Composable
fun PreferenceSwitch(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = null
        )
    }
}

@Composable
fun InfoScreen(onBack: () -> Unit) {
    var showLicenseDialog by rememberSaveable { mutableStateOf(false) }
    var showOpenSourceLicenses by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val packageInfo = remember(context) {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val sourceCodeUrl = stringResource(R.string.source_code_url)
    val uriHandler = LocalUriHandler.current
    val safeUriHandler = remember(uriHandler, context) {
        object : UriHandler {
            override fun openUri(uri: String) {
                try {
                    uriHandler.openUri(uri)
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.activity_not_found, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    val appIcon by produceState<ImageBitmap?>(initialValue = null, context) {
        value = withContext(Dispatchers.IO) {
            context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap()
                .asImageBitmap()
        }
    }

    if (showOpenSourceLicenses) {
        val libraries by produceLibraries(R.raw.aboutlibraries)
        val dialogContainerColor = AlertDialogDefaults.containerColor
        AlertDialog(
            onDismissRequest = { showOpenSourceLicenses = false },
            title = { Text(stringResource(R.string.open_source_licenses)) },
            text = {
                CompositionLocalProvider(LocalUriHandler provides safeUriHandler) {
                    LibrariesContainer(
                        libraries = libraries,
                        modifier = Modifier.fillMaxWidth(),
                        colors = LibraryDefaults.libraryColors(
                            libraryBackgroundColor = Color.Transparent,
                            dialogBackgroundColor = dialogContainerColor
                        ),
                        variantColors = LibraryDefaults.m3VariantColors(
                            rowBackground = Color.Transparent,
                            rowExpandedBackground = dialogContainerColor
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showOpenSourceLicenses = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (showLicenseDialog) {
        val resources = LocalResources.current
        val licenseText by produceState("", resources) {
            value = withContext(Dispatchers.IO) {
                resources.openRawResource(R.raw.font_license).bufferedReader().use { it.readText() }
            }
        }
        AlertDialog(
            onDismissRequest = { showLicenseDialog = false },
            title = { Text(stringResource(R.string.font_license_title)) },
            text = {
                Text(
                    text = licenseText,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showLicenseDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.info)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets.safeContent
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            appIcon?.let { icon ->
                Image(
                    bitmap = icon,
                    contentDescription = stringResource(R.string.app_name),
                    modifier = Modifier.size(72.dp)
                )
            }

            Text(
                text = stringResource(
                    R.string.app_name_and_version,
                    stringResource(R.string.app_name),
                    stringResource(
                        R.string.version_format,
                        packageInfo.versionName.orEmpty(),
                        PackageInfoCompat.getLongVersionCode(packageInfo)
                    )
                ),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                text = stringResource(R.string.about1),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp).fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.about2),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.about3),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.about4),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.about5),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.license_note),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                text = stringResource(R.string.third_party_note),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            FilledTonalButton(
                onClick = { showOpenSourceLicenses = true },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.open_source_licenses))
            }

            TextButton(
                onClick = { safeUriHandler.openUri(sourceCodeUrl) },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.source_code))
            }

            TextButton(
                onClick = { showLicenseDialog = true },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.view_license))
            }
        }
    }
}
