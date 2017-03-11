package com.example.android.bluetoothlegatt;

import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.HashSet;

class DirectBeaconsHolder {
    private final static String TAG = "Holder";

    private HashSet<Integer> unpaired = new HashSet<>();
    private ArrayList<DirectBeacon> beacons = new ArrayList<>();
    private SparseArray<DirectBeacon> beaconsMap = new SparseArray<>();

    ArrayList<DirectBeacon> getBeacons() {
        return beacons;
    }

    public HashSet<Integer> getUnpaired() {
        return unpaired;
    }

    void clear() {
        unpaired.clear();
        beaconsMap.clear();
        beacons.clear();
    }

    boolean addInfo(int short_uuid, byte major, int rssi) {
        return addInfo( short_uuid*16 + major, rssi );
    }

    private boolean addInfo( int id, int rssi ) {
        //Log.d(TAG, String.format("addInfo(%d ,%d)", id, rssi));

        DirectBeacon beacon = beaconsMap.get(id);
        if ( beacon != null ) {
            //Log.d(TAG, "existed paired key");
            beacon.setRssi(id, rssi);
            return true;
        } else {
            //Log.d(TAG, "new or unpaired key");
            if ( !unpaired.contains(id) ) {
                // Found a pair

                int anotherId = -1;
                for (int x: unpaired ) {
                    if ( Math.abs(x - id) == 1 ) {
                        //Log.d(TAG, String.format("Found pair: %d %d\n", x, id));
                        anotherId = x;
                        break;
                    }
                }

                if ( anotherId >= 0 ) {
                    Log.d(TAG, "New DIRECT BEACON");
                    unpaired.remove(anotherId);

                    int id0, id1;
                    if ( DetectParams.INVERT_ANTS_IDXS ) {
                        id0 = Math.max(id, anotherId);
                        id1 = Math.min(id, anotherId);
                    } else {
                        id0 = Math.min(id, anotherId);
                        id1 = Math.max(id, anotherId);
                    }

                    DirectBeacon newBeacon = new DirectBeacon( id0, id1 );
                    beaconsMap.put(id0, newBeacon);
                    beaconsMap.put(id1, newBeacon);
                    beacons.add(newBeacon);
                } else {
                    Log.d(TAG,"New unpaired key");
                    unpaired.add(id);
                }
            }
            //else {
            //    Log.d(TAG, "existed unpaired key");
            //}

        }
        return false;
    }
}
