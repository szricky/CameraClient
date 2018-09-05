/*
 *  UVCCamera
 *  library and sample to access to UVC web camera on non-rooted Android device
 *
 * Copyright (c) 2014-2017 saki t_saki@serenegiant.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  All files in the folder are under this Apache License, Version 2.0.
 *  Files in the libjpeg-turbo, libusb, libuvc, rapidjson folder
 *  may have a different license, see the respective files.
 */

package com.hisign.cameraclient;

import android.app.Activity;
import android.app.Fragment;
import android.graphics.Bitmap;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ToggleButton;

import com.serenegiant.common.BaseFragment;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;

import java.util.List;

public class CameraFragment extends BaseFragment {

	private static final boolean DEBUG = true;
	private static final String TAG = "CameraFragment";

	private static final int DEFAULT_WIDTH = 640;
	private static final int DEFAULT_HEIGHT = 480;

	private Handler mHandler;

	private USBMonitor mUSBMonitor;
	private ICameraClient mCameraClient;

	private ImageView mImageView;
	private ImageView mImageViewR;

	private ToggleButton mPreviewButton;





	public CameraFragment() {
		if (DEBUG) Log.v(TAG, "Constructor:");
	}

	@SuppressWarnings("deprecation")
	@Override
	public void onAttach(final Activity activity) {
		super.onAttach(activity);
		if (DEBUG) Log.v(TAG, "onAttach:");
	}

