package com.example.android.bluetoothlegatt;

import android.util.Log;

import java.util.ArrayList;
import java.util.LinkedList;

public class DirectBeacon {

    final static String TAG = "DBeacon";

    private int id1;
    private int id2;

    private double[] avg_rss = new double[2];
    private double avg_diff;

    ArrayList<LinkedList<Integer>> rss = new ArrayList<>();

    public DirectBeacon(int id1, int id2) {
        this.id1 = id1;
        this.id2 = id2;
        rss.add(0, new LinkedList<Integer>());
        rss.add(1, new LinkedList<Integer>());
        needRecalc = true;
    }

    boolean needRecalc;

    public void setRssi( int id, int rssi ) {
        int idx = 0;
        if ( id == id1 ) {
            idx = 0;
        } else if ( id == id2 ){
            idx = 1;
        } else {
            Log.e(TAG, String.format("setRssi bad idx: %d %d   %d", id1, id2, id));
            return;
        }
        //Log.d(TAG, String.format("setRssi(%d ,%d)", idx, rssi));

        LinkedList<Integer> vals = rss.get(idx);
        vals.addLast(rssi);

        while ( vals.size() > DetectParams.AVG_CNT ) {
            vals.removeFirst();
        }
        needRecalc = true;

        Log.d(TAG, getInfoString() );
    }

    private void recalc() {
        for ( int idx = 0; idx < 2; idx++ ) {
            LinkedList<Integer> vals = rss.get(idx);
            long sum = 0;
            for ( int x : vals ) {
                sum += x;
            }
            avg_rss[idx] =  (double)sum / (double)vals.size();
        }

        avg_diff = Math.abs(avg_rss[0] - avg_rss[1]);

        needRecalc = false;
    }

    public String getInfoString() {
        if ( needRecalc ) {
            recalc();
        }

        if ( DetectParams.DEV_MODE ) {
            return String.format("[ %07X ] : %.1f %.1f  diff: %.1f", id1, avg_rss[0], avg_rss[1], avg_diff);
        } else {
            return String.format("b.cone id[ %07" +
                    "X ]", id1 );
        }
    }

    public boolean isVisible() {
        if ( rss.get(0).size() < DetectParams.AVG_CNT ) {
            return false;
        }

        if ( needRecalc ) {
            recalc();
        }

        return avg_diff > DetectParams.DIFF_THRESHOLD;
    }

}
