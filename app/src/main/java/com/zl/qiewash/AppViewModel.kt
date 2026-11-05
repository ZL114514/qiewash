package com.zl.qiewash

import android.app.Activity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.alipay.sdk.app.PayTask
import com.zl.qiewash.data.Channel
import com.zl.qiewash.data.Device
import com.zl.qiewash.data.Option
import com.zl.qiewash.data.Quote
import com.zl.qiewash.data.Repo
import com.zl.qiewash.data.Session
import com.zl.qiewash.data.SessionExpired
import com.zl.qiewash.data.Sku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Stage { LOGIN, LOCATION, HOME, SCAN, PLAN, RUNNING, ORDERS, ORDER_DETAIL, SETTINGS }

private const val ORDERS_PAGE = 20

class AppViewModel : ViewModel() {
    private val repo = Repo()
    private var session: Session? = null

    var stage by mutableStateOf(Stage.LOGIN)

    /** One-shot navigation request; the composable consumes it and clears it. */
    var navTarget by mutableStateOf<Stage?>(null)
    var busy by mutableStateOf(false)
    var message by mutableStateOf<String?>(null)
    var phone by mutableStateOf("")
    var devices by mutableStateOf<List<Device>>(emptyList())
    var deviceLive by mutableStateOf<Map<String, Repo.Live>>(emptyMap())
    var nearby by mutableStateOf<List<Repo.Info>>(emptyList())
    var nearbyLoading by mutableStateOf(false)
    /** "" = all, "00" = washer, "02" = dryer */
    var deviceFilter by mutableStateOf("")
    var device by mutableStateOf<Device?>(null)
    var skus by mutableStateOf<List<Sku>>(emptyList())
    var sku by mutableStateOf<Sku?>(null)
    var option by mutableStateOf<Option?>(null)
    var quote by mutableStateOf<Quote?>(null)
    var channels by mutableStateOf<List<Channel>>(emptyList())
    var payType by mutableStateOf(13)
    var orderNo by mutableStateOf("")
    var endAt by mutableStateOf(0L)

    /** The machine this account is currently using, possibly resumed after a restart. */
    var activeRun by mutableStateOf<ActiveRun?>(null)
    var payStep by mutableStateOf("")

    // orders page (manual paging + pull to refresh)
    var allOrders by mutableStateOf<List<Repo.OrderBrief>>(emptyList())
    var orderFilter by mutableStateOf("")
    var ordersRefreshing by mutableStateOf(false)
    var ordersLoadingMore by mutableStateOf(false)
    var ordersEnd by mutableStateOf(false)
    var orderDetail by mutableStateOf<org.json.JSONObject?>(null)
    private var ordersPage = 1

    /** The server's orderStatus filter does not match the app-side enum, so filter locally. */
    val orders: List<Repo.OrderBrief>
        get() = if (orderFilter.isBlank()) allOrders
        else allOrders.filter { it.status.toString() == orderFilter }

    data class OrderMonth(
        val key: String,
        val label: String,
        val count: Int,
        val paidCount: Int,
        val total: Double,
        val items: List<Repo.OrderBrief>,
    )

    /** Orders grouped by createTime month with the money actually spent that month. */
    val orderGroups: List<OrderMonth>
        get() = orders
            .groupBy { it.createTime.take(7).ifBlank { "未知" } }
            .toSortedMap(reverseOrder())
            .map { (key, list) ->
                val spent = list.filter { it.status == 2 || it.status == 3 }
                OrderMonth(
                    key = key,
                    label = key.replace("-", " 年 ").replace(" 年 ", "年") + "月",
                    count = list.size,
                    paidCount = spent.size,
                    total = spent.sumOf { it.price.toDoubleOrNull() ?: 0.0 },
                    items = list,
                )
            }

    fun openOrders() {
        go(Stage.ORDERS)
        if (allOrders.isEmpty()) refreshOrders()
    }

    fun pickOrderFilter(f: String) { orderFilter = f }

    fun pickDeviceFilter(code: String) { deviceFilter = code }

    /** Faulty machines sink to the bottom; idle ones come first. */
    private fun statusRank(l: Repo.Live?): Int = when (l?.tone) {
        Repo.Live.IDLE -> 0
        Repo.Live.BUSY -> 1
        Repo.Live.OFF -> 2
        Repo.Live.FAULT -> 3
        else -> 4
    }

    /** categoryCode 02 = dryer; good enough for wording the notification. */
    private fun dryOf(goodsId: String, functionName: String = ""): Boolean =
        devices.firstOrNull { it.goodsId == goodsId }?.categoryCode == "02" ||
            functionName.contains("烘")

