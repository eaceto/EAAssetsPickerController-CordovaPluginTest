package com.ezequielaceto.cordova.assetspicker.gallery;


        import android.content.Context;
        import android.content.Intent;
        import android.net.Uri;
        import android.os.Environment;
        import android.util.Log;

        import java.io.File;
        import java.text.SimpleDateFormat;
        import java.util.Date;

/**
 * Created by Fede on 8/7/14.
 */
public class MediaUtils {

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;

    public static final String VIDEO_MP4 = "video/mp4";

    public static final String IMAGE_JPEG = "image/jpeg";
    public static final String IMAGE_JPG = "image/jpg";
    public static final String IMAGE_PNG = "image/png";

    /**
     * Create a file Uri for saving an image or video
     */
    public static Uri getOutputMediaFileUri(int type) {
        return Uri.fromFile(getOutputMediaFile(type));
    }

    /**
     * Create a File for saving an image or video
     */
    public static File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DCIM), "");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
    }

    public static void addMediaToGallery(File file, Context context) {
        context.sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
    }

    public static boolean isImageMimeType(String mimeType) {
        return IMAGE_JPEG.equals(mimeType) | IMAGE_PNG.equals(mimeType) | IMAGE_JPG.equals(mimeType);
    }

    public static boolean isVideoMimeType(String mimeType) {
        return VIDEO_MP4.equals(mimeType);
    }
}