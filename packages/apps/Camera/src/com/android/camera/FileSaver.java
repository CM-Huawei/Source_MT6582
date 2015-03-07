package com.android.camera;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.Size;
import android.location.Location;
import android.media.ExifInterface;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.provider.MediaStore.Images;
import android.provider.MediaStore.Images.ImageColumns;
import android.provider.MediaStore.Video;

import com.android.camera.Util.ImageFileNamer;
import com.android.camera.FileSaverService;
import com.mediatek.common.featureoption.FeatureOption;

import com.android.camera.R;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class FileSaver{ //push thumbnail to thumbnail manager
    private static final String TAG = "FileSaver";
    
    private static final int QUEUE_LIMIT = 100;
    
    //image type
    public static final int UNKONWEN_TOTATL = -1;
    public static final int IGNORE_IMAGE_TYPE = 0;
    public static final int ORIGINAL_IMAGE = 1;
    public static final int INTERMEDIA_IMAGE = 2;
    public static final int BLENDED_IMAGE = 3;
    private static final String MOTION_TRACK_SUFFIX = "MT";
    private static final String TRACK_PHOTO_SUFFIX = "TK";
    private static final String INTERMEDIA_PHOTO_SUFFIX = "IT";

    private static final String TEMP_SUFFIX = ".tmp";

    private Camera mContext;
    private ContentResolver mResolver;
    private List<FileSaverListener> mSaverListener = new CopyOnWriteArrayList<FileSaverListener>();
    private ArrayList<Integer > mIndexArrary = new ArrayList<Integer>();
    private HashMap<Integer, ImageFileNamer> mFileNamer;
    private FileSaverService mSaverService;
    
    public interface FileSaverListener {
        void onFileSaved(SaveRequest request);
    }
    
    public FileSaver(Camera context) {
        mContext = context;
        mResolver = mContext.getContentResolver();
    }
    
    private FileSaverService.FileSaverListener mListener = new FileSaverService.FileSaverListener() {
    	@Override
    	public void onQueueStatus(boolean full) {
    	    if (mContext.getShutterManager() != null) {
    	        mContext.getShutterManager().setPhotoShutterEnabled(!full);
    	    }
    	}
    	@Override
    	public void onFileSaved(SaveRequest r) {
    	    // here should not call r.notifyListener, otherwise will call MavActor onFileSaver twice
    	    // lead call start animation twice, capture animation in Mav will tearing
    	    for (FileSaverListener listener : mSaverListener) {
                listener.onFileSaved(r);
            }
    	};
    	
    	@Override
    	public void onSaveDone() {
    	    synchronized(FileSaver.this) {
    	        FileSaver.this.notifyAll();
    	    }
    	}
    };
    
    private ServiceConnection mConnection = new ServiceConnection() {
    	@Override
    	public void onServiceConnected(ComponentName name, IBinder b) {
    	    mSaverService = ((FileSaverService.LocalBinder)b).getService();
    	    mSaverService.registerFileSaverListener(mListener);
    	}
    	
    	@Override
        public void onServiceDisconnected(ComponentName name) {
            //when process crashed,unregister file saver listener
            if (mSaverService != null) {
                mSaverService.unregisterFileSaverListener(mListener);
                mSaverService = null;
            }
    	}
    };
    
    public void bindSaverService() {
        Intent intent = new Intent((Context)mContext, FileSaverService.class);
        mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }
    
    public void unBindSaverService() {
        if (mSaverService != null) {
            mSaverService.unregisterFileSaverListener(mListener);
            mSaverService = null;
        }
        if (mConnection != null) {
            mContext.unbindService(mConnection);
        }
    }
    
    public void onContinousShotDone() {
        mSaverService.onContinousShotDone();
    }
    
    public void waitDone() {
    	Log.i(TAG, "waitDone()");
        synchronized (FileSaver.this) {
            while (!mSaverService.isNoneSaveTask()) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    // ignore.
                    Log.e(TAG, "waitDone()", ex);
                }
            }
        }
    }

    public long getWaitingDataSize() {
        return mSaverService.getWaitingDataSize();
    }
    
    public int getWaitingCount() {
       return mSaverService.getWaitingCount();
    }
    
    public boolean addListener(FileSaverListener l) {
        if (!mSaverListener.contains(l)) {
            return mSaverListener.add(l);
        }
        return false;
    }
    
    public boolean removeListener(FileSaverListener l) {
        return mSaverListener.remove(l);
    }
    
    private void addSaveRequest(SaveRequest r) {
        synchronized(this) {
            if (mSaverService == null) {
                return;
            }
            while(mSaverService.getWaitingCount() >= QUEUE_LIMIT) {
                try {
                    wait();
                } catch (InterruptedException ex) {
                    // ignore.
                    Log.e(TAG, "addSaveRequest()", ex);
                }
            }
            mSaverService.addSaveRequest(r);
        }
    }
    //record type, location and datetaken
    public SaveRequest preparePhotoRequest(int type, int pictureType) {
        SaveRequest request = null;
        if (type == Storage.FILE_TYPE_PANO) {
            request = new PanoOperator(pictureType);
        } else {
            request = new PhotoOperator(pictureType);
        }
        request.prepareRequest();
        return request;
    }
    // copy JpegRotation,location ,record type and datetaken
    public SaveRequest copyPhotoRequest(SaveRequest originRequest) {
        SaveRequest request = null;
        if (originRequest instanceof PhotoOperator) {
            request = ((PhotoOperator) originRequest).copyRequest();
        }
        return request;
    }

    //resolution
    public SaveRequest prepareVideoRequest(int fileType, int outputFileFormat, String resolution) {
        //we should prepare file path for recording, so here we fill lots of info.
        VideoOperator operator = new VideoOperator(fileType, outputFileFormat, resolution);
        operator.prepareRequest();
        return operator;
    }
    private abstract class RequestOperator implements SaveRequest {
        int mFileType;
        byte[] mData;//for raw data
        int mTag = 0; // for MT
        int mToatl = -1;
        int mIndex = 0;
        Uri mUri;
        String mTitle;
        String mBlendedTitle;
        Location mLocation;
        int mWidth;
        int mHeight;
        int mOrientation;
        String mDisplayName;
        String mFilePath;//for saved file
        String mMimeType;
        long mDateTaken;
        int mMpoType;//for mpo
        int mStereoType;//for stereo
        int mIsMTK3D;// for N3D
        String mResolution;//for video resolution
        long mDataSize;
        long mDuration;
        int mLivePhoto;
        int mSlowMotionSpeed;
        int mTempPictureType;
        int mTempOutputFileFormat;//for video file format
        String mTempFilePath;//for saved file
        int mTempJpegRotation;
        boolean mIgnoreThumbnail;
        FileSaverListener mListener;
        // M:ConShots
        int mGroupIndex = 0;
        long mGroupId;
        long mFocusValueHigh;
        long mFocusValueLow;
        boolean mIsContinuousRequest = false;

        final static int IS_MTK_3D = 1;
        final static int IS_NOT_3D = 0;
        
        @Override
        public void setContinuousRequest(boolean isContinuousRequest) {
        	mIsContinuousRequest = isContinuousRequest;
        }
        @Override
        public boolean isContinuousRequest() {
        	return mIsContinuousRequest;
        }
        @Override
        public void setIgnoreThumbnail(boolean ignore) {
            mIgnoreThumbnail = ignore;
        }
        @Override
        public boolean isIgnoreThumbnail() {
            return mIgnoreThumbnail;
        }
        @Override
        public String getTempFilePath() {
            return mTempFilePath;
        }
        @Override
        public String getFilePath() {
            return mFilePath;
        }
        @Override
        public int getDataSize() {
            if (mData == null) {
                return 0;
            } else {
                return mData.length;
            }
        }
        @Override
        public void setData(byte[] data) {
            mData = data;
        }
        
        @Override
        public void setTag(int tag) {
            mTag = tag;
        }
        
        @Override
        public void setIndex(int index,int total) {
            if (mTag != IGNORE_IMAGE_TYPE) {
                mIndexArrary.add(index);
            }
            mToatl = total;
        }

        @Override
        public void setDuration(long duration) {
            mDuration = duration;
        }
        
        @Override
        public void setlivePhoto(int livephoto) {
        	mLivePhoto = livephoto;
        }
        
        @Override
        public void setSlowMotionSpeed(int speed) {
            mSlowMotionSpeed = speed;
        }
        @Override
        public Uri getUri() {
            return mUri;
        }
        @Override
        public void setJpegRotation(int jpegRotation) {
            Log.d(TAG, "setJpegRotation(" + jpegRotation + ")");
            mTempJpegRotation = jpegRotation;
        }
        @Override
        public int getJpegRotation() {
            Log.d(TAG, "getJpegRotation mTempJpegRotation=" + mTempJpegRotation);
            return mTempJpegRotation;
        }
        @Override
        public Location getLocation() {
            return mLocation;
        }
        @Override
        public void setLocation(Location loc) {
            mLocation = loc;
        }
        @Override
        public void setTempPath(String path) {
            mTempFilePath = path;
        }
        @Override
        public void setListener(FileSaverListener listener) {
            mListener = listener;
        }
        @Override
        public void notifyListener() {
            if (mListener != null) {
                mListener.onFileSaved(this);
            }
        }
        @Override
        public void updateDataTaken(long time) {
            mDateTaken = time;
        } 
        
        public int getMTK3DType(int stereoType) {
            if (MediaStore.ThreeDimensionColumns.STEREO_TYPE_2D != mStereoType) {
                return IS_MTK_3D;
            } 
            return IS_NOT_3D;
        }
        
        public void saveImageToDatabase(RequestOperator r) {
            Log.i(TAG, "------------->   saveImageToDatabase");
            // Insert into MediaStore.
            ContentValues values = new ContentValues(14);
            values.put(ImageColumns.TITLE, mTag == BLENDED_IMAGE ? r.mBlendedTitle:r.mTitle);
            values.put(ImageColumns.DISPLAY_NAME, r.mDisplayName);
            values.put(ImageColumns.DATE_TAKEN, r.mDateTaken);
            values.put(ImageColumns.MIME_TYPE, r.mMimeType);
            values.put(ImageColumns.DATA, r.mFilePath);
            values.put(ImageColumns.SIZE, r.mDataSize);  
            values.put(ImageColumns.STEREO_TYPE, r.mStereoType);//should be rechecked
            if (r.mLocation != null) {
                values.put(ImageColumns.LATITUDE, r.mLocation.getLatitude());
                values.put(ImageColumns.LONGITUDE, r.mLocation.getLongitude());
            }
            values.put(ImageColumns.ORIENTATION, r.mOrientation);
            // M: ConShots
            values.put(Images.Media.GROUP_ID, r.mGroupId);
            values.put(Images.Media.GROUP_INDEX, r.mGroupIndex);
            values.put(Images.Media.FOCUS_VALUE_HIGH,r.mFocusValueHigh);
            values.put(Images.Media.FOCUS_VALUE_LOW,r.mFocusValueLow);
            values.put(ImageColumns.WIDTH, r.mWidth);
            values.put(ImageColumns.HEIGHT,r.mHeight); 
            values.put(ImageColumns.MPO_TYPE, r.mMpoType);
            try {
                r.mUri = mResolver.insert(Images.Media.EXTERNAL_CONTENT_URI, values);
                mContext.addSecureAlbumItemIfNeeded(false, r.mUri);
                if (mContext.isNonePickIntent()) {
                	// picture taken and saved by camera which is launched by 3rd apps will 
                	// be inserted into DB. But do not broadcast "New_Picture" intent, 
                	// otherwise, it will not pass camera CTS test.
                	Util.broadcastNewPicture(mContext, r.mUri);
                }
            } catch (Throwable th)  { //Here we keep google default, don't follow check style
                // This can happen when the external volume is already mounted, but
                // MediaScanner has not notify MediaProvider to add that volume.
                // The picture is still safe and MediaScanner will find it and
                // insert it into MediaProvider. The only problem is that the user
                // cannot click the thumbnail to review the picture.
                Log.e(TAG, "Failed to write MediaStore", th);
            }
            
            Log.i(TAG, "<-------------   saveImageToDatabase, i.mUri = " + r.mUri);
        }
        @Override
        public void saveSync() {
            if (mData == null) {
                Log.w(TAG, "saveSync() why mData==null???", new Throwable());
                return;
            }
            FileOutputStream out = null;
            try {
                // Write to a temporary file and rename it to the final name. This
                // avoids other apps reading incomplete data.
                out = new FileOutputStream(mTempFilePath);
                out.write(mData);
                out.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to write image", e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        Log.e(TAG, "saveRequest()", e);
                    }
                }
            }
        }
        
        @Override
        public String toString() {
            return new StringBuilder()
            .append("RequestOperator(mUri=")
            .append(mUri)
            .append(", mTempFilePath=")
            .append(mTempFilePath)
            .append(", mFilePath=")
            .append(mFilePath)
            .append(", mIgnoreThumbnail=")
            .append(mIgnoreThumbnail)
            .append(")")
            .toString();
        }
    }
    private class PhotoOperator extends RequestOperator {
        private PhotoOperator(int pictureType) {
            mTempPictureType = pictureType;
        }
        @Override
        public void prepareRequest() {
            mFileType = Storage.FILE_TYPE_PHOTO;
            mDateTaken = System.currentTimeMillis();
            Location loc = mContext.getLocationManager().getCurrentLocation();
            if (loc != null) {
                mLocation = new Location(loc);
            }
        }
        @Override
        public void addRequest() {
            if (mData == null) {
                Log.w(TAG, "addRequest() why mData==null???", new Throwable());
                return;
            }
            //In google default camera, it set picture size when capture and onPictureTaken
            //Here we just set picture size in onPictureTaken.
            Size s = mContext.getParameters().getPictureSize();
            mWidth = s.width;
            mHeight = s.height;
            addSaveRequest(this);
        }

        public PhotoOperator copyRequest() {
            PhotoOperator newRequest = new PhotoOperator(mTempPictureType);
            newRequest.mFileType = Storage.FILE_TYPE_PHOTO;
            Log.d(TAG, "copyRequest,mTag = " + mTag);
            if (mTag == 0) {
                newRequest.mDateTaken = System.currentTimeMillis();
            } else {
                newRequest.mDateTaken = mDateTaken;
            }
            newRequest.mLocation = this.mLocation;
            newRequest.mTempJpegRotation = this.mTempJpegRotation;
            return newRequest;
        }
        
        public void refactoerTitle() {
            if (mTag != IGNORE_IMAGE_TYPE) {
                mTitle += MOTION_TRACK_SUFFIX;
                
                if (mTag == BLENDED_IMAGE) {
                    mBlendedTitle = mTitle;
                    mTitle += TRACK_PHOTO_SUFFIX;
                }
                if (mTag == INTERMEDIA_IMAGE) {
                    mTitle += INTERMEDIA_PHOTO_SUFFIX;
                }
                Storage.prepareMotionTrackFolder(mTitle);
                
                if (mTag != INTERMEDIA_IMAGE) {
                    // if the data is InterMedia file,the file name not 
                    // have the index
                    mTitle += String.format("%02d", mIndex);
                }
                setIgnoreThumbnail(true);

            }
        }
        
        @Override
        public void saveRequest() {
            // get the mindex
            if (mTag != IGNORE_IMAGE_TYPE) {
                mIndex = mIndexArrary.get(0);
                mIndexArrary.remove(0);
            }
            int orientation = Exif.getOrientation(mData);
            // M: ConShots
            mGroupId = Exif.getGroupId(mData);
            mGroupIndex = Exif.getGroupIndex(mData);
            mFocusValueHigh = Exif.getFocusValueHigh(mData);
            mFocusValueLow = Exif.getFocusValueLow(mData);
            int width;
            int height;
            if ((mTempJpegRotation + orientation) % 180 == 0) {
                width = mWidth;
                height = mHeight;
            } else {
                width = mHeight;
                height = mWidth;
            }
            mWidth = width;
            mHeight = height;
            mOrientation = orientation;
            mDataSize = mData.length;
            if (com.mediatek.camera.FrameworksClassFactory.isMockCamera() || mTag != IGNORE_IMAGE_TYPE) {
                mGroupIndex = 0;
            }
            mTitle = createName(mFileType, mDateTaken, mGroupIndex,mTag);
            refactoerTitle();
            // mDisplayName is the fileName
            if (mTag == INTERMEDIA_IMAGE) {
                mDisplayName = Storage.generateFileName(mTitle, Storage.CANNOT_STAT_ERROR);
            } else {
                mDisplayName = Storage.generateFileName(mTitle, mTempPictureType);
            }
            mFilePath = Storage.generateFilepath(mDisplayName ,mTag);
            mTempFilePath = mFilePath + TEMP_SUFFIX;
            saveImageToSDCard(mTempFilePath, mFilePath, mData);
            Log.d(TAG, "mFilePath = " +mFilePath +",mTotal = " + mToatl +" == " +"mindex = " + mIndex +",mTag == BLENDED_IMAGE : " +(mTag == BLENDED_IMAGE));
            if (mToatl == mIndex && mTag == BLENDED_IMAGE) {
                saveLastBlendedImageToSDCard();
            }
            mMimeType = Storage.generateMimetype(mTitle, mTempPictureType);
            //hardcode write stereotype for feature table is not ready
            //mStereoType = Storage.generateStereoType(mContext.getStereo3DType());
            mStereoType = Storage.generateStereoType(null);
            mIsMTK3D = getMTK3DType(mStereoType);
            mMpoType = Storage.generateMpoType(mTempPictureType);
            if (needAddToDataBase()) {
                saveImageToDatabase(this);
            }
            Log.i(TAG, "saveRequest() mTempJpegRotation=" + mTempJpegRotation
                    + ", mOrientation=" + mOrientation);
        }
        
        private boolean needAddToDataBase() {
            boolean needAddToDb = mTag == IGNORE_IMAGE_TYPE || (mTag == BLENDED_IMAGE && mIndex == mToatl);
            Log.i(TAG, "checkSaveRequestState, needAddToDb = " + needAddToDb);
            return needAddToDb;
        }
        
        private void saveImageToSDCard(String tempFilePath,String filePath,byte[] data) {
            FileOutputStream out = null;
            try {
                // Write to a temporary file and rename it to the final name.
                // This
                // avoids other apps reading incomplete data.
                Log.i(TAG, "begin add the data to SD Card,data = " + data);
                out = new FileOutputStream(tempFilePath);
                out.write(data);
                out.close();
                new File(tempFilePath).renameTo(new File(filePath));
            } catch (IOException e) {
                Log.e(TAG, "Failed to write image", e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        Log.e(TAG, "saveRequest()", e);
                    }
                }
            }
            Log.i(TAG, "end of add the data to SD Card");
        }
        
        private void saveLastBlendedImageToSDCard () {
            // means will save the last blended image to the Camera folder
            mDisplayName = Storage.generateFileName(mBlendedTitle, mTempPictureType);
            mFilePath = Storage.generateFilepath(mDisplayName, IGNORE_IMAGE_TYPE);
            Log.d(TAG,"mFilePath = "+mFilePath +",mDisplayName = " + mDisplayName);
            FileOutputStream out = null;
            try {
                out = new FileOutputStream(mFilePath);
                out.write(mData);
                out.close();
            } catch (IOException e) {
                Log.e(TAG, "Failed to write image[blended]", e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        Log.e(TAG, "saveRequest()[blended]", e);
                    }
                }
            }
            setIgnoreThumbnail(false);
        }
        
        @Override
        public Thumbnail createThumbnail(int thumbnailWidth) {
            Thumbnail thumb = null;
            if (mUri != null) {
                // Create a thumbnail whose width is equal or bigger than
                // that of the thumbnail view.
                int inSampleSize = 1;
                if (mTag == BLENDED_IMAGE) {
                    // Create a thumbnail whose width is equal or bigger than
                    // that of the thumbnail view.
                    try {
                        ExifInterface exif = new ExifInterface(mFilePath);
                        int width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0);
                        int height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0);
                        int widthRatio = (int) Math.ceil((double) width / mContext.getPreviewFrameWidth());
                        int heightRatio = (int) Math.ceil((double) height / mContext.getPreviewFrameHeight());
                        inSampleSize = Integer.highestOneBit(Math.max(widthRatio, heightRatio));
                    } catch (IOException ex) {
                        Log.e(TAG, "cannot read exif", ex);
                    }
                    
                } else {
                    int ratio = (int) Math.ceil((double) mWidth / thumbnailWidth);
                    inSampleSize = Integer.highestOneBit(ratio); 
                }
                
                thumb = Thumbnail.createThumbnail(mData, mOrientation, inSampleSize, mUri);
            }
            Log.i(TAG, "createThumbnail(" + thumbnailWidth + ") mOrientation=" + mOrientation
                    + ", mUri=" + mUri);
            return thumb;
        }
    }
    
    private class PanoOperator extends RequestOperator {
        private PanoOperator(int pictureType) {
            mTempPictureType = pictureType;
        }
        @Override
        public void prepareRequest() {
            mFileType = Storage.FILE_TYPE_PANO;
            mDateTaken = System.currentTimeMillis();
            Location loc = mContext.getLocationManager().getCurrentLocation();
            if (loc != null) {
                mLocation = new Location(loc);
            }
            mTitle = createName(mFileType, mDateTaken, 0,0);
            mDisplayName = Storage.generateFileName(mTitle, mTempPictureType);
            mFilePath = Storage.generateFilepath(mDisplayName);
            mTempFilePath = mFilePath + TEMP_SUFFIX;
            //mStereoType = Storage.generateStereoType(mContext.getStereo3DType());
            mStereoType = (Storage.PICTURE_TYPE_MPO_3D == mTempPictureType) ? 
                    MediaStore.ThreeDimensionColumns.STEREO_TYPE_SIDE_BY_SIDE :
                        MediaStore.ThreeDimensionColumns.STEREO_TYPE_2D;
        }
        @Override
        public void addRequest() {
            addSaveRequest(this);
        }
        @Override
        public void saveRequest() {
            //title, file path, temp file path is ready
            FileOutputStream out = null;
            try {
                // Write to a temporary file and rename it to the final name. This
                // avoids other apps reading incomplete data.
                out = new FileOutputStream(mTempFilePath);
                out.write(mData);
                out.close();
                new File(mTempFilePath).renameTo(new File(mFilePath));
            } catch (IOException e) {
                Log.e(TAG, "Failed to write image", e);
            } finally {
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException e) {
                        Log.e(TAG, "saveRequest()", e);
                    }
                }
            }
            mDataSize = new File(mFilePath).length();
            try {
                ExifInterface exif = new ExifInterface(mFilePath);
                int orientation = Util.getExifOrientation(exif);
                int width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0);
                int height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0);
                mWidth = width;
                mHeight = height;
                mOrientation = orientation;
            } catch (IOException ex) {
                Log.e(TAG, "cannot read exif", ex);
            }
            mMimeType = Storage.generateMimetype(mTitle, mTempPictureType);
            mMpoType = Storage.generateMpoType(mTempPictureType);
            
            saveImageToDatabase(this);
        }
        @Override
        public Thumbnail createThumbnail(int thumbnailWidth) {
            Thumbnail thumb = null;
            if (mUri != null) {
                // Create a thumbnail whose width is equal or bigger than
                // that of the thumbnail view.
                int widthRatio = (int) Math.ceil((double) mWidth / mContext.getPreviewFrameWidth());
                int heightRatio = (int) Math.ceil((double) mWidth / mContext.getPreviewFrameHeight());
                int inSampleSize = Integer.highestOneBit(Math.max(widthRatio, heightRatio));
                thumb = Thumbnail.createThumbnail(mFilePath, mOrientation, inSampleSize, mUri, mStereoType);
            }
            Log.i(TAG, "createThumbnail(" + thumbnailWidth + ") mOrientation=" + mOrientation
                    + ", mUri=" + mUri + ", mFilePath=" + mFilePath + ", return " + thumb);
            return thumb;
        }
    }
    
    private class VideoOperator extends RequestOperator {
        private VideoOperator(int fileType, int outputFileFormat, String resolution) {
            mFileType = fileType;
            mTempOutputFileFormat = outputFileFormat;
            mResolution = resolution;
        }
        @Override
        public void prepareRequest() {
            mFileType = Storage.FILE_TYPE_VIDEO;
            mDateTaken = System.currentTimeMillis();
            mTitle = createName(mFileType, mDateTaken, 0,0);
            mDisplayName = mTitle + convertOutputFormatToFileExt(mTempOutputFileFormat);
            mMimeType = convertOutputFormatToMimeType(mTempOutputFileFormat);
            mFilePath = Storage.generateFilepath(mDisplayName);
	    //mStereoType = Storage.generateStereoType(mContext.getStereo3DType());
            mStereoType = Storage.generateStereoType(null);
            mIsMTK3D = getMTK3DType(mStereoType);
        }
        @Override
        public void addRequest() {
            Log.i(TAG, "videoOperator,addRequest");
            addSaveRequest(this);
        }
        @Override
        public void saveRequest() {
            //need video compute duration
            Log.d(TAG, "saveRequest,VideoOperator,write to DB ",new Throwable());
            try {
                File temp = new File(mTempFilePath);
                File file = new File(mFilePath);
                temp.renameTo(file);
                mDataSize = file.length();
                
                ContentValues values = new ContentValues(13);
                values.put(Video.Media.TITLE, mTitle);
                values.put(Video.Media.DISPLAY_NAME, mDisplayName);
                values.put(Video.Media.DATE_TAKEN, mDateTaken);
                values.put(Video.Media.MIME_TYPE, mMimeType);
                values.put(Video.Media.DATA, mFilePath);
                values.put(Video.Media.SIZE, mDataSize);
                values.put(Video.Media.STEREO_TYPE, mStereoType);
                if (mLocation != null) {
                    values.put(Video.Media.LATITUDE, mLocation.getLatitude());
                    values.put(Video.Media.LONGITUDE, mLocation.getLongitude());
                }
                values.put(Video.Media.RESOLUTION, mResolution);
                values.put(Video.Media.DURATION, mDuration);
                values.put(Video.Media.IS_LIVE_PHOTO, mLivePhoto);
                values.put(Video.Media.SLOW_MOTION_SPEED,mSlowMotionSpeed);
                mUri = mResolver.insert(Video.Media.EXTERNAL_CONTENT_URI, values);
                mContext.addSecureAlbumItemIfNeeded(true, mUri);
                mContext.sendBroadcast(new Intent(android.hardware.Camera.ACTION_NEW_VIDEO, mUri));
            } catch (Throwable th)  { //Here we keep google default, don't follow check style
                // This can happen when the external volume is already mounted, but
                // MediaScanner has not notify MediaProvider to add that volume.
                // The picture is still safe and MediaScanner will find it and
                // insert it into MediaProvider. The only problem is that the user
                // cannot click the thumbnail to review the picture.
                Log.e(TAG, "Failed to write MediaStore", th);
            }
            Log.i(TAG, "end of wirte to DB,mUri = " +mUri);
        }
        @Override
        public Thumbnail createThumbnail(int thumbnailWidth) {
            Thumbnail thumb = null;
            if (mUri != null) {
                Bitmap videoFrame = Thumbnail.createVideoThumbnailBitmap(mFilePath, thumbnailWidth, mLivePhoto);
                // split if it is 3D mode
                videoFrame = Thumbnail.create2DFileFromBitmap(videoFrame,mStereoType);
                if (videoFrame != null) {
                    thumb = Thumbnail.createThumbnail(mUri, videoFrame, 0);
                }
            }
            return thumb;
        }
    }
    
    private String convertOutputFormatToFileExt(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return ".mp4";
        }
        return ".3gp";
    }
    
    private String convertOutputFormatToMimeType(int outputFileFormat) {
        if (outputFileFormat == MediaRecorder.OutputFormat.MPEG_4) {
            return "video/mp4";
        }
        return "video/3gpp";
    }
    
    private String createName(int fileType, long dateTaken, int count,int tag) {
        if (mFileNamer == null) {
            mFileNamer = new HashMap<Integer, ImageFileNamer>();
            ImageFileNamer photo = new ImageFileNamer(
                    mContext.getString(R.string.image_file_name_format));
            mFileNamer.put(Storage.FILE_TYPE_PHOTO, photo);
            //pano_file_name_format changed to image format for UX design.
            mFileNamer.put(Storage.FILE_TYPE_PANO, photo);
            mFileNamer.put(Storage.FILE_TYPE_VIDEO, new ImageFileNamer(
                    mContext.getString(R.string.video_file_name_format)));
            mFileNamer.put(Storage.FILE_TYPE_LIV, new ImageFileNamer(
                    mContext.getString(R.string.livephoto_file_name_format)));
        }
        Date date = new Date(dateTaken);
        String name = null;
        // tag != 0 is used for the last blend picture 
        if (count == 0 || tag != 0) {
            name = mFileNamer.get(fileType).generateName(dateTaken,tag);
        } else if (count > 0) {
            name = mFileNamer.get(fileType).generateContinuousName(dateTaken, count);
        }
        Log.i(TAG, "createName(" + fileType + ", " + dateTaken + ")");
        return name;
    }
}
