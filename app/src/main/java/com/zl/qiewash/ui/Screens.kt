package com.zl.qiewash.ui

import com.zl.qiewash.Notify

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.animateColorAsState
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import com.zl.qiewash.R
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.unit.lerp
import kotlin.math.roundToInt
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalLaundryService
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zl.qiewash.AppViewModel
import com.zl.qiewash.data.Device
import com.zl.qiewash.data.Option
import com.zl.qiewash.data.Repo
import com.zl.qiewash.data.Sku
import kotlinx.coroutines.delay

@Composable
fun LoginScreen(
    phone: String,
    onPhone: (String) -> Unit,
    onSend: () -> Unit,
    onLogin: (String) -> Unit,
    onImport: (String, String) -> Unit,
    busy: Boolean,
) {
    var code by remember { mutableStateOf("") }
    var showImport by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().clipToBounds().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(72.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.LocalLaundryService,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(34.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("轻洗", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(
            "扫码 · 启动 · 走人",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(
            value = phone,
            onValueChange = onPhone,
            label = { Text("手机号") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            shape = QwShape.inner,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter(Char::isDigit).take(6) },
                label = { Text("验证码") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                shape = QwShape.inner,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = onSend, enabled = !busy, modifier = Modifier.height(56.dp)) {
                Text("获取验证码")
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { onLogin(code) },
            enabled = !busy,
            shape = QwShape.button,
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            if (busy) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Text("登录", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(4.dp))
        TextButton(
            onClick = { showImport = true },
            enabled = !busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("导入已有凭证") }
        Text(
            "只用于你自己账号的扫码洗衣，与官方 App 无关",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }

    if (showImport) {
        var tokenText by remember { mutableStateOf("") }
        var phoneText by remember { mutableStateOf(phone) }
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入凭证") },
            text = {
                Column {
                    Text(
                        "粘贴官方 App 的 token（32 位）或整行 token=…；有效期与官方一致。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tokenText,
                        onValueChange = { tokenText = it },
                        label = { Text("token") },
                        minLines = 2,
                        maxLines = 4,
                        shape = QwShape.inner,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = phoneText,
                        onValueChange = { phoneText = it.filter(Char::isDigit).take(11) },
                        label = { Text("手机号（可选，仅用于显示）") },
                        singleLine = true,
                        shape = QwShape.inner,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showImport = false
                    onImport(tokenText, phoneText)
                }) { Text("导入") }
            },
            dismissButton = {
                TextButton(onClick = { showImport = false }) { Text("取消") }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    devices: List<Device>,
    live: Map<String, Repo.Live>,
    nearby: List<Repo.Info>,
    nearbyLoading: Boolean,
    deviceFilter: String,
    onFilter: (String) -> Unit,
    onScan: () -> Unit,
    onOpen: (String) -> Unit,
    onOrders: () -> Unit,
    onRefreshNearby: () -> Unit,
    onSettings: () -> Unit,
    active: AppViewModel.ActiveRun?,
    onOpenActive: () -> Unit,
    busy: Boolean,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("轻洗", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOrders) {
                        Icon(Icons.Filled.ReceiptLong, contentDescription = "订单")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "设置")
                    }
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        LazyColumn(
            Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize().clipToBounds(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            item(key = "active") {
                // Banner and scan hero are one group: big top, small bottom for the banner,
                // small top + big bottom for the hero. Animates when a run starts or ends.
                AnimatedVisibility(
                    visible = active != null,
                    enter = expandVertically(tween(240)) + fadeIn(tween(180)),
                    exit = shrinkVertically(tween(200)) + fadeOut(tween(120)),
                ) {
                    active?.let {
                        // A pill needs breathing room from the card below it.
                        ActiveRunCard(
                            it, onOpenActive, shape = CircleShape,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
            }

            item {
                Surface(
                    shape = QwShape.hero,
                    onClick = onScan,
                    enabled = !busy,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.QrCodeScanner,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(56.dp),
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "扫码开始",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            "对准机器上的二维码",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        )
                    }
                }
            }

            item(key = "filters") {
                // Two categories only, so this is a pair of toggles rather than a 3-way picker:
                // both on = everything, one on = that kind, tapping the lone one restores both.
                val options = listOf("00" to "洗衣机", "02" to "烘干机")
                SegmentedRow(
                    options = options,
                    isOn = { deviceFilter.isEmpty() || deviceFilter == it },
                    onPick = { v -> onFilter(if (deviceFilter == v) "" else v) },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            item(key = "sec-nearby") {
                SectionHeader(
                    title = "附近设备",
                    subtitle = if (nearby.isEmpty()) "扫描中…" else "${nearby.size} 台",
                    loading = nearbyLoading,
                    onRefresh = onRefreshNearby,
                )
            }
            if (nearby.isNotEmpty()) {
                itemsIndexed(nearby, key = { _, i -> "n-${i.device.goodsId}" }) { i, info ->
                    DeviceRow(
                        name = info.device.name,
                        subtitle = info.device.categoryName,
                        categoryCode = info.device.categoryCode,
                        live = info.live,
                        shape = QwShape.group(i, nearby.size),
                        onClick = { onOpen(info.device.goodsId) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            if (devices.isNotEmpty()) {
                item(key = "sec-recent") { SectionHeader(title = "最近使用", subtitle = "${devices.size} 台") }
                itemsIndexed(devices, key = { _, d -> "r-${d.goodsId}" }) { i, d ->
                    DeviceRow(
                        name = d.name.ifBlank { "设备 ${d.goodsId}" },
                        subtitle = d.categoryName,
                        categoryCode = d.categoryCode,
                        live = live[d.goodsId],
                        shape = QwShape.group(i, devices.size),
                        onClick = { onOpen(d.goodsId) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String = "",
    loading: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 15.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        if (onRefresh != null) {
            if (loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "刷新", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(
    name: String,
    subtitle: String,
    categoryCode: String,
    live: Repo.Live?,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                MachineIcon(categoryCode, MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (live != null) LiveChip(live)
        }
    }
}

@Composable
private fun LiveChip(live: Repo.Live) {
    val (bg, fg) = when (live.tone) {
        Repo.Live.BUSY -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        Repo.Live.FAULT -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        Repo.Live.OFF -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(shape = QwShape.chip, color = bg) {
        Text(
            live.label,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(
    device: Device,
    skus: List<Sku>,
    sku: Sku?,
    option: Option?,
    price: String?,
    originPrice: String?,
    payStep: String,
    busy: Boolean,
    onPick: (Sku, Option) -> Unit,
    onPay: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text(device.name.ifBlank { "设备" }, fontWeight = FontWeight.SemiBold) },
            )
        },
        bottomBar = {
            // Opaque bar that keeps painting under the nav bar; only its content is inset.
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("实付", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            if (price.isNullOrBlank()) "—" else "¥$price",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (!originPrice.isNullOrBlank() && originPrice != price) {
                            Text(
                                "原价 ¥$originPrice",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Button(
                        onClick = onPay,
                        enabled = !busy && option != null,
                        shape = QwShape.button,
                        modifier = Modifier.height(56.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(payStep.ifBlank { "处理中" })
                        } else {
                            Text("支付并启动", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        // pad already contains the bottom bar height (which itself carries the nav bar inset).
        Column(
            Modifier.padding(pad)
                .fillMaxSize()
                .clipToBounds()
                .padding(horizontal = 20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = QwShape.chip,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        device.categoryName,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    "id ${device.goodsId}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))

            // Washer skus are whole programs (one option each): pick the program, not a duration.
            val programMode = skus.size > 1 && skus.all { it.options.size == 1 }

            if (programMode) {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                ) {
                    itemsIndexed(skus) { i, s ->
                        val o = s.options.first()
                        val selected = s.skuId == sku?.skuId
                        ElevatedCard(
                            shape = QwShape.group(i, skus.size),
                            onClick = { onPick(s, o) },
                            colors = CardDefaults.elevatedCardColors(
                                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        s.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        "${o.minutes} 分钟" + if (s.feature.isNotBlank()) " · ${s.feature}" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Text("¥${o.price}", style = MaterialTheme.typography.titleMedium)
                                AnimatedVisibility(selected) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 10.dp).size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
            if (skus.size > 1) {
                SegmentedRow(
                    options = skus.map { it.skuId to it.name },
                    isOn = { it == sku?.skuId },
                    onPick = { id ->
                        skus.firstOrNull { it.skuId == id }?.options?.firstOrNull()?.let { o ->
                            onPick(skus.first { it.skuId == id }, o)
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))
            }

            Text("时长", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
            ) {
                val opts = sku?.options ?: emptyList()
                itemsIndexed(opts) { i, o ->
                    val selected = o.minutes == option?.minutes
                    ElevatedCard(
                        shape = QwShape.group(i, opts.size),
                        onClick = { sku?.let { s -> onPick(s, o) } },
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    "${o.minutes} 分钟",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (o.desc.isNotBlank()) {
                                    Text(
                                        o.desc,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text("¥${o.price}", style = MaterialTheme.typography.titleMedium)
                            AnimatedVisibility(selected) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 10.dp).size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun RunningScreen(
    device: Device?,
    orderNo: String,
    endAt: Long,
    onDone: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
    }
    val remain = ((endAt - now) / 1000).coerceAtLeast(0)
    val text = "%02d:%02d".format(remain / 60, remain % 60)

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            (device?.categoryName ?: "设备") + "运行中",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        ClockText(
            text = text,
            weight = 600f,
            style = TextStyle(fontSize = 72.sp),
            color = MaterialTheme.colorScheme.primary,
            introKey = orderNo,
        )
        Text(
            if (remain == 0L) "本次已结束，可以取衣" else "结束后自动停机计费",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Surface(
            shape = QwShape.inner,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("订单号", style = MaterialTheme.typography.labelSmall)
                Text(orderNo, style = MaterialTheme.typography.bodyMedium)
                device?.name?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onDone, shape = QwShape.button, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("完成")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OrdersScreen(
    groups: List<AppViewModel.OrderMonth>,
    filter: String,
    refreshing: Boolean,
    loadingMore: Boolean,
    endReached: Boolean,
    onFilter: (String) -> Unit,
    onOpen: (String) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("订单", fontWeight = FontWeight.SemiBold) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        // The list scrolls under the system nav bar; that inset returns as list content
        // padding, so scrolled-to-end content clears it (clipToPadding = false).
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val count = groups.sumOf { it.count }
        Column(Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize().clipToBounds()) {
            SegmentedRow(
                options = listOf("" to "全部", "2" to "使用中", "3" to "已完成", "1" to "已失效"),
                isOn = { filter == it },
                onPick = onFilter,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            val listState = rememberLazyListState()
            val nearEnd by remember(count) {
                derivedStateOf {
                    val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    count > 0 && last >= count - 3
                }
            }
            LaunchedEffect(nearEnd, count) { if (nearEnd) onLoadMore() }

            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = onRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (groups.isEmpty() && !refreshing) {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = navBottom),
                    ) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(top = 120.dp), contentAlignment = Alignment.Center) {
                                Text("没有符合条件的订单", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp,
                            bottom = 16.dp + navBottom,
                        ),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        groups.forEachIndexed { gi, g ->
                            stickyHeader(key = "h-${g.key}") {
                                // The bottom month is still paginating, so its sum is partial.
                                MonthHeader(g, partial = !endReached && gi == groups.lastIndex)
                            }
                            itemsIndexed(g.items, key = { _, o -> o.orderNo }) { i, o ->
                                OrderRow(o, QwShape.group(i, g.items.size)) { onOpen(o.orderNo) }
                            }
                            if (gi != groups.lastIndex) {
                                item(key = "gap-${g.key}") { Spacer(Modifier.height(14.dp)) }
                            }
                        }
                        if (loadingMore) {
                            item {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(g: AppViewModel.OrderMonth, partial: Boolean) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            g.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            "${g.count} 笔 · 支出 ¥%.2f".format(g.total) + if (partial) " +" else "",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun OrderRow(o: Repo.OrderBrief, shape: androidx.compose.ui.graphics.Shape, onClick: () -> Unit) {
    ElevatedCard(
        shape = shape,
        onClick = onClick,
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    o.machineName.ifBlank { o.orderNo },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(o.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                listOf(o.functionName, if (o.minutes > 0) "${o.minutes} 分钟" else "", "¥${o.price}")
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${o.createTime}  ${o.orderNo}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StatusChip(status: Int) {
    val text = Repo.OrderStatus.text(status)
    val (bg, fg) = when (status) {
        2 -> MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        3 -> MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        4, 5 -> MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(shape = QwShape.chip, color = bg) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    detail: org.json.JSONObject?,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("订单详情", fontWeight = FontWeight.SemiBold) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        if (detail == null) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
            }
        } else {
            val rows = listOf(
                "订单号" to detail.optString("orderNo"),
                "状态" to Repo.OrderStatus.text(detail.optInt("orderStatus", -1)),
                "设备" to detail.optString("machineName"),
                "项目" to detail.optString("machineFunctionName"),
                "时长" to detail.optInt("markMinutes", 0).let { if (it > 0) "$it 分钟" else "" },
                "原价" to detail.optString("markPrice"),
                "实付" to detail.optString("payPrice"),
                "支付方式" to detail.optString("payTypeName"),
                "下单时间" to detail.optString("createTime"),
                "支付时间" to detail.optString("payTime"),
                "完成时间" to detail.optString("finishTime"),
                "店铺" to detail.optString("shopName"),
                "机器 imei" to detail.optString("imei"),
                "showStart" to detail.optBoolean("showStart", false).toString(),
            )
            LazyColumn(
                Modifier.padding(pad).fillMaxSize().padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(rows) { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Text(
                            k,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(88.dp),
                        )
                        Text(v.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    phone: String,
    token: String,
    lat: String,
    lng: String,
    spread: Int,
    diagPath: String,
    onSavePosition: (String, String) -> Unit,
    onRelocate: () -> Unit,
    onWeightDemo: () -> Unit,
    onSpread: (Int) -> Unit,
    onImport: (String, String) -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
) {
    val clip = LocalClipboardManager.current
    val ctx = LocalContext.current
    var latInput by remember { mutableStateOf(lat) }
    var lngInput by remember { mutableStateOf(lng) }
    var showImport by remember { mutableStateOf(false) }
    var importText by remember { mutableStateOf("") }
    var confirmLogout by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                title = { Text("设置", fontWeight = FontWeight.SemiBold) },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { pad ->
        val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        Column(
            Modifier.padding(top = pad.calculateTopPadding()).fillMaxSize().clipToBounds()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp + navBottom),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---- account ----
            Surface(shape = QwShape.card, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("账号", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    KeyValue("手机号", phone.ifBlank { "—" })
                    Spacer(Modifier.height(8.dp))
                    KeyValue("凭证", token.take(8) + "…" + token.takeLast(6))
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = {
                            clip.setText(AnnotatedString(token))
                            Toast.makeText(ctx, "凭证已复制", Toast.LENGTH_SHORT).show()
                        }) { Text("复制凭证") }
                        OutlinedButton(onClick = { importText = ""; showImport = true }) { Text("导入") }
                    }
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { confirmLogout = true }) {
                        Text("退出登录", color = MaterialTheme.colorScheme.error)
                    }
                }
            }

            // ---- position ----
            Surface(shape = QwShape.card, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("位置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "只用于下单风控签名，不影响设备列表",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = latInput, onValueChange = { latInput = it },
                            label = { Text("纬度") }, singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = lngInput, onValueChange = { lngInput = it },
                            label = { Text("经度") }, singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onRelocate) { Text("重新定位") }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = onWeightDemo) { Text("可变字重示例") }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("附近扫描半径", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.weight(1f))
                        SingleChoiceSegmentedButtonRow {
                            listOf(10, 20, 30).forEachIndexed { i, v ->
                                SegmentedButton(
                                    selected = spread == v,
                                    onClick = { onSpread(v) },
                                    shape = SegmentedButtonDefaults.itemShape(i, 3),
                                ) { Text("$v") }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onSavePosition(latInput, lngInput) },
                        shape = QwShape.button,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("保存位置") }
                }
            }

            // ---- permissions ----
            Surface(shape = QwShape.card, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("提醒", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    PermissionRow(
                        title = "通知",
                        ok = Notify.canNotify(ctx),
                        desc = "洗涤倒计时与完成提醒",
                        action = "去开启",
                    ) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    PermissionRow(
                        title = "精确闹钟",
                        ok = Notify.canExactAlarm(ctx),
                        desc = "到点准时响，不被省电推迟",
                        action = "去授权",
                    ) {
                        val i = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                                .setData(android.net.Uri.parse("package:" + ctx.packageName))
                        else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                            .setData(android.net.Uri.parse("package:" + ctx.packageName))
                        ctx.startActivity(i)
                    }
                    Spacer(Modifier.height(10.dp))
                    PermissionRow(
                        title = "实况通知（岛上）",
                        ok = Notify.canPromote(ctx),
                        desc = "常驻倒计时显示在顶部状态栏",
                        action = "去设置",
                    ) {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    FilledTonalButton(onClick = { Notify.test(ctx) }) { Text("测试实况通知") }
                }
            }

            // ---- diagnostics ----
            Surface(shape = QwShape.card, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("诊断", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    KeyValue("日志", diagPath.ifBlank { "—" })
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(onClick = {
                        clip.setText(AnnotatedString(diagPath))
                        Toast.makeText(ctx, "路径已复制", Toast.LENGTH_SHORT).show()
                    }) { Text("复制日志路径") }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }

    if (showImport) {
        AlertDialog(
            onDismissRequest = { showImport = false },
            title = { Text("导入已有凭证") },
            text = {
                OutlinedTextField(
                    value = importText, onValueChange = { importText = it },
                    label = { Text("token") }, minLines = 3,
                )
            },
            confirmButton = {
                TextButton(onClick = { showImport = false; onImport(importText, phone) }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { showImport = false }) { Text("取消") } },
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录？") },
            text = { Text("会清掉本机保存的凭证，需要重新登录或导入。") },
            confirmButton = {
                TextButton(onClick = { confirmLogout = false; onLogout() }) {
                    Text("退出", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            key,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(72.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Washer = drum with a water wave, dryer = drum with a heat curl and a fan dot. */
@Composable
private fun MachineIcon(categoryCode: String, tint: Color) {
    val dryer = categoryCode == "02"
    Canvas(Modifier.size(24.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.085f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.07f, h * 0.09f),
            size = Size(w * 0.86f, h * 0.82f),
            cornerRadius = CornerRadius(w * 0.22f),
            style = stroke,
        )
        // control panel
        drawLine(
            tint,
            Offset(w * 0.20f, h * 0.28f),
            Offset(w * 0.80f, h * 0.28f),
            strokeWidth = w * 0.075f,
            cap = StrokeCap.Round,
        )
        val c = Offset(w * 0.5f, h * 0.60f)
        val r = w * 0.215f
        drawCircle(tint, radius = r, center = c, style = stroke)
        val inner = Path()
        if (dryer) {
            // heat curl + fan dot
            inner.moveTo(c.x - r * 0.55f, c.y + r * 0.30f)
            inner.quadraticBezierTo(c.x - r * 0.1f, c.y - r * 0.55f, c.x + r * 0.15f, c.y + r * 0.05f)
            inner.quadraticBezierTo(c.x + r * 0.35f, c.y + r * 0.55f, c.x + r * 0.60f, c.y - r * 0.05f)
            drawPath(inner, tint, style = stroke)
            drawCircle(tint, radius = r * 0.13f, center = Offset(c.x, c.y - r * 0.62f))
        } else {
            // water wave
            inner.moveTo(c.x - r * 0.60f, c.y + r * 0.05f)
            inner.quadraticBezierTo(c.x - r * 0.25f, c.y - r * 0.45f, c.x, c.y + r * 0.02f)
            inner.quadraticBezierTo(c.x + r * 0.25f, c.y + r * 0.48f, c.x + r * 0.60f, c.y + r * 0.02f)
            drawPath(inner, tint, style = stroke)
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    ok: Boolean,
    desc: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (ok) "已开启" else "未开启",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!ok) TextButton(onClick = onClick) { Text(action) }
    }
}

/** Banner for the machine in use; survives leaving the running screen. */
@Composable
private fun ActiveRunCard(
    run: AppViewModel.ActiveRun,
    onClick: () -> Unit,
    shape: androidx.compose.ui.graphics.Shape,
    modifier: Modifier = Modifier,
) {
    var now by remember(run.endAt) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(run.endAt) {
        while (true) {
            now = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    val rawLeft = run.endAt - now
    val finished = rawLeft <= 0
    val left = rawLeft.coerceAtLeast(0)
    val span = (run.endAt - run.startAt).coerceAtLeast(1L)
    val progress = (left.toFloat() / span).coerceIn(0f, 1f)
    // M3E variable weight: light while there is time, heavy as the drum winds down.
    val weight = 320f + (1f - progress) * 580f
    val label = when {
        finished -> "${((-rawLeft) / 60_000L).coerceAtLeast(0)} 分前"
        left >= 3_600_000 -> "%d 时 %02d 分".format(left / 3_600_000, left % 3_600_000 / 60_000)
        else -> "%d:%02d".format(left / 60_000, left % 60_000 / 1000)
    }
    val container = if (finished) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.primary
    val onContainer = if (finished) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimary
    Surface(
        onClick = onClick,
        shape = shape,
        color = container,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (finished) "已完成 · 记得取衣" else "正在使用",
                    style = MaterialTheme.typography.labelMedium,
                    color = onContainer.copy(alpha = 0.7f),
                )
                Text(
                    run.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            ClockText(
                text = label,
                weight = weight,
                style = MaterialTheme.typography.displaySmall,
                color = onContainer,
                introKey = run.orderNo,
            )
        }
    }
}

/** Horizontal variant of the grouped-list corners: outer ends round, inner joints tight. */
private fun segShape(index: Int, count: Int): androidx.compose.ui.graphics.Shape {
    val big = 20.dp
    val small = 6.dp
    val start = if (index == 0) big else small
    val end = if (index == count - 1) big else small
    return RoundedCornerShape(start, end, end, start)
}

/**
 * M3E button group: at rest the segments merge into one container (outer ends round,
 * joints tight). A tap plays a short scale pulse on the segment that was hit — scale
 * instead of width so the click never fights the layout.
 */
@Composable
private fun SegmentedRow(
    options: List<Pair<String, String>>,
    isOn: (String) -> Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val big = 22.dp
    val tight = 8.dp
    val morph = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var kicked by remember { mutableIntStateOf(-1) }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { i, (value, label) ->
            val on = isOn(value)
            val bg by animateColorAsState(
                targetValue = if (on) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                animationSpec = tween(220),
                label = "bg",
            )
            val fg by animateColorAsState(
                targetValue = if (on) MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(220),
                label = "fg",
            )
            val startR = if (i == 0) big else tight
            val endR = if (i == options.lastIndex) big else tight
            val pulse = if (kicked == i) morph.value else 0f
            Box(
                Modifier
                    .weight(1f + 0.16f * pulse)
                    .height(40.dp)
                    .clip(RoundedCornerShape(startR, endR, endR, startR))
                    .background(bg)
                    // Feedback starts on finger-down, so a long hold shows the pulse at
                    // once; the selection only commits if the finger lifts inside.
                    .pointerInput(value) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            kicked = i
                            // Holding shows half of the morph; only a completed tap plays
                            // the rest of it and commits the selection.
                            scope.launch {
                                morph.animateTo(0.5f, tween(120, easing = FastOutSlowInEasing))
                            }
                            if (waitForUpOrCancellation() != null) {
                                scope.launch {
                                    morph.animateTo(1f, tween(140, easing = FastOutSlowInEasing))
                                    morph.animateTo(0f, tween(260, easing = FastOutSlowInEasing))
                                }
                                onPick(value)
                            } else {
                                scope.launch { morph.animateTo(0f, tween(200)) }
                            }
                        }
                    }
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        selected = on
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
            }
        }
    }
}
