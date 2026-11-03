# 轻洗 · QieWash

自助洗衣机 / 烘干机的第三方 Android 客户端：扫码 → 选程序 → 支付 → 倒计时。
Compose + Material 3 Expressive，单人自用、边写边学的产物。

> 非官方项目，与平台方无任何关系。仅调用自己账号的接口，不绕过计费。

## 能做什么

- **登录**：短信验证码，或直接导入已有 token（`Bearer` 之外无需 refresh）
- **扫码**：机器二维码是整条 URL，从中取 `NQT` / `IMEI` / `SN`
- **选档**：洗衣机按程序（大物洗/标准洗/快速洗/单脱水/桶自洁），烘干机按分钟；价格取 `trade/preview` 实付
- **支付**：支付宝 App 支付（`payType 13/32`）；¥0 订单只返回余额渠道，走 `pay/unify/pay` 直付
- **运行**：倒计时 + 常驻「实况」通知（Android 16 起可上岛）+ 到点精确闹钟提醒
- **订单**：按月分组、每月支出统计、下拉刷新、滚动自动补页、按状态/品类筛选
- **附近设备**：官方没有设备列表接口，按 `goodsId` 顺序扫描同一 org 的机器（半径可调），带空闲/使用中/故障状态
- **设置**：位置、通知与精确闹钟授权、导出凭证、退出登录

## 界面

```
┌─ 首页 ─────────────────┐   ┌─ 订单 ─────────────────┐
│ 轻洗            [收据][⚙]│   │ ← 订单                  │
│ ┌────────────────────┐ │   │ [全部][待支付][使用中]… │
│ │    ⛶  扫码开始      │ │   │ 2026年10月 · 21 笔 · ¥46.13
│ │   对准机器上的二维码 │ │   │ ┌────────────────────┐ │
│ └────────────────────┘ │   │ │标准洗 648号5楼  ¥2.99│ │
│ [全部][洗衣机][烘干机]   │   │ ├────────────────────┤ │
│ 附近设备      22 台  ⟳  │   │ │桶自洁 …      ¥0.00 │ │
│ ┌────────────────────┐ │   │ └────────────────────┘ │
│ │◉ 706号5楼 烘干机 空闲│ │   │ 2026年09月 · 19 笔 · ¥63.72 +
│ ├────────────────────┤ │   └────────────────────────┘
│ │◉ 642号5楼 洗衣机 使用中│
│ └────────────────────┘ │   状态与品类筛选在列表上方，
│ 最近使用                │   故障机永远沉到分组末尾
└────────────────────────┘
```

- 卡片圆角按「组内位置」变化：组首大上圆角、组末大下圆角、中间 6dp——借用 M3 Expressive 的同心圆角思路
- 列表用 clipToPadding 语义：内容画到导航栏下，末尾留导航栏高度
- 深色下 `surface` 与背景同色，列表容器改用 `surfaceContainerHigh`

## 构建

需要 **JDK 17** 与 **Android SDK 36**（minSdk 26）。

```bash
git clone https://github.com/ZL114514/qiewash.git
cd qiewash
```

在 `local.properties` 里补两行（该文件已被 gitignore）：

```properties
sdk.dir=/path/to/Android/Sdk
qiewash.appSecret=<你的签名密钥>
```

`appSecret` 是官方 App 里那串参与 SHA-256 签名的盐，**仓库不提供**；没有它所有请求都会签名失败。
自己抓包/逆出来即可，也可以参考 `Api.kt` 里的签名拼接顺序。

```bash
./gradlew :app:assembleDebug
# 产物 app/build/outputs/apk/debug/app-debug.apk
```

## 代码结构

```
app/src/main/java/com/zl/qiewash/
├── MainActivity.kt     单 Activity + navigation-compose 真实返回栈（9 条 route）
├── AppViewModel.kt     状态机 LOGIN/LOCATION/HOME/SCAN/PLAN/RUNNING/ORDERS/SETTINGS
├── Notify.kt           实况通知（ProgressStyle + promoted ongoing）、完成通知、精确闹钟
├── Locate.kt           一次性粗定位，只为补坐标
├── Diag.kt             文件日志（/sdcard/Android/data/…/diag.log）
├── data/
│   ├── Api.kt          表单 POST + 两条 SHA-256 签名 + token 持久化
│   └── Repo.kt         扫码/详情/档位/试算/渠道/建单/预支付/轮询/订单/设备状态
└── ui/
    ├── Theme.kt        M3 配色、QwShape 同心圆角、group(index,count)
    ├── Screens.kt      登录 / 首页 / 选档 / 运行中 / 订单 / 订单详情 / 设置
    ├── Onboard.kt      首次启动的位置引导
    └── Scanner.kt      CameraX + ZXing
```

## 踩过的坑（写给后来人）

- `PayTask.payV2()` 是阻塞调用，必须扔到 `Dispatchers.IO`，放主线程会死锁在「拉起支付宝」。
- 支付成功要轮询 `order/detail` 的 `payTime` / `orderStatus`；`machine/device_is_start` 对未支付订单也返回 true。
- `trade/create` 没有 `orderNo`，单号是 `tradeNo`（= `orderId`）。
- 服务端 `orderStatus` 过滤和 App 枚举不一致，筛选只能在客户端做。
- 设备状态藏在 `deviceErrorCode`：`2` 是「工作中」、`7` 是「已预约」，其余才是故障码（4→4107、8→4103…）。

## 免责

学习 Compose / 协议分析用途。接口随时可能变；因使用本客户端产生的任何问题自负。
项目不含任何真实凭证，也不接受索要密钥的请求。

## License

MIT
