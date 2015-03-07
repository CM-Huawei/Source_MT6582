/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.camera;

import android.hardware.Camera.Parameters;
import android.os.Environment;
import android.os.RemoteException;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.MediaStore;

import com.mediatek.common.mpodecoder.IMpoDecoder;
import com.mediatek.storage.StorageManagerEx;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;

public class Storage {
    private static final String TAG = "CameraStorage";
    
    public static final String DCIM =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).toString();

    public static final String DIRECTORY = DCIM + "/Camera";
    
    public static final String FOLDER_PATH = "/" + Environment.DIRECTORY_DCIM + "/Camera";
    private static final String FOLDER_PATH_ORIGNAL = FOLDER_PATH + "/.ConShots";
    
    // Match the code in MediaProvider.computeBucketValues().
    public static final String BUCKET_ID =
            String.valueOf(DIRECTORY.toLowerCase().hashCode());

    public static final long UNAVAILABLE = -1L;
    public static final long PREPARING = -2L;
    public static final long UNKNOWN_SIZE = -3L;
    public static final long FULL_SDCARD = -4L;
    public static final long LOW_STORAGE_THRESHOLD;
    public static final long RECORD_LOW_STORAGE_THRESHOLD;

    /// M: for more file type and picture type @{
    public static final int CANNOT_STAT_ERROR = -2;
    public static final int PICTURE_TYPE_JPG = 0;
    public static final int PICTURE_TYPE_MPO = 1;
    public static final int PICTURE_TYPE_JPS = 2;
    public static final int PICTURE_TYPE_MPO_3D = 3;
    
    public static final int FILE_TYPE_PHOTO = 0;
    public static final int FILE_TYPE_VIDEO = 1;
    public static final int FILE_TYPE_PANO = 2;
    public static final int FILE_TYPE_LIV = 3;// live photo
    // used for the motion track photos
    private static String motionTrackFolder = null;
    private static String FOLDER_INTERMEDIA_NAME = "InterMedia";

    public static int getSize(String key) {
        return PICTURE_SIZE_TABLE.get(key);
    }

    /* use estimated values for picture size (in Bytes)*/
    static final DefaultHashMap<String, Integer>
            PICTURE_SIZE_TABLE = new DefaultHashMap<String, Integer>();

    static {        
        if(FeatureSwitcher.isMtkFatOnNand() || FeatureSwitcher.isLcaROM()){
            LOW_STORAGE_THRESHOLD= 10000000;
            RECORD_LOW_STORAGE_THRESHOLD = 9600000;
            Log.i(TAG,"LOW_STORAGE_THRESHOLD= 10000000");
        }
        else{
            LOW_STORAGE_THRESHOLD= 50000000;
            RECORD_LOW_STORAGE_THRESHOLD = 48000000;
            Log.i(TAG,"LOW_STORAGE_THRESHOLD= 50000000");
        }
        PICTURE_SIZE_TABLE.put("1280x720-normal", 122880);
        PICTURE_SIZE_TABLE.put("1280x720-fine", 147456);
        PICTURE_SIZE_TABLE.put("1280x720-superfine", 196608);
        
        PICTURE_SIZE_TABLE.put("2560x1440-normal", 245760);
        PICTURE_SIZE_TABLE.put("2560x1440-fine", 368640);
        PICTURE_SIZE_TABLE.put("2560x1440-superfine", 460830);
        
        PICTURE_SIZE_TABLE.put("3328x1872-normal", 542921);
        PICTURE_SIZE_TABLE.put("3328x1872-fine", 542921);
        PICTURE_SIZE_TABLE.put("3328x1872-superfine", 678651);

        PICTURE_SIZE_TABLE.put("4096x2304-normal", 822412);
        PICTURE_SIZE_TABLE.put("4096x2304-fine", 822412);
        PICTURE_SIZE_TABLE.put("4096x2304-superfine", 1028016);
        
        PICTURE_SIZE_TABLE.put("4608x2592-normal", 1040866);
        PICTURE_SIZE_TABLE.put("4608x2592-fine", 1040866);
        PICTURE_SIZE_TABLE.put("4608x2592-superfine", 1301083);
        
        PICTURE_SIZE_TABLE.put("5120x2880-normal", 1285020);
        PICTURE_SIZE_TABLE.put("5120x2880-fine", 1285020);
        PICTURE_SIZE_TABLE.put("5120x2880-superfine", 1606275);

        PICTURE_SIZE_TABLE.put("1280x768-normal", 131072);
        PICTURE_SIZE_TABLE.put("1280x768-fine", 157286);
        PICTURE_SIZE_TABLE.put("1280x768-superfine", 209715);
        
        PICTURE_SIZE_TABLE.put("2880x1728-normal", 331776);
        PICTURE_SIZE_TABLE.put("2880x1728-fine", 497664);
        PICTURE_SIZE_TABLE.put("2880x1728-superfine", 622080);
        
        PICTURE_SIZE_TABLE.put("3600x2160-normal", 677647);
        PICTURE_SIZE_TABLE.put("3600x2160-fine", 677647);
        PICTURE_SIZE_TABLE.put("3600x2160-superfine", 847059);

        PICTURE_SIZE_TABLE.put("3600x2700-normal", 847059);
        PICTURE_SIZE_TABLE.put("3600x2700-fine", 847059);
        PICTURE_SIZE_TABLE.put("3600x2700-superfine", 1058824);
        
        PICTURE_SIZE_TABLE.put("3672x2754-normal", 881280);
        PICTURE_SIZE_TABLE.put("3672x2754-fine", 881280);
        PICTURE_SIZE_TABLE.put("3672x2754-superfine", 12640860);

        PICTURE_SIZE_TABLE.put("4096x3072-normal", 1096550);
        PICTURE_SIZE_TABLE.put("4096x3072-fine", 1096550);
        PICTURE_SIZE_TABLE.put("4096x3072-superfine", 1370688);
        
        PICTURE_SIZE_TABLE.put("4160x3120-normal", 1131085);
        PICTURE_SIZE_TABLE.put("4160x3120-fine", 1131085);
        PICTURE_SIZE_TABLE.put("4160x3120-superfine", 1413857);
        
        PICTURE_SIZE_TABLE.put("4608x3456-normal", 1387821);
        PICTURE_SIZE_TABLE.put("4608x3456-fine", 1387821);
        PICTURE_SIZE_TABLE.put("4608x3456-superfine", 1734777);
        
        PICTURE_SIZE_TABLE.put("5120x3840-normal", 1713359);
        PICTURE_SIZE_TABLE.put("5120x3840-fine", 1713359);
        PICTURE_SIZE_TABLE.put("5120x3840-superfine", 2141700);
        
        PICTURE_SIZE_TABLE.put("3264x2448-normal", 696320);
        PICTURE_SIZE_TABLE.put("3264x2448-fine", 696320);
        PICTURE_SIZE_TABLE.put("3264x2448-superfine", 870400);
        
        PICTURE_SIZE_TABLE.put("2592x1944-normal", 327680);
        PICTURE_SIZE_TABLE.put("2592x1944-fine", 491520);
        PICTURE_SIZE_TABLE.put("2592x1944-superfine", 614400);
        
        PICTURE_SIZE_TABLE.put("2560x1920-normal", 327680);
        PICTURE_SIZE_TABLE.put("2560x1920-fine", 491520);
        PICTURE_SIZE_TABLE.put("2560x1920-superfine", 614400);
        
        PICTURE_SIZE_TABLE.put("2048x1536-normal", 262144);
        PICTURE_SIZE_TABLE.put("2048x1536-fine", 327680);
        PICTURE_SIZE_TABLE.put("2048x1536-superfine", 491520);
        
        PICTURE_SIZE_TABLE.put("1600x1200-normal", 204800);
        PICTURE_SIZE_TABLE.put("1600x1200-fine", 245760);
        PICTURE_SIZE_TABLE.put("1600x1200-superfine", 368640);
        
        PICTURE_SIZE_TABLE.put("1280x960-normal", 163840);
        PICTURE_SIZE_TABLE.put("1280x960-fine", 196608);
        PICTURE_SIZE_TABLE.put("1280x960-superfine", 262144);
        
        PICTURE_SIZE_TABLE.put("1024x768-normal", 102400);
        PICTURE_SIZE_TABLE.put("1024x768-fine", 122880);
        PICTURE_SIZE_TABLE.put("1024x768-superfine", 163840);
        
        PICTURE_SIZE_TABLE.put("640x480-normal", 30720);
        PICTURE_SIZE_TABLE.put("640x480-fine", 30720);
        PICTURE_SIZE_TABLE.put("640x480-superfine", 30720);
        
        PICTURE_SIZE_TABLE.put("320x240-normal", 13312);
        PICTURE_SIZE_TABLE.put("320x240-fine", 13312);
        PICTURE_SIZE_TABLE.put("320x240-superfine", 13312);
        //start add
        
        PICTURE_SIZE_TABLE.put("1600x912-normal", 163840);
        PICTURE_SIZE_TABLE.put("1600x912-fine", 196608);
        PICTURE_SIZE_TABLE.put("1600x912-superfine", 262144);
        
        PICTURE_SIZE_TABLE.put("2048x1152-normal", 196608);
        PICTURE_SIZE_TABLE.put("2048x1152-fine", 245760);
        PICTURE_SIZE_TABLE.put("2048x1152-superfine", 368640);
        
        PICTURE_SIZE_TABLE.put("1600x960-normal", 163840);
        PICTURE_SIZE_TABLE.put("1600x960-fine", 196608);
        PICTURE_SIZE_TABLE.put("1600x960-superfine", 294912);
        
        PICTURE_SIZE_TABLE.put("1024x688-normal", 102400);
        PICTURE_SIZE_TABLE.put("1024x688-fine", 122880);
        PICTURE_SIZE_TABLE.put("1024x688-superfine", 163840);
        
        PICTURE_SIZE_TABLE.put("1280x864-normal", 131072);
        PICTURE_SIZE_TABLE.put("1280x864-fine", 157286);
        PICTURE_SIZE_TABLE.put("1280x864-superfine", 209715);
        
        PICTURE_SIZE_TABLE.put("1440x960-normal", 184320);
        PICTURE_SIZE_TABLE.put("1440x960-fine", 221184);
        PICTURE_SIZE_TABLE.put("1440x960-superfine", 294912);
        
        PICTURE_SIZE_TABLE.put("1728x1152-normal", 221184);
        PICTURE_SIZE_TABLE.put("1728x1152-fine", 265420);
        PICTURE_SIZE_TABLE.put("1728x1152-superfine", 353894);
        
        PICTURE_SIZE_TABLE.put("2048x1360-normal", 232107);
        PICTURE_SIZE_TABLE.put("2048x1360-fine", 290133);
        PICTURE_SIZE_TABLE.put("2048x1360-superfine", 435199);
        
        PICTURE_SIZE_TABLE.put("2560x1712-normal", 292181);
        PICTURE_SIZE_TABLE.put("2560x1712-fine", 438271);
        PICTURE_SIZE_TABLE.put("2560x1712-superfine", 547840);
        //end add
        PICTURE_SIZE_TABLE.put("mav", 1036288);
        PICTURE_SIZE_TABLE.put("autorama", 163840);
        
        PICTURE_SIZE_TABLE.putDefault(1500000);
    }

    private static StorageManager sStorageManager;
    private static StorageManager getStorageManager() {
        if (sStorageManager == null) {
            try {
                sStorageManager = new StorageManager(null, null);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
        return sStorageManager;
    }
    
    public static boolean isSDCard() {
        StorageManager storageManager = getStorageManager();
        String storagePath = sMountPoint;//storageManager.getDefaultPath();
        StorageVolume[] volumes = storageManager.getVolumeList();
        int nVolume = -1;
        for (int i = 0; i < volumes.length; i++) {
            if (volumes[i].getPath().equals(storagePath)) {
                nVolume = i;
                break;
            }
        }
        boolean isSd = false;
        if (nVolume != -1) {
            isSd = volumes[nVolume].isRemovable();
        }
        Log.d(TAG, "isSDCard() storagePath=" + storagePath + " return " + isSd);
        return isSd;
    }

    public static boolean isMultiStorage() {
        StorageManager storageManager = getStorageManager();
        StorageVolume[] volumes = storageManager.getVolumeList();
        return volumes.length > 1;
    }

    public static boolean isHaveExternalSDCard() {
        StorageManager storageManager = getStorageManager();
        StorageVolume[] volumes = storageManager.getVolumeList();
        for (int i = 0; i < volumes.length; i++) {
            if (volumes[i].isRemovable() && Environment.MEDIA_MOUNTED.equals(
                    storageManager.getVolumeState(volumes[i].getPath()))) {
                return true;
            }
        }
        return false;
    }
    // get internal volume path
    public static String getInternalVolumePath() {
        StorageManager storageManager = getStorageManager();
        StorageVolume[] volumes = storageManager.getVolumeList();
        for (int i = 0; i < volumes.length; i++) {
            if (!volumes[i].isRemovable() && Environment.MEDIA_MOUNTED.equals( 
                    storageManager.getVolumeState(volumes[i].getPath()))) {
                return volumes[i].getPath();
            }
        }
        return null;
    }

    public static long getAvailableSpace() {
        String state;
        StorageManager storageManager = getStorageManager();
        state = storageManager.getVolumeState(sMountPoint);
        Log.d(TAG, "External storage state=" + state + ", mount point = " + sMountPoint);
        if (Environment.MEDIA_CHECKING.equals(state)) {
            return PREPARING;
        }
        if (!Environment.MEDIA_MOUNTED.equals(state)) {
            return UNAVAILABLE;
        }
        
        File dir = new File(getFileDirectory());
        dir.mkdirs();
        boolean isDirectory = dir.isDirectory();
        boolean canWrite = dir.canWrite();
        if (!isDirectory || !canWrite) {
            Log.d(TAG, "getAvailableSpace() isDirectory=" + isDirectory + ", canWrite=" + canWrite);
            return FULL_SDCARD;
        }

        try {
            //Here just use one directory to stat fs.
            StatFs stat = new StatFs(getFileDirectory());
            return stat.getAvailableBlocks() * (long) stat.getBlockSize();
        } catch (Exception e) {
            Log.i(TAG, "Fail to access external storage", e);
        }
        return UNKNOWN_SIZE;
    }

    /**
     * OSX requires plugged-in USB storage to have path /DCIM/NNNAAAAA to be
     * imported. This is a temporary fix for bug#1655552.
     */
    public static void ensureOSXCompatible() {
        File nnnAAAAA = new File(DCIM, "100ANDRO");//should check dcim
        if (!(nnnAAAAA.exists() || nnnAAAAA.mkdirs())) {
            Log.e(TAG, "Failed to create " + nnnAAAAA.getPath());
        }
    }
    
    private static String sMountPoint;
    public static String getMountPoint() {
        return sMountPoint;
    }
    
    private static boolean sStorageReady;
    public static boolean isStorageReady() {
        Log.i(TAG, "isStorageReady() mount point = " + sMountPoint + ", return " + sStorageReady);
        return sStorageReady;
    }
    
    public static void setStorageReady(boolean ready) {
        Log.d(TAG, "setStorageReady(" + ready + ") sStorageReady=" + sStorageReady);
        sStorageReady = ready;
    }
    
    public static void mkFileDir(String path) {
        File dir = new File(path);
        if (!dir.exists()) {
            Log.d(TAG, "dir not exit,will create this");
            dir.mkdirs();
        }
    }
    
    public static boolean initializeStorageState() {
        StorageManager storageManager = getStorageManager();
        String defaultPath = StorageManagerEx.getDefaultPath();
        boolean diff = false;
        String old = sMountPoint;
        sMountPoint = defaultPath;
        if (old != null && old.equalsIgnoreCase(sMountPoint)) {
            diff = true;
        }
        String state = storageManager.getVolumeState(sMountPoint);
        setStorageReady(Environment.MEDIA_MOUNTED.equals(state));
        Log.d(TAG, "initializeStorageState() old=" + old + ", sMountPoint=" + sMountPoint
                + " return " + diff);
        return diff;
    }
    
    public static boolean updateDefaultDirectory() {
        mkFileDir(getFileDirectory());
        return initializeStorageState();
    }
    
    public static boolean updateDirectory(String path) {
        StorageManager storageManager = getStorageManager();
        boolean diff = false;
        String old = sMountPoint;
        sMountPoint = path;
        if (old != null && old.equalsIgnoreCase(sMountPoint)) {
            diff = true;
        }
        mkFileDir(getFileDirectory());
        String state = storageManager.getVolumeState(sMountPoint);
        setStorageReady(Environment.MEDIA_MOUNTED.equals(state));
        Log.d(TAG, "updateDefaultDirectory() old=" + old + ", sMountPoint=" + sMountPoint
                + " return " + diff);
        return diff;
    }
    public static String getFileDirectory() {
        String path = sMountPoint + FOLDER_PATH;
        Log.d(TAG, "getFilePath return " + path);
        return path;
    }
    
    public static String getOrignalFileDirectory(int tag) {
        
        String path = sMountPoint + FOLDER_PATH_ORIGNAL;
        if (tag == FileSaver.INTERMEDIA_IMAGE) {
            path = path + "/" + FOLDER_INTERMEDIA_NAME;
        } else if (motionTrackFolder != null) {
            path = path + "/" + motionTrackFolder;
        }
        mkFileDir(path);
        Log.d(TAG, "getFilePath return " + path);
        return path;
    }
    
    public static String getCameraScreenNailPath() {

        String path = sMountPoint + FOLDER_PATH;

        final String prefix = "/local/all/";
        String cameraPath = null;
        cameraPath = prefix + getBucketId(getFileDirectory()); 
        Log.d(TAG, "getCameraScreenNailPath() " + ", return " + cameraPath);
        return cameraPath;
    }
    
    public static String getBucketId(String directory) {
        return String.valueOf(directory.toLowerCase(Locale.ENGLISH).hashCode());
    }
    
    public static String getBucketId() {
        return getBucketId(getFileDirectory());
    }
    
    public static int generateMpoType(int pictureType) {
        if (pictureType == PICTURE_TYPE_MPO) {
            return IMpoDecoder.MTK_TYPE_MAV;
        } else if (pictureType == PICTURE_TYPE_MPO_3D) {
            return IMpoDecoder.MTK_TYPE_Stereo;
        } else {
            return IMpoDecoder.MTK_TYPE_NONE;
        }
    }

    public static String generateFileName(String title, int pictureType) {
        if (pictureType == PICTURE_TYPE_MPO || pictureType == PICTURE_TYPE_MPO_3D) {
            return title + ".mpo";
        } else if (pictureType == PICTURE_TYPE_JPS) {
            return title + ".jps";
        } else if (pictureType == PICTURE_TYPE_JPG){
            return title + ".jpg";
        } else {
            // this case we just want return the 
            // source data,such as intermedia data
            return title;
        }
    }

    public static void prepareMotionTrackFolder (String title) {
        motionTrackFolder = title;
    }
    
    public static String generateMimetype(String title, int pictureType) {
        if (pictureType == PICTURE_TYPE_MPO || pictureType == PICTURE_TYPE_MPO_3D) {
            return "image/mpo";
        } else if (pictureType == PICTURE_TYPE_JPS) {
            return "image/x-jps";
        } else {
            return "image/jpeg";
        }
    }
    public static int generateStereoType(String stereoType) {
        if (Parameters.STEREO3D_TYPE_SIDEBYSIDE.equals(stereoType)) {
            return MediaStore.ThreeDimensionColumns.STEREO_TYPE_SIDE_BY_SIDE;
        } else if (Parameters.STEREO3D_TYPE_TOPBOTTOM.equals(stereoType)) {
            return MediaStore.ThreeDimensionColumns.STEREO_TYPE_TOP_BOTTOM;
        } else if (Parameters.STEREO3D_TYPE_FRAMESEQ.equals(stereoType)) {
            return MediaStore.ThreeDimensionColumns.STEREO_TYPE_FRAME_SEQUENCE;
        } else {
            return MediaStore.ThreeDimensionColumns.STEREO_TYPE_2D;
        }
    }

    public static String generateFilepath(String fileName) {
        return getFileDirectory() + '/' + fileName;
    }
    
    public static String generateFilepath(String fileName,int tag) {
        switch (tag) {
        case 0:
            return generateFilepath(fileName);
            
        default:
            return getOrignalFileDirectory(tag)+ '/' + fileName;
        }
    }

    private static final AtomicLong LEFT_SPACE = new AtomicLong(0);
    public static long getLeftSpace() {
        long left = LEFT_SPACE.get();
        Log.d(TAG, "getLeftSpace() return " + left);
        return LEFT_SPACE.get();
    }
    
    public static void setLeftSpace(long left) {
        LEFT_SPACE.set(left);
        Log.d(TAG, "setLeftSpace(" + left + ")");
    }
}
