# Remote Door Control - Android App

ESP32 远程门禁控制 Android 客户端，通过 MQTT 对接固件，支持视频监控、卷帘门/玻璃门/灯控。

## 功能

- MQTT 登录（TCP 1883，与 ESP32 固件一致）
- 摄像头视频流订阅（`esp32/camera/video`）
- 视频开关控制（`esp32/camera/control`，发送 `on`/`off`）
- 卷帘门 / 玻璃门 / 灯控（与固件 topic 完全一致）
- 60 秒自动关闭视频流
- 凭据本地保存

## 项目结构

```
android/
├── app/src/main/java/com/zheng/remotedoor/
│   ├── LoginActivity.kt          # 登录页
│   ├── MainActivity.kt           # 主界面（底部导航）
│   ├── mqtt/MqttManager.kt       # MQTT 核心逻辑
│   └── ui/                       # 监控 / 门禁 / 设置
└── ...
.github/workflows/android-build.yml  # 云端自动打包
```

## 无需本地 Android 环境 — 使用 GitHub Actions 打包

### 1. 创建 GitHub 仓库

在 GitHub 上新建仓库，例如 `remote-door-control`。

> **安全提示**：GitHub 已不支持用账号密码推送代码，请使用 **Personal Access Token (PAT)**，切勿将密码写入代码或聊天记录。

### 2. 生成 Personal Access Token

1. 登录 GitHub → Settings → Developer settings → Personal access tokens
2. 生成 Token，勾选 `repo` 权限
3. 复制 Token（只显示一次）

### 3. 推送代码到 GitHub

在项目根目录执行：

```bash
git init
git add .
git commit -m "Add Android app with GitHub Actions CI"
git branch -M main
git remote add origin https://github.com/Zheng202201/remote-door-control.git
git push -u origin main
```

推送时：
- 用户名：`Zheng202201`
- 密码：粘贴你的 **PAT**（不是 GitHub 登录密码）

### 4. 下载 APK

推送成功后：

1. 打开仓库 → **Actions** 标签
2. 选择 **Build Android APK** workflow
3. 等待构建完成（约 5–10 分钟）
4. 在 Artifacts 中下载 `remote-door-debug-apk`

也可手动触发：Actions → Build Android APK → **Run workflow**

### 5. 安装到手机

1. 将 `app-debug.apk` 传到手机
2. 允许「未知来源」安装
3. 安装并打开 App

## 登录配置

| 字段 | 默认值 | 说明 |
|------|--------|------|
| 服务器 | `47.122.129.16` | MQTT Broker |
| 端口 | `1883` | TCP 端口（非 Web 的 9001） |
| 用户名 | 你的 MQTT 账号 | 与 Broker 配置一致 |
| 密码 | 你的 MQTT 密码 | 与 Broker 配置一致 |

## MQTT 主题（与 ESP32 固件一致）

| 主题 | 方向 | 说明 |
|------|------|------|
| `esp32/camera/control` | App → 设备 | `on` / `off` |
| `esp32/camera/video` | 设备 → App | JPEG 帧 |
| `esp32/door/control` | App → 设备 | 门控命令 |
| `esp32/light/control` | App → 设备 | 灯控命令 |

门控命令：`iron_up` `iron_down` `iron_stop` `glass_open` `glass_close` `glass_in_out` `glass_only_out`  
灯控命令：`light_left` `light_right`

## 本地开发（可选）

若以后安装 Android Studio：

1. 用 Android Studio 打开 `android/` 目录
2. 同步 Gradle
3. Run 到设备或模拟器

## 安全建议

- 立即修改已在聊天中暴露的 GitHub 密码
- 生产环境建议为 MQTT 启用 TLS
- 不要在代码中硬编码真实密码
