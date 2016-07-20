package com.tchip.autorecord.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

import android.content.Context;

import com.tchip.autorecord.Constant;
import com.tchip.autorecord.MyApp;

public class Flash2SDUtil {

	public static void moveVideoToSD(Context context, boolean isFront,
			boolean isLock, String videoName) {
		String oldFilePath = isFront ? Constant.Path.VIDEO_FRONT_FLASH
				+ videoName : Constant.Path.VIDEO_BACK_FLASH + videoName;
		String newFilePath = isFront ? Constant.Path.VIDEO_FRONT_SD
				: Constant.Path.VIDEO_BACK_SD;
		if (isLock) {
			newFilePath = (isFront ? Constant.Path.VIDEO_FRONT_SD_LOCK
					: Constant.Path.VIDEO_BACK_SD_LOCK) + File.separator;
		}
		newFilePath = newFilePath + videoName;
		boolean isSuccess = copyFile(oldFilePath, newFilePath);
		MyLog.v("moveVideoToSD,name:" + videoName + ",isSuccess:" + isSuccess);
	}

	public static void moveImageToSD(String imageName) {
		boolean isSuccess = copyFile(Constant.Path.IMAGE_FLASH + imageName,
				Constant.Path.IMAGE_SD + imageName);
		MyLog.v("moveImageToSD,name:" + imageName + ",isSuccess:" + isSuccess);
		moveOldImageToSD();
	}

	/**
	 * onFileStart时将拔卡导致没能移动到SD卡的文件移动到SD
	 */
	public static void moveOldFrontVideoToSD() {
		try {
			File dirFront = new File(Constant.Path.VIDEO_FRONT_FLASH);
			String[] listFront = dirFront.list();
			File fileTemp = null;
			for (int i = 0; i < listFront.length; i++) {
				fileTemp = new File(Constant.Path.VIDEO_FRONT_FLASH
						+ File.separator + listFront[i]);
				if (fileTemp.isFile() && !fileTemp.getName().startsWith(".")) {
					FileInputStream input = new FileInputStream(fileTemp);
					FileOutputStream output = new FileOutputStream(
							Constant.Path.VIDEO_FRONT_SD + File.separator
									+ (fileTemp.getName()).toString());
					byte[] b = new byte[1024 * 5];
					int len;
					while ((len = input.read(b)) != -1) {
						output.write(b, 0, len);
					}
					output.flush();
					output.close();
					input.close();
					fileTemp.delete();
					MyLog.v("moveOldFrontVideoToSD:" + fileTemp.getName());
				}
			}
		} catch (Exception e) {
		}
	}

	public static void moveOldBackVideoToSD() {
		try {
			File dirBack = new File(Constant.Path.VIDEO_BACK_FLASH);
			String[] listBack = dirBack.list();
			File fileTemp = null;
			for (int i = 0; i < listBack.length; i++) {
				fileTemp = new File(Constant.Path.VIDEO_BACK_FLASH
						+ File.separator + listBack[i]);
				if (fileTemp.isFile() && !fileTemp.getName().startsWith(".")) {
					FileInputStream input = new FileInputStream(fileTemp);
					FileOutputStream output = new FileOutputStream(
							Constant.Path.VIDEO_BACK_SD + File.separator
									+ (fileTemp.getName()).toString());
					byte[] b = new byte[1024 * 5];
					int len;
					while ((len = input.read(b)) != -1) {
						output.write(b, 0, len);
					}
					output.flush();
					output.close();
					input.close();
					fileTemp.delete();
					MyLog.v("moveOldBackVideoToSD:" + fileTemp.getName());
				}
			}
		} catch (Exception e) {
		}
	}

	public static void moveOldImageToSD() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					File dirImage = new File(Constant.Path.IMAGE_FLASH);
					String[] listImage = dirImage.list();
					File fileTemp = null;
					for (int i = 0; i < listImage.length; i++) {
						fileTemp = new File(Constant.Path.IMAGE_FLASH
								+ File.separator + listImage[i]);
						if (fileTemp.isFile()
								&& !fileTemp.getName().startsWith(".")) {
							FileInputStream input = new FileInputStream(
									fileTemp);
							FileOutputStream output = new FileOutputStream(
									Constant.Path.IMAGE_SD + File.separator
											+ (fileTemp.getName()).toString());
							byte[] b = new byte[1024 * 5];
							int len;
							while ((len = input.read(b)) != -1) {
								output.write(b, 0, len);
							}
							output.flush();
							output.close();
							input.close();
							fileTemp.delete();
							MyLog.v("moveOldImageToSD:" + fileTemp.getName());
						}
					}
				} catch (Exception e) {
				}
			}

		}).start();
	}

	/** 删除Flash中的点文件 */
	public static void deleteFlashDotFile() {
		new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					if (!MyApp.isFrontRecording) {
						File dirFront = new File(
								Constant.Path.VIDEO_FRONT_FLASH);
						String[] listFront = dirFront.list();
						File fileTemp = null;
						for (int i = 0; i < listFront.length; i++) {
							fileTemp = new File(Constant.Path.VIDEO_FRONT_FLASH
									+ File.separator + listFront[i]);
							if (fileTemp.isFile()
									&& fileTemp.getName().startsWith(".")
									&& !MyApp.isFrontRecording) {
								fileTemp.delete();
								MyLog.v("deleteFlashDotFile:"
										+ fileTemp.getName());
							}
						}
					}
					if (!MyApp.isBackRecording) {
						File dirBack = new File(Constant.Path.VIDEO_BACK_FLASH);
						String[] listBack = dirBack.list();
						File fileTemp = null;
						for (int i = 0; i < listBack.length; i++) {
							fileTemp = new File(Constant.Path.VIDEO_BACK_FLASH
									+ File.separator + listBack[i]);
							if (fileTemp.isFile()
									&& fileTemp.getName().startsWith(".")
									&& !MyApp.isBackRecording) {
								fileTemp.delete();
								MyLog.v("deleteFlashDotFile:"
										+ fileTemp.getName());
							}
						}
					}
				} catch (Exception e) {
				}
			}

		}).start();
	}

	public static boolean copyFile(String oldFilePath, String newFilePath) {
		boolean isCopySuccess = true;
		try {
			File oldFile = new File(oldFilePath);
			File newFile = new File(newFilePath);
			if (!newFile.getParentFile().exists()) {
				newFile.getParentFile().mkdirs();
			}
			if (oldFile.exists() && oldFile.isFile()) {
				FileInputStream input = new FileInputStream(oldFile);
				FileOutputStream output = new FileOutputStream(newFile);
				byte[] b = new byte[1024 * 5];
				int len;
				while ((len = input.read(b)) != -1) {
					output.write(b, 0, len);
				}
				output.flush();
				output.close();
				input.close();
			}

			oldFile.delete(); // 删除内部存储文件
			// 更新Media Database
			// sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
			// Uri.parse("file://" + pathTo)));
		} catch (Exception e) {
			isCopySuccess = false;
		}
		return isCopySuccess;
	}
}
