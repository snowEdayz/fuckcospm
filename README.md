# FuckCOSPM

LSPosed 模块：阻止 ColorOS `OplusSecurityPermissionManager` 将以下应用加入"活动启动白名单"：

- `com.eg.android.AlipayGphone`（支付宝）
- `com.heytap.market`（OPPO 应用市场）

即当系统收到白名单更新（云端/本地预置推送）时，将这两个包从白名单预置数据中剥离，使其每次被拉起时都弹确认框。用户主动保存的"始终允许"偏好（`src_and_dst` / `mUserSetList`）遵循用户选择，不剥离。

## Hook 点（system_server / framework）

| 方法 | 作用 |
|---|---|
| `OplusSecurityPermissionManager#putActivityStartWhiteList(Bundle)` | 运行时白名单更新入口（binder） |
| `OplusSecurityPermissionManager$ActivityStartWhiteList#putWhiteList(Bundle,int)` | 内存缓存写入（md5 预置替换 + 用户设置） |
| `OplusSecurityPermissionManager$ActivityStartWhiteList#putPresetWhiteList(String,String)` | 预置条目丢弃（src_pkg/dst_pkg/activity/action 命中目标包） |
| `OplusSecurityPermissionManager$ActivityStartWhiteList#checkAllowStartActivity(String,String,Intent,int,int)` | 匹配阶段兜底：预置白名单命中且涉及目标包时强制改为 START_BLOCK（0）弹确认框；用户保存的"始终允许"偏好（mUserSetList）遵循用户选择，不干预 |
| `OplusSecurityPermissionManager#readActivityStartWhiteList()` | 开机兜底：缓存加载完成后主动清洗预置表残留条目并立即写盘（用户偏好 mUserSetList 保留） |
| `OplusAppStartConfirmManager#isSystemAppOrSameApp(int,String,ActivityInfo)` | 系统 App 特判绕行：目标方（dst）是系统 App 时该特判直接放行不弹框（`com.heytap.market` 是系统 App，剥离白名单对其无效），强制改写为 false 使检查链继续走到白名单检查；调用方（src）是系统 App 时保持放行 |

清理范围：`src_pkg`、`dst_pkg`、`activity`（组件 `pkg/类名`）、`action`（预置表）。用户"始终允许"（`src_and_dst` / `mUserSetList`）遵循用户偏好，不剥离、不清洗。

`checkAllowStartActivity` hook 解决"模块激活前白名单已加载进内存缓存"的残留场景：即使缓存中已有目标包条目，匹配阶段也不会放行。`readActivityStartWhiteList` hook 在开机时（`init()` → MSG 1 加载完成后）主动触发一次白名单更新：清洗 `mPresetList` 并调用 `writeActivityStartWhiteList()` 落盘，保证持久化文件同步干净。示例配置 `example.xml` 中 `com.heytap.market` 位于 `src_pkg`、`com.eg.android.AlipayGphone` 位于 `dst_pkg`，两者均被覆盖。

启动确认的完整链路（system_server 内）：`OplusAppStartConfirmManager.checkStartActivityForConfirm(...)` → 早期条件（弹框 Activity 存在/拦截开关/历史防重复等）→ `isSystemAppOrSameApp`（系统 App 放行）→ `isMultiWindowMode` / `isAppOrActivityHasExist` / `skipLabActivityStartConfirm` → binder `checkAllowStartActivity`（白名单，-1 放行 / 5 拦截 / 0 弹框）。模块的最后一个 hook 绕过了系统 App 特判，其余早期条件为系统设计行为（如目标 App 已在前台不弹框、同一配对 3 次内不重复弹框）。

## 使用

1. 安装模块 APK，在 LSPosed 中启用并将作用域勾选 **System Framework**
2. 重启
3. 日志中搜索 `FuckCOSPM` 确认 hook 生效

## 构建

CI：push 到 `main` 自动构建 debug 并发布到 GitHub pre-release（`build-debug-<run_number>`）。

本地构建：

```bash
gradle wrapper --gradle-version 8.9
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`
