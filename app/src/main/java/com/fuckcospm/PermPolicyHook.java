package com.fuckcospm;

import android.app.AppOpsManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public final class PermPolicyHook {

    private static final String TAG = "fuckcospm";

    public static void install(ClassLoader cl) {
        try {
            final Class<?> permPolicyClass = XposedHelpers.findClass(
                    "com.android.server.pm.OplusRuntimePermGrantPolicyManager", cl);
            final Class<?> androidPackageClass = XposedHelpers.findClass(
                    "com.android.server.pm.pkg.AndroidPackage", cl);
            final Class<?> packageSettingClass = XposedHelpers.findClass(
                    "com.android.server.pm.PackageSetting", cl);

            try {
                XposedHelpers.findAndHookMethod(
                        permPolicyClass,
                        "isPkgInGrantByWhiteList",
                        androidPackageClass,
                        packageSettingClass,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    boolean isSys = ((Boolean) XposedHelpers.callMethod(
                                            param.args[0], "isSystem")).booleanValue();
                                    if (!isSys) {
                                        param.setResult(Boolean.FALSE);
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + ": whitelist check failed: " + t);
                                }
                            }
                        });
                XposedBridge.log(TAG + ": non-system whitelist check hooked");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": whitelist check hook failed: " + t);
            }

            try {
                XposedHelpers.findAndHookMethod(
                        permPolicyClass,
                        "grantOplusOpsPermission",
                        String.class,
                        int.class,
                        String.class,
                        int.class,
                        AppOpsManager.class,
                        PackageManager.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) {
                                try {
                                    String pkg = (String) param.args[0];
                                    PackageManager pm = (PackageManager) param.args[5];
                                    boolean isSys = false;
                                    try {
                                        ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                                        isSys = (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                                    } catch (PackageManager.NameNotFoundException e) {
                                        isSys = false;
                                    }
                                    if (!isSys) {
                                        XposedBridge.log(TAG + ": skip auto-grant op for non-system pkg="
                                                + pkg + ", perm=" + param.args[2]);
                                        param.setResult(null);
                                    }
                                } catch (Throwable t) {
                                    XposedBridge.log(TAG + ": op grant skip failed: " + t);
                                }
                            }
                        });
                XposedBridge.log(TAG + ": non-system op grant hooked");
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": op grant hook failed: " + t);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": perm policy hooks failed: " + t);
        }
    }
}
