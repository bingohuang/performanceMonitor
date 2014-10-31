/*
 * Copyright (c) 2012-2013 NetEase, Inc. and other contributors
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package com.netease.emmagee.performance;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningAppProcessInfo;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.util.Log;

/**
 * Service running in background
 * 
 * @author hz_liuxiao
 */
public class PerformanceMonitor {

	private final static String LOG_TAG = "Emmagee-" + PerformanceMonitor.class.getSimpleName();

	private int delaytime = 1000;
	private Handler handler = new Handler();

	public BufferedWriter bw;
	public FileOutputStream out;
	public OutputStreamWriter osw;
	public String resultFilePath;

	private int pid, uid;
	private CpuInfo cpuInfo;
	private MemoryInfo memoryInfo;
	private TrafficInfo networkInfo;
	private SimpleDateFormat formatterTime;
	private DecimalFormat fomart;
	private boolean isInitialStatic = true;
	private long processCpu1, processCpu2, totalCpu1, totalCpu2, idleCpu1, idleCpu2;
	private long startTraff, endTraff, intervalTraff;
	private String currentBatt, temperature, voltage;
	private boolean isRunnableStop = false;
	private BatteryInfoBroadcastReceiver receiver;
	private Context context;

	private String toolName;

	// private OrangeSolo orange;

	public PerformanceMonitor(Context context, String packageName, String toolName, String mDateTime) {
		this.context = context;

		fomart = new DecimalFormat();
		fomart.setMaximumFractionDigits(2);
		fomart.setMinimumFractionDigits(2);
		// 注册广播监听电量
		receiver = new BatteryInfoBroadcastReceiver();
		IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
		context.registerReceiver(receiver, filter);

		// formatterTime = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		// ObjectSerializable serializable = (ObjectSerializable)
		// intent.getSerializableExtra("orange");
		// orange = serializable.getOrange();
		getAppInfo(packageName);
		// 不在初始化的时候创建报告，而是在真正做记录的时候创建
		// creatReport(toolName, mDateTime);
		this.toolName = toolName;
		cpuInfo = new CpuInfo();
		memoryInfo = new MemoryInfo();
		networkInfo = new TrafficInfo();
	}

	/**
	 * get pid and uid
	 * 
	 * @param packageName
	 */
	private void getAppInfo(String packageName) {
		ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
		List<RunningAppProcessInfo> run = am.getRunningAppProcesses();
		// PackageManager pm = context.getPackageManager();
		for (RunningAppProcessInfo runningProcess : run) {
			if (packageName.equals(runningProcess.processName)) {
				uid = runningProcess.uid;
				pid = runningProcess.pid;
				Log.d(LOG_TAG, "pid = " + pid);
				Log.d(LOG_TAG, "uid = " + uid);
				break;
			}
		}
	}

	/**
	 * write the test result to csv format report.
	 */
	private void creatReport(String toolName, String dateTime) {
		Log.d(LOG_TAG, "start write report");
		// Calendar cal = Calendar.getInstance();
		// SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
		// String mDateTime;
		// if ((Build.MODEL.equals("sdk")) ||
		// (Build.MODEL.equals("google_sdk")))
		// mDateTime = formatter.format(cal.getTime().getTime() + 8 * 60 * 60
		// * 1000);
		// else
		// mDateTime = formatter.format(cal.getTime().getTime());

		String dir = "";
		if (android.os.Environment.getExternalStorageState().equals(android.os.Environment.MEDIA_MOUNTED)) {
			dir = android.os.Environment.getExternalStorageDirectory() + File.separator + toolName;

		} else {
			dir = context.getFilesDir().getPath() + File.separator + toolName;
		}
		// resultFilePath = dir + File.separator + toolName + "-" + mDateTime +
		// ".csv";
		// resultFilePath = dir + File.separator + toolName + "-" +
		// Build.VERSION.SDK_INT + "-"
		// + Build.MODEL.replace(" ", "-") + "-PerformanceMonitor" + ".csv";
		resultFilePath = dir + File.separator + "PerformanceMonitor.csv"; // 这边的性能文件命名改简单一点
		try {
			// 创建目录
			File fileDir = new File(dir);
			if (!fileDir.exists()) {
				fileDir.mkdirs();
			}
			// 创建文件
			File resultFile = new File(resultFilePath);
			// 只有在性能结果文件不存在的情况下才创建文件，并生成头文件，让文件只保持一份就好
			if (!resultFile.exists()) {
				resultFile.createNewFile();
				out = new FileOutputStream(resultFile, true); // 在文件内容后继续加内容
				osw = new OutputStreamWriter(out, "GBK");
				bw = new BufferedWriter(osw);
				// 生成头文件
				bw.write("测试用例信息" + "," + "时间" + "," + "应用占用内存PSS(MB)" + "," + "应用占用内存比(%)" + "," + " 机器剩余内存(MB)" + "," + "应用占用CPU率(%)" + ","
						+ "CPU总使用率(%)" + "," + "流量(KB)" + "," + "当前电量" + "," + "电池温度(C)" + "," + "电压(V)" + "," + "当前Activity数量" + "," + "当前Activity详情" + "\r\n");
				bw.flush();
			} else {
				out = new FileOutputStream(resultFile, true); // 在文件内容后继续加内容
				osw = new OutputStreamWriter(out, "GBK");
				bw = new BufferedWriter(osw);
			}
		} catch (IOException e) {
			Log.e(LOG_TAG, e.getMessage());
		}

		Log.d(LOG_TAG, "end write report");
	}
	
