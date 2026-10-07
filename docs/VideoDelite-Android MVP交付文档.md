# VideoDelite 安卓端 MVP 交付文档

> **文档性质：** 安卓端 MVP 交付基准 / 验收清单
> **产品名称：** VideoDelite（安卓客户端）
> **交付版本：** 1.0.3（versionCode 4）
> **交付日期：** 2026-10-07
> **源码仓库：** `github.com/felixniun/VideoDeLite-Android`（独立仓库，与桌面版分开维护）
> **配套文档：** 桌面/服务端《VideoDelite MVP交付计划书》、本仓 `README.md`

---

## 1. 系统架构总览

安卓端 = **纯原生压缩客户端** + 对接桌面版**同一套账号服务**。产品结构与桌面版一致：
视频读取/分析/编码/落盘 100% 在本机完成，账号服务只处理身份/设备/授权元数据。

```
        ┌─────────────────────────────────────────────┐
        │  安卓手机（本机）                            │
        │  Kotlin 2.0 + Jetpack Compose               │
        │  Media3 Transformer 1.5.1（无 ffmpeg）       │
        │  ┌───────────────────────────────────────┐  │
        │  │ 首页 → 选择视频 → 分析(MediaExtractor) │  │
        │  │      → Transformer 压缩 → 输出复检     │  │
        │  │      → 落相册 Movies/VideoDelite       │  │
        │  │      → Room 压缩历史                   │  │
        │  └───────────────────────────────────────┘  │
        └───────────────┬─────────────────────────────┘
                        │ 仅登录 / 注册 / 设备激活 / 授权状态
                        │ HTTPS  https://videodelite1.898280.xyz:28443
                        ▼
        ┌─────────────────────────────────────────────┐
        │  Debian 家宽服务器（与桌面版共用）           │
        │  nginx(28443→443, Let's Encrypt) → Gin 服务  │
        │  SQL Server（账号/设备/授权/事件表）          │
        └─────────────────────────────────────────────┘

  ▲ 视频数据永不上传：服务端只收到 邮箱 / 密码哈希 / installationId / 版本号
```

**关键点：** API 地址硬编码在 `BuildConfig.API_BASE`（`app/build.gradle.kts`），界面不暴露、不可改。

---

## 2. MVP 交付范围

| # | 功能 | 状态 | 证据 / 说明 |
|---|---|---|---|
| 1 | Kotlin + Compose 原生工程 | ✅ | `./gradlew assembleRelease` → `app/build/outputs/apk/release/` |
| 2 | 视频分析（MediaExtractor） | ✅ | 分辨率/时长/帧率/编码，`Analyzer.kt` |
| 3 | Simple 模式（H.264/H.265 × 低/中/高） | ✅ | 码率表 1:1 移植，540 行 golden 单测锁定 |
| 4 | 硬编优先 + 软编回退（H.264） | ✅ | `DefaultEncoderFactory` 默认策略 |
| 5 | H.265（需机型硬件编码器） | ✅ | 无硬编时界面自动禁用 H.265 |
| 6 | 任务队列 + 并行 1–3 | ✅ | `TaskManager`，桌面 §35 |
| 7 | 实时进度 + 前台服务通知 | ✅ | `ExportService`，切后台/熄屏继续 |
| 8 | 输出复检 | ✅ | `validateOutput`：轨道/时长合理性硬检查 |
| 9 | 落相册 + 重名自动加序号 | ✅ | `OutputSaver`（MediaStore / 旧版写权限） |
| 10 | 压缩历史（Room） | ✅ | 含 `mode` 列（simple/professional），Room v2 迁移 |
| 11 | **Professional 模式** | ✅ | 自定义码率 + CBR/VBR/质量 码控 + 音频码率 |
| 12 | 账号 / 设备 / 授权全套 | ✅ | 注册/验证码/登录/刷新轮换/设备激活吊销/注销 |
| 13 | 五页 UI + 中英双语 + 浅/深/跟随系统 | ✅ | 首页/任务/历史/设置/账号 |
| 14 | 「打开方式 → VideoDelite」直接入队 | ✅ | `ACTION_VIEW video/*`，免登录，对齐桌面拖拽导入 |
| 15 | 检查更新 | ✅ | 对接 `/api/v1/version` |

### 版本沿革

| 版本 | 日期 | 内容 |
|---|---|---|
| 1.0.0 | 2026-10-06 | 首个交付；**压缩管线有线程 bug，已报废勿用** |
| 1.0.1 | 2026-10-07 | E2E 三修复 + 打开方式；签名更换（1.0.0 需先卸载） |
| 1.0.2 | 2026-10-07 | 新增 Professional 模式；修主题切换频闪、修激活卡死 |
| 1.0.3 | 2026-10-07 | 专业模式改为登录即解锁（不再要求设备激活成功） |
| **1.0.4** | **2026-10-07** | **修复专业模式真机编码失败**（去掉 `setBitrateMode`，改用平均码率） |

