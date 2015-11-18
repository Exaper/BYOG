package com.exaper.byog;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import java.util.UUID;

public class VertexView extends View {
    public enum VertexType {
        INTERMEDIATE,
        START,
        END
    }

    private final String mId;
    private final Paint mAreaPaint;
    private final VertexType mType;
    private final int mSize;
    private final float mHighlightMultiplier;
    private final int mOutlineColor;

    private boolean mTemporary;

    public VertexView(Context context) {
        this(context, null);
    }

    public VertexView(Context context, AttributeSet attrs) {
        this(context, attrs, R.style.VertexViewStyle);
    }

    public VertexView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.VertexView, 0, R.style.VertexViewStyle);
        mAreaPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        mOutlineColor = typedArray.getColor(R.styleable.VertexView_outlineColor, Color.WHITE);
        mAreaPaint.setColor(mOutlineColor);
        mType = VertexType.values()[typedArray.getInt(R.styleable.VertexView_vertexType,
                VertexType.INTERMEDIATE.ordinal())];
        mSize = typedArray.getDimensionPixelSize(R.styleable.VertexView_size, 0);
        mHighlightMultiplier = typedArray.getFloat(R.styleable.VertexView_highlightSizeMultiplier, 1);
        typedArray.recycle();
        mId = UUID.randomUUID().toString();
    }

    public String getVertexId() {
        return mId;
    }

    public VertexType getVertexType() {
        return mType;
    }

    public void setTemporary(boolean temporary) {
        if (mTemporary != temporary) {
            mTemporary = temporary;
            if (temporary) {
                setScaleX(mHighlightMultiplier);
                setScaleY(mHighlightMultiplier);
            } else {
                setScaleX(1);
                setScaleY(1);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        mAreaPaint.setAlpha(mTemporary ? 128 : 255);
        // Vertex: 1/3 circle at the center + 1/3 outline.
        int centerX = getWidth() >> 1, centerY = getHeight() >> 1;
        int radius = centerX > centerY ? centerX : centerY;
        mAreaPaint.setStyle(Paint.Style.STROKE);
        mAreaPaint.setStrokeWidth(radius / 3);
        canvas.drawCircle(centerX, centerY, radius - radius / 6 - 1, mAreaPaint);
        mAreaPaint.setStyle(Paint.Style.FILL);
        canvas.drawCircle(centerX, centerY, radius / 3 - 1, mAreaPaint);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(mSize, mSize);
    }
}
