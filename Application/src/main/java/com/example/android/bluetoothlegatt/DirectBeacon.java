package com.example.android.bluetoothlegatt;

import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Locale;

class DirectBeacon {

    private final static String TAG = "DBeacon";

    private int id1;
    private int id2;

    private double[] avg_rss = new double[2];
    private double avg_diff;
    private boolean needRecalc = true;

    private int touchCount = 0;
    private double touchRatioPS = 0.0;
    private long firstTouchMs = 0;
    private long TARGET_DELTA_MS = 1500;

    private ArrayList<LinkedList<Double>> rss = new ArrayList<>();

    DirectBeacon(int id1, int id2) {
        this.id1 = id1;
        this.id2 = id2;
        rss.add(0, new LinkedList<Double>());
        rss.add(1, new LinkedList<Double>());
        needRecalc = true;
        firstTouchMs = Calendar.getInstance().getTimeInMillis();
    }

    private void touch() {
        touchCount++;
        long nowMs = Calendar.getInstance().getTimeInMillis();
        long deltaMs = nowMs - firstTouchMs;
        //Log.d(TAG, String.format("TOUCH: %d - %d = %d, %d",
        //        firstTouchMs, nowMs, deltaMs, touchCount ) );

        if ( deltaMs > TARGET_DELTA_MS ) {
            touchRatioPS = (double)touchCount / (deltaMs/1000.0);
            touchCount = 0;
            firstTouchMs = nowMs;
        }
    }

    private double getTouchRatioPS() {
        return touchRatioPS;
    }


    private double convertRssiDb2Lin( int db ) {
        return Math.pow( 10.0, db/10.0 );
    }

    private double calcDiff( double a1, double a2 ) {
        if ( a1 == 0.0 || a2 == 0.0 ) {
            return 0.0;
        } else {
            return a1/a2;
        }
    }

    void setRssi( int id, int rssi ) {
        int idx = 0;
        if ( id == id1 ) {
            idx = 0;
        } else if ( id == id2 ){
            idx = 1;
        } else {
            Log.e(TAG, String.format("setRssi bad idx: %d %d   %d", id1, id2, id));
            return;
        }
        touch();
        //Log.d(TAG, String.format("setRssi(%d ,%d, %f)", idx, rssi, convertRssiDb2Lin(rssi)));

        LinkedList<Double> vals = rss.get(idx);
        vals.addLast(convertRssiDb2Lin(rssi));

        while ( vals.size() > DetectParams.AVG_CNT ) {
            vals.removeFirst();
        }
        needRecalc = true;

        Log.d(TAG, getInfoString() );
    }

    private void recalc() {
        for ( int idx = 0; idx < 2; idx++ ) {
            LinkedList<Double> vals = rss.get(idx);
            double sum = 0;
            for ( double x : vals ) {
                sum += x;
            }
            avg_rss[idx] =  sum / (double)vals.size();
        }

        avg_diff = calcDiff(avg_rss[0], avg_rss[1]);

        needRecalc = false;
    }

    String getInfoString() {
        if ( needRecalc ) {
            recalc();
        }

        if ( DetectParams.DEV_MODE ) {
            return String.format(Locale.ENGLISH,
                    "[ %07X ] %4.1f ps, ( %5.2f %5.2f )xE6,  %.2f",
                    id1,
                    getTouchRatioPS(),
                    avg_rss[0]*1.0e6, avg_rss[1]*1.0e6, avg_diff);
        } else {
            return String.format(Locale.ENGLISH,
                    "b.cone id[ %07X ] %4.1f ps",
                    id1,
                    getTouchRatioPS() );
        }
    }

    boolean isVisible() {
        if ( rss.get(0).size() < DetectParams.AVG_CNT ) {
            return false;
        }

        if ( needRecalc ) {
            recalc();
        }

        return avg_diff > DetectParams.DIFF_THRESHOLD;
    }

}
