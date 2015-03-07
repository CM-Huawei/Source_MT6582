package com.android.services.telephony.common;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.common.base.Objects;

import java.util.ArrayList;
import java.util.List;


public final class VTSettingParams implements Parcelable {

    // below are values of VT settings. sync with VTSettingUtils.
    public String mPicToReplaceLocal;
    public String mShowLocalMT;
    public String mPicToReplacePeer;
    public boolean mEnableBackCamera;
    public boolean mPeerBigger;
    public boolean mShowLocalMO;
    public boolean mAutoDropBack;
    public boolean mToReplacePeer;

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(mPicToReplaceLocal);
        dest.writeString(mShowLocalMT);
        dest.writeString(mPicToReplacePeer);

        boolean val1[] = {mEnableBackCamera, mPeerBigger, mShowLocalMO, mAutoDropBack, mToReplacePeer};
        dest.writeBooleanArray(val1);
    }

    /**
     * Creates Call objects for Parcelable implementation.
     */
    public static final Parcelable.Creator<VTSettingParams> CREATOR
            = new Parcelable.Creator<VTSettingParams>() {
        @Override
        public VTSettingParams createFromParcel(Parcel in) {
            return new VTSettingParams(in);
        }

        @Override
        public VTSettingParams[] newArray(int size) {
            return new VTSettingParams[size];
        }
    };

    /**
     * Constructor for Parcelable implementation.
     */
    private VTSettingParams(Parcel in) {
        mPicToReplaceLocal = in.readString();
        mShowLocalMT = in.readString();
        mPicToReplacePeer = in.readString();

        boolean val1[] = new boolean[5];
        in.readBooleanArray(val1);
        mEnableBackCamera = val1[0];
        mPeerBigger = val1[1];
        mShowLocalMO = val1[2];
        mAutoDropBack = val1[3];
        mToReplacePeer = val1[4];
    }

    public VTSettingParams() {
        
    }

    public VTSettingParams(VTSettingParams params) {
        mPicToReplaceLocal = params.mPicToReplaceLocal;
        mShowLocalMT = params.mShowLocalMT;
        mPicToReplacePeer = params.mPicToReplacePeer;
        mEnableBackCamera = params.mEnableBackCamera;
        mPeerBigger = params.mPeerBigger;
        mShowLocalMO = params.mShowLocalMO;
        mAutoDropBack = params.mAutoDropBack;
        mToReplacePeer = params.mToReplacePeer;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public String toString() {
        return Objects.toStringHelper(this)
                .add("mPicToReplaceLocal", mPicToReplaceLocal)
                .add("mShowLocalMT", mShowLocalMT)
                .add("mPicToReplacePeer", mPicToReplacePeer)
                .add("mEnableBackCamera", mEnableBackCamera)
                .add("mPeerBigger", mPeerBigger)
                .add("mShowLocalMO", mShowLocalMO)
                .add("mAutoDropBack", mAutoDropBack)
                .add("mToReplacePeer", mToReplacePeer)
                .toString();
    }

}