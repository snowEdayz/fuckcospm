package com.fuckcospm

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Pair
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * 拦截 OPlus 活动启动白名单的所有入口，将目标包从白名单中剥离。
 *
 * 覆盖两条写入路径：
 *  1. 运行时推送（云同步 / 智慧护盾 / 用户"始终允许" / 用户忽略对话框）
 *     -> OplusSecurityPermissionManager.putActivityStartWhiteList(Bundle)
 *     -> ActivityStartWhiteList.putWhiteList(Bundle, int)
 *  2. 开机/重启加载（解析持久化 XML oplus_activity_start_permissions.xml
 *     及预置 XML oplus_preset_activity_start_permissions.xml）
 *     -> ActivityStartWhiteList.putPresetWhiteList(String, String)
 *     -> ActivityStartWhiteList.putUserSetWhiteList(Pair, int)
 *
 * 并拦截匹配阶段 ActivityStartWhiteList.checkAllowStartActivity(...)：
 * 即使目标包已在模块激活前就存在于内存缓存/持久化文件中，
 * 白名单命中（返回 -1）也会被改写为 0（START_BLOCK），强制弹确认框。
 *
 * 开机兜底 OplusSecurityPermissionManager.readActivityStartWhiteList()：
 * 系统开机加载白名单完成后，主动清洗内存缓存中残留的目标包条目，
 * 并立即写盘（writeActivityStartWhiteList），保证持久化文件同步干净。
 *
 * 系统 App 特判 OplusAppStartConfirmManager.isSystemAppOrSameApp()：
 * 弹确认框的总入口 checkStartActivityForConfirm 在检查白名单之前，
 * 会先对系统 App（FLAG_SYSTEM）调用方/目标方直接放行（不弹框）。
 * com.heytap.market 是系统 App，剥离白名单对它不生效，
 * 因此强制使该特判对目标包返回 false，使检查链继续走到白名单检查。
 */
object WhiteListStripper {

    private const val TAG = "FuckCOSPM"

    private val TARGET_PACKAGES = setOf(
        "com.eg.android.AlipayGphone", // 支付宝
        "com.heytap.market",           // 应用市场
    )

    // Bundle 字段名（与 com.android.server.am.OplusSecurityPermissionManager 中常量一致）
    private const val KEY_SRC_PKG = "src_pkg"
    private const val KEY_DST_PKG = "dst_pkg"
    private const val KEY_ACTIVITY = "activity"
    private const val KEY_SRC_AND_DST = "src_and_dst"

    // 匹配阶段返回值（与 OplusSecurityPermissionManager 中常量一致）
    private const val TYPE_DEFAULT = -1    // 白名单命中，放行
    private const val TYPE_START_BLOCK = 0 // 需要弹确认框

    private const val CLASS_MANAGER = "com.android.server.am.OplusSecurityPermissionManager"
    private const val CLASS_WHITE_LIST = "$CLASS_MANAGER\$ActivityStartWhiteList"
    private const val CLASS_CONFIRM_MANAGER = "com.android.server.wm.OplusAppStartConfirmManager"

    fun hookAll(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookPutActivityStartWhiteList(lpparam.classLoader)
        hookPutWhiteList(lpparam.classLoader)
        hookPutPresetWhiteList(lpparam.classLoader)
        hookPutUserSetWhiteList(lpparam.classLoader)
        hookCheckAllowStartActivity(lpparam.classLoader)
        hookBootCleanup(lpparam.classLoader)
        hookIsSystemAppOrSameApp(lpparam.classLoader)
    }

