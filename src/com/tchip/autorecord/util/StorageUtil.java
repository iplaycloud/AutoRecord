package com.tchip.autorecord.util;

import java.io.File;

import com.tchip.autorecord.Constant;
import com.tchip.autorecord.MyApp;
import com.tchip.autorecord.R;
import com.tchip.autorecord.db.DriveVideo;
import com.tchip.autorecord.db.DriveVideoDbHelper;

import android.content.Context;
import android.media.ExifInterface;
import android.os.StatFs;

public class StorageUtil {

	/**
	 * 获得SD卡总大小
	 * 
	 * @return 总大小，单位：字节B
	 */
	public static long getSDTotalSize(String SDCardPath) {
		StatFs stat = new StatFs(SDCardPath);
		long blockSize = stat.getBlockSize();
		long totalBlocks = stat.getBlockCount();
		return blockSize * totalBlocks;
	}

	/**
	 * 获得sd卡剩余容量，即可用大小
	 * 
	 * @return 剩余空间，单位：字节B
	 */
	public static long getSDAvailableSize(String SDCardPath) {
		// StatFs stat = new StatFs("/storage/sdcard1");
		StatFs stat = new StatFs(SDCardPath);
		long blockSize = stat.getBlockSize();
		long availableBlocks = stat.getAvailableBlocks();
		return blockSize * availableBlocks;
	}

	/** 录像SD卡是否存在 */
	public static boolean isFrontCardExist() {
		boolean isVideoCardExist = false;
		try {
			String pathVideo = Constant.Path.RECORD_FRONT;
			File fileVideo = new File(pathVideo);
			boolean isSuccess = fileVideo.mkdirs();
			MyLog.v("StorageUtil.isVideoCardExists,mkdirs isSuccess:"
					+ isSuccess);
			File file = new File(pathVideo);
			if (!file.exists()) {
				isVideoCardExist = false;
			} else {
				isVideoCardExist = true;
			}
		} catch (Exception e) {
			MyLog.e("StorageUtil.isVideoCardExists:Catch Exception!");
			isVideoCardExist = false;
		}
		MyLog.v("StorageUtil.isVideoCardExists:" + isVideoCardExist);
		return isVideoCardExist;
	}

	/** 录像SD卡是否存在 */
	public static boolean isBackCardExist() {
		boolean isVideoCardExist = false;
		try {
			String pathVideo = Constant.Path.RECORD_BACK;
			File fileVideo = new File(pathVideo);
			boolean isSuccess = fileVideo.mkdirs();
			MyLog.v("StorageUtil.isVideoCardExists,mkdirs isSuccess:"
					+ isSuccess);
			File file = new File(pathVideo);
			if (!file.exists()) {
				isVideoCardExist = false;
			} else {
				isVideoCardExist = true;
			}
		} catch (Exception e) {
			MyLog.e("StorageUtil.isVideoCardExists:Catch Exception!");
			isVideoCardExist = false;
		}
		MyLog.v("StorageUtil.isVideoCardExists:" + isVideoCardExist);
		return isVideoCardExist;
	}

	/** 创建前后录像存储卡目录 */
	public static void createRecordDirectory() {
		boolean isSuccess = new File(Constant.Path.RECORD_FRONT).mkdirs();
		MyLog.v("StorageUtil.createRecordDirectory,mkdirs isSuccess:"
				+ isSuccess);
		new File(Constant.Path.RECORD_BACK).mkdirs();
	}

	/**
	 * 递归删除文件和文件夹
	 * 
	 * @param file
	 *            要删除的根目录
	 */
	public static void RecursionDeleteFile(File file) {
		try {
			if (file.isFile()) {
				if (!file.getName().startsWith(".")) { // 不删除正在录制的视频
					file.delete();
				}
				return;
			}
			if (file.isDirectory()) {
				File[] childFile = file.listFiles();
				if (childFile == null || childFile.length == 0) {
					file.delete();
					return;
				}
				for (File f : childFile) {
					RecursionDeleteFile(f);
				}
				file.delete();
			}
		} catch (Exception e) {
			MyLog.e("StorageUtil.RecursionDeleteFile:Catch Exception!");
		}
	}

	/** 将加锁视频移动到加锁文件夹 */
	public static void lockVideo(boolean isFront, String videoName) {
		String rawPath = isFront ? Constant.Path.RECORD_FRONT
				: Constant.Path.RECORD_BACK;
		String lockPath = isFront ? Constant.Path.RECORD_FRONT_LOCK
				: Constant.Path.RECORD_BACK_LOCK;
		File rawFile = new File(rawPath + videoName);
		if (rawFile.exists() && rawFile.isFile()) {
			File lockDir = new File(lockPath);
			if (!lockDir.exists()) {
				lockDir.mkdirs();
			}
			File lockFile = new File(lockDir + File.separator + videoName);
			rawFile.renameTo(lockFile);
			MyLog.v("StorageUtil.lockVideo:" + videoName);
		}
	}

