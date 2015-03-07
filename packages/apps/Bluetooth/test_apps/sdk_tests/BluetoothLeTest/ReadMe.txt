Output Path: 
    out/target/product/[Project]/data/app/BluetoothLeTest.apk

Build Command: 
    ./mk -o=MTK_AUTO_TEST=yes -t mm packages/apps/Bluetooth/test_apps/sdk_tests/BluetoothLeTest/
    
Command To Run Funcational Test:
    adb shell am instrument -e size small -w com.android.bluetoothle.tests/android.bluetooth.BluetoothLeTestRunner