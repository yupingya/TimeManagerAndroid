// LogUtils.java
// 【2025-11-20 16:05】新增：工作日志工具类
// 功能：将运行日志自动保存到 /sdcard/Download/TimeManager/log_yyyyMMdd.txt
package com.example.timemanager.util;

import android.os.Environment;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LogUtils {
    private static final String TAG = "TimeManagerLog";
    private static File logFile = null;

    // 【2025-11-20 16:06】新增：获取当日日志文件（自动创建目录）
    // 功能：返回 /sdcard/Download/TimeManager/log_yyyyMMdd.txt 文件对象
    private static File getLogFile() {
        if (logFile == null) {
            // 获取公共 Download 目录
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            File logDir = new File(downloadDir, "TimeManager");
            if (!logDir.exists()) {
                logDir.mkdirs(); // 自动创建 TimeManager 文件夹
            }
            String dateStr = new SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(new Date());
            logFile = new File(logDir, "log_" + dateStr + ".txt");
        }
        return logFile;
    }

    // 【2025-11-20 16:07】新增：写入日志条目
    // 功能：将带时间戳的日志消息追加到日志文件，并输出到 Android Logcat
    public static void log(String message) {
        try {
            File file = getLogFile();
            FileWriter writer = new FileWriter(file, true); // true = 追加模式
            String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
                    .format(new Date());
            String logEntry = "[" + timestamp + "] " + message + "\n";
            writer.write(logEntry);
            writer.close();

            // 同时输出到 Logcat，方便开发者调试
            Log.i(TAG, message);
        } catch (IOException e) {
            Log.e(TAG, "Failed to write log", e);
        }
    }
}