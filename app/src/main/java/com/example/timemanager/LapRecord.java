package com.example.timemanager;

public class LapRecord {
    private int index;
    private String date; // 年月日信息 (yyyy-MM-dd)
    private String interval; // 新增：间隔时间（每次点击分段的间隔时间）
    private String lapTime; // 间隔累计（累计时间）
    private String startTime; // 本次记录的开始系统时间 (yyyy-MM-dd HH:mm:ss)
    private String recordTime; // 本次记录的完成系统时间 (yyyy-MM-dd HH:mm:ss)
    private long recordSystemTimeMillis; // 记录完成时的系统时间戳 (用于下一段的起始时间计算)
    private String category; // 分段种类
    private String detail; // 具体事件描述

    public LapRecord(int index, String date, String interval, String lapTime, String startTime, String recordTime, long recordSystemTimeMillis, String category, String detail) {
        this.index = index;
        this.date = date;
        this.interval = interval;
        this.lapTime = lapTime;
        this.startTime = startTime;
        this.recordTime = recordTime;
        this.recordSystemTimeMillis = recordSystemTimeMillis;
        this.category = category;
        this.detail = detail;
    }

    // Getter 方法 (用于数据读取)
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