	private static final int IMAGE_VIEW = 0;
	private static final int IMAGE_VIEW_R = 1;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG) Log.v(TAG, "onCreate:");
		if (mUSBMonitor == null) {
			mUSBMonitor = new USBMonitor(getActivity().getApplicationContext(), mOnDeviceConnectListener);
			final List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(getActivity(), R.xml.device_filter);
			mUSBMonitor.setDeviceFilter(filters);
		}

		mHandler = new Handler(){
			public void handleMessage(Message msg) {
				switch (msg.what) {
					case IMAGE_VIEW:
						Log.d(TAG,"setImageBitmap");
						Bitmap bitmap = (Bitmap) msg.obj;
					    mImageView.setImageBitmap(bitmap);
						break;
					case IMAGE_VIEW_R:
						Log.d(TAG,"setImageBitmap");
						Bitmap bitmap1 = (Bitmap) msg.obj;
						mImageViewR.setImageBitmap(bitmap1);
						break;
				}
			}
	};
	}

	@Override
	public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {
		if (DEBUG) Log.v(TAG, "onCreateView:");
		final View rootView = inflater.inflate(R.layout.fragment_main, container, false);
		View view = rootView.findViewById(R.id.start_button);
		view.setOnClickListener(mOnClickListener);
		view =rootView.findViewById(R.id.stop_button);
		view.setOnClickListener(mOnClickListener);
		mPreviewButton = (ToggleButton)rootView.findViewById(R.id.preview_button);
		setPreviewButton(false);
		mPreviewButton.setEnabled(false);
		mImageView = (ImageView) rootView.findViewById(R.id.frame_image_test);
		mImageViewR = (ImageView) rootView.findViewById(R.id.frame_image_test_r);

		return rootView;
	}

	@Override
	public void onResume() {
		super.onResume();
		if (DEBUG) Log.v(TAG, "onResume:");
		mUSBMonitor.register();
	}

	@Override
	public void onPause() {
		if (DEBUG) Log.v(TAG, "onPause:");
		mUSBMonitor.unregister();
		enableButtons(false);
		super.onPause();
	}

	@Override
	public void onDestroyView() {
		if (DEBUG) Log.v(TAG, "onDestroyView:");
		super.onDestroyView();
	}

	@Override
	public void onDestroy() {
		if (DEBUG) Log.v(TAG, "onDestroy:");
		if (mCameraClient != null) {
			mCameraClient.release();
			mCameraClient = null;
		}
		super.onDestroy();
	}

	@Override
	public void onDetach() {
		if (DEBUG) Log.v(TAG, "onDetach:");
		super.onDetach();
	}

	public USBMonitor getUSBMonitor() {
		return mUSBMonitor;
	}

	private final OnDeviceConnectListener mOnDeviceConnectListener = new OnDeviceConnectListener() {
		@Override
		public void onAttach(final UsbDevice device) {
			if (DEBUG) Log.v(TAG, "OnDeviceConnectListener#onAttach:");
			if (!updateCameraDialog()){// && (mCameraView.hasSurface())) {
				tryOpenUVCCamera(true);
			}
		}

		@Override
		public void onConnect(final UsbDevice device, final UsbControlBlock ctrlBlock, final boolean createNew) {
			if (DEBUG) Log.v(TAG, "OnDeviceConnectListener#onConnect:");
		}

		@Override
		public void onDisconnect(final UsbDevice device, final UsbControlBlock ctrlBlock) {
			if (DEBUG) Log.v(TAG, "OnDeviceConnectListener#onDisconnect:");
		}

		@Override
		public void onDettach(final UsbDevice device) {
			if (DEBUG) Log.v(TAG, "OnDeviceConnectListener#onDettach:");
			queueEvent(new Runnable() {
				@Override
				public void run() {
					if (mCameraClient != null) {
						mCameraClient.disconnect();
						mCameraClient.release();
						mCameraClient = null;
					}
				}
			}, 0);
			enableButtons(false);
			updateCameraDialog();
		}

		@Override
		public void onCancel(final UsbDevice device) {
			if (DEBUG) Log.v(TAG, "OnDeviceConnectListener#onCancel:");
			enableButtons(false);
		}
	};

	private boolean updateCameraDialog() {
		final Fragment fragment = getFragmentManager().findFragmentByTag("CameraDialog");
		if (fragment instanceof CameraDialog) {
			((CameraDialog)fragment).updateDevices();
			return true;
		}
		return false;
	}

	private void tryOpenUVCCamera(final boolean requestPermission) {
		if (DEBUG) Log.v(TAG, "tryOpenUVCCamera:");
		openUVCCamera(0);
	}


	private void openUVCCamera(final int index) {
		if (DEBUG) Log.v(TAG, "openUVCCamera:index=" + index);
		if (!mUSBMonitor.isRegistered()) return;
		final List<UsbDevice> list = mUSBMonitor.getDeviceList();
		for (int i = 0;i<list.size();i++) {
			UsbDevice device = list.get(i);
			String mString = String.format("UVC Camera:(%x:%x:%s)", device.getVendorId(), device.getProductId(), device.getDeviceName());
			Log.d(TAG,""+ mString);
		}

		if (list.size() > index) {
			enableButtons(false);
			if (mCameraClient == null)
				mCameraClient = new CameraClient(getActivity(), mCameraListener);
			mCameraClient.select(list.get(index));
			mCameraClient.resize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
			mCameraClient.connect(0x1a90,0x1a20);//实际摄像头pid  0x1a90可见   0x1a20红外
		}
	}

	private final ICameraClientCallback mCameraListener = new ICameraClientCallback() {
		@Override
		public void onConnect() {
			if (DEBUG) Log.v(TAG, "onConnect:");
			enableButtons(true);
			setPreviewButton(true);
		}

		@Override
		public void onDisconnect() {
			if (DEBUG) Log.v(TAG, "onDisconnect:");
			setPreviewButton(false);
			enableButtons(false);
		}

		@Override
		public void handleData(Bitmap bitmap) {
			Log.d(TAG,"handleData bitmap");
			mHandler.sendMessage(mHandler.obtainMessage(IMAGE_VIEW, bitmap));

		}

		@Override
		public void handleDataR(Bitmap bitmap) {
			Log.d(TAG,"handleDataR bitmap");
			mHandler.sendMessage(mHandler.obtainMessage(IMAGE_VIEW_R, bitmap));

		}

	};

	private final OnClickListener mOnClickListener = new OnClickListener() {
		@Override
		public void onClick(final View v) {
			switch (v.getId()) {
			case R.id.start_button:
				if (DEBUG) Log.v(TAG, "onClick:start");
				// start service
				final List<UsbDevice> list = mUSBMonitor.getDeviceList();
				if (list.size() > 0) {
					if (mCameraClient == null)
						mCameraClient = new CameraClient(getActivity(), mCameraListener);
					mCameraClient.select(list.get(0));
					mCameraClient.resize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
					mCameraClient.connect(0x1a90,0x1a20);
					setPreviewButton(false);
				}
				break;
			case R.id.stop_button:
				if (DEBUG) Log.v(TAG, "onClick:stop");
				// stop service
				if (mCameraClient != null) {
					mCameraClient.disconnect();
					mCameraClient.release();
					mCameraClient = null;
				}
				enableButtons(false);
				break;
			}
		}
	};

	private final OnCheckedChangeListener mOnCheckedChangeListener = new OnCheckedChangeListener() {
		@Override
		public void onCheckedChanged(final CompoundButton buttonView, final boolean isChecked) {
			if (DEBUG) Log.v(TAG, "onCheckedChanged:" + isChecked);
		}
	};

	private void setPreviewButton(final boolean onoff) {
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mPreviewButton.setOnCheckedChangeListener(null);
				try {
					mPreviewButton.setChecked(onoff);
				} finally {
					mPreviewButton.setOnCheckedChangeListener(mOnCheckedChangeListener);
				}
			}
		});
	}

	private final void enableButtons(final boolean enable) {
		setPreviewButton(false);
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mPreviewButton.setEnabled(enable);

			}
		});
	}
}
