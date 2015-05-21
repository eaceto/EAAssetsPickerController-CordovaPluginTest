package com.ezequielaceto.cordova.assetspicker.gallery.local;

import android.content.Context;
import android.graphics.*;
import android.os.Handler;

import com.ezequielaceto.cordova.assetspicker.gallery.ImageUtils;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.IImageList;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.ImageLoader;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.IImage;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.actions.CameraActionList;


import java.util.HashMap;

/**
 * Created by Fede on 7/24/14.
 */
class ImageBlockManager {
    @SuppressWarnings("unused")
    private static final String TAG = "ImageBlockManager";

    // Number of rows we want to cache.
    // Assume there are 6 rows per page, this caches 5 pages.
    private static final int CACHE_ROWS = 30;

    // mCache maps from row number to the ImageBlock.
    private final HashMap<Integer, ImageBlock> mCache;

    // These are parameters set in the constructor.
    private final Handler mHandler;
    private final Runnable mRedrawCallback;  // Called after a row is loaded,
    // so GridViewSpecial can draw
    // again using the new images.
    private final IImageList mImageList;
    private final ImageLoader mLoader;
    private final GridViewSpecial.DrawAdapter mDrawAdapter;
    private final GridViewSpecial.LayoutSpec mSpec;
    private final int mColumns;  // Columns per row.
    private final int mBlockWidth;  // The width of an ImageBlock.
    private final int mCount;  // Cache mImageList.getCount().
    private final int mRows;  // Cache (mCount + mColumns - 1) / mColumns
    private final int mBlockHeight;  // The height of an ImageBlock.
    private final Context mContext;

    // Visible row range: [mStartRow, mEndRow). Set by setVisibleRows().
    private int mStartRow = 0;
    private int mEndRow = 0;

    ImageBlockManager(Handler handler, Runnable redrawCallback,
                      IImageList imageList, ImageLoader loader,
                      GridViewSpecial.DrawAdapter adapter,
                      GridViewSpecial.LayoutSpec spec,
                      int columns, int blockWidth, Context context) {
        mHandler = handler;
        mRedrawCallback = redrawCallback;
        mImageList = imageList;
        mLoader = loader;
        mDrawAdapter = adapter;
        mSpec = spec;
        mColumns = columns;
        mBlockWidth = blockWidth;
        mBlockHeight = mSpec.mCellSpacing + mSpec.mCellHeight;
        mCount = imageList.getCount();
        mRows = (mCount + mColumns - 1) / mColumns;
        mCache = new HashMap<Integer, ImageBlock>();
        mPendingRequest = 0;
        mContext = context;
        initGraphics();
    }

    // Set the window of visible rows. Once set we will start to load them as
    // soon as possible (if they are not already in cache).
    public void setVisibleRows(int startRow, int endRow) {
        if (startRow != mStartRow || endRow != mEndRow) {
            mStartRow = startRow;
            mEndRow = endRow;
            startLoading();
        }
    }

    int mPendingRequest;  // Number of pending requests (sent to ImageLoader).
    // We want to keep enough requests in ImageLoader's queue, but not too
    // many.
    static final int REQUESTS_LOW = 3;
    static final int REQUESTS_HIGH = 6;

    // After clear requests currently in queue, start loading the thumbnails.
    // We need to clear the queue first because the proper order of loading
    // may have changed (because the visible region changed, or some images
    // have been invalidated).
    private void startLoading() {
        clearLoaderQueue();
        continueLoading();
    }

    private void clearLoaderQueue() {
        int[] tags = mLoader.clearQueue();
        for (int pos : tags) {
            int row = pos / mColumns;
            int col = pos - row * mColumns;
            ImageBlock blk = mCache.get(row);
            GridViewSpecial.Assert(blk != null);  // We won't reuse the block if it has pending
            // requests. See getEmptyBlock().
            blk.cancelRequest(col);
        }
    }

    // Scan the cache and send requests to ImageLoader if needed.
    private void continueLoading() {
        // Check if we still have enough requests in the queue.
        if (mPendingRequest >= REQUESTS_LOW) return;

        // Scan the visible rows.
        for (int i = mStartRow; i < mEndRow; i++) {
            if (scanOne(i)) return;
        }

        int range = (CACHE_ROWS - (mEndRow - mStartRow)) / 2;
        // Scan other rows.
        // d is the distance between the row and visible region.
        for (int d = 1; d <= range; d++) {
            int after = mEndRow - 1 + d;
            int before = mStartRow - d;
            if (after >= mRows && before < 0) {
                break;  // Nothing more the scan.
            }
            if (after < mRows && scanOne(after)) return;
            if (before >= 0 && scanOne(before)) return;
        }
    }

