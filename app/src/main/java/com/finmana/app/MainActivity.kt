package com.finmana.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.finmana.app.data.AppSettings
import com.finmana.app.data.ChartPoint
import com.finmana.app.data.MoneyTransaction
import com.finmana.app.data.TransactionType
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { FinManaTheme { FinManaApp(viewModel) } }
    }
}

private enum class AppTab(val label: String, val icon: ImageVector) {
    DASHBOARD("Tổng quan", Icons.Default.BarChart),
    TRANSACTIONS("Giao dịch", Icons.Default.ReceiptLong),
    SETTINGS("Cài đặt", Icons.Default.Settings)
}

private enum class Period(val label: String) {
    DAY("Ngày"), WEEK("Tuần"), MONTH("Tháng"), QUARTER("Quý"), YEAR("Năm")
}

@Composable
private fun FinManaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color(0xFF167D5B),
            secondary = Color(0xFF2D6A91),
            surface = Color(0xFFF8FCF9)
        ),
        content = content
    )
}

@Composable
private fun FinManaApp(viewModel: MainViewModel) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val status by viewModel.status.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var tab by remember { mutableStateOf(AppTab.DASHBOARD) }

    LaunchedEffect(status) {
        status?.let {
            snackbar.showSnackbar(it)
            viewModel.clearStatus()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach {
                    NavigationBarItem(
                        selected = tab == it,
                        onClick = { tab = it },
                        icon = { Icon(it.icon, null) },
                        label = { Text(it.label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (tab) {
                AppTab.DASHBOARD -> DashboardScreen(transactions)
                AppTab.TRANSACTIONS -> TransactionsScreen(transactions, viewModel::update, viewModel::delete)
                AppTab.SETTINGS -> SettingsScreen(settings, viewModel::saveAi, viewModel::sync)
            }
        }
    }
}

@Composable
private fun DashboardScreen(transactions: List<MoneyTransaction>) {
    var period by remember { mutableStateOf(Period.MONTH) }
    val filtered = remember(transactions, period) { currentPeriod(transactions, period) }
    val income = filtered.filter { it.type == TransactionType.INCOME }.sumOf { it.amount }
    val expense = filtered.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
    val points = remember(filtered, period) { chartPoints(filtered, period) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("FinMana", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Tự động ghi nhận và hiểu dòng tiền của bạn")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Period.entries.forEach {
                    FilterChip(selected = period == it, onClick = { period = it }, label = { Text(it.label) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MetricCard("Thu", income, Color(0xFF167D5B), Modifier.weight(1f))
                MetricCard("Chi", expense, Color(0xFFC54848), Modifier.weight(1f))
            }
        }
        item { MetricCard("Dòng tiền ròng", income - expense, Color(0xFF2D6A91), Modifier.fillMaxWidth()) }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Biểu đồ thu / chi", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    MoneyChart(points)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun MetricCard(label: String, value: Long, color: Color, modifier: Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))) {
        Column(Modifier.padding(16.dp)) {
            Text(label, color = color)
            Text(formatMoney(value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MoneyChart(points: List<ChartPoint>) {
    val max = points.maxOfOrNull { maxOf(it.income, it.expense) }?.coerceAtLeast(1) ?: 1
    Column {
        Canvas(Modifier.fillMaxWidth().height(180.dp)) {
            if (points.isEmpty()) return@Canvas
            val groupWidth = size.width / points.size
            val barWidth = groupWidth * 0.28f
            points.forEachIndexed { index, point ->
                val x = index * groupWidth + groupWidth * 0.18f
                val incomeHeight = size.height * point.income / max
                val expenseHeight = size.height * point.expense / max
                drawRect(Color(0xFF167D5B), Offset(x, size.height - incomeHeight), Size(barWidth, incomeHeight))
                drawRect(Color(0xFFC54848), Offset(x + barWidth, size.height - expenseHeight), Size(barWidth, expenseHeight))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            points.take(7).forEach { Text(it.label, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun TransactionsScreen(
    transactions: List<MoneyTransaction>,
    onUpdate: (MoneyTransaction, String, String) -> Unit,
    onDelete: (MoneyTransaction) -> Unit
) {
    var selected by remember { mutableStateOf<MoneyTransaction?>(null) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { Text("Giao dịch", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        items(transactions, key = { it.id }) { transaction ->
            TransactionCard(transaction) { selected = transaction }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
    selected?.let { transaction ->
        EditTransactionDialog(
            transaction = transaction,
            onDismiss = { selected = null },
            onDelete = {
                onDelete(transaction)
                selected = null
            },
            onSave = { note, category ->
                onUpdate(transaction, note, category)
                selected = null
            }
        )
    }
}

@Composable
private fun TransactionCard(transaction: MoneyTransaction, onClick: () -> Unit) {
    val color = if (transaction.type == TransactionType.INCOME) Color(0xFF167D5B) else Color(0xFFC54848)
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(transaction.category, fontWeight = FontWeight.Bold)
                Text(transaction.note.ifBlank { transaction.sourceName })
                Text(
                    SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(transaction.occurredAt)),
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    (if (transaction.type == TransactionType.INCOME) "+ " else "- ") + formatMoney(transaction.amount),
                    color = color,
                    fontWeight = FontWeight.Bold
                )
                Text(if (transaction.synced) "Đã sync" else "Chưa sync", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun EditTransactionDialog(
    transaction: MoneyTransaction,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var note by remember { mutableStateOf(transaction.note) }
    var category by remember { mutableStateOf(transaction.category) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ghi chú giao dịch") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(category, { category = it }, label = { Text("Danh mục") })
                OutlinedTextField(note, { note = it }, label = { Text("Lý do / ghi chú") })
                Text(transaction.rawNotification, style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(note, category) }) { Text("Lưu") } },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("Xóa", color = Color(0xFFC54848)) }
                TextButton(onClick = onDismiss) { Text("Hủy") }
            }
        }
    )
}

@Composable
private fun SettingsScreen(
    settings: AppSettings,
    onSaveAi: (String, String, String) -> Unit,
    onSync: () -> Unit
) {
    val context = LocalContext.current
    var apiUrl by remember(settings.apiUrl) { mutableStateOf(settings.apiUrl) }
    var apiKey by remember(settings.apiKey) { mutableStateOf(settings.apiKey) }
    var model by remember(settings.aiModel) { mutableStateOf(settings.aiModel) }
    var signInVersion by remember { mutableIntStateOf(0) }
    val signInOptions = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/drive.file"),
                Scope("https://www.googleapis.com/auth/spreadsheets")
            )
            .build()
    }
    val googleClient = remember { GoogleSignIn.getClient(context, signInOptions) }
    val signInLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        signInVersion++
    }
    val account = remember(signInVersion) { GoogleSignIn.getLastSignedInAccount(context) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Cài đặt", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
        item {
            SettingsCard("Đọc thông báo") {
                Text("Cấp quyền để FinMana nhận biến động số dư từ ví và ngân hàng.")
                Button(onClick = {
                    context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }) { Text("Mở quyền đọc thông báo") }
            }
        }
        item {
            SettingsCard("Google Sheets") {
                Text(account?.email ?: "Chưa đăng nhập Google")
                Button(onClick = { signInLauncher.launch(googleClient.signInIntent) }) {
                    Text(if (account == null) "Đăng nhập Google" else "Đổi tài khoản")
                }
                Button(onClick = onSync, enabled = account != null) { Text("Đồng bộ ngay") }
                if (settings.spreadsheetId.isNotBlank()) {
                    Text("Sheet ID: ${settings.spreadsheetId}", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        item {
            SettingsCard("AI fallback (OpenAI-compatible)") {
                OutlinedTextField(apiUrl, { apiUrl = it }, label = { Text("API URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it }, label = { Text("Model") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    apiKey,
                    { apiKey = it },
                    label = { Text("API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("AI chỉ được gọi khi parser cục bộ không nhận dạng được thông báo.")
                Button(onClick = { onSaveAi(apiUrl, apiKey, model) }) { Text("Lưu cấu hình AI") }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

private fun formatMoney(value: Long): String =
    NumberFormat.getNumberInstance(Locale("vi", "VN")).format(value) + " đ"

private fun currentPeriod(items: List<MoneyTransaction>, period: Period): List<MoneyTransaction> {
    val now = java.time.ZonedDateTime.now()
    val start = when (period) {
        Period.DAY -> now.toLocalDate().atStartOfDay(now.zone)
        Period.WEEK -> now.minusDays((now.dayOfWeek.value - 1).toLong()).toLocalDate().atStartOfDay(now.zone)
        Period.MONTH -> now.withDayOfMonth(1).toLocalDate().atStartOfDay(now.zone)
        Period.QUARTER -> now.withMonth(((now.monthValue - 1) / 3) * 3 + 1).withDayOfMonth(1).toLocalDate().atStartOfDay(now.zone)
        Period.YEAR -> now.withDayOfYear(1).toLocalDate().atStartOfDay(now.zone)
    }
    return items.filter { it.occurredAt >= start.toInstant().toEpochMilli() }
}

private fun chartPoints(items: List<MoneyTransaction>, period: Period): List<ChartPoint> {
    val zone = ZoneId.systemDefault()
    val keyFormatter = when (period) {
        Period.DAY -> DateTimeFormatter.ofPattern("HH'h'")
        Period.WEEK, Period.MONTH -> DateTimeFormatter.ofPattern("dd/MM")
        Period.QUARTER, Period.YEAR -> DateTimeFormatter.ofPattern("MM/yy")
    }
    val grouped = items.groupBy {
        val date = Instant.ofEpochMilli(it.occurredAt).atZone(zone)
        when (period) {
            Period.DAY -> date.truncatedTo(ChronoUnit.HOURS)
            Period.WEEK, Period.MONTH -> date.toLocalDate().atStartOfDay(zone)
            Period.QUARTER, Period.YEAR -> date.withDayOfMonth(1).toLocalDate().atStartOfDay(zone)
        }
    }.toSortedMap()
    return grouped.entries.toList().takeLast(7).map { (time, values) ->
        ChartPoint(
            time.format(keyFormatter),
            values.filter { it.type == TransactionType.INCOME }.sumOf { it.amount },
            values.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amount }
        )
    }
}
