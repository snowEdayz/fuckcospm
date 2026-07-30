package io.github.colorospermissionwhitelisthook;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.oplus.securitypermission";
    private static final String TAG = "[ColorOSPermissionWhitelistHook]";

    private static final String SP_NAME = "application_control_center";
    private static final String VERSION_KEY = "activity_start_white_list_version";
    private static final String POLICY_REVISION_KEY =
            "coloros_permission_whitelist_hook_policy_revision";
    private static final String SANITIZED_VERSION_KEY =
            "coloros_permission_whitelist_hook_sanitized_version";
    private static final int POLICY_REVISION = 1;
    private static final String MD5_POLICY_SUFFIX =
            "|ColorOSPermissionWhitelistHook:" + POLICY_REVISION;

    private static final String KEY_MD5 = "md5";
    private static final String KEY_SRC_PKG = "src_pkg";
    private static final String KEY_DST_PKG = "dst_pkg";
    private static final String KEY_ACTIVITY = "activity";
    private static final String KEY_ACTION = "action";
    private static final String KEY_MINI_PROGRAM_WHITE_LIST = "mini_program_white_list";
    private static final String KEY_MINI_PROGRAM_PUT_TYPE = "mini_program_put_type";
    private static final String KEY_MINI_PROGRAM_USER = "mini_program_user";

    // In system_server, put type 1 executes MiniProgramController.mWhiteList.clear().
    private static final int MINI_PROGRAM_PUT_TYPE_CLEAR_ALL = 1;
    private static final int ALL_USERS = -1;
    private static final int LOG_CHUNK_SIZE = 3000;

    private static final Set<String> REMOVED_DST_PACKAGES = new HashSet<>(Arrays.asList(
            "com.tencent.mobileqq",
            "com.eg.android.AlipayGphone"
    ));

    private static String processName = TARGET_PACKAGE;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        processName = lpparam.processName;
        hookActivityWhitelistRefresh(lpparam.classLoader);
        hookWhitelistMerge(lpparam.classLoader);
        hookWhitelistDelivery(lpparam.classLoader);
        log("hooks installed");
    }

    private static void hookActivityWhitelistRefresh(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "oa.b",
                    classLoader,
                    "g",
                    Context.class,
                    boolean.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            prepareWhitelistRefresh((Context) param.args[0]);
                        }

                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            if (!param.hasThrowable()) {
                                recordSanitizedVersion((Context) param.args[0]);
                            }
                        }
                    }
            );
        } catch (Throwable throwable) {
            logError("failed to hook oa.b.g(Context, boolean)", throwable);
        }
    }

    private static void hookWhitelistMerge(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "oa.b",
                    classLoader,
                    "b",
                    Bundle.class,
                    Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            Bundle merged = (Bundle) param.getResult();
                            if (merged == null) {
                                log("oa.b.b merged bundle = null");
                                return;
                            }

                            logWhitelistBundle("oa.b.b merged/original", merged);
                            int removed = removeBlockedDestinations(merged);
                            if (removed > 0) {
                                log("oa.b.b removed dst_pkg entries=" + removed);
                                logWhitelistBundle("oa.b.b merged/effective", merged);
                            }
                        }
                    }
            );
        } catch (Throwable throwable) {
            logError("failed to hook oa.b.b(Bundle, Bundle)", throwable);
        }
    }

    private static void hookWhitelistDelivery(ClassLoader classLoader) {
        try {
            XposedHelpers.findAndHookMethod(
                    "k9.c",
                    classLoader,
                    "b",
                    Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            Bundle original = (Bundle) param.args[0];
                            if (original == null) {
                                return;
                            }

                            Bundle sanitized = new Bundle(original);
                            int removedDst = removeBlockedDestinations(sanitized);
                            String originalMd5 = sanitized.getString(KEY_MD5);
                            if (originalMd5 != null && !originalMd5.isEmpty()) {
                                sanitized.putString(KEY_MD5, originalMd5 + MD5_POLICY_SUFFIX);
                            }
                            ArrayList<String> incomingMiniPrograms =
                                    sanitized.getStringArrayList(KEY_MINI_PROGRAM_WHITE_LIST);
                            int removedMiniPrograms = incomingMiniPrograms == null
                                    ? 0 : incomingMiniPrograms.size();

                            sanitized.putStringArrayList(
                                    KEY_MINI_PROGRAM_WHITE_LIST,
                                    new ArrayList<String>()
                            );
                            sanitized.putInt(
                                    KEY_MINI_PROGRAM_PUT_TYPE,
                                    MINI_PROGRAM_PUT_TYPE_CLEAR_ALL
                            );
                            sanitized.putInt(KEY_MINI_PROGRAM_USER, ALL_USERS);
                            param.args[0] = sanitized;

                            log("k9.c.b sanitized: removed dst_pkg=" + removedDst
                                    + ", discarded incoming mini-program rules="
                                    + removedMiniPrograms
                                    + ", forcing mini-program clear-all"
                                    + (originalMd5 == null
                                    ? ""
                                    : ", md5=" + originalMd5 + MD5_POLICY_SUFFIX));
                        }
                    }
            );
        } catch (Throwable throwable) {
            logError("failed to hook k9.c.b(Bundle)", throwable);
        }
    }

    private static void prepareWhitelistRefresh(Context context) {
        if (context == null) {
            return;
        }

        try {
            SharedPreferences preferences = context.getSharedPreferences(
                    SP_NAME,
                    Context.MODE_PRIVATE
            );
            String currentVersion = preferences.getString(VERSION_KEY, null);
            String sanitizedVersion = preferences.getString(SANITIZED_VERSION_KEY, null);
            boolean policyMatches =
                    preferences.getInt(POLICY_REVISION_KEY, 0) == POLICY_REVISION;
            boolean versionMatches = currentVersion == null
                    ? sanitizedVersion == null
                    : currentVersion.equals(sanitizedVersion);
            if (policyMatches && versionMatches) {
                return;
            }

            boolean hadVersion = preferences.contains(VERSION_KEY);
            boolean committed = preferences.edit()
                    .remove(VERSION_KEY)
                    .commit();
            log("one-time whitelist refresh: hadVersion=" + hadVersion
                    + ", committed=" + committed
                    + ", policyRevision=" + POLICY_REVISION
                    + ", currentVersion=" + currentVersion
                    + ", lastSanitizedVersion=" + sanitizedVersion);
        } catch (Throwable throwable) {
            // Credential-protected storage can be unavailable before the user unlocks.
            logError("failed to prepare one-time whitelist refresh", throwable);
        }
    }

    private static void recordSanitizedVersion(Context context) {
        if (context == null) {
            return;
        }

        try {
            SharedPreferences preferences = context.getSharedPreferences(
                    SP_NAME,
                    Context.MODE_PRIVATE
            );
            String currentVersion = preferences.getString(VERSION_KEY, null);
            if (currentVersion == null) {
                return;
            }

            boolean committed = preferences.edit()
                    .putInt(POLICY_REVISION_KEY, POLICY_REVISION)
                    .putString(SANITIZED_VERSION_KEY, currentVersion)
                    .commit();
            log("recorded sanitized whitelist version=" + currentVersion
                    + ", committed=" + committed);
        } catch (Throwable throwable) {
            logError("failed to record sanitized whitelist version", throwable);
        }
    }

    private static int removeBlockedDestinations(Bundle bundle) {
        ArrayList<String> destinations = bundle.getStringArrayList(KEY_DST_PKG);
        if (destinations == null || destinations.isEmpty()) {
            return 0;
        }

        ArrayList<String> filtered = new ArrayList<>(destinations.size());
        int removed = 0;
        for (String destination : destinations) {
            if (REMOVED_DST_PACKAGES.contains(destination)) {
                removed++;
            } else {
                filtered.add(destination);
            }
        }
        if (removed > 0) {
            bundle.putStringArrayList(KEY_DST_PKG, filtered);
        }
        return removed;
    }

    private static void logWhitelistBundle(String label, Bundle bundle) {
        logLong(label + " " + KEY_MD5 + "=" + String.valueOf(bundle.get(KEY_MD5)));
        logVisibleList(label, bundle, KEY_SRC_PKG);
        logVisibleList(label, bundle, KEY_DST_PKG);
        logVisibleList(label, bundle, KEY_ACTIVITY);
        logVisibleList(label, bundle, KEY_ACTION);
    }

    private static void logVisibleList(String label, Bundle bundle, String key) {
        ArrayList<String> values = bundle.getStringArrayList(key);
        logLong(label + " " + key + "=" + String.valueOf(values));
    }

    private static void logLong(String message) {
        if (message == null || message.length() <= LOG_CHUNK_SIZE) {
            log(String.valueOf(message));
            return;
        }

        int part = 1;
        for (int start = 0; start < message.length(); start += LOG_CHUNK_SIZE) {
            int end = Math.min(message.length(), start + LOG_CHUNK_SIZE);
            log("[part " + part + "] " + message.substring(start, end));
            part++;
        }
    }

    private static void log(String message) {
        XposedBridge.log(TAG + " [" + processName + "] " + message);
    }

    private static void logError(String message, Throwable throwable) {
        log(message + ": " + throwable);
        XposedBridge.log(throwable);
    }
}
