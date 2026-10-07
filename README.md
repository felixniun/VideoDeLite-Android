# VideoDelite-Android

VideoDelite（本地视频压缩工具）的**安卓客户端**，独立仓库，与 Windows 桌面版（[VideoDelite](https://github.com/felixniun/VideoDeLite)）分开维护。与桌面版共用同一套产品逻辑与账号服务：

- **本地压缩**：视频始终留在本机，不上传（纯原生栈 Media3 Transformer，无 ffmpeg）。
- **同一账号服务**：注册 / 邮箱验证码 / 登录 / 设备激活 / 授权状态 / 设备吊销 / 注销账号，对接 `https://videodelite1.898280.xyz:28443`（地址内置，界面不暴露）。
- **同一压缩策略**：码率表 1:1 移植自桌面版 `internal/encoder/bitrate.go`（分辨率短边分档 × FPS 桶 × 低/中/高，H.265 = H.264 × 0.75 取 0.5 Mbps 步进），单测用桌面版 Go 实现生成的 540 行 golden 值逐一比对（`app/src/test/resources/golden_bitrate.csv`）。

## 与桌面版的功能差异（有意为之）

| 桌面版 | 安卓版 | 原因 |
|---|---|---|
| MP4 / MKV 容器 | 仅 MP4 | 安卓原生栈（Media3 / MediaMuxer）不支持 MKV 封装；引入 ffmpeg 体积+40MB 且上游 ffmpeg-kit 已停止维护 |
| NVENC 硬编 + CPU 回退 | 硬编优先 + 系统软编回退（H.264） | 平台差异；H.265 需要机型硬件编码器支持，无硬编时界面自动禁用 |
| 冲突策略 overwrite/skip/auto/cancel | 重名自动加序号 | 相册 MediaStore 的标准行为 |
| `--cli` 模式 | 不适用 | 安卓无常驻 CLI 场景 |

其余功能（多选导入、实时进度、并行任务 1–3 可设、压缩历史、浅色/深色/跟随系统、简体中文/English、检查更新、未登录可用 Simple 模式）与桌面版一致。**登录即解锁** **Professional 模式**：自定义码率 0.5–120 Mbps + 码控 CBR/VBR/质量（CQ）+ 音频码率 64–512 kbps（账号状态只门控「能否新建专业任务」，绝不打断运行中的压缩，与桌面版授权红线一致）。设备激活退为后台静默登记，其网络/超时类失败不影响解锁（仅服务器明确 403 吊销才锁）。

## 安装（侧载）

构建产物 APK 传到手机（微信/网盘/USB 均可）→ 点开 → 允许「未知来源」→ 安装。首次启动直接可用，无需注册；要管理设备/授权再登录。

> 压缩在**前台服务**中进行：切后台、熄屏继续；但 ROM「一键清理」或系统「强行停止」会杀进程丢任务。想停某个任务用任务页的「取消」按钮。
> 家里 Wi-Fi 下若连不上账号服务（路由器 NAT 回环问题），用移动数据即可，压缩功能本身完全离线。

## 构建

环境：JDK 17+、Android SDK（platform 35 + build-tools）、`local.properties` 指向 SDK 路径（不入库，格式 `sdk.dir=E:/path/to/android-sdk`）。

```bash
./gradlew assembleDebug     # 调试包
./gradlew assembleRelease   # 发布包（产物 app/build/outputs/apk/release/）
./gradlew test              # 单测，含 golden_bitrate.csv 全量比对（540 行）
```

### 正式签名

仓库不带签名密钥。在本目录建 `keystore.properties`（已被 .gitignore 排除，**注意异地备份**，丢失后无法同签名升级，只能卸载重装）：

```properties
storeFile=keystore/videodelite.keystore
storePassword=***
keyAlias=videodelite
keyPassword=***
```

生成密钥库：

```bash
keytool -genkeypair -v -keystore keystore/videodelite.keystore \
  -alias videodelite -keyalg RSA -keysize 2048 -validity 10000
```

没有 `keystore.properties` 时 `assembleRelease` 自动退回 debug 签名（仅便于本地起跑，不可作分发）。

### 调试辅助与"打开方式"入队

App 注册了 `ACTION_VIEW video/*`：系统文件管理器里对任意视频选「打开方式 → VideoDelite」即直接入队压缩（Simple 模式参数，免登录，与桌面版拖拽导入对等）。自动化测试同样走这条通道：

```bash
adb shell am start -a android.intent.action.VIEW --grant-read-uri-permission \
  -d <video content uri> -t video/mp4 -n com.videodelite.app/.MainActivity
```

另有一个仅 `BuildConfig.DEBUG` 生效的入队广播接收器（`com.videodelite.DEBUG_ENQUEUE`，部分模拟器的广播队列不可靠，优先用上面的 intent 通道）。Release 构建里广播接收器不存在，VIEW 通道保留。

## 目录结构

```
app/src/main/java/com/videodelite/app/
  core/     码率表移植（Bitrate.kt）+ 展示格式化
  media/    Analyzer（MediaExtractor 探测）、CompressEngine（Transformer 封装）、
            TaskManager（并行 ≤3）、ExportService（前台服务 + 进度通知）、OutputSaver
  data/     Room 历史、DataStore 设置、EncryptedSharedPreferences 令牌、设备 installationId
  network/  Retrofit API + 401 单飞刷新轮换 + 账号/设备/授权客户端
  ui/       Compose 五页（首页/任务/历史/设置/账号）+ Apple 风格主题
```

- minSdk 26（Android 8.0）/ target 35；release APK 约 2.7MB（R8 + 资源收缩）。
- 输出保存到相册 `Movies/VideoDelite`（Android 10+ 无需权限；8–9 需要 WRITE_EXTERNAL_STORAGE）。
