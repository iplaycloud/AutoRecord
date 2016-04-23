package com.tchip.autorecord.ui;

import java.io.File;

import com.tchip.autorecord.Constant;
import com.tchip.autorecord.MyApp;
import com.tchip.autorecord.R;
import com.tchip.autorecord.Typefaces;
import com.tchip.autorecord.MyApp.CameraState;
import com.tchip.autorecord.db.DriveVideo;
import com.tchip.autorecord.db.DriveVideoDbHelper;
import com.tchip.autorecord.service.SensorWatchService;
import com.tchip.autorecord.service.SleepOnOffService;
import com.tchip.autorecord.util.ClickUtil;
import com.tchip.autorecord.util.DateUtil;
import com.tchip.autorecord.util.HintUtil;
import com.tchip.autorecord.util.MyLog;
import com.tchip.autorecord.util.SettingUtil;
import com.tchip.autorecord.util.StorageUtil;
import com.tchip.autorecord.view.AudioRecordDialog;
import com.tchip.tachograph.TachographCallback;
import com.tchip.tachograph.TachographRecorder;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.SurfaceHolder.Callback;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements TachographCallback,
		Callback {

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
	/** 前后切换 */
	private ImageButton imageCameraSwitch;
	/** 视频尺寸 */
	private ImageButton imageVideoSize;
	/** 视频分段 */
	private ImageButton imageVideoLength;
	/** 静音按钮 */
	private ImageButton imageVideoMute;
	/** 拍照按钮 */
	private ImageButton imagePhotoTake;

	private TextView textRecordTime;

	private Camera camera;
	private SurfaceView surfaceCamera;
	private SurfaceHolder surfaceHolder;
	private TachographRecorder carRecorder;

	private int resolutionState, recordState, intervalState, overlapState,
			muteState;

	private AudioRecordDialog audioRecordDialog;

	/** SIM卡状态 */
	private PowerManager powerManager;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		mMainHandler = new Handler(this.getMainLooper());
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_main);
		context = getApplicationContext();

		sharedPreferences = getSharedPreferences(Constant.MySP.NAME,
				Context.MODE_PRIVATE);
		editor = sharedPreferences.edit();
		videoDb = new DriveVideoDbHelper(context); // 视频数据库
		audioRecordDialog = new AudioRecordDialog(MainActivity.this); // 提示框

		initialLayout();
		SettingUtil.initialNodeState(MainActivity.this);
		StorageUtil.createRecordDirectory();
		setupRecordDefaults();
		setupRecordViews();

		SettingUtil.setGpsState(MainActivity.this, true); // 打开GPS
		// ACC上下电侦测服务
		Intent intentSleepOnOff = new Intent(MainActivity.this,
				SleepOnOffService.class);
		startService(intentSleepOnOff);

		// 首次启动是否需要自动录像
		new Thread(new AutoThread()).start(); // 序列任务线程
		if (1 == SettingUtil.getAccStatus()) {
			MyApp.isAccOn = true; // 同步ACC状态
			// SettingUtil.setAirplaneMode(MainActivity.this, false); // 关闭飞行模式

		} else {
			MyApp.isAccOn = false; // 同步ACC状态
			MyApp.isSleeping = true; // ACC未连接,进入休眠
			MyLog.v("[Main]ACC Check:OFF, Send Broadcast:com.tchip.SLEEP_ON.");

			// sendBroadcast(new Intent(Constant.Broadcast.SLEEP_ON)); //
			// 通知其他应用进入休眠
			// SettingUtil.setAirplaneMode(MainActivity.this, true); // 打开飞行模式
			// SettingUtil.setGpsState(MainActivity.this, false); // 关闭GPS
			// SettingUtil.setEDogEnable(false); // 关闭电子狗电源
		}
		new Thread(new BackThread()).start(); // 后台线程
	}

	@Override
	protected void onPause() {
		MyLog.v("[Main]onPause");
		MyApp.isMainForeground = false;
		MyLog.v("[onPause]MyApplication.isVideoReording:"
				+ MyApp.isVideoReording);

		// ACC在的时候不频繁释放录像区域：ACC在的时候Suspend？
		if (!MyApp.isAccOn && !MyApp.isVideoReording) {
			releaseCameraZone();
			MyApp.isVideoLockSecond = false;
		}
		super.onPause();
	}

	@Override
	protected void onResume() {
		MyLog.v("[Main]onResume");
		MyApp.isMainForeground = true;
		try {

			if (!MyApp.isFirstLaunch) {
				if (!MyApp.isVideoReording || MyApp.shouldResetRecordWhenResume) {
					MyApp.shouldResetRecordWhenResume = false;
					if (camera == null) { // 重置预览区域
						// surfaceHolder = holder;
						setup();
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
			} else {
				MyApp.isFirstLaunch = false;
			}
			refreshRecordButton(); // 更新录像界面按钮状态
			setupRecordViews();
		} catch (Exception e) {
			e.printStackTrace();
			MyLog.e("[Main]onResume catch Exception:" + e.toString());
		}
		super.onResume();
	}

	@Override
	protected void onStop() {
		MyLog.v("[Main]onStop");
		super.onStop();
	}

	@Override
	protected void onDestroy() {
		MyLog.v("[Main]onDestroy");
		release(); // 释放录像区域
		videoDb.close();
		super.onDestroy();
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
				initialService();
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
				if (!MyApp.isSleeping) {
					if (MyApp.shouldWakeRecord) {
						// 序列任务线程
						new Thread(new AutoThread()).start();
						MyApp.shouldWakeRecord = false;
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
						if (!MyApp.isMainForeground) { // 发送Home键，回到主界面
							sendKeyCode(KeyEvent.KEYCODE_HOME);
						}
						new Thread(new RecordWhenCrashThread()).start();
					}
				}
				if (MyApp.shouldTakePhotoWhenAccOff) { // ACC下电拍照
					MyApp.shouldTakePhotoWhenAccOff = false;
					MyApp.shouldSendPathToDSA = true;
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
					if (recordState == Constant.Record.STATE_RECORD_STOPPED) {
						if (!MyApp.isMainForeground) {
							sendKeyCode(KeyEvent.KEYCODE_HOME); // 回到主界面
						}
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
					if (recordState == Constant.Record.STATE_RECORD_STOPPED) {
						if (!MyApp.isMainForeground) { // 发送Home键，回到主界面
							sendKeyCode(KeyEvent.KEYCODE_HOME);
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
				// startRecord();
				if (!MyApp.isVideoReording) {
					startRecordTask();

				}
				break;

			default:
				break;
			}
		}

	};

	/**
	 * 初始化服务:
	 * 
	 * 1.碰撞侦测服务
	 * 
	 * 2.轨迹记录服务
	 * 
	 * 3.天气播报服务
	 */
	private void initialService() {
		// 碰撞侦测服务
		Intent intentSensor = new Intent(this, SensorWatchService.class);
		startService(intentSensor);
		// 天气播报(整点报时)
		Intent intentWeather = new Intent();
		intentWeather.setClassName("com.tchip.weather",
				"com.tchip.weather.service.TimeTickService");
		startService(intentWeather);
	}

	private void initialCameraSurface() {
		surfaceCamera = (SurfaceView) findViewById(R.id.surfaceCamera);
		surfaceCamera.setOnClickListener(new MyOnClickListener());
		surfaceCamera.getHolder().addCallback(this);
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

		// 前后切换图标
		imageCameraSwitch = (ImageButton) findViewById(R.id.imageCameraSwitch);
		imageCameraSwitch.setOnClickListener(myOnClickListener);

		// 拍照
		imagePhotoTake = (ImageButton) findViewById(R.id.imagePhotoTake);
		imagePhotoTake.setOnClickListener(myOnClickListener);

		// 视频尺寸
		imageVideoSize = (ImageButton) findViewById(R.id.imageVideoSize);
		imageVideoSize.setOnClickListener(myOnClickListener);

		// 视频分段长度
		imageVideoLength = (ImageButton) findViewById(R.id.imageVideoLength);
		imageVideoLength.setOnClickListener(myOnClickListener);

		// 静音
		imageVideoMute = (ImageButton) findViewById(R.id.imageVideoMute);
		imageVideoMute.setOnClickListener(myOnClickListener);

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
		if (isVideoRecord) {
			recordState = Constant.Record.STATE_RECORD_STARTED;
			MyApp.isVideoReording = true;
			textRecordTime.setVisibility(View.VISIBLE);
			startUpdateRecordTimeThread();
			setupRecordViews();
		} else {
			recordState = Constant.Record.STATE_RECORD_STOPPED;
			MyApp.isVideoReording = false;
			setupRecordViews();
			releaseCameraZone();
			MyApp.isUpdateTimeThreadRun = false;
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
				} else if (!MyApp.isPowerConnect) { // 电源断开
					MyLog.e("Stop Record:Power is unconnected");
					Message messagePowerUnconnect = new Message();
					messagePowerUnconnect.what = 3;
					updateRecordTimeHandler.sendMessage(messagePowerUnconnect);
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
			case 1: // 处理停车守卫录像
				if (!ClickUtil.isPlusRecordTimeTooQuick(900)) {
					secondCount++;
				}
				if (MyApp.shouldStopWhenCrashVideoSave && MyApp.isVideoReording) {
					if (secondCount == Constant.Record.parkVideoLength) {
						String videoTimeStr = sharedPreferences.getString(
								"videoTime", "3");
						intervalState = "1".equals(videoTimeStr) ? Constant.Record.STATE_INTERVAL_1MIN
								: Constant.Record.STATE_INTERVAL_3MIN;

						MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 1");
						if (stopRecorder() == 0) { // 停止录像
							setRecordState(false);
							setInterval(("1".equals(videoTimeStr)) ? 1 * 60
									: 3 * 60); // 重设视频分段
						} else {
							MyLog.e("stopRecorder Error 1");
						}

						// 熄灭屏幕,判断当前屏幕是否关闭
						PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
						boolean isScreenOn = powerManager.isScreenOn();
						if (isScreenOn) {
							sendBroadcast(new Intent("com.tchip.SLEEP_ON"));
						}
					}
				}

				switch (intervalState) { // 重置时间
				case Constant.Record.STATE_INTERVAL_3MIN:
					if (secondCount >= 180) {
						secondCount = 0;
					}
					break;

				case Constant.Record.STATE_INTERVAL_1MIN:
					if (secondCount >= 60) {
						secondCount = 0;
					}
					break;

				default:
					break;
				}
				textRecordTime.setText(DateUtil
						.getFormatTimeBySecond(secondCount));

				break;

			case 2: // SD卡异常移除：停止录像
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 2");
				if (stopRecorder() == 0) {
					setRecordState(false);
				} else {
					MyLog.e("stopRecorder Error 2");
				}
				String strVideoCardEject = getResources().getString(
						R.string.hint_sd_remove_badly);
				HintUtil.showToast(MainActivity.this, strVideoCardEject);

				MyLog.e("CardEjectReceiver:Video SD Removed");
				HintUtil.speakVoice(MainActivity.this, strVideoCardEject);
				audioRecordDialog.showErrorDialog(strVideoCardEject);
				new Thread(new dismissDialogThread()).start();
				break;

			case 3: // 电源断开，停止录像
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 3");
				if (stopRecorder() == 0) {
					setRecordState(false);
				} else {
					MyLog.e("stopRecorder Error 3");
				}
				String strPowerUnconnect = getResources().getString(
						R.string.hint_stop_record_power_unconnect);
				HintUtil.showToast(MainActivity.this, strPowerUnconnect);
				HintUtil.speakVoice(MainActivity.this, strPowerUnconnect);

				MyLog.e("Record Stop:power unconnect.");
				audioRecordDialog.showErrorDialog(strPowerUnconnect);
				new Thread(new dismissDialogThread()).start();
				break;

			case 4:
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
				break;

			case 5: // 进入休眠，停止录像
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 5");
				if (stopRecorder() == 0) {
					setRecordState(false);
				} else {
					MyLog.e("stopRecorder Error 5");
				}
				// 如果此时屏幕为点亮状态，则不回收
				boolean isScreenOn = powerManager.isScreenOn();
				if (!isScreenOn) {
					releaseCameraZone();
				}
				MyApp.shouldResetRecordWhenResume = true;
				break;

			case 6: // 语音命令：停止录像
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 6");
				if (stopRecorder() == 0) {
					setRecordState(false);
				} else {
					MyLog.e("stopRecorder Error 6");
				}
				break;

			case 7:
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 7");
				if (stopRecorder() == 0) {
					setRecordState(false);
				} else {
					MyLog.e("stopRecorder Error 7");
				}
				String strVideoCardFormat = getResources().getString(
						R.string.hint_sd2_format);
				HintUtil.showToast(MainActivity.this, strVideoCardFormat);

				MyLog.e("CardEjectReceiver:Video SD Removed");
				HintUtil.speakVoice(MainActivity.this, strVideoCardFormat);
				audioRecordDialog.showErrorDialog(strVideoCardFormat);
				new Thread(new dismissDialogThread()).start();
				break;

			case 8: // 程序异常，停止录像
				MyLog.v("[UpdateRecordTimeHandler]stopRecorder() 8");
				if (stopRecorder() == 0) {
					setRecordState(false);
				} else {
					MyLog.e("stopRecorder Error 8");
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
			case R.id.surfaceCamera:
				break;

			case R.id.imageVideoState:
				if (!ClickUtil.isQuickClick(2000)) {
					if (recordState == Constant.Record.STATE_RECORD_STOPPED) {
						if (StorageUtil.isVideoCardExists()) {
							HintUtil.speakVoice(
									MainActivity.this,
									getResources().getString(
											R.string.hint_record_start));
							startRecord();
						} else {
							noVideoSDHint();
						}
					} else if (recordState == Constant.Record.STATE_RECORD_STARTED) {
						HintUtil.speakVoice(MainActivity.this, getResources()
								.getString(R.string.hint_record_stop));
						MyLog.v("[onClick]stopRecorder()");
						stopRecord();
					}
				}
				break;

			case R.id.imageVideoLock:
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
				if (!ClickUtil.isQuickClick(1500)) {
					// 切换分辨率录像停止，需要重置时间
					MyApp.shouldVideoRecordWhenChangeSize = MyApp.isVideoReording;
					MyApp.isVideoReording = false;
					resetRecordTimeText();
					textRecordTime.setVisibility(View.INVISIBLE);
					if (resolutionState == Constant.Record.STATE_RESOLUTION_1080P) {
						setResolution(Constant.Record.STATE_RESOLUTION_720P);
						editor.putString("videoSize", "720");
						recordState = Constant.Record.STATE_RECORD_STOPPED;
						HintUtil.speakVoice(MainActivity.this, getResources()
								.getString(R.string.hint_video_size_720));
					} else if (resolutionState == Constant.Record.STATE_RESOLUTION_720P) {
						setResolution(Constant.Record.STATE_RESOLUTION_1080P);
						editor.putString("videoSize", "1080");
						recordState = Constant.Record.STATE_RECORD_STOPPED;
						HintUtil.speakVoice(MainActivity.this, getResources()
								.getString(R.string.hint_video_size_1080));
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
				if (!ClickUtil.isQuickClick(1000)) {
					if (intervalState == Constant.Record.STATE_INTERVAL_3MIN) {
						if (setInterval(1 * 60) == 0) {
							intervalState = Constant.Record.STATE_INTERVAL_1MIN;
							editor.putString("videoTime", "1");
							HintUtil.speakVoice(
									MainActivity.this,
									getResources().getString(
											R.string.hint_video_time_1));
						}
					} else if (intervalState == Constant.Record.STATE_INTERVAL_1MIN) {
						if (setInterval(3 * 60) == 0) {
							intervalState = Constant.Record.STATE_INTERVAL_3MIN;
							editor.putString("videoTime", "3");
							HintUtil.speakVoice(
									MainActivity.this,
									getResources().getString(
											R.string.hint_video_time_3));
						}
					}
					editor.commit();
					setupRecordViews();
				}
				break;

			case R.id.imageVideoMute:
				if (!ClickUtil.isQuickClick(1500)) {
					// 切换录音/静音状态停止录像，需要重置时间
					MyApp.shouldVideoRecordWhenChangeMute = MyApp.isVideoReording;
					if (MyApp.isVideoReording) {
						resetRecordTimeText();
						textRecordTime.setVisibility(View.INVISIBLE);
						MyApp.isVideoReording = false;
						stopRecord();
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

			case R.id.imagePhotoTake:
				if (!ClickUtil.isQuickClick(1500)) {
					takePhoto();
				}
				break;

			case R.id.imageCameraSwitch:
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
			if (recordState == Constant.Record.STATE_RECORD_STOPPED) {
				if (MyApp.isSleeping) {
					HintUtil.speakVoice(MainActivity.this, getResources()
							.getString(R.string.hint_stop_record_sleeping));
					HintUtil.showToast(MainActivity.this, getResources()
							.getString(R.string.hint_stop_record_sleeping));
				} else {
					if (!powerManager.isScreenOn()) { // 点亮屏幕
						SettingUtil.lightScreen(getApplicationContext());
					}
					if (!MyApp.isMainForeground) { // 发送Home键，回到主界面
						sendKeyCode(KeyEvent.KEYCODE_HOME);
					}
					new Thread(new StartRecordThread()).start(); // 开始录像
				}
			} else {
				MyLog.v("[startRecord]Already record yet");
			}
			setupRecordViews();
			MyLog.v("MyApplication.isVideoReording:" + MyApp.isVideoReording);
		} catch (Exception e) {
			MyLog.e("[MainActivity]startOrStopRecord catch exception: "
					+ e.toString());
		}
	}

	/** 停止录像 */
	private void stopRecord() {
		try {
			if (recordState == Constant.Record.STATE_RECORD_STARTED) {
				if (stopRecorder() == 0) {
					setRecordState(false);
					if (MyApp.shouldStopWhenCrashVideoSave) {
						MyApp.shouldStopWhenCrashVideoSave = false;
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/** 加锁或解锁视频 */
	private void lockOrUnlockVideo() {
		if (!MyApp.isVideoLock) {
			MyApp.isVideoLock = true;
			HintUtil.speakVoice(MainActivity.this,
					getResources().getString(R.string.hint_video_lock));
		} else {
			MyApp.isVideoLock = false;
			MyApp.isVideoLockSecond = false;
			HintUtil.speakVoice(MainActivity.this,
					getResources().getString(R.string.hint_video_unlock));
		}
		setupRecordViews();
	}

	/**
	 * 如果录像界面不在前台且未在录像，则释放Camera，防止出现熄屏时未在录像仍在预览功耗高的问题
	 * 
	 * 调用地方：在成功执行{@link #stopRecorder}之后
	 */
	private void releaseCameraZone() {
		if (!MyApp.isAccOn && !MyApp.isMainForeground) {
			release();
			// surfaceHolder = null;
			if (camera != null) {
				camera.stopPreview();
			}
			MyApp.shouldResetRecordWhenResume = true;
			MyLog.v("[Record]releaseCameraZone");
		}
		MyApp.cameraState = CameraState.NULL;
	}

	// *********** Record ***********
	/** 设置录制初始值 */
	private void setupRecordDefaults() {
		refreshRecordButton();

		recordState = Constant.Record.STATE_RECORD_STOPPED;
		MyApp.isVideoReording = false;

		overlapState = Constant.Record.STATE_OVERLAP_ZERO;

		// 录音,静音;默认录音
		boolean videoMute = sharedPreferences.getBoolean("videoMute",
				Constant.Record.muteDefault);
		muteState = videoMute ? Constant.Record.STATE_MUTE
				: Constant.Record.STATE_UNMUTE;
	}

	private void refreshRecordButton() {
		// 视频尺寸：公版默认720P，善领默认1080P
		String videoSizeStr = sharedPreferences.getString("videoSize",
				Constant.Module.isPublic ? "720" : "1080");
		resolutionState = "1080".equals(videoSizeStr) ? Constant.Record.STATE_RESOLUTION_1080P
				: Constant.Record.STATE_RESOLUTION_720P;

		String videoTimeStr = sharedPreferences.getString("videoTime", "3"); // 视频分段
		intervalState = "1".equals(videoTimeStr) ? Constant.Record.STATE_INTERVAL_1MIN
				: Constant.Record.STATE_INTERVAL_3MIN;
	}

	/** 绘制录像按钮 */
	private void setupRecordViews() {
		HintUtil.setRecordHintFloatWindowVisible(MainActivity.this,
				MyApp.isVideoReording);

		// 视频分辨率
		if (resolutionState == Constant.Record.STATE_RESOLUTION_720P) {
			imageVideoSize.setImageDrawable(getResources().getDrawable(
					R.drawable.video_size_hd, null));
		} else if (resolutionState == Constant.Record.STATE_RESOLUTION_1080P) {
			imageVideoSize.setImageDrawable(getResources().getDrawable(
					R.drawable.video_size_fhd, null));
		}

		// 录像按钮
		imageVideoState.setImageDrawable(getResources().getDrawable(
				MyApp.isVideoReording ? R.drawable.video_stop
						: R.drawable.video_start, null));

		// 视频分段
		if (intervalState == Constant.Record.STATE_INTERVAL_1MIN) {
			imageVideoLength.setImageDrawable(getResources().getDrawable(
					R.drawable.video_length_1m, null));
		} else if (intervalState == Constant.Record.STATE_INTERVAL_3MIN) {
			imageVideoLength.setImageDrawable(getResources().getDrawable(
					R.drawable.video_length_3m, null));
		}

		// 视频加锁
		imageVideoLock.setImageDrawable(getResources().getDrawable(
				MyApp.isVideoLock ? R.drawable.video_lock
						: R.drawable.video_unlock, null));

		// 静音按钮
		boolean videoMute = sharedPreferences.getBoolean("videoMute",
				Constant.Record.muteDefault);
		muteState = videoMute ? Constant.Record.STATE_MUTE
				: Constant.Record.STATE_UNMUTE;
		imageVideoMute.setImageDrawable(getResources().getDrawable(
				videoMute ? R.drawable.video_mute : R.drawable.video_unmute,
				null));
	}

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

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		MyLog.v("[Record]surfaceDestroyed");
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
			camera.lock();

			// Camera.Parameters para = camera.getParameters();
			// para.unflatten(Constant.Record.CAMERA_PARAMS);
			// camera.setParameters(para); // 设置系统Camera参数
			camera.setPreviewDisplay(surfaceHolder);
			camera.startPreview();
			camera.unlock();
			return true;
		} catch (Exception ex) {
			closeCamera();
			MyLog.e("[Record]openCamera:Catch Exception!");
			return false;
		}
	}

	/**
	 * 关闭Camera
	 * 
	 * @return
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
		} catch (Exception ex) {
			camera = null;
			MyLog.e("[MainActivity]closeCamera:Catch Exception!");
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
						if (MyApp.isSleeping
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
			audioRecordDialog.showErrorDialog(strNoSD);
			new Thread(new dismissDialogThread()).start();
			HintUtil.speakVoice(MainActivity.this, strNoSD);
		} else {
			MyLog.v("[noVideoSDHint]No ACC,Do not hint");
		}
	}

	public class dismissDialogThread implements Runnable {
		@Override
		public void run() {
			try {
				Thread.sleep(1500);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			Message messageEject = new Message();
			messageEject.what = 1;
			dismissDialogHandler.sendMessage(messageEject);
		}
	}

	final Handler dismissDialogHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				audioRecordDialog.dismissDialog();
				break;

			default:
				break;
			}
		}
	};

	/**
	 * 停止录像
	 * 
	 * @return 是否成功
	 */
	public int stopRecorder() {
		resetRecordTimeText();
		textRecordTime.setVisibility(View.INVISIBLE);
		if (carRecorder != null) {
			MyLog.d("Record Stop");
			// 停车守卫不播放声音
			if (MyApp.shouldStopWhenCrashVideoSave) {
				MyApp.shouldStopWhenCrashVideoSave = false;
			}
			HintUtil.playAudio(getApplicationContext(), FILE_TYPE_VIDEO);
			return carRecorder.stop();
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
		return (carRecorder != null) ? carRecorder.setVideoSeconds(seconds)
				: -1;
	}

	/**
	 * 设置视频重叠
	 * 
	 * @param seconds
	 * @return
	 */
	public int setOverlap(int seconds) {
		return (carRecorder != null) ? carRecorder.setVideoOverlap(seconds)
				: -1;
	}

	/** 拍照 */
	public int takePhoto() {
		if (!StorageUtil.isVideoCardExists()) { // 判断SD卡2是否存在，需要耗费一定时间
			noVideoSDHint(); // SDCard不存在
			return -1;
		} else if (carRecorder != null) {
			setDirectory(Constant.Path.SDCARD_2); // 设置保存路径，否则会保存到内部存储
			HintUtil.playAudio(getApplicationContext(), FILE_TYPE_IMAGE);
			return carRecorder.takePicture();
		}
		return -1;
	}

	/** ACC下电拍照 */
	public void takePhotoWhenAccOff() {
		if (carRecorder != null) {
			if (!MyApp.isAccOffPhotoTaking) {
				MyApp.isAccOffPhotoTaking = true;
				if (StorageUtil.isVideoCardExists()) {
					setDirectory(Constant.Path.SDCARD_2); // 如果录像卡不存在，则会保存到内部存储
				}
				HintUtil.playAudio(getApplicationContext(), FILE_TYPE_IMAGE);
				carRecorder.takePicture();

				if (sharedPreferences.getBoolean(Constant.MySP.STR_PARKING_ON,
						true) && Constant.Module.hintParkingMonitor) {
					HintUtil.speakVoice(
							getApplicationContext(),
							getResources().getString(
									R.string.hint_start_park_monitor_after_90));
				}
			}
			if (powerManager.isScreenOn()) {
				sendKeyCode(KeyEvent.KEYCODE_POWER); // 熄屏
			}
		}
	}

	/** 语音拍照 */
	public void takePhotoWhenVoiceCommand() {
		if (carRecorder != null) {
			if (StorageUtil.isVideoCardExists()) { // 如果录像卡不存在，则会保存到内部存储
				setDirectory(Constant.Path.SDCARD_2);
			}

			HintUtil.playAudio(getApplicationContext(), FILE_TYPE_IMAGE);
			carRecorder.takePicture();
		}
	}

	/** 设置保存路径 */
	public int setDirectory(String dir) {
		if (carRecorder != null) {
			return carRecorder.setDirectory(dir);
		}
		return -1;
	}

	/** 设置录像静音，需要已经初始化carRecorder */
	private int setMute(boolean mute, boolean isFromUser) {
		if (carRecorder != null) {
			if (isFromUser) {
				HintUtil.speakVoice(
						MainActivity.this,
						getResources().getString(
								mute ? R.string.hint_video_mute_on
										: R.string.hint_video_mute_off));
				editor.putBoolean("videoMute", mute);
				editor.commit();
			}
			muteState = mute ? Constant.Record.STATE_MUTE
					: Constant.Record.STATE_UNMUTE;
			return carRecorder.setMute(mute);
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
			release();
			if (openCamera()) {
				setupRecorder();
			}
		}
		return -1;
	}

	private void setupRecorder() {
		releaseRecorder();
		try {
			carRecorder = new TachographRecorder();
			carRecorder.setTachographCallback(this);
			carRecorder.setCamera(camera);
			carRecorder.setMediaFilenameFixs(
					TachographCallback.FILE_TYPE_VIDEO, "aa_", "_bb");
			carRecorder.setMediaFilenameFixs(
					TachographCallback.FILE_TYPE_SHARE_VIDEO, "cc_", "_ee");
			carRecorder.setMediaFilenameFixs(
					TachographCallback.FILE_TYPE_IMAGE, "ff_", "_gg");
			carRecorder.setMediaFileDirectory(
					TachographCallback.FILE_TYPE_VIDEO, "vvv");
			carRecorder.setMediaFileDirectory(
					TachographCallback.FILE_TYPE_SHARE_VIDEO, "sss");
			carRecorder.setMediaFileDirectory(
					TachographCallback.FILE_TYPE_IMAGE, "iii");
			carRecorder.setClientName(this.getPackageName());
			if (resolutionState == Constant.Record.STATE_RESOLUTION_1080P) {
				carRecorder.setVideoSize(1920, 1080); // 16倍数
				carRecorder.setVideoFrameRate(Constant.Record.FRAME_RATE);
				carRecorder.setVideoBiteRate(Constant.Record.BIT_RATE_1080P);
			} else {
				carRecorder.setVideoSize(1280, 720);
				carRecorder.setVideoFrameRate(Constant.Record.FRAME_RATE);
				carRecorder.setVideoBiteRate(Constant.Record.BIT_RATE_720P);
			}
			if (intervalState == Constant.Record.STATE_INTERVAL_1MIN) {
				carRecorder.setVideoSeconds(1 * 60);
			} else {
				carRecorder.setVideoSeconds(3 * 60);
			}
			if (overlapState == Constant.Record.STATE_OVERLAP_FIVE) {
				carRecorder.setVideoOverlap(5);
			} else {
				carRecorder.setVideoOverlap(0);
			}
			carRecorder.prepare();
		} catch (Exception e) {
			MyLog.e("[MainActivity]setupRecorder: Catch Exception!");
		}

		MyApp.cameraState = CameraState.OKAY;
	}

	/** 释放Recorder */
	private void releaseRecorder() {
		try {
			if (carRecorder != null) {
				carRecorder.stop();
				carRecorder.close();
				carRecorder.release();
				carRecorder = null;
				MyLog.d("Record Release");
			}
		} catch (Exception e) {
			MyLog.e("[MainActivity]releaseRecorder: Catch Exception!");
		}
	}

	@Override
	public void onError(int error) {
		switch (error) {
		case TachographCallback.ERROR_SAVE_VIDEO_FAIL:
			String strSaveVideoErr = getResources().getString(
					R.string.hint_save_video_error);
			HintUtil.showToast(MainActivity.this, strSaveVideoErr);
			MyLog.e("Record Error : ERROR_SAVE_VIDEO_FAIL");
			// 视频保存失败，原因：存储空间不足，清空文件夹，视频被删掉
			resetRecordTimeText();
			MyLog.v("[onError]stopRecorder()");
			if (stopRecorder() == 0) {
				setRecordState(false);
			}
			break;

		case TachographCallback.ERROR_SAVE_IMAGE_FAIL:
			HintUtil.showToast(MainActivity.this,
					getResources().getString(R.string.hint_save_photo_error));
			MyLog.e("Record Error : ERROR_SAVE_IMAGE_FAIL");

			if (MyApp.shouldSendPathToDSA) {
				MyApp.shouldSendPathToDSA = false;
				MyApp.isAccOffPhotoTaking = false;
			}
			break;

		case TachographCallback.ERROR_RECORDER_CLOSED:
			MyLog.e("Record Error : ERROR_RECORDER_CLOSED");
			break;

		default:
			break;
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
							HintUtil.playAudio(getApplicationContext(),
									FILE_TYPE_VIDEO);
							if (carRecorder.start() == 0) {
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
	public int startRecordTask() {
		if (carRecorder != null) {
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

	@Override
	public void onFileStart(int type, String path) {
		if (type == 1) {
			MyApp.nowRecordVideoName = path.split("/")[5];
		}
		Toast.makeText(this, type + " start " + path, Toast.LENGTH_SHORT)
				.show();
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
				boolean isDeleteSuccess = StorageUtil
						.releaseRecordStorage(context);
				mMainHandler.post(new Runnable() {

					@Override
					public void run() {
						// main thread

					}
				});
				this.removeMessages(1);
				break;
			}
		}

	}

	/**
	 * 文件保存回调，注意：存在延时，不能用作重置录像跑秒时间
	 * 
	 * @param type
	 *            0-图片 1-视频
	 * 
	 * @param path
	 *            视频：/mnt/sdcard/tachograph/2015-07-01/2015-07-01_105536.mp4
	 *            图片:/mnt/sdcard/tachograph/camera_shot/2015-07-01_105536.jpg
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
				DriveVideo driveVideo = new DriveVideo(videoName, videoLock,
						videoResolution);
				videoDb.addDriveVideo(driveVideo);

				StartCheckErrorFileThread(); // 执行onFileSave时，此file已经不隐藏，下个正在录的为隐藏
				MyLog.v("[onFileSave]videoLock:" + videoLock
						+ ", isVideoLockSecond:" + MyApp.isVideoLockSecond);
			} else { // 图片
				HintUtil.showToast(MainActivity.this,
						getResources().getString(R.string.hint_photo_save));

				MyApp.writeImageExifPath = path;
				new Thread(new WriteImageExifThread()).start();

				if (MyApp.shouldSendPathToDSA) {
					MyApp.shouldSendPathToDSA = false;
					String[] picPaths = new String[2]; // 第一张保存前置的图片路径
														// ；第二张保存后置的，如无可以为空
					picPaths[0] = path;
					picPaths[1] = "";
					Intent intent = new Intent(Constant.Broadcast.SEND_PIC_PATH);
					intent.putExtra("picture", picPaths);
					sendBroadcast(intent);

					MyApp.isAccOffPhotoTaking = false;
				}

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
		release();
		if (openCamera()) {
			setupRecorder();
		}
	}

	public void release() {
		releaseRecorder();
		closeCamera();
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			return true;
		} else
			return super.onKeyDown(keyCode, event);
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