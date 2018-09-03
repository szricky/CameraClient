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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.usb.UsbDevice;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicYuvToRGB;
import android.renderscript.Type;
import android.util.Log;
import android.view.Surface;

import com.hisign.cameraserver.CameraCallback;
import com.hisign.cameraserver.CameraInterface;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;


public class CameraClient implements ICameraClient {
	private static final boolean DEBUG = true;
	private static final String TAG = "CameraClient";

	protected final WeakReference<Context> mWeakContext;
	protected final WeakReference<CameraHandler> mWeakHandler;
	protected UsbDevice mUsbDevice;

	protected final Object mServiceSync = new Object();
	protected CameraInterface mService;
	protected ICameraClientCallback mListener;

	private Context mContext;

	public CameraClient(final Context context, final ICameraClientCallback listener) {
		if (DEBUG) Log.v(TAG, "Constructor:");
		mContext = context;
		mWeakContext = new WeakReference<Context>(context);
		mListener = listener;
		mWeakHandler = new WeakReference<CameraHandler>(CameraHandler.createHandler(this,context));
		doBindService();
	}

	@Override
	protected void finalize() throws Throwable {
		if (DEBUG) Log.v(TAG, "finalize");
		doUnBindService();
		super.finalize();
	}

	@Override
	public void select(final UsbDevice device) {
		if (DEBUG) Log.v(TAG, "select:device=" + (device != null ? device.getDeviceName() : null));
		mUsbDevice = device;
		final CameraHandler handler = mWeakHandler.get();
		handler.sendMessage(handler.obtainMessage(MSG_SELECT, device));
	}

	@Override
	public void release() {
		if (DEBUG) Log.v(TAG, "release:" + this);
		mUsbDevice = null;
		mWeakHandler.get().sendEmptyMessage(MSG_RELEASE);
	}



	@Override
	public void resize(final int width, final int height) {
		if (DEBUG) Log.v(TAG, String.format("resize(%d,%d)", width, height));
		final CameraHandler handler = mWeakHandler.get();
		handler.sendMessage(handler.obtainMessage(MSG_RESIZE, width, height));
	}
	
	@Override
	public void connect() {
		if (DEBUG) Log.v(TAG, "connect:");
		mWeakHandler.get().sendEmptyMessage(MSG_CONNECT);
	}

	@Override
	public void disconnect() {
		if (DEBUG) Log.v(TAG, "disconnect:" + this);
		mWeakHandler.get().sendEmptyMessage(MSG_DISCONNECT);
	}




	/*@Override
	public void test() {

	}*/

	protected boolean doBindService() {
		if (DEBUG) Log.v(TAG, "doBindService:");
		synchronized (mServiceSync) {
			if (mService == null) {
				final Context context = mWeakContext.get();
				if (context != null) {
					final Intent intent = new Intent(CameraInterface.class.getName());
					//intent.setPackage("com.serenegiant.usbcameratest4");
					intent.setPackage("com.hisign.cameraserver");

					context.bindService(intent,
						mServiceConnection, Context.BIND_AUTO_CREATE);
				} else
					return true;
			}
		}
		return false;
	}

	protected void doUnBindService() {
		if (DEBUG) Log.v(TAG, "doUnBindService:");
		synchronized (mServiceSync) {
			if (mService != null) {
				final Context context = mWeakContext.get();
				if (context != null) {
					try {
						context.unbindService(mServiceConnection);
					} catch (final Exception e) {
						// ignore
					}
				}
				mService = null;
			}
		}
	}

	private final ServiceConnection mServiceConnection = new ServiceConnection() {
		@Override
		public void onServiceConnected(final ComponentName name, final IBinder service) {
			if (DEBUG) Log.v(TAG, "onServiceConnected:name=" + name);
			synchronized (mServiceSync) {
				mService = CameraInterface.Stub.asInterface(service);
				mServiceSync.notifyAll();
			}
		}

		@Override
		public void onServiceDisconnected(final ComponentName name) {
			if (DEBUG) Log.v(TAG, "onServiceDisconnected:name=" + name);
			synchronized (mServiceSync) {
				mService = null;
				mServiceSync.notifyAll();
			}
		}
	};

