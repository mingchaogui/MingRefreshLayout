package com.mingchaogui.ui.refreshlayout;


import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.RelativeLayout;
import android.widget.Scroller;


public class MingRefreshLayout extends RelativeLayout {

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

	// 当前状态
	private int mState = IDLE;
    // 是否正在触摸屏幕
    private boolean mTouching = false;
	// 上一个事件点Y坐标
	private int mLastY;

	// Scroller
	private Scroller mScroller;
    // ViewConfiguration
    private ViewConfiguration mViewConfiguration;
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
        mViewConfiguration = ViewConfiguration.get(getContext());
		mScroller = new Scroller(getContext());
    }

	@Override
	protected void onLayout(boolean changed, int l, int t, int r, int b) {
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

	/**
	 * 由我决定是否分发事件，防止事件冲突
	 *
	 * @see android.view.ViewGroup#dispatchTouchEvent(android.view.MotionEvent)
	 */
	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
        int y = (int) ev.getY(ev.getPointerCount() - 1);

		switch (ev.getActionMasked()) {
			case MotionEvent.ACTION_DOWN:
                mTouching = true;
                mLastY = y;
                mScroller.abortAnimation();

				break;

			case MotionEvent.ACTION_POINTER_DOWN:
			case MotionEvent.ACTION_POINTER_UP:
                // 重置Y轴位置，解决多点操作的冲突问题
                mLastY = y;

				break;

			case MotionEvent.ACTION_MOVE:
                // 滑动距离
				int deltaY = mLastY - y;
                // 对实际滑动距离做缩小，造成用力拉的感觉
                int dragY = (int) (deltaY / mTouchDrag);
                // 记录手指新位置
                mLastY = y;

                int minY = mRefreshHandler.canRefresh() && mState != LOADING ? -getHeight() : 0;
                int maxY = mRefreshHandler.canLoad() && mState != REFRESHING ? getHeight() : 0;
                int currY = mScroller.getCurrY();
                float newScrollY = currY + dragY;
                if (newScrollY < minY) {
                    dragY = minY - currY;
                } else if (newScrollY > maxY) {
                    dragY = maxY - currY;
                }
                if (dragY != 0) {
                    mScroller.startScroll(mScroller.getCurrX(), mScroller.getCurrY(), mScroller.getCurrX(), dragY, 0);
                    invalidate();

                    int finalY = mScroller.getFinalY();
                    if (finalY < 0) {
                        if (mState != REFRESHING && mState != END_REFRESH) {
                            if (finalY <= -mRefreshView.getHeight()) {
                                changeState(READY_TO_REFRESH);
                            } else {
                                changeState(PULL_DOWN);
                            }
                        }
                    } else if (mState == IDLE && finalY > mRefreshView.getHeight()) {
                        changeState(LOADING);
                    }

                    // 防止在滑动过程中误触发子View的事件
                    if (Math.abs(deltaY) > mViewConfiguration.getScaledTouchSlop()) {
                        // 把当前事件的Action设置为ACTION_CANCEL（后面会继续派发给子View处理）
                        ev.setAction(MotionEvent.ACTION_CANCEL);
                    }
                }

				break;

            case MotionEvent.ACTION_CANCEL:
			case MotionEvent.ACTION_UP:
                mTouching = false;

				if (mState == READY_TO_REFRESH) {
					changeState(REFRESHING);
				} else if (mState != LOADING) {
                    changeState(IDLE);
                }
                invalidScroll();

				break;
		}

        return super.dispatchTouchEvent(ev);
	}

    @Override
    public void computeScroll() {
        super.computeScroll();

        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            postInvalidate();
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

        if (y != 0) {
            mScroller.startScroll(
                    mScroller.getCurrX(),
                    mScroller.getCurrY(),
                    mScroller.getCurrX(),
                    y
            );
            invalidate();
        }
	}

    private void changeState(int state) {
		changeState(state, null);
	}

	private void changeState(int state, Object data) {
        if (mState == state) {
            return;
        }

		mState = state;
		switch (mState) {
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
                this.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (!mTouching) {
                            changeState(IDLE);
                            invalidScroll();
                        }
                    }
                }, mSpinDelay);

				break;

			case END_LOAD:// 结束加载
				mLoadViewHandler.onEndLoad(data);
                if (!mTouching) {
                    changeState(IDLE);
                    invalidScroll();
                }

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
    }
}
