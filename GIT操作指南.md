# Git Bash 操作指南

本文档说明：每次修改项目代码后，如何在 **Git Bash** 中把改动推送到 GitHub，并触发云端自动打包 APK。

> **适用项目**：`remote_door_control_v03`  
> **GitHub 仓库**：https://github.com/Zheng202201/reomte-door-control  
> **你的账号**：`Zheng202201`

---

## 一、准备工作（只需做一次）

### 1. 安装 Git Bash

若已能打开 Git Bash 并执行 `git` 命令，可跳过此步。

### 2. 生成 GitHub PAT（个人访问令牌）

Git 推送**不能**使用 GitHub 登录密码，必须使用 **PAT**。

1. 打开：https://github.com/settings/tokens  
2. 点击 **Generate new token** → **Generate new token (classic)**  
3. 勾选权限：
   - ✅ **repo**（推送代码）
   - ✅ **workflow**（更新 GitHub Actions 配置，首次推送 workflow 必需）
4. 生成后复制 Token（形如 `ghp_xxxxxxxx...`），**只显示一次**，请保存到安全位置

### 3. 配置 Git 凭据缓存（推荐，只需一次）

在 Git Bash 执行：

```bash
git config --global credential.helper manager
```

之后第一次输入 PAT 后，Windows 会记住，不必每次重复输入。

### 4. 首次克隆或关联仓库（若尚未配置）

若本地项目还未关联远程仓库，在项目根目录执行：

```bash
cd /h/learn/ESP32/ArdunoIDE_Projects/remote_door_control_v03
git init
git remote add origin https://github.com/Zheng202201/reomte-door-control.git
git branch -M main
```

> 若已能正常 `git push`，说明这步已完成，无需重复。

---

## 二、每次修改代码后的标准流程（最常用）

每次在 Cursor / 编辑器中改完代码，按下面 **4 步** 执行即可。

### 第 1 步：进入项目目录

```bash
cd /h/learn/ESP32/ArdunoIDE_Projects/remote_door_control_v03
```

**说明**：确保当前目录是项目根目录，下面应能看到 `android/`、`web/`、`.github/` 等文件夹。

---

### 第 2 步：查看修改了哪些文件（可选但建议）

```bash
git status
```

**说明**：
- 红色/列出的文件 = 有改动
- 用于确认是否只改了预期文件，避免误提交

查看具体改动内容（可选）：

```bash
git diff
```

---

### 第 3 步：添加并提交改动

```bash
git add .
git commit -m "这里写本次修改的简要说明"
```

**说明**：
- `git add .`：把所有改动加入暂存区
- `git commit -m "..."`：在本地创建一次提交记录
- **commit 信息示例**：
  - `Fix Android CI build`
  - `Add door control buttons`
  - `Update MQTT config`

> 若提示 `nothing to commit`，说明没有新改动，无需推送。

---

### 第 4 步：推送到 GitHub

```bash
git push origin main
```

**说明**：
- 将本地 `main` 分支推送到 GitHub
- 若提示输入凭据：
  - **Username**：`Zheng202201`
  - **Password**：粘贴你的 **PAT**（不是 GitHub 登录密码）

推送成功后，GitHub Actions 会自动开始打包 Android APK（若修改了 `android/` 或 workflow 文件）。

---

## 三、推送后：下载 APK

### 1. 打开 Actions 页面

浏览器访问：

https://github.com/Zheng202201/reomte-door-control/actions

### 2. 查看构建状态

- ✅ 绿色 = 构建成功
- ❌ 红色 = 构建失败（见下方「常见问题」）

### 3. 下载 APK

1. 点击**最新一次成功的构建**
2. 滚动到页面底部 **Artifacts**
3. 下载 **remote-door-debug-apk**
4. 解压得到 `app-debug.apk`，传到手机安装

### 4. 手动触发构建（不修改代码时）

若只想重新打包、没有新代码：

1. 打开 Actions → **Build Android APK**
2. 右侧 **Run workflow** → 选择 `main` → **Run workflow**

---

## 四、常用命令速查

| 命令 | 作用 |
|------|------|
| `git status` | 查看哪些文件被修改 |
| `git diff` | 查看具体改动内容 |
| `git add .` | 暂存所有改动 |
| `git commit -m "说明"` | 提交到本地 |
| `git push origin main` | 推送到 GitHub |
| `git log --oneline -5` | 查看最近 5 次提交 |
| `git pull origin main` | 从 GitHub 拉取最新代码（多人协作时用） |

---

## 五、完整示例（复制即用）

假设你刚改完 Android 代码，可直接执行：

