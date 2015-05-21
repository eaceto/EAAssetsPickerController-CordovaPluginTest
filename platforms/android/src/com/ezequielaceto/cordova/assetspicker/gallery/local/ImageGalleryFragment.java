package com.ezequielaceto.cordova.assetspicker.gallery.local;

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

import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.support.v7.view.ActionMode;
import android.view.*;
import com.ezequielaceto.cordova.assetspicker.gallery.MediaPojo;
import com.ezequielaceto.cordova.assetspicker.gallery.MediaUtils;
import com.ezequielaceto.cordova.assetspicker.gallery.RealPathUtils;
import com.ezequielaceto.cordova.assetspicker.gallery.VersionUtils;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.IImage;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.IImageList;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.ImageLoader;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.ImageManager;
import com.ezequielaceto.cordova.assetspicker.test.R;

import java.io.Closeable;
import java.io.File;
import java.util.ArrayList;


public class ImageGalleryFragment extends Fragment implements GridViewSpecial.Listener,
        GridViewSpecial.DrawAdapter, ActionMode.Callback {

    private final String STATE_SCROLL_POSITION = "scroll_position";
    private final String STATE_SELECTED_INDEX = "first_index";
    private final String FILE_URI = "uri";

    private static final float INVALID_POSITION = -1f;

    private ImageManager.ImageListParam mParam;
    private IImageList mAllImages;
    private int mInclusion;
    boolean mSortAscending = false;
    private View mNoImagesView;

    private Dialog mMediaScanningDialog;
    private SharedPreferences mPrefs;
    private long mVideoSizeLimit = Long.MAX_VALUE;

    private BroadcastReceiver mReceiver = null;

    private final Handler mHandler = new Handler();
    private ImageLoader mLoader;
    private GridViewSpecial mGvs;
    private Uri mCropResultUri;

    // The index of the first picture in GridViewSpecial.
    private int mSelectedIndex = ImageGalleryFragment.INDEX_NONE;
    private float mScrollPosition = INVALID_POSITION;
    private boolean mConfigurationChanged = false;
    private boolean mMultiSelectMode = false;
    private ArrayList<IImage> mSelectedImages = new ArrayList<>();

    private final int kActionTakePictureIndex = 0;
    private final int kActionRecordVideoIndex = 1;
    private final int kActionPickGallery = 2;

    private static final int CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE = 100;
    private static final int CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE = 200;
    private static final int GALLERY_PICK_ACTIVITY_REQUEST_CODE = 300;
    private Uri fileUri;

    protected static final String HEADER_ID = "HEADER_ID";
    protected int MAX_SELECTED = 1;

    public ImageGalleryFragment() {
    }


    protected View mPostBar;
    protected ActionMode mActionMode;
    protected boolean mUseMediaObjectFromPreview = false;

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        View parentView = getParentFragment().getView();
        if (parentView != null)
            mPostBar = parentView.findViewById(getArguments().getInt(HEADER_ID));
    }

    @Override
    public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
        // Inflate the menu for the CAB
        MenuInflater inflater = actionMode.getMenuInflater();
        inflater.inflate(R.menu.select_media_local, menu);
        actionMode.setTitle(getResources().getString(R.string.select_media));
        if (mPostBar != null)
            mPostBar.setVisibility(View.GONE);
        return true;
    }

    protected abstract void addMediaObject();

    @Override
    public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
        return false;
    }

    @Override
    public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
        return false;
    }

    @Override
    public void onDestroyActionMode(ActionMode actionMode) {
        mActionMode = null;
        if (!mUseMediaObjectFromPreview) {
//            ((DefaultPostFragment) getParentFragment()).handleBackPressed();
            addMediaObject();
        }
        if (mPostBar != null)
            mPostBar.setVisibility(View.VISIBLE);
    }

    protected void startActionMode(int cantSelected) {
        if (VersionUtils.hasHoneycomb()) {
            if (mActionMode == null) {
            //    mActionMode = ((EverypostMainActivity) getActivity()).startSupportActionMode(this);
            }
            mActionMode.setSubtitle(getResources().getQuantityString(R.plurals.media_selected, cantSelected, cantSelected));
            mUseMediaObjectFromPreview = false;
        }
    }

    @Override
    public String getScreenName() {
        return "image_gallery";
    }

    public static ImageGalleryFragment newInstance(int headerViewID) {
        Bundle args = new Bundle();
        args.putInt(HEADER_ID, headerViewID);
        ImageGalleryFragment imageGalleryFragment = new ImageGalleryFragment();
        imageGalleryFragment.setArguments(args);
        return imageGalleryFragment;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.image_gallery, container, false);

        mNoImagesView = view.findViewById(R.id.no_images);
        mGvs = (GridViewSpecial) view.findViewById(R.id.grid);

        mGvs.setListener(this);
        if (savedInstanceState != null) {
            fileUri = savedInstanceState.getParcelable(FILE_URI);
        }
        return view;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());

        if (isPickIntent()) {
            mVideoSizeLimit = getActivity().getIntent().getLongExtra(
                    MediaStore.EXTRA_SIZE_LIMIT, Long.MAX_VALUE);
        } else {
            mVideoSizeLimit = Long.MAX_VALUE;
        }

        setupInclusion();

        mLoader = new ImageLoader(getActivity().getContentResolver(), mHandler);
    }

    /**
     * Only valid for one item selectable.
     *
     * @return
     */
    private IImage getCurrentImage() {
        return !mSelectedImages.isEmpty() ? mSelectedImages.get(0) : null;
    }


    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mConfigurationChanged = true;
    }

    private boolean isPickIntent() {
        String action = getActivity().getIntent().getAction();
        return (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT
                .equals(action));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        closePending();
    }


    //@Fede, if we make a join inside onPause method we freeze UI during onExit animation,
    // so when fragment is removing from back state we pass the responsibility to OnDestroy method.
    @Override
    public void onPause() {
        super.onPause();
        if (!isRemoving())
            closePending();
    }

    private void closePending() {

        mLoader.stop();
        mGvs.stop();

        if (mReceiver != null)
            mReceiver = null;

        // Now that we've paused the threads that are using the cursor it is
        // safe to close it.
        if (mAllImages != null) {
            mAllImages.close();
            mAllImages = null;
        }
    }


    private void rebake(boolean unmounted, boolean scanning) {
        mGvs.stop();
        if (mAllImages != null) {
            mAllImages.close();
            mAllImages = null;
        }

        if (mMediaScanningDialog != null) {
            mMediaScanningDialog.cancel();
            mMediaScanningDialog = null;
        }

        if (scanning) {
            mMediaScanningDialog = ProgressDialog.show(getActivity(), null,
                    "Waiting...", true, true);
        }

        mParam = allImages(!unmounted && !scanning);
        mAllImages = ImageManager.makeImageList(getActivity().getApplicationContext(), getActivity().getContentResolver(), mParam);

        mGvs.setImageList(mAllImages);
        mGvs.setDrawAdapter(this);
        mGvs.setLoader(mLoader);
        mGvs.start();
        mNoImagesView.setVisibility(mAllImages.getCount() > 0 ? View.GONE
                : View.VISIBLE);
    }

    @Override
    public void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putFloat(STATE_SCROLL_POSITION, mScrollPosition);
        state.putInt(STATE_SELECTED_INDEX, mSelectedIndex);
        state.putParcelable(FILE_URI, fileUri);
    }

    @Override
    public int getButtonID() {
        return 0;
    }

    @Override
    public void onViewStateRestored(Bundle state) {
        super.onViewStateRestored(state);
        if (state != null) {
            mScrollPosition = state.getFloat(STATE_SCROLL_POSITION,
                    INVALID_POSITION);
            mSelectedIndex = state.getInt(STATE_SELECTED_INDEX, 0);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mGvs.requestFocus();
        String sortOrder = mPrefs.getString("pref_gallery_sort_key", null);
        if (sortOrder != null) {
            mSortAscending = sortOrder.equals("ascending");
        }
        rebake(false, ImageManager.isMediaScannerScanning(getActivity().getContentResolver()));
        int size = mSelectedImages.size();
        //startActionMode(size);
    }

    // Setup what we include (image/video) in the gallery and the title of the gallery.
    private void setupInclusion() {
        mInclusion = ImageManager.INCLUDE_IMAGES | ImageManager.INCLUDE_VIDEOS;
    }

    // Returns the image list parameter which contains the subset of image/video
    // we want.
    private ImageManager.ImageListParam allImages(boolean storageAvailable) {
        if (!storageAvailable) {
            return ImageManager.getEmptyImageListParam();
        } else {
            Uri uri = getActivity().getIntent().getData();
            return ImageManager.getImageListParam(
                    ImageManager.DataLocation.EXTERNAL, mInclusion,
                    mSortAscending ? ImageManager.SORT_ASCENDING
                            : ImageManager.SORT_DESCENDING,
                    (uri != null) ? uri.getQueryParameter("bucketId") : null);
        }
    }

    private void toggleMultiSelected(IImage image) {

        int selectedSize = mSelectedImages.size();

        if (!mMultiSelectMode) {
            if (!mSelectedImages.contains(image))
                mSelectedImages.clear();
        } else {
            if (selectedSize > MAX_SELECTED && !mSelectedImages.contains(image)) {
                return;
            }
        }

        if (mSelectedImages.contains(image)) {
            mSelectedImages.remove(image);
        } else {
            mSelectedImages.add(image);
        }
        mGvs.invalidate();
    }

    public void onImageClicked(int index) {

        if (index < 0 || index >= mAllImages.getCount())
            return;

        mSelectedIndex = index;
        mGvs.setSelectedIndex(index);
    }

    public static final int INDEX_NONE = -1;


    private void takeAPicture() {
        try {
            // create Intent to take a picture and return control to the calling application
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            // create a file to save the image
            fileUri = MediaUtils.getOutputMediaFileUri(MediaUtils.MEDIA_TYPE_IMAGE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
            startActivityForResult(intent, CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    private void recordVideo() {
        try {
            // create a file to save the video
            Intent intent = new Intent(MediaStore.ACTION_VIDEO_CAPTURE);
            fileUri = MediaUtils.getOutputMediaFileUri(MediaUtils.MEDIA_TYPE_VIDEO);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, fileUri);
            // set the video image quality to high
            intent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1);
            startActivityForResult(intent, CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case CAPTURE_IMAGE_ACTIVITY_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    // Image captured and saved to fileUri specified in the Intent

                    File file = new File(fileUri.getPath());
                    MediaUtils.addMediaToGallery(file, getActivity());

                } else if (resultCode == Activity.RESULT_CANCELED) {
                    File file = new File(fileUri.getPath());
                    file.delete();
                }

                break;
            case CAPTURE_VIDEO_ACTIVITY_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    // Video captured and saved to fileUri specified in the Intent

                    File file = new File(fileUri.getPath());
                    MediaUtils.addMediaToGallery(file, getActivity());

                } else if (resultCode == Activity.RESULT_CANCELED) {
                    File file = new File(fileUri.getPath());
                    file.delete();
                }

                break;
            case GALLERY_PICK_ACTIVITY_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {

                    MediaPojo mediaPojo;
                    if (VersionUtils.hasKitKat()) {
                        mediaPojo = RealPathUtils.getRealPathFromURI_API19(getActivity(), data.getData());
                    } else {
                        mediaPojo = RealPathUtils.getRealPathFromURI_API11to18(getActivity(), data.getData());
                    }

                    mediaPojo.getPath();
                }
        }
    }

    @Override
    public void onClickImageAction(int index) {

        if (isIconBlock(index)) {
            handleIconClick(index);
            return;
        }

        // TODO Implement - Kimi
        /*
        MitoMediaObject mediaObject = null;
        IImage resource = mAllImages.getImageAt(index);

        if (resource != null && resource.getDataPath() != null) {

            Uri mediaUri = Uri.parse(resource.getDataPath());
            if (MediaUtils.isImageMimeType(resource.getMimeType())) {
                mediaObject = MitoMediaObject.localPhotoFromCamera(mediaUri.getPath());
            } else if (MediaUtils.isVideoMimeType(resource.getMimeType())) {
                mediaObject = MitoMediaObject.localVideoFromCamera(mediaUri.getPath());
            }

            Intent intent = IntentMediaBuilder.buildDetailIntent(getActivity(), mediaObject, true, false);
            if (intent != null) {
                startActivityForResult(intent, IntentMediaBuilder.SHOW_MEDIA_DETAIL_REQUEST_CODE);
                MitoAnimationHelper.slideIn(getActivity());
            }
        }
        */
    }

    private void handleIconClick(int index) {
        if (index == kActionTakePictureIndex) {
            takeAPicture();
        } else if (index == kActionRecordVideoIndex) {
            recordVideo();
        } else if (index == kActionPickGallery) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT,
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/* videos/*");
            startActivityForResult(Intent.createChooser(intent, "Seleccionar")
                    , GALLERY_PICK_ACTIVITY_REQUEST_CODE);
            getActivity().overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
        }
    }


    //@Fede handling click event, only if is not an icon.
    public void onImageSelected(int index) {

        if (isIconBlock(index)) {
            handleIconClick(index);
            return;
        }

        mGvs.setSelectedIndex(index);

        toggleMultiSelected(mAllImages.getImageAt(index));
        int size = mSelectedImages.size();

        //startActionMode(size);
    }

    //@Fede Index is the position of the image in the gallery, first 2 are icons(Camera and video).
    private boolean isIconBlock(int index) {
        return index == 0 || index == 1 || index == 2;
    }

    public void onLayoutComplete(boolean changed) {
        if (mCropResultUri != null) {
            IImage image = mAllImages.getImageForUri(mCropResultUri);
            mCropResultUri = null;
            if (image != null) {
                mSelectedIndex = mAllImages.getImageIndex(image);
            }
        }
        mGvs.setSelectedIndex(mSelectedIndex);
        if (mScrollPosition == INVALID_POSITION) {
            if (mSortAscending) {
                mGvs.scrollTo(0, mGvs.getHeight());
            } else {
                mGvs.scrollToImage(0);
            }
        } else if (mConfigurationChanged) {
            mConfigurationChanged = false;
            mGvs.scrollTo(mScrollPosition);
            if (mGvs.getCurrentSelection() != ImageGalleryFragment.INDEX_NONE) {
                mGvs.scrollToVisible(mSelectedIndex);
            }
        } else {
            mGvs.scrollTo(mScrollPosition);
        }
    }

    public void onScroll(float scrollPosition) {
        mScrollPosition = scrollPosition;
    }

    private Drawable mVideoOverlay;
    private Drawable mVideoMmsErrorOverlay;
    private Drawable mMultiSelectTrue;
    private Drawable mMultiSelectFalse;

    // mSrcRect and mDstRect are only used in drawImage, but we put them as
    // instance variables to reduce the memory allocation overhead because
    // drawImage() is called a lot.
    private final Rect mSrcRect = new Rect();
    private final Rect mDstRect = new Rect();

    private final Paint mPaint = new Paint(Paint.FILTER_BITMAP_FLAG);

    public void drawImage(Canvas canvas, IImage image, Bitmap b, int xPos,
                          int yPos, int w, int h) {

        if (b != null && !b.isRecycled()) {
            // if the image is close to the target size then crop,
            // otherwise scale both the bitmap and the view should be
            // square but I suppose that could change in the future.

            int bw = b.getWidth();
            int bh = b.getHeight();

            int deltaW = bw - w;
            int deltaH = bh - h;

            if (deltaW >= 0 && deltaW < 10 && deltaH >= 0 && deltaH < 10) {
                int halfDeltaW = deltaW / 2;
                int halfDeltaH = deltaH / 2;
                mSrcRect.set(halfDeltaW, halfDeltaH, bw - halfDeltaW,
                        bh - halfDeltaH);
                mDstRect.set(xPos, yPos, xPos + w, yPos + h);
                canvas.drawBitmap(b, mSrcRect, mDstRect, null);
            } else {
                mSrcRect.set(0, 0, bw, bh);
                mDstRect.set(xPos, yPos, xPos + w, yPos + h);
                canvas.drawBitmap(b, mSrcRect, mDstRect, mPaint);
            }
        } else {
            // If the thumbnail cannot be drawn, put up an error icon
            // instead
            Bitmap error = getErrorBitmap(image);
            int width = error.getWidth();
            int height = error.getHeight();
            mSrcRect.set(0, 0, width, height);
            int left = (w - width) / 2 + xPos;
            int top = (w - height) / 2 + yPos;
            mDstRect.set(left, top, left + width, top + height);
            canvas.drawBitmap(error, mSrcRect, mDstRect, null);
        }

        if (ImageManager.isVideo(image)) {

            Drawable overlay;

            long size = getImageFileSize(image);

            if (size >= 0 && size <= mVideoSizeLimit) {
                if (mVideoOverlay == null) {
                    mVideoOverlay = getResources().getDrawable(
                            R.drawable.ic_gallery_video_overlay);
                }
                overlay = mVideoOverlay;
            } else {
                if (mVideoMmsErrorOverlay == null) {
                    mVideoMmsErrorOverlay = getResources().getDrawable(
                            R.drawable.ic_error_mms_video_overlay);
                }
                overlay = mVideoMmsErrorOverlay;
                Paint paint = new Paint();
                paint.setARGB(0x80, 0x00, 0x00, 0x00);
                canvas.drawRect(xPos, yPos, xPos + w, yPos + h, paint);
            }
            int width = overlay.getIntrinsicWidth();
            int height = overlay.getIntrinsicHeight();
            int left = (w - width) / 2 + xPos;
            int top = (h - height) / 2 + yPos;
            mSrcRect.set(left, top, left + width, top + height);
            overlay.setBounds(mSrcRect);
            overlay.draw(canvas);
        }
    }

    public static long getImageFileSize(IImage image) {
        java.io.InputStream data = image.fullSizeImageData();
        if (data == null) return -1;
        try {
            return data.available();
        } catch (java.io.IOException ex) {
            return -1;
        } finally {
            closeSilently(data);
        }
    }

    public static void closeSilently(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Throwable e) {
                // ignore
            }
        }
    }


    public void drawDecoration(Canvas canvas, IImage image, int xPos, int yPos,
                               int w, int h) {
        if (mSelectedImages != null) {
            initializeMultiSelectDrawables();

            Drawable checkBox = mSelectedImages.contains(image) ? mMultiSelectTrue
                    : mMultiSelectFalse;
            int width = checkBox.getIntrinsicWidth();
            int height = checkBox.getIntrinsicHeight();
            int left = w + xPos - width - 10;
            int top = h - height - 10 + yPos;
            mSrcRect.set(left, top, left + width, top + height);
            checkBox.setBounds(mSrcRect);
            checkBox.draw(canvas);
        }
    }

    @Override
    public boolean needsDecoration() {
        return true;
    }

    @Override
    public void drawFilledRectangle(Canvas canvas, int x, int y, int w, int h, int color) {
        Paint paint = new Paint();
        Rect r = new Rect(x, y, x + w, y + h);

        // border
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        canvas.drawRect(r, paint);
    }

    private void initializeMultiSelectDrawables() {
        if (mMultiSelectTrue == null) {
            mMultiSelectTrue = getResources().getDrawable(
                    R.drawable.check_icon);
        }
        if (mMultiSelectFalse == null) {
            mMultiSelectFalse = getResources().getDrawable(
                    R.drawable.unchecked_icon);
        }
    }

    private Bitmap mMissingImageThumbnailBitmap;
    private Bitmap mMissingVideoThumbnailBitmap;

    // Create this bitmap lazily,
    // and only once for all the ImageBlocks to use
    public Bitmap getErrorBitmap(IImage image) {
        if (ImageManager.isImage(image)) {
            if (mMissingImageThumbnailBitmap == null) {
                mMissingImageThumbnailBitmap = BitmapFactory
                        .decodeResource(getResources(),
                                R.drawable.ic_missing_thumbnail_picture);
            }
            return mMissingImageThumbnailBitmap;
        } else {
            if (mMissingVideoThumbnailBitmap == null) {
                mMissingVideoThumbnailBitmap = BitmapFactory.decodeResource(
                        getResources(), R.drawable.ic_missing_thumbnail_video);
            }
            return mMissingVideoThumbnailBitmap;
        }
    }

}
