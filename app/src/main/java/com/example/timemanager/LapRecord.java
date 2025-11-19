// LapRecord.java 保持不变
package com.example.timemanager;

public class LapRecord {
    private int index;
    private String lapTime; // 本次分段时间
    private String totalTime; // 累计总时间
    private String startTime; // 本次记录的开始系统时间 (yyyy-MM-dd HH:mm:ss)
    private String recordTime; // 本次记录的完成系统时间 (yyyy-MM-dd HH:mm:ss)
    private long recordSystemTimeMillis; // 记录完成时的系统时间戳 (用于下一段的起始时间计算)
    private String category; // 分段种类
    private String detail; // 具体事件描述

    public LapRecord(int index, String lapTime, String totalTime, String startTime, String recordTime, long recordSystemTimeMillis, String category, String detail) {
        this.index = index;
        this.lapTime = lapTime;
        this.totalTime = totalTime;
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

    public String getLapTime() {
        return lapTime;
    }

    public String getTotalTime() {
        return totalTime;
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