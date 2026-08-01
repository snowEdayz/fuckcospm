package com.fuckcospm.xposed

import android.os.Bundle
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class HookEntry : IXposedHookLoadPackage {

    companion object {
        private const val TAG = "FuckCospm"
        private const val TARGET_PACKAGE = "com.oplus.securitypermission"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != TARGET_PACKAGE) return

        Log.i(TAG, "=== Hooking SecurityPermission ===")
        Log.i(TAG, "Package: ${lpparam.packageName}")
        Log.i(TAG, "Classloader: ${lpparam.classLoader}")

        try {
            hookActivityStartWhitelist(lpparam.classLoader)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook ActivityStartWhitelist", e)
        }

        try {
            hookOplusPermissionManager(lpparam.classLoader)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook OplusPermissionManager", e)
        }

        try {
            hookAppStartDialog(lpparam.classLoader)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook AppStartDialog", e)
        }

        try {
            hookMiniProgramBlockDialog(lpparam.classLoader)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook MiniProgramBlockDialog", e)
        }

        try {
            hookRomUpdateXMLUtil(lpparam.classLoader)
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to hook RomUpdateXMLUtil", e)
        }
    }

    /**
     * Hook oa.b (ActivityStartWhiteListUtils)
     * 拦截白名单获取和更新
     */
    private fun hookActivityStartWhitelist(classLoader: ClassLoader) {
        val className = "oa.b"
        val clazz = XposedHelpers.findClass(className, classLoader)

        // Hook g(Context, boolean) - 获取/更新白名单
        XposedHelpers.findAndHookMethod(
            clazz,
            "g",
            android.content.Context::class.java,
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val context = param.args[0] as android.content.Context
                    val forceUpdate = param.args[1] as Boolean
                    Log.i(TAG, "=== ActivityStartWhiteListUtils.g() ===")
                    Log.i(TAG, "Context: ${context.packageName}")
                    Log.i(TAG, "ForceUpdate: $forceUpdate")
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    Log.i(TAG, "=== ActivityStartWhiteListUtils.g() 返回 ===")
                    Log.i(TAG, "Result: ${param.result}")
                }
            }
        )

        // Hook d(Context) - 读取本地 XML 白名单
        XposedHelpers.findAndHookMethod(
            clazz,
            "d",
            android.content.Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result as? Bundle
                    if (result != null) {
                        Log.i(TAG, "=== 本地 XML 白名单 ===")
                        logBundle(result)
                    }
                }
            }
        )

        // Hook f(Context) - 获取 ActivityStart 白名单
        XposedHelpers.findAndHookMethod(
            clazz,
            "f",
            android.content.Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result as? Bundle
                    if (result != null) {
                        Log.i(TAG, "=== ActivityStart 白名单 (合并后) ===")
                        logBundle(result)
                    }
                }
            }
        )
    }

    /**
     * Hook k9.c (OplusPermissionManager)
     * 拦截白名单写入操作
     */
    private fun hookOplusPermissionManager(classLoader: ClassLoader) {
        val className = "k9.c"
        val clazz = XposedHelpers.findClass(className, classLoader)

        // Hook b(Bundle) - putActivityStartWhiteList
        XposedHelpers.findAndHookMethod(
            clazz,
            "b",
            Bundle::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val bundle = param.args[0] as? Bundle
                    if (bundle != null) {
                        Log.i(TAG, "=== putActivityStartWhiteList 调用 ===")
                        logBundle(bundle)
                    }
                }
            }
        )

        // Hook a() - 获取单例
        XposedHelpers.findAndHookMethod(
            clazz,
            "a",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    Log.i(TAG, "=== OplusPermissionManager 单例获取 ===")
                }
            }
        )
    }

    /**
     * Hook l9.f (AppStartDialog)
     * 拦截应用启动确认对话框
     */
    private fun hookAppStartDialog(classLoader: ClassLoader) {
        val className = "l9.f"
        val clazz = XposedHelpers.findClass(className, classLoader)

        // Hook i(Activity, b) - 启动 Activity
        XposedHelpers.findAndHookMethod(
            clazz,
            "i",
            android.app.Activity::class.java,
            XposedHelpers.findClass("l9.b", classLoader),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.args[0] as android.app.Activity
                    val data = param.args[1]
                    Log.i(TAG, "=== AppStartDialog 启动 Activity ===")
                    Log.i(TAG, "Activity: ${activity.javaClass.name}")
                    logAppStartData(data)
                }
            }
        )

        // Hook e(Activity, int, boolean, b) - 对话框点击处理
        XposedHelpers.findAndHookMethod(
            clazz,
            "e",
            android.app.Activity::class.java,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            XposedHelpers.findClass("l9.b", classLoader),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val which = param.args[1] as Int
                    val isChecked = param.args[2] as Boolean
                    val data = param.args[3]
                    Log.i(TAG, "=== AppStartDialog 对话框点击 ===")
                    Log.i(TAG, "Which: $which (-1=允许, -2=取消, -3=其他)")
                    Log.i(TAG, "IsChecked: $isChecked")
                    logAppStartData(data)
                }
            }
        )
    }

    /**
     * Hook l9.k (MiniProgramBlockDialog)
     * 拦截小程序拦截对话框
     */
    private fun hookMiniProgramBlockDialog(classLoader: ClassLoader) {
        val className = "l9.k"
        val clazz = XposedHelpers.findClass(className, classLoader)

        // Hook g(b) - 写入小程序白名单
        XposedHelpers.findAndHookMethod(
            clazz,
            "g",
            XposedHelpers.findClass("l9.b", classLoader),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val data = param.args[0]
                    Log.i(TAG, "=== MiniProgramBlockDialog 写入白名单 ===")
                    logAppStartData(data)
                }
            }
        )

        // Hook f(b) - 启动 pending intent
        XposedHelpers.findAndHookMethod(
            clazz,
            "f",
            XposedHelpers.findClass("l9.b", classLoader),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val data = param.args[0]
                    Log.i(TAG, "=== MiniProgramBlockDialog 启动 PendingIntent ===")
                    logAppStartData(data)
                }
            }
        )
    }

    /**
     * Hook k9.r (RomUpdateXMLUtil)
     * 拦截 RUS 配置解析
     */
    private fun hookRomUpdateXMLUtil(classLoader: ClassLoader) {
        val className = "k9.r"
        val clazz = XposedHelpers.findClass(className, classLoader)

        // Hook f(Context) - 获取 ActivityStart 白名单
        XposedHelpers.findAndHookMethod(
            clazz,
            "f",
            android.content.Context::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result as? Bundle
                    if (result != null) {
                        Log.i(TAG, "=== RomUpdateXMLUtil ActivityStart 白名单 ===")
                        logBundle(result)
                    }
                }
            }
        )

        // Hook w(String) - 解析 ActivityStart 白名单 XML
        XposedHelpers.findAndHookMethod(
            clazz,
            "w",
            String::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val xml = param.args[0] as? String
                    if (xml != null && xml.length > 100) {
                        Log.i(TAG, "=== 解析 ActivityStart 白名单 XML ===")
                        Log.i(TAG, "XML长度: ${xml.length}")
                        Log.i(TAG, "XML内容(前500字符): ${xml.take(500)}")
                    }
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    val result = param.result as? Bundle
                    if (result != null) {
                        Log.i(TAG, "=== XML 解析结果 ===")
                        logBundle(result)
                    }
                }
            }
        )
    }

    /**
     * 打印 Bundle 内容
     */
    private fun logBundle(bundle: Bundle) {
        try {
            for (key in bundle.keySet()) {
                val value = bundle.get(key)
                when (value) {
                    is ArrayList<*> -> {
                        Log.i(TAG, "  [$key] (ArrayList, size=${value.size}):")
                        value.forEachIndexed { index, item ->
                            Log.i(TAG, "    [$index] $item")
                        }
                    }
                    is Bundle -> {
                        Log.i(TAG, "  [$key] (Bundle):")
                        logBundle(value)
                    }
                    else -> {
                        Log.i(TAG, "  [$key] $value")
                    }
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error logging bundle", e)
        }
    }

    /**
     * 打印 AppStartData 内容
     */
    private fun logAppStartData(data: Any?) {
        try {
            val clazz = data?.javaClass ?: return
            Log.i(TAG, "  Class: ${clazz.name}")

            // 尝试读取常见字段
            val fields = mapOf(
                "callerPackage" to "d",
                "callerName" to "c",
                "calleePackage" to "b",
                "calleeName" to "a",
                "sourceIntent" to "f",
                "type" to "g"
            )

            for ((name, methodName) in fields) {
                try {
                    val method = clazz.getDeclaredMethod(methodName)
                    method.isAccessible = true
                    val value = method.invoke(data)
                    Log.i(TAG, "  $name: $value")
                } catch (_: Throwable) {
                    // 忽略无法访问的方法
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error logging AppStartData", e)
        }
    }
}
