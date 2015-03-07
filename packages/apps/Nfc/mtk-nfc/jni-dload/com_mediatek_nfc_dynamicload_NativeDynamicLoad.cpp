#include <stdlib.h>
#include <stdio.h>


#include "mtk_nfc_dynamic_load.h"
#include "com_mediatek_nfc_dynamicload.h"

namespace android {

//query_verison
// nfc_dynamic_load.so
// Run msr_nfc_query_version Check OK or NG
// If NG Run mtk_nfc_query_version Check OK or NG
// Return Value 
//   0x01: 3110 
//   0x02: 6605
//   0xFF: Error
static jint com_mediatek_nfc_dynamicload_NativeDynamicLoad_queryVersion(JNIEnv *env, jobject thiz)
{ 
    int version;
    int read_length;

    version = query_nfc_chip();
    ALOGD("[NFC_queryVersion],version,%d",version);
    if( version != 0x01 && version != 0x02 )
    {      
       version = msr_nfc_get_chip_type();
    if (version != 0x01) {
        version = mtk_nfc_get_chip_type();
    }
       
       if((version == 0x1) || (version == 0x02))
       {
          update_nfc_chip(version);
       }
    }
    return version;
}



//---------------------------------------------------------------------------------------//
//    JNI Define
//---------------------------------------------------------------------------------------//
static JNINativeMethod gMethods[] =
{
   {"doQueryVersion", "()I",
        (void *)com_mediatek_nfc_dynamicload_NativeDynamicLoad_queryVersion},       
};


int register_com_mediatek_nfc_dynamicload_NativeDynamicLoad(JNIEnv *e)
{
   return jniRegisterNativeMethods(e,
      "com/mediatek/nfc/dynamicload/NativeDynamicLoad",
      gMethods, NELEM(gMethods));
}


}  //namespace android