	/**
	 * write data into certain file
	 */
	public void writePerformanceData(String mDateTime, ArrayList<Activity> activities) {
		if (isInitialStatic) {
			// 创建相应的性能数据报告
			creatReport(toolName, mDateTime);
			startTraff = networkInfo.getTrafficInfo(uid);
			isInitialStatic = false;
		}

		// Network
		endTraff = networkInfo.getTrafficInfo(uid);
		if (startTraff == -1)
			intervalTraff = -1;
		else
			intervalTraff = (endTraff - startTraff + 1023) / 1024;

		// CPU
		processCpu1 = cpuInfo.readCpuStat(pid)[0];
		idleCpu1 = cpuInfo.readCpuStat(pid)[1];
		totalCpu1 = cpuInfo.readCpuStat(pid)[2];
		String processCpuRatio = fomart.format(100 * ((double) (processCpu1 - processCpu2) / ((double) (totalCpu1 - totalCpu2))));
		String totalCpuRatio = fomart.format(100 * ((double) ((totalCpu1 - idleCpu1) - (totalCpu2 - idleCpu2)) / (double) (totalCpu1 - totalCpu2)));

		// Memory
		long pidMemory = memoryInfo.getPidMemorySize(pid, context);
		String pss = fomart.format((double) pidMemory / 1024);
		long freeMemory = memoryInfo.getFreeMemorySize(context);
		String freeMem = fomart.format((double) freeMemory / 1024);
		long totalMemorySize = memoryInfo.getTotalMemory();
		String percent = "统计出错";
		if (totalMemorySize != 0) {
			percent = fomart.format(((double) pidMemory / (double) totalMemorySize) * 100);
		}

		try {
			// 当应用的cpu使用率大于0时才写入文件中，过滤掉异常数据
			if (isPositive(processCpuRatio) && isPositive(totalCpuRatio)) {
				// Added: 加入当前Activity的数量和Activity详情
				int size = (activities == null) ? 0 : activities.size();
				String activityNames = "";
				// 把Activity Name拼凑起来
				for(Activity activity : activities) {
					activityNames += getActivityName(activity) + "|";
				}
				// 去掉最后一个"|"
				if (activityNames.length() > 0) {
					activityNames = activityNames.substring(0, activityNames.length() - 1);
				}
				
				if (intervalTraff == -1) {
					bw.write(this.getTestCaseInfo() + "-" + this.getActionInfo() + "," + mDateTime + "," + pss + "," + percent + "," + freeMem + ","
							+ processCpuRatio + "," + totalCpuRatio + "," + "本程序或本设备不支持流量统计" + "," + currentBatt + "," + temperature + "," + voltage
							+ "," + size + "," + activityNames + "\r\n");
				} else {
					bw.write(this.getTestCaseInfo() + "-" + this.getActionInfo() + "," + mDateTime + "," + pss + "," + percent + "," + freeMem + ","
							+ processCpuRatio + "," + totalCpuRatio + "," + intervalTraff + "," + currentBatt + "," + temperature + "," + voltage
							+ "," + size + "," + activityNames + "\r\n");
				}
				bw.flush();
				Log.i(LOG_TAG, "*** writePerformanceData on " + mDateTime + " *** ");
				processCpu2 = processCpu1;
				idleCpu2 = idleCpu1;
				totalCpu2 = totalCpu1;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
	
	/**
	 * 获取当前Activity最简名称，如果有"."，取"."到最后的字符串
	 * @return
	 */
	public String getActivityName(Activity activity) {
		String activityName = activity.getLocalClassName();
		int index = activityName.lastIndexOf(".");
		if(index != -1) {
			activityName = activityName.substring(index + 1);
		}
		return activityName;
	}

	private Runnable task = new Runnable() {

		public void run() {
			if (!isRunnableStop) {
				handler.postDelayed(this, delaytime);
				// writePerformanceData();
			}
		};
	};

	/**
	 * 电量广播类
	 * 
	 * 
	 */
	public class BatteryInfoBroadcastReceiver extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {

			if (Intent.ACTION_BATTERY_CHANGED.equals(intent.getAction())) {
				int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0);

				int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
				currentBatt = String.valueOf(level * 100 / scale) + "%";

				voltage = String.valueOf(intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1) * 1.0 / 1000);

				temperature = String.valueOf(intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) * 1.0 / 10);

				int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
			}

		}

	}

	/**
	 * close all opened stream.
	 */
	public void closeOpenedStream() {
		try {
			if (bw != null)
				bw.close();
			if (osw != null)
				osw.close();
			if (out != null)
				out.close();
		} catch (Exception e) {
			Log.d(LOG_TAG, e.getMessage());
		}
	}

	public void onDestroy() {
		context.unregisterReceiver(receiver);
		isRunnableStop = true;
		closeOpenedStream();
	}

	/**
	 * is text a positive number
	 * 
	 * @param text
	 * @return
	 */
	private boolean isPositive(String text) {
		Double num;
		try {
			num = Double.parseDouble(text);
		} catch (NumberFormatException e) {
			return false;
		}
		return num >= 0;
	}

	/**
	 * 从栈中获取类名和测试方法名
	 * 
	 * @return 类名+测试方法名
	 */
	private String getTestCaseInfo() {
		String testCaseInfo = "";
		String testName, className;
		StackTraceElement[] stack = (new Throwable()).getStackTrace();
		int i = 0;
		while (i < stack.length) {
			// 在setUp和testXXX中会有orange的操作方法
			if (stack[i].getMethodName().toString().startsWith("test") || stack[i].getMethodName().toString().startsWith("setUp")) {
				break;
			}
			i++;
		}

		if (i >= stack.length) {
			testCaseInfo = "No TestCase Info";
		} else {
			// “.”在正则中代码任意字符，不能用来分割，如需使用则通过“//.”转义
			String[] packageName = stack[i].getClassName().toString().split("\\.");
			className = packageName[packageName.length - 1];
			// className = stack[i].getClassName().toString();
			testName = stack[i].getMethodName().toString();
			testCaseInfo = className + "." + testName;
		}
		// Log.i(LOG_TAG, "*** getTestCaseInfo =" + testCaseInfo + " *** ");
		return testCaseInfo;
	}

	/**
	 * 从栈中获取相应操作方法名，有clickXXX，enterXXX，scrollXXX，typeXXX
	 * 
	 * @return 类名+测试方法名
	 */
	private String getActionInfo() {
		String actionInfo = "";
		String testName, className;
		StackTraceElement[] stack = (new Throwable()).getStackTrace();
		int i = 0;
		while (i < stack.length) {
			// 类对应的是OrangeSolo，
			if (stack[i].getMethodName().toString().startsWith("click") || stack[i].getMethodName().toString().startsWith("enter")
					|| stack[i].getMethodName().toString().startsWith("scroll") || stack[i].getMethodName().toString().startsWith("type")) {
				break;
			}
			i++;
		}

		if (i >= stack.length) {
			actionInfo = "No Action Info";
		} else {
			// className暂时不用展现
			testName = stack[i].getMethodName().toString();
			actionInfo = testName;
		}
		return actionInfo;
	}
}
