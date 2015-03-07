package com.mediatek.nfc.addon;

import com.mediatek.nfc.configutil.ConfigUtil;

public class NfcRuntimeOptions {
	static public ConfigUtil.IParser sConfigFileParser;
	
	static private final String CFG_FILE_PATH = "system/etc/nfcse.cfg";
	static private final String CFG_FILE_RULES[] = {
		/**
		 *	MultiSE related config
		 */
		"SWP1=1:SIM1=1,SIM2=2",
		"SWP2=2:SIM1=1,SIM2=2,ESE=3",
		"SD=3:NO=0,YES=1",
		"ESE=4:NO=0,YES=1",
		"NON_NFC_SIM_POPUP=5:NO=0,YES=1",
		"EVT_BROADCAST_AC=6:DEFAULT=0,BYPASS=1",
		"BUNDLE_SIM_STATE=7:NO=0,YES=1",
		"NO_EMU_IN_FLYMODE=8:NO=0,YES=1",
		"BEAM_SEND_FAIL_CNT=9:OPT0=0,OPT1=1,OPT2=2,OPT3=3,OPT4=4,OPT5=5,OPT6=6,OPT7=7,OPT8=8,OPT9=9",
		"BEAM_RECV_FAIL_CNT=10:OPT0=0,OPT1=1,OPT2=2,OPT3=3,OPT4=4,OPT5=5,OPT6=6,OPT7=7,OPT8=8,OPT9=9",
		"BEAM_SEND_SLEEP_TIME=11:OPT0=0,OPT1=1,OPT2=2,OPT3=3,OPT4=4,OPT5=5,OPT6=6,OPT7=7,OPT8=8,OPT9=9",
		"BEAM_RECV_SLEEP_TIME=12:OPT0=0,OPT1=1,OPT2=2,OPT3=3,OPT4=4,OPT5=5,OPT6=6,OPT7=7,OPT8=8,OPT9=9",
		"BEAM_SETUP_CONNECTIION_TIME=13:OPT0=0,OPT1=1,OPT2=2,OPT3=3,OPT4=4,OPT5=5,OPT6=6,OPT7=7,OPT8=8,OPT9=9",
		"GSMA_EVT_BROADCAST=14:NO=0,YES=1",
		"SEAPI_SUPPORT_CMCC=15:NO=0,YES=1"		
	};
	
	/**
	 * Options 
	 */
	static private final int NON_NFC_SIM_POPUP = 5;
	static private final int EVT_BROADCAST_AC = 6;
	static private final int BUNDLE_SIM_STATE = 7;
	static private final int NO_EMU_IN_FLYMODE = 8;
	static private final int BEAM_SEND_FAIL_CNT= 9;
	static private final int BEAM_RECV_FAIL_CNT= 10;
	static private final int BEAM_SEND_SLEEP_TIME= 11;
	static private final int BEAM_RECV_SLEEP_TIME= 12;
    static private final int BEAM_SETUP_CONNECTIION_TIME= 13;
	static private final int GSMA_EVT_BROADCAST = 14;
	static private final int SEAPI_SUPPORT_CMCC = 15;
	
	/**
	 * Values
	 */
	static private final int NO = 0;
	static private final int YES = 1;
	static private final int TABLE_BEAM_SEND_FAIL_CNT[] = {0, 5, 10, 30, 40, 50, 60, 70, 80, 90, 100};
	static private final int TABLE_BEAM_RECV_FAIL_CNT[] = {0, 5, 10, 30, 40, 50, 60, 70, 80, 90, 100};
	static private final int TABLE_BEAM_SEND_SLEEP_TIME[] = {0, 2, 5, 10, 15, 20, 30, 40, 50, 60, 100};
	static private final int TABLE_BEAM_RECV_SLEEP_TIME[] = {0, 2, 5, 10, 15, 20, 30, 40, 50, 60, 100};
	
	static private final int TABLE_BEAM_SETUP_CONNECTION_TIME[] = {    0, 20000, 25000, 30000, 35000, 
                                                                   40000, 45000, 50000, 55000, 60000};
	/**
	 * EVT_BROADCAST_AC values
	 */
	static private final int EVT_AC_DEFAULT = 0;
	static private final int EVT_AC_BYPASS = 1;
	
