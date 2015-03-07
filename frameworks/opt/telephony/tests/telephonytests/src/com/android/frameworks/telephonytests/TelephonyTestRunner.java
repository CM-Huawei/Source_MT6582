package com.android.frameworks.telephonytests;

import com.android.internal.telephony.ATResponseParserTest;
//import com.android.internal.telephony.AdnRecordTest;
import com.android.internal.telephony.ApnSettingTest;
//import com.android.internal.telephony.CallerInfoTest;
import com.android.internal.telephony.GsmAlphabetTest;
import com.android.internal.telephony.IccServiceTableTest;
import com.android.internal.telephony.IntRangeManagerTest;
import com.android.internal.telephony.MccTableTest;
import com.android.internal.telephony.NeighboringCellInfoTest;
//import com.android.internal.telephony.PhoneNumberUtilsTest;
//import com.android.internal.telephony.PhoneNumberWatcherTest;
import com.android.internal.telephony.SimUtilsTest;
import com.android.internal.telephony.TelephonyUtilsTest;
import com.android.internal.telephony.gsm.GSMPhoneTest;
import com.android.internal.telephony.gsm.UsimServiceTableTest;


import junit.framework.TestSuite;
import android.test.InstrumentationTestRunner;
import android.util.Log;

@interface abc {
	int test1() default 0;
}
public class TelephonyTestRunner extends InstrumentationTestRunner {
	
	private final static String TAG = "TelephonyTestRunner";

	@abc (test1 = 1)
	@Override
	public TestSuite getAllTests() {
		// TODO Auto-generated method stub
		TestSuite suite = new TestSuite();
		
		suite.addTestSuite(ATResponseParserTest.class);
//		suite.addTestSuite(AdnRecordTest.class);
		suite.addTestSuite(ApnSettingTest.class);
//		suite.addTestSuite(CallerInfoTest.class);
//		suite.addTestSuite(GsmAlphabetTest.class);
		suite.addTestSuite(IccServiceTableTest.class);
//		suite.addTestSuite(IntRangeManagerTest.class);
		suite.addTestSuite(MccTableTest.class);
		suite.addTestSuite(NeighboringCellInfoTest.class);
//		suite.addTestSuite(PhoneNumberUtilsTest.class);
//		suite.addTestSuite(PhoneNumberWatcherTest.class);
		suite.addTestSuite(SimUtilsTest.class);
		suite.addTestSuite(TelephonyUtilsTest.class);
		suite.addTestSuite(GSMPhoneTest.class);
		suite.addTestSuite(UsimServiceTableTest.class);

		return suite;
	}
	
	private void log(String s) {
        Log.d(TAG, s);
    }
}
