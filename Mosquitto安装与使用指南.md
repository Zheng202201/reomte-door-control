# Mosquitto MQTT 安装与使用指南

本文档说明如何在服务器上安装、配置和管理 **Eclipse Mosquitto** MQTT Broker，并结合本项目 **remote_door_control_v03**（ESP32 门禁 + Android App）给出实际操作示例。

> **适用项目**：`remote_door_control_v03`  
> **当前 Broker 地址**：`47.122.129.16:1883`  
> **协议版本**：MQTT 3.1.1

---

## 一、MQTT 与 Mosquitto 简介

### 1. MQTT 是什么

MQTT（Message Queuing Telemetry Transport）是一种轻量级发布/订阅消息协议，适合物联网场景：

- **发布者（Publisher）**：向某个主题（Topic）发送消息
- **订阅者（Subscriber）**：订阅主题并接收消息
- **Broker（代理服务器）**：负责转发消息，本项目使用 **Mosquitto**

### 2. 本项目中的角色

| 设备/程序 | 角色 | 说明 |
|-----------|------|------|
| ESP32 | 发布 + 订阅 | 发布视频帧；订阅门控、灯控、视频开关指令 |
| Android App | 发布 + 订阅 | 订阅视频流；发送控制指令 |
| Mosquitto 服务器 | Broker | 中转所有 MQTT 消息 |

### 3. 本项目 MQTT 主题一览

| 主题 | 方向 | 说明 |
|------|------|------|
| `esp32/camera/video` | ESP32 → App | JPEG 视频帧（二进制） |
| `esp32/camera/control` | App → ESP32 | 视频流开关：`on` / `off` |
| `esp32/camera/status` | ESP32 → App | 摄像头状态：`on` / `off` / `connected` |
| `esp32/door/control` | App → ESP32 | 门控指令：`iron_up`、`glass_open` 等 |
| `esp32/light/control` | App → ESP32 | 灯控指令：`light_left`、`light_right` |

### 4. 本项目默认账号（示例）

> 以下账号在固件与 App 中使用，生产环境请按需修改并同步更新三端配置。

| 用途 | 用户名 | 密码 |
|------|--------|------|
| ESP32 设备 | `esp32-002` | `esp32-device-002` |
| Android App | 登录时填写 | 登录时填写 |

---

## 二、在 Linux 服务器上安装 Mosquitto

以下以 **Ubuntu 22.04 / Debian** 为例（云服务器常见系统）。

### 1. 更新软件源并安装

```bash
sudo apt update
sudo apt install -y mosquitto mosquitto-clients
```

安装内容说明：

- `mosquitto`：Broker 服务本体
- `mosquitto-clients`：命令行工具 `mosquitto_pub`、`mosquitto_sub`

### 2. 查看版本与服务状态

```bash
mosquitto -h | head -1
sudo systemctl status mosquitto
```

正常时应显示 `active (running)`。

### 3. 设置开机自启

```bash
sudo systemctl enable mosquitto
sudo systemctl start mosquitto
```

---

## 三、基础配置

### 1. 主要配置文件路径

| 文件 | 说明 |
|------|------|
| `/etc/mosquitto/mosquitto.conf` | 主配置 |
| `/etc/mosquitto/conf.d/` | 额外配置片段（推荐放这里） |
| `/etc/mosquitto/passwd` | 用户名密码文件 |
| `/var/log/mosquitto/mosquitto.log` | 日志文件 |

### 2. 创建项目专用配置

```bash
sudo nano /etc/mosquitto/conf.d/remote-door.conf
```

写入以下内容（可按需调整）：

```conf
# 监听端口（默认 1883）
listener 1883
protocol mqtt

# 允许匿名连接（开发阶段可开，生产环境建议关闭）
# allow_anonymous true

# 使用密码文件认证（推荐）
allow_anonymous false
password_file /etc/mosquitto/passwd

# 持久化（保留离线消息等）
persistence true
persistence_location /var/lib/mosquitto/

# 日志
log_dest file /var/log/mosquitto/mosquitto.log
log_type error
log_type warning
log_type notice
log_type information

# 连接保活（与 ESP32 / App 的 keepAlive=30 对应）
max_keepalive 120
```

### 3. 创建 MQTT 用户

```bash
# 创建密码文件并添加 ESP32 用户（-c 仅第一次使用）
sudo mosquitto_passwd -c /etc/mosquitto/passwd esp32-002

# 添加 Android / 其他客户端用户
sudo mosquitto_passwd /etc/mosquitto/passwd android-user
```

