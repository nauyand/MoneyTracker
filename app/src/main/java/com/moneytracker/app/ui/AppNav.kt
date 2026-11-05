package com.moneytracker.app.ui

import com.moneytracker.app.ui.fx.WrappedScreen
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.moneytracker.app.data.Snapshot
import com.moneytracker.app.ui.analytics.AnalyticsScreen
import com.moneytracker.app.ui.budget.BudgetScreen
import com.moneytracker.app.ui.components.LocalApp
import com.moneytracker.app.ui.dashboard.DashboardScreen
import com.moneytracker.app.ui.more.AssetsScreen
import com.moneytracker.app.ui.more.AssistantScreen
import com.moneytracker.app.ui.more.AuditScreen
import com.moneytracker.app.ui.more.BackupScreen
import com.moneytracker.app.ui.more.CategoriesScreen
import com.moneytracker.app.ui.more.DebtDetailScreen
import com.moneytracker.app.ui.more.DebtFormScreen
import com.moneytracker.app.ui.more.DebtsScreen
import com.moneytracker.app.ui.more.IncomeScreen
import com.moneytracker.app.ui.more.MoreScreen
import com.moneytracker.app.ui.more.RecurringFormScreen
import com.moneytracker.app.ui.more.ReportsScreen
import com.moneytracker.app.ui.more.SettingsScreen
import com.moneytracker.app.ui.more.SubscriptionsScreen
import com.moneytracker.app.ui.savings.GoalFormScreen
import com.moneytracker.app.ui.savings.SavingsScreen
import com.moneytracker.app.ui.transactions.ImportScreen
import com.moneytracker.app.ui.transactions.TransactionDetailScreen
import com.moneytracker.app.ui.transactions.TransactionFormScreen
import com.moneytracker.app.ui.transactions.TransactionsScreen
import com.moneytracker.app.ui.vehicles.FuelFormScreen
import com.moneytracker.app.ui.vehicles.VehicleDetailScreen
import com.moneytracker.app.ui.vehicles.VehicleFormScreen
import com.moneytracker.app.ui.vehicles.VehiclesScreen
import com.moneytracker.app.ui.wallets.WalletDetailScreen
import com.moneytracker.app.ui.wallets.WalletFormScreen
import com.moneytracker.app.ui.wallets.WalletsScreen
import com.moneytracker.app.ui.wallets.WalletOrderScreen
import com.moneytracker.app.LocalActivity
import com.moneytracker.app.work.Reminders
import androidx.compose.runtime.LaunchedEffect

data class TopDest(val route: String, val label: String, val icon: ImageVector, val onPhone: Boolean)

val topDestinations = listOf(
    TopDest(Routes.DASHBOARD, "Dashboard", Icons.Outlined.Dashboard, true),
    TopDest(Routes.TRANSACTIONS, "Transaksi", Icons.AutoMirrored.Outlined.ReceiptLong, true),
    TopDest(Routes.WALLETS, "Wallet", Icons.Outlined.AccountBalanceWallet, true),
    TopDest(Routes.ANALYTICS, "Analitik", Icons.Outlined.Insights, false),
    TopDest(Routes.VEHICLES, "BBM", Icons.Outlined.DirectionsCar, true),
    TopDest(Routes.BUDGET, "Anggaran", Icons.Outlined.PieChart, false),
    TopDest(Routes.SAVINGS, "Tabungan", Icons.Outlined.Savings, false),
    TopDest(Routes.MORE, "Lainnya", Icons.Outlined.GridView, true),
)

object Routes {
    const val DASHBOARD = "dashboard"
    const val TRANSACTIONS = "transactions"
    const val WALLETS = "wallets"
    const val ANALYTICS = "analytics"
    const val VEHICLES = "vehicles"
    const val BUDGET = "budget"
    const val SAVINGS = "savings"
    const val MORE = "more"
    const val INCOME = "income"
    const val DEBTS = "debts"
    const val SUBSCRIPTIONS = "subscriptions"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"
    const val BACKUP = "backup"
    const val ASSETS = "assets"
    const val CATEGORIES = "categories"
    const val ASSISTANT = "assistant"
    const val IMPORT = "import"
    const val AUDIT = "audit"
    const val WALLET_ORDER = "wallets/order"
    fun wrapped(month: java.time.YearMonth) = "wrapped?ym=$month"

