package dev.huecoder.handleassist;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.UserHandle;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Replaces ColorOS's Breeno triggers with the action picked in the settings screen:
 * - gesture bar long-press, handled in SystemUI by OplusOcrScreenServiceHandler (Breeno screen recognition);
 * - power key long-press, handled in system_server by PhoneWindowManagerExtImpl.startSpeech (Breeno voice).
 * Actions run in system_server, the only side allowed to start Circle to Search; SystemUI forwards its press there.
 */
public class HandleHook implements IXposedHookLoadPackage {
    private static final String HANDLER = "com.oplus.systemui.navigationbar.ocrscreen.OplusOcrScreenServiceHandler";
    private static final String PWM_EXT = "com.android.server.policy.PhoneWindowManagerExtImpl";
    private static final String ACTION_TRIGGER = Prefs.PACKAGE + ".TRIGGER";
    private static final String EXTRA_TRIGGER = "trigger";
    // Only SystemUI (and the system) holds it, so nothing else can fire actions through the receiver.
    private static final String SENDER_PERMISSION = "android.permission.STATUS_BAR_SERVICE";
    // PhoneWindowManagerExtImpl startSource values
    private static final int SOURCE_POWER = 1024;
    private static final int SOURCE_HOME = 4;
    // VoiceInteractionSession.SHOW_SOURCE_ASSIST_GESTURE
    private static final int SHOW_SOURCE_ASSIST_GESTURE = 4;
    // AssistManager.INVOCATION_TYPE_NAV_HANDLE_LONG_PRESS / INVOCATION_TYPE_POWER_BUTTON_LONG_PRESS
    private static final int INVOCATION_TYPE_NAV_HANDLE_LONG_PRESS = 5;
    private static final int INVOCATION_TYPE_POWER_BUTTON_LONG_PRESS = 6;
    // ContextualSearchManager.ENTRYPOINT_LONG_PRESS_NAV_HANDLE / ENTRYPOINT_SYSTEM_ACTION
    private static final int ENTRYPOINT_LONG_PRESS_NAV_HANDLE = 1;
    private static final int ENTRYPOINT_SYSTEM_ACTION = 9;

