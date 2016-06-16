package com.tchip.autorecord.ui;

import java.io.File;

import com.tchip.autorecord.Constant;
import com.tchip.autorecord.MyApp;
import com.tchip.autorecord.R;
import com.tchip.autorecord.Typefaces;
import com.tchip.autorecord.db.DriveVideo;
import com.tchip.autorecord.db.DriveVideoDbHelper;
import com.tchip.autorecord.service.SensorWatchService;
import com.tchip.autorecord.util.ClickUtil;
import com.tchip.autorecord.util.DateUtil;
import com.tchip.autorecord.util.HintUtil;
import com.tchip.autorecord.util.MyLog;
import com.tchip.autorecord.util.ProviderUtil;
import com.tchip.autorecord.util.ProviderUtil.Name;
import com.tchip.autorecord.util.SettingUtil;
import com.tchip.autorecord.util.StorageUtil;
import com.tchip.tachograph.TachographCallback;
import com.tchip.tachograph.TachographRecorder;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.ContentObserver;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.SurfaceHolder.Callback;
import android.widget.ImageButton;
import android.widget.TextView;

public class MainActivity extends Activity {

	private Context context;

	/** onFileSave时释放空间 */
	private static final HandlerThread fileSaveHandlerThread = new HandlerThread(
			"filesave-thread");
	static {
		fileSaveHandlerThread.start();
	}
	private final Handler releaseStorageWhenFileSaveHandler = new ReleaseStorageWhenFileSaveHandler(
			fileSaveHandlerThread.getLooper());
	private Handler mMainHandler; // 主线程Handler

	/** startRecord时释放空间 */
	private static final HandlerThread startRecordHandlerThread = new HandlerThread(
			"startrecord-thread");
	static {
		startRecordHandlerThread.start();
	}
	private final Handler releaseStorageWhenStartRecordHandler = new ReleaseStorageWhenStartRecordHandler(
			startRecordHandlerThread.getLooper());

	private SharedPreferences sharedPreferences;
	private Editor editor;
	private DriveVideoDbHelper videoDb;

	/** 录像按钮 */
	private ImageButton imageVideoState;
	/** 加锁按钮 */
	private ImageButton imageVideoLock;
	private TextView textVideoLock;
	/** 前后切换 */
	private ImageButton imageCameraSwitch;
	private TextView textCameraSwitch;
	/** 视频尺寸 */
	private ImageButton imageVideoSize;
	private TextView textVideoSize;
	/** 视频分段 */
	private ImageButton imageVideoLength;
	private TextView textVideoLength;
	/** 静音按钮 */
	private ImageButton imageVideoMute;
	private TextView textVideoMute;
	/** 拍照按钮 */
	private ImageButton imagePhotoTake;

	private TextView textRecordTime;

	// 前置
	private Camera camera;
	private SurfaceView surfaceView;
	private SurfaceHolder surfaceHolder;
	private TachographRecorder recorder;

	private int resolutionState, intervalState, muteState;

	private PowerManager powerManager;
	private WakeLock wakeLock;

