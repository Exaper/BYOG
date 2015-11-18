package com.wybe.graph;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.FrameLayout;

public class GraphLayout extends FrameLayout{
    public GraphLayout(Context context) {
        this(context, null);
    }

    public GraphLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GraphLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

    }
}
