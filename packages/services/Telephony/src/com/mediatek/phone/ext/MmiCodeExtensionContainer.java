
package com.mediatek.phone.ext;

import java.util.Iterator;
import java.util.LinkedList;

import android.app.Dialog;
import android.content.Context;
import android.os.Message;
import android.widget.EditText;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.android.internal.telephony.MmiCode;
import com.mediatek.phone.PhoneLog;

public class MmiCodeExtensionContainer extends MmiCodeExtension {
    private static final String LOG_TAG = "MmiCodeExtensionContainer";

    private LinkedList<MmiCodeExtension> mSubExtensionList;

    public void add(MmiCodeExtension extension) {
        if (null == mSubExtensionList) {
            PhoneLog.d(LOG_TAG, "create sub extension list");
            mSubExtensionList = new LinkedList<MmiCodeExtension>();
        }
        PhoneLog.d(LOG_TAG, "add extension, extension is " + extension);
        mSubExtensionList.add(extension);
    }

    public void onMmiCodeStarted(Context context, MmiCode mmiCode, Message callBackMessage,
            Dialog progressDialog) {
        if (null == mSubExtensionList) {
            PhoneLog.d(LOG_TAG, "onMmiCodeStarted(), sub extension list is null, just return");
            return;
        }
        PhoneLog.d(LOG_TAG, "onMmiCodeStarted()");
        Iterator<MmiCodeExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            iterator.next().onMmiCodeStarted(context, mmiCode, callBackMessage, progressDialog);
        }
    }

    public boolean onUssdAlertActivityResume(AlertActivity ussdActivity, EditText inputText, int ussdType, AlertController alertController) {
        if (null == mSubExtensionList) {
            PhoneLog.d(LOG_TAG, "onUssdAlertActivityResume(), sub extension list is null, just return false");
            return false;
        }
        Iterator<MmiCodeExtension> iterator = mSubExtensionList.iterator();
        while (iterator.hasNext()) {
            if (iterator.next().onUssdAlertActivityResume(ussdActivity, inputText, ussdType, alertController)) {
                PhoneLog.d(LOG_TAG, "onUssdAlertActivityResume(), Plug-in return true.");
                return true;
            }
        }
        PhoneLog.d(LOG_TAG, "onUssdAlertActivityResume(), default false");
        return false;
    }
}
