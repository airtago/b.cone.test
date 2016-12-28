package com.example.android.bluetoothlegatt;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.example.android.bluetoothlegatt.R;

public class SettingsActivity extends PreferenceActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.pref);
    }
}