#!/bin/sh

TOPFILE=build/core/envsetup.mk
# We redirect cd to /dev/null in case it's aliased to
# a command that prints something as a side-effect
# (like pushd)
HERE=`/bin/pwd`
T=
while [ \( ! \( -f $TOPFILE \) \) -a \( $PWD != "/" \) ]; do
	cd .. > /dev/null
	T=`PWD= /bin/pwd`
done
if [ -f "$T/$TOPFILE" ]; then
	SRC_ROOT=$T
else
	echo "Error: source tree was not found."
	exit
fi

PRODUCT=`cat $SRC_ROOT/.product`
PRODUCT_OUT=$SRC_ROOT/out/target/product/$PRODUCT
#INTERMEDIATES=$SRC_ROOT/out/target/common/obj/APPS/Weather3DWidget_intermediates
TEST_RUNNER=com.zutubi.android.junitreport.JUnitReportTestRunner

if adb remount
then
  # Prevent insufficient storage space
  adb shell rm -r /data/core

  # Copy binary to device
  #adb uninstall com.android.contacts
  #adb install -r $PRODUCT_OUT/system/app/Weather3DWidget.apk

  # mediatek_external_VT	
  # VT	
  adb push $PRODUCT_OUT/system/lib/libmtk_vt_utils.so /system/lib
  adb push $PRODUCT_OUT/system/lib/libmtk_vt_client.so /system/lib
  adb push $PRODUCT_OUT/system/lib/libmtk_vt_em.so /system/lib
  adb push $PRODUCT_OUT/system/lib/libmtk_vt_service.so /system/lib
  adb push $PRODUCT_OUT/system/lib/libmtk_vt_swip.so /system/lib
  adb shell sync
  
  # install test APK
  adb uninstall com.mtk.sanitytest
  adb install -r $SRC_ROOT/___test_report/VTCallTest.apk

  adb shell mkdir data/data/com.mtk.vtcall/shared_prefs/
  adb push $SRC_ROOT/___test_report/vtcalltest_config.xml data/data/com.mtk.vtcall/shared_prefs/
  adb shell sync

  # before restart, clean log
  # if clean log after restart, logger will stop
  adb shell rm -rf /sdcard/mtklog
  adb shell rm -rf /sdcard2/mtklog  
  rm -rf $SRC_ROOT/___test_report/mtklog_vt  
    
  # restart VM then system will use new Weather3DWidget.apk
  #adb shell "stop;sleep 5;start"
  #sleep 30
  
  # restart phone 
  adb reboot
  sleep 60
  
  PACKAGE=com.mtk.vtcall
  TEST_PACKAGE=com.mtk.vtcall
  
  # remove junit-report and coverage.ec
  adb shell rm /data/data/$PACKAGE/files/coverage.ec
  adb shell rm /data/data/$PACKAGE/files/junit-report.xml
    
  # Run instrumentation test
  adb shell am instrument -e coverage true -w $TEST_PACKAGE/$TEST_RUNNER
  
  # get the report to PC
  #rm $SRC_ROOT/___test_report/junit-report-sms.xml
  #rm $SRC_ROOT/___test_report/junit-report-contact.xml
  #rm $SRC_ROOT/___test_report/junit-report-stk.xml
  #rm $SRC_ROOT/___test_report/junit-report-vt.xml
  adb pull /data/data/$PACKAGE/files/junit-report.xml $SRC_ROOT/___test_report/junit-report-vt.xml
  adb pull /data/data/$PACKAGE/files/1-VTCallScreen.png $SRC_ROOT/___test_report/1-VTCallScreen.png
  adb pull /data/data/$PACKAGE/files/2-VTCallScreen.png $SRC_ROOT/___test_report/2-VTCallScreen.png
  adb pull /data/data/$PACKAGE/files/3-VTCallScreen.png $SRC_ROOT/___test_report/3-VTCallScreen.png
  adb pull /sdcard/mtklog $SRC_ROOT/___test_report/mtklog_vt

  log_path=`cat $SRC_ROOT/.log_path`
  echo $log_path
  if [ "$log_path" = "persist.mtklog.log2sd.path = /mnt/sdcard2" ]; then              
    adb pull /sdcard2/mtklog $SRC_ROOT/___test_report/mtklog_vt  
  else
    adb pull /sdcard/mtklog $SRC_ROOT/___test_report/mtklog_vt    
  fi
  
  # Pull performance test data
  #adb pull /data/data/$PACKAGE/app_perf $SRC_ROOT/perf-$PACKAGE

  # Generate emma code coverage report
  #cd $INTERMEDIATES
  #adb pull /data/data/$PACKAGE/files/coverage.ec
  #java -cp ~/local/emma/lib/emma.jar emma report -r xml -in coverage.ec -in coverage.em
fi