```bash
cd /h/learn/ESP32/ArdunoIDE_Projects/remote_door_control_v03
git status
git add .
git commit -m "Update android app"
git push origin main
```

推送完成后，打开 Actions 页面等待构建，再下载 APK。

---

## 六、常见问题

### 1. 推送时提示 `Connection was reset` / `unable to access`

**错误示例**：
```
fatal: unable to access 'https://github.com/...': Recv failure: Connection was reset
```

**原因**：本地网络访问 GitHub 不稳定（能 ping 通，但 HTTPS 会断）。在国内较常见，与 Git 命令、PAT 无关。

**按顺序尝试**：

#### 方法 A：直接重试（最简单）

过几分钟再执行：

```bash
git push origin main
```

网络有时只是短暂波动，你之前已成功推送过，重试往往有效。

#### 方法 B：优化 Git 网络参数（推荐先试）

在 Git Bash 执行一次即可：

```bash
git config --global http.version HTTP/1.1
git config --global http.postBuffer 524288000
git config --global http.lowSpeedLimit 0
git config --global http.lowSpeedTime 999999
```

然后重试：

```bash
git push origin main
```

#### 方法 C：更换网络

- 手机开热点，电脑连手机 4G/5G 再 push  
- 或换一个时间段（如晚上/凌晨）再试

#### 方法 D：使用代理 / VPN

若你有可用的科学上网工具，在 Git Bash 中临时设置代理（把端口改成你工具的实际端口，常见 `7890`）：

```bash
export http_proxy=http://127.0.0.1:7890
export https_proxy=http://127.0.0.1:7890
git push origin main
```

用完后可取消代理：

```bash
unset http_proxy
unset https_proxy
```

#### 方法 E：改用 SSH 推送（网络好时再配置）

HTTPS 不通时可尝试 SSH，但 SSH 同样受网络影响。配置步骤：

```bash
# 1. 生成 SSH 密钥（一路回车即可）
ssh-keygen -t ed25519 -C "838520625@qq.com"

# 2. 查看公钥并复制
cat ~/.ssh/id_ed25519.pub
```

把公钥添加到 GitHub：https://github.com/settings/keys → **New SSH key**

```bash
# 3. 改用 SSH 地址
cd /h/learn/ESP32/ArdunoIDE_Projects/remote_door_control_v03
git remote set-url origin git@github.com:Zheng202201/reomte-door-control.git
git push origin main
```

---

### 2. 推送时提示 `Authentication failed`

- 确认 Password 填的是 **PAT**，不是 GitHub 登录密码
- PAT 可能已过期，到 https://github.com/settings/tokens 重新生成

### 3. 提示 `refusing to allow a Personal Access Token ... without workflow scope`

- 重新生成 PAT，勾选 **repo** 和 **workflow**
- 清除旧凭据后重试：

```bash
git credential reject <<EOF
protocol=https
host=github.com
EOF
```

然后再次 `git push origin main`，输入新 PAT。

### 4. Actions 构建失败（红色 ✗）

1. 打开失败的那次构建
2. 点击 **build** → 展开 **Build Debug APK**
3. 查看错误日志，或把最后 20–30 行发给协助排查的人

### 5. `git push` 提示 `rejected` / 需要先 pull

若 GitHub 上已有你本地没有的提交：

```bash
git pull origin main --rebase
git push origin main
```

### 6. 提交了不该提交的文件

若误加了敏感文件（如含密码的配置），**不要继续 push**，先联系协助处理；日常勿将 `.env`、真实密码写入仓库。

---

## 七、安全提醒

1. **切勿**将 GitHub 密码、PAT 写入代码或提交到仓库  
2. PAT 等同于密码，泄露后他人可操作你的仓库  
3. 不用时可到 Token 页面 **Delete** 撤销旧 Token  
4. 若密码曾在聊天/邮件中暴露，请尽快修改 GitHub 密码

---

## 八、项目相关链接

| 项目 | 链接 |
|------|------|
| 代码仓库 | https://github.com/Zheng202201/reomte-door-control |
| Actions 构建 | https://github.com/Zheng202201/reomte-door-control/actions |
| 生成 PAT | https://github.com/settings/tokens |
| Android 说明 | 见项目内 `android/README.md` |

---

## 九、流程图（每次改代码）

```
修改代码
   ↓
cd 到项目目录
   ↓
git add .
   ↓
git commit -m "说明"
   ↓
git push origin main
   ↓
打开 Actions 等待构建
   ↓
下载 APK → 安装到手机
```

---

*最后更新：2026-07-09*