    // ── 入口 1：系统服务收到白名单更新（binder 调用的第一站） ──────────────
    private fun hookPutActivityStartWhiteList(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_MANAGER, classLoader, "putActivityStartWhiteList",
                Bundle::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val bundle = param.args[0] as? Bundle ?: return
                        stripFromBundle(bundle, "putActivityStartWhiteList")
                    }
                }
            )
            log("hooked putActivityStartWhiteList")
        } catch (t: Throwable) {
            log("hook putActivityStartWhiteList failed: $t")
        }
    }

    // ── 入口 2：内存缓存写入（md5 预置替换 + 用户 src_and_dst 追加） ────────
    private fun hookPutWhiteList(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_WHITE_LIST, classLoader, "putWhiteList",
                Bundle::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val bundle = param.args[0] as? Bundle ?: return
                        stripFromBundle(bundle, "putWhiteList")
                    }
                }
            )
            log("hooked putWhiteList")
        } catch (t: Throwable) {
            log("hook putWhiteList failed: $t")
        }
    }

    // ── 入口 3：解析 XML 时的预置条目（type=src_pkg/dst_pkg/activity/action） ─
    private fun hookPutPresetWhiteList(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_WHITE_LIST, classLoader, "putPresetWhiteList",
                String::class.java, String::class.java,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val type = param.args[0] as String
                        val value = param.args[1] as String
                        if (shouldDropPresetEntry(type, value)) {
                            log("dropped preset entry: type=$type value=$value")
                            param.result = null
                        }
                    }
                }
            )
            log("hooked putPresetWhiteList")
        } catch (t: Throwable) {
            log("hook putPresetWhiteList failed: $t")
        }
    }

    // ── 入口 4：解析 XML / 用户设置中的 (src, dst) 配对条目 ─────────────────
    private fun hookPutUserSetWhiteList(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_WHITE_LIST, classLoader, "putUserSetWhiteList",
                Pair::class.java, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        val pair = param.args[0] as? Pair<*, *> ?: return
                        val src = pair.first as? String
                        val dst = pair.second as? String
                        if ((src != null && TARGET_PACKAGES.contains(src)) ||
                            (dst != null && TARGET_PACKAGES.contains(dst))
                        ) {
                            log("dropped user-set entry: src=$src dst=$dst")
                            param.result = null
                        }
                    }
                }
            )
            log("hooked putUserSetWhiteList")
        } catch (t: Throwable) {
            log("hook putUserSetWhiteList failed: $t")
        }
    }

    // ── 入口 5（兜底）：匹配阶段，命中白名单且涉及目标包时强制弹框 ────────
    private fun hookCheckAllowStartActivity(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_WHITE_LIST, classLoader, "checkAllowStartActivity",
                String::class.java, String::class.java, Intent::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result as? Int ?: return
                        if (result != TYPE_DEFAULT) return
                        val caller = param.args[0] as? String
                        val callee = param.args[1] as? String
                        if ((caller != null && TARGET_PACKAGES.contains(caller)) ||
                            (callee != null && TARGET_PACKAGES.contains(callee))
                        ) {
                            log("white-list match blocked: caller=$caller callee=$callee -> START_BLOCK")
                            param.result = TYPE_START_BLOCK
                        }
                    }
                }
            )
            log("hooked checkAllowStartActivity")
        } catch (t: Throwable) {
            log("hook checkAllowStartActivity failed: $t")
        }
    }

    // ── 入口 6（开机兜底）：开机白名单加载完成后主动清洗并落盘 ────────────
    // 系统开机时序：OplusSecurityPermissionManager.init() -> handler MSG 1
    // -> readActivityStartWhiteList()（后台线程加载缓存）
    private fun hookBootCleanup(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_MANAGER, classLoader, "readActivityStartWhiteList",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        try {
                            val mgr = param.thisObject ?: return
                            val cached = XposedHelpers.getObjectField(mgr, "mCachedActivityStartWhiteList")
                            val lock = XposedHelpers.getObjectField(mgr, "mActivityStartLock")
                            synchronized(lock) {
                                scrubCachedWhiteList(cached)
                            }
                            // 立即写盘：持久化文件中的残留条目一并清洗
                            XposedHelpers.callMethod(mgr, "writeActivityStartWhiteList")
                            log("boot cleanup done, white list persisted")
                        } catch (t: Throwable) {
                            log("boot cleanup failed: $t")
                        }
                    }
                }
            )
            log("hooked readActivityStartWhiteList (boot cleanup)")
        } catch (t: Throwable) {
            log("hook readActivityStartWhiteList failed: $t")
        }
    }

    // 清洗内存缓存：mPresetList（src_pkg/dst_pkg/activity）+ mUserSetList（Pair 配对）
    private fun scrubCachedWhiteList(cached: Any) {
        val presetList = XposedHelpers.getObjectField(cached, "mPresetList") as? Map<*, *> ?: return
        scrubStringList(presetList[KEY_SRC_PKG], "src_pkg")
        scrubStringList(presetList[KEY_DST_PKG], "dst_pkg")
        scrubComponentList(presetList[KEY_ACTIVITY])

        val userSetList = XposedHelpers.getObjectField(cached, "mUserSetList") as? Map<*, *> ?: return
        for (userList in userSetList.values) {
            scrubPairList(userList)
        }
    }

    private fun scrubStringList(value: Any?, where: String) {
        if (value !is MutableList<*>) return
        var changed = false
        val it = value.listIterator()
        while (it.hasNext()) {
            val item = it.next() ?: continue
            if (item is String && item in TARGET_PACKAGES) {
                it.remove()
                changed = true
            }
        }
        if (changed) {
            log("boot cleanup: removed entries from mPresetList[$where]")
        }
    }

    private fun scrubComponentList(value: Any?) {
        if (value !is MutableList<*>) return
        var changed = false
        val it = value.listIterator()
        while (it.hasNext()) {
            val item = it.next() ?: continue
            if (item is String && TARGET_PACKAGES.any { target -> item == target || item.startsWith("$target/") }) {
                it.remove()
                changed = true
            }
        }
        if (changed) {
            log("boot cleanup: removed entries from mPresetList[activity]")
        }
    }

    private fun scrubPairList(value: Any?) {
        if (value !is MutableList<*>) return
        var changed = false
        val it = value.listIterator()
        while (it.hasNext()) {
            val pair = it.next() as? android.util.Pair<*, *> ?: continue
            val src = pair.first as? String
            val dst = pair.second as? String
            if ((src != null && src in TARGET_PACKAGES) || (dst != null && dst in TARGET_PACKAGES)) {
                it.remove()
                changed = true
            }
        }
        if (changed) {
            log("boot cleanup: removed entries from mUserSetList")
        }
    }

    // ── 入口 7（系统 App 特判绕行）：让目标包走到白名单检查 ──────────────
    // checkStartActivityForConfirm 在调用 checkAllowStartActivity 之前，
    // 先执行 isSystemAppOrSameApp：目标/调用方是系统 App 时直接放行不弹框。
    // com.heytap.market 是系统 App（FLAG_SYSTEM），剥离白名单不会生效，
    // 因此对该方法返回 true 且目标方（dst）为目标包时强制改写为 false。
    // 注意：调用方（src）是系统 App 时保持原样放行，不弹框。
    private fun hookIsSystemAppOrSameApp(classLoader: ClassLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                CLASS_CONFIRM_MANAGER, classLoader, "isSystemAppOrSameApp",
                Int::class.javaPrimitiveType, String::class.java, ActivityInfo::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if ((param.result as? Boolean) != true) return
                        val aInfo = param.args[2] as? ActivityInfo
                        val targetPkg = aInfo?.applicationInfo?.packageName
                        if (targetPkg != null && targetPkg in TARGET_PACKAGES) {
                            log("isSystemAppOrSameApp bypassed: target=$targetPkg -> continue to white-list check")
                            param.result = false
                        }
                    }
                }
            )
            log("hooked isSystemAppOrSameApp")
        } catch (t: Throwable) {
            log("hook isSystemAppOrSameApp failed: $t")
        }
    }

    // ── Bundle 清理：src_pkg / dst_pkg / activity / src_and_dst ─────────────
    private fun stripFromBundle(bundle: Bundle, where: String) {
        var changed = false

        // 包名列表：src_pkg（调用方）、dst_pkg（目标方）
        for (key in listOf(KEY_SRC_PKG, KEY_DST_PKG)) {
            val list = bundle.getStringArrayList(key) ?: continue
            if (list.any { TARGET_PACKAGES.contains(it) }) {
                bundle.putStringArrayList(key, ArrayList(list.filter { !TARGET_PACKAGES.contains(it) }))
                log("$where: removed entries from $key")
                changed = true
            }
        }

        // 组件列表：格式 "包名/类名"，按包名前缀匹配
        val activities = bundle.getStringArrayList(KEY_ACTIVITY)
        if (activities != null) {
            val filtered = activities.filter { value ->
                TARGET_PACKAGES.none { target -> value == target || value.startsWith("$target/") }
            }
            if (filtered.size != activities.size) {
                bundle.putStringArrayList(KEY_ACTIVITY, ArrayList(filtered))
                log("$where: removed entries from activity")
                changed = true
            }
        }

        // 用户"始终允许"配对：2 元素 [caller, callee]
        val srcAndDst = bundle.getStringArrayList(KEY_SRC_AND_DST)
        if (srcAndDst != null && srcAndDst.any { TARGET_PACKAGES.contains(it) }) {
            bundle.remove(KEY_SRC_AND_DST)
            log("$where: removed src_and_dst entry")
            changed = true
        }

        if (changed) {
            log("$where: white list stripped")
        }
    }

    private fun shouldDropPresetEntry(type: String, value: String): Boolean {
        return when (type) {
            KEY_SRC_PKG, KEY_DST_PKG -> TARGET_PACKAGES.contains(value)
            KEY_ACTIVITY -> TARGET_PACKAGES.any { value == it || value.startsWith("$it/") }
            else -> false
        }
    }

    private fun log(msg: String) {
        XposedBridge.log("$TAG: $msg")
    }
}
