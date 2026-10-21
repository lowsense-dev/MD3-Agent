package dev.huecoder.handleassist;

/** Keys shared by the settings screen (writer) and the hooks (readers through XSharedPreferences). */
public final class Prefs {
    public static final String PACKAGE = "dev.huecoder.handleassist";
    public static final String FILE = "settings";

    public static final String TRIGGER_HANDLE = "handle";
    public static final String TRIGGER_POWER = "power";

    public static final String ACTION_ASSISTANT = "assistant";
    public static final String ACTION_CIRCLE_SEARCH = "circle_search";
    public static final String ACTION_APP = "app";
    public static final String ACTION_POWER_MENU = "power_menu";
    public static final String ACTION_STOCK = "stock";
    public static final String ACTION_NONE = "none";

    public static String actionKey(String trigger) {
        return trigger + "_action";
    }

    public static String appKey(String trigger) {
        return trigger + "_app";
    }

    private Prefs() {
    }
}
