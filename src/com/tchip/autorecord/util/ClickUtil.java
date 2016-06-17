package com.tchip.autorecord.util;

public class ClickUtil {

	private static long lastClickTime;

	/**
	 * @param clickMinSpan
	 *            两次点击至少间隔时间,单位:ms
	 * @return
	 */
	public static boolean isQuickClick(int clickMinSpan) {
		long time = System.currentTimeMillis();
		long timeD = time - lastClickTime;
		if (0 < timeD && timeD < clickMinSpan) { // Click Too Quickly
			return true;
		}
		lastClickTime = time;
		return false;
	}

	private static long lastHintNoSd2Time;

	public static boolean isHintNoSd2TooQuick(int runMinSpan) {
		long time = System.currentTimeMillis();
		long timeD = time - lastHintNoSd2Time;
		if (0 < timeD && timeD < runMinSpan) {
			return true;
		}
		lastHintNoSd2Time = time;
		return false;
	}

	private static long lastHintSleepTime;

	public static boolean isHintSleepTooQuick(int runMinSpan) {
		long time = System.currentTimeMillis();
		long timeD = time - lastHintSleepTime;
		if (0 < timeD && timeD < runMinSpan) {
			return true;
		}
		lastHintSleepTime = time;
		return false;
	}

	private static long lastPlusRecordTime;

	public static boolean isPlusRecordTimeTooQuick(int runMinSpan) {
		long time = System.currentTimeMillis();
		long timeD = time - lastPlusRecordTime;
		if (0 < timeD && timeD < runMinSpan) {
			return true;
		}
		lastPlusRecordTime = time;
		return false;
	}

	private static long lastSaveLogTime;

	public static boolean isSaveLogTooQuick(int runMinSpan) {
		long time = System.currentTimeMillis();
		long timeD = time - lastSaveLogTime;
		if (0 < timeD && timeD < runMinSpan) {
			MyLog.v("[ClickUtil]isSaveLogTooQuick,Run Too Quickly!");
			return true;
		}
		lastSaveLogTime = time;
		return false;
	}

	/**
	 * 传递过来的Intent是否是最近传递过来
	 * 
	 * @param sendTime
	 * @return
	 */
	public static boolean isIntentInTime(long sendTime) {
		long nowTime = System.currentTimeMillis();
		long duration = nowTime - sendTime;
		MyLog.v("ClickUtil.sendTime:" + sendTime + ",nowTime:" + nowTime);
		if (duration < 3000) {
			return true;
		} else {
			return false;
		}
	}
}
