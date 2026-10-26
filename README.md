# VirtualCam Manager (richkobe) — 纯 Magisk 全局虚拟摄像头

用 Magisk/Zygisk 模块在系统层替换摄像头画面，配一个管理 APK 做开关与素材管理。**不依赖 LSPosed，不需要在框架管理器里逐个勾选应用作用域。**

本仓库 fork 自 [smithluke874/Android-VirtualCam-Manager](https://github.com/smithluke874/Android-VirtualCam-Manager) v2.1.0，主要变化：升级 ShadowHook 以支持 Android 16、整条工具链升到 API 36、补齐 release 签名与产物校验。详见 [CHANGELOG.md](CHANGELOG.md)。

## 简介

模块在 zygote 阶段注入，对全系统应用生效；hook 的是 Camera1 的预览 surface 与 GLES 纹理绑定/绘制路径，把应用拿到的画面换成你指定的视频或图片。管理 APK 提供一键开关、素材导入、以及故障时显示状态码的 Doctor 卡片。

- 全局生效，无需勾选目标应用
- 管理 APK 内一键开关，随时启停
- 双视频路径：Camera1 surface swap 优先，GL/OES 作兜底
- native 层按 16 KB page size 编译（Android 15+ 要求）
- ShadowHook 2.0.1，官方声明兼容至 Android 17

## 当前状态（v2.2.0）

| 层级 | 状态 |
|---|---|
| Magisk 路径与开关标志 | 可用 |
| APK 一键启用 / 素材导入 | 可用 |
| 双视频路径（Camera1 + `/data/adb/virtualcam`） | 可用 |
| Zygisk `.so` 多 ABI + 16 KB page size | CI 构建通过 |
| Camera1 `setPreviewTexture` / `setPreviewDisplay` 替换 | 代码就位，待真机验证 |
| 被接管 surface 上的 MediaPlayer 循环播放 | 代码就位，待真机验证 |
| ShadowHook 暂存进应用 `code_cache` | 可用 |
| 状态文件镜像到 `DCIM/Camera1/.vcam_*` | 可用 |
| ShadowHook `glBindTexture` + draw | 兜底路径 |
| AMediaCodec + OES / 2D 上传 | 兜底路径 |
| Camera2 session surface 重写 | 未实现 |
| **真机上应用显示替换画面** | **未验证** |

## 下载与安装

本仓库**不创建公开 Release**（上游无 LICENSE 文件，取得授权前不公开发布）。产物从 Actions 构建记录获取，需登录 GitHub 账号。

| 平台 | 文件 | 安装方式 |
|---|---|---|
| Android（通用包） | `virtualcam-manager-2.2.0-android-universal.apk` | 传到手机点击安装，需允许「安装未知应用」 |
| Android（调试版） | `virtualcam-manager-2.2.0-android-universal-debug.apk` | 同上，带调试信息 |
| Magisk 模块 | `VirtualCam-Manager-Magisk-v2.2.0.zip` | Magisk/KernelSU → Modules → 从存储安装 → **重启** |

APK 命名里的 ABI 段由 CI 从产物内部实测得出（本包为 `universal`，含 arm64-v8a / armeabi-v7a / x86 / x86_64 四个 ABI 的 androidx 依赖库）；Magisk 模块的 zygisk 二进制则单独提供 arm64-v8a、armeabi-v7a、x86_64 三种。另附 `SHA256SUMS.txt` 供校验下载完整性。

下载路径：本仓库 **Actions** 页 → 最新一次绿色成功的 `Build Signed APK + Magisk Module` → 页面底部 **Artifacts**。

安装顺序（三步）：

1. Magisk/KernelSU 设置中确认 **Zygisk 已启用**（KernelSU 环境需装 zygisksu 或 ReZygisk）→ Modules → 从存储安装模块 zip → **重启**；
2. 安装管理 APK → 打开 → 在 Magisk 弹窗中**允许 root**；
3. 在 **Home** 页：点 **Pick video or image** 选素材 → 打开 **VirtualCam** 开关 → 先打开一个 **Camera1** 应用（Open Camera、多数系统自带相机）→ 观察状态从 `texture_swapped` 变为 `surface_playing`。

系统要求：Android 8.0（API 26）及以上、arm64 设备、已解锁 bootloader 并 root、Zygisk 可用。

## 使用说明

v2.2 沿用上游的优先路径：

1. 应用调用 `Camera.setPreviewTexture(appTexture)`；
2. 模块给真实摄像头塞一个**空壳** SurfaceTexture；
3. `MediaPlayer` 把你的 `virtual.mp4` 循环播放到**应用**那个 surface 上。

因此：**从不触碰 `android.hardware.Camera` 的 Camera2 / CameraX 应用仍会显示真实画面**，Camera2 session 重写路径尚未实现。

### 状态码含义

| 状态 | 含义 |
|---|---|
| `gate_off` / `no_video` | 控制面关闭（未开开关或没选素材） |
| `hooks_installing` / `hooks_ready` | JNI 与 GL 两路 hook 均已尝试 |
| `jni_hooks_ready` / `jni_camera_patched` | RegisterNatives 拦截已生效 |
| `texture_swapped` / `display_swapped` | 已给 Camera1 塞入空壳 surface |
| `surface_playing` | MediaPlayer 正在应用 surface 上播放 |
| `surface_player_fail` | 替换成功但播放失败 |
| `gl_bind_redir:tex#N` | GL 纹理绑定重定向命中 |
| `oes_ready` / `oes_fallback_2d` | GL 上传路径 |
| `*_pending_shadowhook` | ShadowHook 未加载成功，hook 未安装 |

状态文件位于设备 `DCIM/Camera1/.vcam_*`，管理 APK 的 Doctor 卡片会读取并显示。

## 从源码构建

本机需要：JDK 17（AGP 8.13 要求）、Android SDK（platform 36、build-tools 36.0.0）、NDK r27、cmake 3.22.1。

```bash
# manager-app（Kotlin + Compose，不需要 NDK）
cd manager-app
./gradlew assembleRelease
```

```bash
# zygisk-native（C++，需要 NDK 与 cmake）
cd zygisk-native
cmake -B build-arm64 -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-26 \
  -DANDROID_STL=c++_static -DCMAKE_BUILD_TYPE=Release
cmake --build build-arm64
```

签名：把密钥信息写进 `manager-app/keystore.properties`（该文件已被 `.gitignore` 挡住，**不要提交**），格式为 `storeFile` / `storePassword` / `keyAlias` / `keyPassword` 四行。缺失该文件时构建自动回退为未签名产物。CI 侧由仓库 secret（`VCAM_KEYSTORE_BASE64` 等）临时生成同名文件。

仓库路径含中文等非 ASCII 字符时，AGP 会拒绝构建（b.android.com/95744），已在 `gradle.properties` 中设 `android.overridePathCheck=true` 绕过。

## 项目结构

| 目录 | 内容 |
|---|---|
| `manager-app/` | 管理 APK，Kotlin + Jetpack Compose |
| `zygisk-native/` | Zygisk 模块的 C++ 源码：`main.cpp`（注入入口）、`gl_hooks.cpp`（GLES hook）、`jni_camera_hooks.cpp`（JNI RegisterNatives 拦截） |
| `magisk-module/` | Magisk 刷机 zip 骨架：`module.prop`、`customize.sh`、`service.sh`、`zygisk/`（存放编译产物） |
| `.github/workflows/` | CI：构建签名 APK + 编译 native + 打包模块 zip |
| `docs/` | 设计说明与上游 hook 算法笔记 |
| `patches/` | 上游跨会话搬运源码的 base64 分块残留，**不参与构建** |
| `hook-java/` | 上游 Java hook 参考实现说明 |

## 常见问题

**刷了模块但没生效？** 先确认 Zygisk 已启用且已重启；再看 `DCIM/Camera1/.vcam_*` 状态文件，停在 `*_pending_shadowhook` 说明 hook 库没加载成功。

**用的是 Camera2 应用（如多数新款相机、扫码类应用）？** 当前不生效，Camera2 session 重写路径未实现。先用 Open Camera 这类 Camera1 应用验证模块本身是否工作。

**Camera1 应用的预览帧回调（NV21）没被替换？** `zygisk-native/jni/nv21_hooks.cpp` 在上游就未被列入 `CMakeLists.txt` 编译，属死代码，本版按最小改动未接入。

## 已知限制

- **本版未在真机验证**：构建与签名校验通过 CI 完成，但模块刷入、Zygisk 加载、hook 生效、替换画面是否出现在真实应用中均未实测。上游作者自身的 "Device smoke test" 也仍是待办。
- **不做公开 Release**：上游仓库无 LICENSE 文件（GitHub API `license: null`），默认保留所有权利。取得授权前仅提供自用构建产物。
- **对自定义 native 相机管线、ARCore、厂商私有相机 SDK 的应用可能无效**。
- **合规**：仅可用于自有设备的测试、直播推流、隐私保护等用途。禁止用于绕过身份核验、活体检测或任何欺骗性场景，后果自负。

## 许可证

本仓库为 fork，上游 [smithluke874/Android-VirtualCam-Manager](https://github.com/smithluke874/Android-VirtualCam-Manager) **未提供 LICENSE 文件**，故本仓库同样不声明许可证。在上游授权明确前，代码权利保留归原作者，本仓库改动仅限自用。

第三方组件许可证（均经 GitHub API 实测核对）：ShadowHook（bytedance/android-inline-hook，MIT）、libsu（topjohnwu/libsu，Apache-2.0）、Jetpack Compose 与 AndroidX（Apache-2.0）。
