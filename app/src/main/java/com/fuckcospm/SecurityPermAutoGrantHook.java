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

    private static volatile boolean sInstalled = false;

    public static void install(ClassLoader cl) {
        if (sInstalled) {
            return;
        }
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
                            blockAutoGrant(param, "setMode(int)", 0, null);
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
                            blockAutoGrant(param, "setUidMode(int)", 0, null);
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    AppOpsManager.class,
                    "setMode",
                    String.class,
                    int.class,
                    String.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String opStr = (String) param.args[0];
                            Integer code = strToOp(opStr);
                            blockAutoGrant(param, "setMode(str)", code == null ? -1 : code.intValue(), opStr);
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    AppOpsManager.class,
                    "setUidMode",
                    String.class,
                    int.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String opStr = (String) param.args[0];
                            Integer code = strToOp(opStr);
                            blockAutoGrant(param, "setUidMode(str)", code == null ? -1 : code.intValue(), opStr);
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    AppOpsManager.class,
                    "setUidModeFromPermission",
                    String.class,
                    int.class,
                    int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            String permission = (String) param.args[0];
                            Integer code = permissionToOp(permission);
                            blockAutoGrant(param, "setUidModeFromPermission", code == null ? -1 : code.intValue(), permission);
                        }
                    });
            sInstalled = true;
            XposedBridge.log(TAG + ": securityperm auto-grant guard installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": securityperm auto-grant guard failed: " + t);
        }
    }

    private static void blockAutoGrant(MethodHookParam param, String method, int code, String opStr) {
        try {
            boolean guarded;
            if (opStr != null && code < 0) {
                guarded = false;
            } else {
                guarded = isGuardedOp(code);
            }
            int mode = ((Integer) param.args[param.args.length - 1]).intValue();
            int uid = ((Integer) param.args[1]).intValue();
            String pkg = null;
            for (Object arg : param.args) {
                if (arg instanceof String && !arg.equals(opStr)) {
                    pkg = (String) arg;
                    break;
                }
            }
            XposedBridge.log(TAG + ": appops " + method + " op=" + code + " str=" + opStr
                    + " mode=" + mode + " uid=" + uid + " pkg=" + pkg + " guarded=" + guarded
                    + " caller=" + caller());
            if (!guarded || mode != 0 || uid < 10000) {
                return;
            }
            if (!isSystemApp(pkg)) {
                XposedBridge.log(TAG + ": block auto-grant op=" + code + " pkg=" + pkg + " uid=" + uid
                        + " via " + method);
                param.setResult(null);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": guard failed: " + t);
        }
    }

    private static boolean isSystemApp(String pkg) {
        if (pkg == null) {
            return false;
        }
        try {
            Object activityThread = XposedHelpers.callStaticMethod(
                    android.app.ActivityThread.class, "currentActivityThread");
            if (activityThread == null) {
                return false;
            }
            Context ctx = (Context) XposedHelpers.callMethod(activityThread, "getSystemContext");
            if (ctx == null) {
                return false;
            }
            ApplicationInfo appInfo = ctx.getPackageManager().getApplicationInfo(pkg, 0);
            return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Integer strToOp(String opStr) {
        try {
            return (Integer) XposedHelpers.callStaticMethod(AppOpsManager.class, "strOpToOp", opStr);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Integer permissionToOp(String permission) {
        try {
            return (Integer) XposedHelpers.callStaticMethod(AppOpsManager.class, "permissionToOpCode", permission);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String caller() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (StackTraceElement e : st) {
            String cls = e.getClassName();
            if (cls.startsWith("com.oplus") || cls.startsWith("android.app.AppOpsManager")
                    || cls.startsWith("de.robv") || cls.startsWith("com.fuckcospm")) {
                if (sb.length() > 0) {
                    sb.append(" <- ");
                }
                sb.append(cls).append("#").append(e.getMethodName());
                shown++;
                if (shown >= 4) {
                    break;
                }
            }
        }
        return sb.toString();
    }

    private static boolean isGuardedOp(int code) {
        return code == OP_SYSTEM_ALERT_WINDOW
                || code == OP_START_ACTIVITIES_FROM_BACKGROUND
                || code == OP_START_ACTIVITY_FROM_BACKGROUND
                || code == OP_CHANGE_WIFI_STATE;
    }
}
