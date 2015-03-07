/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony.cdma;

import java.util.List;
import java.util.ArrayList;
import android.util.Log;
import android.util.SparseIntArray;

/**
 * {@hide}
 */
public class TelephonyPlusCode {

    static final String LOG_TAG = "TelephonyPlusCode";

    public static final String PROPERTY_OPERATOR_MCC = "cdma.operator.mcc";
    public static final String PROPERTY_OPERATOR_SID = "cdma.operator.sid";
    public static final String PROPERTY_TIME_LTMOFFSET = "cdma.operator.ltmoffset";
    public static final String PROPERTY_ICC_CDMA_OPERATOR_MCC = "cdma.icc.operator.mcc";

    private static List<SidMccMnc> SidMccMncList_support = null;
    /*
        only contains the countries that china Telecom supports international roaming.
    */
    public static final MccIddNddSid[] MccIddNddSidMap_support = {
        new MccIddNddSid(302, "1"  , 16384, 18431, "011"  , "1" ),//canada
        new MccIddNddSid(310, "1"  , 1    , 2175 , "011"  , "1" ),//usa
        new MccIddNddSid(311, "1"  , 2304 , 7679 , "011"  , "1" ),//usa
        new MccIddNddSid(312, "1"  , 0    , 0    , "011"  , "1" ),//usa
        new MccIddNddSid(313, "1"  , 0    , 0    , "011"  , "1" ),//usa
        new MccIddNddSid(314, "1"  , 0    , 0    , "011"  , "1" ),//usa
        new MccIddNddSid(315, "1"  , 0    , 0    , "011"  , "1" ),//usa
        new MccIddNddSid(316, "1"  , 0    , 0    , "011"  , "1" ),//usa
        new MccIddNddSid(334, "52" , 24576, 25075, "00"   , "01"),//mexico
        new MccIddNddSid(334, "52" , 25100, 25124, "00"   , "01"),//mexico
        new MccIddNddSid(404, "91" , 14464, 14847, "00"   , "0" ),//India
        new MccIddNddSid(425, "972", 8448 , 8479 , "00"   , "0" ),//Israel
        new MccIddNddSid(428, "976", 15520, 15551, "002"  , "0" ),//Mongolia
        new MccIddNddSid(440, "81" , 12288, 13311, "010"  , "0" ),//Japan
        new MccIddNddSid(450, "82" , 2176 , 2303 , "00700", "0" ), //Korea(South)
        new MccIddNddSid(452, "84" , 13312, 13439, "00"   , "0" ),//Vietnam
        new MccIddNddSid(454, "852", 10640, 10655, "001"  , ""  ),//Hong Kong
        new MccIddNddSid(455, "853", 11296, 11311, "00"   , "0" ),//Macao/Macau
        new MccIddNddSid(460, "86" , 13568, 14335, "00"   , "0" ),//china
        new MccIddNddSid(460, "86" , 25600, 26111, "00"   , "0" ),//china
        new MccIddNddSid(466, "886", 13504, 13535, "005"  , ""  ),//Taiwan
        new MccIddNddSid(470, "880", 13472, 13503, "00"   , "0" ),//Bangladesh
        new MccIddNddSid(510, "62" , 10496, 10623, "001"  , "0" ),//Indonesia
    };