    private static XSharedPreferences prefs;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if ("android".equals(lpparam.packageName)) hookSystemServer(lpparam.classLoader);
        else if ("com.android.systemui".equals(lpparam.packageName)) hookGestureBar(lpparam.classLoader);
    }

    private static synchronized String action(String trigger) {
        if (prefs == null) prefs = new XSharedPreferences(Prefs.PACKAGE, Prefs.FILE);
        if (prefs.hasFileChanged()) prefs.reload();
        return prefs.getString(Prefs.actionKey(trigger), Prefs.ACTION_ASSISTANT);
    }

    private static synchronized String app(String trigger) {
        return prefs.getString(Prefs.appKey(trigger), null);
    }

    // ---- SystemUI ----

    private static void hookGestureBar(ClassLoader cl) {
        // Stock code binds the Breeno service here so it is warm by the time the press is long enough.
        XposedHelpers.findAndHookMethod(HANDLER, cl, "onPreLongPress", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!Prefs.ACTION_STOCK.equals(action(Prefs.TRIGGER_HANDLE))) param.setResult(null);
            }
        });

        XposedHelpers.findAndHookMethod(HANDLER, cl, "onLongPressed", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                String action = action(Prefs.TRIGGER_HANDLE);
                if (Prefs.ACTION_STOCK.equals(action)) return;
                param.setResult(null);
                Object handler = param.thisObject;
                Context context = (Context) XposedHelpers.getObjectField(handler, "context");
                if (!Prefs.ACTION_NONE.equals(action)) {
                    vibrate(cl, context);
                    context.sendBroadcast(new Intent(ACTION_TRIGGER).setPackage("android")
                            .putExtra(EXTRA_TRIGGER, Prefs.TRIGGER_HANDLE));
                }
                // Lets the nav bar drop this handler, as the stock flow does after starting Breeno.
                ((Handler) XposedHelpers.getObjectField(handler, "gestureBGHandler"))
                        .post(() -> XposedHelpers.callMethod(handler, "unbindService"));
            }
        });
    }

    /** Same haptic as stock onLongPressed; devices with wave vibration already buzzed in onShowPress. */
    private static void vibrate(ClassLoader cl, Context context) {
        try {
            Class<?> features = XposedHelpers.findClass("com.oplusos.systemui.common.feature.CustomizeFeatureOption", cl);
            if (XposedHelpers.getStaticBooleanField(features, "sIsSupportHomeWaveVibration")) return;
            Object helper = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("com.oplus.systemui.navigationbar.gesture.VibrationHelper", cl), "getInstance", context);
            XposedHelpers.callMethod(helper, "onPerformHapticFeedback");
        } catch (Throwable t) {
            XposedBridge.log("HandleAssist: vibrate failed: " + t);
        }
    }

    // ---- system_server ----

    private static void hookSystemServer(ClassLoader cl) {
        XposedHelpers.findAndHookMethod("com.android.server.policy.PhoneWindowManager", cl, "systemReady", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                Context context = (Context) XposedHelpers.getObjectField(param.thisObject, "mContext");
                Handler background = (Handler) XposedHelpers.callStaticMethod(
                        XposedHelpers.findClass("com.android.internal.os.BackgroundThread", cl), "getHandler");
                BroadcastReceiver receiver = new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context c, Intent intent) {
                        String trigger = intent.getStringExtra(EXTRA_TRIGGER);
                        if (Prefs.TRIGGER_HANDLE.equals(trigger)) run(context, trigger, action(trigger));
                    }
                };
                context.registerReceiver(receiver, new IntentFilter(ACTION_TRIGGER), SENDER_PERMISSION, background,
                        Context.RECEIVER_EXPORTED);
            }
        });

        // startSpeech(deviceId, startSource, eventTime) is where every Breeno key trigger ends up; the stock
        // guards before it (setup wizard, business mode, power-key speech switch) stay in place.
        XposedHelpers.findAndHookMethod(PWM_EXT, cl, "startSpeech", int.class, int.class, long.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                int source = (int) param.args[1];
                // Home long-press only exists with three-button navigation, where it plays the gesture bar's role.
                String trigger = source == SOURCE_POWER ? Prefs.TRIGGER_POWER : source == SOURCE_HOME ? Prefs.TRIGGER_HANDLE : null;
                if (trigger == null) return;
                String action = action(trigger);
                if (Prefs.ACTION_STOCK.equals(action)) return;
                param.setResult(null);
                Object ext = param.thisObject;
                // Stock dedup: sendSpeechMessage clears it, so one press runs one action.
                if (XposedHelpers.getBooleanField(ext, "mSpeechLongPressHandled")) return;
                XposedHelpers.setBooleanField(ext, "mSpeechLongPressHandled", true);
                XposedHelpers.setBooleanField(ext, "mClearPowerKeyDownRecord", true);
                if (Prefs.ACTION_NONE.equals(action)) return;
                try {
                    Object wrapper = XposedHelpers.callMethod(XposedHelpers.getObjectField(ext, "mBase"), "getWrapper");
                    XposedHelpers.callMethod(wrapper, "performHapticFeedback", 0, "Speech - Long Press");
                } catch (Throwable t) {
                    XposedBridge.log("HandleAssist: power haptic failed: " + t);
                }
                run((Context) XposedHelpers.getObjectField(ext, "mContext"), trigger, action);
            }
        });
    }

    private static void run(Context context, String trigger, String action) {
        boolean handle = Prefs.TRIGGER_HANDLE.equals(trigger);
        try {
            switch (action) {
                case Prefs.ACTION_CIRCLE_SEARCH:
                    // The public startContextualSearch uses checkCallingPermission, which always denies a call that is
                    // not an IPC; ours runs inside system_server, so skip straight to what it does after the check.
                    Object config = XposedHelpers.getStaticObjectField(
                            Class.forName("android.app.contextualsearch.ContextualSearchConfig"), "DEFAULT_CONFIG");
                    XposedHelpers.callMethod(service("contextual_search", "android.app.contextualsearch.IContextualSearchManager"),
                            "startContextualSearchInternal", handle ? ENTRYPOINT_LONG_PRESS_NAV_HANDLE : ENTRYPOINT_SYSTEM_ACTION, config);
                    break;
                case Prefs.ACTION_APP:
                    launchApp(context, app(trigger));
                    break;
                case Prefs.ACTION_POWER_MENU:
                    callService("window", "android.view.IWindowManager", "showGlobalActions", null);
                    break;
                default:
                    Bundle args = new Bundle();
                    args.putInt("invocation_type", handle ? INVOCATION_TYPE_NAV_HANDLE_LONG_PRESS : INVOCATION_TYPE_POWER_BUTTON_LONG_PRESS);
                    callService("voiceinteraction", "com.android.internal.app.IVoiceInteractionManagerService",
                            "showSessionForActiveService", args);
            }
        } catch (Throwable t) {
            XposedBridge.log("HandleAssist: " + action + " failed");
            XposedBridge.log(t);
        }
    }

    private static void launchApp(Context context, String pkg) {
        Intent intent = pkg == null ? null : context.getPackageManager().getLaunchIntentForPackage(pkg);
        if (intent == null) {
            XposedBridge.log("HandleAssist: no launchable app " + pkg);
            return;
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        XposedHelpers.callMethod(context, "startActivityAsUser", intent,
                XposedHelpers.getStaticObjectField(UserHandle.class, "CURRENT"));
    }

    /**
     * Calls a system service method by name. Signatures grow across releases (attribution tag, callbacks,
     * configs), so ints get {@code arg} when it is an Integer, a Bundle gets {@code arg} when it is a Bundle,
     * the show-source flag fills the remaining int of showSessionForActiveService, and everything else is null.
     */
    private static void callService(String name, String iface, String method, Object arg) throws Exception {
        Object service = service(name, iface);
        for (Method m : service.getClass().getMethods()) {
            if (!m.getName().equals(method)) continue;
            Class<?>[] types = m.getParameterTypes();
            Object[] values = new Object[types.length];
            for (int i = 0; i < types.length; i++) {
                if (types[i] == Bundle.class && arg instanceof Bundle) values[i] = arg;
                else if (types[i] == int.class) values[i] = arg instanceof Integer ? arg : SHOW_SOURCE_ASSIST_GESTURE;
            }
            if (Boolean.FALSE.equals(m.invoke(service, values))) XposedBridge.log("HandleAssist: " + method + " refused");
            return;
        }
        throw new NoSuchMethodException(iface + "." + method);
    }

    /** In system_server this is the service's own Binder stub, so private methods of the implementation are reachable. */
    private static Object service(String name, String iface) throws Exception {
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, name);
        return Class.forName(iface + "$Stub").getMethod("asInterface", IBinder.class).invoke(null, binder);
    }
}
