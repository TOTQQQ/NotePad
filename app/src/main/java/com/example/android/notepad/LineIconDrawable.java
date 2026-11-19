package com.example.android.notepad;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** Draws the small, consistent line icons used by the two note screens. */
public class LineIconDrawable extends Drawable {
    public static final int NOTE = 0;
    public static final int SEARCH = 1;
    public static final int ADD = 2;
    public static final int SAVE = 3;
    public static final int DELETE = 4;
    public static final int MORE = 5;
    public static final int TODO = 6;
    public static final int REVERT = 7;
    public static final int CHECK = 8;
    public static final int TODO_ON = 9;
    public static final int TODO_OFF = 10;
    public static final int CLEAR = 11;
    public static final int PIN = 12;
    public static final int SHARE = 13;
    public static final int BACK = 14;

    private final int mType;
    private final int mColor;
    private final int mIntrinsicSize;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mPath = new Path();
    private int mAlpha = 255;
    private ColorFilter mColorFilter;

    public LineIconDrawable(Context context, int type, int color) {
        mType = type;
        mColor = color;
        mIntrinsicSize = Math.round(24 * context.getResources().getDisplayMetrics().density);
        mPaint.setStrokeCap(Paint.Cap.ROUND);
        mPaint.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    public void draw(Canvas canvas) {
        if (getBounds().isEmpty()) {
            return;
        }

        canvas.save();
        canvas.translate(getBounds().left, getBounds().top);
        canvas.scale(getBounds().width() / 24f, getBounds().height() / 24f);
        prepareStroke(mColor, 1.8f);

        switch (mType) {
            case NOTE:
                canvas.drawRoundRect(new RectF(5, 3, 19, 21), 2, 2, mPaint);
                drawLine(8, 8, 16, 8, canvas);
                drawLine(8, 12, 16, 12, canvas);
                drawLine(8, 16, 14, 16, canvas);
                break;
            case SEARCH:
                canvas.drawCircle(10.5f, 10.5f, 6.5f, mPaint);
                drawLine(15.2f, 15.2f, 21, 21, canvas);
                break;
            case ADD:
                drawLine(12, 5, 12, 19, canvas);
                drawLine(5, 12, 19, 12, canvas);
                break;
            case SAVE:
                mPath.reset();
                mPath.moveTo(5, 3);
                mPath.lineTo(17, 3);
                mPath.lineTo(21, 7);
                mPath.lineTo(21, 21);
                mPath.lineTo(5, 21);
                mPath.close();
                canvas.drawPath(mPath, mPaint);
                mPath.reset();
                mPath.moveTo(8, 3);
                mPath.lineTo(8, 9);
                mPath.lineTo(16, 9);
                mPath.lineTo(16, 3);
                canvas.drawPath(mPath, mPaint);
                drawLine(8, 16, 16, 16, canvas);
                break;
            case DELETE:
                drawLine(4, 7, 20, 7, canvas);
                mPath.reset();
                mPath.moveTo(9, 7);
                mPath.lineTo(9, 4);
                mPath.lineTo(15, 4);
                mPath.lineTo(15, 7);
                mPath.moveTo(6, 7);
                mPath.lineTo(7, 21);
                mPath.lineTo(17, 21);
                mPath.lineTo(18, 7);
                mPath.moveTo(10, 11);
                mPath.lineTo(10, 17);
                mPath.moveTo(14, 11);
                mPath.lineTo(14, 17);
                canvas.drawPath(mPath, mPaint);
                break;
            case MORE:
                prepareFill(mColor);
                canvas.drawCircle(5, 12, 1.4f, mPaint);
                canvas.drawCircle(12, 12, 1.4f, mPaint);
                canvas.drawCircle(19, 12, 1.4f, mPaint);
                break;
            case TODO:
                canvas.drawRoundRect(new RectF(4, 3, 20, 21), 2, 2, mPaint);
                drawCheck(canvas, mColor, 7, 11, 10, 14, 16, 8);
                drawLine(8, 18, 16, 18, canvas);
                break;
            case REVERT:
                canvas.drawArc(new RectF(5, 5, 19, 19), -65, 290, false, mPaint);
                mPath.reset();
                mPath.moveTo(5, 10);
                mPath.lineTo(5, 4);
                mPath.lineTo(11, 4);
                canvas.drawPath(mPath, mPaint);
                break;
            case CHECK:
                drawCheck(canvas, mColor, 5, 12, 10, 17, 19, 7);
                break;
            case TODO_ON:
                prepareFill(mColor);
                canvas.drawCircle(12, 12, 10, mPaint);
                drawCheck(canvas, Color.WHITE, 7, 12, 10.5f, 15.5f, 17, 8.5f);
                break;
            case TODO_OFF:
                prepareStroke(mColor, 1.6f);
                canvas.drawCircle(12, 12, 9, mPaint);
                break;
            case CLEAR:
                drawLine(6, 6, 18, 18, canvas);
                drawLine(18, 6, 6, 18, canvas);
                break;
            case PIN:
                mPath.reset();
                mPath.moveTo(8, 3);
                mPath.lineTo(16, 3);
                mPath.lineTo(16, 8);
                mPath.lineTo(19, 12);
                mPath.lineTo(5, 12);
                mPath.lineTo(8, 8);
                mPath.close();
                canvas.drawPath(mPath, mPaint);
                drawLine(12, 12, 12, 21, canvas);
                break;
            case SHARE:
                canvas.drawCircle(17, 5, 3, mPaint);
                canvas.drawCircle(7, 12, 3, mPaint);
                canvas.drawCircle(17, 19, 3, mPaint);
                drawLine(9.5f, 10.5f, 14.5f, 6.5f, canvas);
                drawLine(9.5f, 13.5f, 14.5f, 17.5f, canvas);
                break;
            case BACK:
                drawLine(19, 12, 5, 12, canvas);
                drawLine(5, 12, 11, 6, canvas);
                drawLine(5, 12, 11, 18, canvas);
                break;
            default:
                break;
        }
        canvas.restore();
    }

    private void drawLine(float startX, float startY, float stopX, float stopY, Canvas canvas) {
        canvas.drawLine(startX, startY, stopX, stopY, mPaint);
    }

    private void drawCheck(Canvas canvas, int color, float startX, float startY,
            float middleX, float middleY, float endX, float endY) {
        prepareStroke(color, 2f);
        mPath.reset();
        mPath.moveTo(startX, startY);
        mPath.lineTo(middleX, middleY);
        mPath.lineTo(endX, endY);
        canvas.drawPath(mPath, mPaint);
    }

    private void prepareStroke(int color, float width) {
        mPaint.setStyle(Paint.Style.STROKE);
        mPaint.setStrokeWidth(width);
        mPaint.setColor(color);
        mPaint.setAlpha(mAlpha);
        mPaint.setColorFilter(mColorFilter);
    }

    private void prepareFill(int color) {
        mPaint.setStyle(Paint.Style.FILL);
        mPaint.setColor(color);
        mPaint.setAlpha(mAlpha);
        mPaint.setColorFilter(mColorFilter);
    }

    @Override
    public void setAlpha(int alpha) {
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mColorFilter = colorFilter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public int getIntrinsicWidth() {
        return mIntrinsicSize;
    }

    @Override
    public int getIntrinsicHeight() {
        return mIntrinsicSize;
    }
}
