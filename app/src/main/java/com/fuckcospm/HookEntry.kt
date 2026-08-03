package com.fuckcospm

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage

/**
 * LSPosed 模块入口。
 *
 * 两个目标：
 * 1. ColorOS 系统进程（system_server）中
 *    com.android.server.am.OplusSecurityPermissionManager 的活动启动白名单机制。
 * 2. ColorOS 安全权限管理 App（com.oplus.securitypermission）中
 *    机制 1：ROM 云控 Provider + 快捷修复权限（云控自动修改应用权限）。
 */
class HookEntry : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        when (lpparam.packageName) {
            // system_server 对应的 framework 包名是 "android"
            "android" -> {
                XposedBridge.log("FuckCOSPM: module loaded into ${lpparam.processName}")
                WhiteListStripper.hookAll(lpparam)
            }
            // 安全权限管理 App 自身进程
            "com.oplus.securitypermission" -> {
                XposedBridge.log("FuckCOSPM: module loaded into ${lpparam.processName}")
                CloudPermissionBlocker.hookAll(lpparam)
            }
        }
    }
}
