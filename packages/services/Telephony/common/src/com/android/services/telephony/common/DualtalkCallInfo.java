
package com.android.services.telephony.common;

import android.os.Parcel;
import android.os.Parcelable;
import com.google.common.base.Objects;

public class DualtalkCallInfo implements Parcelable {

    private int mCdmaPhoneCallState;
    private boolean mIsSecondHoldCallVisible;
    private boolean mIsSecondaryCallVisible;
    private boolean mIsThreeWayCallOrigStateDialing;
    private boolean mIsCdmaAndGsmActive;
    private boolean mIsDualTalkMultipleHoldCase;
    private boolean mHasDualHoldCallsOnly;
    private boolean mHasMultipleRingingCall;
    private boolean mIsRingingWhenOutgoing;

    public DualtalkCallInfo() {
    }

    /**
     * Parcelable implementation
     */

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(mCdmaPhoneCallState);
        boolean values[] = {
                mIsSecondHoldCallVisible, mIsSecondaryCallVisible, mIsThreeWayCallOrigStateDialing,
                mIsCdmaAndGsmActive, mIsDualTalkMultipleHoldCase, mHasDualHoldCallsOnly,
                mHasMultipleRingingCall, mIsRingingWhenOutgoing
        };
        dest.writeBooleanArray(values);
    }

    /**
     * Constructor for Parcelable implementation.
     */
    private DualtalkCallInfo(Parcel in) {
        mCdmaPhoneCallState = in.readInt();
        boolean values[] = new boolean[8];
        in.readBooleanArray(values);
        mIsSecondHoldCallVisible = values[0];
        mIsSecondaryCallVisible = values[1];
        mIsThreeWayCallOrigStateDialing = values[2];
        mIsCdmaAndGsmActive = values[3];
        mIsDualTalkMultipleHoldCase = values[4];
        mHasDualHoldCallsOnly = values[5];
        mHasMultipleRingingCall = values[6];
        mIsRingingWhenOutgoing = values[7];
    }

    @Override
    public int describeContents() {
        return 0;
    }

    /**
     * Creates Call objects for Parcelable implementation.
     */
    public static final Parcelable.Creator<DualtalkCallInfo> CREATOR = new Parcelable.Creator<DualtalkCallInfo>() {

        @Override
        public DualtalkCallInfo createFromParcel(Parcel in) {
            return new DualtalkCallInfo(in);
        }

        @Override
        public DualtalkCallInfo[] newArray(int size) {
            return new DualtalkCallInfo[size];
        }
    };

    @Override
    public String toString() {
        return Objects.toStringHelper(this).add("mCdmaPhoneCallState", mCdmaPhoneCallState)
                .add("mIsSecondHoldCallVisible", mIsSecondHoldCallVisible)
                .add("mIsSecondaryCallVisible", mIsSecondaryCallVisible)
                .add("mIsThreeWayCallOrigStateDialing", mIsThreeWayCallOrigStateDialing)
                .add("mIsCdmaAndGsmActive", mIsCdmaAndGsmActive)
                .add("mIsDualTalkMultipleHoldCase", mIsDualTalkMultipleHoldCase)
                .add("mhasDualHoldCallsOnly", mHasDualHoldCallsOnly)
                .add("mhasMultipleRingingCall", mHasMultipleRingingCall)
                .add("mIsRingingWhenOutgoing", mIsRingingWhenOutgoing).toString();
    }

    public void setCdmaPhoneCallState(int callState) {
        this.mCdmaPhoneCallState = callState;
    }

    public int getCdmaPhoneCallState() {
        return this.mCdmaPhoneCallState;
    }

    public void setIsSecondHoldCallVisible(boolean visible) {
        this.mIsSecondHoldCallVisible = visible;
    }

    public boolean getIsSecondHoldCallVisible() {
        return this.mIsSecondHoldCallVisible;
    }

    public void setIsSecondaryCallVisible(boolean visible) {
        this.mIsSecondaryCallVisible = visible;
    }

    public boolean getIsSecondaryCallVisible() {
        return this.mIsSecondaryCallVisible;
    }

    public void setIsThreeWayCallOrigStateDialing(boolean visible) {
        this.mIsThreeWayCallOrigStateDialing = visible;
    }

    public boolean getIsThreeWayCallOrigStateDialing() {
        return this.mIsThreeWayCallOrigStateDialing;
    }

    public void setIsCdmaAndGsmActive(boolean result) {
        this.mIsCdmaAndGsmActive = result;
    }

    public boolean getIsCdmaAndGsmActive() {
        return this.mIsCdmaAndGsmActive;
    }

    public void setIsDualTalkMultipleHoldCase(boolean result) {
        this.mIsDualTalkMultipleHoldCase = result;
    }

    public boolean getIsDualTalkMultipleHoldCase() {
        return this.mIsDualTalkMultipleHoldCase;
    }

    public void setHasDualHoldCallsOnly(boolean result) {
        this.mHasDualHoldCallsOnly = result;
    }

    public boolean getHasDualHoldCallsOnly() {
        return this.mHasDualHoldCallsOnly;
    }

    public void setHasMultipleRingingCall(boolean result) {
        this.mHasMultipleRingingCall = result;
    }

    public boolean getHasMultipleRingingCall() {
        return this.mHasMultipleRingingCall;
    }

    public void setIsRingingWhenOutgoing(boolean result) {
        this.mIsRingingWhenOutgoing = result;
    }

    public boolean getIsRingingWhenOutgoing() {
        return this.mIsRingingWhenOutgoing;
    }
}