    fun txForm(id: Long = 0, type: String = "", wallet: Long = 0, duplicate: Boolean = false) = "tx/edit?id=$id&type=$type&wallet=$wallet&dup=$duplicate"
    fun txDetail(id: Long) = "tx/detail/$id"
    fun wallet(id: Long) = "wallet/$id"
    fun walletForm(id: Long = 0, type: String = "") = "wallet/edit?id=$id&type=$type"
    fun vehicle(id: Long) = "vehicle/$id"
    fun vehicleForm(id: Long = 0) = "vehicle/edit?id=$id"
    fun fuelForm(vehicle: Long = 0, id: Long = 0) = "fuel/edit?vehicle=$vehicle&id=$id"
    fun goalForm(id: Long = 0) = "goal/edit?id=$id"
    fun debt(id: Long) = "debt/$id"
    fun debtForm(id: Long = 0) = "debt/edit?id=$id"
    fun recurringForm(id: Long = 0, kind: String = "") = "recurring/edit?id=$id&kind=$kind"
}

fun NavHostController.navigateTop(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val isTop = topDestinations.any { it.route == current }
    val showFab = current == Routes.DASHBOARD || current == Routes.TRANSACTIONS

    Row(Modifier.fillMaxSize()) {
        if (wide) {
            NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, header = {
                FloatingActionButton(onClick = { nav.navigate(Routes.txForm()) }, containerColor = MaterialTheme.colorScheme.primary) { Icon(Icons.Filled.Add, "Tambah transaksi") }
            }) {
                topDestinations.forEach { d ->
                    NavigationRailItem(selected = current == d.route, onClick = { nav.navigateTop(d.route) }, icon = { Icon(d.icon, d.label) }, label = { Text(d.label) })
                }
            }
        }
        Scaffold(
            modifier = Modifier.weight(1f),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!wide && isTop) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
                    topDestinations.filter { it.onPhone }.forEach { d ->
                        NavigationBarItem(
                            selected = current == d.route, onClick = { nav.navigateTop(d.route) },
                            icon = { Icon(d.icon, d.label) }, label = { Text(d.label, maxLines = 1) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer),
                        )
                    }
                }
            },
            floatingActionButton = {
                if (!wide && showFab) FloatingActionButton(onClick = { nav.navigate(Routes.txForm()) }, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Filled.Add, "Tambah transaksi")
                }
            },
        ) { pad ->
            Box(Modifier.padding(pad)) { AppNavHost(nav) }
        }
    }
}

