/**
 * An Image Picker Plugin for Cordova/PhoneGap.
 */
package com.ezequielaceto.android;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.net.Uri;
import android.os.Build;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;

public class EAAssetPicker extends CordovaPlugin {
	public static String TAG = "EAAssetPicker";
	 
	private CallbackContext callbackContext;
	private JSONObject params;
	 
	public boolean execute(String action, final JSONArray args, final CallbackContext callbackContext) throws JSONException {
		 this.callbackContext = callbackContext;
		 this.params = args.getJSONObject(0);
		if (action.equals("pickAssets")) {

			//Intent intent = new Intent(cordova.getActivity(), MultiImageChooserActivity.class);

			int maxAssetsToPick = 1;
			int maxVideoLength = 240;
			String contentType = "all";

			if (this.params.has("MaxNumberOfAssetsToPick")) {
				maxAssetsToPick = this.params.getInt("MaxNumberOfAssetsToPick");
			}
			if (this.params.has("MaxVideoLengthInSeconds")) {
				maxVideoLength = this.params.getInt("MaxVideoLengthInSeconds");
			}
			if (this.params.has("ContentType")) {
				contentType = this.params.getString("ContentType");
			}

			Intent intent = new Intent();
			intent.setAction(Intent.ACTION_GET_CONTENT);

			if (maxAssetsToPick > 1 && Build.VERSION.SDK_INT >= 18 ) {
				intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);
			}
			else {
				intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false);
			}

			if ("photos".equalsIgnoreCase(contentType)) {

				intent.setType("image/*");

				if (this.cordova != null) {

					this.cordova.startActivityForResult((CordovaPlugin) this, intent, 0);
					return true;
				}
			}
			else if ("videos".equalsIgnoreCase(contentType)) {
				intent.setType("video/*");

				if (this.cordova != null) {

					this.cordova.startActivityForResult((CordovaPlugin) this, intent, 0);
					return true;
				}
			}
			else {

				intent.setType("video/*,image/*");

				if (this.cordova != null) {

					this.cordova.startActivityForResult((CordovaPlugin) this, intent, 0);
					return true;
				}
			}
		}
		return false;
	}
	
	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (resultCode == Activity.RESULT_OK && data != null) {
			ArrayList<String> fileNames = new ArrayList<String>();
			if (data.getClipData() != null) {
				for (int i = 0; i < data.getClipData().getItemCount(); i++) {
					ClipData.Item item = data.getClipData().getItemAt(i);
					Uri uri = item.getUri();
					String url = uri.toString();
					fileNames.add(url);
				}
			}
			else if (data.getData() != null) {
				String url = data.getData().toString();
				fileNames.add(url);
			}
			JSONArray res = new JSONArray(fileNames);
			this.callbackContext.success(res);
		} else {
			JSONArray res = new JSONArray();
			this.callbackContext.success(res);
		}
	}
}