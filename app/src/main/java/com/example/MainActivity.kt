// NOTE: To switch the bottom navigation bar to text-only mode, set the icon slot in NavigationBarItem / NavigationRailItem to null (or omit the icon parameter).
package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppScreen
import com.example.ui.MainViewModel
import com.example.ui.adaptive.AdaptiveLayoutUtils
import com.example.ui.adaptive.LocalWindowHeightSizeClass
import com.example.ui.adaptive.LocalWindowSizeClass
import com.example.ui.adaptive.LocalWindowWidthSizeClass
import com.example.ui.screens.ApiManagerScreen
import com.example.ui.screens.CryptoScannerScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.SavedReportsScreen
import com.example.ui.theme.AgentPurple
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.parseColor("#111622"))
        )
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            CompositionLocalProvider(
                LocalWindowSizeClass provides windowSizeClass,
                LocalWindowWidthSizeClass provides windowSizeClass.widthSizeClass,
                LocalWindowHeightSizeClass provides windowSizeClass.heightSizeClass
            ) {
                MyApplicationTheme {
                    TradingAgentsApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TradingAgentsApp(viewModel: MainViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val isExpanded = AdaptiveLayoutUtils.isExpanded

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedback()
        }
    }

    if (uiState.currentScreen != AppScreen.TERMINAL) {
        BackHandler {
            viewModel.setScreen(AppScreen.TERMINAL)
        }
    }

    if (isExpanded) {
        // Tablet / Large Screen Layout with Navigation Rail
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBackground)
        ) {
            NavigationRail(
                containerColor = DarkSurface,
                modifier = Modifier
                    .fillMaxHeight()
                    .border(width = 0.5.dp, color = DarkBorder),
                header = {
                    Box(
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(AgentPurple.copy(alpha = 0.2f))
                            .border(1.dp, AgentPurple, RoundedCornerShape(9.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = null,
                            tint = AgentPurple,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            ) {
                val isTerminalSelected = uiState.currentScreen in listOf(AppScreen.TERMINAL, AppScreen.WAR_ROOM, AppScreen.SCANNER, AppScreen.BACKTEST)
                NavigationRailItem(
                    selected = isTerminalSelected,
                    onClick = { viewModel.setScreen(AppScreen.TERMINAL) },
                    icon = { Icon(if (isTerminalSelected) Icons.Filled.Terminal else Icons.Outlined.Terminal, contentDescription = "Terminal") },
                    label = { Text("Terminal", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = BullishGreen,
                        selectedTextColor = BullishGreen,
                        indicatorColor = BullishGreen.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_one_query_rail")
                )

                val isFavoritesSelected = uiState.currentScreen == AppScreen.FAVORITES
                NavigationRailItem(
                    selected = isFavoritesSelected,
                    onClick = { viewModel.setScreen(AppScreen.FAVORITES) },
                    icon = { Icon(if (isFavoritesSelected) Icons.Filled.Star else Icons.Outlined.StarOutline, contentDescription = "Favorites") },
                    label = { Text("Favorites", fontSize = 10.sp) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = RiskAmber,
                        selectedTextColor = RiskAmber,
                        indicatorColor = RiskAmber.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_favorites_rail")
                )

                val isAiFleetSelected = uiState.currentScreen == AppScreen.AI_FLEET
                NavigationRailItem(
                    selected = isAiFleetSelected,
                    onClick = { viewModel.setScreen(AppScreen.AI_FLEET) },
                    icon = { Icon(if (isAiFleetSelected) Icons.Filled.Hub else Icons.Outlined.Hub, contentDescription = "AI Fleet") },
                    label = { Text("AI Fleet", fontSize = 10.sp) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = AgentPurple,
                        selectedTextColor = AgentPurple,
                        indicatorColor = AgentPurple.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_ai_fleet_rail")
                )

                val isSavedSelected = uiState.currentScreen == AppScreen.SAVED
                NavigationRailItem(
                    selected = isSavedSelected,
                    onClick = { viewModel.setScreen(AppScreen.SAVED) },
                    icon = { Icon(if (isSavedSelected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = "Saved") },
                    label = { Text("Saved", fontSize = 10.sp) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = TechCyan,
                        selectedTextColor = TechCyan,
                        indicatorColor = TechCyan.copy(alpha = 0.15f),
                        unselectedIconColor = TextSecondary,
                        unselectedTextColor = TextMuted
                    ),
                    modifier = Modifier.testTag("nav_saved_rail")
                )
            }

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = DarkBackground,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TradingAgentsTopBar(uiState, viewModel)
                }
            ) { innerPadding ->
                Box(modifier = Modifier.padding(innerPadding)) {
                    AppScreenContent(uiState, viewModel)
                }
            }
        }
    } else {
        // Phone Layout (Compact and Medium) with Bottom Navigation Bar
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TradingAgentsTopBar(uiState, viewModel)
            },
            bottomBar = {
                NavigationBar(
                    containerColor = DarkSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.border(width = 0.5.dp, color = DarkBorder)
                ) {
                    val isTerminalSelected = uiState.currentScreen in listOf(AppScreen.TERMINAL, AppScreen.WAR_ROOM, AppScreen.SCANNER, AppScreen.BACKTEST)
                    NavigationBarItem(
                        selected = isTerminalSelected,
                        onClick = { viewModel.setScreen(AppScreen.TERMINAL) },
                        icon = { Icon(if (isTerminalSelected) Icons.Filled.Terminal else Icons.Outlined.Terminal, contentDescription = "Terminal") },
                        label = { Text("Terminal", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = BullishGreen,
                            selectedTextColor = BullishGreen,
                            indicatorColor = BullishGreen.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_one_query")
                    )

                    val isFavoritesSelected = uiState.currentScreen == AppScreen.FAVORITES
                    NavigationBarItem(
                        selected = isFavoritesSelected,
                        onClick = { viewModel.setScreen(AppScreen.FAVORITES) },
                        icon = { Icon(if (isFavoritesSelected) Icons.Filled.Star else Icons.Outlined.StarOutline, contentDescription = "Favorites") },
                        label = { Text("Favorites", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = RiskAmber,
                            selectedTextColor = RiskAmber,
                            indicatorColor = RiskAmber.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_favorites")
                    )

                    val isAiFleetSelected = uiState.currentScreen == AppScreen.AI_FLEET
                    NavigationBarItem(
                        selected = isAiFleetSelected,
                        onClick = { viewModel.setScreen(AppScreen.AI_FLEET) },
                        icon = { Icon(if (isAiFleetSelected) Icons.Filled.Hub else Icons.Outlined.Hub, contentDescription = "AI Fleet") },
                        label = { Text("AI Fleet", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AgentPurple,
                            selectedTextColor = AgentPurple,
                            indicatorColor = AgentPurple.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_ai_fleet")
                    )

                    val isSavedSelected = uiState.currentScreen == AppScreen.SAVED
                    NavigationBarItem(
                        selected = isSavedSelected,
                        onClick = { viewModel.setScreen(AppScreen.SAVED) },
                        icon = { Icon(if (isSavedSelected) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, contentDescription = "Saved") },
                        label = { Text("Saved", fontSize = 11.sp) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = TechCyan,
                            selectedTextColor = TechCyan,
                            indicatorColor = TechCyan.copy(alpha = 0.15f),
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextMuted
                        ),
                        modifier = Modifier.testTag("nav_saved")
                    )
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                AppScreenContent(uiState, viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TradingAgentsTopBar(uiState: com.example.ui.UiState, viewModel: MainViewModel) {
    CenterAlignedTopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(AgentPurple.copy(alpha = 0.2f))
                        .border(1.dp, AgentPurple, RoundedCornerShape(6.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = null,
                        tint = AgentPurple,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "TradingAgents",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(BullishGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "TAURIC AI",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = BullishGreen,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                // Section 4 Header chip: "Engine-only" or "Engine + AI (n companies)"
                val chipText = if (uiState.isEngineOnly) {
                    "Engine-only"
                } else {
                    val count = uiState.connectedCompaniesCount
                    "Engine + AI ($count ${if (count == 1) "company" else "companies"})"
                }
                val chipBg = if (uiState.isEngineOnly) DarkSurfaceElevated else AgentPurple.copy(alpha = 0.2f)
                val chipBorder = if (uiState.isEngineOnly) DarkBorder else AgentPurple.copy(alpha = 0.5f)
                val chipColor = if (uiState.isEngineOnly) TextMuted else AgentPurple

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(chipBg)
                        .border(0.8.dp, chipBorder, RoundedCornerShape(6.dp))
                        .clickable {
                            viewModel.toggleAiEnrichment(!uiState.aiEnrichmentEnabled)
                        }
                        .padding(horizontal = 6.dp, vertical = 2.5.dp)
                ) {
                    Text(
                        text = chipText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = chipColor,
                        maxLines = 1
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = DarkSurface
        )
    )
}

@Composable
private fun AppScreenContent(uiState: com.example.ui.UiState, viewModel: MainViewModel) {
    when (uiState.currentScreen) {
        AppScreen.TERMINAL, AppScreen.WAR_ROOM, AppScreen.BACKTEST -> DashboardScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.SCANNER -> CryptoScannerScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.FAVORITES -> com.example.ui.screens.FavoritesScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.AI_FLEET -> ApiManagerScreen(uiState = uiState, viewModel = viewModel)
        AppScreen.SAVED -> SavedReportsScreen(uiState = uiState, viewModel = viewModel)
    }
}