    private fun machineNo(name: String): Int =
        Regex("\\d+").find(name)?.value?.toIntOrNull() ?: Int.MAX_VALUE

    val recentFiltered: List<Device>
        get() = devices
            .filter { deviceFilter.isBlank() || it.categoryCode == deviceFilter }
            .sortedWith(compareBy({ statusRank(deviceLive[it.goodsId]) }, { machineNo(it.name) }))

    val nearbyFiltered: List<Repo.Info>
        get() = nearby
            .filter { deviceFilter.isBlank() || it.device.categoryCode == deviceFilter }
            .sortedWith(compareBy({ statusRank(it.live) }, { machineNo(it.device.name) }))

    fun refreshOrders() {
        if (ordersRefreshing) return
        viewModelScope.launch {
            ordersRefreshing = true
            try {
                val first = repo.orders(page = 1, size = ORDERS_PAGE)
                allOrders = first
                ordersPage = 1
                ordersEnd = first.size < ORDERS_PAGE
                Diag.log("orders refresh n=${first.size} end=$ordersEnd")
                // The list is not strictly date-ordered, so keep paging while the newest
                // month still appears near the tail; otherwise its total is a partial sum.
                while (!ordersEnd) {
                    if (allOrders.isEmpty()) break
                    val newestMonth = allOrders.first().createTime.take(7)
                    val tail = allOrders.takeLast(5)
                    if (tail.none { it.createTime.take(7) == newestMonth }) break
                    val next = repo.orders(page = ordersPage + 1, size = ORDERS_PAGE)
                    if (next.isEmpty()) { ordersEnd = true; break }
                    allOrders = (allOrders + next).distinctBy { it.orderNo }
                    ordersPage++
                    ordersEnd = next.size < ORDERS_PAGE
                    Diag.log("orders month fill page=$ordersPage total=${allOrders.size} end=$ordersEnd")
                }
            } catch (e: SessionExpired) {
                session?.clear(); go(Stage.LOGIN)
            } catch (e: Throwable) {
                message = e.friendly()
                Diag.log("orders refresh failed: ${e.message}")
            } finally {
                ordersRefreshing = false
            }
        }
    }

    fun loadMoreOrders() {
        if (ordersRefreshing || ordersLoadingMore || ordersEnd) return
        viewModelScope.launch {
            ordersLoadingMore = true
            try {
                val next = repo.orders(page = ordersPage + 1, size = ORDERS_PAGE)
                val merged = (allOrders + next).distinctBy { it.orderNo }
                ordersEnd = next.isEmpty() || next.size < ORDERS_PAGE || merged.size == allOrders.size
                if (merged.size > allOrders.size) {
                    allOrders = merged
                    ordersPage++
                }
                Diag.log("orders more page=$ordersPage n=${next.size} total=${merged.size} end=$ordersEnd")
            } catch (e: Throwable) {
                Diag.log("orders more failed: ${e.message}")
            } finally {
                ordersLoadingMore = false
            }
        }
    }

    fun openOrderDetail(orderNo: String) {
        go(Stage.ORDER_DETAIL)
        orderDetail = null
        viewModelScope.launch {
            try {
                orderDetail = repo.orderDetailRaw(orderNo)
            } catch (e: Throwable) {
                message = e.friendly()
            }
        }
    }

    // Position used by the order risk signature; editable in settings.
    companion object {
        /** How long a finished run keeps its pill (and the "collect it" nudge). */
        const val GRACE_MS = 5 * 60_000L
    }

    data class ActiveRun(
        val orderNo: String,
        val title: String,
        val deviceName: String,
        val goodsId: String,
        val endAt: Long,
        val startAt: Long = endAt,
        val dry: Boolean = false,
    )

    var lat by mutableStateOf("")
    var lng by mutableStateOf("")
    var spread by mutableStateOf(20)

    fun bind(s: Session) {
        if (session != null) return
        session = s
        lat = s.lat
        lng = s.lng
        spread = s.spread
        if (s.token.isNotEmpty()) {
            phone = s.phone
            viewModelScope.launch {
                if (repo.verifyToken()) enterHome() else s.clear()
            }
        }
    }

    fun hasToken(): Boolean = session?.token?.isNotEmpty() == true

    fun sendCode() = run {
        val p = phone.trim()
        if (p.length != 11) { message = "请输入 11 位手机号"; return@run }
        action { repo.sendCode(p); message = "验证码已发送" }
    }

    fun login(code: String) {
        val p = phone.trim()
        if (code.isBlank()) { message = "请输入验证码"; return }
        action {
            val t = repo.login(p, code)
            session?.save(t, p)
            enterHome()
        }
    }

