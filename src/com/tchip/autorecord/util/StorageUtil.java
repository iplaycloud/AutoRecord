package com.tchip.autorecord.util;

import java.io.File;

import com.tchip.autorecord.Constant;
import com.tchip.autorecord.MyApp;
import com.tchip.autorecord.R;
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
	public static boolean isVideoCardExists() {
		boolean isVideoCardExist = false;

		// String pathVideoCard = Constant.Path.SDCARD_2;
		// File file = new File(pathVideoCard);
		// if (!file.exists()) {
		// isVideoCardExist = false;
		// } else {
		// isVideoCardExist = true;
		// }

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

	/** 创建前后录像存储卡目录 */
	public static void createRecordDirectory() {
		boolean isSuccess = new File(Constant.Path.RECORD_FRONT).mkdirs();
		MyLog.v("StorageUtil.createRecordDirectory,mkdirs isSuccess:"
				+ isSuccess);
		if (Constant.Module.isRecordSingleCard) {
			new File(Constant.Path.RECORD_BACK).mkdirs();
		}
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

	/** 删除空视频文件夹 **/
	public static void deleteEmptyVideoDirectory() {
		File fileRoot = new File(Constant.Path.RECORD_FRONT);
		if (fileRoot.exists()) {
			File[] listFileDate = fileRoot.listFiles();
			for (File file : listFileDate) {
				if (file.isDirectory()) {
					int numberChild = file.listFiles().length;
					if (numberChild == 0) {
						file.delete();
						MyLog.v("StorageUtil.Delete Empty Video Directory:"
								+ file.getName() + ",Length:" + numberChild);
					}
				}
			}
		}
	}

	/** SD卡空间不足 */
	public static boolean isStorageLess() {
		return Constant.Module.isRecordSingleCard ? FileUtil
				.isStorageLessSingle() : FileUtil.isStorageLessDouble();
	}

	/**
	 * 删除最旧视频，调用此函数的地方：
	 * 
	 * 1.开启录像 {@link MainActivity#startRecordTask}
	 * 
	 * 2.文件保存回调{@link MainActivity#onFileSave}
	 */
	public static boolean releaseRecordStorage(Context context) {
		if (!StorageUtil.isVideoCardExists()) {
			MyLog.e("Storageutil.deleteOldestUnlockVideo:No Video Card");
			return false;
		}
		try {
			// 视频数据库
			DriveVideoDbHelper videoDb = new DriveVideoDbHelper(context);
			// AudioRecordDialog audioRecordDialog = new
			// AudioRecordDialog(context);
			StorageUtil.deleteEmptyVideoDirectory();
			while (isStorageLess()) {
				int oldestUnlockVideoId = videoDb.getOldestUnlockVideoId();
				// 删除较旧未加锁视频文件
				if (oldestUnlockVideoId != -1) {
					String oldestUnlockVideoName = videoDb
							.getVideNameById(oldestUnlockVideoId);
					File file = new File(Constant.Path.RECORD_FRONT
							+ oldestUnlockVideoName);
					if (file.exists() && file.isFile()) {
						MyLog.d("StorageUtil.Delete Old Unlock Video:"
								+ file.getName());
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
						if (isStorageLess()) { // 此时若空间依然不足,提示用户清理存储（已不是行车视频的原因）
							MyLog.e("StorageUtil:Storage is full...");
							String strNoStorage = context
									.getResources()
									.getString(
											R.string.hint_storage_full_cause_by_other);
							HintUtil.showToast(context, strNoStorage);

							// audioRecordDialog.showErrorDialog(strNoStorage);
							// MyApp.speakVoice(strNoStorage);
							return false;
						}
					} else {
						// 提示用户清理空间，删除较旧的视频（加锁）
						String strStorageFull = context
								.getResources()
								.getString(
										R.string.hint_storage_full_and_delete_lock);
						// MyApp.speakVoice(strStorageFull);
						HintUtil.showToast(context, strStorageFull);
						String oldestVideoName = videoDb
								.getVideNameById(oldestVideoId);
						File file = new File(Constant.Path.RECORD_FRONT
								+ oldestVideoName);
						if (file.exists() && file.isFile()) {
							MyLog.d("StorageUtil.Delete Old lock Video:"
									+ file.getName());
							int i = 0;
							while (!file.delete() && i < 5) {
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
				if (file.isFile() && !fileName.endsWith(".jpg")
						&& !fileName.endsWith(".tmp")) {
					if (fileName.startsWith(".")) {
						// Delete file start with dot but not the recording one
						if (!MyApp.isVideoReording) {
							file.delete();
							MyLog.v("StorageUtil.RecursionCheckFile-Delete Error File start with DOT:"
									+ fileName);
						}
					} else {
						boolean isVideoExist = videoDb.isVideoExist(fileName);
						if (!isVideoExist) {
							// boolean isSuccess = file.delete();
							boolean isSuccess = file.getAbsoluteFile().delete();
							MyLog.v("StorageUtil.RecursionCheckFile-Delete Error File:"
									+ file.getAbsolutePath()
									+ ",canRead:"
									+ file.canRead()
									+ ",canWrite:"
									+ file.canWrite()
									+ ",isSuccess:"
									+ isSuccess);
						}
					}
					return;
				}
				if (file.isDirectory()) {
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
