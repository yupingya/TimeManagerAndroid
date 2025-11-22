package com.example.timemanager.data.model;

import com.example.timemanager.util.LogUtils;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * LapRecord 数据模型
 * 修改时间：2025-11-19 15:10 - 架构优化：集成时间格式化逻辑，构造函数接收 long 类型原始数据
 */
public class LapRecord {
    private int index;
    private String date; // 年月日信息 (yyyy-MM-dd)
    private String interval; // 间隔时间（字符串格式）
    private String lapTime; // 间隔累计（字符串格式）
    private String startTime; // 本次记录的开始系统时间
    private String recordTime; // 本次记录的完成系统时间
    private long recordSystemTimeMillis; // 记录完成时的系统时间戳
    private String category; // 分段种类
    private String detail; // 具体事件描述

    /**
     * 构造函数
     * 修改时间：2025-11-19 15:10 -改为接收 long 类型的 intervalMillis 和 totalAccumulatedMillis
     * 并在内部调用 formatTime 进行格式化，实现逻辑内聚。
     */
    public LapRecord(int index, String date, long intervalMillis, long totalAccumulatedMillis, String startTime, String recordTime, long recordSystemTimeMillis, String category, String detail) {
        this.index = index;
        this.date = date;
        // 内部直接格式化，不再依赖外部传入 String
        this.interval = formatTime(intervalMillis);
        this.lapTime = formatTime(totalAccumulatedMillis);
        this.startTime = startTime;
        this.recordTime = recordTime;
        this.recordSystemTimeMillis = recordSystemTimeMillis;
        this.category = category;
        this.detail = detail;
    }

    /**
     * 通用时间格式化工具方法
     * 修改时间：2025-11-19 15:12 - 从 MainActivity 迁移至此，作为静态工具供全局使用
     * 格式：HH:mm:ss.SS (分秒两位，毫秒两位)
     */
    public static String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long centiseconds = (millis % 1000) / 10;

        return String.format(Locale.getDefault(), "%d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds);
    }

    // 【2025-11-22 18:30】新增：时间字符串反向解析为毫秒数
    // 功能作用：用于导入功能，将 Excel 中的 HH:mm:ss.SS 格式时间转回 long
    // 新增时间：2025年11月22日 18:30
    public static long parseTime(String timeString) {
        if (timeString == null || timeString.isEmpty()) {
            return 0L;
        }
        try {
            // 格式: H:mm:ss.SS
            String[] parts = timeString.split("[:.]"); // 分割 ':', '.'
            if (parts.length != 4) {
                LogUtils.log("【LapRecord】时间字符串解析失败: 格式错误。原始值: " + timeString);
                android.util.Log.e("LapRecord", "Invalid time string format: " + timeString);
                return 0L;
            }

            long hours = Long.parseLong(parts[0].trim());
            long minutes = Long.parseLong(parts[1].trim());
            long seconds = Long.parseLong(parts[2].trim());
            // parts[3] 是两位毫秒，表示厘秒 (centiseconds)，需要乘以 10 得到毫秒
            long centiseconds = Long.parseLong(parts[3].trim());

            long totalMillis = hours * 3600000L +
                    minutes * 60000L +
                    seconds * 1000L +
                    centiseconds * 10L;

            return totalMillis;
        } catch (NumberFormatException e) {
            LogUtils.log("【LapRecord】时间字符串包含非数字：解析异常。原始值: " + timeString);
            android.util.Log.e("LapRecord", "Time string parsing error (NumberFormatException): " + timeString, e);
            return 0L;
        } catch (Exception e) {
            LogUtils.log("【LapRecord】时间字符串解析发生未知错误。原始值: " + timeString);
            android.util.Log.e("LapRecord", "Time string parsing error (Exception): " + timeString, e);
            return 0L;
        }
    }

    // Getter 方法 (保持不变，适配 Adapter 和 ExcelExportUtil)
    public int getIndex() {
        return index;
    }

    public String getDate() {
        return date;
    }

    public String getInterval() {
        return interval;
    }

    public String getLapTime() {
        return lapTime;
    }

    public String getStartTime() {
        return startTime;
    }

    public String getRecordTime() {
        return recordTime;
    }

    public long getRecordSystemTimeMillis() {
        return recordSystemTimeMillis;
    }

    public String getCategory() {
        return category;
    }

    public String getDetail() {
        return detail;
    }
}