# 更新日志 / Changelog

本仓库 fork 自 [smithluke874/Android-VirtualCam-Manager](https://github.com/smithluke874/Android-VirtualCam-Manager)，在其 v2.1.0 基础上做组件升级与构建链修复。版本号沿用上游语义化版本，本仓库自建版本从 v2.2.0 起。

产物形态有两件：管理 APK（`virtualcam-manager-<版本>-android-universal.apk`）与 Magisk 模块（`VirtualCam-Manager-Magisk-<版本>.zip`）。两者需配套使用，模块提供 hook 能力，APK 提供开关与素材管理。

APK 命名中的 ABI 段由构建脚本从产物内部实测得出，不写死：管理 APK 为纯 Kotlin 工程，其 `lib/` 下的 `.so` 全部来自 androidx 依赖，实测打进 arm64-v8a、armeabi-v7a、x86、x86_64 四个 ABI，故标为 `universal`。Magisk 模块内的 zygisk 二进制则单独编译 arm64-v8a、armeabi-v7a、x86_64 三种。

## v2.2.0

本版核心是修掉一个"装上却不生效"的隐性问题：模块依赖的 inline hook 框架 ShadowHook 被钉在 1.0.10，而该版本官方仅声明兼容到 Android 15，在 Android 16 设备上 hook 会静默失效。同时把整条工具链升到与 Android 16（API 36）匹配的版本，并补齐签名与产物校验。

### 关键修复

- **修复**：ShadowHook 从 1.0.10 升级到 **2.0.1**，以支持 Android 16。根因是 ShadowHook 在运行时由 `dlopen` + `dlsym` 加载，构建期不存在链接依赖，因此版本不兼容不会有任何编译报错，只会在设备上表现为 hook 装不上、状态文件停在 `*_pending_shadowhook`。官方 release notes 明确：v1.0.10 兼容至 Android 15，v2.0.0 起兼容 Android 16，v2.0.1 兼容至 Android 17。
- **修复**：升级前已逐符号核对兼容性——代码使用的 `shadowhook_init`、`shadowhook_hook_sym_name`、`shadowhook_unhook`、`shadowhook_get_errno`、`shadowhook_to_errmsg` 五个符号在 2.0.1 的 arm64 `libshadowhook.so` 中均为真实导出符号（`llvm-nm -D` 实测为 `T`）；`shadowhook_init(mode, debuggable)` 签名未变；两处调用传入的 `mode=1` 在 1.x 与 2.x 中同为 `SHADOWHOOK_MODE_UNIQUE`（2.x 仅新增 `MULTI=2`）。故本次升级无需改动 C++ 代码。
- **修复**：release APK 此前未签名。上游 CI 的 `upload-artifact` 用 `apk/release/*.apk` 通配收集产物，而 `build.gradle.kts` 的 release 块没有 `signingConfig`，AGP 实际产出 `app-release-unsigned.apk`——文件名看着像正式包，装机时会被系统以缺少证书拒绝。现已加入 `signingConfigs.release`，密钥从 `keystore.properties` 读取（该文件不入库），并在 CI 中用 `apksigner verify` + `zipalign -c` 断言签名真实有效后才允许上传。
- **修复**：ShadowHook AAR 下载失败时此前只打印警告并继续打包，产出的模块缺少 `libshadowhook.so`，刷入后 hook 必然失效却无从察觉。现改为下载失败即 CI 失败，并增加关键符号断言，防止未来 ShadowHook 改符号名导致的运行时静默失败。
- **修复**：Magisk 模块此前未校验 arm64 的 zygisk 二进制是否真的编译产出。现增加断言，`magisk-module/zygisk/arm64-v8a.so` 缺失即 CI 失败。
- **修复**：模块脚本行尾导致的刷机失败。在 Windows（`core.autocrlf=true`）本机打包时，工作区的 Magisk 脚本被转成 CRLF，`META-INF/com/google/android/update-binary` 的 shebang 变成 `#!/sbin/sh\r`——recovery 按 `/sbin/sh\r` 找解释器会直接失败，而它是刷机执行的第一个脚本。上游仓库的 blob 本身是 LF、CI 在 ubuntu 上不受影响，问题只在 Windows 本机打包路径。现新增 `.gitattributes`，将 `*.sh`、`update-binary`、`updater-script`、`module.prop` 锁定为 `eol=lf`，二进制文件（`.so`/`.apk`/`.zip`/`.jks`）标记 `binary` 禁止转换，不再依赖各人的 autocrlf 配置。

### 组件升级

- **优化**：`compileSdk` / `targetSdk` 35 → **36**，与 Android 16 对齐。
- **优化**：Android Gradle Plugin 8.7.2 → **8.13.2**（8.x 末版，支持 compileSdk 36；未跨 9.x，避免大版本破坏性变更）。
- **优化**：Kotlin 与 Compose Compiler 插件 2.0.21 → **2.1.21**。
- **优化**：Gradle Wrapper 8.9 → **8.14.3**（AGP 8.13 要求 Gradle 8.13 及以上）。
- **优化**：Compose BOM 2024.10.01 → **2025.09.01**，统一管理 `compose.*` 各模块版本。
- **优化**：`androidx.core:core-ktx` 1.15.0 → **1.17.0**；`lifecycle-runtime-ktx` 2.8.7 → **2.9.4**；`activity-compose` 1.9.3 → **1.10.1**；`navigation-compose` 2.8.3 → **2.9.8**。
- **优化**：CI 的 NDK 从 r26b → **r27c**，与本机 NDK 27.0.12077973 同代，且对 16 KB page size 支持更完善（native 层已带 `-Wl,-z,max-page-size=16384`，这是 Android 15+ 的要求）。
- **保持**：`libsu` 维持 5.2.2。jitpack 上已有 6.0.0，但存在 API 破坏性变更，本项目未用到其新特性，不冒险升级。

### 构建与工程

- **新增**：`.gitignore`，挡住 `local.properties`、构建产物、以及任何 `*.jks` / `*.keystore` / `keystore.properties`（签名密钥红线，绝不入库）。
- **新增**：`gradle.properties` 加入 `android.overridePathCheck=true`。仓库位于含中文字符的路径时 AGP 会直接拒绝构建（b.android.com/95744），关掉该前置检查后可在中文目录下构建，CI 使用英文路径不受影响。
- **优化**：CI 产物按统一命名规范输出——`virtualcam-manager-<版本>-android-<ABI>.apk`、`-debug.apk`，版本号从 `build.gradle.kts` 自动读取，Magisk zip 版本从 `module.prop` 自动读取，避免文件名与实际版本不一致。其中 `<ABI>` 段由脚本从产物内部 `lib/` 目录实测得出（多 ABI 归为 `universal`），不再照抄模板写死 `arm64`——写死会让文件名与实际打进的 ABI 集合不符。同时输出 `SHA256SUMS.txt` 供校验下载完整性。
- **优化**：未配置签名 secret 时，CI 会明确打印 `WARN: ... build will be UNSIGNED` 并跳过签名校验步骤，不再产出"看着像正式包"的未签名产物。

### 安装说明

1. **刷入 Magisk 模块**：Magisk/KernelSU 设置中确认 **Zygisk 已启用**（KernelSU 环境需装 zygisksu/ReZygisk），Modules → 从存储安装 `VirtualCam-Manager-Magisk-v2.2.0.zip` → **重启**。
2. **安装管理 APK**：将 `virtualcam-manager-2.2.0-android-universal.apk` 传到手机安装（需允许"安装未知应用"），首次打开时按提示授予 root 权限。
3. **系统要求**：Android 8.0（API 26）及以上，arm64 设备；已解锁 bootloader 并 root；Zygisk 可用。
4. **使用**：在管理 APK 中导入替换素材并打开开关，随后在目标应用中打开相机即可看到替换画面。运行状态写入 `DCIM/Camera1/.vcam_*`，故障时管理 APK 的 Doctor 卡片会显示状态码。

### 已知限制

- **本版未在真机验证**。构建与签名校验通过 CI 完成，但模块刷入、Zygisk 加载、hook 实际生效、替换画面是否出现均未在设备（一加 Ace 5 Pro / ColorOS / Android 16 / KernelSU）上实测。上游作者自身的 "Device smoke test" 也仍是待办项，该机型未被任何人验证过。
- **Camera1 的 NV21 预览帧替换路径不生效**。`zygisk-native/jni/nv21_hooks.cpp` 与 `nv21_hooks.h` 存在，但未被 `CMakeLists.txt` 的 `add_library` 列入编译，属上游遗留死代码。本版按最小改动原则未接入，Camera1 场景走的是 surface swap 路径。
- **本仓库不做公开发布**。上游 `smithluke874/Android-VirtualCam-Manager` 无 LICENSE 文件（GitHub API `license: null`），默认保留所有权利。在取得作者授权前，本仓库仅提供自用构建产物，不创建公开 Release。
- **`patches/` 目录下的 90+ 个 base64 分块文件是上游跨会话搬运源码的临时载体**，当前不参与构建（CI 首步 `Verify manager sources (no patch assemble)` 即为确认此点）。本版未清理，以免误伤。
- **替换范围取决于 hook 覆盖面**。当前实现 hook 的是 GLES 纹理绑定/绘制与 JNI 的 `RegisterNatives` 路径，对使用自定义 native 相机管线、ARCore 或厂商私有相机 SDK 的应用可能无效。
- **合规提醒**：仅可用于自有设备的测试、直播推流与隐私保护等用途。禁止用于绕过身份核验、活体检测或任何欺骗性场景。
