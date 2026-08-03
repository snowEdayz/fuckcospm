package com.fuckcospm;

import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET_PACKAGE = "com.oplus.securitypermission";
    private static final String TAG = "fuckcospm";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log(TAG + ": module loaded into " + TARGET_PACKAGE);
        try {
            hookPutActivityStartWhiteList(lpparam);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook putActivityStartWhiteList failed: " + t);
        }
    }

    private void hookPutActivityStartWhiteList(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod(
                "k9.c", lpparam.classLoader, "b", Bundle.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        Bundle bundle = (Bundle) param.args[0];
                        XposedBridge.log(TAG + ": ===== putActivityStartWhiteList (push) =====");
                        if (bundle == null) {
                            XposedBridge.log(TAG + ": bundle is null");
                            return;
                        }
                        for (String key : bundle.keySet()) {
                            Object value = bundle.get(key);
                            XposedBridge.log(TAG + ":   " + key + " = " + formatValue(value));
                        }
                        summarize(bundle);
                    }
                });
    }

    private static String formatValue(Object value) {
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(list.get(i));
            }
            sb.append("]");
            return sb.toString();
        }
        return String.valueOf(value);
    }

    private static void summarize(Bundle bundle) {
        List<String> srcAndDst = bundle.getStringArrayList("src_and_dst");
        if (srcAndDst != null) {
            XposedBridge.log(TAG + ": [用户级] 始终允许(src_and_dst): " + srcAndDst);
        }
        List<String> ignored = bundle.getStringArrayList("ignored_activity");
        if (ignored != null) {
            XposedBridge.log(TAG + ": [用户级] 忽略(ignored_activity): " + ignored);
        }
        boolean hasPreset = bundle.containsKey("md5") || bundle.containsKey("src_pkg")
                || bundle.containsKey("dst_pkg") || bundle.containsKey("activity")
                || bundle.containsKey("action");
        if (hasPreset) {
            XposedBridge.log(TAG + ": [预置/云端] 白名单: md5=" + bundle.getString("md5")
                    + " src_pkg=" + bundle.getStringArrayList("src_pkg")
                    + " dst_pkg=" + bundle.getStringArrayList("dst_pkg")
                    + " activity=" + bundle.getStringArrayList("activity")
                    + " action=" + bundle.getStringArrayList("action"));
        }
    }
}
