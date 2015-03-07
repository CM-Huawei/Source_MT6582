package com.mediatek.camera.ext;
import android.app.Activity;

public interface IAppGuideExt {
    public interface OnGuideFinishListener {
        public void onGuideFinish();
    }
    /**
     * Called when the app want to show application guide
     * @param activity: The parent activity
     * @param type: The app type, such as "PHONE/CONTACTS/MMS/CAMERA"
     */
    void showCameraGuide(Activity activity, String type,
            OnGuideFinishListener finishListener);

    /**
     * Called when the app orientation changed
     */
    void configurationChanged();

    /**
     * Called to dismiss app guide dialog
     */
    void dismiss();
}