---

## 3. 架构红线执行情况

对齐桌面版《账号与本地压缩边界规范》与主计划书 §15/§29：

| 红线 | 安卓端执行 | 证据 |
|---|---|---|
| 视频数据不上传 | ✅ | 仅登录/激活联网；`CompressEngine` 全程本地文件 IO |
| 授权与编码解耦 | ✅ | 账号状态只决定「能否新建专业任务」，`TaskManager` 无账号依赖 |
| 授权变化不杀运行中压缩 | ✅ | 解锁条件只在 UI 层判断，运行中的任务不受影响 |
| 输出必须验证，非"没报错即成功" | ✅ | `CompressEngine.validateOutput` 复检轨道与时长 |
| Simple 模式免登录 | ✅ | 未登录全部可用；专业模式才需登录 |
| 不误删用户数据 | ✅ | 清历史/退出/删账号均不动已落盘视频 |
| API 地址不暴露 | ✅ | 硬编码 `BuildConfig.API_BASE`，UI 无入口 |

---

## 4. 实测记录

### 4.1 E2E 全链路（API 35 模拟器）

1080p 输入 5.0MB → 码率表算出 5 Mbps → Transformer 压缩 **7.9s** → 落盘相册
`Movies/VideoDelite` → Room 历史 `success`。**分析 → 码率表 → 压缩 → 落盘 → 历史 全链路通过。**

### 4.2 码率表一致性（golden 单测）

`app/src/test/resources/golden_bitrate.csv`（540 行，由桌面版 Go 实现生成）逐值比对，
`./gradlew test` 全量通过。含 H.265 = H.264 × 0.75 取 0.5 Mbps 步进、低于 720p 下限 1 Mbps、
高于 4K 封顶 120 Mbps 等冻结规则。

### 4.3 UI 与交互（模拟器 + 元素树验证）

| 项 | 结果 |
|---|---|
| 启动 / 五页渲染 | ✅ 无崩溃 |
| 模式选择器（简单/专业） | ✅ 渲染正常 |
| 未登录点「专业」 | ✅ 正确显示锁定卡 + 「去登录」 |
| 主题 浅/深 切换 | ✅ 无崩溃、无卡死、**进程 ID 不变（无 Activity 重建）** |
| 侧载安装 | ✅ `adb install` 成功，versionCode 4 / 1.0.3 |

### 4.4 构建与签名

| 项 | 结果 |
|---|---|
| `./gradlew test` | ✅ 通过（含 540 行 golden 比对） |
| `./gradlew assembleRelease` | ✅ R8 + 资源收缩，产物 2.7MB |
| APK 签名 | ✅ `CN=VideoDelite`，SHA-256 `e44f20da9010ec33d9beb8f6872b23998e349b8b1dcac59648c58244b285165c` |

### 4.5 真机实测状态

- ✅ 登录链路：真机可登录成功（服务端后台可见登录记录）。
- ⏳ 待补测：H.265 硬编判定、各机型编码器差异、通知/后台存活（模拟器只覆盖软编路径）。

---

## 5. 交付物清单

```
VideoDeLite-Android/                     # Gradle 工程根 = 仓库根
├── app/build/outputs/apk/release/       # 构建产物（gitignore）
├── keystore/                            # 【本地 gitignore】签名密钥，务必异地备份
├── keystore.properties                  # 【本地 gitignore】密钥口令
├── gradle/libs.versions.toml            # 依赖版本清单
├── README.md                            # 仓库首页（功能/构建/签名说明）
└── docs/VideoDelite-Android MVP交付文档.md   # 本文档

交付 APK（归档仓 VIdeoDeliteAPK/android/）：
  VideoDelite-1.0.3.apk   2.7MB   release 签名   ← 当前版本
  VideoDelite-1.0.2.apk   2.7MB   历史
  VideoDelite-1.0.1.apk   2.7MB   历史
  VideoDelite-1.0.0.apk   2.7MB   历史（有 bug，勿用）
```

### 全项目文件图鉴（40 个受管文件）

**入口 / 装配**

| 文件 | 职责 |
|---|---|
| `MainActivity.kt` | 唯一 Activity：主题解析、语言切换、`ACTION_VIEW` 入队 |
| `VideoDeliteApp.kt` | Application：初始化 AppGraph；debug 版入队广播接收器 |
| `AppGraph.kt` | 极简服务定位器（设置/DB/令牌/账号/引擎/任务管理） |
| `AndroidManifest.xml` | 权限、前台服务类型、`ACTION_VIEW video/*` + singleTask |

