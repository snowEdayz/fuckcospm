package com.fuckcospm;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class MiniProgramHook {

    private static final String TAG = "fuckcospm";

    private static volatile boolean sHooksInstalled = false;

    public static void install(ClassLoader cl) {
        try {
            final Class<?> ospmClass = XposedHelpers.findClass(
                    "com.android.server.am.OplusSecurityPermissionManager", cl);
            XposedHelpers.findAndHookMethod(
                    ospmClass,
                    "getInstance",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            installMiniProgramHooks(cl);
                        }
                    });
            XposedBridge.log(TAG + ": ospm trigger hook installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": ospm trigger hook failed: " + t);
        }
        installMiniProgramHooks(cl);
    }

    private static void installMiniProgramHooks(ClassLoader cl) {
        if (sHooksInstalled) {
            return;
        }
        try {
            Class<?> mpcClass = XposedHelpers.findClass(
                    "com.android.server.am.OplusSecurityPermissionManager$MiniProgramController", cl);
            XposedHelpers.findAndHookMethod(
                    mpcClass,
                    "isInWhiteList",
                    String.class,
                    String.class,
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            param.setResult(Boolean.FALSE);
                        }
                    });
            sHooksInstalled = true;
            XposedBridge.log(TAG + ": mini-program white list bypass installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": mini-program hook failed: " + t);
        }
    }
}
