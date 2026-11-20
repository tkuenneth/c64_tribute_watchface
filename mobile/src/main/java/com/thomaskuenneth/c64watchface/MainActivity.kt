package com.thomaskuenneth.c64watchface

import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.preference.PreferenceManager
import com.mikepenz.aboutlibraries.ui.compose.LibraryDefaults
import com.mikepenz.aboutlibraries.ui.compose.android.produceLibraries
import com.mikepenz.aboutlibraries.ui.compose.m3.LibrariesContainer
import com.mikepenz.aboutlibraries.ui.compose.m3.libraryColors
import com.mikepenz.aboutlibraries.ui.compose.m3.style.m3VariantColors
import com.thomaskuenneth.common.C64

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)

        setContent {
            AppTheme {
                MainAppScreen(sharedPreferences = sharedPreferences)
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

@Composable
fun MainAppScreen(sharedPreferences: SharedPreferences) {
    var currentScreen by rememberSaveable { mutableStateOf(Screen.Main) }

    BackHandler(enabled = currentScreen != Screen.Main) {
        currentScreen = Screen.Main
    }

    when (currentScreen) {
        Screen.Main -> WatchFaceScreen(
            sharedPreferences = sharedPreferences,
            onNavigateSettings = { currentScreen = Screen.Settings },
            onNavigateInfo = { currentScreen = Screen.Info }
        )
        Screen.Settings -> SettingsScreen(
            sharedPreferences = sharedPreferences,
            onBack = { currentScreen = Screen.Main }
        )
        Screen.Info -> InfoScreen(
            onBack = { currentScreen = Screen.Main }
        )
    }
}

enum class Screen {
    Main, Settings, Info
}

private const val PREFS_HINT_ACKNOWLEDGED = "hint_acknowledged"

@Suppress("UnusedMaterial3ScaffoldPaddingParameter")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchFaceScreen(
    sharedPreferences: SharedPreferences,
    onNavigateSettings: () -> Unit,
    onNavigateInfo: () -> Unit
) {
    var showTopBar by rememberSaveable { mutableStateOf(true) }
    val snackbarHostState = remember { SnackbarHostState() }
    val hintMessage = stringResource(R.string.toast_toggle_fullscreen)
    val hintAction = stringResource(R.string.got_it)

    LaunchedEffect(Unit) {
        if (!sharedPreferences.getBoolean(PREFS_HINT_ACKNOWLEDGED, false)) {
            val result = snackbarHostState.showSnackbar(
                message = hintMessage,
                actionLabel = hintAction,
                duration = SnackbarDuration.Indefinite
            )
            if (result == SnackbarResult.ActionPerformed) {
                sharedPreferences.edit { putBoolean(PREFS_HINT_ACKNOWLEDGED, true) }
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
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    showTopBar = !showTopBar
                }
        ) {
            AndroidView(
                factory = { context ->
                    C64WatchFace(context).apply {
                        val c64 = C64(context, sharedPreferences)
                        c64.centerHorizontally = false
                        c64.setup()
                        tag = c64
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { view ->
                    (view.tag as? C64)?.last = -1f
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(sharedPreferences: SharedPreferences, onBack: () -> Unit) {
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
                key = "date",
                sharedPreferences = sharedPreferences
            )
            PreferenceSwitch(
                title = stringResource(R.string.seconds),
                key = "seconds",
                sharedPreferences = sharedPreferences
            )
            PreferenceSwitch(
                title = stringResource(R.string.uppercase),
                key = "uppercase",
                sharedPreferences = sharedPreferences
            )
        }
    }
}

@Composable
private fun rememberPreference(sharedPreferences: SharedPreferences, key: String): Boolean {
    var value by remember(sharedPreferences, key) {
        mutableStateOf(sharedPreferences.getBoolean(key, false))
    }
    DisposableEffect(sharedPreferences, key) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { preferences, changed ->
            if (changed == key) {
                value = preferences.getBoolean(key, false)
            }
        }
        sharedPreferences.registerOnSharedPreferenceChangeListener(listener)
        onDispose { sharedPreferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    return value
}

@Composable
fun PreferenceSwitch(title: String, key: String, sharedPreferences: SharedPreferences) {
    val isChecked = rememberPreference(sharedPreferences, key)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                sharedPreferences.edit { putBoolean(key, !isChecked) }
            }
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = isChecked,
            onCheckedChange = { sharedPreferences.edit { putBoolean(key, it) } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    val appIcon = remember(context) {
        context.packageManager.getApplicationIcon(context.applicationInfo).toBitmap()
            .asImageBitmap()
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
        val licenseText = remember(resources) {
            resources.openRawResource(R.raw.font_license).bufferedReader().use { it.readText() }
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                bitmap = appIcon,
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(72.dp)
            )

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

            OutlinedButton(
                onClick = { safeUriHandler.openUri(sourceCodeUrl) },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.source_code))
            }

            OutlinedButton(
                onClick = { showOpenSourceLicenses = true },
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(stringResource(R.string.open_source_licenses))
            }

            OutlinedButton(
                onClick = { showLicenseDialog = true },
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(stringResource(R.string.view_license))
            }
        }
    }
}
