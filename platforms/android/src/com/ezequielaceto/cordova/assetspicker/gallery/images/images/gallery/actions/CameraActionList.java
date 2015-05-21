package com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.actions;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.CharArrayBuffer;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;

import com.ezequielaceto.cordova.assetspicker.test.R;

import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.BaseImage;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.BaseImageList;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.IImage;
import com.ezequielaceto.cordova.assetspicker.gallery.images.images.gallery.IImageList;

import java.util.HashMap;

/**
 * Created by kimi on 26/07/14.
 */
public class CameraActionList extends BaseImageList implements IImageList {

    @SuppressWarnings("unused")
    private static final String TAG = "CameraActionList";

    private Context context;

    public HashMap<String, String> getBucketIds() {
        Uri uri = mBaseUri.buildUpon()
                .appendQueryParameter("distinct", "true").build();
        Cursor cursor = getFakeCursor();
        try {
            HashMap<String, String> hash = new HashMap<String, String>();
            while (cursor.moveToNext()) {
                hash.put(cursor.getString(1), cursor.getString(0));
            }
            return hash;
        } finally {
            cursor.close();
        }
    }

    /**
     * ImageList constructor.
     */
    public CameraActionList(Context context, ContentResolver resolver, Uri imageUri,
                            int sort, String bucketId) {
        super(resolver, imageUri, sort, bucketId);
        this.context = context;
    }

    @Override
    protected Cursor createCursor() {
        Cursor c = getFakeCursor();
        return c;
    }

    static final String[] IMAGE_PROJECTION = new String[]{
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DATA,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.MINI_THUMB_MAGIC,
            MediaStore.Images.Media.ORIENTATION,
            MediaStore.Images.Media.TITLE,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.DATE_MODIFIED};

    private static final int INDEX_ID = 0;
    private static final int INDEX_DATA_PATH = 1;
    private static final int INDEX_DATE_TAKEN = 2;
    private static final int INDEX_MINI_THUMB_MAGIC = 3;
    private static final int INDEX_ORIENTATION = 4;
    private static final int INDEX_TITLE = 5;
    private static final int INDEX_MIME_TYPE = 6;
    private static final int INDEX_DATE_MODIFIED = 7;

    @Override
    protected long getImageId(Cursor cursor) {
        return cursor.getLong(INDEX_ID);
    }

    @Override
    protected BaseImage loadImageFromCursor(Cursor cursor) {

        long id = cursor.getLong(INDEX_ID);

        int orientation = 0;

        long dateTaken = System.currentTimeMillis();

        dateTaken += 31536000000l; // one year
        if (id == 0) dateTaken += 5000;

        FakeIImage image = new FakeIImage(this, mContentResolver, id, cursor.getPosition(),
                null, "", "", dateTaken, "",
                orientation);

        return image;
    }

    private FakeCursor getFakeCursor() {
        return new FakeCursor();
    }

    public class FakeIImage extends BaseImage implements IImage {
        private static final String TAG = "BaseImage";

        private ExifInterface mExif;

        private int mRotation;

        public FakeIImage(BaseImageList container, ContentResolver cr,
                          long id, int index, Uri uri, String dataPath,
                          String mimeType, long dateTaken, String title,
                          int rotation) {
            super(id, index, dateTaken);
            //super(container, cr, id, index, uri, dataPath,mimeType, dateTaken, title);
            mRotation = rotation;
        }

        @Override
        public String toString() {
            return "action ID: " + mId;
        }

        @Override
        public int getDegreesRotated() {
            return mRotation;
        }

        protected void setDegreesRotated(int degrees) {
            if (mRotation == degrees) return;
            mRotation = degrees;
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.ImageColumns.ORIENTATION, mRotation);
            mContentResolver.update(mUri, values, null, null);

            //TODO: Consider invalidate the cursor in container
            // ((BaseImageList) getContainer()).invalidateCursor();
        }

        public Uri contentUri(long id) {
            // TODO: avoid using exception for most cases
            try {
                // does our uri already have an id (single image query)?
                // if so just return it
                long existingId = ContentUris.parseId(mBaseUri);
                if (existingId != id) Log.e(TAG, "id mismatch");
                return mBaseUri;
            } catch (NumberFormatException ex) {
                // otherwise tack on the id
                return ContentUris.withAppendedId(mBaseUri, id);
            }
        }

        public boolean isReadonly() {
            String mimeType = getMimeType();
            return !"image/jpeg".equals(mimeType) && !"image/png".equals(mimeType);
        }

        @Override
        public String getMimeType() {
            return "image/png";
        }

        public boolean isDrm() {
            return false;
        }


