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
        hookSetModeInt();
        hookSetUidModeInt();
        hookSetModeString();
        hookSetUidModeString();
        sInstalled = true;
        XposedBridge.log(TAG + ": securityperm auto-grant guard installed");
    }

    private static void hookSetModeInt() {
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
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook setMode(int) failed: " + t);
        }
    }

    private static void hookSetUidModeInt() {
        try {
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
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook setUidMode(int) failed: " + t);
        }
    }

    private static void hookSetModeString() {
        try {
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
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook setMode(str) failed: " + t);
        }
    }

    private static void hookSetUidModeString() {
        try {
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
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook setUidMode(str) failed: " + t);
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
            String callerChain = caller();
            XposedBridge.log(TAG + ": appops " + method + " op=" + code + " str=" + opStr
                    + " mode=" + mode + " uid=" + uid + " pkg=" + pkg + " guarded=" + guarded
                    + " caller=" + callerChain);
            if (!guarded || mode != 0 || uid < 10000) {
                return;
            }
            if (isManualUiCaller(callerChain)) {
                XposedBridge.log(TAG + ": manual ui op=" + code + " pkg=" + pkg + " uid=" + uid
                        + " allowed");
                return;
            }
            if (!isSystemApp(param, pkg)) {
                XposedBridge.log(TAG + ": block auto-grant op=" + code + " pkg=" + pkg + " uid=" + uid
                        + " via " + method);
                param.setResult(null);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": guard failed: " + t);
        }
    }

    private static boolean isManualUiCaller(String callerChain) {
        if (callerChain == null || callerChain.isEmpty()) {
            return false;
        }
        String head = callerChain;
        int idx = head.indexOf(" <- ");
        if (idx > 0) {
            head = head.substring(0, idx);
        }
        int hashIdx = head.indexOf('#');
        String cls = hashIdx > 0 ? head.substring(0, hashIdx) : head;
        String meth = hashIdx > 0 ? head.substring(hashIdx + 1) : "";
        if (cls.contains(".permission.ui.") || cls.contains(".ui.handheld.")) {
            return true;
        }
        return meth.equals("onClick") || meth.equals("onCheckedChanged")
                || meth.equals("onItemSelected") || meth.equals("onOptionsItemSelected")
                || meth.equals("onPreferenceChange") || meth.equals("onPreferenceClick");
    }

    private static boolean isSystemApp(MethodHookParam param, String pkg) {
        if (pkg == null) {
            return false;
        }
        try {
            Context ctx = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
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

    private static String caller() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (StackTraceElement e : st) {
            String cls = e.getClassName();
            if (cls.startsWith("com.fuckcospm") || cls.startsWith("de.robv")) {
                continue;
            }
            if (cls.startsWith("com.oplus") || cls.startsWith("android.app.AppOpsManager")
                    || cls.startsWith("android.app.Instrumentation")
                    || cls.startsWith("android.os.Binder") || cls.startsWith("android.app.IActivityManager")) {
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
