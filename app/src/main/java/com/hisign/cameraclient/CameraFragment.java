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
import android.graphics.BitmapFactory;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.ToggleButton;

import com.example.libyuv.Test;
import com.serenegiant.common.BaseFragment;
import com.serenegiant.usb.CameraDialog;
import com.serenegiant.usb.DeviceFilter;
import com.serenegiant.usb.USBMonitor;
import com.serenegiant.usb.USBMonitor.OnDeviceConnectListener;
import com.serenegiant.usb.USBMonitor.UsbControlBlock;
import com.serenegiant.widget.CameraViewInterface;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.List;

public class CameraFragment extends BaseFragment {

	private static final boolean DEBUG = true;
	private static final String TAG = "CameraFragment";

	private static final int DEFAULT_WIDTH = 480;//640;
	private static final int DEFAULT_HEIGHT = 640;//480;

	private Handler mHandler;

	private USBMonitor mUSBMonitor;
	private ICameraClient mCameraClient;

	private static ImageView mImageView;
	private static ImageView mImageViewR;
	private CameraViewInterface mCameraView;

    private CameraViewInterface mCameraView1;

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

	private static class MyHandler extends Handler{
		//持有弱引用HandlerActivity,GC回收时会被回收掉.
		private final WeakReference<CameraFragment> mCameraFragment;
		public MyHandler(CameraFragment fragment){
			mCameraFragment =new WeakReference<CameraFragment>(fragment);
		}
		@Override
		public void handleMessage(Message msg) {
			CameraFragment cameraFragment= mCameraFragment.get();
			super.handleMessage(msg);
			if(cameraFragment!=null){
				//执行业务逻辑
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
		}
	}

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		if (DEBUG) Log.v(TAG, "onCreate:");
		if (mUSBMonitor == null) {
			mUSBMonitor = new USBMonitor(getActivity().getApplicationContext(), mOnDeviceConnectListener);
			final List<DeviceFilter> filters = DeviceFilter.getDeviceFilters(getActivity(), R.xml.device_filter);
			mUSBMonitor.setDeviceFilter(filters);
		}
		mHandler =new MyHandler(this);
		mNV21ToBitmap = new NV21ToBitmap(getActivity());