    /** Import an existing token (from the official app's MMKV or a previous login). */
    fun importToken(raw: String, phoneHint: String) {
        val t = raw.trim()
            .removePrefix("token=")
            .trim()
            .trim('"', '\'', ' ')
            .replace(Regex("\\s"), "")
        if (t.length < 16) { message = "凭证格式不对"; return }
        val previous = session?.token.orEmpty()
        action {
            com.zl.qiewash.data.Api.token = t
            if (!repo.verifyToken()) {
                com.zl.qiewash.data.Api.token = previous
                error("凭证无效或已过期")
            }
            session?.save(t, phoneHint.trim())
            enterHome()
        }
    }

    /** Called from the onboarding step: saves and continues to the home screen. */
    fun finishLocation(newLat: String, newLng: String) {
        if (!applyPosition(newLat, newLng)) return
        enterHome()
    }

    /** `true` when the location was accepted; the message explains validation failures. */
    fun applyPosition(newLat: String, newLng: String): Boolean {
        val la = newLat.trim().toDoubleOrNull()
        val ln = newLng.trim().toDoubleOrNull()
        if (la == null || ln == null || la !in -90.0..90.0 || ln !in -180.0..180.0) {
            message = "经纬度格式不对"
            return false
        }
        lat = "%.6f".format(la)
        lng = "%.6f".format(ln)
        session?.lat = lat
        session?.lng = lng
        return true
    }

    fun setPosition(newLat: String, newLng: String) {
        if (!applyPosition(newLat, newLng)) return
        message = "位置已更新"
    }

    fun pickSpread(n: Int) {
        spread = n.coerceIn(5, 60)
        session?.spread = spread
    }

    fun tokenValue(): String = session?.token.orEmpty()

    fun logout() {
        session?.clear()
        go(Stage.LOGIN)
        device = null
        skus = emptyList()
        quote = null
        allOrders = emptyList()
        ordersEnd = false
    }

    fun refreshDevices() = action {
        devices = repo.latestUsed()
        loadStatuses()
    }

