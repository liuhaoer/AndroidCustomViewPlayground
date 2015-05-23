package cn.bingoogolapple.acvp.refreshlayout.widget;

import android.content.Context;
import android.support.v4.view.ViewCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * 作者:王浩 邮件:bingoogolapple@gmail.com
 * 创建时间:15/5/21 22:35
 * 描述:
 */
public class BGARefreshLayout extends LinearLayout {
    private static final String TAG = BGARefreshLayout.class.getSimpleName();

    private BGARefreshViewHolder mRefreshViewHolder;
    /**
     * 整个头部控件，下拉刷新控件mRefreshHeaderView和下拉刷新控件下方的自定义组件mCustomHeaderView的父控件
     */
    private LinearLayout mWholeHeaderView;
    /**
     * 下拉刷新控件
     */
    private View mRefreshHeaderView;
    /**
     * 下拉刷新控件下方的自定义控件
     */
    private View mCustomHeaderView;
    /**
     * 下拉刷新控件的高度
     */
    private int mRefreshHeaderViewHeight;
    /**
     * 当前刷新状态
     */
    private RefreshStatus mCurrentRefreshStatus = RefreshStatus.IDLE;
    /**
     * 下拉刷新和上拉加载更多代理
     */
    private BGARefreshLayoutDelegate mDelegate;
    /**
     * 手指按下时，y轴方向的偏移量
     */
    private int mDownY = -1;
    /**
     * 整个头部控件最小的paddingTop
     */
    private int mMinWholeHeaderViewPaddingTop;
    /**
     * 整个头部控件最大的paddingTop
     */
    private int mMaxWholeHeaderViewPaddingTop;

    private AdapterView<?> mAdapterView;
    private ScrollView mScrollView;
    private RecyclerView mRecyclerView;
    private View mNormalView;

    private float mInterceptTouchDownX = -1;
    private float mInterceptTouchDownY = -1;

    public BGARefreshLayout(Context context) {
        this(context, null);
    }

    public BGARefreshLayout(Context context, AttributeSet attrs) {
        super(context, attrs);

        setOrientation(LinearLayout.VERTICAL);

        initWholeHeaderView();
    }

    /**
     * 初始化整个头部控件
     */
    private void initWholeHeaderView() {
        mWholeHeaderView = new LinearLayout(getContext());
        mWholeHeaderView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        mWholeHeaderView.setOrientation(LinearLayout.VERTICAL);
        addView(mWholeHeaderView);
    }

    @Override
    public void onFinishInflate() {
        super.onFinishInflate();

        if (getChildCount() != 2) {
            throw new RuntimeException(BGARefreshLayout.class.getSimpleName() + "必须有且只有一个子控件");
        }

        View contentView = getChildAt(1);
        if (contentView instanceof AdapterView<?>) {
            mAdapterView = (AdapterView<?>) contentView;
        } else if (contentView instanceof RecyclerView) {
            mRecyclerView = (RecyclerView) contentView;
        } else if (contentView instanceof ScrollView) {
            mScrollView = (ScrollView) contentView;
        } else {
            mNormalView = contentView;
            // 设置为可点击，否则在空白区域无法拖动
            mNormalView.setClickable(true);
        }
    }

    public void setRefreshViewHolder(BGARefreshViewHolder refreshViewHolder) {
        mRefreshViewHolder = refreshViewHolder;
        initRefreshHeaderView();
    }

    /**
     * 初始化下拉刷新控件
     *
     * @return
     */
    private void initRefreshHeaderView() {
        mRefreshHeaderView = mRefreshViewHolder.getRefreshHeaderView();
        if (mRefreshHeaderView != null) {
            mRefreshHeaderView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            mWholeHeaderView.addView(mRefreshHeaderView, 0);

            mRefreshHeaderViewHeight = mRefreshViewHolder.getRefreshHeaderViewHeight();
            mMinWholeHeaderViewPaddingTop = -mRefreshHeaderViewHeight;
            mMaxWholeHeaderViewPaddingTop = (int) (mRefreshHeaderViewHeight * mRefreshViewHolder.getSpringDistanceScale());

            mWholeHeaderView.setPadding(0, mMinWholeHeaderViewPaddingTop, 0, 0);
        }
    }