	/** Intent是否是新的 */
	private boolean isIntentInTime = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mMainHandler = new Handler(this.getMainLooper());
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_main);
		context = getApplicationContext();
		powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE); // 获取屏幕状态

		sharedPreferences = getSharedPreferences(Constant.MySP.NAME,
				Context.MODE_PRIVATE);
		editor = sharedPreferences.edit();
		videoDb = new DriveVideoDbHelper(context); // 视频数据库

		initialLayout();

		getContentResolver()
				.registerContentObserver(
						Uri.parse("content://com.tchip.provider.AutoProvider/state/name/"),
						true, new AutoContentObserver(new Handler()));

		StorageUtil.createRecordDirectory();
		setupRecordDefaults();
		setupRecordViews();

		// 首次启动是否需要自动录像
		if (1 == SettingUtil.getAccStatus()) {
			MyApp.isAccOn = true; // 同步ACC状态
			new Thread(new AutoThread()).start(); // 序列任务线程
		} else {
			MyApp.isAccOn = false; // 同步ACC状态
			MyLog.v("[Main]ACC Check:OFF");
		}
		// 碰撞侦测服务
		Intent intentSensor = new Intent(this, SensorWatchService.class);
		startService(intentSensor);

		new Thread(new BackThread()).start(); // 后台线程

		mainReceiver = new MainReceiver();
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(Constant.Broadcast.ACC_ON);
		intentFilter.addAction(Constant.Broadcast.ACC_OFF);
		intentFilter.addAction(Constant.Broadcast.GSENSOR_CRASH);
		intentFilter.addAction(Constant.Broadcast.SPEECH_COMMAND);
		intentFilter.addAction(Constant.Broadcast.MEDIA_FORMAT);
		intentFilter.addAction(Constant.Broadcast.GOING_SHUTDOWN);
		intentFilter.addAction(Constant.Broadcast.RELEASE_RECORD);
		registerReceiver(mainReceiver, intentFilter);
	}

	@Override
	protected void onResume() {
		MyLog.v("[Main]onResume");
		MyApp.isMainForeground = true;
		try {
			refreshRecordButton(); // 更新录像界面按钮状态
			setupRecordViews();
		} catch (Exception e) {
			e.printStackTrace();
			MyLog.e("[Main]onResume catch Exception:" + e.toString());
		}

		// 接收额外信息
		Bundle extras = getIntent().getExtras();
		if (extras != null) {
			String reason = extras.getString("reason");
			long sendTime = extras.getLong("time");
			isIntentInTime = ClickUtil.isIntentInTime(sendTime);
			if (isIntentInTime) {
				MyLog.v("reason:" + reason);
				if ("autoui_oncreate".equals(reason) || "acc_on".equals(reason)) { // 回到主界面
					MyApp.shouldMountRecord = true;
					new Thread(new BackHomeThread()).start();
				}
			}
		}
		super.onResume();
	}

	@Override
	protected void onPause() {
		MyLog.v("[Main]onPause");
		MyApp.isMainForeground = false;
		MyLog.v("[onPause]MyApplication.isVideoReording:"
				+ MyApp.isVideoReording);

		// ACC在的时候不频繁释放录像区域：ACC在的时候Suspend？
		if (!MyApp.isAccOn && !MyApp.isVideoReording) {
			MyApp.isVideoLockSecond = false;
		}
		super.onPause();
	}

	@Override
	protected void onStop() {
		MyLog.v("[Main]onStop");
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		MyLog.v("[Main]onDestroy");
		// 释放录像区域
		releaseRecorder();
		closeCamera();
		videoDb.close();

		// 关闭碰撞侦测服务
		Intent intentCrash = new Intent(context, SensorWatchService.class);
		stopService(intentCrash);

		if (mainReceiver != null) {
			unregisterReceiver(mainReceiver);
		}
		super.onDestroy();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			backToHome();
			return true;
		} else
			return super.onKeyDown(keyCode, event);
	}

	class BackHomeThread implements Runnable {

		@Override
		public void run() {
			try {
				Thread.sleep(3000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			sendKeyCode(KeyEvent.KEYCODE_HOME);
		}

	}

	/** ContentProvder监听 */
	public class AutoContentObserver extends ContentObserver {

		public AutoContentObserver(Handler handler) {
			super(handler);
		}

		@Override
		public void onChange(boolean selfChange, Uri uri) {
			String name = uri.getLastPathSegment(); // getPathSegments().get(2);
			if (name.equals("state")) { // insert

			} else { // update
				MyLog.v("[AutoRecord.ContentObserver]onChange,selfChange:"
						+ selfChange + ",Name:" + name);
				if (Name.SET_DETECT_CRASH_STATE.equals(name)) {
					String strDetectCrashState = ProviderUtil.getValue(context,
							Name.SET_DETECT_CRASH_STATE);
					if (strDetectCrashState != null
							&& strDetectCrashState.trim().length() > 0) {
						if ("0".equals(strDetectCrashState)) {
							MyApp.isCrashOn = false;
						} else {
							MyApp.isCrashOn = true;
						}
					} else {
						MyApp.isCrashOn = true;
					}
				} else if (Name.SET_DETECT_CRASH_LEVEL.equals(name)) {
					String strDetectCrashLevel = ProviderUtil.getValue(context,
							Name.SET_DETECT_CRASH_LEVEL);
					if (strDetectCrashLevel != null
							&& strDetectCrashLevel.trim().length() > 0) {
						if ("0".equals(strDetectCrashLevel)) {
							MyApp.crashSensitive = 0;
						} else if ("2".equals(strDetectCrashLevel)) {
							MyApp.crashSensitive = 2;
						} else {
							MyApp.crashSensitive = 1;
						}
					} else {
						MyApp.crashSensitive = 2;
					}
				} else if (Name.SET_PARK_MONITOR_STATE.equals(name)) {

				} else if (Name.ACC_STATE.equals(name)) {
					MyApp.isAccOn = (SettingUtil.getAccStatus() == 1);
				}
			}
			super.onChange(selfChange, uri);
		}

		@Override
		public void onChange(boolean selfChange) {
			super.onChange(selfChange);
		}

	}

	/**
	 * 获取休眠锁
	 * 
	 * PARTIAL_WAKE_LOCK
	 * 
	 * SCREEN_DIM_WAKE_LOCK
	 * 
	 * FULL_WAKE_LOCK
	 * 
	 * ON_AFTER_RELEASE
	 */
	private void acquireWakeLock(long timeout) {
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				this.getClass().getCanonicalName());
		wakeLock.acquire(timeout);
		MyLog.v("[SleepOnOff]WakeLock acquire, timeout:" + timeout);
	}

	/** 返回HOME */
	private void backToHome() {
		Intent intent = new Intent(Intent.ACTION_MAIN);
		intent.addCategory(Intent.CATEGORY_HOME);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
	}

	private MainReceiver mainReceiver;

	public class MainReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			MyLog.v("[AutoRecord.MainReceiver]action:" + action);
			if (action.equals(Constant.Broadcast.ACC_OFF)) {
				MyApp.isAccOn = false;
			} else if (action.equals(Constant.Broadcast.ACC_ON)) {
				MyApp.isAccOn = true;
				MyApp.shouldWakeRecord = true;
			} else if (action.equals(Constant.Broadcast.GSENSOR_CRASH)) { // 停车守卫:侦测到碰撞广播触发
				// if (!MyApp.isAccOn) {
				// MyLog.v("[GSENSOR_CRASH]Before State->shouldCrashRecord:"
				// + MyApp.shouldCrashRecord
				// + ",shouldStopWhenCrashVideoSave:"
				// + MyApp.shouldStopWhenCrashVideoSave);
				//
				// if (MyApp.shouldStopWhenCrashVideoSave) {
				// if (!MyApp.shouldCrashRecord && !MyApp.isVideoReording) {
				// MyApp.shouldCrashRecord = true;
				// MyApp.shouldStopWhenCrashVideoSave = true;
				// }
				// } else {
				// MyApp.shouldCrashRecord = true;
				// MyApp.shouldStopWhenCrashVideoSave = true;
				// }
				// }
			} else if (action.equals(Constant.Broadcast.SPEECH_COMMAND)) {
				String command = intent.getExtras().getString("command");
				if ("open_dvr".equals(command)) {
					if (MyApp.isAccOn && !MyApp.isVideoReording) {
						MyApp.shouldMountRecord = true;
					}
				} else if ("close_dvr".equals(command)) {
					if (MyApp.isVideoReording) {
						MyApp.shouldStopRecordFromVoice = true;
					}
				} else if ("take_photo".equals(command)
						|| "take_photo_wenxin".equals(command)) {
					takePhoto();
				}
			} else if (action.equals(Constant.Broadcast.MEDIA_FORMAT)) {
				String path = intent.getExtras().getString("path");
				MyLog.e("SleepOnOffReceiver: MEDIA_FORMAT !! Path:" + path);
				if ("/storage/sdcard2".equals(path)) {
					MyApp.isVideoCardFormat = true;
				}
			} else if (Constant.Broadcast.GOING_SHUTDOWN.equals(action)) {
				MyApp.isGoingShutdown = true;
			} else if (Constant.Broadcast.RELEASE_RECORD.equals(action)) { // 退出录像
				releaseCameraZone();
				android.os.Process.killProcess(android.os.Process.myPid());
				System.exit(1);
			}
		}
	}

	private void speakVoice(String content) {
		sendBroadcast(new Intent(Constant.Broadcast.TTS_SPEAK).putExtra(
				"content", content));
	}

	/**
	 * 序列任务线程，分步执行：
	 * 
	 * 1.初次启动清空录像文件夹
	 * 
	 * 2.自动录像
	 * 
	 * 3.初始化服务：轨迹记录
	 */
	public class AutoThread implements Runnable {

		@Override
		public void run() {
			try {
				StartCheckErrorFileThread(); // 检查并删除异常视频文件
				// 自动录像:如果已经在录像则不处理
				if (Constant.Record.autoRecord && !MyApp.isVideoReording) {
					Thread.sleep(Constant.Record.autoRecordDelay);
					Message message = new Message();
					message.what = 1;
					autoHandler.sendMessage(message);
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
				MyLog.e("[Main]AutoThread: Catch Exception!");
			}
		}
	}

	final Handler autoHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				startRecord();
				break;

			default:
				break;
			}
		}
	};

	/** 后台线程，用以监测是否需要录制碰撞加锁视频(停车侦测) */
	public class BackThread implements Runnable {

		@Override
		public void run() {
			while (true) {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				Message message = new Message();
				message.what = 1;
				backHandler.sendMessage(message);
				// 修正标志：不对第二段视频加锁
				if (MyApp.isVideoLockSecond && !MyApp.isVideoReording) {
					MyApp.isVideoLockSecond = false;
				}
			}
		}
	}

	/**
	 * 后台线程的Handler,监测：
	 * 
	 * 1.是否需要休眠唤醒
	 * 
	 * 2.停车守卫侦测，启动录像
	 * 
	 * 3.ACC下电，拍照
	 * 
	 * 4.插入录像卡，若ACC在，启动录像
	 */
	final Handler backHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				if (MyApp.shouldWakeRecord) {
					MyApp.shouldWakeRecord = false;
					if (MyApp.isAccOn && !MyApp.isVideoReording) {
						new Thread(new AutoThread()).start(); // 序列任务线程
					}
				}
				if (MyApp.shouldMountRecord) {
					MyApp.shouldMountRecord = false;
					if (MyApp.isAccOn && !MyApp.isVideoReording) {
						new Thread(new RecordWhenMountThread()).start();
					}
				}

				if (MyApp.shouldCrashRecord) { // 停车侦测录像
					MyApp.shouldCrashRecord = false;
					if (!MyApp.isVideoReording) {
						if (Constant.Record.parkVideoLock) { // 是否需要加锁
							MyApp.isVideoLock = true;
						}
						new Thread(new RecordWhenCrashThread()).start();
					}
				}
				if (MyApp.shouldTakePhotoWhenAccOff) { // ACC下电拍照
					MyApp.shouldTakePhotoWhenAccOff = false;
					new Thread(new TakePhotoWhenAccOffThread()).start();
				}
				if (MyApp.shouldTakeVoicePhoto) { // 语音拍照
					MyApp.shouldTakeVoicePhoto = false;
					new Thread(new TakeVoicePhotoThread()).start();
				}
				break;

			default:
				break;
			}
		}
	};

	public class TakeVoicePhotoThread implements Runnable {
		@Override
		public void run() {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Message messageTakePhotoWhenAccOff = new Message();
			messageTakePhotoWhenAccOff.what = 2;
			takePhotoWhenEventHappenHandler
					.sendMessage(messageTakePhotoWhenAccOff);
		}
	}

	/** ACC下电拍照线程 */
	public class TakePhotoWhenAccOffThread implements Runnable {

		@Override
		public void run() {
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Message messageTakePhotoWhenAccOff = new Message();
			messageTakePhotoWhenAccOff.what = 1;
			takePhotoWhenEventHappenHandler
					.sendMessage(messageTakePhotoWhenAccOff);
		}

	}

	/**
	 * 处理需要拍照事件：
	 * 
	 * 1.ACC_OFF，拍照给DSA
	 * 
	 * 2.语音拍照
	 */
	final Handler takePhotoWhenEventHappenHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				takePhotoWhenAccOff();
				break;

			case 2:
				takePhotoWhenVoiceCommand();
				break;

			default:
				break;
			}
		}
	};

	/** 插入录像卡录制一个视频线程 */
	public class RecordWhenMountThread implements Runnable {

		@Override
		public void run() {
			MyLog.v("[Main]run RecordWhenMountThread");
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Message message = new Message();
			message.what = 1;
			recordWhenMountHandler.sendMessage(message);
		}

	}

	/** 插入视频卡时录制视频 */
	final Handler recordWhenMountHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				try {
					if (!MyApp.isVideoReording) {
						startRecordTask();
					}
					MyLog.v("[Record]isVideoReording:" + MyApp.isVideoReording);
				} catch (Exception e) {
					MyLog.e("[EventRecord]recordWhenEventHappenHandler catch exception: "
							+ e.toString());
				}
				break;

			default:
				break;
			}
		}
	};

	/** 底层碰撞后录制一个视频线程 */
	public class RecordWhenCrashThread implements Runnable {

		@Override
		public void run() {
			MyLog.v("[Thread]run RecordWhenCrashThread");
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Message message = new Message();
			message.what = 1;
			recordWhenCrashHandler.sendMessage(message);
		}
	}

	/**
	 * 以下事件发生时录制视频：
	 * 
	 * 停车守卫：底层碰撞
	 */
	final Handler recordWhenCrashHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				try {
					if (!MyApp.isVideoReording) {
						if (!MyApp.isMainForeground) { // 发送Home键，回到主界面
						}
						setInterval(3 * 60); // 防止在分段一分钟的时候，停车守卫录出1分和0秒两段视频

						StartCheckErrorFileThread();
						if (!MyApp.isVideoReording) {
							if (startRecordTask() == 0) {
								setRecordState(true);
							} else {
								MyLog.e("Start Record Failed");
							}
						}
					}
					setupRecordViews();
					MyLog.v("[Record]isVideoReording:" + MyApp.isVideoReording);
				} catch (Exception e) {
					MyLog.e("[EventRecord]recordWhenEventHappenHandler catch exception: "
							+ e.toString());
				}
				break;

			default:
				break;
			}
		}
	};

	/** 更改分辨率后重启录像 */
	public class StartRecordWhenChangeSizeThread implements Runnable {

		@Override
		public void run() {
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Message message = new Message();
			message.what = 1;
			startRecordWhenChangeSizeOrMute.sendMessage(message);
		}
	}

	/** 更改录音/静音状态后重启录像 */
	public class StartRecordWhenChangeMuteThread implements Runnable {

		@Override
		public void run() {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Message message = new Message();
			message.what = 1;
			startRecordWhenChangeSizeOrMute.sendMessage(message);
		}

	}

	final Handler startRecordWhenChangeSizeOrMute = new Handler() {

		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				if (!MyApp.isVideoReording) {
					startRecordTask();
				}
				break;

			default:
				break;
			}
		}

	};

	private void initialCameraSurface() {
		surfaceView = (SurfaceView) findViewById(R.id.surfaceView);
		surfaceView.setOnClickListener(new MyOnClickListener());
		surfaceView.getHolder().addCallback(new FrontCallBack());
	}

	/** 初始化布局 */
	private void initialLayout() {
		MyOnClickListener myOnClickListener = new MyOnClickListener();
		initialCameraSurface(); // 录像窗口
		textRecordTime = (TextView) findViewById(R.id.textRecordTime);
		textRecordTime.setTypeface(Typefaces.get(this, Constant.Path.FONT
				+ "Font-Quartz-Regular.ttf"));

		// 录制
		imageVideoState = (ImageButton) findViewById(R.id.imageVideoState);
		imageVideoState.setOnClickListener(myOnClickListener);

		// 锁定
		imageVideoLock = (ImageButton) findViewById(R.id.imageVideoLock);
		imageVideoLock.setOnClickListener(myOnClickListener);
		textVideoLock = (TextView) findViewById(R.id.textVideoLock);
		textVideoLock.setOnClickListener(myOnClickListener);

		// 前后切换图标
		imageCameraSwitch = (ImageButton) findViewById(R.id.imageCameraSwitch);
		imageCameraSwitch.setOnClickListener(myOnClickListener);
		textCameraSwitch = (TextView) findViewById(R.id.textCameraSwitch);
		textCameraSwitch.setOnClickListener(myOnClickListener);

		// 拍照
		imagePhotoTake = (ImageButton) findViewById(R.id.imagePhotoTake);
		imagePhotoTake.setOnClickListener(myOnClickListener);

		// 视频尺寸
		imageVideoSize = (ImageButton) findViewById(R.id.imageVideoSize);
		imageVideoSize.setOnClickListener(myOnClickListener);
		textVideoSize = (TextView) findViewById(R.id.textVideoSize);
		textVideoSize.setOnClickListener(myOnClickListener);

		// 视频分段长度
		imageVideoLength = (ImageButton) findViewById(R.id.imageVideoLength);
		imageVideoLength.setOnClickListener(myOnClickListener);
		textVideoLength = (TextView) findViewById(R.id.textVideoLength);
		textVideoLength.setOnClickListener(myOnClickListener);

		// 静音
		imageVideoMute = (ImageButton) findViewById(R.id.imageVideoMute);
		imageVideoMute.setOnClickListener(myOnClickListener);
		textVideoMute = (TextView) findViewById(R.id.textVideoMute);
		textVideoMute.setOnClickListener(myOnClickListener);
	}

	private int secondCount = -1;

	/**
	 * 录制时间秒钟复位:
	 * 
	 * 1.停止录像{@link #stopRecorder()}
	 * 
	 * 2.录像过程中更改录像分辨率
	 * 
	 * 3.录像过程中更改静音状态
	 * 
	 * 4.视频保存失败{@link #onError(int)}
	 * 
	 * 5.开始录像{@link #startRecordTask()}
	 * 
	 */
	private void resetRecordTimeText() {
		secondCount = -1;
		textRecordTime.setText("00 : 00");
	}

	Thread mRunSecond = null;

	/** 开启录像跑秒线程 */
	private void startUpdateRecordTimeThread() {
		if (!MyApp.isUpdateTimeThreadRun) {
			if (mRunSecond != null) {
				// mRunSecond.interrupt();
			}
			new Thread(new UpdateRecordTimeThread()).start(); // 更新录制时间
		} else {
			MyLog.e("[Main]UpdateRecordTimeThread already run");
		}
	}

	/** 设置当前录像状态 */
	private void setRecordState(boolean isVideoRecord) {
		ProviderUtil.setValue(context, Name.REC_FRONT_STATE,
				isVideoRecord ? "1" : "0");
		if (isVideoRecord) {
			if (!MyApp.isVideoReording) {
				MyApp.isVideoReording = true;
				textRecordTime.setVisibility(View.VISIBLE);
				startUpdateRecordTimeThread();
				setupRecordViews();
			}
		} else {
			if (MyApp.isVideoReording) {
				MyApp.isVideoReording = false;
				textRecordTime.setVisibility(View.INVISIBLE);
				resetRecordTimeText();
				MyApp.isUpdateTimeThreadRun = false;
				setupRecordViews();
			}
		}
	}

	public class UpdateRecordTimeThread implements Runnable {

		@Override
		public void run() {
			// 解决录像时，快速点击录像按钮两次，线程叠加跑秒过快的问题
			do {
				MyApp.isUpdateTimeThreadRun = true;
				if (MyApp.isCrashed) {
					Message messageVideoLock = new Message();
					messageVideoLock.what = 4;
					updateRecordTimeHandler.sendMessage(messageVideoLock);
				}
				if (MyApp.isAppException) { // 程序异常,停止录像
					MyApp.isAppException = false;
					MyLog.e("App exception, stop record!");
					Message messageException = new Message();
					messageException.what = 8;
					updateRecordTimeHandler.sendMessage(messageException);
					return;
				} else if (MyApp.isVideoCardEject) { // 录像时视频SD卡拔出停止录像
					MyLog.e("SD card remove badly or power unconnected, stop record!");
					Message messageEject = new Message();
					messageEject.what = 2;
					updateRecordTimeHandler.sendMessage(messageEject);
					return;
				} else if (MyApp.isVideoCardFormat) { // 录像SD卡格式化
					MyApp.isVideoCardFormat = false;
					MyLog.e("SD card is format, stop record!");
					Message messageFormat = new Message();
					messageFormat.what = 7;
					updateRecordTimeHandler.sendMessage(messageFormat);
					return;
				} else if (MyApp.isGoingShutdown) {
					MyApp.isGoingShutdown = false;
					MyLog.e("Going shutdown, stop record!");
					Message messageFormat = new Message();
					messageFormat.what = 9;
					updateRecordTimeHandler.sendMessage(messageFormat);
					return;
				} else if (!MyApp.isAccOn
						&& !MyApp.shouldStopWhenCrashVideoSave) { // ACC下电停止录像
					MyLog.e("Stop Record:isSleeping = true");
					Message messageSleep = new Message();
					messageSleep.what = 5;
					updateRecordTimeHandler.sendMessage(messageSleep);
					return;
				} else if (MyApp.shouldStopRecordFromVoice) {
					MyApp.shouldStopRecordFromVoice = false;
					Message messageStopRecordFromVoice = new Message();
					messageStopRecordFromVoice.what = 6;
					updateRecordTimeHandler
							.sendMessage(messageStopRecordFromVoice);
					return;
				} else {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					Message messageSecond = new Message();
					messageSecond.what = 1;
					updateRecordTimeHandler.sendMessage(messageSecond);
				}
			} while (MyApp.isVideoReording);
			MyApp.isUpdateTimeThreadRun = false;
		}

	}

	final Handler updateRecordTimeHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1: { // 处理停车守卫录像
				this.removeMessages(1);
				if (!ClickUtil.isPlusRecordTimeTooQuick(900)) {
					secondCount++;
					if (secondCount % 5 == 0) {
						ProviderUtil.setValue(context, Name.REC_FRONT_STATE,
								"1");
					}
				}
				if (MyApp.shouldStopWhenCrashVideoSave && MyApp.isVideoReording) {
					if (secondCount == Constant.Record.parkVideoLength) {
						String videoTimeStr = sharedPreferences.getString(
								"videoTime", "3");
						intervalState = "1".equals(videoTimeStr) ? Constant.Record.STATE_INTERVAL_1MIN
								: Constant.Record.STATE_INTERVAL_3MIN;

						MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 1");
						stopRecorder5Times(); // 停止录像
						setInterval(("1".equals(videoTimeStr)) ? 1 * 60
								: 3 * 60); // 重设视频分段
					}
				}

				switch (intervalState) { // 重置时间
				case Constant.Record.STATE_INTERVAL_3MIN:
					if (secondCount >= 180) {
						secondCount = 0;
						acquireWakeLock(185 * 1000);
					}
					break;

				case Constant.Record.STATE_INTERVAL_1MIN:
					if (secondCount >= 60) {
						secondCount = 0;
						acquireWakeLock(65 * 1000);
					}
					break;

				default:
					break;
				}
				textRecordTime.setText(DateUtil
						.getFormatTimeBySecond(secondCount));
				this.removeMessages(1);
			}
				break;

			case 2: { // SD卡异常移除：停止录像
				this.removeMessages(2);
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 2");
				stopRecorder5Times();
				String strVideoCardEject = getResources().getString(
						R.string.hint_sd_remove_badly);
				HintUtil.showToast(MainActivity.this, strVideoCardEject);

				MyLog.e("CardEjectReceiver:Video SD Removed");
				speakVoice(strVideoCardEject);
				this.removeMessages(2);
			}
				break;

			case 4: {
				this.removeMessages(4);
				MyApp.isCrashed = false;
				// 碰撞后判断是否需要加锁第二段视频
				if (intervalState == Constant.Record.STATE_INTERVAL_1MIN) {
					if (secondCount > 45) {
						MyApp.isVideoLockSecond = true;
					}
				} else if (intervalState == Constant.Record.STATE_INTERVAL_3MIN) {
					if (secondCount > 165) {
						MyApp.isVideoLockSecond = true;
					}
				}
				setupRecordViews();
				this.removeMessages(4);
			}
				break;

			case 5: { // 进入休眠，停止录像
				this.removeMessages(5);
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 5");
				stopRecorder5Times();
				this.removeMessages(5);
			}
				break;

			case 6: { // 语音命令：停止录像
				this.removeMessages(6);
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 6");
				stopRecorder5Times();
				this.removeMessages(6);
			}
				break;

			case 7: {
				this.removeMessages(7);
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 7");
				stopRecorder5Times();
				String strVideoCardFormat = getResources().getString(
						R.string.hint_sd2_format);
				HintUtil.showToast(MainActivity.this, strVideoCardFormat);

				MyLog.e("CardEjectReceiver:Video SD Removed");
				speakVoice(strVideoCardFormat);
				this.removeMessages(7);
			}
				break;

			case 8: { // 程序异常，停止录像
				this.removeMessages(8);
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 8");
				stopRecorder5Times();
				this.removeMessages(8);
			}
				break;

			case 9: { // 系统关机，停止录像
				this.removeMessages(9);
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 9");
				stopRecorder5Times();
				String strGoingShutdown = getResources().getString(
						R.string.hint_going_shutdown);
				HintUtil.showToast(MainActivity.this, strGoingShutdown);

				MyLog.e("CardEjectReceiver:Going Shutdown");
				speakVoice(strGoingShutdown);
				this.removeMessages(9);
			}
				break;

			default:
				break;
			}
		}
	};

	class MyOnClickListener implements View.OnClickListener {
		@Override
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.imageVideoState:
				if (!ClickUtil.isQuickClick(2000)) {
					if (MyApp.isVideoReording) {
						speakVoice(getResources().getString(
								R.string.hint_record_stop));
						MyLog.v("[onClick]stopRecorder()");
						stopRecorder5Times();
					} else {
						if (StorageUtil.isVideoCardExists()) {
							speakVoice(getResources().getString(
									R.string.hint_record_start));
							startRecord();
						} else {
							noVideoSDHint();
						}
					}
				}
				break;

			case R.id.imageVideoLock:
			case R.id.textVideoLock:
				if (!ClickUtil.isQuickClick(1000)) {
					if (MyApp.isVideoReording) {
						lockOrUnlockVideo();
					} else {
						HintUtil.showToast(MainActivity.this, getResources()
								.getString(R.string.hint_not_record));
					}
				}
				break;

			case R.id.imageVideoSize:
			case R.id.textVideoSize:
				if (!ClickUtil.isQuickClick(3000)) {
					// 切换分辨率录像停止，需要重置时间
					MyApp.shouldVideoRecordWhenChangeSize = MyApp.isVideoReording;
					MyApp.isVideoReording = false;
					resetRecordTimeText();
					textRecordTime.setVisibility(View.INVISIBLE);
					if (resolutionState == Constant.Record.STATE_RESOLUTION_1080P) {
						setResolution(Constant.Record.STATE_RESOLUTION_720P);
						editor.putString("videoSize", "720");
						MyApp.isVideoReording = false;
						speakVoice(getResources().getString(
								R.string.hint_video_size_720));
					} else if (resolutionState == Constant.Record.STATE_RESOLUTION_720P) {
						setResolution(Constant.Record.STATE_RESOLUTION_1080P);
						editor.putString("videoSize", "1080");
						MyApp.isVideoReording = false;
						speakVoice(getResources().getString(
								R.string.hint_video_size_1080));
					}
					editor.commit();
					setupRecordViews();
					// 修改分辨率后按需启动录像
					if (MyApp.shouldVideoRecordWhenChangeSize) {
						new Thread(new StartRecordWhenChangeSizeThread())
								.start();
						MyApp.shouldVideoRecordWhenChangeSize = false;
					}
				}
				break;

			case R.id.imageVideoLength:
			case R.id.textVideoLength:
				if (!ClickUtil.isQuickClick(1000)) {
					if (intervalState == Constant.Record.STATE_INTERVAL_3MIN) {
						if (setInterval(1 * 60) == 0) {
							intervalState = Constant.Record.STATE_INTERVAL_1MIN;
							editor.putString("videoTime", "1");
							speakVoice(getResources().getString(
									R.string.hint_video_time_1));
						}
					} else if (intervalState == Constant.Record.STATE_INTERVAL_1MIN) {
						if (setInterval(3 * 60) == 0) {
							intervalState = Constant.Record.STATE_INTERVAL_3MIN;
							editor.putString("videoTime", "3");
							speakVoice(getResources().getString(
									R.string.hint_video_time_3));
						}
					}
					editor.commit();
					setupRecordViews();
				}
				break;

			case R.id.imageVideoMute:
			case R.id.textVideoMute:
				if (!ClickUtil.isQuickClick(1500)) {
					// 切换录音/静音状态停止录像，需要重置时间
					MyApp.shouldVideoRecordWhenChangeMute = MyApp.isVideoReording;
					if (MyApp.isVideoReording) {
						stopRecorder5Times();
						resetRecordTimeText();
						textRecordTime.setVisibility(View.INVISIBLE);
						MyApp.isVideoReording = false;
					}
					if (muteState == Constant.Record.STATE_MUTE) {
						setMute(false, true);
					} else if (muteState == Constant.Record.STATE_UNMUTE) {
						setMute(true, true);
					}
					setupRecordViews();
					// 修改录音/静音后按需还原录像状态
					if (MyApp.shouldVideoRecordWhenChangeMute) {
						new Thread(new StartRecordWhenChangeMuteThread())
								.start();
						MyApp.shouldVideoRecordWhenChangeMute = false;
					}
				}
				break;

			// case R.id.surfaceView:
			case R.id.imagePhotoTake:
				if (!ClickUtil.isQuickClick(1500)) {
					takePhoto();
				}
				break;

			case R.id.imageCameraSwitch:
			case R.id.textCameraSwitch:
				sendBroadcast(new Intent("com.tchip.showUVC"));
				break;

			default:
				break;
			}
		}
	}

	/** 启动录像 */
	private void startRecord() {
		try {
			if (!MyApp.isVideoReording) {
				if (!MyApp.isAccOn) {
					if (!ClickUtil.isHintSleepTooQuick(3000)) {
						HintUtil.showToast(MainActivity.this, getResources()
								.getString(R.string.hint_stop_record_sleeping));
					}
				} else {
					new Thread(new StartRecordThread()).start(); // 开始录像
				}
			} else {
				MyLog.v("[startRecord]Already record yet");
			}
			setupRecordViews();
			MyLog.v("MyApplication.isVideoReording:" + MyApp.isVideoReording);
		} catch (Exception e) {
			e.printStackTrace();
			MyLog.e("[MainActivity]startRecord catch exception: "
					+ e.toString());
		}
	}

	/** 加锁或解锁视频 */
	private void lockOrUnlockVideo() {
		if (!MyApp.isVideoLock) {
			MyApp.isVideoLock = true;
			speakVoice(getResources().getString(R.string.hint_video_lock));
		} else {
			MyApp.isVideoLock = false;
			MyApp.isVideoLockSecond = false;
			speakVoice(getResources().getString(R.string.hint_video_unlock));
		}
		setupRecordViews();
	}

	/** 重置预览区域 */
	private void recreateCameraZone() {
		if (camera == null) {
			// surfaceHolder = holder;
			releaseRecorder();
			closeCamera();
			if (openCamera()) {
				setupRecorder();
			}
		} else {
			try {
				camera.lock();
				camera.setPreviewDisplay(surfaceHolder);
				camera.startPreview();
				camera.unlock();
			} catch (Exception e) {
				// e.printStackTrace();
			}
		}
	}

	/**
	 * 释放Camera
	 */
	private void releaseCameraZone() {
		if (!MyApp.isAccOn && !MyApp.isMainForeground) {
			// 释放录像区域
			releaseRecorder();
			closeCamera();
			// surfaceHolder = null;
			if (camera != null) {
				camera.stopPreview();
			}
			MyApp.shouldResetRecordWhenResume = true;
			MyLog.v("[Record]releaseCameraZone");
			MyApp.isCameraPreview = false;
		}
	}

	// *********** Record ***********
	/** 设置录制初始值 */
	private void setupRecordDefaults() {
		refreshRecordButton();

		MyApp.isVideoReording = false;

		// 录音,静音;默认录音
		boolean videoMute = sharedPreferences.getBoolean("videoMute",
				Constant.Record.muteDefault);
		muteState = videoMute ? Constant.Record.STATE_MUTE
				: Constant.Record.STATE_UNMUTE;
	}

	private void refreshRecordButton() {
		// 视频尺寸：公版默认720P，善领默认1080P
		// String videoSizeStr = sharedPreferences.getString("videoSize",
		// Constant.Module.isPublic ? "720" : "1080");
		String videoSizeStr = sharedPreferences.getString("videoSize", "1080");
		resolutionState = "1080".equals(videoSizeStr) ? Constant.Record.STATE_RESOLUTION_1080P
				: Constant.Record.STATE_RESOLUTION_720P;

		String videoTimeStr = sharedPreferences.getString("videoTime", "3"); // 视频分段
		intervalState = "1".equals(videoTimeStr) ? Constant.Record.STATE_INTERVAL_1MIN
				: Constant.Record.STATE_INTERVAL_3MIN;
	}

	/** 绘制录像按钮 */
	private void setupRecordViews() {
		// 视频分辨率
		if (resolutionState == Constant.Record.STATE_RESOLUTION_720P) {
			imageVideoSize.setImageDrawable(getResources().getDrawable(
					R.drawable.video_size_hd, null));
			textVideoSize.setText(getResources().getString(
					R.string.icon_hint_720p));
		} else if (resolutionState == Constant.Record.STATE_RESOLUTION_1080P) {
			imageVideoSize.setImageDrawable(getResources().getDrawable(
					R.drawable.video_size_fhd, null));
			textVideoSize.setText(getResources().getString(
					R.string.icon_hint_1080p));
		}

		// 录像按钮
		imageVideoState.setImageDrawable(getResources().getDrawable(
				MyApp.isVideoReording ? R.drawable.video_stop
						: R.drawable.video_start, null));

		// 视频分段
		if (intervalState == Constant.Record.STATE_INTERVAL_1MIN) {
			imageVideoLength.setImageDrawable(getResources().getDrawable(
					R.drawable.video_length_1m, null));
			textVideoLength.setText(getResources().getString(
					R.string.icon_hint_1_minute));
		} else if (intervalState == Constant.Record.STATE_INTERVAL_3MIN) {
			imageVideoLength.setImageDrawable(getResources().getDrawable(
					R.drawable.video_length_3m, null));
			textVideoLength.setText(getResources().getString(
					R.string.icon_hint_3_minutes));
		}

		// 视频加锁
		imageVideoLock.setImageDrawable(getResources().getDrawable(
				MyApp.isVideoLock ? R.drawable.video_lock
						: R.drawable.video_unlock, null));
		textVideoLock.setText(getResources().getString(
				MyApp.isVideoLock ? R.string.icon_hint_lock
						: R.string.icon_hint_unlock));

		// 静音按钮
		boolean videoMute = sharedPreferences.getBoolean("videoMute",
				Constant.Record.muteDefault);
		muteState = videoMute ? Constant.Record.STATE_MUTE
				: Constant.Record.STATE_UNMUTE;
		imageVideoMute.setImageDrawable(getResources().getDrawable(
				videoMute ? R.drawable.video_mute : R.drawable.video_unmute,
				null));
		textVideoMute.setText(getResources()
				.getString(
						videoMute ? R.string.icon_hint_mute
								: R.string.icon_hint_unmute));
	}

	class FrontCallBack implements Callback {

		@Override
		public void surfaceChanged(SurfaceHolder holder, int format, int width,
				int height) {
			// surfaceHolder = holder;
		}

		@Override
		public void surfaceCreated(SurfaceHolder holder) {
			MyLog.v("[Record]surfaceCreated");
			if (camera == null) {
				surfaceHolder = holder;
				setup();
			} else {
				previewCamera();
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
	private boolean openCamera() {
		if (camera != null) {
			closeCamera();
		}
		try {
			MyLog.v("[Record] Camera.open");
			camera = Camera.open(0);
			previewCamera();
			return true;
		} catch (Exception ex) {
			closeCamera();
			MyLog.e("[Record]openCamera:Catch Exception!");
			return false;
		}
	}

	/**
	 * Camera预览：
	 * 
	 * lock > setPreviewDisplay > startPreview > unlock
	 */
	private void previewCamera() {
		try {
			camera.lock();
			if (Constant.Module.useSystemCameraParam) { // 设置系统Camera参数
				Camera.Parameters para = camera.getParameters();
				para.unflatten(Constant.Record.CAMERA_PARAMS);
				camera.setParameters(para);
			}
			camera.setPreviewDisplay(surfaceHolder);
			// camera.setDisplayOrientation(180);
			camera.startPreview();
			camera.unlock();
		} catch (Exception e) {
			MyApp.isCameraPreview = false;
			e.printStackTrace();
		} finally {
			MyApp.isCameraPreview = true;
			// if (shouldBackHome) {
			// shouldBackHome = false;
			// // backToHome(); FIXME
			// }
		}
	}

	/**
	 * 关闭Camera
	 * 
	 * lock > stopPreview > setPreviewDisplay > release > unlock
	 */
	private boolean closeCamera() {
		if (camera == null)
			return true;
		try {
			camera.lock();
			camera.stopPreview();
			camera.setPreviewDisplay(null);
			camera.release();
			camera.unlock();
			camera = null;
			return true;
		} catch (Exception e) {
			camera = null;
			MyLog.e("[MainActivity]closeCamera:Catch Exception:" + e.toString());
			return false;
		}
	}

	private class RestartRecordThread implements Runnable {

		@Override
		public void run() {
			try {
				Thread.sleep(500);
			} catch (Exception e) {
				e.printStackTrace();
			}
			Message messageRestart = new Message();
			messageRestart.what = 1;
			restartRecordHandler.sendMessage(messageRestart);
		}

	}

	final Handler restartRecordHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				if (!MyApp.isVideoReording) {
					if (startRecordTask() == 0) {
						setRecordState(true);
					} else {
						MyLog.e("Start Record Failed");
					}
				}
				break;
			default:
				break;
			}
		}
	};

	/**
	 * 录像线程， 调用此线程地方：
	 * 
	 * 1.首次启动录像{@link AutoThread }
	 * 
	 * 2.ACC上电录像 {@link BackThread}
	 * 
	 * 3.停车侦测，录制一个视频,时长:{@link Constant.Record.parkVideoLength}
	 * 
	 * 4.插卡自动录像
	 */
	private class StartRecordThread implements Runnable {

		@Override
		public void run() {
			StartCheckErrorFileThread();
			int i = 0;
			while (i < 5) {
				if (MyApp.isVideoReording) {
					i = 5;
				} else {
					if (!StorageUtil.isVideoCardExists()) {
						// 如果是休眠状态，且不是停车侦测录像情况，避免线程执行过程中，ACC下电后仍然语音提醒“SD不存在”
						if (!MyApp.isAccOn
								&& !MyApp.shouldStopWhenCrashVideoSave) {
							return;
						}
						i++;
						MyLog.e("[StartRecordThread]No SD:try " + i);
						try {
							Thread.sleep(2000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						if (i == 4) {
							Message messageRetry = new Message();
							messageRetry.what = 2;
							startRecordHandler.sendMessage(messageRetry);
						}
					} else { // 开始录像
						Message messageRecord = new Message();
						messageRecord.what = 1;
						startRecordHandler.sendMessage(messageRecord);
						i = 5;
					}
				}
			}
		}
	}

	final Handler startRecordHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				if (!MyApp.isVideoReording) {
					if (startRecordTask() == 0) {
						setRecordState(true);
					} else {
						MyLog.e("Start Record Failed");
					}
				}
				break;

			case 2:
				noVideoSDHint(); // SDCard2不存在
				break;

			default:
				break;
			}
		}
	};

	/** 视频SD卡不存在提示 */
	private void noVideoSDHint() {
		if (MyApp.isAccOn) {
			String strNoSD = getResources().getString(
					R.string.hint_sd2_not_exist);
			HintUtil.showToast(context, strNoSD);
			speakVoice(strNoSD);
		} else {
			MyLog.v("[noVideoSDHint]No ACC,Do not hint");
		}
	}

	/** 停止录像x5 */
	private void stopRecorder5Times() {
		if (MyApp.isVideoReording) {
			try {
				int tryTime = 0;
				while (stopRecorder() != 0 && tryTime < 5) { // 停止录像
					tryTime++;
				}
				if (MyApp.shouldStopWhenCrashVideoSave) {
					MyApp.shouldStopWhenCrashVideoSave = false;
				}
				setRecordState(false);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * 停止录像
	 * 
	 * @return 是否成功
	 */
	public int stopRecorder() {
		resetRecordTimeText();
		textRecordTime.setVisibility(View.INVISIBLE);
		if (recorder != null) {
			MyLog.d("Record Stop");
			// 停车守卫不播放声音
			if (MyApp.shouldStopWhenCrashVideoSave) {
				MyApp.shouldStopWhenCrashVideoSave = false;
			}
			HintUtil.playAudio(getApplicationContext(),
					com.tchip.tachograph.TachographCallback.FILE_TYPE_VIDEO);
			return recorder.stop();
		}
		return -1;
	}

	/**
	 * 设置视频分段
	 * 
	 * @param seconds
	 * @return
	 */
	public int setInterval(int seconds) {
		return (recorder != null) ? recorder.setVideoSeconds(seconds) : -1;
	}

	/**
	 * 设置视频重叠
	 * 
	 * @param seconds
	 * @return
	 */
	public int setOverlap(int seconds) {
		return (recorder != null) ? recorder.setVideoOverlap(seconds) : -1;
	}

	/** 拍照 */
	public int takePhoto() {
		if (!StorageUtil.isVideoCardExists()) { // 判断SD卡2是否存在，需要耗费一定时间
			noVideoSDHint(); // SDCard不存在
			return -1;
		} else if (recorder != null) {
			setDirectory(Constant.Path.SDCARD_2); // 设置保存路径，否则会保存到内部存储
			HintUtil.playAudio(getApplicationContext(),
					com.tchip.tachograph.TachographCallback.FILE_TYPE_IMAGE);
			return recorder.takePicture();
		}
		return -1;
	}

	/** ACC下电拍照 */
	public void takePhotoWhenAccOff() {
		if (recorder != null) {
			if (!MyApp.isAccOffPhotoTaking) {
				MyApp.isAccOffPhotoTaking = true;
				if (StorageUtil.isVideoCardExists()) {
					setDirectory(Constant.Path.SDCARD_2); // 如果录像卡不存在，则会保存到内部存储
				}
				HintUtil.playAudio(getApplicationContext(),
						com.tchip.tachograph.TachographCallback.FILE_TYPE_IMAGE);
				recorder.takePicture();

				if (sharedPreferences.getBoolean(Constant.MySP.STR_PARKING_ON,
						true) && Constant.Module.hintParkingMonitor) {
					speakVoice(getResources().getString(
							R.string.hint_start_park_monitor_after_90));
				}
			}
			// if (powerManager.isScreenOn()) {
			// sendKeyCode(KeyEvent.KEYCODE_POWER); // 熄屏
			// }
		}
	}

	/** 语音拍照 */
	public void takePhotoWhenVoiceCommand() {
		if (recorder != null) {
			if (StorageUtil.isVideoCardExists()) { // 如果录像卡不存在，则会保存到内部存储
				setDirectory(Constant.Path.SDCARD_2);
			}

			HintUtil.playAudio(getApplicationContext(),
					com.tchip.tachograph.TachographCallback.FILE_TYPE_IMAGE);
			recorder.takePicture();
		}
	}

	/** 设置保存路径 */
	public int setDirectory(String dir) {
		if (recorder != null) {
			return recorder.setDirectory(dir);
		}
		return -1;
	}

	/** 设置录像静音，需要已经初始化recorderFront */
	private int setMute(boolean mute, boolean isFromUser) {
		if (recorder != null) {
			if (isFromUser) {
				speakVoice(getResources().getString(
						mute ? R.string.hint_video_mute_on
								: R.string.hint_video_mute_off));
			}
			editor.putBoolean("videoMute", mute);
			editor.commit();
			muteState = mute ? Constant.Record.STATE_MUTE
					: Constant.Record.STATE_UNMUTE;
			return recorder.setMute(mute);
		}
		return -1;
	}

	/**
	 * 设置分辨率
	 * 
	 * @param state
	 * @return
	 */
	public int setResolution(int state) {
		if (state != resolutionState) {
			resolutionState = state;
			// 释放录像区域
			releaseRecorder();
			closeCamera();
			if (openCamera()) {
				setupRecorder();
			}
		}
		return -1;
	}

	private void setupRecorder() {
		releaseRecorder();
		try {
			recorder = new TachographRecorder();
			recorder.setTachographCallback(new FrontTachographCallback());
			recorder.setCamera(camera);
			// 前缀，后缀
			recorder.setMediaFilenameFixs(TachographCallback.FILE_TYPE_VIDEO,
					"", "");
			recorder.setMediaFilenameFixs(
					TachographCallback.FILE_TYPE_SHARE_VIDEO, "", "");
			recorder.setMediaFilenameFixs(TachographCallback.FILE_TYPE_IMAGE,
					"", "");
			// 路径
			recorder.setMediaFileDirectory(TachographCallback.FILE_TYPE_VIDEO,
					"VideoFront");
			recorder.setMediaFileDirectory(
					TachographCallback.FILE_TYPE_SHARE_VIDEO, "Share");
			recorder.setMediaFileDirectory(TachographCallback.FILE_TYPE_IMAGE,
					"Image");
			recorder.setClientName(this.getPackageName());
			if (resolutionState == Constant.Record.STATE_RESOLUTION_1080P) {
				recorder.setVideoSize(1920, 1080);
				recorder.setVideoFrameRate(Constant.Record.FRAME_RATE);
				recorder.setVideoBiteRate(Constant.Record.BIT_RATE_1080P);
			} else {
				recorder.setVideoSize(1280, 720);
				recorder.setVideoFrameRate(Constant.Record.FRAME_RATE);
				recorder.setVideoBiteRate(Constant.Record.BIT_RATE_720P);
			}
			if (intervalState == Constant.Record.STATE_INTERVAL_1MIN) {
				recorder.setVideoSeconds(1 * 60);
			} else {
				recorder.setVideoSeconds(3 * 60);
			}
			recorder.setVideoOverlap(0);
			recorder.prepare();
		} catch (Exception e) {
			MyLog.e("[MainActivity]setupRecorder: Catch Exception!");
		}

	}

	/** 释放Recorder */
	private void releaseRecorder() {
		try {
			if (recorder != null) {
				recorder.stop();
				recorder.close();
				recorder.release();
				recorder = null;
				MyLog.d("Record Release");
			}
		} catch (Exception e) {
			MyLog.e("[MainActivity]releaseRecorder: Catch Exception!");
		}
	}

	class FrontTachographCallback implements TachographCallback {

		@Override
		public void onError(int error) {
			switch (error) {
			case TachographCallback.ERROR_SAVE_VIDEO_FAIL:
				String strSaveVideoErr = getResources().getString(
						R.string.hint_save_video_error);
				HintUtil.showToast(MainActivity.this, strSaveVideoErr);
				MyLog.e("Record Error : ERROR_SAVE_VIDEO_FAIL");
				// 视频保存失败，原因：存储空间不足，清空文件夹，视频被删掉
				// resetRecordTimeText();
				// MyLog.v("[onError]stopRecorder()");
				// if (stopRecorder() == 0) {
				// setRecordState(false);
				// }
				break;

			case TachographCallback.ERROR_SAVE_IMAGE_FAIL:
				HintUtil.showToast(MainActivity.this,
						getResources()
								.getString(R.string.hint_save_photo_error));
				MyLog.e("Record Error : ERROR_SAVE_IMAGE_FAIL");
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
					Message messageDeleteUnlockVideo = new Message();
					messageDeleteUnlockVideo.what = 1;
					releaseStorageWhenFileSaveHandler
							.sendMessage(messageDeleteUnlockVideo);

					String videoName = path.split("/")[5];
					int videoResolution = (resolutionState == Constant.Record.STATE_RESOLUTION_720P) ? 720
							: 1080;
					int videoLock = 0;

					if (MyApp.isVideoLock) {
						videoLock = 1;
						MyApp.isVideoLock = false; // 还原
						if (MyApp.isVideoReording && MyApp.isVideoLockSecond) {
							MyApp.isVideoLock = true;
							MyApp.isVideoLockSecond = false; // 不录像时修正加锁图标
						}
					}
					setupRecordViews(); // 更新录制按钮状态
					DriveVideo driveVideo = new DriveVideo(videoName,
							videoLock, videoResolution);
					videoDb.addDriveVideo(driveVideo);

					StartCheckErrorFileThread(); // 执行onFileSave时，此file已经不隐藏，下个正在录的为隐藏
					MyLog.v("[onFileSave]videoLock:" + videoLock
							+ ", isVideoLockSecond:" + MyApp.isVideoLockSecond);
				} else { // 图片
					HintUtil.showToast(MainActivity.this, getResources()
							.getString(R.string.hint_photo_save));

					MyApp.writeImageExifPath = path;
					new Thread(new WriteImageExifThread()).start();

					// 通知语音
					Intent intentImageSave = new Intent(
							Constant.Broadcast.ACTION_IMAGE_SAVE);
					intentImageSave.putExtra("path", path);
					sendBroadcast(intentImageSave);
				}

				sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
						Uri.parse("file://" + path))); // 更新Media Database
				MyLog.d("[onFileSave]Type=" + type + ",Save path:" + path);
			} catch (Exception e) {
				e.printStackTrace();
				MyLog.e("[Main]onFileSave catch Exception:" + e.toString());
			}
		}

		@Override
		public void onFileStart(int type, String path) {
			if (type == 1) {
				MyApp.nowRecordVideoName = path.split("/")[5];
			}
			MyLog.v("[onFileStart]Path:" + path);
		}

	}

	class ReleaseStorageWhenStartRecordHandler extends Handler {

		public ReleaseStorageWhenStartRecordHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				this.removeMessages(1);
				final boolean isDeleteSuccess = StorageUtil
						.releaseRecordStorage(context);
				mMainHandler.post(new Runnable() {

					@Override
					public void run() {
						if (isDeleteSuccess) {
							HintUtil.playAudio(
									getApplicationContext(),
									com.tchip.tachograph.TachographCallback.FILE_TYPE_VIDEO);
							if (MyApp.isCameraPreview && recorder.start() == 0) {
								setRecordState(true);
							}
						}

					}
				});
				this.removeMessages(1);
				break;
			}
		}

	}

	/**
	 * 开启录像
	 * 
	 * @return 0:成功 -1:失败
	 */
	public int startRecordTask() { // FIXME
		if (!MyApp.isVideoReording && MyApp.isCameraPreview && recorder != null) {
			MyLog.d("Record Start");
			setDirectory(Constant.Path.SDCARD_2); // 设置保存路径
			// 设置录像静音
			if (sharedPreferences.getBoolean("videoMute",
					Constant.Record.muteDefault)) {
				setMute(true, false);
			} else {
				setMute(false, false);
			}
			resetRecordTimeText();

			Message messageReleaseWhenStartRecord = new Message();
			messageReleaseWhenStartRecord.what = 1;
			releaseStorageWhenStartRecordHandler
					.sendMessage(messageReleaseWhenStartRecord);
			if (!StorageUtil.isStorageLess()) {
				return 0;
			} else {
				return -1;
			}
		}
		return -1;
	}

	class ReleaseStorageWhenFileSaveHandler extends Handler {

		public ReleaseStorageWhenFileSaveHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				this.removeMessages(1);
				final boolean isDeleteSuccess = StorageUtil
						.releaseRecordStorage(context);
				mMainHandler.post(new Runnable() {

					@Override
					public void run() {
						if (isDeleteSuccess) {
						}

					}
				});
				this.removeMessages(1);
				break;
			}
		}

	}

	private class WriteImageExifThread implements Runnable {

		@Override
		public void run() {
			StorageUtil.writeImageExif();
		}

	}

	/** 检查并删除异常视频文件：SD存在但数据库中不存在的文件 */
	private void StartCheckErrorFileThread() {
		MyLog.v("[CheckErrorFile]isVideoChecking:" + isVideoChecking);
		if (!isVideoChecking) {
			new Thread(new CheckVideoThread()).start();
		}
	}

	/** 当前是否正在校验错误视频 */
	private boolean isVideoChecking = false;

	private class CheckVideoThread implements Runnable {

		@Override
		public void run() {
			MyLog.v("[CheckVideoThread]START:" + DateUtil.getTimeStr("mm:ss"));
			isVideoChecking = true;
			File file = new File(Constant.Path.RECORD_FRONT);
			StorageUtil.RecursionCheckFile(MainActivity.this, file);
			MyLog.v("[CheckVideoThread]END:" + DateUtil.getTimeStr("mm:ss"));
			isVideoChecking = false;
		}

	}

	public void setup() {
		releaseRecorder();
		closeCamera();
		if (openCamera()) {
			setupRecorder();
		}
	}

	private void sendKeyCode(final int keyCode) {
		new Thread() {
			public void run() {
				try {
					Instrumentation inst = new Instrumentation();
					inst.sendKeyDownUpSync(keyCode);
				} catch (Exception e) {
					MyLog.e("Exception when sendPointerSync:" + e.toString());
				}
			}
		}.start();
	}
}
