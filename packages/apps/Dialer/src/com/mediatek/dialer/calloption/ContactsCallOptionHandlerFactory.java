package com.mediatek.dialer.calloption;

import com.mediatek.calloption.CallOptionHandlerFactory;
import com.mediatek.contacts.ExtensionManager;

public class ContactsCallOptionHandlerFactory extends CallOptionHandlerFactory {
    protected void createHandlerPrototype() {
        mInternetCallOptionHandler = new ContactsInternetCallOptionHandler();
        mVideoCallOptionHandler = new ContactsVideoCallOptionHandler();
        mInternationalCallOptionHandler = new ContactsInternationalCallOptionHandler();
        mSimSelectionCallOptionHandler = new ContactsSimSelectionCallOptionHandler();
        mSimStatusCallOptionHandler = new ContactsSimStatusCallOptionHandler();
        mIpCallOptionHandler = new ContactsIpCallOptionHandler();
        mVoiceMailCallOptionHandler = new ContactsVoiceMailCallOptionHandler();
        ExtensionManager.getInstance().getContactsCallOptionHandlerFactoryExtension().createHandlerPrototype(this);
    }
}
