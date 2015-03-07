
package com.mediatek.phone.ext;

import android.app.Dialog;
import android.content.Context;
import android.os.Message;
import android.widget.EditText;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;
import com.android.internal.telephony.MmiCode;

/**
 * Extension class for MmiCode, USSD.
 */
public class MmiCodeExtension {

    /**
     * @param context
     * @param mmiCode
     * @param callBackMessage
     * @param progressDialog
     */
    public void onMmiCodeStarted(Context context, MmiCode mmiCode, Message callBackMessage, Dialog progressDialog) {
    }

    /**
     * This method can be for update the UssdAlertActivity's buttons and
     * EditText attributes.
     *
     * @param ussdActivity
     * @param inputText
     * @param ussdType
     * @param alertController
     * @return
     */
    public boolean onUssdAlertActivityResume(AlertActivity ussdActivity, EditText inputText, int ussdType, AlertController alertController) {
        return false;
    }
}
