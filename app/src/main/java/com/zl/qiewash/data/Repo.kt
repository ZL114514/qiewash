package com.zl.qiewash.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Device(
    val goodsId: String,
    val name: String,
    val categoryCode: String,
    val categoryName: String,
    val shopId: String,
    val orgId: String = "",
    val imei: String = "",
    val bluetooth: Boolean = false,
)

/** One selectable duration for a sku: minutes + list price + hint. */
data class Option(val minutes: Int, val price: String, val desc: String)

/**
 * byMinutes = true  : sku 是「温度/模式」，价格随分钟数阶梯（extAttr.perPrice），下单 amount=分钟
 * byMinutes = false : sku 就是整个洗涤程序，amount 固定 1（洗衣机）
 */
data class Sku(
    val skuId: String,
    val name: String,
    val options: List<Option>,
    val byMinutes: Boolean = true,
    val feature: String = "",
)

data class Quote(val originPrice: String, val realPrice: String, val promotionsJson: String)

data class Channel(val method: Int, val name: String, val preferred: Boolean)

class Repo {
    // ---- auth -------------------------------------------------------------

    suspend fun sendCode(phone: String) = io {
        val r = Api.post("common/sms/sendCode", listOf("phone" to phone, "template" to "reg"))
        check(r.optInt("code", -1) == 0) { r.optString("msg", "发送失败") }
    }

    suspend fun login(phone: String, code: String): String = io {
        val r = Api.post("user/reg", listOf("channel" to "android_app", "phone" to phone, "verify" to code))
        val t = r.optJSONObject("data")?.optString("token").orEmpty()
        check(r.optInt("code", -1) == 0 && t.isNotEmpty()) { r.optString("msg", "登录失败") }
        t
    }

    suspend fun verifyToken(): Boolean = io {
        runCatching { Api.post("user/info", emptyList()).optInt("code", -1) == 0 }.getOrDefault(false)
    }

    // ---- device -----------------------------------------------------------