    /**
     * 添加下拉刷新控件下方的自定义控件
     *
     * @param customHeaderView
     */
    public void addCustomHeaderView(View customHeaderView) {
        mCustomHeaderView = customHeaderView;
        if (mCustomHeaderView != null) {
            mCustomHeaderView.setLayoutParams(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            mWholeHeaderView.addView(mCustomHeaderView);
        }
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mInterceptTouchDownX = event.getRawX();
                mInterceptTouchDownY = event.getRawY();
                break;
            case MotionEvent.ACTION_MOVE:
                if (mInterceptTouchDownX == -1) {
                    mInterceptTouchDownX = (int) event.getRawX();
                }
                if (mInterceptTouchDownY == -1) {
                    mInterceptTouchDownY = (int) event.getRawY();
                }

                int interceptTouchMoveDistanceY = (int) (event.getRawY() - mInterceptTouchDownY);
                if (Math.abs(event.getRawX() - mInterceptTouchDownX) < Math.abs(interceptTouchMoveDistanceY)) {
                    if (shouldHandleRefresh() && interceptTouchMoveDistanceY > 0) {

                        // ACTION_DOWN时没有消耗掉事件，子控件会处于按下状态，这里设置ACTION_CANCEL，使子控件取消按下状态
                        event.setAction(MotionEvent.ACTION_CANCEL);
                        super.onInterceptTouchEvent(event);
                        return true;
                    }
                }
                break;
            case MotionEvent.ACTION_CANCEL:
            case MotionEvent.ACTION_UP:
                // 重置
                mInterceptTouchDownX = -1;
                mInterceptTouchDownY = -1;
                break;
        }

        return super.onInterceptTouchEvent(event);
    }

    /**
     * 是否满足处理刷新的条件
     *
     * @return
     */
    private boolean shouldHandleRefresh() {
        // 内容是普通控件，满足
        if (mNormalView != null) {
            return true;
        }

        // 内容是ScrollView，并且其scrollY为0时满足
        if (mScrollView != null && mScrollView.getScrollY() == 0) {
            return true;
        }

        if (mAdapterView != null) {
            int firstChildTop = 0;
            if (mAdapterView.getChildCount() > 0) {
                // 如果AdapterView的子控件数量不为0，获取第一个子控件的top
                firstChildTop = mAdapterView.getChildAt(0).getTop();
            }
            if (mAdapterView.getFirstVisiblePosition() == 0 && firstChildTop == 0) {
                return true;
            }
        }

        if (mRecyclerView != null) {
            int firstChildTop = 0;
            if (mRecyclerView.getChildCount() > 0) {
                // 如果RecyclerView的子控件数量不为0，获取第一个子控件的top
                firstChildTop = mRecyclerView.getChildAt(0).getTop();
            }
            LinearLayoutManager layoutManager = (LinearLayoutManager) mRecyclerView.getLayoutManager();
            if (layoutManager.findFirstCompletelyVisibleItemPosition() == 0 && firstChildTop == 0) {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (null != mRefreshHeaderView) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mDownY = (int) event.getY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (handleActionMove(event)) {
                        return true;
                    }
                    break;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    if (handleActionUpOrCancel()) {
                        return true;
                    }
                    break;
                default:
                    break;
            }
        }
        return super.onTouchEvent(event);
    }

    /**
     * 处理手指滑动事件
     *
     * @param event
     * @return true表示自己消耗掉该事件，false表示不消耗该事件
     */
    private boolean handleActionMove(MotionEvent event) {
        // 如果处于正在刷新状态，则跳出switch语句，执行父控件的touch事件
        if (mCurrentRefreshStatus == RefreshStatus.REFRESHING) {
            return false;
        }

        if (mDownY == -1) {
            mDownY = (int) event.getY();
        }
        int diffY = (int) event.getY() - mDownY;

        diffY = (int) (diffY / mRefreshViewHolder.getPaddingTopScale());

        // 如果是向下拉，并且当前可见的第一个条目的索引等于0，才处理整个头部控件的padding
        if (diffY > 0 && shouldHandleRefresh()) {
            // ACTION_DOWN时没有消耗掉事件，子控件会处于按下状态，这里设置ACTION_CANCEL，使子控件取消按下状态
            event.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(event);

            int paddingTop = mMinWholeHeaderViewPaddingTop + diffY;
            if (paddingTop > 0 && mCurrentRefreshStatus != RefreshStatus.RELEASE_REFRESH) {
                // 下拉刷新控件完全显示，并且当前状态没有处于释放开始刷新状态
                mCurrentRefreshStatus = RefreshStatus.RELEASE_REFRESH;
                handleRefreshStatusChanged();

                paddingTop = Math.min(paddingTop, mMaxWholeHeaderViewPaddingTop);
                mRefreshViewHolder.handleScale(1.0f, diffY);
            } else if (paddingTop < 0) {
                // 下拉刷新控件没有完全显示，并且当前状态没有处于下拉刷新状态
                if (mCurrentRefreshStatus != RefreshStatus.PULL_DOWN) {
                    boolean isPreRefreshStatusNotIdle = mCurrentRefreshStatus != RefreshStatus.IDLE;
                    mCurrentRefreshStatus = RefreshStatus.PULL_DOWN;
                    if (isPreRefreshStatusNotIdle) {
                        handleRefreshStatusChanged();
                    }
                }
                float scale = 1 - paddingTop * 1.0f / mMinWholeHeaderViewPaddingTop;
                /**
                 * 往下滑
                 * paddingTop    mMinWholeHeaderViewPaddingTop ==> 0
                 * scale         0 ==> 1
                 * 往上滑
                 * paddingTop    0 ==> mMinWholeHeaderViewPaddingTop
                 * scale         1 ==> 0
                 */
                mRefreshViewHolder.handleScale(scale, diffY);
            }
            mWholeHeaderView.setPadding(0, paddingTop, 0, 0);

            if (mRefreshViewHolder.canChangeToRefreshingStatus()) {
                mDownY = -1;
                changeToRefreshing();
            }
            return true;
        }
        return false;
    }