	/**
	 * get reference to instance of IUVCService
	 * you should not call this from UI thread, this method block until the service is available
	 * @return
	 */
	private CameraInterface getService() {
		synchronized (mServiceSync) {
			if (mService == null) {
				try {
					mServiceSync.wait();
				} catch (final InterruptedException e) {
					if (DEBUG) Log.e(TAG, "getService:", e);
				}
			}
		}
		return mService;
	}

	private static final int MSG_IMAGE_VIEW = 10;
	private static final int MSG_IMAGE_VIEW_R = 12;

	private static final int MSG_SET_THREAD_CALL_BACK = 11;


	private static final int MSG_SELECT = 0;
	private static final int MSG_CONNECT = 1;
	private static final int MSG_DISCONNECT = 2;
	private static final int MSG_ADD_SURFACE = 3;
	private static final int MSG_REMOVE_SURFACE = 4;
	private static final int MSG_START_RECORDING = 6;
	private static final int MSG_STOP_RECORDING = 7;
	private static final int MSG_CAPTURE_STILL = 8;
	private static final int MSG_RESIZE = 9;

	private static final int MSG_RELEASE = 99;



	private static final class CameraHandler extends Handler {
		private static Context mContext;
		public static CameraHandler createHandler(final CameraClient parent,Context context) {
			final CameraTask runnable = new CameraTask(parent);
			mContext = context;
			new Thread(runnable).start();
			return runnable.getHandler();
		}

		private CameraTask mCameraTask;
		private CameraHandler(final CameraTask cameraTask) {
			mCameraTask = cameraTask;
		}


		@Override
		public void handleMessage(final Message msg) {
			switch (msg.what) {
			case MSG_SELECT:
				mCameraTask.handleSelect((UsbDevice)msg.obj);
				break;
			case MSG_CONNECT:
				mCameraTask.handleConnect();
				break;
			case MSG_DISCONNECT:
				mCameraTask.handleDisconnect();
				break;
			case MSG_ADD_SURFACE:
				mCameraTask.handleAddSurface((Surface)msg.obj, msg.arg1 != 0);
				break;
			case MSG_REMOVE_SURFACE:
				mCameraTask.handleRemoveSurface((Surface)msg.obj);
				break;
			case MSG_START_RECORDING:
			//	mCameraTask.handleStartRecording();
				break;
			case MSG_STOP_RECORDING:
			//	mCameraTask.handleStopRecording();
				break;
			case MSG_CAPTURE_STILL:
			//	mCameraTask.handleCaptureStill((String)msg.obj);
				break;
			case MSG_RESIZE:
				mCameraTask.handleResize(msg.arg1, msg.arg2);
				break;
			case MSG_RELEASE:
				mCameraTask.handleRelease();
				mCameraTask = null;
				Looper.myLooper().quit();
				break;
			/*case MSG_IMAGE_VIEW:
				mCameraTask.handleImage((byte[])msg.obj);
				break;*/
				default:
				throw new RuntimeException("unknown message:what=" + msg.what);
			}
		}

		private static final class CameraTask extends CameraCallback.Stub implements Runnable,Handler.Callback {
			private static final String TAG_CAMERA = "CameraClientThread";
			private final Object mSync = new Object();
			private CameraClient mParent;
			private CameraHandler mHandler;
			private boolean mIsConnected;
			private int mServiceId;
			private Handler mHander = new Handler(this);

			private CameraTask(final CameraClient parent) {
				mParent = parent;
			}
			
			public Bitmap rawByteArray2RGBABitmap2(byte[] data, int width, int height) {
				int frameSize = width * height;
				int[] rgba = new int[frameSize];

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

				Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);//ARGB_8888);
				bmp.setPixels(rgba, 0 , width, 0, 0, width, height);
				return bmp;
			}

			public CameraHandler getHandler() {
				synchronized (mSync) {
					if (mHandler == null)
					try {
						mSync.wait();
					} catch (final InterruptedException e) {
					}
				}
				return mHandler;
			}

