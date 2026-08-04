package com.fuckcospm;

import android.content.Context;
import android.content.pm.ApplicationInfo;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodHook.MethodHookParam;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class AppOpsServiceGuard {

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
        Class<?> serviceClass = null;
        String[] candidates = {
                "com.android.server.appop.AppOpsService",
                "com.android.server.AppOpsService",
                "com.android.server.appop.OplusAppOpsService",
                "com.android.server.oplus.appop.OplusAppOpsService"
        };
        for (String name : candidates) {
            try {
                serviceClass = XposedHelpers.findClass(name, cl);
                if (serviceClass != null) {
                    XposedBridge.log(TAG + ": appops-service class found: " + name);
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
        if (serviceClass == null) {
            XposedBridge.log(TAG + ": appops-service class not found, skip guard");
            return;
        }
        final Class<?> svc = serviceClass;
        hook(svc, "setMode", new Class<?>[]{int.class, int.class, String.class, int.class}, "setMode(int)");
        hook(svc, "setUidMode", new Class<?>[]{int.class, int.class, int.class}, "setUidMode(int)");
        hook(svc, "setMode", new Class<?>[]{String.class, int.class, String.class, int.class}, "setMode(str)");
        hook(svc, "setUidMode", new Class<?>[]{String.class, int.class, int.class}, "setUidMode(str)");
        hook(svc, "setModeFromPermission", new Class<?>[]{String.class, int.class, String.class, int.class}, "setModeFromPermission");
        hook(svc, "setUidModeFromPermission", new Class<?>[]{String.class, int.class, int.class}, "setUidModeFromPermission");
        sInstalled = true;
        XposedBridge.log(TAG + ": appops-service guard installed");
    }

    private static void hook(final Class<?> svc, final String method, final Class<?>[] sig, final String label) {
        try {
            Object[] args = new Object[sig.length + 1];
            System.arraycopy(sig, 0, args, 0, sig.length);
            args[sig.length] = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    blockAutoGrant(param, label);
                }
            };
            XposedHelpers.findAndHookMethod(svc, method, args);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook AppOpsService." + method + " failed: " + t);
        }
    }

    private static void blockAutoGrant(MethodHookParam param, String method) {
        try {
            Object[] args = param.args;
            int code;
            String strOp = null;
            if (args[0] instanceof String) {
                strOp = (String) args[0];
                Integer parsed = strToOp(strOp);
                code = parsed == null ? -1 : parsed.intValue();
            } else {
                code = ((Integer) args[0]).intValue();
            }
            int uid = ((Integer) args[1]).intValue();
            int mode = ((Integer) args[args.length - 1]).intValue();
            String pkg = null;
            for (Object arg : args) {
                if (arg instanceof String && !arg.equals(strOp)) {
                    pkg = (String) arg;
                    break;
                }
            }
            boolean guarded = strOp == null || code >= 0 ? isGuardedOp(code) : false;
            boolean direct = isInternalDirectCall();
            if (guarded) {
                XposedBridge.log(TAG + ": appopssvc " + method + " op=" + code + " str=" + strOp
                        + " mode=" + mode + " uid=" + uid + " pkg=" + pkg + " guarded=" + guarded
                        + " direct=" + direct);
            }
            if (!guarded || !direct || mode != 0 || uid < 10000) {
                return;
            }
            if (!isSystemApp(pkg)) {
                XposedBridge.log(TAG + ": block svc auto-grant op=" + code + " pkg=" + pkg + " uid=" + uid
                        + " via " + method);
                param.setResult(null);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": svc guard failed: " + t);
        }
    }

    private static boolean isInternalDirectCall() {
        StackTraceElement[] st = Thread.currentThread().getStackTrace();
        for (StackTraceElement e : st) {
            String cls = e.getClassName();
            String meth = e.getMethodName();
            if (cls.equals("android.os.Binder") && meth.equals("execTransactInternal")) {
                return false;
            }
            if (cls.startsWith("android.os.Binder") && meth.startsWith("execTransact")) {
                return false;
            }
            if (cls.endsWith("IAppOpsService$Stub") && meth.equals("onTransact")) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSystemApp(String pkg) {
        if (pkg == null) {
            return false;
        }
        try {
            Context ctx = getSystemContext();
            if (ctx == null) {
                return false;
            }
            ApplicationInfo appInfo = ctx.getPackageManager().getApplicationInfo(pkg, 0);
            return (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Throwable t) {
            return false;
        }
    }

    private static Context getSystemContext() {
        try {
            Object at = XposedHelpers.callStaticMethod(
                    Class.forName("android.app.ActivityThread"), "currentActivityThread");
            if (at != null) {
                Context ctx = (Context) XposedHelpers.callMethod(at, "getSystemContext");
                if (ctx != null) {
                    return ctx;
                }
            }
        } catch (Throwable t) {
        }
        try {
            Object at2 = XposedHelpers.callStaticMethod(
                    Class.forName("android.app.ActivityThread"), "systemMain");
            if (at2 != null) {
                return (Context) XposedHelpers.callMethod(at2, "getSystemContext");
            }
        } catch (Throwable t) {
        }
        return null;
    }

    private static Integer strToOp(String opStr) {
        try {
            return (Integer) XposedHelpers.callStaticMethod(Class.forName("android.app.AppOpsManager"),
                    "strOpToOp", opStr);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isGuardedOp(int code) {
        return code == OP_SYSTEM_ALERT_WINDOW
                || code == OP_START_ACTIVITIES_FROM_BACKGROUND
                || code == OP_START_ACTIVITY_FROM_BACKGROUND
                || code == OP_CHANGE_WIFI_STATE;
    }
}