	/**
	 * 删除最旧视频，调用此函数的地方：
	 * 
	 * 1.开启录像 {@link MainActivity#startRecordTask}
	 * 
	 * 2.文件保存回调{@link MainActivity#onFileSave}
	 */
	public static boolean releaseFrontStorage(Context context) {
		if (!StorageUtil.isFrontCardExist()) {
			MyLog.e("Storageutil.deleteOldestUnlockVideo:No Video Card");
			return false;
		}
		try {
			// 视频数据库
			DriveVideoDbHelper videoDb = new DriveVideoDbHelper(context);
			// AudioRecordDialog audioRecordDialog = new
			// AudioRecordDialog(context);
			while (FileUtil.isFrontStorageLess()) {
				int oldestUnlockVideoId = videoDb.getOldestUnlockFrontVideoId();
				if (oldestUnlockVideoId != -1) { // 删除较旧未加锁视频文件
					String oldestUnlockVideoName = videoDb
							.getVideNameById(oldestUnlockVideoId);
					File file;
					if (oldestUnlockVideoName.endsWith("_1.mp4")) { // 后录
						file = new File(Constant.Path.RECORD_BACK
								+ oldestUnlockVideoName);
					} else {
						file = new File(Constant.Path.RECORD_FRONT
								+ oldestUnlockVideoName);
					}
					if (file.exists() && file.isFile()) {
						MyLog.d("StorageUtil.Delete Old Unlock Video:"
								+ file.getPath());
						int i = 0;
						while (!file.delete() && i < 3) {
							i++;
							MyLog.d("StorageUtil.Delete Old Unlock Video:"
									+ file.getName() + " Filed!!! Try:" + i);
						}
					}
					videoDb.deleteDriveVideoById(oldestUnlockVideoId); // 删除数据库记录
				} else {
					int oldestVideoId = videoDb.getOldestVideoId();
					if (oldestVideoId == -1) {
						if (FileUtil.isFrontStorageLess()) { // 此时若空间依然不足,提示用户清理存储（已不是行车视频的原因）
							MyLog.e("StorageUtil:Storage is full...");
							String strNoStorage = context
									.getResources()
									.getString(
											R.string.hint_storage_full_cause_by_other);
							HintUtil.showToast(context, strNoStorage);
							return false;
						}
					} else { // 提示用户清理空间，删除较旧的视频（加锁）
						String strStorageFull = context
								.getResources()
								.getString(
										R.string.hint_storage_full_and_delete_lock);
						HintUtil.showToast(context, strStorageFull);
						String oldestVideoName = videoDb
								.getVideNameById(oldestVideoId);
						File file;
						if (oldestVideoName.endsWith("_1.mp4")) { // 后录文件
							file = new File(Constant.Path.RECORD_BACK_LOCK
									+ File.separator + oldestVideoName);
						} else { // 前录文件
							file = new File(Constant.Path.RECORD_FRONT_LOCK
									+ File.separator + oldestVideoName);
						}
						if (file.exists() && file.isFile()) {
							MyLog.d("StorageUtil.Delete Old Unlock Video:"
									+ file.getPath());
							int i = 0;
							while (!file.delete() && i < 3) {
								i++;
								MyLog.d("StorageUtil.Delete Old lock Video:"
										+ file.getName() + " Filed!!! Try:" + i);
							}
						}
						videoDb.deleteDriveVideoById(oldestVideoId); // 删除数据库记录
					}
				}
			}
			return true;
		} catch (Exception e) {
			/* 异常原因：1.文件由用户手动删除 */
			MyLog.e("StorageUtil.deleteOldestUnlockVideo:Catch Exception:"
					+ e.toString());
			e.printStackTrace();
			return true;
		}
	}

