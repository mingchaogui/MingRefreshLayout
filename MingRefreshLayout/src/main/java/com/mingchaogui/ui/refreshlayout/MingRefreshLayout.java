package com.mingchaogui.ui.refreshlayout;


import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.FrameLayout;
import android.widget.Scroller;


public class MingRefreshLayout extends FrameLayout {

    // 初始状态
    private static final int IDLE = 0;
    // 初始状态
    private static final int PULL_DOWN = 1;
    // 释放刷新
    private static final int READY_TO_REFRESH = 2;
    // 正在刷新
    private static final int REFRESHING = 3;
    // 正在加载
    private static final int LOADING = 4;
    // 刷新完毕
    private static final int END_REFRESH = 5;
    // 加载完毕
    private static final int END_LOAD = 6;
    // 加载完毕到初始状态的中间状态
    private static final int TO_IDLE = 7;

    // 当前状态
    private int mState = IDLE;
    // 上一个onInterceptTouchEvent事件点Y坐标
    private float mInterceptLastY;
    // 上一个onTouchEvent事件点Y坐标
    private float mLastY;

    // Scroller
    private Scroller mScroller;
    // 只有滑动距离大于这个值时才会尝试去拦截事件
    private int mTouchSlop;
    // 滑动阻力，下拉滑动距离 = 手指滑动距离 / 阻力
    private float mTouchDrag = 1.5f;
    // 刷新/加载完毕后，RefreshView/LoadView滞留时间
    private int mSpinDelay = 1000;

    private View mRefreshView;
    private RefreshViewHandler mRefreshViewHandler;
    private View mContentView;
    private View mLoadView;
    private LoadViewHandler mLoadViewHandler;

    // 刷新回调接口
    private RefreshHandler mRefreshHandler;

    public MingRefreshLayout(Context context) {
        super(context);

        init();
    }

    public MingRefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public MingRefreshLayout(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init();
    }

    private void init() {
        mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop() / 6;
        mScroller = new Scroller(getContext());
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        // 以第一个非RefreshView也非LoadView的View为ContentView
        if (mContentView == null) {
            for (int i = 0; i < this.getChildCount(); i++) {
                View child = this.getChildAt(i);
                if (child != mRefreshView && child != mLoadView) {
                    mContentView = child;

                    break;
                }
            }
        }

        int childTop = 0;
        if (mRefreshView != null) {
            childTop = -mRefreshView.getMeasuredHeight();

            mRefreshView.layout(
                    0,
                    childTop,
                    mRefreshView.getMeasuredWidth(),
                    childTop + mRefreshView.getMeasuredHeight()
            );

            childTop = mRefreshView.getBottom();
        }

        if (mContentView != null) {
            mContentView.layout(
                    0,
                    childTop,
                    mContentView.getMeasuredWidth(),
                    childTop + mContentView.getMeasuredHeight()
            );

            childTop = mContentView.getBottom();
        }
        if (mLoadView != null && mLoadView.getVisibility() != GONE) {
            mLoadView.layout(
                    0,
                    childTop,
                    mLoadView.getMeasuredWidth(),
                    childTop + mLoadView.getMeasuredHeight()
            );
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mScroller.forceFinished(true);
                mLastY = ev.getY(ev.getPointerCount() - 1);

                break;
        }

        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 取最后一个（放下的）手指坐标
        float y = ev.getY(ev.getPointerCount() - 1);

        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                // 滑动距离
                float deltaY = mInterceptLastY - y;
                if (Math.abs(deltaY) < mTouchSlop) {
                    break;
                } else if (deltaY < 0) {// 手指向下滑动
                    return mRefreshHandler.canRefresh()
                            || getScrollY() > 0;// ScrollY大于0时，LoadView会被显示，应拦截此事件使手指可以把LoadView推回不可显示的位置
                } else {// 手指向上滑动
                    return mRefreshHandler.canLoad()
                            || getScrollY() < 0;// ScrollY小于0时，RefreshView会被显示，应拦截此事件使手指可以把RefreshView推回不可显示的位置
                }
        }

        // 记录手指新位置
        mInterceptLastY = y;

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float y = event.getY(event.getPointerCount() - 1);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                // 滑动距离
                float deltaY = mLastY - y;
                // 对滑动距离做缩小，造成用力拉的感觉
                int dragY = (int) (deltaY / mTouchDrag);
                // 实际滑动距离
                int scrollDY = dragY;

                int minY = mRefreshHandler.canRefresh() && mState != LOADING ? -getHeight() : 0;
                int maxY = mRefreshHandler.canLoad() && mState != REFRESHING ? getHeight() : 0;
                int startX = mScroller.getCurrX();
                int startY = mScroller.getCurrY();
                float newScrollY = startY + scrollDY;
                if (newScrollY < minY) {
                    scrollDY = minY - startY;
                } else if (newScrollY > maxY) {
                    scrollDY = maxY - startY;
                }

                mScroller.startScroll(startX, startY, 0, scrollDY, 0);
                invalidate();
                onFingerMove();

