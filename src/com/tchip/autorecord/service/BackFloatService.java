package com.tchip.autorecord.service;

import java.io.IOException;

import com.tchip.autorecord.Constant;
import com.tchip.autorecord.R;
import com.tchip.autorecord.util.HintUtil;
import com.tchip.autorecord.util.MyLog;
import com.tchip.tachograph.TachographCallback;
import com.tchip.tachograph.TachographRecorder;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.Camera;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.view.SurfaceHolder.Callback;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

public class BackFloatService extends Service {
	
	private Context context;

	RelativeLayout layoutFloat; // 定义浮动窗口布局
	WindowManager.LayoutParams wmParams; // 创建浮动窗口设置布局参数的对象
	WindowManager windowManager;

	// 后置
	private Camera cameraBack;
	private SurfaceView surfaceViewBack;
	private SurfaceHolder surfaceHolderBack;
	private TachographRecorder recorderBack;

	private ImageButton imageCameraSwitch;

	@Override
	public void onCreate() {
		super.onCreate();
		context = getApplicationContext();
		createFloatView();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (layoutFloat != null) {
			windowManager.removeView(layoutFloat); // 移除悬浮窗口
		}
	}

	private void createFloatView() {
		wmParams = new WindowManager.LayoutParams();
		// 获取的是WindowManagerImpl.CompatModeWrapper
		windowManager = (WindowManager) getApplication().getSystemService(
				getApplication().WINDOW_SERVICE);

		wmParams.type = LayoutParams.TYPE_PHONE; // 设置Window Type
		wmParams.format = PixelFormat.RGBA_8888; // 设置图片格式，效果为背景透明
		// 设置浮动窗口不可聚焦（实现操作除浮动窗口外的其他可见窗口的操作）
		wmParams.flags = LayoutParams.FLAG_NOT_FOCUSABLE;
		wmParams.gravity = Gravity.CENTER;// Gravity.CENTER | Gravity.TOP;
		// 以屏幕左上角为原点，设置x、y初始值，相对于gravity
		wmParams.x = 0;
		wmParams.y = 0;

		// 设置悬浮窗口长宽数据
		wmParams.width = WindowManager.LayoutParams.MATCH_PARENT;
		wmParams.height = WindowManager.LayoutParams.MATCH_PARENT;
		// 设置悬浮窗口长宽数据 wmParams.width = 200; wmParams.height = 80;

		LayoutInflater inflater = LayoutInflater.from(getApplication()); // 获取浮动窗口视图所在布局
		layoutFloat = (RelativeLayout) inflater.inflate(
				R.layout.back_record_float_window, null);
		windowManager.addView(layoutFloat, wmParams); // 添加mFloatLayout

		MyOnClickListener myOnClickListener = new MyOnClickListener();
		// initialCameraSurfaceBack
		surfaceViewBack = (SurfaceView) layoutFloat
				.findViewById(R.id.surfaceViewBack);
		surfaceViewBack.setOnClickListener(myOnClickListener);
		surfaceViewBack.getHolder().addCallback(new BackCallBack());

		imageCameraSwitch = (ImageButton) layoutFloat
				.findViewById(R.id.imageCameraSwitch);
		imageCameraSwitch.setOnClickListener(myOnClickListener);

		layoutFloat.measure(View.MeasureSpec.makeMeasureSpec(0,
				View.MeasureSpec.UNSPECIFIED), View.MeasureSpec
				.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

		if (cameraBack == null) {
			setupBack();
		} else {
			previewCameraBack();
		}
	}

	class MyOnClickListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {

			case R.id.imageCameraSwitch:
				HintUtil.showToast(context, "imageCameraSwitch");
				break;

			default:
				break;
			}
		}
	}

	class BackCallBack implements Callback {
		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			// surfaceHolder = holder;
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			MyLog.v("[Record]surfaceCreated");
			if (cameraBack == null) {
				surfaceHolderBack = holder;
				setupBack();
			} else {
				previewCameraBack();
			}

		}

		@Override
		public void surfaceDestroyed(SurfaceHolder holder) {
			MyLog.v("[Record]surfaceDestroyed");
		}
	}

	/**
	 * 打开摄像头
	 * 
	 * @return
	 */
	private boolean openCameraBack() {
		if (cameraBack != null) {
			closeCameraBack();
		}
		try {
			MyLog.v("[Record] Camera.open");
			cameraBack = Camera.open(1);// Camera.open(0);
			previewCameraBack();
			return true;
		} catch (Exception ex) {
			closeCameraBack();
			MyLog.e("[Record]openCamera:Catch Exception!");
			return false;
		}
	}

	/**
	 * Camera预览：
	 * 
	 * lock > setPreviewDisplay > startPreview > unlock
	 */
	private void previewCameraBack() {
		try {
			cameraBack.lock();
			if (Constant.Module.useSystemCameraParam) { // 设置系统Camera参数
				Camera.Parameters para = cameraBack.getParameters();
				para.unflatten(Constant.Record.CAMERA_PARAMS);
				cameraBack.setParameters(para);
			}
			cameraBack.setPreviewDisplay(surfaceHolderBack);
			// camera.setDisplayOrientation(180);
			cameraBack.startPreview();
			cameraBack.unlock();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 关闭Camera
	 * 
	 * lock > stopPreview > setPreviewDisplay > release > unlock
	 */
	private boolean closeCameraBack() {
		if (cameraBack == null)
			return true;
		try {
			cameraBack.lock();
			cameraBack.stopPreview();
			cameraBack.setPreviewDisplay(null);
			cameraBack.release();
			cameraBack.unlock();
			cameraBack = null;
			return true;
		} catch (Exception e) {
			cameraBack = null;
			MyLog.e("[MainActivity]closeCamera:Catch Exception:" + e.toString());
			return false;
		}
	}

	private void setupRecorderBack() {
		releaseRecorderBack();
		try {
			recorderBack = new TachographRecorder();
			recorderBack.setTachographCallback(new BackTachographCallback());
			recorderBack.setCamera(cameraBack);
			// 前缀，后缀
			recorderBack.setMediaFilenameFixs(
					TachographCallback.FILE_TYPE_VIDEO, "", "");
			recorderBack.setMediaFilenameFixs(
					TachographCallback.FILE_TYPE_SHARE_VIDEO, "", "");
			recorderBack.setMediaFilenameFixs(
					TachographCallback.FILE_TYPE_IMAGE, "", "");
			// 路径
			recorderBack.setMediaFileDirectory(
					TachographCallback.FILE_TYPE_VIDEO, "VideoBack");
			recorderBack.setMediaFileDirectory(
					TachographCallback.FILE_TYPE_SHARE_VIDEO, "Share");
			recorderBack.setMediaFileDirectory(
					TachographCallback.FILE_TYPE_IMAGE, "Image");
			recorderBack.setClientName(this.getPackageName());

			recorderBack.setVideoSize(720, 480);
			recorderBack.setVideoFrameRate(Constant.Record.FRAME_RATE);
			recorderBack.setVideoBiteRate(Constant.Record.BIT_RATE_720P);

			recorderBack.setVideoSeconds(1 * 60);
			recorderBack.setVideoOverlap(0);
			recorderBack.prepare();
		} catch (Exception e) {
			MyLog.e("[MainActivity]setupRecorder: Catch Exception!");
		}

		// MyApp.cameraState = CameraState.OKAY;
	}

	private void releaseRecorderBack() {
		try {
			if (recorderBack != null) {
				recorderBack.stop();
				recorderBack.close();
				recorderBack.release();
				recorderBack = null;
				MyLog.d("Record Release");
			}
		} catch (Exception e) {
			MyLog.e("[MainActivity]releaseRecorder: Catch Exception!");
		}
	}

	public void setupBack() {
		releaseBack();
		if (openCameraBack()) {
			setupRecorderBack();
		}
	}

	public void releaseBack() {
		releaseRecorderBack();
		closeCameraBack();
	}

	class BackTachographCallback implements TachographCallback {

		@Override
		public void onError(int error) {
			switch (error) {
			case TachographCallback.ERROR_SAVE_VIDEO_FAIL:
				break;

			case TachographCallback.ERROR_SAVE_IMAGE_FAIL:
				break;

			case TachographCallback.ERROR_RECORDER_CLOSED:
				MyLog.e("Record Error : ERROR_RECORDER_CLOSED");
				break;

			default:
				break;
			}
		}

		/**
		 * 文件保存回调，注意：存在延时，不能用作重置录像跑秒时间
		 * 
		 * @param type
		 *            0-图片 1-视频
		 * 
		 * @param path
		 *            视频：/storage/sdcard2/DrivingRecord/VideoFront/2016-05-
		 *            04_155010.mp4
		 *            图片:/storage/sdcard2/DrivingRecord/Image/2015-
		 *            07-01_105536.jpg
		 */
		@Override
		public void onFileSave(int type, String path) {
			try {
				if (type == 1) { // 视频
				} else { // 图片
				}
				MyLog.d("[onFileSave]Type=" + type + ",Save path:" + path);
			} catch (Exception e) {
				e.printStackTrace();
				MyLog.e("[Main]onFileSave catch Exception:" + e.toString());
			}
		}

		@Override
		public void onFileStart(int type, String path) {
			MyLog.v("[onFileStart.Back]Path:" + path);
		}

	}

}