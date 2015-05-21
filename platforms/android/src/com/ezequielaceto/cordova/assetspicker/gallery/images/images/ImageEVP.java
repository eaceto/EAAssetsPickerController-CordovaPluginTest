package com.ezequielaceto.cordova.assetspicker.gallery.images.images;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.IOException;

/**
 * Created by David on 21/07/14.
 */
public class ImageEVP extends ImageResizer {

    private static final String TAG = "ImageEvper";
    private static final int EVP_CACHE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String EVP_CACHE_DIR = "evp";
    private static final int IO_BUFFER_SIZE = 8 * 1024;

    private DiskLruCache mEvpDiskCache;
    private File mEvpCacheDir;
    private boolean mEvpDiskCacheStarting = true;
    private final Object mEvpDiskCacheLock = new Object();
    private static final int DISK_CACHE_INDEX = 0;


    public ImageEVP(Context context, int imageWidth, int imageHeight) {
        super(context, imageWidth, imageHeight);
        mEvpCacheDir = ImageCache.getDiskCacheDir(context, EVP_CACHE_DIR);
    }

    public ImageEVP(Context context, int imageSize) {
        super(context, imageSize);
        mEvpCacheDir = ImageCache.getDiskCacheDir(context, EVP_CACHE_DIR);
    }


    /**
     * The main processing method. This happens in a background task. In this case we are just
     * sampling down the bitmap and returning it from a resource.
     *
     * @param url
     * @return
     */
    private Bitmap processBitmap(String url) {
        return decodeSampledBitmapFromFile(url, mImageWidth, mImageHeight, getImageCache());
    }

    @Override
    protected Bitmap processBitmap(Object data) {
        return processBitmap((String)data);
    }

    @Override
    protected void initDiskCacheInternal() {
        super.initDiskCacheInternal();
        initEvpDiskCache();
    }

    private void initEvpDiskCache() {
        if (!mEvpCacheDir.exists()) {
            mEvpCacheDir.mkdirs();
        }
        synchronized (mEvpDiskCacheLock) {
            if (ImageCache.getUsableSpace(mEvpCacheDir) > EVP_CACHE_SIZE) {
                try {
                    mEvpDiskCache = DiskLruCache.open(mEvpCacheDir, 1, 1, EVP_CACHE_SIZE);
                } catch (IOException e) {
                    mEvpDiskCache = null;
                }
            }
            mEvpDiskCacheStarting = false;
            mEvpDiskCacheLock.notifyAll();
        }
    }

    @Override
    protected void clearCacheInternal() {
        super.clearCacheInternal();
        synchronized (mEvpDiskCacheLock) {
            if (mEvpDiskCache != null && !mEvpDiskCache.isClosed()) {
                try {
                    mEvpDiskCache.delete();
                } catch (IOException e) {
                    Log.e(TAG, "clearCacheInternal - " + e);
                }
                mEvpDiskCache = null;
                mEvpDiskCacheStarting = true;
                initEvpDiskCache();
            }
        }
    }

    @Override
    protected void flushCacheInternal() {
        super.flushCacheInternal();
        synchronized (mEvpDiskCacheLock) {
            if (mEvpDiskCache != null) {
                try {
                    mEvpDiskCache.flush();
                } catch (IOException e) {
                    Log.e(TAG, "flush - " + e);
                }
            }
        }
    }

    @Override
    protected void closeCacheInternal() {
        super.closeCacheInternal();
        synchronized (mEvpDiskCacheLock) {
            if (mEvpDiskCache != null) {
                try {
                    if (!mEvpDiskCache.isClosed()) {
                        mEvpDiskCache.close();
                        mEvpDiskCache = null;
                    }
                } catch (IOException e) {
                    Log.e(TAG, "closeCacheInternal - " + e);
                }
            }
        }
    }
}
