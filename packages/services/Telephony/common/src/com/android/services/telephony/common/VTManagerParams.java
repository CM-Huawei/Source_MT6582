package com.android.services.telephony.common;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.List;


public final class VTManagerParams implements Parcelable {

    // below are values of VTManager, sync with VTManager
    public int mCameraSensorCount;
    public int mVideoQuality;
    public boolean mCanDecBrightness;
    public boolean mCanIncBrightness;
    public boolean mCanDecZoom;
    public boolean mCanIncZoom;
    public boolean mCanDecContrast;
    public boolean mCanIncContrast;
    public boolean mIsSupportNightMode;
    public boolean mIsNightModeOn;
    public String mColorEffect;
    public List<String> mSupportedColorEffects;

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCameraSensorCount);
        dest.writeInt(mVideoQuality);
        boolean val2[] = { mCanDecBrightness, mCanIncBrightness, mCanDecZoom, mCanIncZoom, mCanDecContrast, mCanIncContrast,
                mIsSupportNightMode, mIsNightModeOn };
        dest.writeBooleanArray(val2);
        dest.writeString(mColorEffect);
        dest.writeStringList(mSupportedColorEffects);
    }

    /**
     * Creates Call objects for Parcelable implementation.
     */
    public static final Parcelable.Creator<VTManagerParams> CREATOR
            = new Parcelable.Creator<VTManagerParams>() {
        @Override
        public VTManagerParams createFromParcel(Parcel in) {
            return new VTManagerParams(in);
        }

        @Override
        public VTManagerParams[] newArray(int size) {
            return new VTManagerParams[size];
        }
    };

    /**
     * Constructor for Parcelable implementation.
     */
    private VTManagerParams(Parcel in) {
        mCameraSensorCount = in.readInt();
        mVideoQuality = in.readInt();

        boolean val2[] = new boolean[8];
        in.readBooleanArray(val2);
        mCanDecBrightness = val2[0];
        mCanIncBrightness = val2[1];
        mCanDecZoom = val2[2];
        mCanIncZoom = val2[3];
        mCanDecContrast = val2[4];
        mCanIncContrast = val2[5];
        mIsSupportNightMode = val2[6];
        mIsNightModeOn = val2[7];

        mColorEffect = in.readString();
        mSupportedColorEffects = new ArrayList<String>();
        in.readStringList(mSupportedColorEffects);
    }

    public VTManagerParams() {
        mSupportedColorEffects = new ArrayList<String>();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("mCameraSensorCount", mCameraSensorCount)
                .add("mVideoQuality", mVideoQuality)
                .add("mCanDecBrightness", mCanDecBrightness)
                .add("mCanIncBrightness", mCanIncBrightness)
                .add("mCanDecZoom", mCanDecZoom)
                .add("mCanIncZoom", mCanIncZoom)
                .add("mCanDecContrast", mCanDecContrast)
                .add("mCanIncContrast", mCanIncContrast)
                .add("mIsSupportNightMode", mIsSupportNightMode)
                .add("mIsNightModeOn", mIsNightModeOn)
                .add("mColorEffect", mColorEffect)
                .add("mSupportedColorEffects", mSupportedColorEffects)
                .toString();
    }

}