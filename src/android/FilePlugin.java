package site.mboss.cordova;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;

import android.os.Environment;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class FilePlugin extends CordovaPlugin {
	private static final String ACTION_CREATE_DIRECTORY = "createDirectory";
	private static final String ACTION_DELETE_DIRECTORY = "deleteDirectory";
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

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		try {
			// create directory
			if (action.equals(FilePlugin.ACTION_CREATE_DIRECTORY)) {
				this.createDirectory(args.getString(0));
				this.callback.ok(true);
				return true;
			} else if (action.equals(FilePlugin.ACTION_DELETE_DIRECTORY)) {
				if (!deleteDirectory(args.getString(0))) {
					this.callback.ok(false);
				} else {
					this.callback.ok(true);
				}
			} else if (action.equals(FilePlugin.ACTION_READ_STRING)) {
				byte[] bytes = this.readBuffer(args.getString(0));
				if (bytes == null) {
					this.callback.ok("");
				} else {
					this.callback.ok(bytes.toString());
				}
			} else if (action.equals(FilePlugin.ACTION_READ_BUFFER)) {
				byte[] bytes = this.readBuffer(args.getString(0));
				if (bytes == null) {
					this.callback.ok(null);
				} else {
					this.callback.ok(bytes);
				}
			} else if (action.equals(FilePlugin.ACTION_WRITE_FILE)) {
				boolean success = this.write(args.getString(0), args.getString(1));
				if (success) {
					this.callback.ok(true);
				} else {
					this.callback.ok(false);
				}
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

	/**
	 * create directory
	 */
	private boolean createDirectory(String path) {
		if (path.equals("")) {
			return true;
		}
		try {
			File dir = new File(Environment.getExternalStorageDirectory(), path);
			if (!dir.exists()) {
				dir.mkdir();
			}
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	/**
	 * 读取文件
	 * 
	 * @param path 路径
	 * @return 返回字节数组
	 */
	private byte[] readBuffer(String path) {
		try {
			// 创建文件
			File file = new File(Environment.getExternalStorageDirectory(), path);
			if (!file.exists() || !file.isFile()) {
				return null;
			}
			// 创建FileInputStream对象
			FileInputStream fis = new FileInputStream(file);
			// 创建字节数组 每次缓冲1M
			byte[] b = new byte[1024];
			int len = 0;// 一次读取1024字节大小，没有数据后返回-1.
			// 创建ByteArrayOutputStream对象
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			// 一次读取1024个字节，然后往字符输出流中写读取的字节数
			while ((len = fis.read(b)) != -1) {
				baos.write(b, 0, len);
			}
			// 将读取的字节总数生成字节数组
			byte[] data = baos.toByteArray();
			// 关闭字节输出流
			baos.close();
			// 关闭文件输入流
			fis.close();
			// 返回字符串对象
			return data;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * 保存文件
	 * 
	 * @param path 文件路径
	 * @param data 文件内容
	 * @return 成功返回 true
	 */
	public static boolean write(String path, byte[] data) {
		try {
			// 创建指定路径的文件
			File file = new File(Environment.getExternalStorageDirectory(), path);
			// 如果文件存在
			if (file.exists()) {
				// 创建新的空文件
				file.delete();
			}
			file.createNewFile();
			// 获取文件的输出流对象
			FileOutputStream outStream = new FileOutputStream(file);
			// 获取字符串对象的byte数组并写入文件流
			outStream.write(data);
			// 最后关闭文件输出流
			outStream.close();
			return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
	}

	public boolean deleteDirectory(String path) {
		File dir = new File(Environment.getExternalStorageDirectory(), path);
		if (!dir.exists()) {
			return true;
		}
		File[] files = dir.listFiles();
		if (files == null || files.length < 1) {
			return dir.delete();
		}
		for (File f : files) {
			if (f.isDirectory()) {
				if (!this.deleteDirWihtFile(f)) {
					return false;
				}
			} else {
				if (!f.delete()) {
					return false;
				}
			}
		}
		return dir.delete();
	}

	private boolean deleteDirWihtFile(File dir) {
		if (dir == null || !dir.exists() || !dir.isDirectory()) {
			return true;
		}
		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				if (!file.delete()) {
					return false;
				}
			} else if (file.isDirectory()) {
				if (!deleteDirWihtFile(file)) {
					return false;
				}
			}
		}
		return dir.delete();
	}
}