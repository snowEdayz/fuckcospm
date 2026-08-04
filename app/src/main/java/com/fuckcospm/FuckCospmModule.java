package com.fuckcospm;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class FuckCospmModule implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("android".equals(lpparam.packageName)) {
            ClassLoader cl = lpparam.classLoader;
            AppStartConfirmHook.install(cl);
            MiniProgramHook.install(cl);
            PermPolicyHook.install(cl);
        } else if ("com.oplusos.securitypermission".equals(lpparam.packageName)) {
            SecurityPermAutoGrantHook.install(lpparam.classLoader);
        }
    }
}
