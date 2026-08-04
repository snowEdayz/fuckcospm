package com.fuckcospm;

import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class SecurityPermAutoGrantHook {

    private static final String TAG = "fuckcospm";

    private static final int OP_SYSTEM_ALERT_WINDOW = 24;
    private static final int OP_START_ACTIVITIES_FROM_BACKGROUND = 10002;
    private static final int OP_START_ACTIVITY_FROM_BACKGROUND = 10003;
    private static final int OP_CHANGE_WIFI_STATE = 10128;

    public static void install(ClassLoader cl) {
        try {
            XposedHelpers.findAndHookMethod(
                    AppOpsManager.class,
                    "setMode",
                    int.class,
                    int.class,
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            blockAutoGrant(param);
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    AppOpsManager.class,
                    "setUidMode",
                    int.class,
                    int.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            blockAutoGrant(param);
                        }
                    });
            XposedBridge.log(TAG + ": securityperm auto-grant guard installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": securityperm auto-grant guard failed: " + t);
        }
    }

    private static void blockAutoGrant(MethodHookParam param) {
        try {
            int code = ((Integer) param.args[0]).intValue();
            if (!isGuardedOp(code)) {
                return;
            }
            int mode = ((Integer) param.args[param.args.length - 1]).intValue();
            if (mode != 0) {
                return;
            }
            int uid = ((Integer) param.args[1]).intValue();
            if (uid < 10000) {
                return;
            }
            String pkg = param.args.length == 4 ? (String) param.args[2] : null;
            Context ctx = null;
            try {
                ctx = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
            } catch (Throwable ignored) {
            }
            if (ctx != null && pkg != null) {
                try {
                    ApplicationInfo ai = ctx.getPackageManager().getApplicationInfo(pkg, 0);
                    if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                        return;
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": guard isSys check failed: " + t);
                }
            }
            XposedBridge.log(TAG + ": block auto-grant op=" + code + " pkg=" + pkg + " uid=" + uid);
            param.setResult(null);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": guard failed: " + t);
        }
    }

    private static boolean isGuardedOp(int code) {
        return code == OP_SYSTEM_ALERT_WINDOW
                || code == OP_START_ACTIVITIES_FROM_BACKGROUND
                || code == OP_START_ACTIVITY_FROM_BACKGROUND
                || code == OP_CHANGE_WIFI_STATE;
    }
}