按提示输入密码。ESP32 固件中当前密码为 `esp32-device-002`，请保持一致。

### 4. 设置权限并重启

```bash
sudo chown mosquitto:mosquitto /etc/mosquitto/passwd
sudo chmod 640 /etc/mosquitto/passwd

sudo mosquitto -c /etc/mosquitto/mosquitto.conf -v
# 若无报错，Ctrl+C 退出后执行：

sudo systemctl restart mosquitto
sudo systemctl status mosquitto
```

---

## 四、防火墙与安全组

### 1. 云服务器安全组

若使用阿里云 / 腾讯云等，需在**安全组**中放行：

| 端口 | 协议 | 说明 |
|------|------|------|
| 1883 | TCP | MQTT 明文端口（本项目当前使用） |
| 8883 | TCP | MQTT over TLS（可选，更安全） |

### 2. Linux 防火墙（ufw）

```bash
sudo ufw allow 1883/tcp
sudo ufw reload
sudo ufw status
```

### 3. 安全建议

- 生产环境务必关闭 `allow_anonymous true`
- 为每个设备/客户端使用独立账号
- 有条件时启用 TLS（端口 8883）
- 不要将 MQTT 端口暴露给公网而不设密码

---

## 五、常用服务管理命令

```bash
# 启动
sudo systemctl start mosquitto

# 停止
sudo systemctl stop mosquitto

# 重启（修改配置后）
sudo systemctl restart mosquitto

# 查看状态
sudo systemctl status mosquitto

# 查看实时日志
sudo tail -f /var/log/mosquitto/mosquitto.log
```

---

## 六、命令行测试（mosquitto_pub / mosquitto_sub）

将 `47.122.129.16` 替换为你的服务器 IP。

### 1. 订阅视频主题（观察是否有数据）

```bash
mosquitto_sub -h 47.122.129.16 -p 1883 \
  -u esp32-002 -P esp32-device-002 \
  -t "esp32/camera/status" -v
```

### 2. 发送视频开关指令

```bash
# 开启视频流
mosquitto_pub -h 47.122.129.16 -p 1883 \
  -u android-user -P 你的密码 \
  -t "esp32/camera/control" -m "on"

# 关闭视频流
mosquitto_pub -h 47.122.129.16 -p 1883 \
  -u android-user -P 你的密码 \
  -t "esp32/camera/control" -m "off"
```

### 3. 发送门控指令

```bash
mosquitto_pub -h 47.122.129.16 -p 1883 \
  -u android-user -P 你的密码 \
  -t "esp32/door/control" -m "iron_up"
```

### 4. 发送灯控指令

```bash
mosquitto_pub -h 47.122.129.16 -p 1883 \
  -u android-user -P 你的密码 \
  -t "esp32/light/control" -m "light_left"
```

### 5. 本地测试（在服务器本机）

```bash
mosquitto_sub -h 127.0.0.1 -p 1883 -u esp32-002 -P esp32-device-002 -t "#" -v
```

---

## 七、与本项目的对接检查清单

部署或排查时，按以下顺序检查：

### 1. Broker 是否运行

```bash
sudo systemctl status mosquitto
ss -tlnp | grep 1883
```

### 2. ESP32 固件配置

文件：`remote_door_control_v03/remote_door_control_v03.ino`

确认以下项与服务器一致：

```cpp
const char* mqtt_server = "47.122.129.16";
const int mqtt_port = 1883;
const char* mqtt_user = "esp32-002";
const char* mqtt_password = "esp32-device-002";
```

### 3. Android App 配置

文件：`android/app/src/main/java/com/zheng/remotedoor/MqttConfig.kt`

默认服务器：

```kotlin
const val DEFAULT_HOST = "47.122.129.16"
const val DEFAULT_PORT = 1883
```

登录页填写与 Mosquitto 密码文件中一致的账号密码。

### 4. 三端主题必须一致

| 检查项 | ESP32 | Android |
|--------|-------|---------|
| 视频主题 | `esp32/camera/video` | `esp32/camera/video` |
| 视频控制 | `esp32/camera/control` | `esp32/camera/control` |
| 门控 | `esp32/door/control` | `esp32/door/control` |
| 灯控 | `esp32/light/control` | `esp32/light/control` |

> 注意：旧版 Web 页可能使用 `esp32-002/camera/control` 格式，与当前固件不一致时需统一修改。

---

## 八、Windows 本地安装（可选，用于开发调试）

