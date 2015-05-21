/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.ezequielaceto.cordova.assetspicker.gallery.local;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.*;
import android.view.View.OnTouchListener;
import android.widget.TextView;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.*;
import com.ezequielaceto.cordova.assetspicker.test.R;

// This activity can display a whole picture and navigate them in a specific
// gallery. It has two modes: normal mode and slide show mode. In normal mode
// the user view one image at a time, and can click "previous" and "next"
// button to see the previous or next image. In slide show mode it shows one
// image after another, with some transition effect.
public class ViewImageActivity extends ActionBarActivity
        implements View.OnClickListener {

    private static final String STATE_URI = "uri";
    private static final String STATE_SLIDESHOW = "slideshow";
    private static final String TAG = "ViewImage";

    private ImageGetter mGetter;
    private Uri mSavedUri;
    boolean mPaused = true;
    private boolean mShowControls = true;

    // Choices for what adjacents to load.
    private static final int[] sOrderAdjacents = new int[]{0, 1, -1};
    final GetterHandler mHandler = new GetterHandler();

    static final int MODE_NORMAL = 1;
    static final int MODE_SLIDESHOW = 2;
    private int mMode = MODE_NORMAL;
    int mCurrentPosition = 0;
    private SharedPreferences mPrefs;
    public static final String KEY_IMAGE_LIST = "image_list";
    private static final String STATE_SHOW_CONTROLS = "show_controls";

    IImageList mAllImages;

    private ImageManager.ImageListParam mParam;
    GestureDetector mGestureDetector;

    // The image view displayed for normal mode.
    private ImageViewTouch mImageView;
    // This is the cache for thumbnail bitmaps.
    private BitmapCache mCache;
    private final Runnable mDismissOnScreenControlRunner = new Runnable() {
        public void run() {
//            hideOnScreenControls();
        }
    };
    private TextView btnCancel;
    private TextView btnUse;
    private Intent intent;


    private void showOnScreenControls() {
        if (mPaused) return;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent m) {
        if (mPaused) return true;
        return super.dispatchTouchEvent(m);
    }

    @Override
    public void onClick(View view) {

    }

    private void scheduleDismissOnScreenControls() {
        mHandler.removeCallbacks(mDismissOnScreenControlRunner);
        mHandler.postDelayed(mDismissOnScreenControlRunner, 2000);
    }

    private void setupOnScreenControls(View rootView, View ownerView) {
        setupOnTouchListeners(rootView);
    }


    private void setupOnTouchListeners(View rootView) {
        mGestureDetector = new GestureDetector(this, new MyGestureListener());

        // If the user touches anywhere on the panel (including the
        // next/prev button). We show the on-screen controls. In addition
        // to that, if the touch is not on the prev/next button, we
        // pass the event to the gesture detector to detect double tap.
        final OnTouchListener buttonListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                scheduleDismissOnScreenControls();
                return false;
            }
        };

        OnTouchListener rootListener = new OnTouchListener() {
            public boolean onTouch(View v, MotionEvent event) {
                buttonListener.onTouch(v, event);
                mGestureDetector.onTouchEvent(event);

                // We do not use the return value of
                // mGestureDetector.onTouchEvent because we will not receive
                // the "up" event if we return false for the "down" event.
                return true;
            }
        };

        rootView.setOnTouchListener(rootListener);
    }

    private class MyGestureListener extends
            GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {
            if (mPaused) return false;
            ImageViewTouch imageView = mImageView;
            if (imageView.getScale() > 1F) {
                imageView.postTranslateCenter(-distanceX, -distanceY);
            }
            return true;
        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            if (mPaused) return false;
            setMode(MODE_NORMAL);
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (mPaused) return false;
            showOnScreenControls();
            scheduleDismissOnScreenControls();
            return true;
        }

        @Override
        public boolean onDoubleTap(MotionEvent e) {
            if (mPaused) return false;
            ImageViewTouch imageView = mImageView;

            // Switch between the original scale and 3x scale.
            if (imageView.getScale() > 1F) {
                mImageView.zoomTo(1f);
            } else {
                mImageView.zoomToPoint(2f, e.getX(), e.getY());
            }
            return true;
        }
    }

    boolean isPickIntent() {
        String action = getIntent().getAction();
        return (Intent.ACTION_PICK.equals(action)
                || Intent.ACTION_GET_CONTENT.equals(action));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {

        super.onPrepareOptionsMenu(menu);
        if (mPaused) return false;

        setMode(MODE_NORMAL);

        return true;
    }

    void setImage(int pos, boolean showControls) {
        mCurrentPosition = pos;

        Bitmap b = mCache.getBitmap(pos);
        if (b != null) {
            IImage image = mAllImages.getImageAt(pos);
            mImageView.setImageRotateBitmapResetBase(
                    new RotateBitmap(b, image.getDegreesRotated()), true);
        }

        ImageGetterCallback cb = new ImageGetterCallback() {
            public void completed() {
            }

            public boolean wantsThumbnail(int pos, int offset) {
                return !mCache.hasBitmap(pos + offset);
            }

            public boolean wantsFullImage(int pos, int offset) {
                return offset == 0;
            }

            public int fullImageSizeToUse(int pos, int offset) {
                // this number should be bigger so that we can zoom.  we may
                // need to get fancier and read in the fuller size image as the
                // user starts to zoom.
                // Originally the value is set to 480 in order to avoid OOM.
                // Now we set it to 2048 because of using
                // native memory allocation for Bitmaps.
                final int imageViewSize = 2048;
                return imageViewSize;
            }

            public int[] loadOrder() {
                return sOrderAdjacents;
            }

            public void imageLoaded(int pos, int offset, RotateBitmap bitmap,
                                    boolean isThumb) {
                // shouldn't get here after onPause()

                // We may get a result from a previous request. Ignore it.
                if (pos != mCurrentPosition) {
                    bitmap.recycle();
                    System.gc();
                    return;
                }

                if (isThumb) {
                    mCache.put(pos + offset, bitmap.getBitmap());
                }
                if (offset == 0) {
                    // isThumb: We always load thumb bitmap first, so we will
                    // reset the supp matrix for then thumb bitmap, and keep
                    // the supp matrix when the full bitmap is loaded.
                    mImageView.setImageRotateBitmapResetBase(bitmap, isThumb);
                }
            }
        };

        // Could be null if we're stopping a slide show in the course of pausing
        if (mGetter != null) {
            mGetter.setPosition(pos, cb, mAllImages, mHandler);
        }
        if (showControls) showOnScreenControls();
        scheduleDismissOnScreenControls();
    }

    @Override
    public void onCreate(Bundle instanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(instanceState);

        intent = getIntent();
        mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        setContentView(R.layout.viewimage);

        mImageView = (ImageViewTouch) findViewById(R.id.imageViewTouch);
        mImageView.setEnableTrackballScroll(true);
        mCache = new BitmapCache(3);
        mImageView.setRecycler(mCache);

        makeGetter();

        mParam = getIntent().getParcelableExtra(KEY_IMAGE_LIST);

        if (instanceState != null) {
            mSavedUri = instanceState.getParcelable(STATE_URI);
            mShowControls = instanceState.getBoolean(STATE_SHOW_CONTROLS, true);
        } else {
            mSavedUri = getIntent().getData();
        }

        setupOnScreenControls(findViewById(R.id.rootLayout), mImageView);

        hideRemoveButton();
        loadUseButton();
    }

    private void hideRemoveButton() {
        btnCancel = (TextView) findViewById(R.id.remove_local_media_button);
        btnCancel.setVisibility(View.GONE);
    }

    private void loadUseButton() {
        btnUse = (TextView) findViewById(R.id.use_local_media_button);
        btnUse.setVisibility(View.VISIBLE);
        btnUse.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                //setResult(IntentMediaBuilder.ADD_MEDIA_OBJECT_RESULT, intent);
                finish();
            }
        });
    }

    void setMode(int mode) {
        if (mMode == mode) {
            return;
        }

        mMode = mode;

        if (mGetter != null) {
            mGetter.cancelCurrent();
        }

        // mGetter null is a proxy for being paused
        if (mGetter != null) {
            setImage(mCurrentPosition, true);
        }
    }

    private void makeGetter() {
        mGetter = new ImageGetter(getContentResolver());
    }

    private IImageList buildImageListFromUri(Uri uri) {
        String sortOrder = mPrefs.getString(
                "pref_gallery_sort_key", "descending");
        int sort = sortOrder.equals("ascending")
                ? ImageManager.SORT_ASCENDING
                : ImageManager.SORT_DESCENDING;
        return ImageManager.makeImageList(this, getContentResolver(), uri, sort);
    }

    private boolean init(Uri uri) {
        if (uri == null) return false;
        mAllImages = (mParam == null)
                ? buildImageListFromUri(uri)
                : ImageManager.makeImageList(this, getContentResolver(), mParam);
        IImage image = mAllImages.getImageForUri(uri);
        if (image == null) return false;
        mCurrentPosition = mAllImages.getImageIndex(image);
        return true;
    }

    private Uri getCurrentUri() {
        if (mAllImages.getCount() == 0) return null;
        IImage image = mAllImages.getImageAt(mCurrentPosition);
        if (image == null) return null;
        return image.fullSizeImageUri();
    }

    @Override
    public void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putParcelable(STATE_URI,
                mAllImages.getImageAt(mCurrentPosition).fullSizeImageUri());
        b.putBoolean(STATE_SLIDESHOW, mMode == MODE_SLIDESHOW);
    }

    @Override
    public void onStart() {
        super.onStart();
        mPaused = false;

        if (!init(mSavedUri)) {
            Log.w(TAG, "init failed: " + mSavedUri);
            finish();
            return;
        }

        // normally this will never be zero but if one "backs" into this
        // activity after removing the sdcard it could be zero.  in that
        // case just "finish" since there's nothing useful that can happen.
        int count = mAllImages.getCount();
        if (count == 0) {
            finish();
            return;
        } else if (count <= mCurrentPosition) {
            mCurrentPosition = count - 1;
        }

        if (mGetter == null) {
            makeGetter();
        }

        setImage(mCurrentPosition, mShowControls);
        mShowControls = false;
    }

    @Override
    public void onStop() {
        super.onStop();
        mPaused = true;

        // mGetter could be null if we call finish() and leave early in
        // onStart().
        if (mGetter != null) {
            mGetter.cancelCurrent();
            mGetter.stop();
            mGetter = null;
        }
        setMode(MODE_NORMAL);

        // removing all callback in the message queue
        mHandler.removeAllGetterCallbacks();

        if (mAllImages != null) {
            mSavedUri = getCurrentUri();
            mAllImages.close();
            mAllImages = null;
        }

        mImageView.clear();
        mCache.clear();

    }
}
