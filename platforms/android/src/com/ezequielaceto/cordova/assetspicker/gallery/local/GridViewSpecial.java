/*
 * Copyright (C) 2009 The Android Open Source Project
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

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.media.AudioManager;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.*;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.widget.Scroller;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.IImage;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.IImageList;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.ImageLoader;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.ImageManager;


public class GridViewSpecial extends View {

    private GestureDetector mGestureDetector;

    public static void Assert(boolean cond) {
        if (!cond) {
            throw new AssertionError();
        }
    }

    private static final float MAX_FLING_VELOCITY = 7500;
    private int mScrollX;
    private int mScrollY;

    public static interface Listener {

        public void onClickImageAction(int index);

        public void onImageClicked(int index);

        public void onImageSelected(int index);

        public void onLayoutComplete(boolean changed);

        /**
         * Invoked when the <code>GridViewSpecial</code> scrolls.
         *
         * @param scrollPosition the position of the scroller in the range
         *                       [0, 1], when 0 means on the top and 1 means on the buttom
         */
        public void onScroll(float scrollPosition);
    }

    public static interface DrawAdapter {
        public void drawImage(Canvas canvas, IImage image,
                              Bitmap b, int xPos, int yPos, int w, int h);

        public void drawDecoration(Canvas canvas, IImage image,
                                   int xPos, int yPos, int w, int h);

        public boolean needsDecoration();

        public void drawFilledRectangle(Canvas canvas, int x, int y, int w, int h, int color);
    }

    public static final int INDEX_NONE = -1;

    // There are two cell size we will use. It can be set by setSizeChoice().
    // The mLeftEdgePadding fields is filled in onLayout(). See the comments
    // in onLayout() for details.
    static class LayoutSpec {
        LayoutSpec(int w, int h, int intercellSpacing, int leftEdgePadding,
                   DisplayMetrics metrics) {
            mCellWidth = dpToPx(w, metrics);
            mCellHeight = dpToPx(h, metrics);
            mCellSpacing = dpToPx(intercellSpacing, metrics);
            mLeftEdgePadding = dpToPx(leftEdgePadding, metrics);
        }

        int mCellWidth, mCellHeight;
        int mCellSpacing;
        int mLeftEdgePadding;
    }

    private LayoutSpec mCellSizeChoices;

    private void initCellSize() {
        Activity a = (Activity) getContext();
        DisplayMetrics metrics = new DisplayMetrics();
        a.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        //@Fede always 3 columns in portrait mode.
        mSpec = new LayoutSpec((int) ((metrics.widthPixels / 3) / metrics.density) - 3,
                (int) ((metrics.widthPixels / 3) / metrics.density) - 3,
                3, 0, metrics);
    }


    // Converts dp to pixel.
    private static int dpToPx(int dp, DisplayMetrics metrics) {
        return (int) (metrics.density * dp);
    }

    // These are set in init().
    private final Handler mHandler = new Handler();
    private ImageBlockManager mImageBlockManager;

    // These are set in set*() functions.
    private ImageLoader mLoader;
    private Listener mListener = null;
    private DrawAdapter mDrawAdapter = null;
    private IImageList mAllImages = ImageManager.makeEmptyImageList(this.getContext());
    private int mSizeChoice = 1;  // default is big cell size

    // These are set in onLayout().
    private LayoutSpec mSpec;
    private int mColumns;
    private int mMaxScrollY;

    // We can handle events only if onLayout() is completed.
    private boolean mLayoutComplete = false;

    // Selection state
    private int mCurrentSelection = INDEX_NONE;
    private int mCurrentPressState = 0;
    private static final int TAPPING_FLAG = 1;
    private static final int CLICKING_FLAG = 2;

    // These are cached derived information.
    private int mCount;  // Cache mImageList.getCount();
    private int mRows;  // Cache (mCount + mColumns - 1) / mColumns
    private int mBlockHeight; // Cache mSpec.mCellSpacing + mSpec.mCellHeight

    private boolean mRunning = false;
    private Scroller mScroller = null;

    public GridViewSpecial(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context context) {
        setVerticalScrollBarEnabled(true);
        mGestureDetector = new GestureDetector(context, new MyGestureDetector());
        setFocusableInTouchMode(true);
        initCellSize();
    }

    private final Runnable mRedrawCallback = new Runnable() {
        public void run() {
            invalidate();
        }
    };

    public void setLoader(ImageLoader loader) {
        Assert(!mRunning);
        mLoader = loader;
    }

    public void setListener(Listener listener) {
        Assert(!mRunning);
        mListener = listener;
    }

    public void setDrawAdapter(DrawAdapter adapter) {
        Assert(!mRunning);
        mDrawAdapter = adapter;
    }

    public void setImageList(IImageList list) {
        Assert(!mRunning);
        mAllImages = list;
        mCount = mAllImages.getCount();
    }

    public void setSizeChoice(int choice) {
        Assert(!mRunning);
        if (mSizeChoice == choice) return;
        mSizeChoice = choice;
    }

    @Override
    public void onLayout(boolean changed, int left, int top,
                         int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (!mRunning) {
            return;
        }

        int width = right - left;

        // The width is divided into following parts:
        //
        // LeftEdgePadding CellWidth (CellSpacing CellWidth)* RightEdgePadding
        //
        // We determine number of cells (columns) first, then the left and right
        // padding are derived. We make left and right paddings the same size.
        //
        // The height is divided into following parts:
        //
        // CellSpacing (CellHeight CellSpacing)+

        mColumns = 1 + (width - mSpec.mCellWidth)
                / (mSpec.mCellWidth + mSpec.mCellSpacing);

        mSpec.mLeftEdgePadding = (width
                - ((mColumns - 1) * mSpec.mCellSpacing)
                - (mColumns * mSpec.mCellWidth)) / 2;

        mRows = (mCount + mColumns - 1) / mColumns;
        mBlockHeight = mSpec.mCellSpacing + mSpec.mCellHeight;
        mMaxScrollY = mSpec.mCellSpacing + (mRows * mBlockHeight)
                - (bottom - top);

        // Put mScrollY in the valid range. This matters if mMaxScrollY is
        // changed. For example, orientation changed from portrait to landscape.
        mScrollY = getScrollY();
        mScrollY = Math.max(0, Math.min(mMaxScrollY, mScrollY));

        //generateOutlineBitmap();

        if (mImageBlockManager != null) {
            mImageBlockManager.recycle();
        }

        mImageBlockManager = new ImageBlockManager(mHandler, mRedrawCallback,
                mAllImages, mLoader, mDrawAdapter, mSpec, mColumns, width, getContext());
        mListener.onLayoutComplete(changed);
        moveDataWindow();
        mLayoutComplete = true;
    }

    @Override
    protected int computeVerticalScrollRange() {
        return mMaxScrollY + getHeight();
    }

    private void moveDataWindow() {
        // Calculate visible region according to scroll position.
        int startRow = (mScrollY - mSpec.mCellSpacing) / mBlockHeight;
        int endRow = (mScrollY + getHeight() - mSpec.mCellSpacing - 1)
                / mBlockHeight + 1;

        // Limit startRow and endRow to the valid range.
        // Make sure we handle the mRows == 0 case right.
        startRow = Math.max(Math.min(startRow, mRows - 1), 0);
        endRow = Math.max(Math.min(endRow, mRows), 0);
        mImageBlockManager.setVisibleRows(startRow, endRow);
    }

    // In MyGestureDetector we have to check canHandleEvent() because
