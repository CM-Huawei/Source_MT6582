/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.internal.telephony.uicc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import android.os.Environment;
import android.telephony.Rlog;
import android.util.Xml;

import com.android.internal.util.XmlUtils;

import com.mediatek.common.MediatekClassFactory;
import com.mediatek.common.telephony.IServiceStateExt;

// MVNO-API START
import com.mediatek.common.featureoption.FeatureOption;
import java.util.ArrayList;
import java.lang.Integer;
// MVNO-API END

public class SpnOverride {
    private static HashMap<String, String> mCarrierSpnMap;
    private static SpnOverride sInstance;
    static final Object sInstSync = new Object();
    static final String LOG_TAG = "SpnOverride";
    static final String PARTNER_SPN_OVERRIDE_PATH ="etc/spn-conf.xml";

    // MVNO-API START
      // EF_SPN
    private static HashMap<String, String> CarrierVirtualSpnMapByEfSpn;
    private static final String PARTNER_VIRTUAL_SPN_BY_EF_SPN_OVERRIDE_PATH ="etc/virtual-spn-conf-by-efspn.xml";

      // IMSI
    private ArrayList CarrierVirtualSpnMapByImsi;
    private static final String PARTNER_VIRTUAL_SPN_BY_IMSI_OVERRIDE_PATH ="etc/virtual-spn-conf-by-imsi.xml";

    // EF_PNN
    private static HashMap<String, String> CarrierVirtualSpnMapByEfPnn;
    private static final String PARTNER_VIRTUAL_SPN_BY_EF_PNN_OVERRIDE_PATH ="etc/virtual-spn-conf-by-efpnn.xml";

    // EF_GID1
    private static HashMap<String, String> CarrierVirtualSpnMapByEfGid1;
    private static final String PARTNER_VIRTUAL_SPN_BY_EF_GID1_OVERRIDE_PATH ="etc/virtual-spn-conf-by-efgid1.xml";

    public class VirtualSpnByImsi {
        public String pattern;
        public String name;
        public VirtualSpnByImsi(String pattern, String name) {
            this.pattern = pattern;
            this.name = name;
        }
    }

    // MVNO-API END

    public static SpnOverride getInstance() {
        synchronized (sInstSync) {
            if (sInstance == null) {
                sInstance = new SpnOverride();
            }
        }
        return sInstance;
    }

    private SpnOverride() {
        mCarrierSpnMap = new HashMap<String, String>();
        loadSpnOverrides();

        // MVNO-API
        // EF_SPN
        CarrierVirtualSpnMapByEfSpn = new HashMap<String, String>();
        loadVirtualSpnOverridesByEfSpn();

        // IMSI
        this.CarrierVirtualSpnMapByImsi = new ArrayList();
        this.loadVirtualSpnOverridesByImsi();

        // EF_PNN
        CarrierVirtualSpnMapByEfPnn = new HashMap<String, String>();
        loadVirtualSpnOverridesByEfPnn();

        // EF_GID1
        CarrierVirtualSpnMapByEfGid1 = new HashMap<String, String>();
        loadVirtualSpnOverridesByEfGid1();
    }

    public boolean containsCarrier(String carrier) {
        return mCarrierSpnMap.containsKey(carrier);
    }

    public String getSpn(String carrier) {
        return mCarrierSpnMap.get(carrier);
    }

    private static void loadSpnOverrides() {
        FileReader spnReader;
        Rlog.d(LOG_TAG, "loadSpnOverrides");
        final File spnFile = new File(Environment.getRootDirectory(),
                PARTNER_SPN_OVERRIDE_PATH);

        try {
            spnReader = new FileReader(spnFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can not open " +
                    Environment.getRootDirectory() + "/" + PARTNER_SPN_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(spnReader);

            XmlUtils.beginDocument(parser, "spnOverrides");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"spnOverride".equals(name)) {
                    break;
                }

                String numeric = parser.getAttributeValue(null, "numeric");
                String data    = parser.getAttributeValue(null, "spn");

                mCarrierSpnMap.put(numeric, data);
            }
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in spn-conf parser " + e);
        }

