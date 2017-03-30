package com.example.android.bluetoothlegatt;

import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.Locale;

class DirectBeacon {

    private class RcvStat {
        private ArrayList<LinkedList<Long>> vals = new ArrayList<>();

        RcvStat() {
            for( int i = 0; i < 3; i++ ) {
                vals.add( new LinkedList<Long>() );
            }
        }

        void touch( int idx, long time ) {
            vals.get(idx).addLast(time);
            clearOld(time);
        }

        private void clearOld( long curTime ) {
            for( int i = 0; i < 3; i++ ) {
                LinkedList<Long> times = vals.get(i);
                if ( !times.isEmpty() ) {
                    while (curTime - times.peekFirst() > DetectParams.AVG_TIME) {
                        times.removeFirst();

                        if ( times.isEmpty() ) {
                            break;
                        }
                    }
                }
            }
        }

        public String toString() {
            String s = "";

            double sentCount = DetectParams.AVG_TIME / DetectParams.TX_TARGET_TIME;
            s += " 0: " + (int)(100.0 * vals.get(0).size() / sentCount) + "%";
            s += " 1: " + (int)(100.0 * vals.get(1).size() / sentCount) + "%";
            s += " P: " + (int)(100.0 * vals.get(2).size() / sentCount) + "%";
            s += " ";

            return s;
        }

    };
    private RcvStat rcvStat = new RcvStat();

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

    private PreBeacon preBeacon = new PreBeacon();

    private double[] avg_rss = new double[2];
    private double avg_diff;
    private boolean needRecalc = true;



    private LinkedList<BeaconData> values = new LinkedList<>();

    DirectBeacon(int id1, int id2) {
        this.id1 = id1;
        this.id2 = id2;
        needRecalc = true;
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
        int idx;
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
        //int anotherIdx = (idx+1) % 2;

        //Log.d(TAG, "?" + idx + " " + curTime % 10000 );

        rcvStat.touch(thisIdx, curTime);


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
                    rcvStat.touch(2, curTime);

                    BeaconData newData;
                    if ( thisIdx == 0 ) {
                        newData = new BeaconData(curTime, rssi, preBeacon.dbVal);
                    } else {
                        newData = new BeaconData(curTime, preBeacon.dbVal, rssi);
                    }
                    values.addLast(newData);
                    preBeacon.isEmpty = true;
                    needRecalc = true;
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

        double sum = 0.0;
        avg_rss[0] = 0.0;
        avg_rss[1] = 0.0;
        for ( BeaconData x : values ) {
            sum += x.dbDiff;
            avg_rss[0] += x.linValue[0];
            avg_rss[1] += x.linValue[1];
        }
        int count = values.size();
        if ( count != 0 ) {
            avg_diff = sum / count;
            avg_rss[0] /= count;
            avg_rss[1] /= count;
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
                    "[%07X]%s(%5.2f %5.2f)e-6, %.2f",
                    id1,
                    rcvStat.toString(),
                    avg_rss[0]*1.0e6, avg_rss[1]*1.0e6, avg_diff);
        } else {
            return String.format(Locale.ENGLISH,
                    "[%07X]%s",
                    id1,
                    rcvStat.toString() );
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