    /** Live status for every 最近使用 card, one details call each. */
    private fun loadStatuses() {
        val ids = devices.map { it.goodsId }
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val map = mutableMapOf<String, Repo.Live>()
            ids.chunked(4).forEach { chunk ->
                chunk.forEach { id ->
                    runCatching { repo.live(id) }.getOrNull()?.let { map[id] = it }
                }
                deviceLive = map.toMap()
            }
        }
    }

    /** No device-list endpoint exists, so nearby machines are discovered by id scanning. */
    fun loadNearby(force: Boolean = false) {
        if (nearbyLoading) return
        if (!force && nearby.isNotEmpty()) return
        viewModelScope.launch {
            nearbyLoading = true
            try {
                val recent = devices.ifEmpty { runCatching { repo.latestUsed() }.getOrDefault(emptyList()) }
                devices = recent
                val found = repo.scanNearby(recent, spread)
                nearby = found
                Diag.log("nearby n=${found.size}")
            } catch (e: Throwable) {
                Diag.log("nearby failed: ${e.message}")
            } finally {
                nearbyLoading = false
            }
        }
    }

    fun openScanner() { go(Stage.SCAN) }

    fun openSettings() { go(Stage.SETTINGS) }

    fun dropExpiredRun() {
        val run = activeRun ?: return
        if (System.currentTimeMillis() > run.endAt + GRACE_MS) clearActiveRun()
    }

    fun reopenLocation() { go(Stage.LOCATION) }

    val diagPath: String get() = Diag.filePath()

    /** Same path as a scan result, for a device picked from "最近使用". */
    fun openDevice(goodsId: String) {
        action {
            val full = repo.details(goodsId)
            device = full
            skus = repo.skus(goodsId)
            check(skus.isNotEmpty()) { "该设备暂无可选时长" }
            sku = skus.first()
            option = sku!!.options.firstOrNull()
            quote = option?.let { repo.preview(goodsId, sku!!, it) }
            payStep = ""
            channels = emptyList()
            go(Stage.PLAN)
            loadChannels()
        }
    }

    fun onScanned(text: String) {
        if (stage != Stage.SCAN) return
        action {
            val info = repo.scan(text.trim())
            val full = repo.details(info.goodsId)
            device = full.copy(categoryName = info.categoryName.ifBlank { full.categoryName })
            skus = repo.skus(info.goodsId)
            check(skus.isNotEmpty()) { "该设备暂无可选时长" }
            sku = skus.first()
            option = sku!!.options.firstOrNull()
            quote = option?.let { repo.preview(info.goodsId, sku!!, it) }
            payStep = ""
            channels = emptyList()
            go(Stage.PLAN)
            loadChannels()
        }
    }

    fun pick(s: Sku, o: Option) {
        sku = s
        option = o
        quote = null
        viewModelScope.launch {
            runCatching { repo.preview(device!!.goodsId, s, o) }
                .onSuccess { quote = it }
                .onFailure { message = it.friendly() }
        }
    }

    private fun loadChannels() {
        val d = device ?: return
        val price = quote?.realPrice?.ifBlank { option?.price }.orEmpty()
        viewModelScope.launch {
            runCatching { repo.channels(d, price) }
                .onSuccess {
                    channels = it
                    payType = it.firstOrNull { c -> c.method == 13 }?.method
                        ?: it.firstOrNull()?.method ?: 13
                }
        }
    }

    /** create -> prepay -> Alipay/balance -> order status */
    fun pay(activity: Activity) {
        val d = device ?: return
        val s = sku ?: return
        val o = option ?: return
        val q = quote ?: return
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                val free = q.realPrice.toDoubleOrNull() == 0.0
                payStep = "创建订单"
                val order = repo.create(d, s, o, q, lat, lng)
                orderNo = order
                Diag.log("create ok order=$order price=${q.realPrice} free=$free")

                // The server picks the route: prepayParam -> app pay, else payTypeList (余额).
                val pp = repo.prePay(order, payType)
                Diag.log("prepay paramLen=${pp.param.length} methods=${pp.methods}")
                var usedPayType = payType
                if (pp.param.isNotBlank()) {
                    payStep = "拉起支付宝"
                    val info = pp.param
                    // payV2 blocks until the user is done: never run it on the main dispatcher.
                    val result = withContext(Dispatchers.IO) { PayTask(activity).payV2(info, true) }
                    val status = result["resultStatus"].orEmpty()
                    Diag.log("payV2 status=$status memo=${result["memo"]} result=${result["result"]}")
                    if (status != "9000") {
                        payStep = ""
                        message = when (status) {
                            "6001" -> "已取消支付"
                            "4000" -> "支付失败"
                            "5000" -> "支付结果未知，请在官方 App 核对"
                            "" -> "支付宝没有返回结果，请在官方 App 核对"
                            else -> "支付未完成（$status）"
                        }
                        return@launch
                    }
                } else {
                    val method = pp.methods.firstOrNull() ?: 2
                    payStep = "余额支付"
                    Diag.log("balancePay method=$method")
                    repo.balancePay(order, method)
                    usedPayType = method
                    Diag.log("balancePay done")
                }

                payStep = "启动设备"
                var state = repo.orderDetail(order)
                Diag.log("order status=${state.status}/${Repo.OrderStatus.text(state.status)} device_is_start=${repo.deviceStarted(order)}")
                var tries = 0
                while (tries < 15 && state.status == 0) {
                    delay(1200)
                    state = repo.orderDetail(order)
                    Diag.log("order status=${state.status}/${Repo.OrderStatus.text(state.status)}")
                    tries++
                }
                when (state.status) {
                    2, 3 -> Unit
                    1 -> error("支付超时，订单已关闭，请重新下单")
                    4, 5 -> error("订单进入退款流程（${Repo.OrderStatus.text(state.status)}），请到官方 App 查看")
                    else -> error("订单状态：${Repo.OrderStatus.text(state.status)}，请到官方 App 查看")
                }
                if (state.status == 0) error("订单仍未确认支付，请到官方 App 查看")

                val mins = if (state.minutes > 0) state.minutes else o.minutes
                endAt = System.currentTimeMillis() + mins * 60_000L
                val run = ActiveRun(
                    orderNo = order,
                    title = listOfNotNull(device?.name?.takeIf { it.isNotBlank() }, sku?.name)
                        .joinToString(" · ").ifBlank { "洗衣" },
                    deviceName = device?.name.orEmpty(),
                    goodsId = device?.goodsId.orEmpty(),
                    endAt = endAt,
                    startAt = endAt - mins * 60_000L,
                    dry = dryOf(device?.goodsId.orEmpty(), sku?.name.orEmpty()),
                )
                activeRun = run
                saveRun(run)
                payStep = ""
                go(Stage.RUNNING)
            } catch (e: SessionExpired) {
                session?.clear()
                go(Stage.LOGIN)
                message = "登录已过期，请重新登录"
            } catch (e: Throwable) {
                payStep = ""
                message = e.friendly()
                Diag.log("pay failed: ${e.message}")
            } finally {
                busy = false
            }
        }
    }

    /** Re-reads the run from the server so a restart (or a second device) shows the same banner. */
    fun resumeActiveRun() {
        viewModelScope.launch {
            val saved = session?.runEndAt ?: 0L
            val savedRun = ActiveRun(
                orderNo = session?.runOrderNo.orEmpty(),
                title = session?.runTitle.orEmpty().ifBlank { "洗衣" },
                deviceName = session?.runTitle.orEmpty().substringBefore(" · "),
                goodsId = session?.runGoodsId.orEmpty(),
                endAt = saved,
                startAt = session?.runStartAt ?: saved,
                dry = dryOf(session?.runGoodsId.orEmpty(), session?.runTitle.orEmpty()),
            )
            val briefs = runCatching { repo.orders(1, 20) }.getOrDefault(emptyList())
            val live = briefs.firstOrNull { it.status == 2 }
            if (live != null) {
                val detail = runCatching { repo.orderDetail(live.orderNo) }.getOrNull()
                val start = parseTime(detail?.payTime) ?: parseTime(live.createTime) ?: System.currentTimeMillis()
                val mins = detail?.minutes?.takeIf { it > 0 } ?: live.minutes
                val run = ActiveRun(
                    orderNo = live.orderNo,
                    title = listOfNotNull(live.machineName.takeIf { it.isNotBlank() }, live.functionName)
                        .joinToString(" · ").ifBlank { savedRun.title },
                    deviceName = live.machineName,
                    goodsId = savedRun.goodsId.takeIf { it.isNotBlank() }.orEmpty(),
                    endAt = start + mins * 60_000L,
                    startAt = start,
                    dry = dryOf(savedRun.goodsId, live.functionName),
                )
                activeRun = run
                saveRun(run)
                Diag.log("active run resumed ${run.orderNo} left=${(run.endAt - System.currentTimeMillis()) / 60000}m")
                return@launch
            }
            // Keep a just-finished run around for a few minutes: the pill turns red and
            // reminds you to collect the laundry.
            if (saved > System.currentTimeMillis() - GRACE_MS) {
                activeRun = savedRun
                Diag.log("active run from prefs left=${(saved - System.currentTimeMillis()) / 60000}m")
            } else {
                activeRun = null
                saveRun(null)
            }
        }
    }

    private fun parseTime(text: String?): Long? {
        val t = text?.takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).parse(t)?.time
        }.getOrNull()
    }

    private fun saveRun(run: ActiveRun?) {
        val s = session ?: return
        if (run == null) {
            s.runOrderNo = ""
            s.runTitle = ""
            s.runGoodsId = ""
            s.runEndAt = 0L
            s.runStartAt = 0L
        } else {
            s.runOrderNo = run.orderNo
            s.runTitle = run.title
            s.runGoodsId = run.goodsId
            s.runEndAt = run.endAt
            s.runStartAt = run.startAt
        }
    }

    /** Jump back into the banner's run; the plan screen needs the device, so re-read it. */
    fun openActiveRun() {
        val run = activeRun ?: return
        action {
            if (device?.goodsId != run.goodsId && run.goodsId.isNotBlank()) {
                device = repo.details(run.goodsId)
            }
            orderNo = run.orderNo
            endAt = run.endAt
            go(Stage.RUNNING)
        }
    }

    /** Clears the run after it ended (or was abandoned). */
    fun clearActiveRun() {
        activeRun = null
        saveRun(null)
    }

    fun finishRun() {
        clearActiveRun()
        go(Stage.HOME)
        orderNo = ""
        endAt = 0
        device = null
        skus = emptyList()
        quote = null
        refreshDevices()
        refreshOrders()
    }

    fun dismissMessage() { message = null }

    fun consumeNav() { navTarget = null }

    private fun go(s: Stage) {
        Diag.log("go $s")
        stage = s
        navTarget = s
    }

    private fun enterHome() {
        Diag.log("enterHome lat=$lat lng=$lng")
        if (lat.isBlank() || lng.isBlank()) {
            go(Stage.LOCATION)
            return
        }
        go(Stage.HOME)
        viewModelScope.launch {
            runCatching { devices = repo.latestUsed() }
            loadStatuses()
            loadNearby()
            resumeActiveRun()
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (busy) return
        viewModelScope.launch {
            busy = true
            try {
                block()
            } catch (e: SessionExpired) {
                session?.clear()
                go(Stage.LOGIN)
                message = "登录已过期，请重新登录"
            } catch (e: Throwable) {
                message = e.friendly()
            } finally {
                busy = false
            }
        }
    }

    private fun Throwable.friendly(): String =
        (message ?: "操作失败").replace(Regex("java\\.|Exception:?"), "").trim()
}
