package com.mediatek.incallui.ext;

import com.android.incallui.Log;


class PluginWrapper implements IInCallUIPlugin{

    private CallCardExtContainer mCallCardExtContainer = null;
    private VTCallExtContainer mVTCallExtContainer = null;
    private CallButtonExtContainer mCallButtonExtContainer = null;
    private InCallUIExtContainer mInCallUIExtContainer = null;
    private NotificationExtContainer mNotificationExtContainer = null;
    private int mSize = 0;

    public PluginWrapper() {
        mCallCardExtContainer = new CallCardExtContainer();
        mVTCallExtContainer = new VTCallExtContainer();
        mCallButtonExtContainer = new CallButtonExtContainer();
        mInCallUIExtContainer = new InCallUIExtContainer();
        mNotificationExtContainer = new NotificationExtContainer();
    }

    /**
     * @param phonePlugin 
     */
    public void addExtensions(IInCallUIPlugin plugin) {
        mCallCardExtContainer.add(plugin.getCallCardExtension());
        mVTCallExtContainer.add(plugin.getVTCallExtension());
        mCallButtonExtContainer.add(plugin.getCallButtonExtension());
        mInCallUIExtContainer.add(plugin.getInCallUIExtension());
        mNotificationExtContainer.add(plugin.getNotificationExtension());

        mSize++;
        log("addExtensions, plugin:" + plugin + ", size=" + mSize);
    }

    public int size() {
        return mSize;
    }

    private void log(String msg) {
        Log.d(this, msg);
    }

    @Override
    public CallCardExtension getCallCardExtension() {
        return mCallCardExtContainer;
    }

    @Override
    public VTCallExtension getVTCallExtension() {
        return mVTCallExtContainer;
    }

    @Override
    public CallButtonExtension getCallButtonExtension() {
        return mCallButtonExtContainer;
    }

    @Override
    public InCallUIExtension getInCallUIExtension() {
        return mInCallUIExtContainer;
    }

    @Override
    public NotificationExtension getNotificationExtension() {
        return mNotificationExtContainer;
    }
}
