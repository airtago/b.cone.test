package com.example.android.bluetoothlegatt;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Created by Michael Lukin on 27.11.2017.
 */

public class LogStorage {
    private static CopyOnWriteArrayList<String> list = new CopyOnWriteArrayList<>();

    public static void setLogging(boolean mLogging) {
        LogStorage.mLogging = mLogging;
    }

    private static boolean mLogging;

    public static void pushString(String str) {
        if (mLogging) {
            list.add(str);
        }
    }

    public static boolean isEmpty() {
        return list.isEmpty();
    }

    public static boolean isNotEmpty() {
        return !isEmpty();
    }

    public static String popString() {
        if (list.isEmpty()) {
            return null;
        }

        String res = list.get(0);
        list.remove(0);
        return res;
    }
}
