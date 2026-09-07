/*
 * Hidden Settings Injector — LSPosed / Xposed module
 *
 * Adds shortcut Preference entries that launch already-existing (but not
 * normally reachable) Activities of the stock Settings app on Android 9
 * (API 28). Purely additive: it inserts list items into the Developer
 * options / System pages of com.android.settings. It hooks nothing outside
 * com.android.settings and modifies no system behaviour.
 *
 * Author: Nyan<(Nyan4)
 * License: MIT OR Apache-2.0 (dual-licensed, see LICENSE files)
 */
package com.nyan4.hiddensettings;

import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import java.lang.reflect.Constructor;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class HiddenSettingsInjector implements IXposedHookLoadPackage {

    private static final String TAG = "HiddenSettingsInjector";
    private static final String TARGET_PACKAGE = "com.android.settings";

    // Fragment classes for Android 9 (API 28), verified against
    // android-9.0.0_r46 AOSP sources.
    private static final String DEV_FRAGMENT =
            "com.android.settings.development.DevelopmentSettingsDashboardFragment";
    private static final String SYSTEM_FRAGMENT =
            "com.android.settings.system.SystemDashboardFragment";

    // Marker so we never add our items twice to the same screen instance
    // (onActivityCreated can be reached again after a configuration change).
    private static final String ADDED_FLAG = "nyan4_hidden_items_added";

    // ---- Item definitions -------------------------------------------------

    private static final class Item {
        final String title;
        final String pkg;
        final String cls;

        Item(String title, String pkg, String cls) {
            this.title = title;
            this.pkg = pkg;
            this.cls = cls;
        }
    }

    // Items for the "開発者向けオプション" (Developer options) page.
    private static final Item[] DEV_ITEMS = new Item[]{
            new Item("上位互換システムUIデモモード",
                    "com.android.systemui",
                    "com.android.systemui.DemoMode"),
            new Item("WebView DevUI",
                    "com.google.android.webview",
                    "org.chromium.android_webview.devui.MainActivity"),
            new Item("画面ロックテスト",
                    "com.android.settings",
                    "com.mediatek.settings.inputmethod.VowKeyguardConfirm"),
            new Item("Smart Call Forwarding",
                    "com.android.settings",
                    "com.android.settings.Settings$SmartCallFwdActivity"),
            new Item("Advanced Calling",
                    "com.android.settings",
                    "com.android.settings.Settings$AdvancedCallingOptionsActivity"),
            new Item("Wi-Fi Calling",
                    "com.android.settings",
                    "com.android.settings.Settings$AdvancedWifiCallingActivity"),
            new Item("テスト中",
                    "com.android.settings",
                    "com.android.settings.Settings$TestingSettingsActivity"),
    };

    // Items for the "システム" (System) page.
    private static final Item[] SYSTEM_ITEMS = new Item[]{
            new Item("使用統計情報",
                    "com.android.settings",
                    "com.android.settings.UsageStatsActivity"),
            new Item("DRMリセット",
                    "com.android.settings",
                    "com.android.settings.Settings$DrmResetActivity"),
    };

    // ----------------------------------------------------------------------

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        XposedBridge.log(TAG + ": hooking " + lpparam.packageName);

        hookFragment(lpparam, DEV_FRAGMENT, DEV_ITEMS);
        hookFragment(lpparam, SYSTEM_FRAGMENT, SYSTEM_ITEMS);
    }

    /**
     * Hooks onActivityCreated() of the given fragment class. At that point the
     * DashboardFragment has already built its PreferenceScreen (via
     * onCreatePreferences), so we can safely append our own entries.
     */
    private void hookFragment(final LoadPackageParam lpparam,
                              final String fragmentClassName,
                              final Item[] items) {
        try {
            XposedHelpers.findAndHookMethod(
                    fragmentClassName,
                    lpparam.classLoader,
                    "onActivityCreated",
                    Bundle.class,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                injectItems((Fragment) param.thisObject, items);
                            } catch (Throwable t) {
                                // Never let our failure crash the Settings app.
                                XposedBridge.log(TAG + ": injectItems failed on "
                                        + fragmentClassName + ": " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hooked " + fragmentClassName);
        } catch (Throwable t) {
            // Fragment class may not exist on some ROMs — log and move on.
            XposedBridge.log(TAG + ": could not hook " + fragmentClassName + ": " + t);
        }
    }

    /**
     * Appends every {@link Item} as a clickable Preference to the fragment's
     * PreferenceScreen, using reflection against the host app's AndroidX
     * Preference classes (loaded from the Settings classloader).
     */
    private void injectItems(Fragment fragment, Item[] items) throws Throwable {
        // getPreferenceScreen() lives on PreferenceFragmentCompat.
        final Object preferenceScreen =
                XposedHelpers.callMethod(fragment, "getPreferenceScreen");
        if (preferenceScreen == null) {
            XposedBridge.log(TAG + ": preferenceScreen is null, skipping");
            return;
        }

        // Guard against double injection on re-entry (e.g. rotation).
        Object already = XposedHelpers.getAdditionalInstanceField(preferenceScreen, ADDED_FLAG);
        if (Boolean.TRUE.equals(already)) {
            return;
        }
        XposedHelpers.setAdditionalInstanceField(preferenceScreen, ADDED_FLAG, Boolean.TRUE);

        final Context context =
                (Context) XposedHelpers.callMethod(preferenceScreen, "getContext");
        final ClassLoader cl = context.getClassLoader();

        // Resolve AndroidX Preference classes from the host classloader.
        final Class<?> preferenceClass =
                XposedHelpers.findClass("androidx.preference.Preference", cl);
        final Class<?> listenerClass = XposedHelpers.findClass(
                "androidx.preference.Preference$OnPreferenceClickListener", cl);

        final Constructor<?> ctor = preferenceClass.getConstructor(Context.class);

        for (final Item item : items) {
            try {
                final Object pref = ctor.newInstance(context);
                XposedHelpers.callMethod(pref, "setTitle", item.title);
                // Show the target so it's clear what launches; harmless if long.
                XposedHelpers.callMethod(pref, "setSummary", item.pkg + "/" + item.cls);
                // Persist nothing and give a stable key.
                XposedHelpers.callMethod(pref, "setPersistent", false);
                XposedHelpers.callMethod(pref, "setKey",
                        "nyan4_" + item.cls.replace('$', '_'));
                XposedHelpers.callMethod(pref, "setIconSpaceReserved", false);

                final Object listener = java.lang.reflect.Proxy.newProxyInstance(
                        cl,
                        new Class<?>[]{listenerClass},
                        (proxy, method, args) -> {
                            if ("onPreferenceClick".equals(method.getName())) {
                                launch(context, item);
                                return Boolean.TRUE;
                            }
                            if ("equals".equals(method.getName())) {
                                return proxy == args[0];
                            }
                            if ("hashCode".equals(method.getName())) {
                                return System.identityHashCode(proxy);
                            }
                            if ("toString".equals(method.getName())) {
                                return "Nyan4ClickListener(" + item.title + ")";
                            }
                            return null;
                        });

                XposedHelpers.callMethod(pref, "setOnPreferenceClickListener", listener);

                // preferenceScreen.addPreference(pref)
                XposedHelpers.callMethod(preferenceScreen, "addPreference", pref);
            } catch (Throwable t) {
                XposedBridge.log(TAG + ": failed to add item '" + item.title + "': " + t);
            }
        }
    }

    /**
     * Launches the target Activity. Every failure mode is caught and surfaced
     * as a Toast so the Settings app itself never crashes.
     */
    private void launch(Context context, Item item) {
        try {
            Intent intent = new Intent();
            intent.setClassName(item.pkg, item.cls);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable e) {
            String msg = "起動できませんでした: " + item.title
                    + " (" + e.getClass().getSimpleName() + ")";
            try {
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
            } catch (Throwable ignored) {
                // ignore — nothing else we can do safely
            }
            XposedBridge.log(TAG + ": " + msg + " : " + e);
        }
    }
}
