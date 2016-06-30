package com.tchip.autorecord.view;

import com.tchip.autorecord.R;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.view.MotionEvent;
import android.view.View;

public class BackLineView extends View {

	private Context context;
	private SharedPreferences sharedPreferences;
	private Editor editor;
	int[] point1 = { 270, 480 };
	int[] point2 = { 325, 380 };
	int[] point3 = { 375, 280 };
	int[] point4 = { 430, 180 };
	int[] point5 = { 750, 180 };
	int[] point6 = { 805, 280 };
	int[] point7 = { 855, 380 };
	int[] point8 = { 910, 480 };

	/** 线短粗细 */
	private int LINE_WIDTH = 8;
	/** 编辑指示图半径 */
	private int POINT_HINT_RADIUS = 25;
	/** 是否是编辑模式 */
	private boolean isModifyMode = false;
	/** 手指是否按下 */
	private boolean isTouchDown = false;
	/** 当前正在编辑的点 */
	private int nowModifyPoint = 0;

	public BackLineView(Context context) {
		super(context);
		this.context = context;
		sharedPreferences = context.getSharedPreferences("BackLine",
				Context.MODE_PRIVATE);
		editor = sharedPreferences.edit();
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		loadPointConfig();
		Paint paint = new Paint();
		paint.setStyle(Paint.Style.STROKE);// 空心
		paint.setAntiAlias(true); // 抗锯齿
		paint.setStrokeWidth(LINE_WIDTH); // 粗细
		Path path = new Path();

		// Line 3-4 5-6
		paint.setColor(Color.GREEN);
		path.moveTo(point3[0], point3[1]); // 3
		path.lineTo(point4[0], point4[1]); // 4
		path.moveTo(point5[0], point5[1]); // 5
		path.lineTo(point6[0], point6[1]); // 6
		canvas.drawPath(path, paint);

		// Line 2-3 6-7
		paint.setColor(Color.YELLOW);
		path.reset();
		path.moveTo(point2[0], point2[1]); // 2
		path.lineTo(point3[0], point3[1]); // 3
		path.moveTo(point6[0], point6[1]); // 6
		path.lineTo(point7[0], point7[1]); // 7
		canvas.drawPath(path, paint);

		// Line 1-2 7-8
		paint.setColor(Color.RED);
		path.reset();
		path.moveTo(point1[0], point1[1]); // 1
		path.lineTo(point2[0], point2[1]); // 2
		path.moveTo(point7[0], point7[1]); // 7
		path.lineTo(point8[0], point8[1]); // 8
		canvas.drawPath(path, paint);

		PathEffect effect = new DashPathEffect(new float[] { 14, 14, 14, 14 },
				1); // {实线,空白,实线,空白:偶数}
		paint.setPathEffect(effect);
		// 4--5
		path.reset();
		paint.setColor(Color.GREEN);
		path.moveTo(point4[0], point4[1]); // 4
		path.lineTo(point5[0], point5[1]); // 5
		canvas.drawPath(path, paint);
		// 3--6
		path.reset();
		paint.setColor(Color.YELLOW);
		path.moveTo(point3[0], point3[1]); // 3
		path.lineTo(point6[0], point6[1]); // 6
		canvas.drawPath(path, paint);
		// 2--7
		path.reset();
		paint.setColor(Color.RED);
		path.moveTo(point2[0], point2[1]); // 2
		path.lineTo(point7[0], point7[1]); // 7
		canvas.drawPath(path, paint);

		if (isModifyMode) { // 绘制指示点
			Bitmap bitmap = BitmapFactory.decodeResource(getResources(),
					R.drawable.back_line_point);
			canvas.drawBitmap(bitmap, point1[0] - POINT_HINT_RADIUS, point1[1]
					- POINT_HINT_RADIUS, paint);
			canvas.drawBitmap(bitmap, point2[0] - POINT_HINT_RADIUS, point2[1]
					- POINT_HINT_RADIUS, paint);
			canvas.drawBitmap(bitmap, point3[0] - POINT_HINT_RADIUS, point3[1]
					- POINT_HINT_RADIUS, paint);
			canvas.drawBitmap(bitmap, point4[0] - POINT_HINT_RADIUS, point4[1]
					- POINT_HINT_RADIUS, paint);
			canvas.drawBitmap(bitmap, point5[0] - POINT_HINT_RADIUS, point5[1]
					- POINT_HINT_RADIUS, paint);
			canvas.drawBitmap(bitmap, point6[0] - POINT_HINT_RADIUS, point6[1]
					- POINT_HINT_RADIUS, paint);
			canvas.drawBitmap(bitmap, point7[0] - POINT_HINT_RADIUS, point7[1]
					- POINT_HINT_RADIUS, paint);
			canvas.drawBitmap(bitmap, point8[0] - POINT_HINT_RADIUS, point8[1]
					- POINT_HINT_RADIUS, paint);
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		float x = event.getX();
		float y = event.getY();

		switch (event.getAction()) {
		case MotionEvent.ACTION_DOWN:
			isTouchDown = true;
			nowModifyPoint = whichPoint(x, y);
			break;

		case MotionEvent.ACTION_MOVE:
			if (isModifyMode && isTouchDown && nowModifyPoint != 0) {
				setPointLocation(nowModifyPoint, (int) x, (int) y);
			}
			break;

		case MotionEvent.ACTION_UP:
			isTouchDown = false;
			break;
		}
		return true;
	}

	public void setModifyMode(boolean isModifyMode) {
		this.isModifyMode = isModifyMode;
	}

	public boolean getModifyMode() {
		return isModifyMode;
	}

	/** 从SP加载点坐标 */
	private void loadPointConfig() {
		point1[0] = sharedPreferences.getInt("point1x", point1[0]);
		point1[1] = sharedPreferences.getInt("point1y", point1[1]);
		point4[0] = sharedPreferences.getInt("point4x", point4[0]);
		point4[1] = sharedPreferences.getInt("point4y", point4[1]);

		point5[0] = sharedPreferences.getInt("point5x", point5[0]);
		point5[1] = sharedPreferences.getInt("point5y", point5[1]);
		point8[0] = sharedPreferences.getInt("point8x", point8[0]);
		point8[1] = sharedPreferences.getInt("point8y", point8[1]);

		point2[1] = sharedPreferences.getInt("point2y", point2[1]);
		point2[0] = (point2[1] - point1[1]) * (point1[0] - point4[0])
				/ (point1[1] - point4[1]) + point1[0];

		point3[1] = sharedPreferences.getInt("point3y", point3[1]);
		point3[0] = (point3[1] - point1[1]) * (point1[0] - point4[0])
				/ (point1[1] - point4[1]) + point1[0];

		point6[1] = sharedPreferences.getInt("point6y", point6[1]);
		point6[0] = (point6[1] - point5[1]) * (point5[0] - point8[0])
				/ (point5[1] - point8[1]) + point5[0];

		point7[1] = sharedPreferences.getInt("point7y", point7[1]);
		point7[0] = (point7[1] - point5[1]) * (point5[0] - point8[0])
				/ (point5[1] - point8[1]) + point5[0];
	}

	public void clearPonitConfig() {
		editor.putInt("point1x", 270);
		editor.putInt("point1y", 480);
		editor.putInt("point2x", 325);
		editor.putInt("point2y", 380);
		editor.putInt("point3x", 375);
		editor.putInt("point3y", 280);
		editor.putInt("point4x", 430);
		editor.putInt("point4y", 180);
		editor.putInt("point5x", 750);
		editor.putInt("point5y", 180);
		editor.putInt("point6x", 805);
		editor.putInt("point6y", 280);
		editor.putInt("point7x", 855);
		editor.putInt("point7y", 380);
		editor.putInt("point8x", 910);
		editor.putInt("point8y", 480);
		editor.commit();
	}

	private void setPointLocation(int point, int x, int y) {
		if (x < 150) { // x:0-1184
			x = 150;
		} else if (x > 1034) {
			x = 1034;
		}
		if (y < 50) { // y:0-480
			y = 50;
		}
		switch (point) {
		case 1:
			if (x < point8[0] - POINT_HINT_RADIUS * 2) {
				point1[0] = x;
				editor.putInt("point1x", x);
			} else {
				point1[0] = point8[0] - POINT_HINT_RADIUS * 2;
				editor.putInt("point1x", point8[0] - POINT_HINT_RADIUS * 2);
			}
			if (y > 450) {
				point1[1] = y;
				editor.putInt("point1y", y);
			} else {
				point1[1] = 450;
				editor.putInt("point1y", 450);
			}
			break;

		case 2:
			if (y > point1[1] - POINT_HINT_RADIUS * 2) {
				point2[0] = x;
				point2[1] = point1[1] - POINT_HINT_RADIUS * 2;
				editor.putInt("point2x", x);
				editor.putInt("point2y", point1[1] - POINT_HINT_RADIUS * 2);
			} else if (y > point3[1] + POINT_HINT_RADIUS * 2 && y < point1[1]) {
				point2[0] = x;
				point2[1] = y;
				editor.putInt("point2x", x);
				editor.putInt("point2y", y);
			} else {
				point2[0] = x;
				point2[1] = point3[1] + POINT_HINT_RADIUS * 2;
				editor.putInt("point2x", x);
				editor.putInt("point2y", point3[1] + POINT_HINT_RADIUS * 2);
			}
			break;

		case 3:
			if (y < point4[1] + POINT_HINT_RADIUS * 2) {
				point3[0] = x;
				point3[1] = point4[1] + POINT_HINT_RADIUS * 2;
				editor.putInt("point3x", x);
				editor.putInt("point3y", point4[1] + POINT_HINT_RADIUS * 2);
			} else if (y > point2[1] - POINT_HINT_RADIUS * 2) {
				point3[0] = x;
				point3[1] = point2[1] - POINT_HINT_RADIUS * 2;
				editor.putInt("point3x", x);
				editor.putInt("point3y", point2[1] - POINT_HINT_RADIUS * 2);
			} else {
				point3[0] = x;
				point3[1] = y;
				editor.putInt("point3x", x);
				editor.putInt("point3y", y);
			}
			break;

		case 4:
			if (y > point3[1] - POINT_HINT_RADIUS * 2) {
				point4[1] = point3[1] - POINT_HINT_RADIUS * 2;
				editor.putInt("point4y", point3[1] - POINT_HINT_RADIUS * 2);
			} else {
				point4[1] = y;
				editor.putInt("point4y", y);
			}

			if (x > point5[0] - POINT_HINT_RADIUS * 2) {
				point4[0] = x;
				editor.putInt("point4x", point5[0] - POINT_HINT_RADIUS * 2);
			} else {
				point4[0] = x;
				editor.putInt("point4x", x);
			}
			break;

		case 5:
			if (y > point6[1] - POINT_HINT_RADIUS * 2) {
				point5[1] = point6[1] - POINT_HINT_RADIUS * 2;
				editor.putInt("point5y", point6[1] - POINT_HINT_RADIUS * 2);
			} else {
				point5[1] = y;
				editor.putInt("point5y", y);
			}

			if (x < point4[0] + POINT_HINT_RADIUS * 2) {
				point5[0] = x;
				editor.putInt("point5x", point4[0] + POINT_HINT_RADIUS * 2);
			} else {
				point5[0] = x;
				editor.putInt("point5x", x);
			}

			break;

		case 6:
			if (y < point5[1] + POINT_HINT_RADIUS * 2) {
				point6[0] = x;
				point6[1] = point5[1] + POINT_HINT_RADIUS * 2;
				editor.putInt("point6x", x);
				editor.putInt("point6y", point5[1] + POINT_HINT_RADIUS * 2);
			} else if (y > point7[1] - POINT_HINT_RADIUS * 2) {
				point6[0] = x;
				point6[1] = point7[1] - POINT_HINT_RADIUS * 2;
				editor.putInt("point6x", x);
				editor.putInt("point6y", point7[1] - POINT_HINT_RADIUS * 2);
			} else {
				point6[0] = x;
				point6[1] = y;
				editor.putInt("point6x", x);
				editor.putInt("point6y", y);
			}
			break;

		case 7:
			if (y > point8[1] - POINT_HINT_RADIUS * 2) {
				point7[0] = x;
				point7[1] = point8[1] - POINT_HINT_RADIUS * 2;
				editor.putInt("point7x", x);
				editor.putInt("point7y", point8[1] - POINT_HINT_RADIUS * 2);
			} else if (y > point6[1] + POINT_HINT_RADIUS * 2 && y < point8[1]) {
				point7[0] = x;
				point7[1] = y;
				editor.putInt("point7x", x);
				editor.putInt("point7y", y);
			} else {
				point7[0] = x;
				point7[1] = point6[1] + POINT_HINT_RADIUS * 2;
				editor.putInt("point7x", x);
				editor.putInt("point7y", point6[1] + POINT_HINT_RADIUS * 2);
			}
			break;

		case 8:
			if (x > point1[0] + POINT_HINT_RADIUS * 2) {
				point8[0] = x;
				editor.putInt("point8x", x);
			} else {
				point8[0] = point1[0] + POINT_HINT_RADIUS * 2;
				editor.putInt("point8x", point1[0] + POINT_HINT_RADIUS * 2);
			}
			if (y > 450) {
				point8[1] = y;
				editor.putInt("point8y", y);
			} else {
				point8[1] = 450;
				editor.putInt("point8y", 450);
			}
			break;

		default:
			break;
		}
		editor.commit();
		postInvalidate();
	}

	private int SPAN_ZONE = 25;

	private int whichPoint(float x, float y) {
		if (x - SPAN_ZONE < point1[0] && point1[0] < x + SPAN_ZONE
				&& y - SPAN_ZONE < point1[1] && point1[1] < y + SPAN_ZONE) {
			return 1;
		} else if (x - SPAN_ZONE < point2[0] && point2[0] < x + SPAN_ZONE
				&& y - SPAN_ZONE < point2[1] && point2[1] < y + SPAN_ZONE) {
			return 2;
		} else if (x - SPAN_ZONE < point3[0] && point3[0] < x + SPAN_ZONE
				&& y - SPAN_ZONE < point3[1] && point3[1] < y + SPAN_ZONE) {
			return 3;
		} else if (x - SPAN_ZONE < point4[0] && point4[0] < x + SPAN_ZONE
				&& y - SPAN_ZONE < point4[1] && point4[1] < y + SPAN_ZONE) {
			return 4;
		} else if (x - SPAN_ZONE < point5[0] && point5[0] < x + SPAN_ZONE
				&& y - SPAN_ZONE < point5[1] && point5[1] < y + SPAN_ZONE) {
			return 5;
		} else if (x - SPAN_ZONE < point6[0] && point6[0] < x + SPAN_ZONE
				&& y - SPAN_ZONE < point6[1] && point6[1] < y + SPAN_ZONE) {
			return 6;
		} else if (x - SPAN_ZONE < point7[0] && point7[0] < x + SPAN_ZONE
				&& y - SPAN_ZONE < point7[1] && point7[1] < y + SPAN_ZONE) {
			return 7;
		} else if (x - SPAN_ZONE < point8[0] && point8[0] < x + SPAN_ZONE
				&& y - SPAN_ZONE < point8[1] && point8[1] < y + SPAN_ZONE) {
			return 8;
		} else
			return 0;
	}
}
