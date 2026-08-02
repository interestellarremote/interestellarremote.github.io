package com.antigravity.remote.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.antigravity.remote.ui.components.BrandWordmark
import com.antigravity.remote.ui.components.HeroTag
import com.antigravity.remote.ui.components.InterestellarLogo
import com.antigravity.remote.ui.screens.*
import com.antigravity.remote.billing.BillingUiState
import com.antigravity.remote.ui.theme.AntigravityRemoteTheme
import com.antigravity.remote.ui.theme.AppBackground
import com.antigravity.remote.ui.theme.BrandGold
import com.antigravity.remote.ui.theme.NeonCyan
import com.antigravity.remote.ui.theme.TopBarBackground

@Composable
fun RemoteApp(
    viewModel: RemoteViewModel,
    billingState: BillingUiState = BillingUiState(),
    onPurchase: (String) -> Unit = {},
    onManageSubscription: () -> Unit = {},
    onOpenPcDownload: () -> Unit = {},
    onLogin: () -> Unit,
    onLogout: () -> Unit,
    onScan: () -> Unit,
    onPairingUri: (Uri) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(billingState.activeProductId, billingState.message, state.signedIn) {
        if (state.signedIn) {
            viewModel.refreshAccessStatus()
        }
    }
    AntigravityRemoteTheme {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
            if (!state.signedIn) {
                Scaffold(containerColor = Color.Transparent) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize().background(AppBackground)) {
                        LoginScreen(onLogin = onLogin)
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            } else {
                val nav = rememberNavController()
                val startDestination = remember {
                    when {
                        state.selectedProject != null -> Routes.CHAT
                        state.selectedDevice != null -> Routes.PROJECTS
                        else -> Routes.DEVICES
                    }
                }
                val currentBackStackEntry by nav.currentBackStackEntryAsState()
                val currentRoute = currentBackStackEntry?.destination?.route ?: startDestination
                val parentRoute = navigationParentRoute(
                    route = currentRoute,
                    hasSelectedDevice = state.selectedDevice != null,
                    hasSelectedProject = state.selectedProject != null,
                )
                BackHandler(enabled = nav.previousBackStackEntry == null && parentRoute != null) {
                    nav.navigate(requireNotNull(parentRoute)) {
                        popUpTo(currentRoute) { inclusive = true }
                        launchSingleTop = true
                    }
                }
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        Column {
                            RemoteTopBar(nav, state, billingState)
                            if (state.isOffline) {
                                Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.error)) {
                                    Text(
                                        "Sem conexão com a internet",
                                        Modifier.padding(8.dp).fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.onError,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
                        }
                    },
                    bottomBar = {
                        RemoteBottomBar(
                            nav = nav,
                            currentRoute = currentRoute,
                            state = state,
                        )
                    },
                    snackbarHost = {
                        SnackbarHost(remember { SnackbarHostState() }.also { host ->
                            LaunchedEffect(state.error) { state.error?.let { host.showSnackbar(it); viewModel.clearError() } }
                        })
                    }
                ) { padding ->
                    Box(Modifier.padding(padding).fillMaxSize().background(AppBackground)) {
                        NavHost(nav, startDestination = startDestination) {
                            composable(Routes.DEVICES) {
                                DevicesScreen(
                                    state = state,
                                    vm = viewModel,
                                    nav = nav,
                                    onScan = onScan,
                                    onOpenPcDownload = onOpenPcDownload,
                                    subscriptionActive = state.isPro,
                                    onOpenSubscription = { nav.navigate(Routes.SUBSCRIPTION) },
                                )
                            }
                            composable(Routes.PROJECTS) { ProjectsScreen(state, viewModel, nav) }
                            composable(Routes.PROJECT_DASHBOARD) { ProjectDashboardScreen(state, viewModel, nav) }
                            composable(Routes.PROJECT_FILES) { ProjectFilesScreen(state, viewModel) }
                            composable(Routes.CONVERSATIONS) { ConversationsScreen(state, viewModel, nav) }
                            composable(Routes.CHAT) {
                                ChatScreen(
                                    state = state,
                                    vm = viewModel,
                                    onOpenSubscription = { nav.navigate(Routes.SUBSCRIPTION) },
                                )
                            }
                            composable(Routes.APPROVALS) { ApprovalsScreen(state, viewModel) }
                            composable(Routes.BUILDS) { BuildsScreen(state, viewModel) }
                            composable(Routes.ARTIFACTS) { ArtifactsScreen(state, viewModel) }
                            composable(Routes.INBOX) { InboxScreen(state, viewModel, nav) }
                            composable(Routes.AUDIT) { AuditScreen(state) }
                            composable(Routes.SUBSCRIPTION) {
                                SubscriptionScreen(
                                    billingState = billingState,
                                    subscriptionActive = state.isPro && !state.devForcePro,
                                    onPurchase = onPurchase,
                                    onManageSubscription = onManageSubscription,
                                    onBack = { nav.popBackStack() },
                                )
                            }
                            composable(Routes.SETTINGS) {
                                SettingsScreen(
                                    state = state,
                                    vm = viewModel,
                                    onLogout = onLogout,
                                    billingState = billingState,
                                    onManageSubscription = onManageSubscription,
                                    onOpenSubscription = { nav.navigate(Routes.SUBSCRIPTION) },
                                    onOpenPcDownload = onOpenPcDownload,
                                )
                            }
                        }
                        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
                    }
                }
            }
        }
    }
}

