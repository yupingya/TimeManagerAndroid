// 【2025-11-22 06:00】新增：LapRepository - 统一管理分段记录的持久化与导出
// 功能作用：遵循 Repository 模式，隔离数据存储逻辑（SharedPreferences + 文件导出）
// 新增时间：2025年11月22日 06:00
package com.example.timemanager.data.repository;

import android.content.Context;
import android.net.Uri;
import androidx.annotation.NonNull;
import com.example.timemanager.data.model.LapRecord;
import com.example.timemanager.util.ExcelExportUtil;
import com.example.timemanager.util.LogUtils;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LapRepository {
    private static final String PREF_NAME = "TimeManagerPrefs";
    private static final String KEY_LAP_RECORDS = "lapRecords";

    private final Context context;
    private final Gson gson;

    // 【2025-11-22 06:02】新增构造函数
    // 功能作用：初始化上下文和Gson实例，确保线程安全
    // 新增时间：2025年11月22日 06:02
    public LapRepository(@NonNull Context context) {
        this.context = context.getApplicationContext();
        this.gson = new Gson();
    }

    // 【2025-11-22 06:03】新增：保存分段记录到 SharedPreferences
    // 功能作用：将 List<LapRecord> 序列化为 JSON 并持久化
    // 新增时间：2025年11月22日 06:03
    public void saveLapRecords(@NonNull List<LapRecord> records) {
        try {
            String json = gson.toJson(records);
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAP_RECORDS, json)
                    .apply();
            LogUtils.log("【LapRepository】分段记录已保存，数量：" + records.size());
        } catch (Exception e) {
            LogUtils.log("【LapRepository.saveLapRecords】保存失败：" + e.getMessage());
            android.util.Log.e("LapRepository", "保存分段记录异常", e);
        }
    }

    // 【2025-11-22 18:40】新增：直接保存指定记录列表
    // 功能作用：用于数据导入功能，将导入的数据列表作为新的系统保存数据
    // 新增时间：2025年11月22日 18:40
    public void saveAllLapRecords(@NonNull List<LapRecord> records) {
        try {
            String json = gson.toJson(records);
            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_LAP_RECORDS, json)
                    .apply();
            LogUtils.log(String.format(Locale.getDefault(), "【LapRepository】已保存所有分段记录，数量：%d", records.size()));
        } catch (Exception e) {
            LogUtils.log("【LapRepository.saveAllLapRecords】保存失败：" + e.getMessage());
            android.util.Log.e("LapRepository", "保存所有分段记录异常", e);
        }
    }

    // 【2025-11-22 06:04】新增：从 SharedPreferences 加载分段记录
    // 功能作用：反序列化 JSON 为 List<LapRecord>
    // 新增时间：2025年11月22日 06:04
    public List<LapRecord> loadLapRecords() {
        try {
            String json = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .getString(KEY_LAP_RECORDS, null);
            if (json == null) return new ArrayList<>();
            Type type = new TypeToken<ArrayList<LapRecord>>(){}.getType();
            List<LapRecord> records = gson.fromJson(json, type);
            LogUtils.log("【LapRepository】分段记录已加载，数量：" + (records != null ? records.size() : 0));
            return records != null ? records : new ArrayList<>();
        } catch (Exception e) {
            LogUtils.log("【LapRepository.loadLapRecords】加载失败：" + e.getMessage());
            android.util.Log.e("LapRepository", "加载分段记录异常", e);
            return new ArrayList<>();
        }
    }

    // 【2025-11-22 06:05】新增：导出分段记录到 Excel
    // 功能作用：调用工具类执行导出，并记录结果
    // 新增时间：2025年11月22日 06:05
    public boolean exportToExcel(@NonNull Uri uri, @NonNull List<LapRecord> records) {
        try {
            boolean success = ExcelExportUtil.exportLapRecordsToExcel(context, uri, records);
            LogUtils.log("【LapRepository.exportToExcel】导出" + (success ? "成功" : "失败"));
            return success;
        } catch (Exception e) {
            LogUtils.log("【LapRepository.exportToExcel】异常：" + e.getMessage());
            android.util.Log.e("LapRepository", "导出Excel异常", e);
            return false;
        }
    }
}