若想在 Windows 本机测试 MQTT：

### 1. 下载安装

1. 访问：https://mosquitto.org/download/
2. 下载 Windows 安装包并安装
3. 默认路径示例：`C:\Program Files\mosquitto\`

### 2. 启动 Broker

以管理员身份打开 CMD：

```cmd
cd "C:\Program Files\mosquitto"
mosquitto -c mosquitto.conf -v
```

### 3. 测试

另开一个 CMD：

```cmd
mosquitto_sub -h 127.0.0.1 -t "test/topic" -v
mosquitto_pub -h 127.0.0.1 -t "test/topic" -m "hello"
```

---

## 九、常见问题排查

### 1. 客户端连接失败 / Connection refused

| 可能原因 | 处理方法 |
|----------|----------|
| Mosquitto 未启动 | `sudo systemctl start mosquitto` |
| 安全组未放行 1883 | 在云控制台开放端口 |
| 防火墙拦截 | `sudo ufw allow 1883/tcp` |
| IP 或端口填错 | 核对三端配置 |

### 2. 认证失败 / Not authorized

| 可能原因 | 处理方法 |
|----------|----------|
| 用户名密码错误 | 重新 `mosquitto_passwd` 设置 |
| 未启用密码文件 | 检查 `password_file` 配置 |
| 仍允许匿名但客户端带了错误密码 | 统一认证策略 |

```bash
# 验证用户是否存在
sudo cat /etc/mosquitto/passwd
```

### 3. ESP32 连上但 App 收不到视频

| 可能原因 | 处理方法 |
|----------|----------|
| 未发送 `on` 到 `esp32/camera/control` | App 或命令行先发 `on` |
| 主题名不一致 | 对照第七节表格 |
| 视频帧过大 | 检查 ESP32 串口日志与网络 |

### 4. 连接频繁断开

- 检查 `keepAlive` 设置（本项目为 30 秒）
- 查看 Broker 日志：`sudo tail -f /var/log/mosquitto/mosquitto.log`
- 检查服务器内存与网络稳定性

### 5. 修改配置后不生效

```bash
# 检查配置语法
sudo mosquitto -c /etc/mosquitto/mosquitto.conf -v

# 重启服务
sudo systemctl restart mosquitto
```

---

## 十、进阶：启用 TLS 加密（可选）

生产环境建议启用 MQTT over TLS（端口 8883）。

### 1. 准备证书

可使用 Let's Encrypt 免费证书，或自签名证书（仅测试用）。

### 2. 配置示例

在 `/etc/mosquitto/conf.d/remote-door.conf` 追加：

```conf
listener 8883
protocol mqtt
cafile /etc/mosquitto/certs/ca.crt
certfile /etc/mosquitto/certs/server.crt
keyfile /etc/mosquitto/certs/server.key
```

### 3. 客户端调整

- ESP32：需使用 `WiFiClientSecure` 并配置 CA 证书
- Android：MqttManager 需改为 SSL 连接

> 启用 TLS 后，ESP32 固件与 Android 代码均需同步修改，建议有一定经验后再操作。

---

## 十一、常用命令速查表

| 操作 | 命令 |
|------|------|
| 安装 | `sudo apt install -y mosquitto mosquitto-clients` |
| 启动服务 | `sudo systemctl start mosquitto` |
| 重启服务 | `sudo systemctl restart mosquitto` |
| 查看状态 | `sudo systemctl status mosquitto` |
| 查看日志 | `sudo tail -f /var/log/mosquitto/mosquitto.log` |
| 添加用户 | `sudo mosquitto_passwd /etc/mosquitto/passwd 用户名` |
| 订阅主题 | `mosquitto_sub -h IP -u 用户 -P 密码 -t "主题" -v` |
| 发布消息 | `mosquitto_pub -h IP -u 用户 -P 密码 -t "主题" -m "内容"` |
| 检查端口 | `ss -tlnp \| grep 1883` |

---

## 十二、相关文件索引（本项目）

| 文件 | 说明 |
|------|------|
| `remote_door_control_v03/remote_door_control_v03.ino` | ESP32 MQTT 连接与主题 |
| `android/.../MqttConfig.kt` | Android 默认服务器与主题 |
| `android/.../mqtt/MqttManager.kt` | Android MQTT 连接逻辑 |
| `web/esp32-02.html` | Web 控制页（MQTT 配置需与固件一致） |

---

*文档版本：v1.0 | 适用于 remote_door_control_v03 项目*
