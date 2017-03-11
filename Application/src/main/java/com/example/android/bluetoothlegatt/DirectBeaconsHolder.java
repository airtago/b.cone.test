package com.example.android.bluetoothlegatt;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class DirectBeaconsHolder {
    final static String TAG = "Holder";

    HashSet<Integer> unpaired = new HashSet<>();
    ArrayList<DirectBeacon> beacons = new ArrayList<>();
    HashMap<Integer, DirectBeacon> beaconsMap = new HashMap<>();

    public ArrayList<DirectBeacon> getBeacons() {
        return beacons;
    }

    public HashSet<Integer> getUnpaired() {
        return unpaired;
    }

    public void clear() {
        unpaired.clear();
        beaconsMap.clear();
        beacons.clear();
    }

    public void addInfo( String idstr, int rssi ) {
        int a0 = Integer.parseInt( idstr.substring(0,2), 16 );
        int a1 = Integer.parseInt( idstr.substring(3,5), 16 );
        int id = a0 + a1 * 256;
        addInfo(id, rssi);
    }

    public void addInfo( int short_uuid, byte major, int rssi ) {
        addInfo( short_uuid*16 + major, rssi );
    }

    public void addInfo( int id, int rssi ) {
        //Log.d(TAG, String.format("addInfo(%d ,%d)", id, rssi));

        if ( beaconsMap.containsKey(id) ) {
            //Log.d(TAG, "existed paired key");
            DirectBeacon beacon = beaconsMap.get(id);
            beacon.setRssi(id, rssi);
        } else {
            //Log.d(TAG, "new or unpair key");
            if ( unpaired.contains(id) ) {
                //Log.d(TAG, "existed unpaired key");
            } else {
                // Find pair
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

                    DirectBeacon beacon = new DirectBeacon( id0, id1 );
                    beaconsMap.put(id0, beacon);
                    beaconsMap.put(id1, beacon);
                    beacons.add(beacon);
                } else {
                    Log.d(TAG,"New unpaired key");
                    unpaired.add(id);
                }
            }

        }
    }
}
