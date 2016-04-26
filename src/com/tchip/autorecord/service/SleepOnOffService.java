package com.tchip.autorecord.service;

import com.tchip.autorecord.Constant;
import com.tchip.autorecord.MyApp;
import com.tchip.autorecord.util.MyLog;
import com.tchip.autorecord.util.SettingUtil;

//import cn.kuwo.autosdk.api.KWAPI;

import android.app.Instrumentation;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.provider.Settings;
import android.view.KeyEvent;
import android.widget.Toast;

public class SleepOnOffService extends Service {
	private Context context;
	private SharedPreferences sharedPreferences;
	private Editor editor;
	private PowerManager powerManager;
	private WakeLock wakeLock;

	/** ACC断开进入预备模式的时间:秒 **/
	private int preSleepCount = 0;

	/** 预备睡眠模式的时间:秒 **/
	private final int TIME_SLEEP_CONFIRM = 2;

	/** ACC连接进入预备模式的时间:秒 **/
	private int preWakeCount = 0;

	/** 预备唤醒模式的时间:秒 **/
	private final int TIME_WAKE_CONFIRM = 1;

	/** ACC断开的时间:秒 **/
	private int accOffCount = 0;

	/** ACC断开进入深度休眠之前的时间:秒 **/
	private final int TIME_SLEEP_GOING = 85;

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public void onCreate() {
		super.onCreate();

		context = getApplicationContext();
		sharedPreferences = getSharedPreferences(Constant.MySP.NAME,
				Context.MODE_PRIVATE);
		editor = sharedPreferences.edit();
		powerManager = (PowerManager) context
				.getSystemService(Context.POWER_SERVICE);

		// 动态注册监听函数
		sleepOnOffReceiver = new SleepOnOffReceiver();
		IntentFilter filter = new IntentFilter();
		filter.addAction(Constant.Broadcast.ACC_ON);
		filter.addAction(Constant.Broadcast.ACC_OFF);
		filter.addAction(Constant.Broadcast.GSENSOR_CRASH);
		filter.addAction(Constant.Broadcast.SPEECH_COMMAND);
		filter.addAction(Constant.Broadcast.SETTING_SYNC);
		filter.addAction(Constant.Broadcast.MEDIA_FORMAT);
		registerReceiver(sleepOnOffReceiver, filter);
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
	private void acquireWakeLock() {
		wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
				this.getClass().getCanonicalName());
		wakeLock.acquire(90 * 1000);
		MyLog.v("[SleepOnOff]WakeLock acquire");
	}

	private SleepOnOffReceiver sleepOnOffReceiver;

