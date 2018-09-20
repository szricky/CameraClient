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
import com.hisign.cameraserver.TestPra;

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
	public void select1(final UsbDevice device) {
		if (DEBUG) Log.v(TAG, "select:device=" + (device != null ? device.getDeviceName() : null));
		mUsbDevice = device;
		final CameraHandler handler = mWeakHandler.get();
		handler.sendMessage(handler.obtainMessage(MSG_SELECT_1, device));
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
	public void connect(int pid_0,int pid_1 ) {
		if (DEBUG) Log.v(TAG, "connect:");
		final CameraHandler handler = mWeakHandler.get();
		handler.sendMessage(handler.obtainMessage(MSG_CONNECT,pid_0,pid_1));
	}

	@Override
	public void disconnect() {
		if (DEBUG) Log.v(TAG, "disconnect:" + this);
		mWeakHandler.get().sendEmptyMessage(MSG_DISCONNECT);
	}

	@Override
	public void addSurface(Surface surface, boolean isRecordable) {
		if (DEBUG) Log.v(TAG, "addSurface:surface=" + surface + ",hash=" + surface.hashCode());
		final CameraHandler handler = mWeakHandler.get();
		handler.sendMessage(handler.obtainMessage(MSG_ADD_SURFACE, isRecordable ? 1 : 0, 0, surface));
	}

	@Override
	public void addSurface1(Surface surface, boolean isRecordable) {
		if (DEBUG) Log.v(TAG, "addSurface1:surface=" + surface + ",hash=" + surface.hashCode());
		final CameraHandler handler = mWeakHandler.get();
		handler.sendMessage(handler.obtainMessage(MSG_ADD_SURFACE_1, isRecordable ? 1 : 0, 0, surface));
	}


	protected boolean doBindService() {
		if (DEBUG) Log.v(TAG, "doBindService:");
		synchronized (mServiceSync) {
			if (mService == null) {
				final Context context = mWeakContext.get();
				if (context != null) {
					final Intent intent = new Intent(CameraInterface.class.getName());
					intent.setPackage("com.hisign.cameraserver");
					context.bindService(intent, mServiceConnection, Context.BIND_AUTO_CREATE);
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
	// 失效重联机制, 当Binder死亡时, 重新连接
	private IBinder.DeathRecipient mDeathRecipient = new IBinder.DeathRecipient() {
		@Override public void binderDied() {
			Log.e(TAG, "Binder失效");
			doBindService();
		}
	};
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



	private static final int MSG_SELECT = 0;
	private static final int MSG_SELECT_1 = 11;

	private static final int MSG_CONNECT = 1;
	private static final int MSG_DISCONNECT = 2;
	private static final int MSG_ADD_SURFACE = 3;
	private static final int MSG_ADD_SURFACE_1 = 10;

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
			case MSG_SELECT_1:
				mCameraTask.handleSelect1((UsbDevice)msg.obj);
				break;
			case MSG_CONNECT:
				int pid_0 = (int) msg.arg1;
				int pid_1 = (int) msg.arg2;
				mCameraTask.handleConnect(pid_0,pid_1);
				break;
			case MSG_DISCONNECT:
				mCameraTask.handleDisconnect();
				break;
			case MSG_ADD_SURFACE:
				mCameraTask.handleAddSurface((Surface)msg.obj, msg.arg1 != 0);
				break;
			case MSG_ADD_SURFACE_1:
				mCameraTask.handleAddSurface1((Surface)msg.obj, msg.arg1 != 0);
				break;
			case MSG_REMOVE_SURFACE:
				mCameraTask.handleRemoveSurface((Surface)msg.obj);
				break;
			case MSG_START_RECORDING:
				break;
			case MSG_STOP_RECORDING:
				break;
			case MSG_CAPTURE_STILL:
				break;
			case MSG_RESIZE:
				mCameraTask.handleResize(msg.arg1, msg.arg2);
				break;
			case MSG_RELEASE:
				mCameraTask.handleRelease();
				mCameraTask = null;
				Looper.myLooper().quit();
				break;
				default:
				throw new RuntimeException("unknown message:what=" + msg.what);
			}
		}

		private Handler mHandler;


		private static final class CameraTask extends CameraCallback.Stub implements Runnable{//,Handler.Callback {
			private static final String TAG_CAMERA = "CameraClientThread";
			private final Object mSync = new Object();
			private CameraClient mParent;
			private CameraHandler mHandler;
			private boolean mIsConnected;
			private int mServiceId;
			private int mServiceId_1;


			private CameraTask(final CameraClient parent) {
				mParent = parent;
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

			private static byte[] mBytes;
			@Override
			public void onFrame(TestPra testPra, int camera) throws RemoteException {
				mBytes = testPra.getBytes();
				mParent.mListener.handleData(mBytes,camera);
			}

			@Override
			public void onConnected(int camera) throws RemoteException {
				if (DEBUG) Log.v(TAG_CAMERA, "callbacks from service onConnected:");
				mIsConnected = true;
				if (mParent != null) {
					if (mParent.mListener != null) {
						if (camera ==0 ){
							Log.d(TAG,"mListener onConnect ,camera = " + camera);
							mParent.mListener.onConnect();
						}else {
							Log.d(TAG,"mListener onConnect1 ,camera = " + camera);
							mParent.mListener.onConnect1();

						}
					}
				}
			}


			//================================================================================
			public void handleSelect(final UsbDevice device) {
				if (DEBUG) Log.v(TAG_CAMERA, "handleSelect:");
				final CameraInterface service = mParent.getService();
				if (service != null) {
					mServiceId = device.hashCode();
					Log.d(TAG,"mServiceId = " + mServiceId);

					try {
                         service.registerCallback(this);
					} catch (final RemoteException e) {
						if (DEBUG) Log.e(TAG_CAMERA, "select:", e);
					}
				}
			}

			public void handleSelect1(final UsbDevice device) {
				if (DEBUG) Log.v(TAG_CAMERA, "handleSelect:");
				final CameraInterface service = mParent.getService();
				if (service != null) {
					mServiceId_1 = device.hashCode();
					Log.d(TAG,"mServiceId_1 = " + mServiceId_1);
				}
			}

			public void handleRelease() {
				if (DEBUG) Log.v(TAG_CAMERA, "handleRelease:");
				mIsConnected = false;
				mParent.doUnBindService();
			}

			public void handleConnect(int pid_0,int pid_1) {
				if (DEBUG) Log.v(TAG_CAMERA, "handleConnect:");
				final CameraInterface service = mParent.getService();
				if (service != null)
				try {
						Log.d(TAG,"mIsConnected is : " + mIsConnected);
					if (!mIsConnected/*!service.isConnected(mServiceId)*/) {
						Log.d(TAG,"service.openCamera");
						service.openCamera(pid_0,pid_1);
						mIsConnected = true;
						if (mParent != null) {
							if (mParent.mListener != null) {
								mParent.mListener.onConnect();
								mParent.mListener.onConnect1();

							}
						}
					} else {
						if (mParent != null) {
							if (mParent.mListener != null) {
								mParent.mListener.onConnect();
								mParent.mListener.onConnect1();

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
						service.stop();
						mIsConnected = false;

					} else {
						if (DEBUG) Log.v(TAG_CAMERA, "onDisConnected:");
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
				if (DEBUG) Log.v(TAG_CAMERA, "handleAddSurface:addSurface=" + surface + ",hash=" + surface.hashCode());
				final CameraInterface service = mParent.getService();
				if (service != null)
					try {
						service.addSurface(mServiceId, surface.hashCode(), surface, isRecordable);
					} catch (final RemoteException e) {
						if (DEBUG) Log.e(TAG_CAMERA, "handleAddSurface:", e);
					}
			}

			public void handleAddSurface1(final Surface surface, final boolean isRecordable) {
				if (DEBUG) Log.v(TAG_CAMERA, "handleAddSurface1:addSurface=" + surface + ",hash=" + surface.hashCode());
				final CameraInterface service = mParent.getService();
				if (service != null)
					try {
						service.addSurface1(mServiceId_1, surface.hashCode(), surface, isRecordable);
					} catch (final RemoteException e) {
						if (DEBUG) Log.e(TAG_CAMERA, "handleAddSurface:", e);
					}
			}


			public void handleRemoveSurface(final Surface surface) {
				if (DEBUG) Log.v(TAG_CAMERA, "handleRemoveSurface:surface=" + surface + ",hash=" + surface.hashCode());
				final CameraInterface service = mParent.getService();
			}

			public void handleResize(final int width, final int height) {
				if (DEBUG) Log.v(TAG, String.format("handleResize(%d,%d)", width, height));
				final CameraInterface service = mParent.getService();

			}

		/*	public void handleImage(Bitmap bitmap) {
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
			}*/



	/*			@Override
			public boolean handleMessage(Message msg) {
				switch (msg.what) {
					case MSG_IMAGE_VIEW:
						Log.d(TAG,"CAMERA_DATA");
						byte[] data = (byte[]) msg.obj;
						rawByteArray2RGBABitmap2(bmp_l,data ,640,480);
						handleImage(bmp_l);
						data = null;
						msg.obj = null;

						break;
					case MSG_IMAGE_VIEW_R:
						Log.d(TAG,"CAMERA_DATA_R" + System.currentTimeMillis());
						byte[] data1 = (byte[]) msg.obj;
						rawByteArray2RGBABitmap2(bmp_r,data1 ,640,480);
						Log.d(TAG,"CAMERA_DATA_R finish " + System.currentTimeMillis() );
						handleImageR(bmp_r);
						data1 = null;
						msg.obj = null;
						break;
					default:
						break;
				}

				return false;
			}*/
		}
	}







}
