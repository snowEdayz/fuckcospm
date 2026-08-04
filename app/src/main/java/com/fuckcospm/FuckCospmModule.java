package com.fuckcospm;

import android.content.Intent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class FuckCospmModule implements IXposedHookLoadPackage {

    private static final String TAG = "fuckcospm";

    private static final int RESULT_NEED_CONFIRM = 0;

    private static final String[] TARGET_CALLEE_PACKAGES = {
            "com.heytap.market",
            "com.nearme.instant.platform"
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }
        try {
            Class<?> whiteListClass = XposedHelpers.findClass(
                    "com.android.server.am.OplusSecurityPermissionManager$ActivityStartWhiteList",
                    lpparam.classLoader);
            XposedHelpers.findAndHookMethod(
                    whiteListClass,
                    "checkAllowStartActivity",
                    String.class, String.class, Intent.class, int.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String calleePkg = (String) param.args[1];
                            if (isTarget(calleePkg)) {
                                XposedBridge.log(TAG + ": whitelist bypassed, callerPkg=" + param.args[0]
                                        + ", calleePkg=" + calleePkg);
                                param.setResult(RESULT_NEED_CONFIRM);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hook installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook failed: " + t);
        }
    }

    private static boolean isTarget(String calleePkg) {
        if (calleePkg == null) {
            return false;
        }
        for (String pkg : TARGET_CALLEE_PACKAGES) {
            if (pkg.equals(calleePkg)) {
                return true;
            }
        }
        return false;
    }
}
