package com.fuckcospm;

import android.os.Bundle;

import java.util.ArrayList;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadHook;

/**
 * 在 system_server 中拦截 OPPO Activity 启动白名单更新：
 * 当 putActivityStartWhiteList(Bundle) 被调用时，从 Bundle 的
 * src_pkg / dst_pkg / src_and_dst 中剔除目标包名。
 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "FuckCosPM";

    private static final String[] BLOCKED_PACKAGES = {
            "com.eg.android.AlipayGphone",
            "com.heytap.market"
    };

    private static final String SRC_PKG = "src_pkg";
    private static final String DST_PKG = "dst_pkg";
    private static final String SRC_AND_DST = "src_and_dst";

    private static final String[] HOOK_CLASSES = {
            "com.android.server.oplus.SecurityPermissionService",
            "com.android.server.am.OplusSecurityPermissionManager"
    };

    @Override
    public void handleLoadPackage(XC_LoadHook.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }
        for (String className : HOOK_CLASSES) {
            hookWhiteListMethod(lpparam.classLoader, className);
        }
    }

    private void hookWhiteListMethod(ClassLoader cl, String className) {
        try {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, cl);
            if (clazz == null) {
                XposedBridge.log(TAG + ": class not found: " + className);
                return;
            }
            XposedHelpers.hookAllMethods(clazz, "putActivityStartWhiteList", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    if (param.args.length > 0 && param.args[0] instanceof Bundle) {
                        sanitize((Bundle) param.args[0]);
                    }
                }
            });
            XposedBridge.log(TAG + ": hooked " + className);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook " + className + " failed: " + t);
        }
    }

    private void sanitize(Bundle bundle) {
        if (bundle == null) {
            return;
        }
        removeFromList(bundle, SRC_PKG);
        removeFromList(bundle, DST_PKG);
        removeSrcAndDst(bundle);
    }

    private void removeFromList(Bundle bundle, String key) {
        ArrayList<String> list = bundle.getStringArrayList(key);
        if (list == null || list.isEmpty()) {
            return;
        }
        boolean changed = list.removeIf(this::isBlocked);
        if (changed) {
            bundle.putStringArrayList(key, list);
            XposedBridge.log(TAG + ": removed from " + key + ", packages=" + joined());
        }
    }

    private void removeSrcAndDst(Bundle bundle) {
        if (!bundle.containsKey(SRC_AND_DST)) {
            return;
        }
        ArrayList<String> pair = bundle.getStringArrayList(SRC_AND_DST);
        if (pair == null || pair.size() != 2) {
            return;
        }
        if (isBlocked(pair.get(0)) || isBlocked(pair.get(1))) {
            bundle.remove(SRC_AND_DST);
            XposedBridge.log(TAG + ": removed src_and_dst entry, packages=" + joined());
        }
    }

    private boolean isBlocked(String pkg) {
        if (pkg == null) {
            return false;
        }
        for (String blocked : BLOCKED_PACKAGES) {
            if (blocked.equals(pkg)) {
                return true;
            }
        }
        return false;
    }

    private String joined() {
        return String.join(", ", BLOCKED_PACKAGES);
    }
}