**core/ —— 压缩策略**

| 文件 | 职责 |
|---|---|
| `Bitrate.kt` | 冻结码率表（短边分档 × FPS 桶 × 低/中/高；H.265×0.75） |
| `Format.kt` | 体积/时长/预估输出的展示格式化 |

**media/ —— 压缩管线**

| 文件 | 职责 |
|---|---|
| `Analyzer.kt` | MediaExtractor 探测分辨率/时长/帧率/编码 |
| `VideoInfo.kt` | 分析结果模型 |
| `Presets.kt` | Simple 模式的编码器/画质枚举 |
| `ProConfig.kt` | **专业模式参数模型**（码控 CBR/VBR/CQ、码率、音频码率） |
| `CompressEngine.kt` | Media3 Transformer 封装：码率/码控映射、进度、取消、输出复检 |
| `TaskManager.kt` | 队列调度（并行 ≤3）、状态机、写历史、前台服务拉起 |
| `ExportService.kt` | 前台服务 + 常驻进度通知（mediaProcessing→dataSync 降级） |
| `OutputSaver.kt` | 落相册 `Movies/VideoDelite`，重名自动加序号 |

**data/ —— 本地持久化**

| 文件 | 职责 |
|---|---|
| `History.kt` | Room 实体/DAO/DB（v2：新增 `mode` 列 + 迁移） |
| `SettingsRepository.kt` | DataStore：语言/主题/默认编码器/默认画质/并行数 |
| `TokenStore.kt` | EncryptedSharedPreferences：令牌对 |
| `DeviceId.kt` | 稳定 installationId（16 hex，首次随机生成） |

**network/ —— 账号服务客户端**

| 文件 | 职责 |
|---|---|
| `VdApi.kt` | Retrofit 接口 + AuthStack（401 单飞刷新轮换）+ apiCall 错误映射 |
| `ApiModels.kt` | 与服务端 JSON 一一对应的线上模型 |
| `AccountClient.kt` | 登录/注册/验证码/激活/授权/设备/注销；**登录即解锁专业模式** |

**ui/ —— Compose 界面**

| 文件 | 职责 |
|---|---|
| `AppRoot.kt` | Scaffold + 底部五页导航 + 任务角标 |
| `components/Common.kt` | SectionCard/SectionLabel/VdSegmented/错误本地化 |
| `screens/HomeScreen.kt` | 首页：模式选择、编解码、画质、选片、开始压缩 |
| `screens/ProPanel.kt` | **专业模式面板**（码控/码率/音频码率，登录后可见） |
| `screens/TasksScreen.kt` | 任务页：进度、取消、清除 |
| `screens/HistoryScreen.kt` | 历史页：压缩率/耗时/状态 |
| `screens/SettingsScreen.kt` | 设置页：外观/压缩/账号/关于/检查更新 |
| `screens/AccountScreen.kt` | 账号页：登录注册验证、授权状态、设备列表、吊销、注销 |
| `theme/Theme.kt` | Apple 风格配色 + 深浅主题 |

**res/ —— 资源**

`values/strings.xml`（中文默认）、`values-en/strings.xml`（英文）、
`values*/colors.xml`、`values*/themes.xml`、`drawable/ic_launcher_{bg,fg}.xml`、
`drawable/ic_stat_vd.xml`、`mipmap-anydpi-v26/ic_launcher.xml`

**测试**

`app/src/test/java/.../core/BitrateTest.kt` + `app/src/test/resources/golden_bitrate.csv`（540 行）

### 数据流（谁调谁）

```
选择视频 → HomeScreen(HomeViewModel) → Pending 列表 → 点「开始压缩」
   → TaskManager.enqueue(...) 排队（并行 ≤3）
   → Analyzer.analyze(uri)     取分辨率/时长/帧率
   → Bitrate.simpleBitrateMbps()（或 ProConfig 自定义码率）
   → CompressEngine.compress()  → Media3 Transformer（硬编优先）
        ↑ 每 200ms getProgress → TaskManager 更新 → TasksView 进度条 / 通知
   → CompressEngine.validateOutput()  复检
   → OutputSaver.save()         落相册 Movies/VideoDelite
   → Room 写历史（success / failed）

账号线（与压缩线完全隔离）：
AccountScreen → AccountClient.login() → VdApi(/auth/login) → 存令牌 → 登录即解锁专业模式
                                   └→ 后台静默 activateDevice()（设备登记，失败不影响解锁）
```

---

## 6. 与桌面版的合法差异（已确认，非遗漏）

