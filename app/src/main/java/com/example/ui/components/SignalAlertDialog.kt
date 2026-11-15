package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.db.TradingSignal
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceElevated
import com.example.ui.theme.TechCyan
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun SignalAlertDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    symbol: String,
    currentPrice: Double,
    alertsEnabled: Boolean,
    onToggleAlerts: () -> Unit,
    customThreshold: Double?,
    onSetCustomThreshold: (Double) -> Unit,
    onSendTestAlert: () -> Unit,
    recentAlertsLog: List<String>,
    armedSignals: List<TradingSignal>,
    hasNotificationPermission: Boolean,
    onRequestPermission: () -> Unit
) {
    if (!isOpen) return

    var inputPrice by remember { mutableStateOf(if (customThreshold != null && customThreshold > 0.0) customThreshold.toString() else "") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DarkBackground)
                .border(1.dp, TechCyan.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("signal_alert_dialog")
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(TechCyan.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = TechCyan,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "PUSH NOTIFICATIONS & ALERTS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "TradingSignal & Price Threshold Engine",
                            fontSize = 9.sp,
                            color = TextMuted
                        )
                    }
                }

                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextMuted, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Permission Warning if missing
            if (!hasNotificationPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BearishRed.copy(alpha = 0.15f))
                        .border(1.dp, BearishRed.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .clickable { onRequestPermission() }
                        .padding(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = BearishRed, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Notification permission not granted. Tap to grant permission.",
                            fontSize = 11.sp,
                            color = BearishRed,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Alerts Global Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceElevated)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Real-Time Signal Alerts",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = if (alertsEnabled) "Trigger push alert on buy/target/stop breach" else "All alerts muted",
                        fontSize = 10.sp,
                        color = if (alertsEnabled) BullishGreen else TextMuted
                    )
                }

                Switch(
                    checked = alertsEnabled,
                    onCheckedChange = { onToggleAlerts() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = TechCyan,
                        checkedTrackColor = TechCyan.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier.testTag("switch_signal_alerts")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Set Custom Price Threshold for Symbol
            Text(
                text = "Set Price Alert for $symbol (Current: ${com.example.domain.util.PriceFormatter.formatCurrency(currentPrice)})",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = inputPrice,
                    onValueChange = { inputPrice = it },
                    placeholder = { Text("e.g. 68500", fontSize = 11.sp, color = TextMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val price = inputPrice.toDoubleOrNull()
                            if (price != null && price > 0.0) onSetCustomThreshold(price)
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TechCyan,
                        unfocusedBorderColor = DarkBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = TechCyan
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val price = inputPrice.toDoubleOrNull()
                        if (price != null && price > 0.0) onSetCustomThreshold(price)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TechCyan),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Arm Alert", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }

            if (customThreshold != null && customThreshold > 0.0) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "✓ Armed for $symbol at ${com.example.domain.util.PriceFormatter.formatCurrency(customThreshold)}",
                    fontSize = 10.sp,
                    color = TechCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Test Push Notification Button
            Button(
                onClick = onSendTestAlert,
                colors = ButtonDefaults.buttonColors(containerColor = DarkSurfaceElevated),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, TechCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .testTag("btn_send_test_push")
            ) {
                Icon(Icons.Default.Send, contentDescription = null, tint = TechCyan, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Test Push Notification Now",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TechCyan
                )
            }

            // Recent Alerts Log
            if (recentAlertsLog.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Recent Trigger Log:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextMuted)
                Spacer(modifier = Modifier.height(4.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(DarkSurfaceElevated)
                        .padding(8.dp)
                ) {
                    recentAlertsLog.take(4).forEach { log ->
                        Text(log, fontSize = 9.sp, color = TextSecondary, lineHeight = 13.sp)
                    }
                }
            }
        }
    }
}

private fun formatPrice(price: Double): String {
    return com.example.domain.util.PriceFormatter.formatPrice(price)
}
