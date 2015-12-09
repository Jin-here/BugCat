package com.vgaw.bugcat;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * context of bug file(filepath is "../bugbox/bug") is like below:
 * MAGIC                            --head--
 * VERSION                              |
 * APP_VERSION                          |
 * |
 * --head--
 * KEY
 * KEY
 * KEY
 * ...
 */

/**
 * todo 可能存在的问题，写入太快的问题
 * todo bug文件放置位置问题
 */
public class BugCat {
    private static final String MAGIC = "com.vgaw.bugcat";
    private static final String VERSION = "1.0";
    private static final String DIR_NAME = "bugbox";
    private static final String FILE_NAME = "bug";

    private static final int MAGIC_INDEX = 1;
    private static final int VERSION_INDEX = 3;
    private static final int APP_VERSION_INDEX = 5;
    private static final int HEAD_END_INDEX = 7;

    private static BugCat instance;
    private File file;
    private Context context;

    private BugCat() {
    }

    public static BugCat getInstance() {
        synchronized (BugCat.class) {
            if (instance == null) {
                instance = new BugCat();
            }
        }
        return instance;
    }

    /**
     * 初始化
     *
     * @param context
     */
    public void initial(Context context) {
        this.context = context;
        // 若文件不存在，则创建，并写入head
        File dir = getDiskCacheDir(context, DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        file = new File(dir, FILE_NAME);
        if (!file.exists()) {
            writeHead();
        }

        // 如果版本变更，清空，再重新写入head
        if (isAppVersionChanged(getAppVersion())) {
            writeHead();
        }
    }

    private boolean writeNewKey(String key) {
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file, true));
            writer.write(key);
            writer.newLine();
            return true;
        } catch (IOException e) {
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private boolean isKeyExist(String key) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            if ((runToLine(reader, HEAD_END_INDEX)) != null) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    if (line.equals(key)) {
                        return true;
                    }
                    continue;
                }
            }
        } catch (FileNotFoundException e) {
            // 文件不存在
        } catch (IOException e) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        return false;
    }

    /**
     * 基于bug文件存在的情况下，但要考虑head没有写入完整的情况
     * 默认head没写入完整，在写入一遍
     *
     * @param nowVersion
     * @return
     */
    private boolean isAppVersionChanged(String nowVersion) {
        return readAppVersion() != nowVersion;
    }

    private void writeHead() {
        Writer writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file));
            writer.write(MAGIC);
            writer.write("\n");
            writer.write(VERSION);
            writer.write("\n");
            writer.write(getAppVersion());
            writer.write("\n");
            writer.write("\n");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * 提交bug入口，只提交一次，不管提交失不失败
     *
     * @param bugInfo
     */
    public void deliverBug(String bugInfo) {
        String key = hashKeyForDisk(bugInfo);
        if (!isKeyExist(key)) {
            writeNewKey(key);
        }

    }

    /**
     * 唯一一处完整性检查，为保证效率，不做其他完整性检查
     *
     * @return 若写入head不完整，则返回null
     */
    private String readAppVersion() {
        BufferedReader reader = null;
        String version = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            if ((version = runToLine(reader, APP_VERSION_INDEX)) == null) {
                // 上次写入head没写完整，重新写一遍
                writeHead();
                version = getAppVersion();
            }
        } catch (FileNotFoundException e) {
            // 文件不存在
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        return version;

    }

    /**
     * 如果index大于文件目前的行数返回null
     *
     * @param reader
     * @param index
     * @return
     */
    private String runToLine(BufferedReader reader, int index) {
        String line = null;
        for (int i = 0; i < index; i++) {
            try {
                line = reader.readLine();
            } catch (IOException e) {
                // 读到文件末尾
                return null;
            }
        }
        return line;
    }

    /**
     * 若app版本变更，bug文件清空重写
     * 向服务器发送所需信息，可由http请求user-agent获取，可不用在此处获取，以节省流量
     *
     * @return
     */
    private String getAppVersion() {
        // 获取packagemanager的实例
        PackageManager packageManager = context.getPackageManager();
        // getPackageName()是你当前类的包名，0代表是获取版本信息
        PackageInfo packInfo = null;
        try {
            packInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            return packInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "get app version failed";
        }
    }

    /**
     * 向服务器发送所需信息，可由http请求user-agent获取，可不用在此处获取，以节省流量
     *
     * @return
     */
    private String getPhoneInfo() {
        StringBuilder sb = new StringBuilder();
        try {
            PackageManager pm = context.getPackageManager();
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), PackageManager.GET_ACTIVITIES);
            if (pi != null) {
                String versionName = pi.versionName == null ? "null" : pi.versionName;
                String versionCode = pi.versionCode + "";
                sb.append("versionName:" + versionName + "\n");
                sb.append("versionCode:" + versionCode + "\n");
            }
        } catch (PackageManager.NameNotFoundException e) {
        }
        Field[] fields = Build.class.getDeclaredFields();
        for (Field field : fields) {
            try {
                field.setAccessible(true);
                sb.append(field.getName() + ":" + field.get(null).toString() + "\n");
            } catch (Exception e) {
            }
        }
        String result = sb.toString();
        return result.equals("") ? "get phone info failed" : result;
    }

    /**
     * A hashing method that changes a string (like a URL) into a hash suitable for using as a
     * disk filename.
     */
    private String hashKeyForDisk(String key) {
        String cacheKey;
        try {
            final MessageDigest mDigest = MessageDigest.getInstance("MD5");
            mDigest.update(key.getBytes());
            cacheKey = bytesToHexString(mDigest.digest());
        } catch (NoSuchAlgorithmException e) {
            cacheKey = String.valueOf(key.hashCode());
        }
        return cacheKey;
    }

    private String bytesToHexString(byte[] bytes) {
        // http://stackoverflow.com/questions/332079
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(0xFF & bytes[i]);
            if (hex.length() == 1) {
                sb.append('0');
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * Get a usable cache directory (external if available, internal otherwise).
     *
     * @param context    The context to use
     * @param uniqueName A unique directory name to append to the cache dir
     * @return The cache dir
     */
    private File getDiskCacheDir(Context context, String uniqueName) {
        // Check if media is mounted or storage is built-in, if so, try and use external cache dir
        // otherwise use internal cache dir
        final String cachePath =
                Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                        !isExternalStorageRemovable() ? getExternalCacheDir(context).getPath() :
                        context.getCacheDir().getPath();

        //return new File(cachePath + File.separator + uniqueName);
        return new File("/sdcard/" + uniqueName);
    }

    /**
     * Check if external storage is built-in or removable.
     *
     * @return True if external storage is removable (like an SD card), false
     * otherwise.
     */
    @TargetApi(Build.VERSION_CODES.GINGERBREAD)
    private boolean isExternalStorageRemovable() {
        if (Utils.hasGingerbread()) {
            return Environment.isExternalStorageRemovable();
        }
        return true;
    }

    /**
     * Get the external app cache directory.
     *
     * @param context The context to use
     * @return The external cache dir
     */
    @TargetApi(Build.VERSION_CODES.FROYO)
    private File getExternalCacheDir(Context context) {
        if (Utils.hasFroyo()) {
            return context.getExternalCacheDir();
        }

        // Before Froyo we need to construct the external cache dir ourselves
        final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
        return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
    }
}
