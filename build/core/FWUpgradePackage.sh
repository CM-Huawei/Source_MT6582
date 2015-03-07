#!/bin/bash

ROOTPATH="target_files-package"
mkdir -p $ROOTPATH
#bootable
mkdir -p  $ROOTPATH/bootable/recovery
cp -u bootable/recovery/Android.mk  $ROOTPATH/bootable/recovery/
#build
mkdir -p  $ROOTPATH/build/target/product/
cp -a build/target/product/security/  $ROOTPATH/build/target/product/
mkdir -p $ROOTPATH/build/tools/
cp -ur build/tools/releasetools/  $ROOTPATH/build/tools/
#out
mkdir -p $ROOTPATH/out/host/linux-x86/bin/
cp -u out/host/linux-x86/bin/minigzip  out/host/linux-x86/bin/mkbootfs out/host/linux-x86/bin/mkbootimg out/host/linux-x86/bin/fs_config  out/host/linux-x86/bin/mkyaffs2image  out/host/linux-x86/bin/zipalign  out/host/linux-x86/bin/bsdiff out/host/linux-x86/bin/imgdiff out/host/linux-x86/bin/mkuserimg.sh  out/host/linux-x86/bin/make_ext4fs  out/host/linux-x86/bin/aapt  out/host/linux-x86/bin/simg2img out/host/linux-x86/bin/e2fsck $ROOTPATH/out/host/linux-x86/bin/
mkdir -p $ROOTPATH/out/host/linux-x86/framework
cp -u out/host/linux-x86/framework/signapk.jar  out/host/linux-x86/framework/dumpkey.jar $ROOTPATH/out/host/linux-x86/framework/
#mediatek
mkdir -p $ROOTPATH/mediatek/misc
cp -u mediatek/misc/ota_scatter.txt  $ROOTPATH/mediatek/misc/
#org.zip
cp -u $1/obj/PACKAGING/target_files_intermediates/*-target_files-*.zip  $ROOTPATH/ota_target_files.zip
#build.prop
cp -u $1/system/build.prop $ROOTPATH/build.prop

cp -u $1/lk.bin $ROOTPATH/lk.bin
cp -u $1/logo.bin $ROOTPATH/logo.bin

#configure.xml
echo "">$ROOTPATH/configure.xml
echo "<root>">>$ROOTPATH/configure.xml

#buildnumber
var=$(grep  "ro.fota.version=" "$1/system/build.prop" )
buildnumber=${var##"ro.fota.version="}
echo "<buildnumber>$buildnumber</buildnumber>">>$ROOTPATH/configure.xml

#language
var=$(grep  "ro.product.locale.language=" "$1/system/build.prop" )
echo "<language>${var##"ro.product.locale.language="}</language>">>$ROOTPATH/configure.xml

#oem
var=$(grep  "ro.fota.oem=" "$1/system/build.prop" )
echo "<oem>${var##"ro.fota.oem="}</oem>">>$ROOTPATH/configure.xml

#operator
var=$(grep  "ro.operator.optr=" "$1/system/build.prop")
if [ "$var" = "" ] ; then
  var=other
else
var=$(echo $var|tr A-Z a-z)
if [ ${var##"ro.operator.optr="} = op01 ] ; then
var=CMCC
elif [ ${var##"ro.operator.optr="} = op02 ] ; then
var=CU
else
var=other
fi
fi
echo "<operator>${var##"ro.operator.optr="}</operator>">>$ROOTPATH/configure.xml

#model
var=$(grep  "ro.fota.device=" "$1/system/build.prop" )
product=${var##"ro.fota.device="}
echo "<product>$product</product>">>$ROOTPATH/configure.xml

#publishtime
echo "<publishtime>$(date +20%y%m%d%H%M%S)</publishtime>">>$ROOTPATH/configure.xml

#versionname
echo "<versionname>$buildnumber</versionname>">>$ROOTPATH/configure.xml
#key
echo "<key>$2</key>">>$ROOTPATH/configure.xml
echo "</root>">>$ROOTPATH/configure.xml

if [ -f $1/target_files-package.zip ]; then
echo "delete exist file:$1/target_files-package"
rm -f $1/target_files-package.zip
fi

cd target_files-package
zip -rq target_files-package.zip bootable build mediatek out configure.xml build.prop lk.bin logo.bin ota_target_files.zip
cd ..
mv target_files-package/target_files-package.zip $1/target_files-package.zip
rm -rf target_files-package