| 桌面版 | 安卓端 | 原因 |
|---|---|---|
| MP4 / MKV 容器 | **仅 MP4** | 原生栈无 MKV muxer；引 ffmpeg +40MB 且上游 ffmpeg-kit 停维（用户明确否决 ffmpeg） |
| NVENC/QSV/AMF + CPU 回退 | 硬编优先 + 系统软编回退（H.264） | 平台差异 |
| — | H.265 仅机型有硬件编码器时开放 | 无 ffmpeg 软编；界面自动禁用 |
| 冲突策略 4 种 | 重名自动加序号 | MediaStore 标准行为 |
| `--cli` 无头模式 | 不适用 | 安卓无常驻 CLI 场景 |
| Pro 面板含 MKV/字幕/章节/元数据保留 | 不含 | 无 ffmpeg 无法实现 |
| Pro 面板 VBR 有独立上限、CQ 用 CRF、音频 VBR 质量档 | 统一以**平均码率**编码：CBR/VBR 用填写的码率，质量模式按画质数值换算码率；音频 VBR 按等效码率 | media3 1.5.1 的 `VideoEncoderSettings` 只有 `setBitrate`/`setBitrateMode`，`AudioEncoderSettings` 只有 `setBitrate`。实测证明设置硬件编码器不接受的 `setBitrateMode` 会让 `MediaCodec.configure()` 硬失败且 media3 不回退（见已知限制 2），故只设码率 |

---

## 7. 已知限制与运维备忘

1. **签名密钥不在仓库**：`keystore/videodelite.keystore` + `keystore.properties` 均 gitignore。
   **务必异地备份**——丢失后无法同签名升级，只能卸载重装。1.0.1 起签名已更换，1.0.0 需先卸载。
2. **专业模式码控为近似实现**：media3 1.5.1 无法设置 CRF/质量，且设置硬件编码器不接受的
   `setBitrateMode` 会让 `MediaCodec.configure()` 硬失败（media3 不回退）——1.0.3 在真机
   4K H.264 上实测到 `Codec exception` 与裸 `error`，1.0.4 起**只设平均码率**，码控方式
   仅决定码率怎么算。CBR 与 VBR 在编码器层面因此等价，界面已注明。
3. **设备激活（服务端）**：真机登录正常，但设备激活在服务端 `handleActivate`
   （`UpsertDevice` MSSQL `MERGE` → `GetActiveLicense` → `CreateLicense`）可能挂起/超时。
   1.0.3 起激活降级为**后台静默登记**，失败不影响专业模式解锁——但服务端仍建议排查该链路。
4. **DNS 解析**：公网 DNS（Cloudflare 托管）当前把 `videodelite1.898280.xyz` 解析到内网
   `192.168.100.101`。内网 hosts 可正常使用；**真机在家庭网络之外（移动数据）可能连不上**，
   建议改为公网 A 记录 + 端口转发，或上 Cloudflare Tunnel。
5. **后台存活边界**：切后台/熄屏/从最近任务划掉都继续；ROM「一键清理」或系统「强行停止」
   会真杀进程丢任务。想停用任务页「取消」。
6. **通知权限**：Android 13+ 首次压缩会请求，建议允许，否则后台无进度可见。
7. **前台服务类型降级**：API 35 优先 `mediaProcessing`，部分系统镜像/模拟器直接拒绝
   （`InvalidForegroundServiceTypeException`），代码自动降级为 `dataSync`，属预期行为。
8. **1.0.0 已报废**：压缩线程 bug 在任何设备触发，归档中仅留痕。

---

## 8. 验收结论

| 验收项 | 结论 |
|---|---|
| 功能完整度（Simple + Professional + 账号 + 历史） | ✅ 达成 |
| 架构红线（视频不上传 / 授权不干扰编码） | ✅ 达成 |
| 码率策略与桌面端一致性 | ✅ 540 行 golden 全量一致 |
| 构建可复现 | ✅ 干净检出 `./gradlew test assembleRelease` |
| E2E 全链路 | ✅ API 35 模拟器通过 |
| 交付物齐备 | ✅ APK + 源码 + 文档 |
| 真机长尾（H.265 硬编/通知/后台） | ⏳ 待侧载实测反馈 |

**结论：安卓端 MVP 达成，可交付侧载实测。**

---

## 9. 后续方向

1. 真机实测反馈闭环（H.265 判定、各机型编码器差异、后台存活）。
2. 服务端设备激活链路排查（MSSQL `devices`/`licenses` 表挂起）。
3. 服务端公网可达性（DNS A 记录 / Cloudflare Tunnel），让移动数据也能登录。
4. 专业模式补齐可选参数（编码速度 preset 等）视需求。
5. 归档仓 `VIdeoDeliteAPK` 是否建 GitHub 远程（当前仅本地提交）。