    suspend fun latestUsed(): List<Device> = io {
        val r = Api.post("goods/latestUsed", listOf("categoryCode" to "5"))
        val arr = r.optJSONArray("data") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Device(
                goodsId = o.optString("goodsId"),
                name = o.optString("goodsName"),
                categoryCode = o.optString("categoryCode"),
                categoryName = categoryName(o.optString("orgName"), o.optString("categoryCode")),
                shopId = o.optString("shopId"),
                orgId = o.optString("orgId"),
            )
        }
    }

    /** Live state of a machine, read from goods/normal/details. */
    data class Live(val label: String, val tone: Int) {
        companion object { const val IDLE = 0; const val BUSY = 1; const val FAULT = 2; const val OFF = 3 }
    }

    /** Device + its live state from a single details call. */
    data class Info(val device: Device, val live: Live)

    suspend fun info(goodsId: String): Info = io { infoOf(goodsId) }

    suspend fun live(goodsId: String): Live = info(goodsId).live

    private suspend fun infoOf(goodsId: String): Info {
        val r = Api.post("goods/normal/details", listOf("goodsId" to goodsId))
        val d = r.optJSONObject("data") ?: error(r.optString("msg", "设备不存在"))
        check(r.optInt("code", -1) == 0) { r.optString("msg", "设备不存在") }
        return Info(parseDevice(d), parseLive(d))
    }

    private fun parseDevice(d: JSONObject) = Device(
        goodsId = d.optString("goodsId"),
        name = d.optString("name"),
        categoryCode = d.optString("categoryCode"),
        categoryName = categoryName(d.optString("orgName"), d.optString("categoryCode")),
        shopId = d.optString("shopId").ifBlank { d.optString("orgId") },
        orgId = d.optString("orgId"),
        imei = d.optString("imei"),
        bluetooth = d.optInt("isBluetooth", 0) == 1,
    )

    /** optString() returns the literal "null" for JSON null values, so normalise it away. */
    private fun JSONObject.t(key: String): String {
        if (isNull(key)) return ""
        val v = optString(key).trim()
        return if (v.equals("null", true)) "" else v
    }

    // deviceErrorCode is not just "broken": 2 = working, 7 = reserved, the rest are faults
    // (mapping taken from the official H5 bundle's handleMachineError).
    private val faultCodes = mapOf(4 to 4107, 8 to 4103, 9 to 4110, 10 to 4201, 12 to 4204, 13 to 4202)

    private fun parseLive(d: JSONObject): Live {
        val code = d.t("deviceErrorCode").toIntOrNull() ?: 0
        val remain = d.optInt("remainTime", 0)
        val sold = d.optInt("soldState", -1)
        return when {
            code == 2 || code == 21 -> Live(
                if (remain > 0) "使用中 · 剩 $remain 分" else "使用中", Live.BUSY
            )
            code == 7 -> Live("已预约", Live.BUSY)
            code != 0 -> Live("故障" + (faultCodes[code]?.let { " $it" } ?: ""), Live.FAULT)
            sold == 2 -> Live("停用", Live.OFF)
            sold == 1 -> Live("空闲", Live.IDLE)
            else -> Live("停用", Live.OFF)
        }
    }

    suspend fun details(goodsId: String): Device = info(goodsId).device

    /**
     * There is no shop device-list endpoint, but goodsId is globally sequential, so walk a
     * window around the known machines and keep the ones from the same orgs.
     */
    suspend fun scanNearby(recent: List<Device>, spread: Int = 20): List<Info> = io {
        val ids = recent.mapNotNull { it.goodsId.toIntOrNull() }
        val orgs = recent.map { it.orgId }.filter { it.isNotBlank() }.toSet()
        if (ids.isEmpty() || orgs.isEmpty()) return@io emptyList()
        val targets = ids.flatMap { id -> (id - spread..id + spread) }
            .filter { it > 0 }.distinct() - ids.toSet()
        val found = mutableListOf<Info>()
        targets.chunked(8).forEach { chunk ->
            val batch = coroutineScope {
                chunk.map { id -> async { runCatching { infoOf(id.toString()) }.getOrNull() } }.awaitAll()
            }
            batch.filterNotNull().filter { it.device.orgId in orgs && it.device.name.isNotBlank() }
                .forEach { found += it }
        }
        found.distinctBy { it.device.goodsId }.sortedBy { it.device.name }
    }

    /**
     * Machine QRs are a URL with NQT/IMEI/SN in the query string, not a bare code,
     * so parse the parameter first and only fall back to the raw text.
     */
    suspend fun scan(raw: String): Device = io {
        val text = raw.trim()
        var lastMsg = "二维码无法识别"
        for ((key, value) in scanCandidates(text)) {
            val r = Api.post("goods/scan/v2", listOf(key to value))
            val d = r.optJSONObject("data")
            if (r.optInt("code", -1) == 0 && d != null) {
                return@io Device(
                    goodsId = d.optString("id"),
                    name = "",
                    categoryCode = d.optString("categoryCode"),
                    categoryName = d.optString("categoryName"),
                    shopId = d.optString("shopId"),
                )
            }
            lastMsg = r.optString("msg").ifBlank { lastMsg }
        }
        error("$lastMsg（内容：${text.take(80)}）")
    }

    private fun scanCandidates(text: String): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        val uri = runCatching { android.net.Uri.parse(text) }.getOrNull()
        uri?.queryParameterNames?.forEach { name ->
            val upper = name.uppercase()
            if (upper in setOf("NQT", "IMEI", "SN")) {
                uri.getQueryParameter(name)?.takeIf { it.isNotBlank() }?.let { out += upper to it }
            }
        }
        if (out.isEmpty()) {
            out += "NQT" to text
            out += "SN" to text
        }
        return out
    }

    suspend fun skus(goodsId: String): List<Sku> = io {
        val r = Api.post("goods/normal/skus", listOf("goodsId" to goodsId))
        val arr = r.optJSONArray("data") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ea = runCatching { JSONObject(o.optString("extAttr")) }.getOrNull()
            val per = ea?.optJSONArray("perPrice") ?: JSONArray()
            val options = (0 until per.length()).mapNotNull { j ->
                val p = per.getJSONObject(j)
                val minutes = p.optString("time").toIntOrNull() ?: return@mapNotNull null
                Option(minutes, p.optString("price"), p.optString("desc"))
            }
            val feature = o.optString("feature")
            val single = if (options.isEmpty()) {
                val mins = o.optString("unit").toIntOrNull() ?: 0
                val price = o.optString("vipDiscountPrice")
                    .ifBlank { o.optString("discountPrice") }
                    .ifBlank { o.optString("price") }
                if (mins > 0) listOf(Option(mins, price, feature)) else emptyList()
            } else emptyList()
            Sku(
                skuId = o.optString("skuId"),
                name = o.optString("name"),
                options = if (options.isNotEmpty()) options else single,
                byMinutes = options.isNotEmpty(),
                feature = feature,
            )
        }.filter { it.options.isNotEmpty() }
    }

    // ---- order ------------------------------------------------------------

    private fun itemsJson(goodsId: String, skuId: String, amount: String) =
        """[{"goodsId":"$goodsId","skuId":"$skuId","amount":"$amount","num":"$amount","soldType":"1"}]"""

    /** Dryer skus sell minutes (amount=minutes); washer skus sell one whole program (amount=1). */
    private fun amountOf(sku: Sku, o: Option): String = if (sku.byMinutes) o.minutes.toString() else "1"

    suspend fun preview(goodsId: String, sku: Sku, o: Option): Quote = io {
        val r = Api.post(
            "trade/preview/v4",
            listOf(
                "items" to itemsJson(goodsId, sku.skuId, amountOf(sku, o)),
                "promotions" to "[]",
                "isAutoSelectPromotion" to "1"
            )
        )
        val d = r.optJSONObject("data") ?: error(r.optString("msg", "试算失败"))
        val promos = JSONArray()
        val list = d.optJSONArray("promotions")
        if (list != null) {
            for (i in 0 until list.length()) {
                val participate = list.getJSONObject(i).optJSONArray("participateList") ?: continue
                for (j in 0 until participate.length()) {
                    val p = participate.getJSONObject(j)
                    if (!p.optBoolean("isUsed", true)) continue
                    promos.put(
                        JSONObject()
                            .put("assetId", p.optString("assetId", "0"))
                            .put("oldPromotionId", p.optString("oldPromotionId"))
                            .put("orgId", p.optString("orgId"))
                            .put("promotionId", p.optString("promotionId", "0"))
                            .put("promotionType", p.optString("promotionType"))
                    )
                }
            }
        }
        Quote(
            originPrice = d.optString("originPrice"),
            realPrice = d.optString("realPrice"),
            promotionsJson = promos.toString()
        )
    }

    suspend fun channels(device: Device, payPrice: String): List<Channel> = io {
        val r = Api.post(
            "payChannelRoute/prePay",
            listOf(
                "imei" to device.imei,
                "goodsId" to device.goodsId,
                "hitAd" to "0",
                "payPrice" to payPrice,
                "useTicket" to "0",
                "shopId" to device.orgId.ifBlank { device.shopId },
                "combineOrderStatus" to "false"
            )
        )
        val arr = r.optJSONArray("data") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Channel(o.optInt("method"), o.optString("methodName"), o.optBoolean("defaultFlag"))
        }
    }

    suspend fun deviceHealthy(goodsId: String): Boolean = io {
        val r = Api.post("trade/check_device_status", listOf("goodsId" to goodsId))
        r.optJSONObject("data")?.optBoolean("health", true) ?: true
    }

    suspend fun create(
        device: Device,
        sku: Sku,
        o: Option,
        quote: Quote,
        lat: String,
        lng: String,
    ): String = io {
        val r = Api.post(
            "trade/create",
            listOf(
                "promotions" to quote.promotionsJson.ifBlank { "[]" },
                "items" to itemsJson(device.goodsId, sku.skuId, amountOf(sku, o))
            ),
            Api.riskHeaders("/trade/create", device.imei, device.categoryCode, lat, lng)
        )
        val d = r.optJSONObject("data") ?: error(r.optString("msg", "下单失败"))
        check(r.optInt("code", -1) == 0) { r.optString("msg", "下单失败") }
        // trade/create returns tradeNo/orderId (there is no "orderNo" field).
        d.optString("tradeNo").ifBlank { d.optString("orderId") }
            .ifBlank { error("下单返回里没有单号：$d") }
    }

    /** prepayParam empty means the order cannot go through a 3rd-party app (e.g. ¥0 → 余额). */
    data class PrePay(val param: String, val methods: List<Int>)

    suspend fun prePay(orderNo: String, payType: Int): PrePay = io {
        val r = Api.post("pay/unify/prePay", listOf("orderNo" to orderNo, "payType" to payType.toString()))
        check(r.optInt("code", -1) == 0) { r.optString("msg", "预支付失败") }
        val d = r.optJSONObject("data")
        val list = d?.optJSONArray("payTypeList")
        PrePay(
            param = d?.optString("prepayParam").orEmpty(),
            methods = (0 until (list?.length() ?: 0)).map { list!!.optInt(it) },
        )
    }

    /** Balance payment (method 2); the only route the server offers for ¥0 orders. */
    suspend fun balancePay(orderNo: String, payType: Int = 2) = io {
        val r = Api.post("pay/unify/pay", listOf("orderNo" to orderNo, "payType" to payType.toString()))
        check(r.optInt("code", -1) == 0) { r.optString("msg", "支付失败") }
    }

    suspend fun payStatus(orderNo: String, payType: Int): Boolean = io {
        val r = Api.post("order/pay_status", listOf("orderNo" to orderNo, "payType" to payType.toString()))
        r.optBoolean("data", false)
    }

    /** Some categories need an explicit start; dryers boot server-side on payment. */
    suspend fun startDevice(orderNo: String): Boolean = io {
        runCatching { Api.post("trade/order/start", listOf("orderNo" to orderNo)).optBoolean("data", true) }
            .getOrDefault(false)
    }

    data class OrderBrief(
        val orderNo: String,
        val status: Int,
        val createTime: String,
        val price: String,
        val minutes: Int,
        val machineName: String,
        val functionName: String,
        val shopName: String,
    )

    /** orderStatus filters server-side; status/type/orderType/pageNum are ignored by the API. */
    suspend fun orders(page: Int = 1, size: Int = 30, statusFilter: String = ""): List<OrderBrief> = io {
        val params = mutableListOf("page" to page.toString(), "pageSize" to size.toString())
        if (statusFilter.isNotBlank()) params += "orderStatus" to statusFilter
        val r = Api.post("order/list", params)
        check(r.optInt("code", -1) == 0) { r.optString("msg", "订单列表读取失败") }
        val arr = r.optJSONObject("data")?.optJSONArray("items") ?: JSONArray()
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            OrderBrief(
                orderNo = o.optString("orderNo"),
                status = o.optInt("orderStatus", -1),
                createTime = o.optString("createTime"),
                price = o.optString("payPrice").ifBlank { o.optString("markPrice") },
                minutes = o.optInt("markMinutes", 0),
                machineName = o.optString("machineName"),
                functionName = o.optString("machineFunctionName"),
                shopName = o.optString("shopName"),
            )
        }
    }

    suspend fun orderDetailRaw(orderNo: String): JSONObject = io {
        val r = Api.post("order/detail", listOf("orderNo" to orderNo))
        r.optJSONObject("data") ?: error(r.optString("msg", "查不到该订单"))
    }

    /** orderStatus enum copied from the official H5 bundle. */
    object OrderStatus {
        fun text(s: Int): String = when (s) {
            0 -> "待支付"
            1 -> "已失效"
            2 -> "使用中"
            3 -> "已完成"
            4 -> "退款中"
            5 -> "已退款"
            else -> "未知($s)"
        }
    }

    /** Order truth: 0 待支付, 1 已失效, 2 使用中, 3 已完成, 4 退款中, 5 已退款. */
    data class OrderState(val status: Int, val payTime: String, val minutes: Int, val payPrice: String)

    suspend fun orderDetail(orderNo: String): OrderState = io {
        val r = Api.post("order/detail", listOf("orderNo" to orderNo))
        val d = r.optJSONObject("data") ?: error(r.optString("msg", "查不到订单"))
        OrderState(
            status = d.optInt("orderStatus", -1),
            payTime = d.t("payTime"),
            minutes = d.optInt("markMinutes", 0),
            payPrice = d.t("payPrice"),
        )
    }

    suspend fun deviceStarted(orderNo: String): Boolean = io {
        runCatching { Api.post("machine/device_is_start", listOf("orderNo" to orderNo)).optBoolean("data", false) }
            .getOrDefault(false)
    }

    private fun categoryName(org: String, code: String): String = when {
        org.contains("烘干") -> "烘干机"
        org.contains("洗衣") -> "洗衣机"
        code == "02" -> "烘干机"
        code == "00" -> "洗衣机"
        code == "03" -> "吹风机"
        else -> "设备"
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