			@Override
			public void run() {
				if (DEBUG) Log.v(TAG_CAMERA, "run:");
				Looper.prepare();
				synchronized (mSync) {
					mHandler = new CameraHandler(this);
					mSync.notifyAll();
				}
				Looper.loop();
				if (DEBUG) Log.v(TAG_CAMERA, "run:finishing");
				synchronized (mSync) {
					mHandler = null;
					mParent = null;
					mSync.notifyAll();
				}
			}

//================================================================================
// callbacks from service

			@Override
			public void onFrame(byte[] data, int camera) throws RemoteException {
				Log.d(TAG,"onFrame ,Data length is : " + data.length );

				final Bitmap bitmap = rawByteArray2RGBABitmap2(data ,640,480);

				if (camera == 0){
					Message obtainMessage = mHander.obtainMessage();
					obtainMessage.obj= bitmap;
					obtainMessage.arg1=camera;
					obtainMessage.what= MSG_IMAGE_VIEW;
					mHander.sendMessage(obtainMessage);

				}else {
					Message obtainMessage = mHander.obtainMessage();
					obtainMessage.obj= bitmap;
					obtainMessage.arg1=camera;
					obtainMessage.what= MSG_IMAGE_VIEW_R;
					mHander.sendMessage(obtainMessage);
				}
			}


			//================================================================================
			public void handleSelect(final UsbDevice device) {
				if (DEBUG) Log.v(TAG_CAMERA, "handleSelect:");
				final CameraInterface service = mParent.getService();
				if (service != null) {
					try {
					//	mServiceId = service.select(device, this);
						service.registerCallback(this);
					} catch (final RemoteException e) {
						if (DEBUG) Log.e(TAG_CAMERA, "select:", e);
					}
				}
			}



			public void handleRelease() {
				if (DEBUG) Log.v(TAG_CAMERA, "handleRelease:");
				mIsConnected = false;
				mParent.doUnBindService();
			}

			public void handleConnect() {
				if (DEBUG) Log.v(TAG_CAMERA, "handleConnect:");
				final CameraInterface service = mParent.getService();
				if (service != null)
				try {
						Log.d(TAG,"mIsConnected is : " + mIsConnected);
					if (!mIsConnected/*!service.isConnected(mServiceId)*/) {
						//service.connect(mServiceId);
						service.openCamera();
						mIsConnected = true;
						if (mParent != null) {
							if (mParent.mListener != null) {
								mParent.mListener.onConnect();
							}
						}
					} else {
						//mIsConnected = true;
						if (mParent != null) {
							if (mParent.mListener != null) {
								mParent.mListener.onConnect();
							}
						}
					}
				} catch (final RemoteException e) {
					if (DEBUG) Log.e(TAG_CAMERA, "handleConnect:", e);
				}
			}

			public void handleDisconnect() {
				if (DEBUG) Log.v(TAG_CAMERA, "handleDisconnect:");
				final CameraInterface service = mParent.getService();
				if (service != null)
				try {
					Log.d(TAG,"mIsConnected is : " + mIsConnected);

					if (mIsConnected){//service.isConnected(mServiceId)) {
						//service.disconnect(mServiceId);
						service.stop();
						mIsConnected = false;

					} else {
						//onDisConnected();
						if (DEBUG) Log.v(TAG_CAMERA, "onDisConnected:");
						//mIsConnected = false;
						if (mParent != null) {
							if (mParent.mListener != null) {
								mParent.mListener.onDisconnect();
							}
						}

					}
				} catch (final RemoteException e) {
					if (DEBUG) Log.e(TAG_CAMERA, "handleDisconnect:", e);
				}
			}

			public void handleAddSurface(final Surface surface, final boolean isRecordable) {
				if (DEBUG) Log.v(TAG_CAMERA, "handleAddSurface:surface=" + surface + ",hash=" + surface.hashCode());
				final CameraInterface service = mParent.getService();
				/*if (service != null)
				try {
					service.addSurface(mServiceId);//, surface.hashCode(), surface, isRecordable);

				} catch (final RemoteException e) {
					if (DEBUG) Log.e(TAG_CAMERA, "handleAddSurface:", e);
				}*/
			}