    public static final MccSidLtmOff[] MccSidLtmOffMap_support = {
        new MccSidLtmOff(310, 1    , -20, -10),   new MccSidLtmOff(404, 1    , 11, 11),   
        new MccSidLtmOff(310, 7    , -20, -10),   new MccSidLtmOff(404, 7    , 11, 11),

        new MccSidLtmOff(310, 13   , -20, -10),   new MccSidLtmOff(454, 13   , 16, 16),

        new MccSidLtmOff(310, 1111 , -20, -10),   new MccSidLtmOff(450, 1111 , 18, 18),
        new MccSidLtmOff(310, 1112 , -20, -10),   new MccSidLtmOff(450, 1112 , 18, 18),
        new MccSidLtmOff(310, 1113 , -20, -10),   new MccSidLtmOff(450, 1113 , 18, 18),
        new MccSidLtmOff(310, 1700 , -20, -10),   new MccSidLtmOff(450, 1700 , 18, 18),
        new MccSidLtmOff(310, 2177 , -20, -10),   new MccSidLtmOff(450, 2177 , 18, 18),
        new MccSidLtmOff(310, 2179 , -20, -10),   new MccSidLtmOff(450, 2179 , 18, 18),
        new MccSidLtmOff(310, 2181 , -20, -10),   new MccSidLtmOff(450, 2181 , 18, 18),                                                                                
        new MccSidLtmOff(310, 2183 , -20, -10),   new MccSidLtmOff(450, 2183 , 18, 18),
        new MccSidLtmOff(310, 2185 , -20, -10),   new MccSidLtmOff(450, 2185 , 18, 18),
        new MccSidLtmOff(310, 2187 , -20, -10),   new MccSidLtmOff(450, 2187 , 18, 18),
        new MccSidLtmOff(310, 2189 , -20, -10),   new MccSidLtmOff(450, 2189 , 18, 18),
        new MccSidLtmOff(310, 2191 , -20, -10),   new MccSidLtmOff(450, 2191 , 18, 18),
        new MccSidLtmOff(310, 2193 , -20, -10),   new MccSidLtmOff(450, 2193 , 18, 18),
        new MccSidLtmOff(310, 2195 , -20, -10),   new MccSidLtmOff(450, 2195 , 18, 18),
        new MccSidLtmOff(310, 2197 , -20, -10),   new MccSidLtmOff(450, 2197 , 18, 18),
        new MccSidLtmOff(310, 2199 , -20, -10),   new MccSidLtmOff(450, 2199 , 18, 18),

        new MccSidLtmOff(310, 2201 , -20, -10),   new MccSidLtmOff(450, 2201 , 18, 18),                                                                                 
        new MccSidLtmOff(310, 2203 , -20, -10),   new MccSidLtmOff(450, 2203 , 18, 18),
        new MccSidLtmOff(310, 2205 , -20, -10),   new MccSidLtmOff(450, 2205 , 18, 18),
        new MccSidLtmOff(310, 2207 , -20, -10),   new MccSidLtmOff(450, 2207 , 18, 18),
        new MccSidLtmOff(310, 2209 , -20, -10),   new MccSidLtmOff(450, 2209 , 18, 18),
        new MccSidLtmOff(310, 2211 , -20, -10),   new MccSidLtmOff(450, 2211 , 18, 18),
        new MccSidLtmOff(310, 2213 , -20, -10),   new MccSidLtmOff(450, 2213 , 18, 18),
        new MccSidLtmOff(310, 2215 , -20, -10),   new MccSidLtmOff(450, 2215 , 18, 18),
        new MccSidLtmOff(310, 2217 , -20, -10),   new MccSidLtmOff(450, 2217 , 18, 18),
        new MccSidLtmOff(310, 2219 , -20, -10),   new MccSidLtmOff(450, 2219 , 18, 18),
        new MccSidLtmOff(310, 2221 , -20, -10),   new MccSidLtmOff(450, 2221 , 18, 18),                                                                                
        new MccSidLtmOff(310, 2223 , -20, -10),   new MccSidLtmOff(450, 2223 , 18, 18),
        new MccSidLtmOff(310, 2225 , -20, -10),   new MccSidLtmOff(450, 2225 , 18, 18),
        new MccSidLtmOff(310, 2227 , -20, -10),   new MccSidLtmOff(450, 2227 , 18, 18),
        new MccSidLtmOff(310, 2229 , -20, -10),   new MccSidLtmOff(450, 2229 , 18, 18),
        new MccSidLtmOff(310, 2231 , -20, -10),   new MccSidLtmOff(450, 2231 , 18, 18),
        new MccSidLtmOff(310, 2233 , -20, -10),   new MccSidLtmOff(450, 2233 , 18, 18),
        new MccSidLtmOff(310, 2235 , -20, -10),   new MccSidLtmOff(450, 2235 , 18, 18),
        new MccSidLtmOff(310, 2237 , -20, -10),   new MccSidLtmOff(450, 2237 , 18, 18),
        new MccSidLtmOff(310, 2239 , -20, -10),   new MccSidLtmOff(450, 2239 , 18, 18),
        new MccSidLtmOff(310, 2241 , -20, -10),   new MccSidLtmOff(450, 2241 , 18, 18),                                                                         
        new MccSidLtmOff(310, 2243 , -20, -10),   new MccSidLtmOff(450, 2243 , 18, 18),
        new MccSidLtmOff(310, 2301 , -20, -10),   new MccSidLtmOff(450, 2301 , 18, 18),  
        new MccSidLtmOff(310, 2303 , -20, -10),   new MccSidLtmOff(450, 2303 , 18, 18),
        new MccSidLtmOff(310, 2369 , -20, -10),   new MccSidLtmOff(450, 2369 , 18, 18),
        new MccSidLtmOff(310, 2370 , -20, -10),   new MccSidLtmOff(450, 2370 , 18, 18),
        new MccSidLtmOff(310, 2371 , -20, -10),   new MccSidLtmOff(450, 2371 , 18, 18),

        new MccSidLtmOff(450, 2222 , 18 ,  18),   new MccSidLtmOff(404, 2222 , 11, 11),

        new MccSidLtmOff(440, 12461, 18 ,  18),   new MccSidLtmOff(470, 12461, 12, 12),   
        new MccSidLtmOff(440, 12463, 18 ,  18),   new MccSidLtmOff(470, 12463, 12, 12),   
        new MccSidLtmOff(440, 12464, 18 ,  18),   new MccSidLtmOff(470, 12464, 12, 12),
        
    };