    // Returns true if we can stop scanning.
    private boolean scanOne(int i) {
        mPendingRequest += tryToLoad(i);
        return mPendingRequest >= REQUESTS_HIGH;
    }

    // Returns number of requests we issued for this row.
    private int tryToLoad(int row) {
        GridViewSpecial.Assert(row >= 0 && row < mRows);
        ImageBlock blk = mCache.get(row);
        if (blk == null) {
            // Find an empty block
            blk = getEmptyBlock();
            blk.setRow(row);
            blk.invalidate();
            mCache.put(row, blk);
        }
        return blk.loadImages();
    }

    // Get an empty block for the cache.
    private ImageBlock getEmptyBlock() {
        // See if we can allocate a new block.
        if (mCache.size() < CACHE_ROWS) {
            return new ImageBlock(mContext);
        }
        // Reclaim the old block with largest distance from the visible region.
        int bestDistance = -1;
        int bestIndex = -1;
        for (int index : mCache.keySet()) {
            // Make sure we don't reclaim a block which still has pending
            // request.
            if (mCache.get(index).hasPendingRequests()) {
                continue;
            }
            int dist = 0;
            if (index >= mEndRow) {
                dist = index - mEndRow + 1;
            } else if (index < mStartRow) {
                dist = mStartRow - index;
            } else {
                // Inside the visible region.
                continue;
            }
            if (dist > bestDistance) {
                bestDistance = dist;
                bestIndex = index;
            }
        }
        return mCache.remove(bestIndex);
    }

    public void invalidateImage(int index) {
        int row = index / mColumns;
        int col = index - (row * mColumns);
        ImageBlock blk = mCache.get(row);
        if (blk == null) return;
        if ((blk.mCompletedMask & (1 << col)) != 0) {
            blk.mCompletedMask &= ~(1 << col);
        }
        startLoading();
    }

    // After calling recycle(), the instance should not be used anymore.
    public void recycle() {
        for (ImageBlock blk : mCache.values()) {
            blk.recycle();
        }
        mCache.clear();
        mEmptyBitmap.recycle();
    }

    // Draw the images to the given canvas.
    public void doDraw(Canvas canvas, int thisWidth, int thisHeight,
                       int scrollPos) {
        final int height = mBlockHeight;


        // Note that currentBlock could be negative.
        int currentBlock = (scrollPos < 0)
                ? ((scrollPos - height + 1) / height)
                : (scrollPos / height);

        while (true) {
            final int yPos = currentBlock * height;
            if (yPos >= scrollPos + thisHeight) {
                break;
            }

            ImageBlock blk = mCache.get(currentBlock);

            if (blk != null) {
                //@eaceto first row is like any other row
                blk.doDraw(canvas, 0, yPos);

                /*
                if (blk.mRow != 0) {
                    blk.doDraw(canvas, 0, yPos);
                }else {
                    //@Fede draw epty blocks for first row too anyway.
                    drawEmptyBlock(canvas, 0, yPos, currentBlock);
                }
                */
            } else {
                drawEmptyBlock(canvas, 0, yPos, currentBlock);
            }

            currentBlock += 1;
        }
    }

    // Return number of columns in the given row. (This could be less than
    // mColumns for the last row).
    private int numColumns(int row) {
        return Math.min(mColumns, mCount - row * mColumns);
    }