			public void handleRemoveSurface(final Surface surface) {
				if (DEBUG) Log.v(TAG_CAMERA, "handleRemoveSurface:surface=" + surface + ",hash=" + surface.hashCode());
				final CameraInterface service = mParent.getService();
				/*if (service != null)
				try {
					service.removeSurface(mServiceId, surface.hashCode());
				} catch (final RemoteException e) {
					if (DEBUG) Log.e(TAG_CAMERA, "handleRemoveSurface:", e);
				}*/
			}

			public void handleResize(final int width, final int height) {
				if (DEBUG) Log.v(TAG, String.format("handleResize(%d,%d)", width, height));
				final CameraInterface service = mParent.getService();

			}

			public void handleImage(Bitmap bitmap) {
				if (mParent != null) {
					if (mParent.mListener != null) {
						mParent.mListener.handleData(bitmap);
					}
				}
			}

			public void handleImageR(Bitmap bitmap) {
				if (mParent != null) {
					if (mParent.mListener != null) {
						mParent.mListener.handleDataR(bitmap);
					}
				}
			}



				@Override
			public boolean handleMessage(Message msg) {
				switch (msg.what) {
					case MSG_IMAGE_VIEW:
						Log.d(TAG,"CAMERA_DATA");
						Bitmap bitmap=(Bitmap)msg.obj;
						int camera=msg.arg1;
						handleImage(bitmap);
						//Log.d(TAG,"bitmap is : " + bitmap.getByteCount() + " , camera is " + camera);
						//preView_right.setImageBitmap(bitmap);
						break;
					case MSG_IMAGE_VIEW_R:
						Log.d(TAG,"CAMERA_DATA_R");
						Bitmap bitmap1=(Bitmap)msg.obj;
						int camera1=msg.arg1;
						handleImageR(bitmap1);

						break;
					/*case MSG_SET_THREAD_CALL_BACK:

						break;*/
					default:
						break;
				}

				return false;
			}
		}
	}



	public static Bitmap Bytes2Bitmap(byte[] data) {
		//处理data
		BitmapFactory.Options newOpts = new BitmapFactory.Options();
		newOpts.inJustDecodeBounds = true;
		YuvImage yuvimage = new YuvImage(
				data,
				ImageFormat.NV21,
				640,
				480,
				null);
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		// 80--JPG图片的质量[0-100],100最高
		yuvimage.compressToJpeg(new Rect(0, 0, 640,480 ), 80, baos);
		byte[] rawImage = baos.toByteArray();
		//将rawImage转换成bitmap
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inPreferredConfig = Bitmap.Config.RGB_565;
		try {
			baos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		yuvimage = null;
		return BitmapFactory.decodeByteArray(rawImage, 0, rawImage.length, options);
	}


	public static Bitmap YuvToBmp(Context context, byte[] data, int width, int height) {
		RenderScript rs;
		ScriptIntrinsicYuvToRGB yuvToRgbIntrinsic;
		byte[] outBytes = new byte[width * height * 4];

		rs = RenderScript.create(context);
		yuvToRgbIntrinsic = ScriptIntrinsicYuvToRGB.create(rs, Element.RGBA_8888(rs));

		Type.Builder yuvType = new Type.Builder(rs, Element.U8(rs))
				.setX(width).setY(height)
				.setYuvFormat(android.graphics.ImageFormat.NV21);

		Allocation in = Allocation.createTyped(rs, yuvType.create(), Allocation.USAGE_SCRIPT);

		Type.Builder rgbaType = new Type.Builder(rs, Element.RGBA_8888(rs)).setX(width).setY(height);

		Allocation out = Allocation.createTyped(rs, rgbaType.create(), Allocation.USAGE_SCRIPT);

		in.copyFrom(data);

		yuvToRgbIntrinsic.setInput(in);
		yuvToRgbIntrinsic.forEach(out);

		out.copyTo(outBytes);

		Bitmap bmpout = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		out.copyTo(bmpout);

		return bmpout;
	}



}
