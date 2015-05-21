package com.ezequielaceto.cordova.assetspicker.gallery;


import android.net.Uri;

/**
 * Created by Fede on 12/1/14.
 */
public class MediaPojo {

    private Uri uri;
    private String path;
    private String mimetype;

    public Uri getUri() {
        return uri;
    }

    public void setUri(Uri uri) {
        this.uri = uri;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMimetype() {
        return mimetype;
    }

    public void setMimetype(String mimetype) {
        this.mimetype = mimetype;
    }
}