// GestureDetector could queue events and fire them later. At that time
// stop() may have already been called and we can't handle the events.
    private class MyGestureDetector extends SimpleOnGestureListener {
        private AudioManager mAudioManager;

        @Override
        public boolean onDown(MotionEvent e) {
            if (!canHandleEvent()) return false;
            if (mScroller != null && !mScroller.isFinished()) {
                mScroller.forceFinished(true);
                return false;
            }
            int index = computeSelectedIndex(e.getX(), e.getY());
            if (index >= 0 && index < mCount) {
                setSelectedIndex(index);
            } else {
                setSelectedIndex(INDEX_NONE);
            }
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2,
                               float velocityX, float velocityY) {
            if (!canHandleEvent()) return false;
            if (velocityY > MAX_FLING_VELOCITY) {
                velocityY = MAX_FLING_VELOCITY;
            } else if (velocityY < -MAX_FLING_VELOCITY) {
                velocityY = -MAX_FLING_VELOCITY;
            }

            setSelectedIndex(INDEX_NONE);
            mScroller = new Scroller(getContext());
            mScroller.fling(0, mScrollY, 0, -(int) velocityY, 0, 0, 0,
                    mMaxScrollY);
            computeScroll();

            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (!canHandleEvent()) return;
            performLongClick();
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {
            if (!canHandleEvent()) return false;
            setSelectedIndex(INDEX_NONE);
            scrollBy(0, (int) distanceY);
            invalidate();
            return true;
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            if (!canHandleEvent()) return false;
            int index = computeSelectedIndex(e.getX(), e.getY());
            if (index >= 0 && index < mCount) {
                // Play click sound.
                if (mAudioManager == null) {
                    mAudioManager = (AudioManager) getContext()
                            .getSystemService(Context.AUDIO_SERVICE);
                }
                mAudioManager.playSoundEffect(AudioManager.FX_KEY_CLICK);

                if(clickedOnSelectionCheck(e.getX(), e.getY())){
                    //Select checked of the image
                    mListener.onImageSelected(index);
                }else{
                    //Go to action on click image
                    mListener.onClickImageAction(index);
                }
                return true;
            }
            return false;
        }

    }

    public int getCurrentSelection() {
        return mCurrentSelection;
    }

    public void invalidateImage(int index) {
        if (index != INDEX_NONE) {
            mImageBlockManager.invalidateImage(index);
        }
    }

    /**
     * @param index <code>INDEX_NONE</code> (-1) means remove selection.
     */
    public void setSelectedIndex(int index) {
        // A selection box will be shown for the image that being selected,
        // (by finger or by the dpad center key). The selection box can be drawn
        // in two colors. One color (yellow) is used when the the image is
        // still being tapped or clicked (the finger is still on the touch
        // screen or the dpad center key is not released). Another color
        // (orange) is used after the finger leaves touch screen or the dpad
        // center key is released.

        if (mCurrentSelection == index) {
            return;
        }
        // This happens when the last picture is deleted.
        mCurrentSelection = Math.min(index, mCount - 1);

        if (mCurrentSelection != INDEX_NONE) {
            ensureVisible(mCurrentSelection);
        }
        invalidate();
    }

    public void scrollToImage(int index) {
        Rect r = getRectForPosition(index);
        scrollTo(0, r.top);
    }

    public void scrollToVisible(int index) {
        Rect r = getRectForPosition(index);
        int top = getScrollY();
        int bottom = getScrollY() + getHeight();
        if (r.bottom > bottom) {
            scrollTo(0, r.bottom - getHeight());
        } else if (r.top < top) {
            scrollTo(0, r.top);
        }
    }

    private void ensureVisible(int pos) {
        Rect r = getRectForPosition(pos);
        int top = getScrollY();
        int bot = top + getHeight();

        if (r.bottom > bot) {
            mScroller = new Scroller(getContext());
            mScroller.startScroll(mScrollX, mScrollY, 0,
                    r.bottom - getHeight() - mScrollY, 200);
            computeScroll();
        } else if (r.top < top) {
            mScroller = new Scroller(getContext());
            mScroller.startScroll(mScrollX, mScrollY, 0, r.top - mScrollY, 200);
            computeScroll();
        }
    }

    public void start() {
        // These must be set before start().
        Assert(mLoader != null);
        Assert(mListener != null);
        Assert(mDrawAdapter != null);
        mRunning = true;
        requestLayout();
    }

    // If the the underlying data is changed, for example,
    // an image is deleted, or the size choice is changed,
    // The following sequence is needed:
    //
    // mGvs.stop();
    // mGvs.set...(...);
    // mGvs.set...(...);
    // mGvs.start();
    public void stop() {
        // Remove the long press callback from the queue if we are going to
        // stop.
        mHandler.removeCallbacks(mLongPressCallback);
        mScroller = null;
        if (mImageBlockManager != null) {
            mImageBlockManager.recycle();
            mImageBlockManager = null;
        }
        mRunning = false;
        mCurrentSelection = INDEX_NONE;
    }

    @Override
    public void onDraw(Canvas canvas) {
        canvas.drawRGB(255, 255, 255);
        super.onDraw(canvas);
        if (!canHandleEvent())
            return;

        mImageBlockManager.doDraw(canvas, getWidth(), getHeight(), mScrollY);
        paintDecoration(canvas);
        moveDataWindow();
    }

    @Override
    public void computeScroll() {
        if (mScroller != null) {
            boolean more = mScroller.computeScrollOffset();
            scrollTo(0, mScroller.getCurrY());
            if (more) {
                invalidate();  // So we draw again
            } else {
                mScroller = null;
            }
        } else {
            super.computeScroll();
        }
    }

    // Return the rectange for the thumbnail in the given position.
    Rect getRectForPosition(int pos) {
        int row = pos / mColumns;
        int col = pos - (row * mColumns);

        int left = mSpec.mLeftEdgePadding
                + (col * (mSpec.mCellWidth + mSpec.mCellSpacing));
        int top = row * mBlockHeight;

        return new Rect(left, top,
                left + mSpec.mCellWidth + mSpec.mCellSpacing,
                top + mSpec.mCellHeight + mSpec.mCellSpacing);
    }

    // Inverse of getRectForPosition: from screen coordinate to image position.
    int computeSelectedIndex(float xFloat, float yFloat) {
        int x = (int) xFloat;
        int y = (int) yFloat;

        int spacing = mSpec.mCellSpacing;
        int leftSpacing = mSpec.mLeftEdgePadding;

        int row = (mScrollY + y - spacing) / (mSpec.mCellHeight + spacing);
        int col = Math.min(mColumns - 1,
                (x - leftSpacing) / (mSpec.mCellWidth + spacing));
        return (row * mColumns) + col;
    }

    private boolean clickedOnSelectionCheck(float xFloat, float yFloat) {

        int x = (int) xFloat;
        int y = (int) yFloat;

        int spacing = mSpec.mCellSpacing;
        int leftSpacing = mSpec.mLeftEdgePadding;

        int row = (mScrollY + y - spacing) / (mSpec.mCellHeight + spacing);
        int col = Math.min(mColumns - 1,
                (x - leftSpacing) / (mSpec.mCellWidth + spacing));

        int posYOnCell = (mScrollY + y) - (row * (mSpec.mCellHeight + spacing));
        int minCellY = mSpec.mCellHeight - mSpec.mCellHeight / 3;
        int maxCellY = mSpec.mCellHeight;

        int posXOnCell = x - (col * (mSpec.mCellWidth + spacing));
        int minCellX = mSpec.mCellWidth - mSpec.mCellWidth / 3;
        int maxCellX = mSpec.mCellWidth;

        return posYOnCell > minCellY && posYOnCell < maxCellY &&
                posXOnCell > minCellX && posXOnCell < maxCellX;

    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (!canHandleEvent()) {
            return false;
        }
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN:
                mCurrentPressState |= TAPPING_FLAG;
                invalidate();
                break;
            case MotionEvent.ACTION_UP:
                mCurrentPressState &= ~TAPPING_FLAG;
                invalidate();
                break;
        }
        mGestureDetector.onTouchEvent(ev);
        // Consume all events
        return true;
    }

    @Override
    public void scrollBy(int x, int y) {
        scrollTo(mScrollX + x, mScrollY + y);
    }

    public void scrollTo(float scrollPosition) {
        scrollTo(0, Math.round(scrollPosition * mMaxScrollY));
    }

    @Override
    public void scrollTo(int x, int y) {
        y = Math.max(0, Math.min(mMaxScrollY, y));
        if (mSpec != null) {
            mListener.onScroll((float) mScrollY / mMaxScrollY);
        }
        mScrollX = x;
        mScrollY = y;
        super.scrollTo(x, y);
    }

    private boolean canHandleEvent() {
        return mRunning && mLayoutComplete;
    }

    private final Runnable mLongPressCallback = new Runnable() {
        public void run() {
            mCurrentPressState &= ~CLICKING_FLAG;
            //showContextMenu();
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!canHandleEvent()) return false;
        int sel = mCurrentSelection;
        if (sel != INDEX_NONE) {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (sel != mCount - 1 && (sel % mColumns < mColumns - 1)) {
                        sel += 1;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (sel > 0 && (sel % mColumns != 0)) {
                        sel -= 1;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (sel >= mColumns) {
                        sel -= mColumns;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    sel = Math.min(mCount - 1, sel + mColumns);
                    break;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    if (event.getRepeatCount() == 0) {
                        mCurrentPressState |= CLICKING_FLAG;
                        mHandler.postDelayed(mLongPressCallback,
                                ViewConfiguration.getLongPressTimeout());
                    }
                    break;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        } else {
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    int startRow =
                            (mScrollY - mSpec.mCellSpacing) / mBlockHeight;
                    int topPos = startRow * mColumns;
                    Rect r = getRectForPosition(topPos);
                    if (r.top < getScrollY()) {
                        topPos += mColumns;
                    }
                    topPos = Math.min(mCount - 1, topPos);
                    sel = topPos;
                    break;
                default:
                    return super.onKeyDown(keyCode, event);
            }
        }
        setSelectedIndex(sel);
        return true;
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (!canHandleEvent()) return false;

        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            mCurrentPressState &= ~CLICKING_FLAG;
            invalidate();

            // The keyUp doesn't get called when the longpress menu comes up. We
            // only get here when the user lets go of the center key before the
            // longpress menu comes up.
            mHandler.removeCallbacks(mLongPressCallback);

            // open the photo
            mListener.onImageClicked(mCurrentSelection);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void paintDecoration(Canvas canvas) {

        int xPos = mSpec.mLeftEdgePadding;

        if (!mDrawAdapter.needsDecoration())
            return;

        // Calculate visible region according to scroll position.
        int startRow = (mScrollY - mSpec.mCellSpacing) / mBlockHeight;
        int endRow = (mScrollY + getHeight() - mSpec.mCellSpacing - 1)
                / mBlockHeight + 1;

        // Limit startRow and endRow to the valid range.
        // Make sure we handle the mRows == 0 case right.
        startRow = Math.max(Math.min(startRow, mRows - 1), 0);
        endRow = Math.max(Math.min(endRow, mRows), 0);

        int startIndex = startRow * mColumns;
        int endIndex = Math.min(endRow * mColumns, mCount);


        int yPos = mSpec.mCellSpacing + startRow * mBlockHeight;
        int nCol = 0;
        for (int nBlock = startIndex; nBlock < endIndex; nBlock++) {

            //@Fede not draw decoration in icons (Decorations are select states)
            if (isIconBlock(nCol, nBlock)) {
                nCol += 1;
                xPos += mSpec.mCellWidth + mSpec.mCellSpacing;
                continue;
            }

            IImage image = mAllImages.getImageAt(nBlock);

            mDrawAdapter.drawDecoration(canvas, image, xPos, yPos,
                    mSpec.mCellWidth, mSpec.mCellHeight);

            // Calculate next position
            nCol += 1;
            if (nCol == mColumns) {
                xPos = mSpec.mLeftEdgePadding;
                yPos += mBlockHeight;
                nCol = 0;
            } else {
                xPos += mSpec.mCellWidth + mSpec.mCellSpacing;
            }
        }
    }

    //@Fede Return true if this block is using for showing an icon and not a gallery resource.
    private boolean isIconBlock(int nCol, int nBlock) {
        return (nCol == 0 && nBlock == 0) || (nBlock == 1 && nCol == 1);
    }

}


