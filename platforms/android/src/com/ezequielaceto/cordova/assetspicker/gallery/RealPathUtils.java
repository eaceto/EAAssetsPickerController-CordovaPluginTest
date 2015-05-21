package com.ezequielaceto.cordova.assetspicker.gallery;

        import android.annotation.SuppressLint;
        import android.content.Context;
        import android.database.Cursor;
        import android.net.Uri;
        import android.provider.DocumentsContract;
        import android.provider.MediaStore;
        import android.support.v4.content.CursorLoader;

/**
 * Created by Fede on 12/1/14.
 */
public class RealPathUtils {

    @SuppressLint("NewApi")
    public static MediaPojo getRealPathFromURI_API19(Context context, Uri uri) {

        MediaPojo mediaPojo = new MediaPojo();
        mediaPojo.setUri(uri);

        String path = "";
        String mimeType = "";
        Cursor cursor = null;

        try {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                String wholeID = DocumentsContract.getDocumentId(uri);

                // Split at colon, use second item in the array
                String id = wholeID.split(":")[1];


                // where id is equal to
                String sel = MediaStore.Images.Media._ID + "=?";

                cursor = context.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        null, sel, new String[]{id}, null);

                int columnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);

                if (cursor.moveToFirst()) {
                    mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.MIME_TYPE));
                    path = cursor.getString(columnIndex);
                }

            } else {
                CursorLoader loader = new CursorLoader(context, uri, null, null, null, null);
                cursor = loader.loadInBackground();
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    path = cursor.getString(column_index);
                    mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.MIME_TYPE));
                }

            }
        } finally {
            if (cursor != null)
                cursor.close();
        }

        mediaPojo.setPath(path);
        mediaPojo.setMimetype(mimeType);

        return mediaPojo;
    }


    @SuppressLint("NewApi")
    public static MediaPojo getRealPathFromURI_API11to18(Context context, Uri contentUri) {

        MediaPojo mediaPojo = new MediaPojo();
        mediaPojo.setUri(contentUri);

        String path = "";
        String mimeType = "";
        Cursor cursor = null;

        CursorLoader cursorLoader = new CursorLoader(
                context,
                contentUri, null, null, null, null);
        try {
            cursor = cursorLoader.loadInBackground();

            if (cursor.moveToFirst()) {
                path = cursor.getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));
                mimeType = cursor.getString(cursor.getColumnIndex(MediaStore.Images.ImageColumns.MIME_TYPE));
            }

        } finally {
            if (cursor != null)
                cursor.close();
        }

        mediaPojo.setMimetype(mimeType);
        mediaPojo.setPath(path);

        return mediaPojo;
    }
}