    // Draw a block which has not been loaded.
    private void drawEmptyBlock(Canvas canvas, int xPos, int yPos, int row) {
        // Draw the background.
        canvas.drawRect(xPos, yPos, xPos + mBlockWidth, yPos + mBlockHeight,
                mBackgroundPaint);

        // Draw the empty images.
        int x = xPos + mSpec.mLeftEdgePadding;
        int y = yPos + mSpec.mCellSpacing;
        int cols = numColumns(row);


        Bitmap iconBitmap = null;
        for (int i = 0; i < cols; i++) {
            //FIX ME: Here i overlap and old image with my resource.
            //Add video icon at index 1
            //@Fede
            if (row == 0 && i == 0)
                iconBitmap = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.camera_icon);

            canvas.drawBitmap(iconBitmap == null ? mEmptyBitmap : iconBitmap, x, y, null);
            x += (mSpec.mCellWidth + mSpec.mCellSpacing);
        }
    }

    // mEmptyBitmap is what we draw if we the wanted block hasn't been loaded.
    // (If the user scrolls too fast). It is a gray image with normal outline.
    // mBackgroundPaint is used to draw the (black) background outside
    // mEmptyBitmap.
    Paint mBackgroundPaint;
    private Bitmap mEmptyBitmap;

    private void initGraphics() {
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setStyle(Paint.Style.FILL);
        //mBackgroundPaint.setColor(0xFF000000);  // black
        mBackgroundPaint.setColor(mContext.getResources().getColor(0xFFFFFFFF));

        mEmptyBitmap = Bitmap.createBitmap(mSpec.mCellWidth, mSpec.mCellHeight,
                Bitmap.Config.RGB_565);

        Canvas canvas = new Canvas(mEmptyBitmap);
        canvas.drawRGB(192, 192, 192);
        //canvas.drawBitmap(mOutline, 0, 0, null);
    }

    // ImageBlock stores bitmap for one row. The loaded thumbnail images are
    // drawn to mBitmap. mBitmap is later used in onDraw() of GridViewSpecial.
    private class ImageBlock {
        private Bitmap mBitmap;
        private final Canvas mCanvas;
        private final Context context;

        // Columns which have been requested to the loader
        private int mRequestedMask;

        // Columns which have been completed from the loader
        private int mCompletedMask;

        // The row number this block represents.
        private int mRow;

        public ImageBlock(Context aContext) {
            mBitmap = Bitmap.createBitmap(mBlockWidth, mBlockHeight,
                    Bitmap.Config.RGB_565);
            mCanvas = new Canvas(mBitmap);
            mCanvas.drawRGB(255, 255, 255);
            mRow = -1;
            context = aContext;
        }

        public void setRow(int row) {
            mRow = row;
        }

        public void invalidate() {
            // We do not change mRequestedMask or do cancelAllRequests()
            // because the data coming from pending requests are valid. (We only
            // invalidate data which has been drawn to the bitmap).
            mCompletedMask = 0;
        }

        // After recycle, the ImageBlock instance should not be accessed.
        public void recycle() {
            cancelAllRequests();
            mBitmap.recycle();
            mBitmap = null;
        }

        private boolean isVisible() {
            return mRow >= mStartRow && mRow < mEndRow;
        }

        // Returns number of requests submitted to ImageLoader.
        public int loadImages() {
            GridViewSpecial.Assert(mRow != -1);

            int columns = numColumns(mRow);

            // Calculate what we need.
            int needMask = ((1 << columns) - 1)
                    & ~(mCompletedMask | mRequestedMask);

            if (needMask == 0) {
                return 0;
            }

            int retVal = 0;
            int base = mRow * mColumns;

            for (int col = 0; col < columns; col++) {
                if ((needMask & (1 << col)) == 0) {
                    continue;
                }

                int pos = base + col;

                final IImage image = mImageList.getImageAt(pos);
                if (image != null) {
                    // This callback is passed to ImageLoader. It will invoke
                    // loadImageDone() in the main thread. We limit the callback
                    // thread to be in this very short function. All other
                    // processing is done in the main thread.
                    final int colFinal = col;
                    ImageLoader.LoadedCallback cb =
                            new ImageLoader.LoadedCallback() {
                                public void run(final Bitmap b) {
                                    mHandler.post(new Runnable() {
                                        public void run() {
                                            loadImageDone(image, b,
                                                    colFinal);
                                        }
                                    });
                                }
                            };
                    // Load Image
                    mLoader.getBitmap(image, cb, pos);
                    mRequestedMask |= (1 << col);
                    retVal += 1;
                }
            }

            return retVal;
        }

        // Whether this block has pending requests.
        public boolean hasPendingRequests() {
            return mRequestedMask != 0;
        }

        // Called when an image is loaded.
        private void loadImageDone(IImage image, Bitmap b,
                                   int col) {

            if (mBitmap == null)
                return;  // This block has been recycled.

            int spacing = mSpec.mCellSpacing;
            int leftSpacing = mSpec.mLeftEdgePadding;

            int _x = leftSpacing
                    + (col * (mSpec.mCellWidth + spacing));
            int _y = spacing;
            int _w = mSpec.mCellWidth, _h = mSpec.mCellHeight;


            mDrawAdapter.drawFilledRectangle(mCanvas, _x - leftSpacing, _y - spacing, _w + spacing, _h + spacing, Color.WHITE);

            // @eaceto for camera action buttons
            if (image.getClass().equals(CameraActionList.FakeIImage.class)) {

                Bitmap newBitmap = ImageUtils.thumbBitmap(mContext, col);

                if (_w > b.getWidth()) {
                    // centrar horizontalmente
                    _x += (_w - b.getWidth()) / 2;
                    _w = b.getWidth();
                }

                if (_h > b.getHeight()) {
                    // centrar verticalmente
                    _y = (_h - b.getHeight()) / 2;
                    _h = b.getHeight();
                }

                drawBitmap(image, newBitmap, _x, _y, _w, _h); // xPos, yPos

                if (newBitmap != null)
                    newBitmap.recycle();

            } else {

                Bitmap newBitmap = ImageUtils.scaleCenterCrop(b, _w, _h);

                drawBitmap(image, newBitmap, _x, _y, _w, _h); // xPos, yPos

                if (newBitmap != null)
                    newBitmap.recycle();
            }


            if (b != null)
                b.recycle();

            int mask = (1 << col);
            GridViewSpecial.Assert((mCompletedMask & mask) == 0);
            GridViewSpecial.Assert((mRequestedMask & mask) != 0);
            mRequestedMask &= ~mask;
            mCompletedMask |= mask;
            mPendingRequest--;

            if (isVisible()) {
                mRedrawCallback.run();
            }

            // Kick start next block loading.
            continueLoading();
        }

        // Draw the loaded bitmap to the block bitmap.
        private void drawBitmap(
                IImage image, Bitmap b, int xPos, int yPos, int width, int height) {

            // @eaceto Esto no dibuja centrado. Sino que dibuja estirando la celda
            mDrawAdapter.drawImage(mCanvas, image, b, xPos, yPos,
                    width, height);
        }

        // Draw the block bitmap to the specified canvas.
        public void doDraw(Canvas canvas, int xPos, int yPos) {
            int cols = numColumns(mRow);


            if (cols == mColumns) {
                canvas.drawBitmap(mBitmap, xPos, yPos, null);

            } else {

                // This must be the last row -- we draw only part of the block.
                // Draw the background.
                canvas.drawRect(xPos, yPos, xPos + mBlockWidth,
                        yPos + mBlockHeight, mBackgroundPaint);


                // Draw part of the block.
                int w = mSpec.mLeftEdgePadding
                        + cols * (mSpec.mCellWidth + mSpec.mCellSpacing);
                Rect srcRect = new Rect(0, 0, w, mBlockHeight);
                Rect dstRect = new Rect(srcRect);
                dstRect.offset(xPos, yPos);
                canvas.drawBitmap(mBitmap, srcRect, dstRect, null);
            }

            // Draw the part which has not been loaded.
            int isEmpty = ((1 << cols) - 1) & ~mCompletedMask;

            if (isEmpty != 0) {
                int x = xPos + mSpec.mLeftEdgePadding;
                int y = yPos + mSpec.mCellSpacing;

                for (int i = 0; i < cols; i++) {
                    if ((isEmpty & (1 << i)) != 0) {
                        canvas.drawBitmap(mEmptyBitmap, x, y, null);
                    }
                    x += (mSpec.mCellWidth + mSpec.mCellSpacing);
                }
            }
        }

        // Mark a request as cancelled. The request has already been removed
        // from the queue of ImageLoader, so we only need to mark the fact.
        public void cancelRequest(int col) {
            int mask = (1 << col);
            GridViewSpecial.Assert((mRequestedMask & mask) != 0);
            mRequestedMask &= ~mask;
            mPendingRequest--;
        }

        // Try to cancel all pending requests for this block. After this
        // completes there could still be requests not cancelled (because it is
        // already in progress). We deal with that situation by setting mBitmap
        // to null in recycle() and check this in loadImageDone().
        private void cancelAllRequests() {
            for (int i = 0; i < mColumns; i++) {
                int mask = (1 << i);
                if ((mRequestedMask & mask) != 0) {
                    int pos = (mRow * mColumns) + i;
                    if (mLoader.cancel(mImageList.getImageAt(pos))) {
                        mRequestedMask &= ~mask;
                        mPendingRequest--;
                    }
                }
            }
        }
    }
}
