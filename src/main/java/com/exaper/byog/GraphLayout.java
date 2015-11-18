package com.exaper.byog;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.ViewGroup;

import org.jgrapht.alg.DijkstraShortestPath;
import org.jgrapht.graph.SimpleWeightedGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class GraphLayout extends ViewGroup {
    private static final long LONG_PRESS_TIMEOUT = ViewConfiguration.getLongPressTimeout();
    private final Rect mTempRect = new Rect();
    private final Paint mConnectorPaint;
    private final Paint mShortestPathConnectorPaint;
    // Used to differentiate move from long press. Squared distance user has to move their finger to confirm
    // move vs long press.
    private final int mTouchSlop;
    private VertexView mTouchDownView;
    private int mTouchDownX;
    private int mTouchDownY;
    private CheckForLongPress mPendingCheckForLongPress;
    private List<Connector> mConnectors;
    private Connector mPendingConnector;
    private List<Edge> mShortestPath;

    public GraphLayout(Context context) {
        this(context, null);
    }

    public GraphLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public GraphLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.GraphLayout, 0, R.style.GraphLayoutStyle);
        mConnectorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mConnectorPaint.setColor(typedArray.getColor(R.styleable.GraphLayout_edgeColor, Color.WHITE));
        mConnectorPaint.setStrokeWidth(typedArray.getDimensionPixelSize(R.styleable.GraphLayout_edgeWidth, 1));
        mShortestPathConnectorPaint = new Paint(mConnectorPaint);
        mShortestPathConnectorPaint.setColor(typedArray.getColor(R.styleable.GraphLayout_shortestPathEdgeColor,
                mShortestPathConnectorPaint.getColor()));
        typedArray.recycle();
        setWillNotDraw(false);
        mConnectors = new ArrayList<>();
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean handled;
        final int actionMasked = event.getActionMasked();
        switch (actionMasked) {
            case MotionEvent.ACTION_DOWN:
                handled = onTouchDown((int) event.getX(), (int) event.getY());
                break;
            case MotionEvent.ACTION_MOVE:
                handled = onTouchMove(event);
                break;
            case MotionEvent.ACTION_UP:
                handled = onTouchUp(event);
                break;
            case MotionEvent.ACTION_CANCEL:
                handled = onTouchCancel(event);
                break;
            default:
                handled = false;

        }
        return handled;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
            VertexView child = (VertexView) getChildAt(i);
            int childWidth = child.getMeasuredWidth();
            int childHeight = child.getMeasuredHeight();
            GraphLayoutParams lp = resolveLayoutParamsForVertex(child);
            child.setLayoutParams(lp);
            child.layout(lp.x - (childWidth >> 1), lp.y - (childHeight >> 1),
                    lp.x + (childWidth >> 1), lp.y + (childHeight >> 1));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
            getChildAt(i).measure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        for (Connector connector : mConnectors) {
            Paint connectorPaint = mConnectorPaint;
            if (mShortestPath != null) {
                for (int i = 0, length = mShortestPath.size(); i < length; i++) {
                    Edge edge = mShortestPath.get(i);
                    if (connector.connects(edge.getSource(), edge.getTarget())) {
                        connectorPaint = mShortestPathConnectorPaint;
                        break;
                    }
                }
            }
            drawConnector(canvas, connector, connectorPaint);
        }
        if (mPendingConnector != null) {
            mConnectorPaint.setAlpha(100);
            drawConnector(canvas, mPendingConnector, mConnectorPaint);
            mConnectorPaint.setAlpha(255);
        }
        super.onDraw(canvas);
    }

    private static void drawConnector(Canvas canvas, Connector connector, Paint paint) {
        float startX = connector.v1.getX() + (connector.v1.getWidth() >> 1);
        float startY = connector.v1.getY() + (connector.v1.getHeight() >> 1);
        float endX = connector.v2.getX() + (connector.v2.getWidth() >> 1);
        float endY = connector.v2.getY() + (connector.v2.getHeight() >> 1);
        canvas.drawLine(startX, startY, endX, endY, paint);
    }

    private boolean onTouchDown(int x, int y) {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            VertexView child = (VertexView) getChildAt(i);
            child.getHitRect(mTempRect);
            if (mTempRect.contains(x, y)) {
                mTouchDownView = child;
                mTouchDownX = x;
                mTouchDownY = y;
                // User wants to move the vertex or initiate adding a new one (via the long press).
                startWaitingForLongPress();
                break;
            }
        }
        return mTouchDownView != null;
    }

    private boolean onTouchMove(MotionEvent event) {
        boolean handled = false;
        if (mTouchDownView != null) {
            float dX = event.getX() - mTouchDownX, dY = event.getY() - mTouchDownY;
            if (mPendingCheckForLongPress == null || Math.hypot(dX, dY) >= mTouchSlop) {
                mTouchDownView.setTranslationX(dX);
                mTouchDownView.setTranslationY(dY);
                updateShortestPath();
                // TODO Invalidate only area that needs to be redrawn.
                invalidate();
                stopWaitingForLongPress();
            }
            handled = true;
        }
        return handled;
    }

    private boolean onTouchUp(MotionEvent event) {
        boolean handled = false;
        if (mTouchDownView != null) {
            int newX = (int) mTouchDownView.getX() + mTouchDownView.getWidth() / 2;
            int newY = (int) mTouchDownView.getY() + mTouchDownView.getHeight() / 2;
            GraphLayoutParams lp = (GraphLayoutParams) mTouchDownView.getLayoutParams();
            lp.x = newX;
            lp.y = newY;
            mTouchDownView.setTemporary(false);

            // Merging mTouchDownView with other Vertex in case if it was dropped over it.
            Rect vertexRect = new Rect();
            mTouchDownView.getHitRect(vertexRect);
            for (int i = 0, childCount = getChildCount(); i < childCount; i++) {
                VertexView child = (VertexView) getChildAt(i);
                if (mTouchDownView != child) {
                    child.getHitRect(mTempRect);
                    if (mTempRect.intersect(vertexRect)) {
                        // Disallow swallowing start and end vertices!
                        if (mTouchDownView.getVertexType() != VertexView.VertexType.INTERMEDIATE) {
                            mergeVertices(mTouchDownView, child);
                        } else {
                            mergeVertices(child, mTouchDownView);
                        }
                        if (mPendingConnector != null) {
                            mPendingConnector = new Connector(mPendingConnector.v1, child);
                        }
                        break;
                    }
                }
            }

            if (mPendingConnector != null) {
                if (!mConnectors.contains(mPendingConnector) && !mPendingConnector.isCyclic()) {
                    mConnectors.add(mPendingConnector);
                }
                mPendingConnector = null;
            }
            updateShortestPath();
            requestLayout();
            handled = true;
        }
        completeTouchHandling();
        return handled;
    }

    private boolean onTouchCancel(MotionEvent event) {
        if (mTouchDownView != null && mPendingConnector != null) {
            // We were trying to add new vertex but event was cancelled.
            mPendingConnector = null;
            removeView(mTouchDownView);
        }
        completeTouchHandling();
        return true;
    }

    private void completeTouchHandling() {
        stopWaitingForLongPress();
        if (mTouchDownView != null) {
            mTouchDownView.setTranslationX(0);
            mTouchDownView.setTranslationY(0);
            mTouchDownX = mTouchDownY = 0;
            mTouchDownView = null;
        }
    }

    private void startNewVertex(VertexView parent) {
        VertexView newVertex = new VertexView(getContext());
        newVertex.setTemporary(true);
        GraphLayoutParams lp = new GraphLayoutParams(parent.getLayoutParams());
        lp.x = mTouchDownX;
        lp.y = mTouchDownY;
        newVertex.setLayoutParams(lp);
        addView(newVertex);
        mPendingConnector = new Connector(mTouchDownView, newVertex);
        mTouchDownView = newVertex;
    }

    private void mergeVertices(VertexView absorber, VertexView victim) {
        List<Connector> newConnectors = new ArrayList<>();
        Iterator<Connector> connectorsIterator = mConnectors.iterator();
        while (connectorsIterator.hasNext()) {
            Connector connector = connectorsIterator.next();
            if (connector.v1 == victim && connector.v2 == absorber) {
                connectorsIterator.remove();
            } else if (connector.v2 == victim && connector.v1 == absorber) {
                connectorsIterator.remove();
            } else if (connector.v1 == victim) {
                connectorsIterator.remove();
                newConnectors.add(new Connector(absorber, connector.v2));
            } else if (connector.v2 == victim) {
                connectorsIterator.remove();
                newConnectors.add(new Connector(connector.v1, absorber));
            }
        }
        for (Connector connector : newConnectors) {
            if (!mConnectors.contains(connector) && !connector.isCyclic()) {
                mConnectors.add(connector);
            }
        }
        removeView(victim);
    }

    private void startWaitingForLongPress() {
        mPendingCheckForLongPress = new CheckForLongPress();
        postDelayed(mPendingCheckForLongPress, LONG_PRESS_TIMEOUT);
    }

    private void stopWaitingForLongPress() {
        if (mPendingCheckForLongPress != null) {
            removeCallbacks(mPendingCheckForLongPress);
            mPendingCheckForLongPress = null;
        }
    }

    private GraphLayoutParams resolveLayoutParamsForVertex(VertexView view) {
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (!(lp instanceof GraphLayoutParams)) {
            GraphLayoutParams graphLayoutParams = new GraphLayoutParams(lp);
            switch (view.getVertexType()) {
                case START:
                    graphLayoutParams.x = view.getMeasuredWidth() / 2;
                    graphLayoutParams.y = getMeasuredHeight() / 2;
                    break;
                case END:
                    graphLayoutParams.x = getMeasuredWidth() - view.getMeasuredWidth() / 2;
                    graphLayoutParams.y = getMeasuredHeight() / 2;
                    break;
                case INTERMEDIATE:
                default:
                    throw new IllegalStateException("Unable to resolve layout params for view " + view + " with type " +
                            view.getVertexType());
            }
            lp = graphLayoutParams;
        }
        return (GraphLayoutParams) lp;
    }

    /**
     * TODO Consider reusing existing graph and updating weights instead of recreating it.
     */
    private void updateShortestPath() {
        mShortestPath = null;
        Vertex start = null, end = null;
        Map<VertexView, Vertex> verticesMap = new HashMap<>();
        SimpleWeightedGraph<Vertex, Edge> graph = new SimpleWeightedGraph(Edge.class);
        for (Connector connector : mConnectors) {
            Vertex vertex1 = verticesMap.get(connector.v1);
            if (vertex1 == null) {
                vertex1 = new Vertex(connector.v1.getVertexId());
                verticesMap.put(connector.v1, vertex1);
            }
            switch (connector.v1.getVertexType()) {
                case START:
                    start = vertex1;
                    break;
                case END:
                    end = vertex1;
                    break;
            }

            Vertex vertex2 = verticesMap.get(connector.v2);
            if (vertex2 == null) {
                vertex2 = new Vertex(connector.v2.getVertexId());
                verticesMap.put(connector.v2, vertex2);
            }
            switch (connector.v2.getVertexType()) {
                case START:
                    start = vertex2;
                    break;
                case END:
                    end = vertex2;
                    break;
            }
            float edgeLength = (float) Math.hypot(connector.v1.getX() - connector.v2.getX(),
                    connector.v1.getY() - connector.v2.getY());
            graph.addVertex(vertex1);
            graph.addVertex(vertex2);
            Edge edge = graph.addEdge(vertex1, vertex2);
            graph.setEdgeWeight(edge, edgeLength);
        }

        if (start != null && end != null) {
            mShortestPath = DijkstraShortestPath.findPathBetween(graph, start, end);
        }
    }

    private final class CheckForLongPress implements Runnable {
        @Override
        public void run() {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            mPendingCheckForLongPress = null;
            startNewVertex(mTouchDownView);
        }
    }

    private static final class GraphLayoutParams extends ViewGroup.LayoutParams {
        public int x, y;

        public GraphLayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            if (source instanceof GraphLayoutParams) {
                GraphLayoutParams lp = (GraphLayoutParams) source;
                x = lp.x;
                y = lp.y;
            }
        }
    }

    private static final class Connector {
        public final VertexView v1;
        public final VertexView v2;

        public Connector(VertexView v1, VertexView v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        @Override
        public boolean equals(Object o) {
            Connector other = (Connector) o;
            return this == other || (v1 == other.v1 && v2 == other.v2) || (v1 == other.v2 && v2 == other.v1);
        }

        public boolean isCyclic() {
            return v1 == v2;
        }

        public boolean connects(Vertex vertex1, Vertex vertex2) {
            String v1Id = v1.getVertexId();
            String v2Id = v2.getVertexId();
            return (vertex1.getId().equals(v1Id) && vertex2.getId().equals(v2Id)) ||
                    (vertex1.getId().equals(v2Id) && vertex2.getId().equals(v1Id));
        }
    }
}