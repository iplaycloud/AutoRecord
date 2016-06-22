package com.tchip.autorecord.receiver;

import com.tchip.autorecord.MyApp;
import com.tchip.autorecord.util.MyLog;
import com.tchip.autorecord.util.StorageUtil;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CardEjectReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		MyLog.i("CardEjectReceiver.action:" + action);
		if (action.equals(Intent.ACTION_MEDIA_EJECT)
				|| action.equals(Intent.ACTION_MEDIA_BAD_REMOVAL)
				|| action.equals(Intent.ACTION_MEDIA_UNMOUNTED)) {
			if ("/storage/sdcard2".equals(intent.getData().getPath())) {
				MyApp.isVideoCardEject = true;
			}
			// 规避播放音乐时拔SD,media-server died,从而导致主界面录像预览卡死问题
			// 但会导致播放网络音乐拔SD卡,同样关掉酷我
			// KWAPI.createKWAPI(context, "auto").exitAPP(context);
			// context.sendBroadcast(new Intent("com.tchip.KILL_APP").putExtra(
			// "value", "music_kuwo"));
		} else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
			if ("/storage/sdcard2".equals(intent.getData().getPath())) {
				StorageUtil.createRecordDirectory();
				if (MyApp.isAccOn && !MyApp.isFrontRecording) {
					MyApp.shouldMountRecord = true; // 插入录像卡自动录像
				}
				MyApp.isVideoCardEject = false;
				MyApp.isVideoCardFormat = false;
			}
		}
	}

}
