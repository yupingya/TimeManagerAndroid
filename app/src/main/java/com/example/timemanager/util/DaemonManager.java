// DaemonManager.java
// 修改时间：20251118 16:35
package com.example.timemanager.util;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class DaemonManager {
    // 启动保活服务
    public static void startDaemonService(Context context) {
        Intent intent = new Intent(context, DaemonService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    // 停止保活服务
    public static void stopDaemonService(Context context) {
        Intent intent = new Intent(context, DaemonService.class);
        context.stopService(intent);
    }
}