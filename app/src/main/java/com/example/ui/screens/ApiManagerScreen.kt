package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.ai.CompanyDiscoveryEngine
import com.example.domain.model.AiCompanyConfig
import com.example.domain.model.CompanyDefinition
import com.example.domain.model.CompanyStatus
import com.example.domain.model.DiscoveredModel
import com.example.domain.model.ErrorKind
import com.example.domain.model.LoadBalancingMode
import com.example.domain.model.ModelTier
import com.example.domain.model.OffHoursMode
import com.example.domain.model.SupportedCompanies
import com.example.ui.MainViewModel
import com.example.ui.UiState
import com.example.ui.theme.AgentPurple
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.RiskAmber
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiManagerScreen(
    uiState: UiState,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    var quickPasteKey by remember { mutableStateOf("") }
    var showConnectCompaniesDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Feedback message banner
        uiState.feedbackMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BullishGreen.copy(alpha = 0.15f))
                    .border(1.dp, BullishGreen.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Dismiss",
                        color = TechCyan,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { viewModel.clearFeedback() }
                            .padding(start = 8.dp)
                    )
                }
            }
        }

        // Header: Title, AI Enrichment switch, and "Connect companies" button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(AgentPurple.copy(alpha = 0.2f))
                        .border(1.dp, AgentPurple, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Psychology,
                        contentDescription = "AI Fleet",
                        tint = AgentPurple,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "AI Fleet & Providers",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (uiState.isEngineOnly) "Engine-only mode (No keys required)" else "Connected: ${uiState.connectedCompaniesCount} companies",
                        fontSize = 10.sp,
                        color = if (uiState.isEngineOnly) TechCyan else BullishGreen
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = { showConnectCompaniesDialog = true },
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("connect_companies_btn"),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TechCyan),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = androidx.compose.ui.graphics.SolidColor(TechCyan.copy(alpha = 0.5f)))
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // AI Enrichment Mode Switch Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AI Enrichment Layer",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (uiState.aiEnrichmentEnabled) "Auto: LLMs enrich analysis; engine operates 100% if keys missing/down" else "Off: Pure on-device deterministic quantitative analysis only",
                        fontSize = 10.sp,
                        color = TextMuted
                    )
                }
                Switch(
                    checked = uiState.aiEnrichmentEnabled,
                    onCheckedChange = { viewModel.toggleAiEnrichment(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BullishGreen,
                        checkedTrackColor = BullishGreen.copy(alpha = 0.3f),
                        uncheckedThumbColor = TextMuted,
                        uncheckedTrackColor = DarkSurfaceElevated
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Quick Connect / Paste Any Key Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, TechCyan.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Key, contentDescription = null, tint = TechCyan, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Quick Connect (Paste Any Key)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Paste a key for any provider (AIza = Gemini, gsk_ = Groq, sk-or- = OpenRouter, sk-ant- = Anthropic, pplx- = Perplexity, sk- = OpenAI/DeepSeek). The app auto-detects the provider and discovers available models.",
                    fontSize = 10.sp,
                    color = TextMuted,
                    lineHeight = 14.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = quickPasteKey,
                        onValueChange = { quickPasteKey = it },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("quick_paste_key_input"),
                        placeholder = { Text("Paste any provider API key...", fontSize = 11.sp, color = TextMuted) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TechCyan,
                            unfocusedBorderColor = DarkBorder,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text ?: ""
                                    if (clip.isNotBlank()) {
                                        quickPasteKey = clip.trim()
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = TechCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (quickPasteKey.isNotBlank()) {
                                viewModel.pasteAnyKey(quickPasteKey)
                                quickPasteKey = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                        modifier = Modifier.testTag("quick_connect_submit_btn")
                    ) {
                        Text("Connect", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DarkBackground)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Strategy Selector Card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PROVIDER FLEET",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
            Text(
                text = "Auto-Cascade Router",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = AgentPurple
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // List of Provider Cards
        val companies = uiState.aiCompanies.ifEmpty {
            SupportedCompanies.ALL.map { def ->
                AiCompanyConfig(definition = def, status = CompanyStatus.NO_KEY)
            }
        }

        companies.forEach { company ->
            CompanyCard(
                company = company,
                isDiscovering = uiState.isDiscoveringCompany == company.definition.id,
                onSaveKey = { key -> viewModel.saveCompanyApiKey(company.definition.id, key) },
                onRefresh = { viewModel.refreshCompany(company.definition.id) },
                onPinModel = { modelId -> viewModel.pinModel(company.definition.id, modelId) },
                onUpdateCustomConfig = { endpoint, header -> viewModel.updateCompanyCustomConfig(company.definition.id, endpoint, header) },
                onOpenConsole = {
                    if (company.definition.getKeyUrl.isNotBlank()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(company.definition.getKeyUrl))
                        context.startActivity(intent)
                    }
                }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Settings > Session & SEC EDGAR Data Config
        var secAgentInput by remember(uiState.secUserAgent) { mutableStateOf(uiState.secUserAgent) }
        val defaultWindowsStr = remember(uiState.stockEntryWindows) {
            uiState.stockEntryWindows.joinToString(", ") {
                String.format(Locale.US, "%02d:%02d-%02d:%02d", it.startHour, it.startMinute, it.endHour, it.endMinute)
            }
        }
        var windowsInput by remember(defaultWindowsStr) { mutableStateOf(defaultWindowsStr) }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("session_settings_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, TechCyan.copy(alpha = 0.35f)),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Settings > Session & Fundamentals",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Off-hours Stock Policy (applies uniformly across Terminal & Scanner):",
                    fontSize = 10.sp,
                    color = TextMuted
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OffHoursMode.values().forEach { mode ->
                        val selected = uiState.offHoursMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) TechCyan.copy(alpha = 0.2f) else DarkSurfaceElevated)
                                .border(1.dp, if (selected) TechCyan else DarkBorder, RoundedCornerShape(6.dp))
                                .clickable { viewModel.setOffHoursMode(mode) }
                                .padding(horizontal = 6.dp, vertical = 8.dp)
                                .testTag("offhours_mode_${mode.name.lowercase(Locale.US)}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                color = if (selected) TechCyan else TextSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Text(
                    text = uiState.offHoursMode.description,
                    fontSize = 9.sp,
                    color = TextSecondary
                )

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = windowsInput,
                        onValueChange = { windowsInput = it },
                        label = { Text("Stock Entry Windows (ET)", fontSize = 9.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_stock_entry_windows"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    Button(
                        onClick = { viewModel.setStockEntryWindows(windowsInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                        modifier = Modifier.testTag("btn_save_entry_windows")
                    ) {
                        Text("Save", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DarkBackground)
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = secAgentInput,
                        onValueChange = { secAgentInput = it },
                        label = { Text("SEC User-Agent (Name email@domain.com)", fontSize = 9.sp) },
                        placeholder = { Text("MyDesk contact@domain.com", fontSize = 9.sp, color = TextMuted) },
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("input_sec_user_agent"),
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary)
                    )
                    Button(
                        onClick = { viewModel.setSecUserAgent(secAgentInput) },
                        colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                        modifier = Modifier.testTag("btn_save_sec_user_agent")
                    ) {
                        Text("Save", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = DarkBackground)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Engine & Data Diagnostics Card
        val diag = uiState.diagnosticsSnapshot
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("diagnostics_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Diagnostics (Last 200 Evaluations & Feeds)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    TextButton(onClick = { viewModel.refreshDiagnostics() }) {
                        Text("Refresh", fontSize = 10.sp, color = TechCyan, fontWeight = FontWeight.Bold)
                    }
                }
                Text(
                    text = "NO TRADE count: ${diag.noTradeCount} / ${diag.totalEvaluatedCount} • Replay blocks: ${diag.replayBlockCount} • Risk Sizer blocks: ${diag.sizerBlockCount}",
                    fontSize = 10.sp,
                    color = TextSecondary,
                    fontWeight = FontWeight.SemiBold
                )
                if (diag.noTradeReasonCounts.isNotEmpty()) {
                    diag.noTradeReasonCounts.entries.forEach { (reason, count) ->
                        Text("• $reason: $count", fontSize = 9.sp, color = RiskAmber)
                    }
                } else {
                    Text("• No NO_TRADE reasons recorded in current window.", fontSize = 9.sp, color = TextMuted)
                }

                if (diag.barFeeds.isNotEmpty()) {
                    Text("Bars Loaded & Feed Age:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    diag.barFeeds.take(6).forEach { feed ->
                        val ageSec = (feed.candleAgeMillis / 1000L).coerceAtLeast(0L)
                        val ageStr = if (ageSec >= 3600) "${ageSec / 3600}h" else if (ageSec >= 60) "${ageSec / 60}m" else "${ageSec}s"
                        Text(
                            text = "• ${feed.symbol} [${feed.timeframeLabel}]: ${feed.barsLoaded} bars loaded • age $ageStr",
                            fontSize = 9.sp,
                            color = TextSecondary
                        )
                    }
                }

                Text("Recent API Errors (last 20, keys redacted):", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                if (diag.recentApiErrors.isEmpty()) {
                    Text("• No recent API errors recorded.", fontSize = 9.sp, color = BullishGreen)
                } else {
                    diag.recentApiErrors.forEach { err ->
                        Text("• $err", fontSize = 9.sp, color = BearishRed)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // About & Open Source Licenses (Required attribution)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("about_licenses_card"),
            colors = CardDefaults.cardColors(containerColor = DarkSurface),
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            shape = RoundedCornerShape(10.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "About & Open Source Licenses",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ideas adapted from day-trader-skill (MIT).",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TechCyan
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Reproducible Monte Carlo session bootstrap replays (default Seed 19L), closed-bar execution timer, and Paper Ledger run locally on-device. No live broker orders are ever transmitted.",
                    fontSize = 10.sp,
                    color = TextMuted,
                    lineHeight = 14.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }

    // "Connect companies" Dialog
    if (showConnectCompaniesDialog) {
        ConnectCompaniesDialog(
            companies = uiState.aiCompanies,
            onDismiss = { showConnectCompaniesDialog = false },
            onOpenConsole = { url ->
                if (url.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            }
        )
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CompanyCard(
    company: AiCompanyConfig,
    isDiscovering: Boolean,
    onSaveKey: (String) -> Unit,
    onRefresh: () -> Unit,
    onPinModel: (String?) -> Unit,
    onUpdateCustomConfig: (String?, String?) -> Unit,
    onOpenConsole: () -> Unit
) {
    var keyText by remember(company.apiKey) { mutableStateOf(company.apiKey) }
    var isKeyVisible by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    var customEndpoint by remember(company.customEndpoint) { mutableStateOf(company.customEndpoint ?: "") }
    var customAuthHeader by remember(company.customAuthHeader) { mutableStateOf(company.customAuthHeader ?: "") }
    val clipboardManager = LocalClipboardManager.current

    var showDiagnosticsSheet by remember { mutableStateOf(false) }

    val borderColor = when (company.status) {
        CompanyStatus.CONNECTED -> BullishGreen.copy(alpha = 0.5f)
        CompanyStatus.NEEDS_CREDIT, CompanyStatus.NEEDS_CREDIT_SKIPPED -> RiskAmber.copy(alpha = 0.5f)
        CompanyStatus.INVALID_KEY -> BearishRed.copy(alpha = 0.5f)
        CompanyStatus.QUOTA_USED -> BearishRed.copy(alpha = 0.5f)
        CompanyStatus.TESTING -> TechCyan.copy(alpha = 0.5f)
        CompanyStatus.NO_KEY -> DarkBorder
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("company_card_${company.definition.id}"),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Company name, Status Chip, Get Key Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(AgentPurple.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = company.definition.displayName.take(1),
                            color = AgentPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = company.definition.displayName,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        if (company.status == CompanyStatus.NEEDS_CREDIT_SKIPPED || company.definition.needsCreditByDefault) {
                            Text(
                                text = if (company.status == CompanyStatus.NEEDS_CREDIT_SKIPPED) "Needs credit - skipped by router" else "Paid API (Requires balance)",
                                fontSize = 9.sp,
                                color = RiskAmber
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status Chip - Tapping opens diagnostics sheet
                    StatusChip(
                        status = if (isDiscovering) CompanyStatus.TESTING else company.status,
                        resetsInHours = company.quotaResetsInHours,
                        onClick = { showDiagnosticsSheet = true }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    // "Get Key" link
                    if (company.definition.getKeyUrl.isNotBlank()) {
                        IconButton(
                            onClick = onOpenConsole,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Get Key", tint = TechCyan, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Key Input Field Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = keyText,
                    onValueChange = { keyText = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("key_input_${company.definition.id}"),
                    placeholder = {
                        Text(
                            text = if (company.definition.id == "ollama") "No key required (Local server)" else "Enter API key...",
                            fontSize = 11.sp,
                            color = TextMuted
                        )
                    },
                    singleLine = true,
                    visualTransformation = if (isKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TechCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { isKeyVisible = !isKeyVisible }) {
                                Icon(
                                    imageVector = if (isKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Toggle key visibility",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text ?: ""
                                    if (clip.isNotBlank()) keyText = clip.trim()
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = TechCyan, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(6.dp))
                Button(
                    onClick = { onSaveKey(keyText) },
                    enabled = !isDiscovering,
                    colors = ButtonDefaults.buttonColors(containerColor = if (company.isConnected) DarkSurfaceElevated else BullishGreen),
                    modifier = Modifier.testTag("save_btn_${company.definition.id}")
                ) {
                    if (isDiscovering) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = TextPrimary, strokeWidth = 2.dp)
                    } else {
                        Text(if (company.isConnected) "Save" else "Test", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (company.isConnected) TextPrimary else DarkBackground)
                    }
                }
            }

            // Error or Status detail message
            company.statusDetail?.let { detail ->
                if (detail.isNotBlank() && company.status != CompanyStatus.CONNECTED && company.status != CompanyStatus.NO_KEY) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = detail,
                        fontSize = 10.sp,
                        color = if (company.status == CompanyStatus.NEEDS_CREDIT || company.status == CompanyStatus.NEEDS_CREDIT_SKIPPED) RiskAmber else BearishRed,
                        maxLines = 2
                    )
                }
            }

            // Expandable detected models toggle with exact Patch 2 metrics:
            // "Verified n / Listed m / Needs billing k / Unverified u"
            val totalModels = company.detectedModels.size
            val modelsSummaryText = if (totalModels > 0) {
                "Verified ${company.verifiedCount} / Listed ${company.listedCount} / Needs billing ${company.needsBillingCount} / Unverified ${company.unverifiedCount}"
            } else {
                "Detected Models (0)"
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = modelsSummaryText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (company.isConnected) BullishGreen else TextMuted
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (company.isConnected) {
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh models", tint = TextMuted, modifier = Modifier.size(14.dp))
                        }
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        tint = TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Expandable Detected Models Section (grouped by TOP, STANDARD, FAST)
            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (company.detectedModels.isEmpty()) {
                        Text(
                            text = "No models detected yet. Save a valid API key to discover models.",
                            fontSize = 10.sp,
                            color = TextMuted
                        )
                    } else {
                        val grouped = company.detectedModels.groupBy { it.tier }

                        listOf(ModelTier.TOP, ModelTier.STANDARD, ModelTier.FAST).forEach { tier ->
                            val tierModels = grouped[tier] ?: emptyList()
                            if (tierModels.isNotEmpty()) {
                                TierModelGroup(tier = tier, models = tierModels, pinnedModelId = company.pinnedModelId)
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                        }
                    }
                }
            }

            // Advanced Settings Toggle
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvanced = !showAdvanced }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, tint = TextMuted, modifier = Modifier.size(12.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Advanced (Pin a model, Custom Endpoint)", fontSize = 10.sp, color = TextMuted)
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = TextMuted,
                    modifier = Modifier.size(14.dp)
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    // Pin-a-model dropdown
                    Text("Pin Model for ${company.definition.displayName}:", fontSize = 10.sp, color = TextSecondary)
                    Spacer(modifier = Modifier.height(4.dp))
                    var pinExpanded by remember { mutableStateOf(false) }
                    val currentPin = company.pinnedModelId ?: "Auto (Router Managed)"

                    ExposedDropdownMenuBox(
                        expanded = pinExpanded,
                        onExpandedChange = { pinExpanded = it },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = currentPin,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = pinExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AgentPurple,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(
                            expanded = pinExpanded,
                            onDismissRequest = { pinExpanded = false },
                            modifier = Modifier.background(DarkSurfaceElevated)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Auto (Router Managed)", color = TechCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                onClick = {
                                    onPinModel(null)
                                    pinExpanded = false
                                }
                            )
                            company.detectedModels.filter { it.isUsable }.forEach { m ->
                                DropdownMenuItem(
                                    text = { Text("${m.modelName} [${m.tier.name}]", color = TextPrimary, fontSize = 11.sp) },
                                    onClick = {
                                        onPinModel(m.modelName)
                                        pinExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Custom endpoint field for Custom OpenAI / self-hosted
                    if (company.definition.id in listOf("custom_openai", "ollama")) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Custom Base URL:", fontSize = 10.sp, color = TextSecondary)
                        Spacer(modifier = Modifier.height(2.dp))
                        OutlinedTextField(
                            value = customEndpoint,
                            onValueChange = { customEndpoint = it },
                            placeholder = { Text(company.definition.defaultBaseUrl, fontSize = 10.sp, color = TextMuted) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = TechCyan,
                                unfocusedBorderColor = DarkBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = { onUpdateCustomConfig(customEndpoint.takeIf { it.isNotBlank() }, customAuthHeader.takeIf { it.isNotBlank() }) },
                            colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated)
                        ) {
                            Text("Update Custom URL", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }

    // Diagnostics Sheet Dialog for this company
    if (showDiagnosticsSheet) {
        CompanyDiagnosticsDialog(
            company = company,
            onDismiss = { showDiagnosticsSheet = false },
            onRetry = {
                showDiagnosticsSheet = false
                onRefresh()
            }
        )
    }
}

@Composable
fun StatusChip(
    status: CompanyStatus,
    resetsInHours: Int? = null,
    onClick: (() -> Unit)? = null
) {
    val text = when (status) {
        CompanyStatus.NO_KEY -> "No key"
        CompanyStatus.TESTING -> "Testing..."
        CompanyStatus.CONNECTED -> "Connected"
        CompanyStatus.INVALID_KEY -> "Invalid key"
        CompanyStatus.NEEDS_CREDIT -> "Needs credit"
        CompanyStatus.NEEDS_CREDIT_SKIPPED -> "Needs credit - skipped"
        CompanyStatus.QUOTA_USED -> if (resetsInHours != null) "Quota used - resets in ${resetsInHours}h" else "Quota used"
    }

    val (bg, fg) = when (status) {
        CompanyStatus.CONNECTED -> BullishGreen.copy(alpha = 0.15f) to BullishGreen
        CompanyStatus.NEEDS_CREDIT, CompanyStatus.NEEDS_CREDIT_SKIPPED -> RiskAmber.copy(alpha = 0.15f) to RiskAmber
        CompanyStatus.INVALID_KEY, CompanyStatus.QUOTA_USED -> BearishRed.copy(alpha = 0.15f) to BearishRed
        CompanyStatus.TESTING -> TechCyan.copy(alpha = 0.15f) to TechCyan
        CompanyStatus.NO_KEY -> DarkSurfaceElevated to TextMuted
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .border(0.8.dp, fg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
    ) {
        Text(text = text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = fg)
    }
}

@Composable
fun CompanyDiagnosticsDialog(
    company: AiCompanyConfig,
    onDismiss: () -> Unit,
    onRetry: () -> Unit
) {
    val diagnostics = company.lastDiagnostics
    val defaultUrl = company.customEndpoint ?: company.definition.defaultBaseUrl
    val redactedUrl = diagnostics?.redactedUrl ?: CompanyDiscoveryEngine.redactUrl(defaultUrl)
    val errorKind = diagnostics?.errorKind ?: company.lastErrorKind ?: when (company.status) {
        CompanyStatus.INVALID_KEY -> ErrorKind.INVALID_KEY
        CompanyStatus.NEEDS_CREDIT, CompanyStatus.NEEDS_CREDIT_SKIPPED -> ErrorKind.ACCOUNT_NEEDS_CREDIT
        CompanyStatus.QUOTA_USED -> ErrorKind.RATE_LIMITED
        else -> null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(TechCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = TechCyan, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${company.definition.displayName} Diagnostics",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Error Kind
                DiagnosticRow(
                    label = "Error Kind",
                    value = errorKind?.label ?: "None (Healthy)",
                    color = if (errorKind == null) BullishGreen else BearishRed
                )
                errorKind?.let {
                    Text(text = it.description, fontSize = 10.sp, color = TextMuted, modifier = Modifier.padding(bottom = 6.dp))
                }

                // 2. HTTP Code
                val httpCode = diagnostics?.httpCode ?: when (company.status) {
                    CompanyStatus.INVALID_KEY -> 401
                    CompanyStatus.NEEDS_CREDIT -> 402
                    CompanyStatus.QUOTA_USED -> 429
                    else -> 200
                }
                DiagnosticRow(label = "HTTP Status Code", value = "HTTP $httpCode", color = if (httpCode == 200) BullishGreen else RiskAmber)

                // 3. Provider Status
                DiagnosticRow(label = "Provider Status", value = company.status.label, color = Color(company.status.colorHex))

                // 4. Redacted URL
                DiagnosticRow(label = "Target Endpoint", value = redactedUrl, color = TechCyan)

                // 5. First 300 characters of provider message
                val messageExcerpt = diagnostics?.providerMessageExcerpt
                    ?: company.statusDetail
                    ?: "No provider error messages recorded."
                DiagnosticRow(label = "Provider Message (First 300 chars)", value = messageExcerpt.take(300), color = TextSecondary)

                // 6. What the router will try next
                val nextAction = diagnostics?.routerNextAction ?: when (company.status) {
                    CompanyStatus.CONNECTED -> "Active in routing pool. Model tier matched dynamically."
                    CompanyStatus.NEEDS_CREDIT_SKIPPED -> "Router skips paid models; routes to free models if available, or cascades to other companies."
                    CompanyStatus.NEEDS_CREDIT -> "Cascades to free tier or other active companies."
                    CompanyStatus.QUOTA_USED -> "Cooldown active (${company.quotaResetsInHours ?: 1}h); cascades to alternative models."
                    CompanyStatus.INVALID_KEY -> "Permanently blocked until key is updated."
                    CompanyStatus.NO_KEY -> "Engine-only rule-based quantitative analysis."
                    CompanyStatus.TESTING -> "Running candidate probes across tiers."
                }
                DiagnosticRow(label = "Router Next Action", value = nextAction, color = AgentPurple)

                // Verification breakdown metrics
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Catalog Verification Breakdown",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "Verified: ${company.verifiedCount} | Listed: ${company.listedCount} | Needs billing: ${company.needsBillingCount} | Unverified: ${company.unverifiedCount}",
                    fontSize = 10.sp,
                    color = TextMuted
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = BullishGreen)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp), tint = DarkBackground)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Retry Now", color = DarkBackground, fontWeight = FontWeight.Bold, fontSize = 11.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = TextMuted, fontSize = 11.sp)
            }
        },
        containerColor = DarkSurface,
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(text = label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
        Text(text = value, fontSize = 11.sp, color = color, lineHeight = 15.sp)
    }
}

@Composable
fun TierModelGroup(tier: ModelTier, models: List<DiscoveredModel>, pinnedModelId: String?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(tier.badgeColorHex).copy(alpha = 0.2f))
                    .border(0.5.dp, Color(tier.badgeColorHex), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            ) {
                Text(tier.name, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, color = Color(tier.badgeColorHex))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(tier.description, fontSize = 9.sp, color = TextMuted)
        }
        Spacer(modifier = Modifier.height(4.dp))
        models.forEach { model ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkSurfaceElevated.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (model.isUsable) BullishGreen else BearishRed)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = model.modelName,
                        fontSize = 11.sp,
                        color = if (model.isUsable) TextPrimary else TextMuted,
                        fontWeight = if (model.modelName == pinnedModelId) FontWeight.Bold else FontWeight.Normal
                    )
                    if (model.modelName == pinnedModelId) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("(PINNED)", fontSize = 8.sp, color = AgentPurple, fontWeight = FontWeight.Bold)
                    }
                    if (model.isFree) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(BullishGreen.copy(alpha = 0.2f))
                                .padding(horizontal = 3.dp, vertical = 0.5.dp)
                        ) {
                            Text("FREE", fontSize = 7.sp, fontWeight = FontWeight.Bold, color = BullishGreen)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (model.latencyMs > 0) {
                        Text("${model.latencyMs}ms", fontSize = 9.sp, color = TextMuted)
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("${model.callsToday} calls", fontSize = 9.sp, color = TechCyan)
                }
            }
        }
    }
}

@Composable
fun ConnectCompaniesDialog(
    companies: List<AiCompanyConfig>,
    onDismiss: () -> Unit,
    onOpenConsole: (String) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = TechCyan)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Connect Companies", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Add one API key per company. TradingAgents will automatically discover and cascade between available models.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                SupportedCompanies.ALL.forEach { def ->
                    val isConnected = companies.firstOrNull { it.definition.id == def.id }?.isConnected == true
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(def.displayName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                                    if (def.needsCreditByDefault) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("(Needs Credit)", fontSize = 9.sp, color = RiskAmber)
                                    }
                                }
                                Text(
                                    text = if (isConnected) "Connected & Discovered" else "Click to get API key",
                                    fontSize = 9.sp,
                                    color = if (isConnected) BullishGreen else TextMuted
                                )
                            }
                            if (def.getKeyUrl.isNotBlank()) {
                                TextButton(onClick = { onOpenConsole(def.getKeyUrl) }) {
                                    Text("Get Key", fontSize = 10.sp, color = TechCyan)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = TechCyan)
            ) {
                Text("Done", color = DarkBackground, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = DarkSurface
    )
}
