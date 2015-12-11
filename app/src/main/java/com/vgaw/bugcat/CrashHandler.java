package com.vgaw.bugcat;

import java.lang.Thread.UncaughtExceptionHandler;

import android.content.Context;
import android.widget.Toast;

public class CrashHandler implements UncaughtExceptionHandler {

	private Context mContext;
	private static CrashHandler instance = null;
	
	private CrashHandler(){}
	
	public static CrashHandler getInstance() {
		synchronized (CrashHandler.class) {
			if (instance == null){
				instance = new CrashHandler();
			}
		}
		return instance;
	}

	
	public void initial(Context mContext) {
		// TODO Auto-generated constructor stub
		this.mContext = mContext;
		// 设置该CrashHandler为程序的默认处理器
		Thread.setDefaultUncaughtExceptionHandler(this);
	}

	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		// TODO Auto-generated method stub
		Toast.makeText(mContext, "很抱歉,程序出现异常,即将退出.", Toast.LENGTH_SHORT).show();
		handleException(ex);
		// 退出程序
		android.os.Process.killProcess(android.os.Process.myPid());
		System.exit(1);
	}

	private boolean handleException(Throwable ex) {
		return true;
	}

}