@Composable
private fun AppNavHost(nav: NavHostController) {
    val app = LocalApp.current
    val snap by app.repository.snapshot.collectAsState()
    val s: Snapshot = snap ?: return
    val back: () -> Unit = { nav.popBackStack() }
    val longArg = { name: String -> navArgument(name) { type = NavType.LongType; defaultValue = 0L } }
    val strArg = { name: String -> navArgument(name) { type = NavType.StringType; defaultValue = "" } }

    NavHost(
        nav, startDestination = Routes.DASHBOARD,
        enterTransition = { fadeIn() + slideInHorizontally { it / 12 } },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { fadeOut() + slideOutHorizontally { it / 12 } },
    ) {
        composable(Routes.DASHBOARD) { DashboardScreen(s, nav) }
        composable(Routes.TRANSACTIONS) { TransactionsScreen(s, nav) }
        composable(Routes.WALLETS) { WalletsScreen(s, nav) }
        composable(Routes.ANALYTICS) { AnalyticsScreen(s, nav) }
        composable(Routes.VEHICLES) { VehiclesScreen(s, nav) }
        composable(Routes.BUDGET) { BudgetScreen(s, nav) }
        composable(Routes.SAVINGS) { SavingsScreen(s, nav) }
        composable(Routes.MORE) { MoreScreen(nav) }

        composable(
            "tx/edit?id={id}&type={type}&wallet={wallet}&dup={dup}",
            listOf(longArg("id"), strArg("type"), longArg("wallet"), navArgument("dup") { type = NavType.BoolType; defaultValue = false }),
        ) { e ->
            val a = e.arguments!!
            TransactionFormScreen(s, nav, a.getLong("id"), a.getString("type").orEmpty(), a.getLong("wallet"), a.getBoolean("dup"))
        }
        composable("tx/detail/{id}", listOf(longArg("id"))) { e -> TransactionDetailScreen(s, nav, e.arguments!!.getLong("id")) }
        composable("wallet/{id}", listOf(longArg("id"))) { e -> WalletDetailScreen(s, nav, e.arguments!!.getLong("id")) }
        composable("wallet/edit?id={id}&type={type}", listOf(longArg("id"), strArg("type"))) { e ->
            WalletFormScreen(s, nav, e.arguments!!.getLong("id"), e.arguments!!.getString("type").orEmpty())
        }
        composable("vehicle/{id}", listOf(longArg("id"))) { e -> VehicleDetailScreen(s, nav, e.arguments!!.getLong("id")) }
        composable("vehicle/edit?id={id}", listOf(longArg("id"))) { e -> VehicleFormScreen(s, nav, e.arguments!!.getLong("id")) }
        composable("fuel/edit?vehicle={vehicle}&id={id}", listOf(longArg("vehicle"), longArg("id"))) { e ->
            FuelFormScreen(s, nav, e.arguments!!.getLong("vehicle"), e.arguments!!.getLong("id"))
        }
        composable("goal/edit?id={id}", listOf(longArg("id"))) { e -> GoalFormScreen(s, nav, e.arguments!!.getLong("id")) }
        composable("debt/{id}", listOf(longArg("id"))) { e -> DebtDetailScreen(s, nav, e.arguments!!.getLong("id")) }
        composable("debt/edit?id={id}", listOf(longArg("id"))) { e -> DebtFormScreen(s, nav, e.arguments!!.getLong("id")) }
        composable("recurring/edit?id={id}&kind={kind}", listOf(longArg("id"), strArg("kind"))) { e ->
            RecurringFormScreen(s, nav, e.arguments!!.getLong("id"), e.arguments!!.getString("kind").orEmpty())
        }
        composable(Routes.INCOME) { IncomeScreen(s, nav) }
        composable(Routes.DEBTS) { DebtsScreen(s, nav) }
        composable(Routes.SUBSCRIPTIONS) { SubscriptionsScreen(s, nav) }
        composable(Routes.REPORTS) { ReportsScreen(s, back) }
        composable(Routes.SETTINGS) { SettingsScreen(s, nav) }
        composable(Routes.BACKUP) { BackupScreen(s, back) }
        composable(Routes.ASSETS) { AssetsScreen(s, back) }
        composable(Routes.CATEGORIES) { CategoriesScreen(s, back) }
        composable(Routes.ASSISTANT) { AssistantScreen(s, back) }
        composable(Routes.IMPORT) { ImportScreen(s, back) }
        composable(Routes.AUDIT) { AuditScreen(back) }
        composable(Routes.WALLET_ORDER) { WalletOrderScreen(s, nav) }
        composable("wrapped?ym={ym}", listOf(strArg("ym"))) { e ->
            val ym = runCatching { java.time.YearMonth.parse(e.arguments!!.getString("ym")) }.getOrDefault(thisMonth().minusMonths(1))
            WrappedScreen(s, ym) { nav.popBackStack() }
        }
    }

    // Buka layar sesuai notifikasi pengingat yang diketuk.
    val activity = LocalActivity.current
    val open = activity.pendingOpen.value
    LaunchedEffect(open) {
        when (open) {
            Reminders.OPEN_NEW_TX -> nav.navigate(Routes.txForm())
            Reminders.OPEN_WALLETS -> nav.navigateTop(Routes.WALLETS)
            Reminders.OPEN_WRAPPED -> nav.navigate(Routes.wrapped(thisMonth().minusMonths(1)))
        }
        if (open != null) activity.pendingOpen.value = null
    }
}
