package com.mediatek.keyguard.ext;

/**
 * Carrier text related interface
 */
public interface ICarrierTextExt {

    // Changed the CHINA TELECOM to China Telecom
    String changedPlmnToCapitalize(String plmn);

    /**
     * For CU, display "No SIM CARD" without "NO SERVICE" when
     * there is no sim card in device and carrier's service is
     * ready.
     *
     * @param simMessage
     *          the first part of common carrier text
     * @param original
     *          common carrier text
     * @param simId
     *          current sim id
     *
     * @return ture if sim is in service
     */
    CharSequence getTextForSimMissing(CharSequence simMessage, CharSequence original, int simId);
}
