# ColorOS Permission Whitelist Hook

这是一个仅作用于 `com.oplus.securitypermission` 的传统 Xposed API 模块，适配当前目录中分析的 ColorOS `SecurityPermission.apk`。

## 行为

1. Hook `oa.b.b(Bundle, Bundle)`，将每次 `ArraySet` 合并后的 `src_pkg`、`dst_pkg`、`activity`、`action` 转为 `ArrayList.toString()` 形式写入 LSPosed 日志。长日志会拆分，避免 logcat 单行截断。
2. 从所有下发的 `dst_pkg` 中删除：

   - `com.tencent.mobileqq`
   - `com.eg.android.AlipayGphone`

3. Hook `k9.c.b(Bundle)` 作为最终防线，防止远端或其他调用路径绕过 `oa.b.b()`。对包含 `md5` 的最终下发副本追加 `|ColorOSPermissionWhitelistHook:<策略版本>`。system_server 会忽略与当前缓存 md5 相同的预置白名单；该后缀确保过滤后的 Bundle 至少被接收一次。应用自身仍保存原始 md5，因此不会反复下发。
4. 对每次下发强制加入：

   ```text
   mini_program_white_list=[]
   mini_program_put_type=1
   mini_program_user=-1
   ```

   当前 ROM 的 `oplus-services.jar` 中，`mini_program_put_type=1` 对应 `MiniProgramController.mWhiteList.clear()`，会清空内存中的全局及各用户小程序白名单，并调度写回 XML。云端批量更新和用户勾选“始终允许”都会经过 `k9.c.b(Bundle)`，因此后续也无法重新加入。
5. Hook `oa.b.g(Context, boolean)`，首次按策略版本运行时删除一次：

   ```text
   application_control_center/activity_start_white_list_version
   ```

   下发完成后记录：

   ```text
   coloros_permission_whitelist_hook_policy_revision=1
   coloros_permission_whitelist_hook_sanitized_version=<当前原始 md5>
   ```

   只有策略版本改变，或当前 `activity_start_white_list_version` 与上次经 Hook 下发的版本不同，才会清除版本键并触发一次重新下发。这样不会因每次启动都清除版本号而无限下发，也能处理模块停用期间云端版本发生变化的情况。以后修改过滤策略时递增源码中的 `POLICY_REVISION`。

## 构建和使用

1. 使用 JDK 17、Android SDK 35 和 Gradle 8.9 构建：

   ```powershell
   gradle :app:assembleRelease
   ```

2. 安装 `app/build/outputs/apk/release/app-release-unsigned.apk`；如使用 release 产物，请先按自己的方式签名。
3. 在 LSPosed 中启用模块，作用域只勾选 `系统权限管理` / `com.oplus.securitypermission`。
4. 强制停止目标应用后重启设备。该包包含主进程与 `:ui` 进程，模块会在两个进程中安装 Hook。
5. 在 LSPosed 模块日志中搜索：

   ```text
   [ColorOSPermissionWhitelistHook]
   ```

## GitHub Actions

`.github/workflows/pre-release.yml` 会在每次 `push` 时：

1. 使用 JDK 17、Android SDK 35 和 Gradle 8.9 构建 `:app:assembleDebug`。
2. 将 `app-debug.apk` 保存为 14 天的 Actions artifact。
3. 将固定标签 `pre-release` 移动到当前提交，并覆盖同名 GitHub Pre-release 中的 `app-debug.apk`。工作流使用全局 concurrency，连续推送时只保留最新一次发布任务。

仓库需要允许 GitHub Actions 具有读写权限；工作流已声明 `contents: write`，组织或仓库级策略仍可能覆盖该权限。

## 适配边界

当前 Hook 依赖该 APK 的混淆类名和签名：`oa.b.b(Bundle, Bundle)`、`oa.b.g(Context, boolean)`、`k9.c.b(Bundle)`。系统更新后若这些名称变化，LSPosed 日志会出现 `failed to hook ...`，需要基于新 APK 更新方法名。
