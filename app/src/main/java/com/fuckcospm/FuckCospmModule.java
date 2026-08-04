package com.fuckcospm;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Binder;
import android.os.UserHandle;
import android.util.Pair;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class FuckCospmModule implements IXposedHookLoadPackage {

    private static final String TAG = "fuckcospm";

    private static final String[] TARGET_PACKAGES = {
            "com.heytap.market",
            "com.nearme.instant.platform"
    };

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!"android".equals(lpparam.packageName)) {
            return;
        }
        ClassLoader cl = lpparam.classLoader;
        try {
            final Class<?> confirmManagerClass = XposedHelpers.findClass(
                    "com.android.server.wm.OplusAppStartConfirmManager", cl);
            final Class<?> activityRecordClass = XposedHelpers.findClass(
                    "com.android.server.wm.ActivityRecord", cl);
            final Class<?> activityOptionsClass = XposedHelpers.findClass(
                    "android.app.ActivityOptions", cl);
            final Class<?> profilerInfoClass = XposedHelpers.findClass(
                    "android.app.ProfilerInfo", cl);

            XposedHelpers.findAndHookMethod(
                    confirmManagerClass,
                    "checkStartActivityForConfirm",
                    activityRecordClass,
                    ActivityInfo.class,
                    Intent.class,
                    int.class,
                    int.class,
                    String.class,
                    activityOptionsClass,
                    profilerInfoClass,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.args[1] == null || param.args[2] == null) {
                                return;
                            }
                            Object aInfoApp = XposedHelpers.getObjectField(param.args[1], "applicationInfo");
                            if (aInfoApp == null) {
                                return;
                            }
                            String targetPkg = (String) XposedHelpers.getObjectField(aInfoApp, "packageName");
                            if (!isTarget(targetPkg)) {
                                return;
                            }
                            Object manager = param.thisObject;
                            String callerPkg = (String) param.args[5];
                            int callerUid = ((Integer) param.args[4]).intValue();
                            if (callerPkg == null || callerPkg.equals(targetPkg)
                                    || appId(callerUid) < 10000) {
                                return;
                            }
                            if (param.args[0] != null
                                    && ((Boolean) XposedHelpers.callMethod(param.args[0], "isActivityTypeHome")).booleanValue()) {
                                return;
                            }
                            XposedBridge.log(TAG + ": forcing confirm dialog, calleePkg=" + targetPkg
                                    + ", callerPkg=" + callerPkg);
                            try {
                                int calleeUid = ((Integer) XposedHelpers.getObjectField(aInfoApp, "uid")).intValue();
                                int userId = userId(calleeUid);

                                Intent confirmIntent = (Intent) XposedHelpers.callMethod(
                                        manager,
                                        "getCheckConformIntent",
                                        callerPkg,
                                        param.args[1],
                                        param.args[2],
                                        param.args[3],
                                        Integer.valueOf(callerUid),
                                        param.args[0],
                                        Integer.valueOf(userId),
                                        Integer.valueOf(0),
                                        param.args[6]);

                                Object mAtms = XposedHelpers.getObjectField(manager, "mAtms");
                                Object supervisor = XposedHelpers.getObjectField(mAtms, "mTaskSupervisor");
                                Object resolved = XposedHelpers.callMethod(
                                        supervisor,
                                        "resolveActivity",
                                        confirmIntent,
                                        null,
                                        Integer.valueOf(0),
                                        param.args[7],
                                        Integer.valueOf(userId(callerUid)),
                                        Integer.valueOf(callerUid),
                                        Integer.valueOf(Binder.getCallingUid()));
                                if (resolved != null) {
                                    param.setResult(new Pair<>(new Pair<>(confirmIntent, resolved), Boolean.FALSE));
                                } else {
                                    XposedHelpers.setObjectField(manager, "mHasConformActivity", Boolean.FALSE);
                                    param.setResult(null);
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": forced confirm failed: " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hook installed");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook install failed: " + t);
        }
    }

    private static boolean isTarget(String calleePkg) {
        if (calleePkg == null) {
            return false;
        }
        for (String pkg : TARGET_PACKAGES) {
            if (pkg.equals(calleePkg)) {
                return true;
            }
        }
        return false;
    }

    private static int appId(int uid) {
        return ((Integer) XposedHelpers.callStaticMethod(UserHandle.class, "getAppId", Integer.valueOf(uid))).intValue();
    }

    private static int userId(int uid) {
        return ((Integer) XposedHelpers.callStaticMethod(UserHandle.class, "getUserId", Integer.valueOf(uid))).intValue();
    }
}