        // load the op customization items
        IServiceStateExt serviceStateExt;
        try {
            serviceStateExt = MediatekClassFactory.createInstance(IServiceStateExt.class);
        } catch (Exception e) {
            serviceStateExt = null;
        }
        if (serviceStateExt != null) {
            Rlog.d(LOG_TAG, "the plugin of IServiceStateExt is " + serviceStateExt.getClass());
            // overlay the items using operation's customized items.
            Map<String, String> opItems = serviceStateExt.loadSpnOverrides();
            if (opItems != null) {
                Rlog.d(LOG_TAG, "load operator spn overrides, the size is: " + opItems.size() + ".");
                for (Entry<String, String> entry : opItems.entrySet()) {
                    if (mCarrierSpnMap.containsKey(entry.getKey())) {
                        mCarrierSpnMap.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
    }

    // MVNO-API START
    private static void loadVirtualSpnOverridesByEfSpn() {
        FileReader spnReader;
        Rlog.d(LOG_TAG, "loadVirtualSpnOverridesByEfSpn");
        final File spnFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_SPN_BY_EF_SPN_OVERRIDE_PATH);

        try {
            spnReader = new FileReader(spnFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_SPN_BY_EF_SPN_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(spnReader);

            XmlUtils.beginDocument(parser, "virtualSpnOverridesByEfSpn");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"virtualSpnOverride".equals(name)) {
                    break;
                }

                String mccmncspn = parser.getAttributeValue(null, "mccmncspn");
                String spn = parser.getAttributeValue(null, "name");
                Rlog.w(LOG_TAG, "test mccmncspn = " + mccmncspn + ", name = " + spn);
                CarrierVirtualSpnMapByEfSpn.put(mccmncspn, spn);
            }
            spnReader.close();
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efspn parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efspn parser " + e);
        }
    }

    public String getSpnByEfSpn(String mccmnc, String spn) {
        if(mccmnc == null || spn == null || mccmnc.isEmpty() || spn.isEmpty())
            return null;

        return CarrierVirtualSpnMapByEfSpn.get(mccmnc + spn);
    }

    private void loadVirtualSpnOverridesByImsi() {
        FileReader spnReader;
        Rlog.d(LOG_TAG, "loadVirtualSpnOverridesByImsi");
        final File spnFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_SPN_BY_IMSI_OVERRIDE_PATH);

        try {
            spnReader = new FileReader(spnFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_SPN_BY_IMSI_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(spnReader);

            XmlUtils.beginDocument(parser, "virtualSpnOverridesByImsi");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"virtualSpnOverride".equals(name)) {
                    break;
                }

                String imsipattern = parser.getAttributeValue(null, "imsipattern");
                String spn = parser.getAttributeValue(null, "name");
                Rlog.w(LOG_TAG, "test imsipattern = " + imsipattern + ", name = " + spn);
                this.CarrierVirtualSpnMapByImsi.add(new VirtualSpnByImsi(imsipattern, spn));
            }
            spnReader.close();
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-imsi parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-imsi parser " + e);
        }
    }

    public String getSpnByImsi(String mccmnc, String imsi) {
        if(mccmnc == null || imsi == null || mccmnc.isEmpty() || imsi.isEmpty())
            return null;

        VirtualSpnByImsi vsbi;
        for(int i = 0; i < this.CarrierVirtualSpnMapByImsi.size(); i++) {
            vsbi = (VirtualSpnByImsi)(this.CarrierVirtualSpnMapByImsi.get(i));
            Rlog.w(LOG_TAG, "getSpnByImsi(): imsi = " + imsi + ", pattern = " + vsbi.pattern);

            if(imsiMatches(vsbi.pattern,imsi) == true) {
                return vsbi.name;
            }
        }
        return null;
    }

    public String isOperatorMvnoForImsi(String mccmnc, String imsi) {
        if(mccmnc == null || imsi == null || mccmnc.isEmpty() || imsi.isEmpty())
            return null;

        VirtualSpnByImsi vsbi;
        String pattern;
        for(int i = 0; i < this.CarrierVirtualSpnMapByImsi.size(); i++) {
            vsbi = (VirtualSpnByImsi)(this.CarrierVirtualSpnMapByImsi.get(i));
            Rlog.w(LOG_TAG, "isOperatorMvnoForImsi(): imsi = " + imsi + ", pattern = " + vsbi.pattern);

            if(imsiMatches(vsbi.pattern,imsi) == true) {
                return vsbi.pattern;
            }
        }
        return null;
    }

   private boolean imsiMatches(String imsiDB, String imsiSIM) {
        // Note: imsiDB value has digit number or 'x' character for seperating USIM information
        // for MVNO operator. And then digit number is matched at same order and 'x' character
        // could replace by any digit number.
        // ex) if imsiDB inserted '310260x10xxxxxx' for GG Operator,
        //     that means first 6 digits, 8th and 9th digit
        //     should be set in USIM for GG Operator.
        int len = imsiDB.length();
        int idxCompare = 0;

        Rlog.w(LOG_TAG, "mvno match imsi = " + imsiSIM + "pattern = " + imsiDB);
        if (len <= 0) return false;
        if (len > imsiSIM.length()) return false;

        for (int idx=0; idx<len; idx++) {
            char c = imsiDB.charAt(idx);
            if ((c == 'x') || (c == 'X') || (c == imsiSIM.charAt(idx))) {
                continue;
            } else {
                return false;
            }
        }
        return true;
    }
    private static void loadVirtualSpnOverridesByEfPnn() {
        FileReader spnReader;
        Rlog.d(LOG_TAG, "loadVirtualSpnOverridesByEfPnn");
        final File spnFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_SPN_BY_EF_PNN_OVERRIDE_PATH);

        try {
            spnReader = new FileReader(spnFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_SPN_BY_EF_PNN_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(spnReader);

            XmlUtils.beginDocument(parser, "virtualSpnOverridesByEfPnn");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"virtualSpnOverride".equals(name)) {
                    break;
                }

                String mccmncpnn = parser.getAttributeValue(null, "mccmncpnn");
                String spn = parser.getAttributeValue(null, "name");
                Rlog.w(LOG_TAG, "test mccmncpnn = " + mccmncpnn + ", name = " + spn);
                CarrierVirtualSpnMapByEfPnn.put(mccmncpnn, spn);
            }
            spnReader.close();
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efpnn parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efpnn parser " + e);
        }
    }

    public String getSpnByEfPnn(String mccmnc, String pnn) {
        if(mccmnc == null || pnn == null || mccmnc.isEmpty() || pnn.isEmpty())
            return null;

        return CarrierVirtualSpnMapByEfPnn.get(mccmnc + pnn);
    }

    private static void loadVirtualSpnOverridesByEfGid1() {
        FileReader spnReader;
        Rlog.d(LOG_TAG, "loadVirtualSpnOverridesByEfGid1");
        final File spnFile = new File(Environment.getRootDirectory(), PARTNER_VIRTUAL_SPN_BY_EF_GID1_OVERRIDE_PATH);

        try {
            spnReader = new FileReader(spnFile);
        } catch (FileNotFoundException e) {
            Rlog.w(LOG_TAG, "Can't open " +
                    Environment.getRootDirectory() + "/" + PARTNER_VIRTUAL_SPN_BY_EF_GID1_OVERRIDE_PATH);
            return;
        }

        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setInput(spnReader);

            XmlUtils.beginDocument(parser, "virtualSpnOverridesByEfGid1");

            while (true) {
                XmlUtils.nextElement(parser);

                String name = parser.getName();
                if (!"virtualSpnOverride".equals(name)) {
                    break;
                }

                String mccmncgid1 = parser.getAttributeValue(null, "mccmncgid1");
                String spn = parser.getAttributeValue(null, "name");
                Rlog.w(LOG_TAG, "test mccmncgid1 = " + mccmncgid1 + ", name = " + spn);
                CarrierVirtualSpnMapByEfPnn.put(mccmncgid1, spn);
            }
            spnReader.close();
        } catch (XmlPullParserException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efgid1 parser " + e);
        } catch (IOException e) {
            Rlog.w(LOG_TAG, "Exception in virtual-spn-conf-by-efgid1 parser " + e);
        }
    }

    public String getSpnByEfGid1(String mccmnc, String gid1) {
        if(mccmnc == null || gid1 == null || mccmnc.isEmpty() || gid1.isEmpty())
            return null;

        return CarrierVirtualSpnMapByEfPnn.get(mccmnc + gid1);
    }

    // MVNO-API END
}
