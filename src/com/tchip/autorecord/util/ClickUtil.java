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
		if (0 < timeD && timeD < clickMinSpan) {
			MyLog.v("[ClickUtil]isQuickClick:Click Too Quickly!");
			return true;
		}
		lastClickTime = time;
		return false;
	}

	private static long lastUpdate3GSingalTime;

	/**
	 * @param runMinSpan
	 *            两次运行至少间隔时间,单位:ms
	 * @return
	 */
	public static boolean isUpdate3GSignalTooQuick(int runMinSpan) {
		long time = System.currentTimeMillis();
		long timeD = time - lastUpdate3GSingalTime;
		if (0 < timeD && timeD < runMinSpan) {
			MyLog.v("[ClickUtil]isUpdate3GSignalTooQuick,Run Too Quickly!");
			return true;
		}
		lastUpdate3GSingalTime = time;
		return false;
	}

	private static long lastUpdateRightTopIconTime;

	public static boolean isUpdateRightTopIconTooQuick(int runMinSpan) {
		long time = System.currentTimeMillis();
		long timeD = time - lastUpdateRightTopIconTime;
		if (0 < timeD && timeD < runMinSpan) {
			MyLog.v("[ClickUtil]isUpdateRightTopIconTooQuick,Run Too Quickly!");
			return true;
		}
		lastUpdateRightTopIconTime = time;
		return false;
	}

	private static long lastPlusRecordTime;

	public static boolean isPlusRecordTimeTooQuick(int runMinSpan) {
		long time = System.currentTimeMillis();
		long timeD = time - lastPlusRecordTime;
		if (0 < timeD && timeD < runMinSpan) {
			MyLog.v("[ClickUtil]isPlusRecordTimeTooQuick,Run Too Quickly!");
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
		if (duration < 3000) {
			return true;
		} else {
			return false;
		}
	}
}
