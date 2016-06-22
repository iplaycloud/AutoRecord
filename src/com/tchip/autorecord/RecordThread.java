package com.tchip.autorecord;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * 录像线程
 */
public class RecordThread extends Thread {

	private MediaRecorder mediarecorder;// 录制视频的类
	private SurfaceHolder surfaceHolder;
	private long recordTime;
	private SurfaceView surfaceView;// 显示视频的控件
	private Camera mCamera;
	private String file;
	private boolean sleep = false;

	public RecordThread(long recordTime, SurfaceView surfaceview,
			SurfaceHolder surfaceHolder, Camera camera, String file,
			boolean sleep) {
		this.recordTime = recordTime;
		this.surfaceView = surfaceview;
		this.surfaceHolder = surfaceHolder;
		this.mCamera = camera;
		this.file = file;
		this.sleep = sleep;
	}

	@Override
	public void run() {

		startRecord(); // 开始录像

		// 启动定时器，到规定时间recordTime后执行停止录像任务
		Timer timer = new Timer();
		timer.schedule(new TimerThread(), recordTime);
	}

	/**
	 * 开始录像
	 */
	public void startRecord() {
		if (sleep)
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
		mediarecorder = new MediaRecorder();// 创建mediarecorder对象
		mCamera.unlock(); // 解锁camera
		mediarecorder.setCamera(mCamera);

		// 设置录制视频源为Camera(相机)
		mediarecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
		mediarecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);

		// 设置录制文件质量，格式，分辨率之类，这个全部包括了
		mediarecorder.setProfile(CamcorderProfile
				.get(CamcorderProfile.QUALITY_LOW));

		mediarecorder.setPreviewDisplay(surfaceHolder.getSurface());
		mediarecorder.setOutputFile(file); // 设置视频文件输出的路径
		try {
			mediarecorder.prepare(); // 准备录制
			mediarecorder.start(); // 开始录制
		} catch (IllegalStateException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * 停止录制
	 */
	public void stopRecord() {
		if (mediarecorder != null) {
			mediarecorder.stop(); // 停止录制
			mediarecorder.release(); // 释放资源
			mediarecorder = null;

			if (mCamera != null) {
				mCamera.release();
				mCamera = null;
			}
		}
	}

	/**
	 * 定时器
	 */
	class TimerThread extends TimerTask {

		@Override
		public void run() {
			stopRecord(); // 停止录像
			this.cancel();
		}
	}
}
