package com.zl.qiewash

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.zl.qiewash.data.Session
import com.zl.qiewash.ui.HomeScreen
import com.zl.qiewash.ui.WeightDemoScreen
import com.zl.qiewash.ui.LocationSetupScreen
import com.zl.qiewash.ui.LoginScreen
import com.zl.qiewash.ui.OrderDetailScreen
import com.zl.qiewash.ui.OrdersScreen
import com.zl.qiewash.ui.SettingsScreen
import com.zl.qiewash.ui.PlanScreen
import com.zl.qiewash.ui.QieWashTheme
import com.zl.qiewash.ui.RunningScreen
import com.zl.qiewash.ui.ScanScreen

private object Route {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SCAN = "scan"
    const val PLAN = "plan"
    const val RUNNING = "running"
    const val ORDERS = "orders"
    const val ORDER_DETAIL = "order"
    const val SETTINGS = "settings"
    const val LOCATION = "location"
    const val WEIGHT_DEMO = "weight_demo"
}

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notify.ensureChannels(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                .launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        Diag.attach(getExternalFilesDir(null))
        Diag.log("launch v${BuildConfig.VERSION_NAME}")
        vm.bind(Session(applicationContext))
        setContent {
            QieWashTheme {
                val snackbar = remember { SnackbarHostState() }
                LaunchedEffect(vm.message) {
                    vm.message?.let {
                        snackbar.showSnackbar(it)
                        vm.dismissMessage()
                    }
                }
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // Screens own their insets (TopAppBar/Scaffold callbacks); this outer
                    // Scaffold only hosts the snackbar, so it must not inset the content
                    // again or every app bar ends up double-padded under the status bar.
                    Scaffold(
                        snackbarHost = { SnackbarHost(snackbar) },
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                    ) { pad ->
                        Box(Modifier.padding(pad).clipToBounds()) { AppNav(vm) }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppNav(vm: AppViewModel) {
    val nav = rememberNavController()
    val activity = LocalContext.current as? ComponentActivity

    // The ViewModel only requests a destination; the NavHost owns the actual stack,
    // so the system back gesture (and its predictive-back preview) works per level.
    val ctx = LocalContext.current
    // One effect owns the live notification: it keeps ticking while a run is in flight,
    // survives leaving the running screen, and only fires "done" when the clock runs out.
    LaunchedEffect(vm.activeRun?.orderNo, vm.activeRun?.endAt) {
        val run = vm.activeRun
        if (run == null) {
            Notify.cancelLive(ctx)
            Notify.cancelEnd(ctx)
            return@LaunchedEffect
        }
        while (true) {
            val left = run.endAt - System.currentTimeMillis()
            if (left <= 0) {
                // Notification is done; the pill stays red for a few minutes as a nudge.
                Notify.done(ctx, "${run.title} 已结束，记得取衣")
                Notify.cancelEnd(ctx)
                Notify.cancelLive(ctx)
                kotlinx.coroutines.delay(AppViewModel.GRACE_MS)
                vm.dropExpiredRun()
                break
            }
            Notify.live(ctx, run.title, run.endAt, left)
            Notify.scheduleEnd(ctx, run.endAt, "${run.title} 已结束，记得取衣")
            kotlinx.coroutines.delay(30_000)
        }
    }
    LaunchedEffect(vm.navTarget) {
        val target = vm.navTarget ?: return@LaunchedEffect
        vm.consumeNav()
        nav.routeTo(target)
    }

    NavHost(
        navController = nav,
        startDestination = if (vm.hasToken()) Route.HOME else Route.LOGIN,
        enterTransition = { slideInHorizontally(tween(260)) { it / 6 } + fadeIn(tween(200)) },
        exitTransition = { slideOutHorizontally(tween(260)) { -it / 12 } + fadeOut(tween(200)) },
        popEnterTransition = { slideInHorizontally(tween(260)) { -it / 12 } + fadeIn(tween(200)) },
        popExitTransition = { slideOutHorizontally(tween(260)) { it } + fadeOut(tween(200)) },
    ) {
        composable(Route.LOGIN) {
            LoginScreen(
                phone = vm.phone,
                onPhone = { vm.phone = it.filter(Char::isDigit).take(11) },
                onSend = { vm.sendCode() },
                onLogin = { vm.login(it) },
                onImport = { token, phone -> vm.importToken(token, phone) },
                busy = vm.busy,
            )
        }

        composable(Route.HOME) {
            HomeScreen(
                devices = vm.recentFiltered,
                live = vm.deviceLive,
                nearby = vm.nearbyFiltered,
                nearbyLoading = vm.nearbyLoading,
                deviceFilter = vm.deviceFilter,
                onFilter = { vm.pickDeviceFilter(it) },
                onScan = { vm.openScanner() },
                onOpen = { vm.openDevice(it) },
                onOrders = { vm.openOrders() },
                onRefreshNearby = { vm.loadNearby(force = true) },
                onSettings = { vm.openSettings() },
                active = vm.activeRun,
                onOpenActive = { vm.openActiveRun() },
                busy = vm.busy,
            )
        }

        composable(
            Route.SCAN,
            enterTransition = { fadeIn(tween(180)) },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(180)) },
            popExitTransition = { fadeOut(tween(180)) },
        ) {
            ScanScreen(
                onResult = { vm.onScanned(it) },
                onClose = { nav.popBackStack() },
            )
        }

        composable(Route.PLAN) {
            val device = vm.device
            if (device == null) {
                LaunchedEffect(Unit) { nav.routeTo(Stage.HOME) }
            } else {
                PlanScreen(
                    device = device,
                    skus = vm.skus,
                    sku = vm.sku,
                    option = vm.option,
                    price = vm.quote?.realPrice?.ifBlank { vm.option?.price },
                    originPrice = vm.quote?.originPrice,
                    payStep = vm.payStep,
                    busy = vm.busy,
                    onPick = { s, o -> vm.pick(s, o) },
                    onPay = { activity?.let { vm.pay(it) } },
                    onBack = { nav.popBackStack() },
                )
            }
        }

        composable(Route.ORDERS) {
            OrdersScreen(
                groups = vm.orderGroups,
                filter = vm.orderFilter,
                refreshing = vm.ordersRefreshing,
                loadingMore = vm.ordersLoadingMore,
                endReached = vm.ordersEnd,
                onFilter = { vm.pickOrderFilter(it) },
                onOpen = { vm.openOrderDetail(it) },
                onRefresh = { vm.refreshOrders() },
                onLoadMore = { vm.loadMoreOrders() },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Route.LOCATION) {
            LocationSetupScreen(
                initialLat = vm.lat,
                initialLng = vm.lng,
                onSave = { a, b -> vm.finishLocation(a, b) },
                onBack = if (vm.hasToken()) ({ nav.popBackStack() }) else null,
            )
        }

        composable(Route.WEIGHT_DEMO) {
            WeightDemoScreen(onBack = { nav.popBackStack() })
        }

        composable(Route.SETTINGS) {
            SettingsScreen(
                phone = vm.phone,
                token = vm.tokenValue(),
                lat = vm.lat,
                lng = vm.lng,
                spread = vm.spread,
                diagPath = vm.diagPath,
                onSavePosition = { a, b -> vm.setPosition(a, b) },
                onRelocate = { vm.reopenLocation() },
                onWeightDemo = { nav.navigate(Route.WEIGHT_DEMO) },
                onSpread = { vm.pickSpread(it) },
                onImport = { t, p -> vm.importToken(t, p) },
                onLogout = { vm.logout() },
                onBack = { nav.popBackStack() },
            )
        }

        composable(Route.ORDER_DETAIL) {
            OrderDetailScreen(detail = vm.orderDetail, onBack = { nav.popBackStack() })
        }

        composable(Route.RUNNING) {
            RunningScreen(
                device = vm.device,
                orderNo = vm.orderNo,
                endAt = vm.endAt,
                onDone = { vm.finishRun() },
            )
        }
    }
}

/** Root destinations clear the stack so back never re-enters a finished flow. */
private fun NavHostController.routeTo(target: Stage) {
    when (target) {
        Stage.LOGIN -> navigate(Route.LOGIN) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }

        Stage.HOME -> if (currentDestination?.route != Route.HOME) navigate(Route.HOME) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }

        Stage.SCAN -> navigate(Route.SCAN) { launchSingleTop = true }

        Stage.PLAN -> if (currentDestination?.route != Route.PLAN) navigate(Route.PLAN) {
            popUpTo(Route.HOME)
            launchSingleTop = true
        }

        Stage.RUNNING -> navigate(Route.RUNNING) {
            popUpTo(Route.HOME)
            launchSingleTop = true
        }

        Stage.ORDERS -> navigate(Route.ORDERS) { launchSingleTop = true }

        Stage.ORDER_DETAIL -> navigate(Route.ORDER_DETAIL) { launchSingleTop = true }

        Stage.SETTINGS -> navigate(Route.SETTINGS) { launchSingleTop = true }

        Stage.LOCATION -> navigate(Route.LOCATION) { launchSingleTop = true }
    }
}
