package com.fuckcospm

import android.content.Context
import android.content.Intent
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 阻止 ColorOS "安全权限管理"(com.oplus.securitypermission) 的机制 1：
 * ROM 云控 Provider + 快捷修复权限（云控自动修改应用权限）。
 *
 * 机制 1 数据链路（全部位于 com.oplus.securitypermission 进程内）：
 *   content://com.oplus.romupdate.provider.db/update_list
 *     (filtername = "safe_suggest_permission_list"，OPPO ROM 云控下发)
 *   ├─ k9.r (RomUpdateXMLUtil)        —— 读取云控 XML 并落盘
 *   │     Q(): 启动后台读取线程（PermissionApplication 调用）
 *   │     S(): 主进程云控配置更新入口
 *   │     W(): 后台线程读取 safe_permission_list 并应用
 *   │     I(): 后台线程读取 safe_suggest_permission_list 并保存 XML 文件
 *   ├─ z9.b (QuickFixPermissionManager) —— 解析 <quick_fix_permission>
 *   │     g(): 启动修复线程
 *   │     h(): 实际执行修复（p.Q -> p.N -> AppOps.setUidMode / grantRuntimePermission）
 *   ├─ ka.c (SuggestPermissionUpdateManager) —— 解析 <suggest_permission> 写本地 DB
 *   └─ x9.a (PresetPermissionController)     —— 解析 <preset_premission_config> 写 SharedPreferences
 *
 * 触发入口：
 *   - App 启动：PermissionApplication.onCreate -> k9.r.Q()/S()
 *   - 开机广播：BOOT_COMPLETED -> PermissionReceiver / RecommendPolicyReceiver
 *   - 系统云控更新广播 -> SuggestPermissionUpdateService / PresetPermissionUpdateService
 *
 * 阻断策略（分层兜底）：
 *   1. k9.r 的 4 个读取入口全部返回，云控 XML 不再被读取/落盘
 *   2. z9.b/ka.c/x9.a 三个消费方入口全部返回，即使 XML 已被缓存也拒绝应用
 *   3. 两个 IntentService 入口返回，云控更新广播无法触发任何处理
 */
object CloudPermissionBlocker {

    private const val TAG = "FuckCOSPM"

    private const val CLASS_ROM_UPDATE_XML = "k9.r"                       // RomUpdateXMLUtil
    private const val CLASS_QUICK_FIX = "z9.b"                            // QuickFixPermissionManager
    private const val CLASS_QUICK_FIX_ENTRY = "z9.a"                      // quick_fix_permission 条目
    private const val CLASS_SUGGEST_MGR = "ka.c"                          // SuggestPermissionUpdateManager
    private const val CLASS_PRESET_CTRL = "x9.a"                          // PresetPermissionController
    private const val CLASS_SUGGEST_SERVICE =
        "com.oplusos.securitypermission.permission.suggestpermission.SuggestPermissionUpdateService"
    private const val CLASS_PRESET_SERVICE =
        "com.oplusos.securitypermission.permission.presetconfigs.PresetPermissionUpdateService"

    fun hookAll(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookRomUpdateXmlUtil(lpparam.classLoader)
        hookQuickFix(lpparam.classLoader)
        hookSuggestManager(lpparam.classLoader)
        hookPresetController(lpparam.classLoader)
        hookUpdateServices(lpparam.classLoader)
    }

    // ── 层 1：云控 XML 读取入口全部阻断 ────────────────────────────────
    private fun hookRomUpdateXmlUtil(classLoader: ClassLoader) {
        // k9.r.Q(Context) / S(Context) / W(Context) / I(Context) 均为 static void
        for (method in listOf("Q", "S", "W", "I")) {
            try {
                XposedHelpers.findAndHookMethod(
                    CLASS_ROM_UPDATE_XML, classLoader, method,
                    Context::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            log("blocked k9.r.$method() (rom-update cloud XML)")
                            param.result = null
                        }
                    }
                )
                log("hooked k9.r.$method")
            } catch (t: Throwable) {
                log("hook k9.r.$method failed: $t")
            }
        }
    }

    // ── 层 2a：快捷修复权限（真正的"云控自动改权限"） ──────────────────
    private fun hookQuickFix(classLoader: ClassLoader) {
        // z9.b.g()：启动修复线程
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_QUICK_FIX, classLoader, "g",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log("blocked z9.b.g() (quick-fix permission thread)")
                        param.result = null
                    }
                }
            )
            log("hooked z9.b.g")
        } catch (t: Throwable) {
            log("hook z9.b.g failed: $t")
        }

        // z9.b.h(Context, z9.a)：实际执行修复（private，兜底）
        try {
            val entryClass = XposedHelpers.findClass(CLASS_QUICK_FIX_ENTRY, classLoader)
            XposedHelpers.findAndHookMethod(
                CLASS_QUICK_FIX, classLoader, "h",
                Context::class.java, entryClass,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log("blocked z9.b.h() (quick-fix permission apply)")
                        param.result = null
                    }
                }
            )
            log("hooked z9.b.h")
        } catch (t: Throwable) {
            log("hook z9.b.h failed: $t")
        }
    }

    // ── 层 2b：建议权限写入本地 DB ─────────────────────────────────────
    private fun hookSuggestManager(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_SUGGEST_MGR, classLoader, "d",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log("blocked ka.c.d() (suggest permission DB write)")
                        param.result = null
                    }
                }
            )
            log("hooked ka.c.d")
        } catch (t: Throwable) {
            log("hook ka.c.d failed: $t")
        }
    }

    // ── 层 2c：预置权限写入 SharedPreferences ──────────────────────────
    private fun hookPresetController(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_PRESET_CTRL, classLoader, "f",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        log("blocked x9.a.f() (preset permission write)")
                        param.result = null
                    }
                }
            )
            log("hooked x9.a.f")
        } catch (t: Throwable) {
            log("hook x9.a.f failed: $t")
        }
    }

    // ── 层 3：云控更新服务入口 ─────────────────────────────────────────
    private fun hookUpdateServices(classLoader: ClassLoader) {
        val services = listOf(
            CLASS_SUGGEST_SERVICE to "SuggestPermissionUpdateService",
            CLASS_PRESET_SERVICE to "PresetPermissionUpdateService",
        )
        for ((cls, name) in services) {
            try {
                XposedHelpers.findAndHookMethod(
                    cls, classLoader, "onHandleIntent",
                    Intent::class.java,
                    object : XC_MethodHook() {
                        override fun beforeHookedMethod(param: MethodHookParam) {
                            log("blocked $name.onHandleIntent (cloud update trigger)")
                            param.result = null
                        }
                    }
                )
                log("hooked $name.onHandleIntent")
            } catch (t: Throwable) {
                log("hook $name.onHandleIntent failed: $t")
            }
        }
    }

    private fun log(msg: String) {
        XposedBridge.log("$TAG: $msg")
    }
}