    public static final SparseIntArray MOBILE_NUMBER_SPEC_MAP = new SparseIntArray();
    static {
        MOBILE_NUMBER_SPEC_MAP.put(13, 86); 
        MOBILE_NUMBER_SPEC_MAP.put(15, 86); 
        MOBILE_NUMBER_SPEC_MAP.put(18, 86);
        MOBILE_NUMBER_SPEC_MAP.put(99, 91);
    }

    public static final String[] IDD_MAP_2_CHAR = new String[] {
        "00",
    };

    public static final String[] IDD_MAP_3_CHAR = new String[] {
        "000","001","002","005","009","010", 
        "011","020","810"
    };
    
    public static final String[] IDD_MAP_4_CHAR = new String[] {
        "0011","0015",
    };

    public static final String[] IDD_MAP_5_CHAR = new String[] {
        "00700", "17700"
    };

    public static List<SidMccMnc> getSidMccMncList() {
        Log.d(LOG_TAG, "[getSidMccMncList] getSidMccMncList = " + (SidMccMncList_support == null));

        if(SidMccMncList_support == null) {
            InitSidMccMncList();
        }

        return SidMccMncList_support;
    }

    private static void InitSidMccMncList() {
        Log.d(LOG_TAG, "[InitSidMccMncList] InitSidMccMncList");

        SidMccMncList_support = new ArrayList<SidMccMnc>();
        
        Add(2, 310010);      //Verizon
        Add(3, 310010);      //Verizon
        Add(4, 310010);      //Verizon
        Add(5, 310730);      //U.S.Cellular
        Add(6, 310010);      //Verizon
        Add(8, 310010);      //Verizon
        Add(10, 310070);     //Cingular
        Add(12, 310010);      //Verizon
        Add(15, 310010);      //Verizon
        Add(17, 310010);      //Verizon
        Add(18, 310010);      //Verizon
        Add(20, 310010);      //Verizon
        Add(21, 310010);      //Verizon
        Add(22, 310010);      //Verizon
        Add(26, 310010);      //Verizon
        Add(28, 310010);      //Verizon
        Add(30, 310010);      //Verizon
        Add(32, 310010);      //Verizon
        Add(40, 310010);      //Verizon
        Add(41, 310010);      //Verizon
        Add(42, 310500);       //Alltel
        Add(44, 310070);      //Cingular
        Add(45, 310500);      //Alltel
        Add(46, 310070);      //Cingular
        Add(48, 310010);      //Verizon
        Add(51, 310010);      //Verizon
        Add(53, 310500);       //Alltel
        Add(54, 310500);       //Alltel
        Add(56, 310010);      //Verizon
        Add(57, 310500);       //Alltel
        Add(58, 310010);      //Verizon
        Add(59, 310010);      //Verizon
        Add(60, 310010);      //Verizon
        Add(64, 310010);      //Verizon
        Add(65, 310010);      //Verizon
        Add(69, 310010);      //Verizon
        Add(73, 310010);      //Verizon
        Add(74, 310500);      //Alltel
        Add(78, 310010);      //Verizon
        Add(79, 310500);      //Alltel
        Add(80, 310010);      //Verizon
        Add(81, 310070);      //Cingular
        Add(83, 310500);       //Alltel
        Add(84, 310500);       //Alltel
        Add(85, 310500);       //Alltel
        Add(81, 310070);      //Cingular
        Add(83, 310500);      //Alltel
        Add(84, 310500);      //Alltel
        Add(85, 310500);      //Alltel
        Add(86, 310010);      //Verizon
        Add(92, 310010);      //Verizon
        Add(93, 310010);      //Verizon
        Add(94, 310010);      //Verizon
        Add(95, 310010);      //Verizon
        Add(96, 310010);      //Verizon
        Add(97, 310500);      //Alltel
        Add(100, 310500);      //Alltel
        Add(106, 310070);      //Cingular
        Add(110, 310010);      //Verizon
        Add(112, 310010);      //Verizon
        Add(113, 310010);      //Verizon
        Add(114, 310500);      //Alltel
        Add(116, 310500);      //Alltel
        Add(119, 310010);      //Verizon
        Add(120, 310500);      //Alltel
        Add(126, 310500);      //Alltel
        Add(127, 310010);      //Verizon
        Add(130, 310500);      //Alltel
        Add(133, 310010);      //Verizon
        Add(137, 310010);      //Verizon
        Add(138, 310070);      //Cingular
        Add(139, 310010);      //Verizon
        Add(140, 310010);      //Verizon
        Add(142, 310500);      //Alltel
        Add(143, 310010);       //Verizon
        Add(144, 310500);       //Alltel
        Add(150, 310010);       //Verizon
        Add(152, 310500);       //Alltel
        Add(154, 310010);      //Verizon
        Add(156, 310500);      //Alltel
        Add(162, 310010);       //Verizon
        Add(163, 310010);       //Verizon
        Add(165, 310010);       //Verizon
        Add(166, 310730);       //U.S.Cellular
        Add(169, 310500);        //Alltel
        Add(179, 310010);        //Verizon
        Add(180, 310010);        //Verizon
        Add(181, 310010);        //Verizon
        Add(182, 310500);        //Alltel
        Add(186, 310010);        //Verizon
        Add(188, 310500);        //Alltel
        Add(189, 310010);        //Verizon
        Add(190, 310010);        //Verizon
        Add(204, 310500);        //Alltel
        Add(205, 310500);        //Alltel
        Add(208, 310500);        //Alltel
        Add(212, 310500);        //Alltel
        Add(214, 310010);        //Verizon
        Add(215, 310070);         //Cingular
        Add(216, 310500);         //Alltel
        Add(220, 310500);         //Alltel
        Add(222, 310010);        //Verizon
        Add(224, 310010);        //Verizon
        Add(226, 310010);        //Verizon
        Add(228, 310010);        //Verizon
        Add(229, 310070);        //Cingular
        Add(234, 310050);        //ACS Wireless
        Add(240, 310500);        //Alltel
        Add(241, 310010);        //Verizon
        Add(244, 310500);        //Alltel
        Add(249, 311130);        //Amarillo Cellular
        Add(250, 310010);        //Verizon
        Add(256, 310500);        //Alltel
        Add(260, 310500);        //Alltel
        Add(262, 310010);         //Verizon
        Add(264, 0);             //Cellular South
        Add(266, 310010);         //Verizon
        Add(272, 310010);         //Verizon
        Add(276, 310010);         //Verizon
        Add(277, 310030);         //CENT USA
        Add(281, 310500);         //Alltel
        Add(284, 310010);         //Verizon
        Add(285, 310500);         //Alltel
        Add(286, 310010);         //Verizon
        Add(294, 310010);         //Verizon
        Add(298, 310730);         //U.S.Cellular
        Add(299, 310010);         //Verizon
        Add(300, 310010);         //Verizon
        Add(302, 310500);         //Alltel
        Add(312, 310500);         //Alltel
        Add(316, 310010);          //Verizon
        Add(318, 310500);          //Alltel
        Add(319, 0);              //Midwest Wireless
        Add(323, 310010);         //Verizon
        Add(324, 0);              //Pioneer/Enid Cellular
        Add(328, 310010);         //Verizon
        Add(329, 310010);         //Verizon
        Add(330, 310010);         //Verizon
        Add(340, 310500);         //Alltel
        Add(341, 0);              //Dobson Cellular Systems
        Add(342, 310500);         //Alltel
        Add(348, 310500);         //Alltel
        Add(349, 310010);         //Verizon
        Add(350, 310500);          //Alltel
        Add(359, 310070);          //Cingular
        Add(361, 310070);          //Cingular Wireless
        Add(362, 42502);           //Cellcom Israel
        Add(364, 310730);          //U.S.Cellular
        Add(368, 310500);          //Alltel
        Add(370, 310500);          //Alltel
        Add(371, 310500);          //Alltel
        Add(374, 310500);          //Alltel
        Add(376, 310500);          //Alltel
        Add(377, 310010);          //Verizon
        Add(384, 310730);          //U.S.Cellular
        Add(386, 310500);          //Alltel
        Add(392, 310500);          //Alltel
        Add(396, 310500);          //Alltel
        Add(400, 310070);          //Cingular
        Add(403, 310030);          //CENT USA
        Add(404, 310010);          //Verizon
        Add(414, 310070);          //Cingular
        Add(416, 310500);          //Alltel
        Add(418, 310500);          //Alltel
        Add(424, 310500);          //Alltel
        Add(426, 310070);          //Cingular
        Add(428, 310010);          //Verizon
        Add(436, 310730);          //U.S.Cellular
        Add(440, 310500);          //Alltel
        Add(443, 310010);          //Verizon
        Add(444, 310500);          //Alltel
        Add(447, 310010);          //Verizon
        Add(448, 310500);          //Alltel
        Add(451, 310500);          //Alltel
        Add(463, 310070);          //Cingular Wireless
        Add(478, 310500);          //Alltel
        Add(486, 310010);          //Verizon
        Add(487, 310500);          //Alltel
        Add(498, 310010);          //Verizon
        Add(502, 310010);          //Verizon
        Add(506, 310010);          //Verizon
        Add(510, 310180);          //West Central Wireless
        Add(511, 310500);          //Alltel
        Add(520, 310500);          //Alltel
        Add(528, 310010);          //Verizon
        Add(529, 310500);          //Alltel
        Add(530, 310010);          //Verizon
        Add(532, 310010);          //Verizon
        Add(539, 310010);          //Verizon
        Add(544, 310500);          //Alltel
        Add(546, 310500);          //Alltel
        Add(550, 310500);          //Alltel
        Add(555, 310500);          //Alltel
        Add(574, 310730);          //U.S.Cellular
        Add(578, 310500);          //Alltel
        Add(579, 310070);          //Cingular Wireless
        Add(580, 310730);          //U.S.Cellular
        Add(587, 310070);          //Cingular Wireless
        Add(607, 310070);          //Cingular
        Add(1015, 310010);         //Verizon
        Add(1018, 310050);         //ACS Wireless
        Add(1022, 310050);         //ACS Wireless
        Add(1024, 310350);         //Mohave Cellular
        Add(1026, 310010);         //Verizon
        Add(1027, 310320);         //Smith Bagley
        Add(1028, 310010);         //Verizon
        Add(1029, 310500);         //Alltel
        Add(1030, 310010);         //Verizon
        Add(1034, 310010);         //Verizon
        Add(1038, 310500);         //Alltel
        Add(1055, 310070);         //Cingular
        Add(1058, 310500);         //Alltel
        Add(1060, 310010);         //Verizon
        Add(1064, 311590);         //Golden State Cellular
        Add(1069, 310500);         //Alltel
        Add(1083, 310010);         //Verizon
        Add(1086, 310010);         //Verizon
        Add(1088, 310010);         //Verizon
        Add(1093, 310500);         //Alltel
        Add(1101, 310500);         //Alltel
        Add(1124, 310500);         //Alltel
        Add(1129, 310010);         //Verizon
        Add(1139, 310010);         //Verizon
        Add(1148, 310500);         //Alltel
        Add(1151, 310010);         //Verizon
        Add(1153, 310010);         //Verizon
        Add(1155, 310070);         //Cingular Wireless
        Add(1164, 310010);         //Verizon
        Add(1165, 310500);         //Alltel
        Add(1173, 310500);         //Alltel
        Add(1174, 310010);         //Verizon
        Add(1180, 310010);         //Verizon
        Add(1189, 310010);         //Verizon
        Add(1192, 310500);         //Alltel
        Add(1200, 310730);         //U.S.Cellular
        Add(1211, 310730);         //U.S.Cellular
        Add(1212, 311430);         //Cellular 29 Plus
        Add(1213, 310730);         //U.S.Cellular
        Add(1215, 310730);         //U.S.Cellular
        Add(1216, 0);              //Midwest Wireless
        Add(1220, 310010);         //Verizon
        Add(1232, 0);              //Midwest Wireless
        Add(1234, 0);              //Midwest Wireless
        Add(1236, 0);              //Midwest Wireless
        Add(1255, 310890);         //Rural Cellular
        Add(1258, 310500);         //Alltel
        Add(1267, 310890);         //Rural Cellular
        Add(1271, 310500);         //Alltel
        Add(1272, 310730);         //U.S.Cellular
        Add(1280, 311440);         //Bluegrass Cellular
        Add(1290, 0);              //Appalachian Wireless
        Add(1317, 310730);         //U.S.Cellular
        Add(1320, 310730);         //U.S.Cellular
        Add(1332, 310500);         //Alltel
        Add(1333, 0);              //Dobson Cellular Systems
        Add(1335, 0);              //Dobson Cellular Systems
        Add(1336, 310500);         //Alltel
        Add(1337, 0);              //Dobson Cellular Systems
        Add(1338, 310500);         //Alltel
        Add(1341, 310030);         //CENT USA
        Add(1345, 310030);         //CENT USA
        Add(1350, 311050);         //Thumb Cellular
        Add(1367, 310500);         //Alltel
        Add(1369, 310500);         //Alltel
        Add(1370, 0);              //??
        Add(1372, 0);              //Midwest Wireless
        Add(1375, 310500);         //Alltel
        Add(1382, 0);              //Cellular South
        Add(1383, 310500);         //Alltel
        Add(1385, 310500);         //Alltel
        Add(1393, 310500);         //Alltel
        Add(1394, 0);              //Cellular South
        Add(1396, 311420);         //Northwest Missouri Cellular
        Add(1399, 310730);         //U.S.Cellular
        Add(1400, 310500);         //Alltel
        Add(1403, 310730);         //U.S.Cellular
        Add(1406, 310730);         //U.S.Cellular
        Add(1408, 310010);         //Verizon
        Add(1411, 310730);         //U.S.Cellular
        Add(1419, 310730);         //U.S.Cellular
        Add(1423, 310730);         //U.S.Cellular
        Add(1425, 310730);         //U.S.Cellular
        Add(1434, 310010);         //Verizon
        Add(1441, 310500);         //Alltel
        Add(1453, 311030);         //Indigo Wireless
        Add(1465, 310730);         //U.S.Cellular
        Add(1466, 310500);         //Alltel
        Add(1473, 310500);         //Alltel
        Add(1484, 310730);         //U.S.Cellular
        Add(1493, 310500);         //Alltel
        Add(1496, 310100);         //Plateau Wireless
        Add(1499, 310500);         //Alltel
        Add(1500, 310100);         //Plateau Wireless
        Add(1504, 310100);         //Plateau Wireless
        Add(1506, 310010);         //Verizon
        Add(1508, 310010);         //Verizon
        Add(1516, 310010);         //Verizon
        Add(1522, 310130);         //Carolina West Wireless
        Add(1528, 310500);         //Alltel
        Add(1530, 310500);         //Alltel
        Add(1532, 310500);         //Alltel
        Add(1534, 310500);         //Alltel
        Add(1536, 310500);         //Alltel
        Add(1538, 310500);         //Alltel
        Add(1540, 310500);         //Alltel
        Add(1541, 310730);         //U.S.Cellular
        Add(1542, 310500);         //Alltel
        Add(1544, 310500);         //Alltel
        Add(1546, 310500);         //Alltel
        Add(1548, 310010);         //Verizon
        Add(1559, 310010);         //Verizon
        Add(1567, 310010);         //Verizon
        Add(1574, 310500);         //Alltel
        Add(1590, 0);              //Dobson Cellular Systems
        Add(1595, 310730);         //U.S.Cellular
        Add(1598, 311080);         //Pine Cellular
        Add(1607, 310730);         //U.S.Cellular
        Add(1608, 0);              //Ramcell
        Add(1609, 310890);         //Rural Cellular
        Add(1610, 310730);         //U.S.Cellular
        Add(1640, 310500);         //Alltel
        Add(1643, 310730);         //U.S.Cellular
        Add(1645, 0);              //Triton
        Add(1650, 310500);         //Alltel
        Add(1652, 310500);         //Alltel
        Add(1661, 310500);         //Alltel
        Add(1692, 310950);         //XIT
        Add(1696, 310100);         //Plateau Wireless
        Add(1703, 310500);         //Alltel
        Add(1739, 310500);         //Alltel
        Add(1740, 310010);         //Verizon
        Add(1741, 310500);         //Alltel
        Add(1742, 310020);         //UnionTel
        Add(1748, 310010);         //Verizon
        Add(1749, 310010);         //Verizon
        Add(1759, 310500);         //Alltel
        Add(1776, 310010);         //Verizon
        Add(1779, 310730);         //U.S.Cellular
        Add(1784, 310730);         //U.S.Cellular
        Add(1794, 310730);         //U.S.Cellular
        Add(1802, 310730);         //U.S.Cellular
        Add(1812, 0);              //Midwest Wireless
        Add(1818, 310500);         //Alltel
        Add(1823, 310500);         //Alltel
        Add(1825, 310500);         //Alltel
        Add(1826, 310010);         //Verizon
        Add(1827, 310010);         //Verizon
        Add(1828, 310020);         //UnionTel
        Add(1830, 310010);         //Verizon
        Add(1868, 310980);         //AT&amp;T
        Add(1892, 310860);         //Five Star Wireless
        Add(1902, 310500);         //Alltel
        Add(1912, 310010);         //Verizon
        Add(1922, 311150);         //Wilkes Cellular
        Add(1932, 311000);         //Mid-Tex Cellular
        Add(1949, 311040);         //Commnet
        Add(1970, 310540);         //Oklahoma Western Telephone
        Add(1976, 0);              //Brazos Celllular
        Add(1989, 310500);         //Alltel
        Add(1996, 0);              //Cellular South
        Add(2038, 310500);         //Alltel
        Add(2058, 310010);         //Verizon
        Add(2115, 310010);         //Verizon
        Add(2119, 310010);         //Verizon
        Add(2129, 310890);         //Rural Cellular
        Add(2141, 310730);         //U.S.Cellular
        Add(3000, 311040);         //Commnet
        Add(3034, 311040);         //Commnet
        Add(3066, 310010);         //Verizon
        Add(3076, 310020);         //UnionTel
        Add(3226, 310500);         //Alltel
        Add(3462, 0);              //Custer Telephone Cooperative
        Add(4103, 310120);         //Sprint
        Add(4106, 310120);         //Sprint
        Add(4107, 310120);         //Sprint
        Add(4110, 310500);         //Alltel
        Add(4119, 310010);         //Verizon
        Add(4120, 310120);         //Sprint
        Add(4121, 310120);         //Sprint
        Add(4124, 310120);         //Sprint
        Add(4126, 310120);         //Sprint
        Add(4132, 310120);         //Sprint
        Add(4135, 310120);         //Sprint
        Add(4138, 310010);         //Verizon
        Add(4139, 310120);         //Sprint
        Add(4144, 310120);         //Sprint
        Add(4145, 310120);         //Sprint
        Add(4148, 310120);         //Sprint
        Add(4151, 310120);         //Sprint
        Add(4152, 310010);         //Verizon
        Add(4153, 310120);         //Sprint
        Add(4154, 310010);         //Verizon
        Add(4155, 310120);         //Sprint
        Add(4157, 310120);         //Sprint
        Add(4159, 310120);         //Sprint
        Add(4160, 310010);         //Verizon
        Add(4162, 310120);         //Sprint
        Add(4164, 310120);         //Sprint
        Add(4166, 310120);         //Sprint
        Add(4168, 310120);         //Sprint
        Add(4170, 310120);         //Sprint
        Add(4171, 310120);         //Sprint
        Add(4174, 310120);         //Sprint
        Add(4180, 310120);         //Sprint
        Add(4181, 310120);         //Sprint
        Add(4182, 310010);         //Verizon
        Add(4183, 310120);         //Sprint
        Add(4186, 310120);         //Sprint
        Add(4188, 310120);         //Sprint
        Add(4190, 310120);         //Sprint
        Add(4192, 310010);         //Verizon
        Add(4195, 310120);         //Sprint
        Add(4198, 310120);         //Sprint
        Add(4199, 0);              // 3 Rivers Wireless
        Add(4225, 310500);         //Alltel
        Add(4274, 310120);         //Sprint
        Add(4292, 310016);         //Cricket
        Add(4325, 310016);         //Cricket
        Add(4376, 310120);         //Sprint
        Add(4381, 310016);         //Cricket
        Add(4384, 310120);         //Sprint
        Add(4390, 310120);         //Sprint
        Add(4396, 310120);         //Sprint
        Add(4413, 310016);         //Cricket
        Add(4418, 310120);         //Sprint
        Add(4509, 310016);         //Cricket
        Add(4518, 310016);         //Cricket
        Add(4535, 310016);         //Cricket
        Add(4622, 310120);         //Sprint
        Add(4647, 310016);         //Cricket
        Add(4654, 310120);         //Sprint
        Add(4667, 310016);         //Cricket
        Add(4693, 310016);         //Cricket
        Add(4694, 310120);         //Sprint
        Add(4743, 310016);         //Cricket
        Add(4771, 310016);         //Cricket
        Add(4809, 310016);         //Cricket
        Add(4812, 310120);         //Sprint
        Add(4828, 0);              //Qwest
        Add(4857, 310016);         //Cricket
        Add(4923, 310016);         //Cricket
        Add(4928, 0);              //Qwest
        Add(4961, 310016);         //Cricket
        Add(4973, 310016);         //Cricket
        Add(4979, 0);              //??
        Add(4982, 310120);         //Sprint
        Add(5019, 310016);         //Cricket
        Add(5027, 310016);         //Cricket
        Add(5105, 310016);         //Cricket
        Add(5116, 310120);         //Sprint
        Add(5117, 310016);         //Cricket
        Add(5142, 310120);         //Sprint
        Add(5145, 310016);         //Cricket
        Add(5173, 310016);         //Cricket
        Add(5269, 310050);         //ACS Wireless
        Add(5351, 0);              //Qwest
        Add(5361, 310016);         //Cricket
        Add(5386, 310016);         //Cricket
        Add(5450, 310016);         //Cricket
        Add(5458, 310016);         //Cricket
        Add(5461, 0);              //Qwest
        Add(5510, 310050);         //ACS Wireless
        Add(5513, 310050);         //ACS Wireless
        Add(5540, 310016);         //Cricket
        Add(5586, 310016);         //Cricket
        Add(5618, 310016);         //Cricket
        Add(5660, 0);              //Cellular South
        Add(5667, 310500);         //Alltel
        Add(5682, 310050);         //ACS Wireless
        Add(5685, 310050);         //ACS Wireless
        Add(5756, 310016);         //Cricket
        Add(5908, 310730);         //U.S.Cellular
        Add(5911, 310730);         //U.S.Cellular
        Add(5914, 310016);         //Cricket
        Add(5945, 310016);         //Cricket
        Add(6249, 310016);         //Cricket
        Add(6323, 310016);         //Cricket
        Add(6371, 310016);         //Cricket
        Add(6415, 310016);         //Cricket
        Add(6425, 310016);         //Cricket
        Add(6439, 310016);         //Cricket
        Add(6441, 310016);         //Cricket
        Add(6488, 310730);         //U.S.Cellular
        Add(6490, 311440);         //Bluegrass Cellular
        Add(7316, 311040);         //Commnet
        Add(8097, 0);              //Beuda Digita
        Add(8176, 0);              //Oceanic Digital Jamaica
        Add(8832, 0);              //Codetel Comunicaciones Moviles
        Add(8861, 0);              //MoCelCo
        Add(8863, 311040);         //Commnet
        Add(8950, 311040);         //Commnet
        Add(9246, 311040);         //Commnet
        Add(9332, 311040);         //Commnet
        Add(9562, 311040);         //Commnet
        Add(16384, 30286);         //Telus
        Add(16390, 302702);        //MT&T Mobility
        Add(16408, 0);             //NBTel Mobility
        Add(16410, 302654);         //SaskTel Mobility
        Add(16412, 302654);         //SaskTel Mobility
        Add(16414, 302703);         //NewTel Mobility
        Add(16418, 0);             //ThunderBay Mobility
        Add(16420, 302610);        //Bell Mobility
        Add(16422, 30286);         //Telus
        Add(16428, 302660);         //MTS
        Add(16430, 0);             //IslandTel Mobility
        Add(16462, 0);             //NorTel Mobility
        Add(16472, 0);             //NMI Mobility
        Add(25100, 0);             //Operadora Unefon (Mexico)
        Add(30524, 310500);        //Alltel
        Add(30635, 310500);        //Alltel
        Add(31092, 311350);        //Sagebrush Cellular
//      Add(31092, 0);             //Triangle Communication Systems
    }

    private static void Add(int mSid, int mMccMnc) {
        SidMccMnc mSidMccMnc = new SidMccMnc(mSid, mMccMnc);
        SidMccMncList_support.add(mSidMccMnc);
    }

}
