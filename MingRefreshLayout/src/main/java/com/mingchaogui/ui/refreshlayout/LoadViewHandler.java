package com.mingchaogui.ui.refreshlayout;


import android.view.View;

public abstract class LoadViewHandler {

    public abstract void onViewCreated(View view);

    public void onBeginLoad() {

    }

    /**
     *
     * @param data 可携带额外的数据，以便根据加载结果显示不同的样式
     */
    public void onEndLoad(Object data) {

    }
}