                break;

            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                if (mState == IDLE) {
                    break;
                } else if (mState == READY_TO_REFRESH) {
                    changeState(REFRESHING);
                } else if (mState != LOADING) {
                    changeState(TO_IDLE);
                }
                invalidScroll();

                break;
        }

        // 记录手指新位置
        mLastY = y;

        return super.onTouchEvent(event);
    }

    public void onFingerMove() {
        int currY = mScroller.getCurrY();
        // 特殊状态下不会触发状态改变
        if (mState != REFRESHING && mState != END_REFRESH
                && mState != LOADING && mState != END_LOAD
                && mState != TO_IDLE) {
            if (currY < 0) {
                if (currY <= -mRefreshView.getHeight()) {
                    changeState(READY_TO_REFRESH);
                } else {
                    changeState(PULL_DOWN);
                }
            } else if (currY > 0) {
                changeState(LOADING);
            } else {
                changeState(IDLE);
            }
        }
    }

    @Override
    public void computeScroll() {
        super.computeScroll();

        int currX = mScroller.getCurrX();
        int currY = mScroller.getCurrY();

        if (mScroller.computeScrollOffset()) {
            scrollTo(currX, currY);
            postInvalidate();
        } else if (currY == 0 && mState != REFRESHING && mState != LOADING) {// 回滚到了待机位置
            changeState(IDLE);
        }
    }

    /**
     * 根据状态回弹到正确位置
     */
    private void invalidScroll() {
        // 默认滚动到Y0位置
        int y = -mScroller.getCurrY();
        switch (mState) {
            case REFRESHING:
            case END_REFRESH:
                // 滚到刚好能完全显示RefreshView的位置
                y -= mRefreshView.getHeight();

                break;

            case LOADING:
            case END_LOAD:
                if (mScroller.getCurrY() > mLoadView.getHeight()) {// 滚过头了，往回滚一点，刚好能完全显示LoadView就行
                    y += mLoadView.getHeight();
                } else {// 没滚过头，那么我就不滚了
                    y = 0;
                }

                break;
        }

        mScroller.startScroll(
                mScroller.getCurrX(),
                mScroller.getCurrY(),
                0,
                y
        );
        invalidate();
    }

    private void changeState(int state) {
        changeState(state, null);
    }

    private void changeState(int state, Object data) {
        if (mState == state) {
            return;
        }
        mState = state;

        switch (state) {
            case PULL_DOWN:// 下拉
                mRefreshViewHandler.onPullDown();

                break;

            case READY_TO_REFRESH:// 释放刷新
                mRefreshViewHandler.onReadyToRefresh();

                break;

            case REFRESHING:// 正在刷新
                mRefreshViewHandler.onBeginRefresh();
                mRefreshHandler.onRefresh();

                break;

            case LOADING:// 正在加载
                mLoadViewHandler.onBeginLoad();
                mRefreshHandler.onLoad();

                break;

            case END_REFRESH:// 结束刷新
                mRefreshViewHandler.onEndRefresh(data);

                break;

            case END_LOAD:// 结束加载
                mLoadViewHandler.onEndLoad(data);

                break;
        }
    }

    public void setRefreshHandler(RefreshHandler handler) {
        mRefreshHandler = handler;
    }

    public void setRefreshView(int resId, RefreshViewHandler handler) {
        if (mRefreshView != null) {
            this.removeView(mRefreshView);
        }
        // keep pointer
        mRefreshView = LayoutInflater.from(getContext()).inflate(resId, this, false);
        mRefreshViewHandler = handler;
        // callback
        mRefreshViewHandler.onViewCreated(mRefreshView);
        // add view to first
        this.addView(mRefreshView, 0);
    }

    public void setLoadView(int resId, LoadViewHandler handler) {
        if (mLoadView != null) {
            this.removeView(mLoadView);
        }
        // keep pointer
        mLoadView = LayoutInflater.from(getContext()).inflate(resId, this, false);
        mLoadViewHandler = handler;
        // callback
        mLoadViewHandler.onViewCreated(mLoadView);
        // add view to last
        this.addView(mLoadView, getChildCount());
    }

    public void beginRefresh() {
        if (mState == IDLE) {
            changeState(REFRESHING);
            invalidScroll();
        }
    }

    public void beginLoad() {
        if (mState == IDLE) {
            changeState(LOADING);
            invalidScroll();
        }
    }

    public void endRefresh() {
        this.endRefresh(null);
    }

    /**
     * @param data
     */
    public void endRefresh(Object data) {
        if (mState != REFRESHING) {
            return;
        }

        changeState(END_REFRESH, data);
        this.postDelayed(new Runnable() {
            @Override
            public void run() {
                changeState(TO_IDLE);
                invalidScroll();
            }
        }, mSpinDelay);
    }

    public void endLoad() {
        this.endLoad(null);
    }

    /**
     * @param data
     */
    public void endLoad(Object data) {
        if (mState != LOADING) {
            return;
        }

        changeState(END_LOAD, data);
        changeState(TO_IDLE);
        invalidScroll();
    }
}
