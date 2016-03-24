package com.vgaw.bugcat;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.widget.Toast;

import com.vgaw.bugcat.http.HttpCat;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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
 * 暂时不设置最大size，因为占用本来就不多
 */
public class BugCat implements Thread.UncaughtExceptionHandler {
    private final String MAGIC = "com.vgaw.bugcat";
    private final String VERSION = "1.0";
    private final String DIR_NAME = "bugbox";
    private final String FILE_NAME = "bug";
    private final String TEMP_FILE_NAME = "bug_temp";

    private final int MAGIC_INDEX = 1;
    private final int VERSION_INDEX = 3;
    private final int APP_VERSION_INDEX = 5;
    private final int HEAD_END_INDEX = 4;

    private final String UPLOADED = "U";
    private final String NEW = "N";

    private static BugCat instance = new BugCat();
    private File file;
    private File tempFile;
    private File dir;
    private Context context;

    private BugCat() {
    }

    public static BugCat getInstance() {
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
        dir = getDiskCacheDir(context, DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        file = new File(dir, FILE_NAME);
        tempFile = new File(dir, TEMP_FILE_NAME);
        if (!file.exists()) {
            writeHead(false);
        }

        // 如果版本变更，清空，再重新写入head
        /*if (isAppVersionChanged(getAppVersion())) {
            writeHead(false);
        }*/

        // 设置为程序的默认未捕获异常处理器
        Thread.setDefaultUncaughtExceptionHandler(this);

        // 注册Wifi监听器
        registerReceiver();
    }

    /**
     * 解除监听器，使用注意点同receiver
     */
    public void release(){
        unregisterReceiver();
    }

    private boolean writeNewKey(String key, boolean isTemp) {
        BufferedWriter writer = null;
        try {
            if (isTemp){
                writer = new BufferedWriter(new FileWriter(tempFile, true));
            }else {
                writer = new BufferedWriter(new FileWriter(file, true));
            }
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

    private int isKeyExist(String key) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            if ((runToLine(reader, HEAD_END_INDEX)) != null) {
                String line = null;
                while ((line = reader.readLine()) != null) {
                    String[] splits = line.split(" ");
                    if (splits[0].equals(key)){
                        if (splits[1].equals(NEW)){
                            return 1;
                        }else if (splits[1].equals(UPLOADED)){
                            return 2;
                        }
                    }
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
        return 0;
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

    private void writeHead(boolean isTempFile) {
        BufferedWriter writer = null;
        try {
            if (isTempFile){
                writer = new BufferedWriter(new FileWriter(tempFile));
            }else {
                writer = new BufferedWriter(new FileWriter(file));
            }
            writer.write(MAGIC);
            writer.write("\n");
            writer.write(VERSION);
            writer.write("\n");
            writer.write(getAppVersion());
            writer.write("\n");
            writer.write("\n");
        } catch (IOException e) {
        } finally {
            try {
                writer.close();
            } catch (IOException e) {
            }
        }
    }

    /**
     * 将bug保存到本地，带下次进入程序时上传，
     * 之所以不立即上传，是因为程序崩溃后，上传都不会成功的
     *
     * @param bugInfo
     */
    public void deliverBug(String bugInfo) {
        String key = hashKeyForDisk(bugInfo);
        if (isKeyExist(key) == 0) {
            writeNewKey(key + " " + NEW, false);
            persistBug(key, bugInfo);
        }

    }

    // 以key为文件名，将bug信息存入该文件中。该文件位于根目录下
    private void persistBug(String key, String bugInfo) {
        File file = new File(dir, key);
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file));
            writer.write(bugInfo);
        } catch (IOException e) {
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * 一条一条bug上传
     */
    private void uploadStepByStep() {
        // 搜寻状态为NEW的文件
        // 读取文件
        // 上传
        // 修改NEW为UPLOADED，并删除bug文件
        // continue
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(file));
            String s;
            for (int i = 0; i < HEAD_END_INDEX; i++) {
                s = reader.readLine();
            }
            String line = null;
            while ((line = reader.readLine()) != null) {
                String[] splits = line.split(" ");
                if (!isInTempFile(splits[0])) {
                    if (!UPLOADED.equals(splits[1])){
                        // 读取文件内容
                        final File bugFile = new File(dir, splits[0]);
                        BufferedReader r = new BufferedReader(new FileReader(bugFile));
                        StringBuilder sb = new StringBuilder();
                        String temp = null;
                        while ((temp = r.readLine()) != null) {
                            sb.append(temp + "\n");
                        }
                        if (r != null){
                            r.close();
                        }
                        // 上传信息
                        final String finalLine = splits[0] + " " + UPLOADED;
                        if (!tempFile.exists()){
                            writeHead(true);
                        }
                        HttpCat.fly(sb.toString(), new HttpCat.AbstractResponseListener() {
                            @Override
                            public void onSuccess(String flyCat) {
                                super.onSuccess(flyCat);
                                // 在tempFile增加该记录，表示已经上传
                                writeNewKey(finalLine, true);
                                bugFile.delete();
                            }
                        });
                    }else {
                        if (!tempFile.exists()){
                            writeHead(true);
                        }
                        writeNewKey(line, true);
                    }
                }
            }
        } catch (FileNotFoundException e) {
        } catch (IOException e) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
    }

    private boolean isInTempFile(String key) {
        BufferedReader reader = null;
        if (!tempFile.exists()) {
            return false;
        }
        try {
            reader = new BufferedReader(new FileReader(tempFile));
            String line = null;
            while ((line = reader.readLine()) != null) {
                String split[] = line.split(" ");
                if (split[0].equals(key)) {
                    return true;
                }
            }
        } catch (FileNotFoundException e) {
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


    public void deliverBug(Throwable ex) {
        deliverBug(getCrashInfo(ex));
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
                writeHead(false);
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
     * 放置位置对用户不可见（用户不能在文件浏览器中看到该文件，因为该文件对用户没有任何用处，显示出来反而干扰用户，而且被用户删除，又需要重新创建，影响效率）
     * 尽量放在external storage，因为没必要放到internal storage
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
        /*return new File(cachePath + File.separator + uniqueName);*/
        return new File("/sdcard/" + uniqueName);
    }

    public boolean isWifi() {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            return false;
        }
        if (networkInfo.getType() == ConnectivityManager.TYPE_WIFI) {
            return true;
        }
        return false;
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

    @Override
    public void uncaughtException(Thread thread, Throwable ex) {
        handleException(ex);
        /*// 退出程序，移到成功将信息上传后执行
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);*/
    }

    /**
     * 处理捕获的未捕获异常
     * <p/>
     * bug形式如下：
     * <p/>
     * bug  :/ by zero
     * cause:null
     * path :12->fun->Test->Test.java
     *
     * @param ex
     */
    protected void handleException(Throwable ex) {
        Toast.makeText(context, "很抱歉,程序出现异常,即将退出.", Toast.LENGTH_SHORT).show();
        deliverBug(ex);
        // 退出程序
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
    }

    // TODO: 2015-12-11 信息是否详细待测
    private String getCrashInfo(Throwable ex) {
        StackTraceElement[] elements = ex.getStackTrace();
        if (elements.length == 0) {
            return "get crash info failed";
        }
        StackTraceElement element0 = ex.getStackTrace()[0];
        String bug = "bug  :" + ex.getMessage();
        String cause = "cause:" + ex.getCause();
        String path = "path :" + element0.getLineNumber() + "->" + element0.getMethodName() + "->" + element0.getClassName() + "->" + element0.getFileName();
        System.out.println(bug + "\n" + cause + "\n" + path);
        return bug + "\n" + cause + "\n" + path;
    }

    public class ConnectionChangeReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            NetworkInfo wifiNetInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
            uploadStepByStep();
            /*if (wifiNetInfo.isConnected()) {
                // 在打开wifi的情况下，上传bug信息
                uploadStepByStep();
            }*/
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (tempFile.exists()) {
                        file.delete();
                        tempFile.renameTo(new File(dir, "bug"));
                    }
                }
            }, 10000);
        }
    }

    private ConnectionChangeReceiver receiver = null;

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        receiver = new ConnectionChangeReceiver();
        context.registerReceiver(receiver, filter);
    }

    private void unregisterReceiver() {
        if (receiver != null) {
            context.unregisterReceiver(receiver);
        }
    }
}
