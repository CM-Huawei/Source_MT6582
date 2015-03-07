package com.android.internal.policy.impl.keyguard;

import android.view.ViewGroup;

public interface DgilForLockscreen {
    
    public static interface Callback {
        public void unlock();
    }
    public ViewGroup getDgillayout();  
    public void notifyUnlockedScreen(); 
}
