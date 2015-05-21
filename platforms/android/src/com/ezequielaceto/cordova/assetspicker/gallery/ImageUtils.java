package com.ezequielaceto.cordova.assetspicker.gallery;

import android.graphics.Bitmap;
import android.graphics.RectF;
import android.graphics.Canvas;
import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import com.ezequielaceto.cordova.assetspicker.test.R;


/**
 * Created by kimi on 29/07/14.
 */
public class ImageUtils {
    public static Bitmap scaleCenterCrop(Bitmap source, int newHeight, int newWidth) {

        if (source == null)
            return null;

        int sourceWidth = source.getWidth();
        int sourceHeight = source.getHeight();

        // Compute the scaling factors to fit the new height and width, respectively.
        // To cover the final image, the final scaling will be the bigger
        // of these two.
        float xScale = (float) newWidth / sourceWidth;
        float yScale = (float) newHeight / sourceHeight;
        float scale = Math.max(xScale, yScale);

        // Now get the size of the source bitmap when scaled
        float scaledWidth = scale * sourceWidth;
        float scaledHeight = scale * sourceHeight;

        // Let's find out the upper left coordinates if the scaled bitmap
        // should be centered in the new size give by the parameters
        float left = (newWidth - scaledWidth) / 2;
        float top = (newHeight - scaledHeight) / 2;

        // The target rectangle for the new, scaled version of the source bitmap will now
        // be
        RectF targetRect = new RectF(left, top, left + scaledWidth, top + scaledHeight);

        Bitmap.Config config = source.getConfig();

        //@Fede workaround to avoid IllegalArgumentException, this could be happen with an unrecognized bitmap config
        if (config == null)
            config = Bitmap.Config.ARGB_8888;

        // Finally, we create a new bitmap of the specified size and draw our new,
        // scaled bitmap onto it.
        Bitmap dest = Bitmap.createBitmap(newWidth, newHeight, config);
        Canvas canvas = new Canvas(dest);
        canvas.drawBitmap(source, null, targetRect, null);

        return dest;
    }

    public static Bitmap thumbBitmap(Context context, int col) {
        Drawable myDrawable;

        if (col == 0) {
            myDrawable = context.getResources().getDrawable(R.drawable.photo_camera_icon);
        } else if (col == 1) {
            myDrawable = context.getResources().getDrawable(R.drawable.video_camera_icon);
        } else {
            myDrawable = context.getResources().getDrawable(R.drawable.album_icon);
        }

        return ((BitmapDrawable) myDrawable).getBitmap();
    }

}
