package com.mediatek.calloption;

public abstract class CallOptionHandlerFactory {

    protected CallOptionBaseHandler mFirstCallOptionHandler;
    protected CallOptionBaseHandler mEmergencyCallOptionHandler;
    protected CallOptionBaseHandler mInternetCallOptionHandler;
    protected CallOptionBaseHandler mVideoCallOptionHandler;
    protected CallOptionBaseHandler mInternationalCallOptionHandler;
    protected CallOptionBaseHandler mSimSelectionCallOptionHandler;
    protected CallOptionBaseHandler mSimStatusCallOptionHandler;
    protected CallOptionBaseHandler mIpCallOptionHandler;
    protected CallOptionBaseHandler mVoiceMailCallOptionHandler;
    protected CallOptionBaseHandler mFinalCallOptionHandler;

    public CallOptionHandlerFactory() {
        mFirstCallOptionHandler = new FirstCallOptionHandler();
        mEmergencyCallOptionHandler = new EmergencyCallOptionHandler();
        mFinalCallOptionHandler = new FinalCallOptionHandler();
        createHandlerPrototype();
    }

    protected abstract void createHandlerPrototype();

    public CallOptionBaseHandler getFirstCallOptionHandler() {
        return mFirstCallOptionHandler;
    }

    public CallOptionBaseHandler getInternetCallOptionHandler() {
        return mInternetCallOptionHandler;
    }

    public CallOptionBaseHandler getEmergencyCallOptionHandler() {
        return mEmergencyCallOptionHandler;
    }

    public CallOptionBaseHandler getVideoCallOptionHandler() {
        return mVideoCallOptionHandler;
    }

    public CallOptionBaseHandler getInternationalCallOptionHandler() {
        return mInternationalCallOptionHandler;
    }

    public CallOptionBaseHandler getSimSelectionCallOptionHandler() {
        return mSimSelectionCallOptionHandler;
    }

    public CallOptionBaseHandler getSimStatusCallOptionHandler() {
        return mSimStatusCallOptionHandler;
    }

    public CallOptionBaseHandler getIpCallOptionHandler() {
        return mIpCallOptionHandler;
    }

    public CallOptionBaseHandler getVoiceMailCallOptionHandler() {
        return mVoiceMailCallOptionHandler;
    }

    public CallOptionBaseHandler getFinalCallOptionHandler() {
        return mFinalCallOptionHandler;
    }

    public void setEmergencyCallOptionHandler(CallOptionBaseHandler emergencyCallOptionHandler) {
        mEmergencyCallOptionHandler = emergencyCallOptionHandler;
    }

    public void setInternationalCallOptionHandler(CallOptionBaseHandler internationalCallOptionHandler) {
        mInternationalCallOptionHandler = internationalCallOptionHandler;
    }
}