internal fun navigationParentRoute(
    route: String,
    hasSelectedDevice: Boolean,
    hasSelectedProject: Boolean,
): String? = when (route) {
    Routes.CHAT -> Routes.CONVERSATIONS
    Routes.CONVERSATIONS -> Routes.PROJECT_DASHBOARD
    Routes.PROJECT_FILES, Routes.BUILDS -> Routes.PROJECT_DASHBOARD
    Routes.ARTIFACTS -> Routes.CHAT
    Routes.PROJECT_DASHBOARD -> Routes.PROJECTS
    Routes.PROJECTS -> Routes.DEVICES
    Routes.INBOX, Routes.APPROVALS, Routes.AUDIT, Routes.SUBSCRIPTION, Routes.SETTINGS -> when {
        hasSelectedProject -> Routes.PROJECT_DASHBOARD
        hasSelectedDevice -> Routes.PROJECTS
        else -> Routes.DEVICES
    }
    else -> null
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun RemoteTopBar(
    nav: NavHostController,
    state: RemoteUiState,
    billingState: BillingUiState,
) {
    val selectedDeviceName = state.devices.firstOrNull { it.id == state.selectedDevice }?.name
    val selectedProjectName = state.projects.firstOrNull { it.id == state.selectedProject }?.name
    Surface(
        color = TopBarBackground,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        TopAppBar(
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    InterestellarLogo(Modifier.size(38.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        BrandWordmark(compact = true)
                        Text(
                            text = when {
                                selectedProjectName != null -> selectedProjectName
                                selectedDeviceName != null -> selectedDeviceName
                                else -> "${state.devices.size} computador(es) conectado(s)"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = NeonCyan,
            ),
            actions = {
                if (state.isPro) {
                    HeroTag("PRO", warm = true, accent = BrandGold)
                }
                val unread = state.tasks.count { it.unread }
                IconButton(onClick = { nav.navigate(Routes.INBOX) }) {
                    BadgedBox(badge = { if (unread > 0) Badge { Text(unread.toString()) } }) {
                        Icon(Icons.Default.Inbox, "Caixa de entrada")
                    }
                }
                if (state.approvals.isNotEmpty()) {
                    IconButton(onClick = { nav.navigate(Routes.APPROVALS) }) {
                        BadgedBox(badge = { Badge { Text(state.approvals.size.toString()) } }) {
                            Icon(Icons.Default.Warning, "Aprovações")
                        }
                    }
                }
                IconButton(onClick = { nav.navigate(Routes.SUBSCRIPTION) }) {
                    Icon(Icons.Default.WorkspacePremium, "Assinatura Interestellar Pro")
                }
                IconButton(onClick = { nav.navigate(Routes.SETTINGS) }) {
                    Icon(Icons.Default.Settings, "Configurações")
                }
            },
        )
    }
}

@Composable
private fun RemoteBottomBar(
    nav: NavHostController,
    currentRoute: String,
    state: RemoteUiState,
) {
    val items = listOf(
        BottomNavItem(
            route = Routes.DEVICES,
            label = "Computadores",
            icon = Icons.Default.Computer,
            selected = currentRoute == Routes.DEVICES,
        ),
        BottomNavItem(
            route = Routes.PROJECTS,
            label = "Projetos",
            icon = Icons.Default.FolderOpen,
            selected = currentRoute in setOf(Routes.PROJECTS, Routes.PROJECT_DASHBOARD, Routes.PROJECT_FILES, Routes.BUILDS),
        ),
        BottomNavItem(
            route = Routes.CHAT,
            label = "Agente",
            icon = Icons.Default.SmartToy,
            selected = currentRoute in setOf(Routes.CONVERSATIONS, Routes.CHAT, Routes.ARTIFACTS),
        ),
        BottomNavItem(
            route = Routes.INBOX,
            label = "Inbox",
            icon = Icons.Default.Inbox,
            selected = currentRoute in setOf(Routes.INBOX, Routes.APPROVALS, Routes.AUDIT),
        ),
    )

    Surface(color = TopBarBackground) {
        NavigationBar(
            containerColor = Color.Transparent,
            tonalElevation = 0.dp,
        ) {
            items.forEach { item ->
                val enabled = when (item.route) {
                    Routes.PROJECTS -> state.selectedDevice != null || state.devices.isNotEmpty()
                    Routes.CHAT -> state.selectedProject != null
                    else -> true
                }
                NavigationBarItem(
                    selected = item.selected,
                    onClick = {
                        nav.navigate(item.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    enabled = enabled,
                    icon = { Icon(item.icon, contentDescription = item.label) },
                    label = {
                        Text(
                            item.label,
                            fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                        )
                    },
                    alwaysShowLabel = true,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                    ),
                )
            }
        }
    }
}

private data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val selected: Boolean,
)
