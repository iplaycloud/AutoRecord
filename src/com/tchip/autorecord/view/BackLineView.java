package com.tchip.autorecord.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.view.View;

public class BackLineView extends View {

	public BackLineView(Context context) {
		super(context);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		super.onDraw(canvas);

		int[] point1 = { 270, 480 };
		int[] point2 = { 325, 380 };
		int[] point3 = { 375, 280 };
		int[] point4 = { 430, 180 };
		int[] point5 = { 750, 180 };
		int[] point6 = { 805, 280 };
		int[] point7 = { 855, 380 };
		int[] point8 = { 910, 480 };

		Paint paint = new Paint();
		paint.setStyle(Paint.Style.STROKE);// 空心
		paint.setAntiAlias(true); // 抗锯齿
		paint.setStrokeWidth(5); // 粗细
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
	}
}
