package com.mingchaogui.ui.refreshlayout;


public abstract class RefreshHandler {

    public abstract boolean canRefresh();

    public boolean canLoad() {
        return false;
    }

    public abstract void onRefresh();

    public void onLoad() {

    }
}
