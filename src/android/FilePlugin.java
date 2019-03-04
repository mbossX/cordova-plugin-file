package site.mboss.cordova;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.apache.cordova.CordovaPreferences;
import org.json.JSONArray;
import org.json.JSONObject;

import android.os.Environment;
import android.util.Log;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class FilePlugin extends CordovaPlugin {
	private static final String PACKAGE_PROPERTY_KEY = "package";
	private static final String ACTION_CREATE_DIRECTORY = "createDirectory";
	private static final String ACTION_DELETE_DIRECTORY = "deleteDirectory";
	private static final String ACTION_READ_STRING = "readString";
	private static final String ACTION_READ_BUFFER = "readBuffer";
	private static final String ACTION_WRITE_FILE = "write";
	private static final String TAG = "File";

	private String packageName = "cn.itvmedia.player/";

	@Override
	protected void pluginInitialize() {
		super.pluginInitialize();
		packageName = preferences.getString(PACKAGE_PROPERTY_KEY, "cn.itvmedia.player/");
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) {
		try {
			// create directory
			if (action.equals(FilePlugin.ACTION_CREATE_DIRECTORY)) {
				this.createDirectory(args.getString(0));
				this.callback.ok(true);
				return true;
			}
			// delete directory
			else if (action.equals(FilePlugin.ACTION_DELETE_DIRECTORY)) {
				if (!deleteDirectory(args.getString(0))) {
					this.callback.ok(false);
				} else {
					this.callback.ok(true);
				}
			}
			// read string
			else if (action.equals(FilePlugin.ACTION_READ_STRING)) {
				byte[] bytes = this.readBuffer(args.getString(0));
				if (bytes == null) {
					this.callback.ok("");
				} else {
					this.callback.ok(bytes.toString());
				}
			}
			// read buffer
			else if (action.equals(FilePlugin.ACTION_READ_BUFFER)) {
				byte[] bytes = this.readBuffer(args.getString(0));
				if (bytes == null) {
					this.callback.ok(null);
				} else {
					this.callback.ok(bytes);
				}
			}
			// write
			else if (action.equals(FilePlugin.ACTION_WRITE_FILE)) {
				boolean success = this.write(args.getJsonObject(0), args.getString(1));
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

	/**
	 * create directory
	 */
	private boolean createDirectory(String path) {
		if (path.equals("")) {
			return true;
		}
		try {
			File dir = new File(Environment.getExternalStorageDirectory(), packageName + path);
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
	 * @return 成功返回 true
	 */
	public boolean write(JSONObject args) {
		try {
			if (!args.has("path")) {
				return false;
			}
			if (!args.has("data")) {
				return false;
			}
			String path = args.getString("path");
			byte[] data = args.getArrayBuffer("path");
			// 创建指定路径的文件
			File file = new File(Environment.getExternalStorageDirectory(), packageName + path);
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
		File dir = new File(Environment.getExternalStorageDirectory(), packageName + path);
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