	public class SleepOnOffReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			MyLog.v("[SleepOnOffReceiver]action:" + action);
			if (action.equals(Constant.Broadcast.ACC_OFF)) {
				MyApp.isAccOn = false;

				preSleepCount = 0;
				MyApp.isSleepConfirm = true;

				preWakeCount = 0;
				MyApp.isWakeConfirm = false;

				new Thread(new PreSleepThread()).start();
			} else if (action.equals(Constant.Broadcast.ACC_ON)) {
				MyApp.isAccOn = true;
				MyApp.isAccOffPhotoTaking = false; // 重置ACC下电拍照标志

				preSleepCount = 0;
				MyApp.isSleepConfirm = false;

				preWakeCount = 0;
				MyApp.isWakeConfirm = true;

				new Thread(new PreWakeThread()).start();
			} else if (action.equals(Constant.Broadcast.GSENSOR_CRASH)) { // 停车守卫:侦测到碰撞广播触发
				if (MyApp.isSleeping) {
					MyLog.v("[GSENSOR_CRASH]Before State->shouldCrashRecord:"
							+ MyApp.shouldCrashRecord
							+ ",shouldStopWhenCrashVideoSave:"
							+ MyApp.shouldStopWhenCrashVideoSave);

					if (MyApp.shouldStopWhenCrashVideoSave) {
						if (!MyApp.shouldCrashRecord && !MyApp.isVideoReording) {
							MyApp.shouldCrashRecord = true;
							MyApp.shouldStopWhenCrashVideoSave = true;
						}
					} else {
						MyApp.shouldCrashRecord = true;
						MyApp.shouldStopWhenCrashVideoSave = true;
					}
				}
			} else if (action.equals(Constant.Broadcast.SPEECH_COMMAND)) {
				String command = intent.getExtras().getString("command");
				if ("take_photo".equals(command)) {
					MyApp.shouldTakeVoicePhoto = true; // 语音拍照

					sendKeyCode(KeyEvent.KEYCODE_HOME); // 发送Home键，回到主界面
					if (!powerManager.isScreenOn()) { // 确保屏幕点亮
						SettingUtil.lightScreen(getApplicationContext());
					}
				} else if ("take_photo_wenxin".equals(command)) {
					MyApp.shouldTakeVoicePhoto = true; // 语音拍照

					sendKeyCode(KeyEvent.KEYCODE_HOME); // 发送Home键，回到主界面
					if (!powerManager.isScreenOn()) { // 确保屏幕点亮
						SettingUtil.lightScreen(getApplicationContext());
					}
				} else if ("open_dvr".equals(command)) {
					if (MyApp.isAccOn && !MyApp.isVideoReording) {
						MyApp.shouldMountRecord = true;
					}
					sendKeyCode(KeyEvent.KEYCODE_HOME);

				} else if ("close_dvr".equals(command)) {
					if (MyApp.isVideoReording) {
						MyApp.shouldStopRecordFromVoice = true;
					}

				}
			} else if (action.equals(Constant.Broadcast.SETTING_SYNC)) {
				String content = intent.getExtras().getString("content");
				if ("parkOn".equals(content)) { // 停车守卫:开
					editor.putBoolean(Constant.MySP.STR_PARKING_ON, true);
					editor.commit();

				} else if ("parkOff".equals(content)) { // 停车守卫:关
					editor.putBoolean(Constant.MySP.STR_PARKING_ON, false);
					editor.commit();

				} else if ("crashOn".equals(content)) { // 碰撞侦测:开
					editor.putBoolean("crashOn", true);
					editor.commit();

				} else if ("crashOff".equals(content)) { // 碰撞侦测:关
					editor.putBoolean("crashOn", false);
					editor.commit();

				} else if ("crashLow".equals(content)) { // 碰撞侦测灵敏度:低
					MyApp.crashSensitive = 0;
					editor.putInt("crashSensitive", 0);
					editor.commit();

				} else if ("crashMiddle".equals(content)) { // 碰撞侦测灵敏度:中
					MyApp.crashSensitive = 1;
					editor.putInt("crashSensitive", 1);
					editor.commit();

				} else if ("crashHigh".equals(content)) { // 碰撞侦测灵敏度:高
					MyApp.crashSensitive = 2;
					editor.putInt("crashSensitive", 2);
					editor.commit();

				}
			} else if (action.equals(Constant.Broadcast.MEDIA_FORMAT)) {
				String path = intent.getExtras().getString("path");
				MyLog.e("SleepOnOffReceiver: MEDIA_FORMAT !! Path:" + path);
				if ("/storage/sdcard2".equals(path)) {
					MyApp.isVideoCardFormat = true;
				}

			}
		}
	}

	/** 预备唤醒(ACC_ON)线程 **/
	public class PreWakeThread implements Runnable {

		@Override
		public void run() {
			synchronized (preWakeHandler) {
				/** 激发条件:1.ACC上电 **/
				while (MyApp.isWakeConfirm && MyApp.isAccOn) {
					try {
						Thread.sleep(1000);
						Message message = new Message();
						message.what = 1;
						preWakeHandler.sendMessage(message);

					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}

	}

	private final Handler preWakeHandler = new Handler() {
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				if (MyApp.isAccOn) {
					preWakeCount++;
				} else {
					preWakeCount = 0;
				}
				MyLog.v("[ParkingMonitor]preWakeCount:" + preWakeCount);

				if (preWakeCount == TIME_WAKE_CONFIRM && MyApp.isAccOn) {
					MyApp.isWakeConfirm = false;
					preWakeCount = 0;
					deviceWake();
				}
				break;

			default:
				break;
			}
		}
	};

	/** 预备休眠线程 **/
	public class PreSleepThread implements Runnable {

		@Override
		public void run() {
			synchronized (preSleepHandler) {
				/** 激发条件:1.ACC下电 2.未进入休眠 **/
				while (MyApp.isSleepConfirm && !MyApp.isAccOn
						&& !MyApp.isSleeping) {
					try {
						Thread.sleep(1000);
						Message message = new Message();
						message.what = 1;
						preSleepHandler.sendMessage(message);

					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}

	}

	final Handler preSleepHandler = new Handler() {

		public void handleMessage(Message msg) {
			switch (msg.what) {
			case 1:
				if (!MyApp.isAccOn) {
					preSleepCount++;
				} else {
					preSleepCount = 0;
				}
				MyLog.v("[ParkingMonitor]preSleepCount:" + preSleepCount);

				if (preSleepCount == TIME_SLEEP_CONFIRM && !MyApp.isAccOn
						&& !MyApp.isSleeping) {
					MyApp.isSleepConfirm = false;
					preSleepCount = 0;
					deviceAccOff();
				}
				break;

			default:
				break;
			}
		}

	};

	/**
	 * 90s后进入停车侦测守卫模式，期间如果ACC上电则取消
	 */
	public class GoingParkMonitorThread implements Runnable {

		@Override
		public void run() {
			synchronized (goingParkMonitorHandler) {
				/** 激发条件:1.ACC下电 2.未进入休眠 **/
				while (!MyApp.isAccOn && !MyApp.isSleeping) {
					try {
						Thread.sleep(1000);
						Message message = new Message();
						message.what = 1;
						goingParkMonitorHandler.sendMessage(message);

					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}

	final Handler goingParkMonitorHandler = new Handler() {
		public void handleMessage(android.os.Message msg) {
			switch (msg.what) {
			case 1:
				if (!MyApp.isAccOn) {
					accOffCount++;
				} else {
					accOffCount = 0;
				}
				MyLog.v("[ParkingMonitor]accOffCount:" + accOffCount);

				if (accOffCount >= TIME_SLEEP_GOING && !MyApp.isAccOn
						&& !MyApp.isSleeping) {
					deviceSleep();
				}
				break;

			default:
				break;
			}
		}
	};

	/**
	 * 执行90秒任务
	 */
	private void deviceAccOff() {
		accOffCount = 0;

		if (!MyApp.isMainForeground) {
			// 发送Home键，回到主界面
			sendKeyCode(KeyEvent.KEYCODE_HOME);
			if (!powerManager.isScreenOn()) { // 确保屏幕点亮
				SettingUtil.lightScreen(getApplicationContext());
			}
		}

		MyApp.shouldTakePhotoWhenAccOff = true;

		acquireWakeLock();
		new Thread(new GoingParkMonitorThread()).start();

		stopExternalService();

		SettingUtil.setGpsState(context, false);
		SettingUtil.setEDogEnable(false); // 关闭电子狗电源

		// 关闭FM发射，并保存休眠前状态
		boolean fmStateBeforeSleep = SettingUtil.isFmTransmitOn(context);
		editor.putBoolean("fmStateBeforeSleep", fmStateBeforeSleep);
		editor.commit();
		if (fmStateBeforeSleep) {
			MyLog.v("[SleepReceiver]Sleep: close FM");
			Settings.System.putString(context.getContentResolver(),
					Constant.FMTransmit.SETTING_ENABLE, "0");
			SettingUtil.SaveFileToNode(SettingUtil.nodeFmEnable, "0");
			sendBroadcast(new Intent("com.tchip.FM_CLOSE_CARLAUNCHER")); // 通知状态栏同步图标
		}
	}

	/**
	 * 休眠广播触发
	 */
	private void deviceSleep() {
		try {
			MyLog.e("[SleepOnOffService]deviceSleep.");
			MyApp.isSleeping = true; // 进入低功耗待机
		} catch (Exception e) {
			MyLog.e("[SleepReceiver]Error when run deviceSleep");
		} finally {
			MyApp.isAccOffPhotoTaking = false; // 重置ACC下电拍照标志
			SettingUtil.setAirplaneMode(context, true); // 打开飞行模式
			context.sendBroadcast(new Intent(Constant.Broadcast.SLEEP_ON)); // 通知其他应用进入休眠
		}
	}

	/**
	 * 唤醒广播触发
	 */
	private void deviceWake() {
		try {
			MyApp.isSleeping = false; // 取消低功耗待机
			startExternalService();

			MyApp.shouldStopWhenCrashVideoSave = false; // 如果当前正在停车侦测录像，录满30S后不停止

			// MainActivity,BackThread的Handler启动AutoThread,启动录像和服务
			MyApp.shouldWakeRecord = true;

			sendKeyCode(KeyEvent.KEYCODE_HOME); // 发送Home键，回到主界面
			SettingUtil.setAirplaneMode(context, false); // 关闭飞行模式
			SettingUtil.setGpsState(context, true); // 打开GPS
			// SettingUtil.setEDogEnable(true); // 打开电子狗电源
			context.sendBroadcast(new Intent(Constant.Broadcast.SLEEP_OFF)); // 通知其他应用取消休眠

			// 重置FM发射状态
			boolean fmStateBeforeSleep = sharedPreferences.getBoolean(
					"fmStateBeforeSleep", false);
			if (fmStateBeforeSleep) {
				MyLog.v("[SleepReceiver]WakeUp:open FM Transmit");
				Settings.System.putString(context.getContentResolver(),
						Constant.FMTransmit.SETTING_ENABLE, "1");
				SettingUtil.SaveFileToNode(SettingUtil.nodeFmEnable, "1");
				sendBroadcast(new Intent("com.tchip.FM_OPEN_CARLAUNCHER")); // 通知状态栏同步图标
			}
		} catch (Exception e) {
			MyLog.e("[SleepReceiver]Error when run deviceWake");
		}
	}

	/**
	 * 开启外部服务：
	 * 
	 * 1.天气播报(整点播报)
	 * 
	 * 2.碰撞侦测服务
	 */
	private void startExternalService() {
		try {
			// 天气播报(整点报时)
			Intent intentWeather = new Intent();
			intentWeather.setClassName("com.tchip.weather",
					"com.tchip.weather.service.TimeTickService");
			startService(intentWeather);

			// 碰撞侦测服务
			Intent intentCrash = new Intent(context, SensorWatchService.class);
			startService(intentCrash);
		} catch (Exception e) {
			e.printStackTrace();
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

	/**
	 * 关闭外部应用与服务：
	 * 
	 * 天气播报服务
	 * 
	 * 酷我音乐
	 * 
	 * 碰撞侦测服务
	 */
	private void stopExternalService() {
		try {
			// 天气播报(整点报时)
			Intent intentWeather = new Intent();
			intentWeather.setClassName("com.tchip.weather",
					"com.tchip.weather.service.TimeTickService");
			stopService(intentWeather);

			// 碰撞侦测服务
			Intent intentCrash = new Intent(context, SensorWatchService.class);
			stopService(intentCrash);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onDestroy() {
		if (sleepOnOffReceiver != null) {
			unregisterReceiver(sleepOnOffReceiver);
		}

		super.onDestroy();
	}

}