	/**
	 * 删除最旧视频，调用此函数的地方：
	 * 
	 * 1.开启录像 {@link MainActivity#startRecordTask}
	 * 
	 * 2.文件保存回调{@link MainActivity#onFileSave}
	 */
	public static boolean releaseBackStorage(Context context) {
		if (!StorageUtil.isBackCardExist()) {
			MyLog.e("Storageutil.deleteOldestUnlockVideo:No Video Card");
			return false;
		}
		try {
			// 视频数据库
			DriveVideoDbHelper videoDb = new DriveVideoDbHelper(context);
			while (FileUtil.isBackStorageLess()) {
				int oldestUnlockVideoId = videoDb.getOldestUnlockBackVideoId();
				if (oldestUnlockVideoId != -1) { // 删除较旧未加锁视频文件
					String oldestUnlockVideoName = videoDb
							.getVideNameById(oldestUnlockVideoId);
					File file;
					if (oldestUnlockVideoName.endsWith("_1.mp4")) { // 后录
						file = new File(Constant.Path.RECORD_BACK
								+ oldestUnlockVideoName);
					} else {
						file = new File(Constant.Path.RECORD_FRONT
								+ oldestUnlockVideoName);
					}

					if (file.exists() && file.isFile()) {
						MyLog.d("StorageUtil.Delete Old Unlock Video:"
								+ file.getPath());
						int i = 0;
						while (!file.delete() && i < 3) {
							i++;
							MyLog.d("StorageUtil.Delete Old Unlock Video:"
									+ file.getName() + " Filed!!! Try:" + i);
						}
					}
					videoDb.deleteDriveVideoById(oldestUnlockVideoId); // 删除数据库记录
				} else {
					int oldestVideoId = videoDb.getOldestVideoId();
					if (oldestVideoId == -1) {
						if (FileUtil.isFrontStorageLess()) { // 此时若空间依然不足,提示用户清理存储（已不是行车视频的原因）
							MyLog.e("StorageUtil:Storage is full...");
							String strNoStorage = context
									.getResources()
									.getString(
											R.string.hint_storage_full_cause_by_other);
							HintUtil.showToast(context, strNoStorage);
							return false;
						}
					} else { // 提示用户清理空间，删除较旧的视频（加锁）
						String strStorageFull = context
								.getResources()
								.getString(
										R.string.hint_storage_full_and_delete_lock);
						HintUtil.showToast(context, strStorageFull);
						String oldestVideoName = videoDb
								.getVideNameById(oldestVideoId);
						File file;
						if (oldestVideoName.endsWith("_1.mp4")) { // 后录文件
							file = new File(Constant.Path.RECORD_BACK_LOCK
									+ File.separator + oldestVideoName);
						} else { // 前录文件
							file = new File(Constant.Path.RECORD_FRONT_LOCK
									+ File.separator + oldestVideoName);
						}
						if (file.exists() && file.isFile()) {
							MyLog.d("StorageUtil.Delete Old Unlock Back Video:"
									+ file.getPath());
							int i = 0;
							while (!file.delete() && i < 3) {
								i++;
								MyLog.d("StorageUtil.Delete Old lock Video:"
										+ file.getName() + " Filed!!! Try:" + i);
							}
						}
						videoDb.deleteDriveVideoById(oldestVideoId); // 删除数据库记录
					}
				}
			}
			return true;
		} catch (Exception e) {
			/*
			 * 异常原因：1.文件由用户手动删除
			 */
			MyLog.e("StorageUtil.deleteOldestUnlockVideo:Catch Exception:"
					+ e.toString());
			e.printStackTrace();
			return true;
		}
	}

	/**
	 * 删除数据库中不存在的错误视频文件
	 * 
	 * @param file
	 */
	public static void RecursionCheckFile(Context context, File file) {

		if (file.exists()) {
			DriveVideoDbHelper videoDb = new DriveVideoDbHelper(context); // 视频数据库
			try {
				String fileName = file.getName();
				if (file.isFile()) {
					if (fileName.endsWith(".mp4")) {
						if (fileName.startsWith(".")) {
							// Delete file start with dot but not the recording
							// one
							if (!MyApp.isFrontRecording
									&& !MyApp.isBackRecording) {
								file.delete();
								MyLog.v("StorageUtil.RecursionCheckFile-Delete DOT File:"
										+ fileName);
							}
						} else {
							boolean isVideoExist = videoDb
									.isVideoExist(fileName);
							if (!isVideoExist) {
								boolean isLock = file.getPath()
										.contains("Lock");
								DriveVideo driveVideo = new DriveVideo(
										fileName, isLock ? 1 : 0, 555, 0);
								videoDb.addDriveVideo(driveVideo);
								MyLog.v("StorageUtil.RecursionCheckFile-Insert Video To DB:"
										+ file.getAbsolutePath());
							}
						}
						return;
					} else if (!fileName.endsWith(".jpg")
							&& !fileName.endsWith(".tmp")) {
						file.delete();
					}
				} else if (file.isDirectory()) {
					File[] childFile = file.listFiles();
					if (childFile == null || childFile.length == 0) {
						return;
					}
					for (File f : childFile) {
						RecursionCheckFile(context, f);
					}
				}
			} catch (Exception e) {
				MyLog.e("StorageUtil.RecursionCheckFile-Catch Exception:"
						+ e.toString());
			}

		}
	}

	/**
	 * 写入照片EXIF信息
	 * 
	 * @param context
	 * @param imagePath
	 */
	public static void writeImageExif() {
		if (!MyApp.writeImageExifPath.equals("NULL")
				&& MyApp.writeImageExifPath.endsWith(".jpg")) {
			try { // Android Way
				ExifInterface exif = new ExifInterface(MyApp.writeImageExifPath);
				exif.setAttribute(ExifInterface.TAG_ORIENTATION, ""
						+ ExifInterface.ORIENTATION_NORMAL);
				exif.setAttribute(ExifInterface.TAG_MAKE, "TQ"); // 品牌
				exif.setAttribute(ExifInterface.TAG_MODEL, "X2"); // 型号/机型
				exif.saveAttributes();
			} catch (Exception e) {
				MyLog.e("writeImageExif.Set Attribute Catch Exception:"
						+ e.toString());
				e.printStackTrace();
			}

			MyApp.writeImageExifPath = "NULL";

		} else {

		}
	}
}