	static {
		sConfigFileParser = ConfigUtil.createParser(CFG_FILE_RULES);
		sConfigFileParser.parse(CFG_FILE_PATH);
	}
	
	static public ConfigUtil.IParser getParser() {
		return sConfigFileParser;
	}
	
	static public boolean isSupportNonNfcSimPopup() {
		int userConfig[] = new int[1];
		boolean ret = false;
		try {
			if (sConfigFileParser.get(NON_NFC_SIM_POPUP, userConfig)) {
				ret = (userConfig[0] == YES) ? true : false;
			}
		} catch (Exception e) {}
		return ret;
	}
	
	static public boolean isEvtBroadcastAcBypass() {
		int userConfig[] = new int[1];
		boolean ret = false;
		try {
			if (sConfigFileParser.get(EVT_BROADCAST_AC, userConfig)) {
				ret = (userConfig[0] == EVT_AC_BYPASS) ? true : false;
			}
		} catch (Exception e) {}
		return ret;	
	}
	
	static public boolean isBundleSimState() {
		int userConfig[] = new int[1];
		boolean ret = false;
		try {
			if (sConfigFileParser.get(BUNDLE_SIM_STATE, userConfig)) {
				ret = (userConfig[0] == YES) ? true : false;
			}
		} catch (Exception e) {}
		return ret;
	}
	
	static public boolean isNoCardEmuInFlyMode() {
		int userConfig[] = new int[1];
		boolean ret = false;
		try {
			if (sConfigFileParser.get(NO_EMU_IN_FLYMODE, userConfig)) {
				ret = (userConfig[0] == YES) ? true : false;
			}		} catch (Exception e) {}
		return ret;	
	}

	static public boolean isSeapiSupportCmcc() {
		int userConfig[] = new int[1];
		boolean ret = false;
		try {
			if (sConfigFileParser.get(SEAPI_SUPPORT_CMCC, userConfig)) {
				ret = (userConfig[0] == YES) ? true : false;
			}		} catch (Exception e) {}
		return ret;	
	}
	
	static public int getBeamPlusSendFailCnt() {
		int userConfig[] = new int[1];
		int ret = 0;
		try {
			if (sConfigFileParser.get(BEAM_SEND_FAIL_CNT, userConfig)) {
				ret = TABLE_BEAM_SEND_FAIL_CNT[userConfig[0]];
			}		} catch (Exception e) {}
		return ret;	
	}
	
	static public int getBeamPlusRecvFailCnt() {
		int userConfig[] = new int[1];
		int ret = 0;
		try {
			if (sConfigFileParser.get(BEAM_RECV_FAIL_CNT, userConfig)) {
				ret = TABLE_BEAM_RECV_FAIL_CNT[userConfig[0]];
			}		} catch (Exception e) {}
		return ret;	
	}
	
	static public int getBeamPlusSendFailSleepTime() {
		int userConfig[] = new int[1];
		int ret = 0;
		try {
			if (sConfigFileParser.get(BEAM_SEND_SLEEP_TIME, userConfig)) {
				ret = TABLE_BEAM_SEND_SLEEP_TIME[userConfig[0]];
			}		} catch (Exception e) {}
		return ret;	
	}
	
	static public int getBeamPlusRecvFailSleepTime() {
		int userConfig[] = new int[1];
		int ret = 0;
		try {
			if (sConfigFileParser.get(BEAM_RECV_SLEEP_TIME, userConfig)) {
				ret = TABLE_BEAM_RECV_SLEEP_TIME[userConfig[0]];
			}		} catch (Exception e) {}
		return ret;	
	}

    static public int getBeamPlusSetupConnectionTimeoutValue() {
        int userConfig[] = new int[1];
        int ret = 0;
        try {
            if (sConfigFileParser.get(BEAM_SETUP_CONNECTIION_TIME, userConfig)) {
                ret = TABLE_BEAM_SETUP_CONNECTION_TIME[userConfig[0]];
            }       } catch (Exception e) {}
        return ret; 
    }

	static public boolean isGsmaEvtBroadcast() {
		int userConfig[] = new int[1];
		boolean ret = false;
		try {
			if (sConfigFileParser.get(GSMA_EVT_BROADCAST, userConfig)) {
				ret = (userConfig[0] == YES) ? true : false;
			}
		} catch (Exception e) {}
		return ret;	
	}    

}
