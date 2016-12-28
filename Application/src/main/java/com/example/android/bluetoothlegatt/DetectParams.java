package com.example.android.bluetoothlegatt;

import android.content.SharedPreferences;

public class DetectParams {
    public static int    AVG_CNT = 5;
    public static double DIFF_THRESHOLD = 10.0;
    public static boolean DEV_MODE = false;

    public static void createFromSharedPreferences(SharedPreferences sp) {
        DEV_MODE = sp.getBoolean("DEV_MODE", false);
        DIFF_THRESHOLD = Double.parseDouble(sp.getString("DIFF_THRESHOLD", Double.toString(DIFF_THRESHOLD)));
        AVG_CNT = Integer.parseInt(sp.getString("AVG_CNT", Integer.toString(AVG_CNT)));
    }
}
