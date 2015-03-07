package com.mediatek.phone.calloption;

import com.mediatek.calloption.CallOptionHandlerFactory;
import com.mediatek.phone.ext.ExtensionManager;

public class PhoneCallOptionHandlerFactory extends CallOptionHandlerFactory {

    protected void createHandlerPrototype() {
        mInternetCallOptionHandler = new PhoneInternetCallOptionHandler();
        mVideoCallOptionHandler = new PhoneVideoCallOptionHandler();
        mInternationalCallOptionHandler = new PhoneInternationalCallOptionHandler();
        mSimSelectionCallOptionHandler = new PhoneSimSelectionCallOptionHandler();
        mSimStatusCallOptionHandler = new PhoneSimStatusCallOptionHandler();
        mIpCallOptionHandler = new PhoneIpCallOptionHandler();
        mVoiceMailCallOptionHandler = new PhoneVoiceMailCallOptionHandler();
        ExtensionManager.getInstance().getPhoneCallOptionHandlerFactoryExtension().createHandlerPrototype(this);
    }
}
