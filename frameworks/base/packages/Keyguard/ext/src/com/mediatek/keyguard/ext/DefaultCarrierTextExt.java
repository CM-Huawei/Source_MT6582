package com.mediatek.keyguard.ext;

import com.mediatek.keyguard.ext.ICarrierTextExt;

public class DefaultCarrierTextExt implements ICarrierTextExt {

    @Override
    public String changedPlmnToCapitalize(String plmn) {
        if (plmn != null ){
            return plmn.toString().toUpperCase();
        }
        return null;
    }
    
    @Override
    public CharSequence getTextForSimMissing(CharSequence simMessage, CharSequence original, int simId) {
        return original;
    }
}
