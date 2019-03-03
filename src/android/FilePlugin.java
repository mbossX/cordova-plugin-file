package com.cyph.cordova;

import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.Context;
import android.database.Cursor;
import android.content.CursorLoader;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Base64;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.media.MediaMetadataRetriever;
import android.text.TextUtils;
import android.widget.RelativeLayout;
import android.os.Bundle;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.lang.Exception;
import java.net.URI;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class FilePlugin extends CordovaPlugin {
	private static final String ACTION_CREATE_DIRECTORY = "createDirectory";
	private static final String ACTION_READ_STRING = "readString";
	private static final String ACTION_READ_BUFFER = "readBuffer";
	private static final String ACTION_WRITE_FILE = "write";
	private static final int PICK_FILE_REQUEST = 1;
	private static final String TAG = "File";

	private CallbackContext callback;

	public void chooseFile(CallbackContext callbackContext, String accept) {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType(accept);
		intent.putExtra(Intent.EXTRA_MIME_TYPES, accept);
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		intent.putExtra(Intent.EXTRA_LOCAL_ONLY, true);

		Intent chooser = Intent.createChooser(intent, "Select File");
		cordova.startActivityForResult(this, chooser, Chooser.PICK_FILE_REQUEST);

		PluginResult pluginResult = new PluginResult(PluginResult.Status.NO_RESULT);
		pluginResult.setKeepCallback(true);
		this.callback = callbackContext;
		callbackContext.sendPluginResult(pluginResult);
	}

	public void createDirectory(CallbackContext callbackContext, String path) {

	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		try {
			// create directory
			if (action.equals(FilePlugin.ACTION_CREATE_DIRECTORY)) {
				this.createDirectory(callbackContext, args.getString(0));
				return true;
			}
		} catch (JSONException err) {
			this.callback.error("Execute failed: " + err.toString());
		}

		return false;
	}

	@Override
	public void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		final Chooser that = this;

		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, "start");
		pluginResult.setKeepCallback(true);
		that.callback.sendPluginResult(pluginResult);
		cordova.getThreadPool().execute(new Runnable() {
			public void run() {
				try {
					if (requestCode == Chooser.PICK_FILE_REQUEST && that.callback != null) {
						if (resultCode == Activity.RESULT_OK) {
							Uri uri = data.getData();

							if (uri != null) {
								ContentResolver contentResolver = that.cordova.getActivity().getContentResolver();

								JSONObject result = new JSONObject();
								String name = Chooser.getDisplayName(contentResolver, uri);

								String mediaType = contentResolver.getType(uri);
								if (mediaType == null || mediaType.isEmpty()) {
									mediaType = "application/octet-stream";
								}

								String base64 = null;
								String thumbnail = null;
								int duration = 0;
								int w = 0;
								int h = 0;
								byte[] byteArray = null;
								if (mediaType.indexOf("image") > -1) {
									// String path = this.getRealPath(uri);
									int degree = that.readPictureDegree(contentResolver.openInputStream(uri));
									if (degree != 0) {
										Bitmap photoBmp = MediaStore.Images.Media.getBitmap(contentResolver, uri);
										Bitmap bmp = that.rotaingImageView(degree, photoBmp);
										photoBmp.recycle();
										w = bmp.getWidth();
										h = bmp.getHeight();
										byteArray = that.bitmapToBytes(bmp, 100);
										thumbnail = that.bitmapToBase64(that.getThumbnail(bmp));
										// base64 = that.bitmapToBase64(bmp);
										bmp.recycle();
									} else {
										byte[] bytes = Chooser
												.getBytesFromInputStream(contentResolver.openInputStream(uri));
										// base64 = Base64.encodeToString(bytes, Base64.DEFAULT);
										Bitmap bmp = that.bytesToBimap(bytes);
										w = bmp.getWidth();
										h = bmp.getHeight();
										thumbnail = that.bitmapToBase64(that.getThumbnail(that.bytesToBimap(bytes)));
										byteArray = bytes;
										bmp.recycle();
									}
									// result.put("degree", degree);
								} else { // video get thumbnail
									MediaMetadataRetriever mmr = new MediaMetadataRetriever();
									mmr.setDataSource(that.cordova.getActivity(), uri);
									String durationStr = mmr
											.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
									if (!TextUtils.isEmpty(durationStr)) {
										duration = Integer.valueOf(durationStr);
									}
									Bitmap bmp = mmr.getFrameAtTime(duration % 1000);
									w = bmp.getWidth();
									h = bmp.getHeight();
									thumbnail = that.bitmapToBase64(that.getThumbnail(bmp));

									byteArray = Chooser.getBytesFromInputStream(contentResolver.openInputStream(uri));
									// base64 = Base64.encodeToString(bytes, Base64.DEFAULT);
									bmp.recycle();
								}
								// result.put("data", base64);
								result.put("w", w);
								result.put("h", h);
								result.put("thumbnail", thumbnail);
								result.put("mediaType", mediaType);
								// result.put("name", name);
								result.put("duration", duration);
								// result.put("length", byteArray.length);
								// result.put("uri", uri.toString());

								PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result.toString());
								pluginResult.setKeepCallback(true);
								that.callback.sendPluginResult(pluginResult);

								int len = byteArray.length;
								do {
									int count = 1024 * 512; // 512k
									if (len < count) {
										count = len;
									}
									byte[] bs = new byte[count];
									System.arraycopy(byteArray, byteArray.length - len, bs, 0, count);
									pluginResult = new PluginResult(PluginResult.Status.OK, bs);
									pluginResult.setKeepCallback(true);
									that.callback.sendPluginResult(pluginResult);
									len -= count;
								} while (len > 0);
								pluginResult = new PluginResult(PluginResult.Status.OK, "end");
								pluginResult.setKeepCallback(true);
								that.callback.sendPluginResult(pluginResult);
								// that.callback.success(result.toString());
							} else {
								that.callback.error("File URI was null.");
							}
						} else if (resultCode == Activity.RESULT_CANCELED) {
							that.callback.success("RESULT_CANCELED");
						} else {
							that.callback.error(resultCode);
						}
					}
				} catch (Exception err) {
					that.callback.error("Failed to read file: " + err.toString());
				}
			}
		});
	}
}