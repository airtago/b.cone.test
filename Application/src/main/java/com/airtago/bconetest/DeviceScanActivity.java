/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.airtago.bconetest;

import android.Manifest;
import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;


/**
 * Activity for scanning and displaying available Bluetooth LE devices.
 */
public class DeviceScanActivity extends ListActivity {
    final static String TAG = "SCANNER";
    public static final String PREF_KEY_LOG_ON_START = "log on start";

    private LeDeviceListAdapter mLeDeviceListAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private boolean mScanning;
    private boolean mLogging = false;
    private Handler mHandler;
    private Menu mMenu;

    private SharedPreferences sharedPreferences;

    // Stops scanning after 10 minutes.
    private static final long SCAN_PERIOD = 600 * 1000;

    private static final int B_CONE_REQUEST_ENABLE_BT = 1;
    private static final int B_CONE_REQUEST_ACCESS_FINE_LOCATION = 2;

    private void requestLocationPermissions() {
        if (
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
            &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
        {
            ActivityCompat.requestPermissions(DeviceScanActivity.this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    B_CONE_REQUEST_ACCESS_FINE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {
            case B_CONE_REQUEST_ACCESS_FINE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(DeviceScanActivity.this,
                            R.string.permission_granted,
                            Toast.LENGTH_LONG).show();

                } else {
                    Toast.makeText(DeviceScanActivity.this,
                            R.string.permission_denied,
                            Toast.LENGTH_LONG).show();
                }
                break;
            }

        }
    }

    private void checkLocationService() {
        final LocationManager manager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            final AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(R.string.ask_enable_location)
                    .setCancelable(false)
                    .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int id) {
                            startActivity(new Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                        }
                    })
                    .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                        public void onClick(final DialogInterface dialog, final int id) {
                            dialog.cancel();
                        }
                    });
            final AlertDialog alert = builder.create();
            alert.show();
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActionBar actionBar = getActionBar();
        if ( actionBar != null ) {
            setTitle(R.string.title_devices);
        }
        mHandler = new Handler();

        // Use this check to determine whether BLE is supported on the device.  Then you can
        // selectively disable BLE-related features.
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        }

        requestLocationPermissions();
        checkLocationService();

        // Initializes a Bluetooth adapter.  For API level 18 and above, get a reference to
        // BluetoothAdapter through BluetoothManager.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();

        LogStorage.setLogging(mLogging);

        // Checks if Bluetooth is supported on the device.
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, R.string.error_bluetooth_not_supported, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        DetectParams.createFromSharedPreferences(sharedPreferences);
    }

    @Override
    public boolean onKeyDown(int keycode, KeyEvent e) {
        switch(keycode) {
            case KeyEvent.KEYCODE_MENU:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                return true;
        }

        return super.onKeyDown(keycode, e);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean logOnStart = this.sharedPreferences.getBoolean(PREF_KEY_LOG_ON_START, false);
        mLogging = logOnStart;

        getMenuInflater().inflate(R.menu.main, menu);
        mMenu = menu;
        if (!mScanning) {
            menu.findItem(R.id.menu_stop).setVisible(false);
            menu.findItem(R.id.menu_scan).setVisible(true);
            menu.findItem(R.id.menu_refresh).setActionView(null);
        } else {
            menu.findItem(R.id.menu_stop).setVisible(true);
            menu.findItem(R.id.menu_scan).setVisible(false);
            menu.findItem(R.id.menu_refresh).setActionView(
                    R.layout.actionbar_indeterminate_progress);
        }
        menu.findItem(R.id.menu_settings).setVisible(true);

        if (!mLogging) {
            menu.findItem(R.id.menu_start_log).setVisible(true);
            menu.findItem(R.id.menu_stop_log).setVisible(false);
        } else {
            menu.findItem(R.id.menu_start_log).setVisible(false);
            menu.findItem(R.id.menu_stop_log).setVisible(true);
        }

        if (!logOnStart) {
            menu.findItem(R.id.menu_log_on_start).setVisible(true);
            menu.findItem(R.id.menu_no_log_on_start).setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_scan:
                mLeDeviceListAdapter.clear();
                scanLeDevice(true);
                break;
            case R.id.menu_stop:
                scanLeDevice(false);
                break;
            case R.id.menu_settings:
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
                break;
            case R.id.menu_start_log:
                startLogger();
                break;
            case R.id.menu_stop_log:
                stopLogger();
                break;
            case R.id.menu_log_on_start:
                logOnStartOn();
                break;
            case R.id.menu_no_log_on_start:
                logOnStartOff();
                break;
        }
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Ensures Bluetooth is enabled on the device.  If Bluetooth is not currently enabled,
        // fire an intent to display a dialog asking the user to grant permission to enable it.
        if (!mBluetoothAdapter.isEnabled()) {
            if (!mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, B_CONE_REQUEST_ENABLE_BT);
            }
        }

        // Initializes list view adapter.
        mLeDeviceListAdapter = new LeDeviceListAdapter();
        setListAdapter(mLeDeviceListAdapter);
        scanLeDevice(true);

        DetectParams.createFromSharedPreferences(sharedPreferences);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // User chose not to enable Bluetooth.
        if (requestCode == B_CONE_REQUEST_ENABLE_BT && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        mLeDeviceListAdapter.clear();
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        //final BluetoothDevice device = mLeDeviceListAdapter.getDevice(position);
        //if (device == null) return;

    }


    private void scanLeDevice(final boolean enable) {
        if (enable) {
            // Stops scanning after a pre-defined scan period.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                    scanLeDevice(true);
                }
            }, SCAN_PERIOD );

            mScanning = true;
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
            //mBluetoothAdapter.stopScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    private void logOnStartOn() {
        SharedPreferences.Editor e = sharedPreferences.edit();
        e.putBoolean(PREF_KEY_LOG_ON_START, true);
        e.apply();
        mMenu.findItem(R.id.menu_log_on_start).setVisible(false);
        mMenu.findItem(R.id.menu_no_log_on_start).setVisible(true);
    }

    private void logOnStartOff() {
        SharedPreferences.Editor e = sharedPreferences.edit();
        e.putBoolean(PREF_KEY_LOG_ON_START, false);
        e.apply();
        mMenu.findItem(R.id.menu_log_on_start).setVisible(true);
        mMenu.findItem(R.id.menu_no_log_on_start).setVisible(false);
    }

    private void startLogger(){
        mLogging = true;
        LogStorage.setLogging(true);
        mMenu.findItem(R.id.menu_start_log).setVisible(false);
        mMenu.findItem(R.id.menu_stop_log).setVisible(true);
    }
    private void stopLogger(){
        LogStorage.setLogging(false);
        while (LogStorage.isNotEmpty()) {
            writeLog(LogStorage.popString());
        }
        mLogging = false;
        mMenu.findItem(R.id.menu_start_log).setVisible(true);
        mMenu.findItem(R.id.menu_stop_log).setVisible(false);
    }

    private void writeLog(String stringForLog) {
        if (!mLogging) {
            return;
        }

        File logFile = new File("sdcard/b-cone-log.txt");
        if (!logFile.exists())
        {
            try
            {
                logFile.createNewFile();
            }
            catch (IOException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        try
        {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter buf = new BufferedWriter(new FileWriter(logFile, true));
            buf.append(stringForLog);
            buf.newLine();
            buf.close();
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }



    // Adapter for holding devices found through scanning.
    private class LeDeviceListAdapter extends BaseAdapter {
        private DirectBeaconsHolder beaconsHolder = new DirectBeaconsHolder();
        private LayoutInflater mInflator;

        LeDeviceListAdapter() {
            super();
            mInflator = DeviceScanActivity.this.getLayoutInflater();
        }

        boolean addDevice( int rssi, byte[] scanRecord) {
            int uuid = scanRecord[9]*65536 + scanRecord[23]*256 + scanRecord[24];
            byte major = scanRecord[26];
            //byte minor = scanRecord[28];

            //Log.d(TAG, String.format("0x%08X %d %d", uuid, major, minor));

            Calendar calendar = Calendar.getInstance();
            long curTime = calendar.getTimeInMillis();
            Date logTime = calendar.getTime();

            LogStorage.pushString(String.format(Locale.ENGLISH,"%s: [%07X] packet major: %d time ms: %d rssi: %d",
                    logTime.toString(), uuid, major, curTime, rssi));

            return beaconsHolder.addInfo( uuid, major, rssi );
        }

        void clear() {
            beaconsHolder.clear();
        }

        @Override
        public int getCount() {
            //return beaconsHolder.getBeacons().size() + beaconsHolder.getUnpaired().size();
            return beaconsHolder.getBeacons().size();
        }

        @Override
        public Object getItem(int i) {
            return beaconsHolder.getBeacons().get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            // General ListView optimization code.
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view.findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view.findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            DirectBeacon beacon = beaconsHolder.getBeacons().get(i);

            LogStorage.pushString(beacon.getStringForLog());

            viewHolder.deviceName.setText( beacon.getInfoString() );
            if ( beacon.isVisible() ) {
                viewHolder.deviceAddress.setText(R.string.visible);

                viewHolder.deviceAddress.setBackgroundColor(Color.GREEN);
                viewHolder.deviceName.setBackgroundColor(Color.GREEN);
            } else {
                viewHolder.deviceAddress.setText(R.string.invisible);

                viewHolder.deviceAddress.setBackgroundColor(Color.WHITE);
                viewHolder.deviceName.setBackgroundColor(Color.WHITE);
            }

            return view;
        }
    }

    // Device scan callback.
    private BluetoothAdapter.LeScanCallback mLeScanCallback =
            new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            final int rssi_ = rssi;
            final byte[] scanRecord_ = scanRecord;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if ( mLeDeviceListAdapter.addDevice(rssi_, scanRecord_) ) {
                        mLeDeviceListAdapter.notifyDataSetChanged();
                    }
                }
            });
        }
    };

    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}