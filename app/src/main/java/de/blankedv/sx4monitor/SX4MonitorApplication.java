package de.blankedv.sx4monitor;

import android.app.Application;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Created by mblank on 23.01.17.
 */

public class SX4MonitorApplication extends Application {

    final public static boolean DEBUG = true;
    final public static String TAG = "SX4Monitor";

    public static final int LANBAHN_PORT = 27027;
    public static final String LANBAHN_GROUP = "239.200.201.250";


    // connection state
    public static SXnetClientThread client;
    public static long mLastMessage = 0;
    public static String connString = "";
    public static final int INVALID_INT = -9999;
    public static final boolean demoFlag = false;
    public static final long TIMEOUT_MILLISECS = 20000; // when no signal is received for 20 secs => timeout

    public static IncomingHandler handler;   //
    public static final BlockingQueue<String> sendQ = new ArrayBlockingQueue<String>(500);

    public static final int SXNET_PORT = 4104;
    public static final String SXNET_START_IP = "192.168.178.66";
    public static final int SX_FEEDBACK_MESSAGE = 1;
    public static final int ERROR_MESSAGE = 4;
    public static final int POWER_MESSAGE = 5;


    // preferences
    public static final String KEY_IP = "ip_preference";
    public static final String KEY_AUTOIP = "autoIPPref";
    public static final String KEY_FONT_FACTOR = "fontFacPref";
    public static boolean fontFactorChanged = false;

    // selectrix data
    public static final int SXMAX = 111;
    public static final int MAX_DISPLAY_CHAN = 109;
    public static final int[] sxData = new int[SXMAX+1];   // contains all selectrix channel data
    public static final int[] oldData = new int[SXMAX+1];   // contains all selectrix channel data
    public static final int[] colorMark = new int[SXMAX+1];   // contains all selectrix channel data

    public static boolean globalPower = false;

    public static boolean pauseTimer = false;

    public static int counter = 0;

    private final String APPSTRING = "SXMonitor4Application";


    @Override
    public void onCreate() {
        super.onCreate();
        if (DEBUG) Log.d(TAG, "onCreate "+APPSTRING);

        // no sx-data initially
        for (int i = 0; i <= SXMAX; i++) {
            sxData[i] = INVALID_INT;
            oldData[i] = sxData[i];
            colorMark[i] = 0;
        }



        handler = new IncomingHandler(this);

    }


    // this construct to avoid leaks see the postings
// https://groups.google.com/forum/?fromgroups=#!msg/android-developers/1aPZXZG6kWk/lIYDavGYn5UJ
// http://stackoverflow.com/questions/11407943/this-handler-class-should-be-static-or-leaks-might-occur-incominghandler
    static class IncomingHandler extends Handler {
        private final WeakReference<SX4MonitorApplication> mApp;

        IncomingHandler(SX4MonitorApplication app) {
            mApp = new WeakReference<>(app);
        }

        @Override
        public void handleMessage(Message msg) {
            SX4MonitorApplication app = mApp.get();
            if (app != null) {
                app.handleMessage(msg);
            }
        }
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
        Log.d(TAG, APPSTRING + "terminating.");

    }

    public void handleMessage(Message msg) {
        int what = msg.what;
        int data = msg.arg2;

        switch (what) {
            case SX_FEEDBACK_MESSAGE:
                mLastMessage = System.currentTimeMillis();
                int chan = msg.arg1;
                sxData[chan] = data;
                if (DEBUG) Log.d(TAG, "sx_fb_msg "+chan+"/"+data);
                break;
            case POWER_MESSAGE:
                mLastMessage = System.currentTimeMillis();
                if (data != 0) {
                    globalPower = true;
                } else {
                    globalPower = false;
                }
                if (DEBUG) Log.d(TAG, "power=" + data);
                break;
            case ERROR_MESSAGE:
                String error = (String) msg.obj;
                Toast toast = Toast.makeText(getApplicationContext(), error, Toast.LENGTH_LONG);
                toast.show();
                break;

        }

    }

    /*public void initializeDiscoveryListener() {

        // Instantiate a new DiscoveryListener
        mDiscoveryListener = new NsdManager.DiscoveryListener() {

            //  Called as soon as service discovery begins.
            @Override
            public void onDiscoveryStarted(String regType) {
                Log.d(TAG, "Service discovery started");
            }

            @Override
            public void onServiceFound(NsdServiceInfo service) {
                // A service was found!  Do something with it.
                Log.d(TAG, "Service discovery success" + service);
                if (!service.getServiceType().equals(SERVICE_TYPE)) {
                    // Service type is the string containing the protocol and
                    // transport layer for this service.
                    Log.d(TAG, "Unknown Service Type: " + service.getServiceType());
                } else if (service.getServiceName().equals(mServiceName)) {
                    // The name of the service tells the user what they'd be
                    // connecting to. It could be "Bob's Chat App".
                    Log.d(TAG, "Same machine: " + mServiceName);
                } else if (service.getServiceName().contains("NsdChat")){
                    mNsdManager.resolveService(service, mResolveListener);
                }
            }

            @Override
            public void onServiceLost(NsdServiceInfo service) {
                // When the network service is no longer available.
                // Internal bookkeeping code goes here.
                Log.e(TAG, "service lost" + service);
            }

            @Override
            public void onDiscoveryStopped(String serviceType) {
                Log.i(TAG, "Discovery stopped: " + serviceType);
            }

            @Override
            public void onStartDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                //mNsdManager.stopServiceDiscovery(this);
            }

            @Override
            public void onStopDiscoveryFailed(String serviceType, int errorCode) {
                Log.e(TAG, "Discovery failed: Error code:" + errorCode);
                mNsdManager.stopServiceDiscovery(this);
            }
        };
    }
*/
}
