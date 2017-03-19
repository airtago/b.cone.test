package com.example.android.bluetoothlegatt;

import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Locale;

class DirectBeacon {

    private class PreBeacon {
        int idx = 0;
        long time = 0;
        double dbVal = -100.0;
        boolean isEmpty = true;

        void CreateNew( int idx, long time, double dbVal ) {
            this.idx = idx;
            this.time = time;
            this.dbVal = dbVal;
            this.isEmpty = false;
        }
    }

    private class BeaconData {
        long time = 0;
        double[] dbValue  = new double[2];
        double[] linValue = new double[2];

        double dbDiff;
        double linDiff;

        BeaconData( long time, double dbVal0, double dbVal1 ) {
            this.time = time;
            dbValue[0] = dbVal0;
            dbValue[1] = dbVal1;

            linValue[0] = convertRssiDb2Lin(dbVal0);
            linValue[1] = convertRssiDb2Lin(dbVal1);

            dbDiff = linValue[0]/linValue[1];
            linDiff = convertRssiDb2Lin(dbDiff);
        }

    }

    private final static String TAG = "DBeacon";

    private int id1;
    private int id2;

    PreBeacon preBeacon = new PreBeacon();

    private double[] avg_rss = new double[2];
    private double avg_diff;
    private boolean needRecalc = true;

    private int touchCount = 0;
    private double touchRatioPS = 0.0;
    private long firstTouchMs = 0;
    private long TARGET_DELTA_MS = 1500;

    private int[] idxSpinnerIdx = new int[2];
    private String idxSpinnerStr = "|.|";
    private static String[] spinnerStr = { "|", "/", "-", "\\" };
    private static int spinnerSz = 4;

    private LinkedList<BeaconData> values = new LinkedList<>();

    DirectBeacon(int id1, int id2) {
        this.id1 = id1;
        this.id2 = id2;
        needRecalc = true;
        firstTouchMs = Calendar.getInstance().getTimeInMillis();
        idxSpinnerIdx[0] = 0;
        idxSpinnerIdx[1] = 1;
    }

    private void touchRatio() {
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

    private void touchIndex(int idx) {
        if ( idx == 0 || idx == 1 ) {
            idxSpinnerIdx[idx] = (idxSpinnerIdx[idx]+1) % spinnerSz;
        }
        idxSpinnerStr = spinnerStr[idxSpinnerIdx[0]] + "." + spinnerStr[idxSpinnerIdx[1]];
    }

    private double convertRssiDb2Lin( double db ) {
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

        long curTime = Calendar.getInstance().getTimeInMillis();
        int thisIdx    = idx;
        int anotherIdx = (idx+1) % 2;

        //Log.d(TAG, "?" + idx + " " + curTime % 10000 );


        boolean addNew = false;

        if ( preBeacon.isEmpty ) {
            preBeacon.CreateNew( thisIdx, curTime, rssi );
            Log.d(TAG, "Create NEW");

        } else {

            if ( preBeacon.idx == thisIdx ) {
                Log.d(TAG, "DROP: " + thisIdx + " " + Math.abs(curTime - preBeacon.time) + "ms");
                preBeacon.CreateNew( thisIdx, curTime, rssi );

            } else {

                if (Math.abs( curTime - preBeacon.time ) > 100 ) {
                    Log.d(TAG, "DROP: " + thisIdx + " " + Math.abs(curTime - preBeacon.time) + "ms");
                    preBeacon.CreateNew( thisIdx, curTime, rssi );

                } else {

                    Log.d(TAG, "+++++++++ ADD: time " + Math.abs( curTime - preBeacon.time ));

                    BeaconData newData;
                    if ( thisIdx == 0 ) {
                        newData = new BeaconData(curTime, rssi, preBeacon.dbVal);
                    } else {
                        newData = new BeaconData(curTime, preBeacon.dbVal, rssi);
                    }
                    values.addLast(newData);
                    preBeacon.isEmpty = true;
                    needRecalc = true;
                    touchRatio();
                }
            }

        }

        if ( !values.isEmpty() ) {
            while (curTime - values.peekFirst().time > DetectParams.AVG_TIME) {
                values.removeFirst();
                needRecalc = true;

                if ( values.isEmpty() ) {
                    break;
                }
            }
        }



        //touchIndex(idx);

        //printValues();

    }

    private void printValues() {

        if ( !values.isEmpty() ) {
            String s = ">>>>> ";
            for (BeaconData x : values) {
                s += x.time % 10000;
                s += " (";
                s += (int) x.dbValue[0];
                s += ",";
                s += (int) x.dbValue[1];
                s += ");  ";
            }
            Log.d(TAG, s);
        }
    }

    private void recalc() {

        double sum = 0;
        for ( BeaconData x : values ) {
            sum += x.dbDiff;
        }
        if ( values.size() != 0 ) {
            avg_diff = sum / values.size();
        } else {
            avg_diff = 0.0;
        }

        needRecalc = false;
    }

    String getInfoString() {
        if ( needRecalc ) {
            recalc();
        }

        if ( DetectParams.DEV_MODE ) {
            return String.format(Locale.ENGLISH,
                    "[%07X] %4.1fps %s (%5.2f %5.2f)xE6, %.2f",
                    id1,
                    getTouchRatioPS(),
                    idxSpinnerStr,
                    avg_rss[0]*1.0e6, avg_rss[1]*1.0e6, avg_diff);
        } else {
            return String.format(Locale.ENGLISH,
                    "[%07X] %4.1fps %s",
                    id1,
                    getTouchRatioPS(),
                    idxSpinnerStr );
        }
    }

    boolean isVisible() {
        if ( values.isEmpty() ) {
            return false;
        }

        if ( needRecalc ) {
            recalc();
        }

        return avg_diff > DetectParams.DIFF_THRESHOLD;
    }

}
