package com.mingchaogui.ui.refreshlayout;


import android.view.View;

public abstract class RefreshViewHandler {

    public abstract void onViewCreated(View view);

    public void onPullDown() {

    }

    public void onReadyToRefresh() {

    }

    public void onBeginRefresh() {

    }

    /**
     *
     * @param data 可携带额外的数据，以便根据刷新结果显示不同的样式
     */
    public void onEndRefresh(Object data) {

    }
}
