package com.hisign.cameraclient;

import android.app.Fragment;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;

import com.hisign.cameraserver.CameraInterface;

public class MainActivity extends AppCompatActivity {
    private static final boolean DEBUG = false;
    private static final String TAG = "MainActivity";
    private CameraInterface myAidlInterface;

    private ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            myAidlInterface = CameraInterface.Stub.asInterface(service);
            Log.d(TAG, "onServiceConnected" + myAidlInterface);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            myAidlInterface = null;
            Log.d(TAG, "onServiceDisconnected");
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        if (savedInstanceState == null) {
            if (DEBUG) Log.i(TAG, "onCreate:new");
            final Fragment fragment = new CameraFragment();
            getFragmentManager().beginTransaction()
                    .add(R.id.container, fragment).commit();
        }


        Intent intent = new Intent();
        //intent.setPackage("com.serenegiant.service");
        intent.setPackage("com.hisign.cameraserver");
        intent.setAction("com.hisign.cameraserver.CameraInterface");
        bindService(intent, connection, BIND_AUTO_CREATE);

    }
}
