package com.example.android.bluetoothlegatt;

import android.content.SharedPreferences;

class DetectParams {
    static long   AVG_TIME = 1000;
    static double DIFF_THRESHOLD = 10.0;
    static boolean DEV_MODE = false;
    static boolean INVERT_ANTS_IDXS = false;

    static void createFromSharedPreferences(SharedPreferences sp) {
        DEV_MODE = sp.getBoolean("DEV_MODE", false);
        INVERT_ANTS_IDXS = sp.getBoolean("INVERT_ANTS_IDXS", false);
        DIFF_THRESHOLD = Double.parseDouble(sp.getString("DIFF_THRESHOLD", Double.toString(DIFF_THRESHOLD)));
        AVG_TIME = Long.parseLong(sp.getString("AVG_TIME", Long.toString(AVG_TIME)));
    }
}
