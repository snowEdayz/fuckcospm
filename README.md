# FuckCOSPM

LSPosed 模块：阻止 ColorOS `OplusSecurityPermissionManager` 将以下应用加入"活动启动白名单"：

- `com.eg.android.AlipayGphone`（支付宝）
- `com.heytap.market`（OPPO 应用市场）

即当系统收到白名单更新（云端/本地预置推送、用户勾选"始终允许"）时，将这两个包从白名单数据中剥离，使其每次被拉起时都弹确认框。

## Hook 点（system_server / framework）

| 方法 | 作用 |
|---|---|
| `OplusSecurityPermissionManager#putActivityStartWhiteList(Bundle)` | 运行时白名单更新入口（binder） |
| `OplusSecurityPermissionManager$ActivityStartWhiteList#putWhiteList(Bundle,int)` | 内存缓存写入（md5 预置替换 + 用户设置） |
| `OplusSecurityPermissionManager$ActivityStartWhiteList#putPresetWhiteList(String,String)` | 解析持久化/预置 XML 时的预置条目 |
| `OplusSecurityPermissionManager$ActivityStartWhiteList#putUserSetWhiteList(Pair,int)` | 解析 XML 中的用户 (src,dst) 配对 |
| `OplusSecurityPermissionManager$ActivityStartWhiteList#checkAllowStartActivity(String,String,Intent,int,int)` | 匹配阶段兜底：白名单命中且涉及目标包时强制改为 START_BLOCK（0）弹确认框 |

清理范围：`src_pkg`、`dst_pkg`、`activity`（组件 `pkg/类名`）、`src_and_dst`（用户"始终允许"）。

最后一个 hook 解决"模块激活前白名单已加载进内存缓存"的残留场景：即使缓存中已有目标包条目，匹配阶段也不会放行。示例配置 `example.xml` 中 `com.heytap.market` 位于 `src_pkg`、`com.eg.android.AlipayGphone` 位于 `dst_pkg`，两者均被覆盖。

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