		rs = RenderScript.create(getActivity());
		yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.U8_4(rs));
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
		mCameraView = (CameraViewInterface)rootView.findViewById(R.id.camera_view);
		mCameraView.setAspectRatio(DEFAULT_WIDTH / (float)DEFAULT_HEIGHT);
        mCameraView1 = (CameraViewInterface)rootView.findViewById(R.id.camera_view1);
        mCameraView1.setAspectRatio(DEFAULT_WIDTH / (float)DEFAULT_HEIGHT);
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
			if (!updateCameraDialog() && mCameraView.hasSurface() && mCameraView1.hasSurface()){// && (mCameraView.hasSurface())) {
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
            Log.d(TAG,"mCameraClient select");
            mCameraClient.select(list.get(index));
			if (list.size()>index+1){
			    Log.d(TAG,"mCameraClient select1");
                mCameraClient.select1(list.get(index+1));

            }
			mCameraClient.resize(DEFAULT_WIDTH, DEFAULT_HEIGHT);
			mCameraClient.connect(0x1a90,0x1a20);//实际摄像头pid  0x1a90可见   0x1a20红外
		}
	}
	/*private static Bitmap bmp_l = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);//ARGB_8888);
	private static Bitmap bmp_r = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);//ARGB_8888);*/
	private static Bitmap bmp_l = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888);//ARGB_8888);
	private static Bitmap bmp_r = Bitmap.createBitmap(480, 640, Bitmap.Config.ARGB_8888);//ARGB_8888);
	private static int[] rgba = new int[640*480];

	private NV21ToBitmap mNV21ToBitmap;

	public static void rawByteArray2RGBABitmap2(Bitmap bitmap,final byte[] data, int width, int height) {
		int frameSize = width * height;
		for (int h = 0; h < height; h++)
			for (int w = 0; w < width; w++) {
				int y = (0xff & ((int) data[h * width + w]));
				int u = (0xff & ((int) data[frameSize + (h >> 1) * width + (w & ~1) + 0]));
				int v = (0xff & ((int) data[frameSize + (h >> 1) * width + (w & ~1) + 1]));
				y = y < 16 ? 16 : y;

				int b = Math.round(1.164f * (y-16) + 2.018f * (u - 128));
				int g = Math.round(1.164f * (y-16) - 0.813f * (v - 128) - 0.391f * (u - 128));
				int r =  Math.round(1.164f * (y-16) + 1.596f*(v - 128));

				r = r < 0 ? 0 : (r > 255 ? 255 : r);
				g = g < 0 ? 0 : (g > 255 ? 255 : g);
				b = b < 0 ? 0 : (b > 255 ? 255 : b);

				rgba[h * width + w] = 0xff000000 + (r << 16) + (g << 8) + b;
			}

		bitmap.setPixels(rgba, 0 , width, 0, 0, width, height);
		//return bmp;
	}
	private RenderScript rs;
	private ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
	private Type.Builder yuvType, rgbaType;
	private Allocation in, out;






	static BitmapFactory.Options options = new BitmapFactory.Options();
	public static Bitmap byteToBitmap(byte[] imgByte) {
		InputStream input = null;
		Bitmap bitmap = null;


		input = new ByteArrayInputStream(imgByte);
		SoftReference softRef = new SoftReference(BitmapFactory.decodeStream(
				input, null, options));
		bitmap = (Bitmap) softRef.get();
		if (imgByte != null) {
			imgByte = null;
		}

		try {
			if (input != null) {
				input.close();
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return bitmap;
	}


		private final ICameraClientCallback mCameraListener = new ICameraClientCallback() {
		@Override
		public void onConnect() {
			if (DEBUG) Log.v(TAG, "mCameraListener onConnect:");
			mCameraClient.addSurface(mCameraView.getSurface(), false);

			enableButtons(true);
			setPreviewButton(true);
		}

        @Override
        public void onConnect1() {
            if (DEBUG) Log.v(TAG, "mCameraListener onConnect111:");
            mCameraClient.addSurface1(mCameraView1.getSurface(), false);

        }

        @Override
		public void onDisconnect() {
			if (DEBUG) Log.v(TAG, "onDisconnect:");
			setPreviewButton(false);
			enableButtons(false);
		}



		//	private int w=640,h=480;
		private int w=480,h=640;

			//用于保存将yuv数据转成argb数据
			byte[] rgbbuffer=new byte[w*h*4];
			byte[] rgbbuffer1=new byte[w*h*4];

			@Override
		public void handleData(final byte[] data, int camera) {
			Log.d(TAG,"data  length = " + data.length);

			if (data != null){
				if (camera ==0){
					Test.convertToArgb(data,w*h*3/2,rgbbuffer,w*4,0,0,w,h,w,h,0,0);
					bmp_l.copyPixelsFromBuffer(ByteBuffer.wrap(rgbbuffer));
				//	rawByteArray2RGBABitmap2(bmp_l,data ,640,480);
					mHandler.sendMessage(mHandler.obtainMessage(IMAGE_VIEW, bmp_l));
				}else {
					Test.convertToArgb(data,w*h*3/2,rgbbuffer1,w*4,0,0,w,h,w,h,0,0);
					bmp_r.copyPixelsFromBuffer(ByteBuffer.wrap(rgbbuffer1));
			//		rawByteArray2RGBABitmap2(bmp_r,data ,640,480);
					mHandler.sendMessage(mHandler.obtainMessage(IMAGE_VIEW_R, bmp_r));
				}
			}

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
                    if (list.size()> 1){
                        Log.d(TAG,"mCameraClient select1");
                        mCameraClient.select1(list.get(1));

                    }
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
			if (isChecked) {
				mCameraClient.addSurface(mCameraView.getSurface(), false);
                mCameraClient.addSurface1(mCameraView1.getSurface(), false);

//				mCameraClient.addSurface(mCameraViewSub.getHolder().getSurface(), false);
			} else {
				//mCameraClient.removeSurface(mCameraView.getSurface());
//				mCameraClient.removeSurface(mCameraViewSub.getHolder().getSurface());
			}
		}
	};

/*	private void setPreviewButton(final boolean onoff) {
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
	}*/


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

/*	private final void enableButtons(final boolean enable) {
		setPreviewButton(false);
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mPreviewButton.setEnabled(enable);

			}
		});
	}*/

	private final void enableButtons(final boolean enable) {
		setPreviewButton(false);
		getActivity().runOnUiThread(new Runnable() {
			@Override
			public void run() {
				mPreviewButton.setEnabled(enable);
			//	mRecordButton.setEnabled(enable);
			//	mStillCaptureButton.setEnabled(enable);

			}
		});
	}
}