        public Bitmap thumbBitmap(boolean rotateAsNeeded) {
            Drawable myDrawable;

            if (mId == 0) {
                myDrawable = context.getResources().getDrawable(R.drawable.photo_camera_icon);
            } else if (mId == 1) {
                myDrawable = context.getResources().getDrawable(R.drawable.video_camera_icon);
            } else {
                myDrawable = context.getResources().getDrawable(R.drawable.album_icon);
            }

            return ((BitmapDrawable) myDrawable).getBitmap();
        }

        @Override
        public Bitmap miniThumbBitmap() {
            Drawable myDrawable = null;

            if (mId == 0) {
                myDrawable = context.getResources().getDrawable(R.drawable.photo_camera_icon);
            } else if (mId == 1) {
                myDrawable = context.getResources().getDrawable(R.drawable.video_camera_icon);
            } else {
                myDrawable = context.getResources().getDrawable(R.drawable.album_icon);
            }

            Bitmap bitmap = ((BitmapDrawable) myDrawable).getBitmap(); // y porque no un RecycleBitmapDrawable?

            Bitmap newBitmap = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(newBitmap);

            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawPaint(paint);

            paint.setAntiAlias(true);
            paint.setFilterBitmap(true);
            paint.setDither(true);

            canvas.drawBitmap(newBitmap, 0, 0, paint);

            return newBitmap;
        }

        @Override
        public boolean rotateImageBy(int degrees) {
            return true;
        }
    }

    private class FakeCursor implements Cursor {

        int mCursorPosition;

        public FakeCursor() {
            mCursorPosition = 0;
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public int getPosition() {
            return 0;
        }

        @Override
        public boolean move(int i) {
            if (i < getCount()) {
                mCursorPosition = i;
                return true;
            }
            return false;
        }

        @Override
        public boolean moveToPosition(int i) {
            if (i < getCount()) {
                mCursorPosition = i;
                return true;
            }
            return false;
        }

        @Override
        public boolean moveToFirst() {
            mCursorPosition = 0;
            return true;
        }

        @Override
        public boolean moveToLast() {
            mCursorPosition = getCount() - 1;
            return true;
        }

        @Override
        public boolean moveToNext() {
            mCursorPosition++;
            if (mCursorPosition < getCount()) return true;
            mCursorPosition = getCount() - 1;
            return false;
        }

        @Override
        public boolean moveToPrevious() {
            mCursorPosition--;
            if (mCursorPosition < 0) return true;
            mCursorPosition = 0;
            return false;
        }

        @Override
        public boolean isFirst() {
            return mCursorPosition == 0;
        }

        @Override
        public boolean isLast() {
            return mCursorPosition == getCount() - 1;
        }

        @Override
        public boolean isBeforeFirst() {
            return mCursorPosition < 0;
        }

        @Override
        public boolean isAfterLast() {
            return mCursorPosition >= getCount();
        }

        @Override
        public int getColumnIndex(String s) {
            return 0;
        }

        @Override
        public int getColumnIndexOrThrow(String s) throws IllegalArgumentException {
            return 0;
        }

        @Override
        public String getColumnName(int i) {
            return null;
        }

        @Override
        public String[] getColumnNames() {
            return new String[0];
        }

        @Override
        public int getColumnCount() {
            return 0;
        }

        @Override
        public byte[] getBlob(int i) {
            return new byte[0];
        }

        @Override
        public String getString(int i) {
            return null;
        }

        @Override
        public void copyStringToBuffer(int i, CharArrayBuffer charArrayBuffer) {

        }

        @Override
        public short getShort(int i) {
            return 0;
        }

        @Override
        public int getInt(int i) {
            return 0;
        }

        @Override
        public long getLong(int i) {
            if (i == INDEX_ID) return mCursorPosition;
            return i;
        }

        @Override
        public float getFloat(int i) {
            return 0;
        }

        @Override
        public double getDouble(int i) {
            return 0;
        }

        @Override
        public int getType(int i) {
            return 0;
        }

        @Override
        public boolean isNull(int i) {
            return false;
        }

        @Override
        public void deactivate() {

        }

        @Override
        public boolean requery() {
            return false;
        }

        @Override
        public void close() {
            mCursorPosition = 0;
        }

        @Override
        public boolean isClosed() {
            return true;
        }

        @Override
        public void registerContentObserver(ContentObserver contentObserver) {

        }

        @Override
        public void unregisterContentObserver(ContentObserver contentObserver) {

        }

        @Override
        public void registerDataSetObserver(DataSetObserver dataSetObserver) {

        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver dataSetObserver) {

        }

        @Override
        public void setNotificationUri(ContentResolver contentResolver, Uri uri) {

        }

        @Override
        public Uri getNotificationUri() {
            return null;
        }

        @Override
        public boolean getWantsAllOnMoveCalls() {
            return false;
        }

        @Override
        public Bundle getExtras() {
            return null;
        }

        @Override
        public Bundle respond(Bundle bundle) {
            return null;
        }
    }

}