    /**
     * 处理手指抬起事件
     *
     * @return true表示自己消耗掉该事件，false表示不消耗该事件
     */
    private boolean handleActionUpOrCancel() {
        mDownY = -1;

        boolean isReturnTrue = false;
        // 如果当前头部刷新控件没有完全隐藏，则需要返回true，自己消耗ACTION_UP事件
        if (mWholeHeaderView.getPaddingTop() != mMinWholeHeaderViewPaddingTop) {
            isReturnTrue = true;
        }

        if (mCurrentRefreshStatus == RefreshStatus.PULL_DOWN) {
            // 处于下拉刷新状态，松手时隐藏下拉刷新控件
            hiddenRefreshHeaderView();
            mCurrentRefreshStatus = RefreshStatus.IDLE;
            handleRefreshStatusChanged();
        } else if (mCurrentRefreshStatus == RefreshStatus.RELEASE_REFRESH) {
            // 处于松开进入刷新状态，松手时完全显示下拉刷新控件，进入正在刷新状态
            changeToRefreshing();
        }

        return isReturnTrue;
    }

    /**
     * 切换到正在刷新状态
     */
    private void changeToRefreshing() {
        changeRefreshHeaderViewToZero();
        mCurrentRefreshStatus = RefreshStatus.REFRESHING;
        handleRefreshStatusChanged();

        if (mDelegate != null) {
            mDelegate.onBGARefreshLayoutBeginRefreshing();
        }
    }

    /**
     * 处理下拉刷新控件状态变化
     */
    private void handleRefreshStatusChanged() {
        switch (mCurrentRefreshStatus) {
            case IDLE:
                mRefreshViewHolder.changeToIdle();
                break;
            case PULL_DOWN:
                mRefreshViewHolder.changeToPullDown();
                break;
            case RELEASE_REFRESH:
                mRefreshViewHolder.changeToReleaseRefresh();
                break;
            case REFRESHING:
                mRefreshViewHolder.changeToRefreshing();
                break;
            default:
                break;
        }
    }

    /**
     * 结束下拉刷新和上拉加载更多
     */
    public void endRefreshing() {
        mCurrentRefreshStatus = RefreshStatus.IDLE;
        hiddenRefreshHeaderView();
        handleRefreshStatusChanged();
        mRefreshViewHolder.onEndRefreshing();
    }

    /**
     * 隐藏下拉刷新控件，带动画
     */
    private void hiddenRefreshHeaderView() {
        ViewCompat.postOnAnimation(mWholeHeaderView, new Runnable() {
            @Override
            public void run() {
                if (mWholeHeaderView.getPaddingTop() > mMinWholeHeaderViewPaddingTop) {
                    mWholeHeaderView.setPadding(0, mWholeHeaderView.getPaddingTop() - 8, 0, 0);
                    hiddenRefreshHeaderView();
                } else {
                    mWholeHeaderView.setPadding(0, mMinWholeHeaderViewPaddingTop, 0, 0);
                }
            }
        });
    }

    /**
     * 设置下拉刷新控件的paddingTop到0，带动画
     */
    private void changeRefreshHeaderViewToZero() {
        ViewCompat.postOnAnimation(mWholeHeaderView, new Runnable() {
            @Override
            public void run() {
                if (mWholeHeaderView.getPaddingTop() > 0) {
                    mWholeHeaderView.setPadding(0, mWholeHeaderView.getPaddingTop() - 8, 0, 0);
                    changeRefreshHeaderViewToZero();
                } else {
                    mWholeHeaderView.setPadding(0, 0, 0, 0);
                }
            }
        });
    }

    public void setDelegate(BGARefreshLayoutDelegate delegate) {
        mDelegate = delegate;
    }

    public interface BGARefreshLayoutDelegate {
        /**
         * 开始刷新
         */
        void onBGARefreshLayoutBeginRefreshing();
    }

    public enum RefreshStatus {
        IDLE, PULL_DOWN, RELEASE_REFRESH, REFRESHING
    }
}