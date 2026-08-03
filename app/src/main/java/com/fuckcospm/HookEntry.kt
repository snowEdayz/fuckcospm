package com.fuckcospm

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 模块入口。
 *
 * 目标：OPPO/OnePlus 的 ColorOS 系统进程（system_server）中
 * com.android.server.am.OplusSecurityPermissionManager 的活动启动白名单机制。
 */
class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        // system_server 对应的 framework 包名是 "android"
        if (lpparam.packageName != "android") return

        XposedBridge.log("FuckCOSPM: module loaded into ${lpparam.processName}")
        WhiteListStripper.hookAll(lpparam)
    }
}
