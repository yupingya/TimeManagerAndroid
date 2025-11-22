// 【2025-11-22 06:05】新增：TimerViewModel - 封装计时器核心业务逻辑
// 功能作用：管理计时状态、分段记录、模式切换，解耦 MainActivity 复杂逻辑
// 新增时间：2025年11月22日 06:05
package com.example.timemanager.viewmodel;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.timemanager.data.model.LapRecord;
import com.example.timemanager.data.repository.LapRepository;
import com.example.timemanager.util.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import androidx.annotation.NonNull; // 确保有这个导入，以支持 @NonNull
import com.example.timemanager.data.model.LapRecord;
import com.example.timemanager.util.LogUtils;

public class TimerViewModel extends AndroidViewModel {
    private final LapRepository lapRepository;
    private final SharedPreferences prefs;

    // UI State
    private final MutableLiveData<Boolean> isRunning = new MutableLiveData<>(false);
    private final MutableLiveData<Long> elapsedMillis = new MutableLiveData<>(0L);
    private final MutableLiveData<List<LapRecord>> lapRecords = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<Boolean> isNightMode = new MutableLiveData<>(false);

    // Internal state
    private long startTimeMillis = 0;
    private long pauseOffsetMillis = 0;
    private long startTimeForLap = 0;
    private long lastLapEndElapsedMillis = 0;
    private long totalLapAccumulatedMillis = 0;
    private int lapIndex = 0;

    // 【2025-11-22 06:07】新增构造函数
    // 功能作用：初始化仓库、偏好设置，并加载上次状态
    // 新增时间：2025年11月22日 06:07
    public TimerViewModel(@NonNull Application application) {
        super(application);
        this.lapRepository = new LapRepository(application);
        this.prefs = application.getSharedPreferences("TimeManagerPrefs", Application.MODE_PRIVATE);
        loadState();
    }

    // 【2025-11-22 06:08】新增：获取是否正在运行（供 View 观察）
    // 功能作用：暴露 LiveData 供 MainActivity 监听
    // 新增时间：2025年11月22日 06:08
    public LiveData<Boolean> getIsRunning() { return isRunning; }

    // 【2025-11-22 06:09】新增：获取已过时间（供 View 观察）
    // 功能作用：暴露 LiveData 供 MainActivity 更新时间显示
    // 新增时间：2025年11月22日 06:09
    public LiveData<Long> getElapsedMillis() { return elapsedMillis; }

    // 【2025-11-22 06:10】新增：获取分段记录列表（供 View 观察）
    // 功能作用：供 RecyclerView Adapter 更新数据
    // 新增时间：2025年11月22日 06:10
    public LiveData<List<LapRecord>> getLapRecords() { return lapRecords; }

    // 【2025-11-22 06:11】新增：获取日夜模式状态（供 View 观察）
    // 功能作用：触发主题切换
    // 新增时间：2025年11月22日 06:11
    public LiveData<Boolean> getIsNightMode() { return isNightMode; }

    // 【2025-11-22 06:12】新增：开始/暂停切换
    // 功能作用：统一处理启动和暂停逻辑
    // 新增时间：2025年11月22日 06:12
    public void toggleStartPause() {
        if (Boolean.TRUE.equals(isRunning.getValue())) {
            pauseTimer();
        } else {
            startTimer();
        }
        saveState();
    }

    // 【2025-11-22 06:13】私有方法：开始计时
    // 功能作用：设置起始时间戳
    // 新增时间：2025年11月22日 06:13
    private void startTimer() {
        isRunning.setValue(true);
        startTimeMillis = System.currentTimeMillis() - (elapsedMillis.getValue() != null ? elapsedMillis.getValue() : 0L);
        startTimeForLap = System.currentTimeMillis();
        LogUtils.log("【TimerViewModel】开始计时");
    }

    // 【2025-11-22 06:14】私有方法：暂停计时
    // 功能作用：记录暂停偏移量
    // 新增时间：2025年11月22日 06:14
    private void pauseTimer() {
        isRunning.setValue(false);
        Long current = elapsedMillis.getValue();
        if (current != null) {
            pauseOffsetMillis = current;
        }
        LogUtils.log("【TimerViewModel】暂停计时");
    }

    // 【2025-11-22 06:15】新增：重置计时器
    // 功能作用：清空所有状态和记录
    // 新增时间：2025年11月22日 06:15
    public void resetTimer() {
        isRunning.setValue(false);
        elapsedMillis.setValue(0L);
        pauseOffsetMillis = 0;
        startTimeMillis = 0;
        lapIndex = 0;
        startTimeForLap = 0;
        lastLapEndElapsedMillis = 0;
        totalLapAccumulatedMillis = 0;
        lapRecords.setValue(new ArrayList<>());
        lapRepository.saveLapRecords(new ArrayList<>());
        LogUtils.log("【TimerViewModel】重置计时器");
        saveState();
    }

    // 【2025-11-22 16:10】修复：正确维护 totalLapAccumulatedMillis 累计值
// 功能作用：确保“间隔累计”列显示各分段时间的真实累加结果
// 修改时间：2025年11月22日 16:10
    public void addLapRecord(LapRecord record) {
        List<LapRecord> current = new ArrayList<>(lapRecords.getValue());
        current.add(record);
        lapRecords.setValue(current);
        lapIndex = current.size();

        // 【2025-11-22 16:11】关键修复：从 record 的 interval 字段反解析出毫秒值并累加
        // 注意：此方法仅用于兼容现有架构，理想情况应传入原始 long 值
        long intervalMillis = parseTimeToMillis(record.getInterval());
        totalLapAccumulatedMillis += intervalMillis;

        Long currentElapsed = elapsedMillis.getValue();
        if (currentElapsed != null) {
            lastLapEndElapsedMillis = currentElapsed;
        }

        lapRepository.saveLapRecords(current);
        LogUtils.log("【TimerViewModel】新增分段记录：" + record.getCategory() +
                " | 间隔=" + record.getInterval() +
                " | 累计=" + record.getLapTime() +
                " | 内部累计值更新为=" + totalLapAccumulatedMillis);
        saveState();
    }

    // 【2025-11-22 16:12】新增：将格式化的时间字符串（如 "1:02:03.45"）解析为毫秒
// 功能作用：支持从 LapRecord 的 interval 字段反推原始毫秒值，用于累计计算
// 新增时间：2025年11月22日 16:12
    private long parseTimeToMillis(String timeStr) {
        try {
            String[] parts = timeStr.split(":");
            if (parts.length == 3) {
                String[] secCenti = parts[2].split("\\.");
                long hours = Long.parseLong(parts[0]);
                long minutes = Long.parseLong(parts[1]);
                long seconds = Long.parseLong(secCenti[0]);
                long centiseconds = secCenti.length > 1 ? Long.parseLong(secCenti[1]) : 0;
                return ((hours * 3600 + minutes * 60 + seconds) * 1000) + (centiseconds * 10);
            }
        } catch (Exception e) {
            LogUtils.log("【TimerViewModel.parseTimeToMillis】解析时间字符串失败: " + timeStr + " | 错误: " + e.getMessage());
        }
        return 0;
    }

    // 【2025-11-22 06:17】新增：切换日夜模式
    // 功能作用：更新主题并持久化
    // 新增时间：2025年11月22日 06:17
    public void toggleNightMode() {
        boolean newMode = !isNightMode.getValue();
        isNightMode.setValue(newMode);
        prefs.edit().putBoolean("isNight", newMode).apply();
        LogUtils.log("【TimerViewModel】切换日夜模式：" + (newMode ? "黑夜" : "白天"));
    }

    // 【2025-11-22 06:18】新增：导出数据
    // 功能作用：调用 Repository 执行导出
    // 新增时间：2025年11月22日 06:18
    public boolean exportData(android.net.Uri uri) {
        List<LapRecord> records = lapRecords.getValue();
        if (records == null || records.isEmpty()) {
            LogUtils.log("【TimerViewModel.exportData】无记录可导出");
            return false;
        }
        return lapRepository.exportToExcel(uri, records);
    }

    // 【2025-11-22 06:19】私有方法：保存状态到 SharedPreferences
    // 功能作用：恢复计时器状态
    // 新增时间：2025年11月22日 06:19
    private void saveState() {
        prefs.edit()
                .putBoolean("isRunning", isRunning.getValue())
                .putLong("pauseOffsetMillis", pauseOffsetMillis)
                .putLong("startTimeMillis", startTimeMillis)
                .putInt("lapIndex", lapIndex)
                .putLong("startTimeForLap", startTimeForLap)
                .putLong("lastLapEndElapsedMillis", lastLapEndElapsedMillis)
                .putLong("totalLapAccumulatedMillis", totalLapAccumulatedMillis)
                .apply();
    }

    // 【2025-11-22 06:20】私有方法：从 SharedPreferences 加载状态
    // 功能作用：应用启动时恢复上次状态
    // 新增时间：2025年11月22日 06:20
    private void loadState() {
        isRunning.setValue(prefs.getBoolean("isRunning", false));
        pauseOffsetMillis = prefs.getLong("pauseOffsetMillis", 0);
        startTimeMillis = prefs.getLong("startTimeMillis", 0);
        lapIndex = prefs.getInt("lapIndex", 0);
        startTimeForLap = prefs.getLong("startTimeForLap", 0);
        lastLapEndElapsedMillis = prefs.getLong("lastLapEndElapsedMillis", 0);
        totalLapAccumulatedMillis = prefs.getLong("totalLapAccumulatedMillis", 0);
        isNightMode.setValue(prefs.getBoolean("isNight", false));
        lapRecords.setValue(lapRepository.loadLapRecords());
    }

    // 【2025-11-22 18:45】新增：导入分段记录数据
    // 功能作用：将导入的数据设置为系统当前数据，并重置计时器状态到最大累计时间。
    // 新增时间：2025年11月22日 18:45
    public void importRecords(@NonNull List<LapRecord> importedRecords, long maxElapsedMillis) {
        // 1. 停止并重置当前计时状态
        if (Boolean.TRUE.equals(isRunning.getValue())) {
            // 确保计时器是停止状态
            isRunning.setValue(false);
            LogUtils.log("【TimerViewModel】导入数据时：停止当前计时器运行状态。");
        }

        // 2. 更新内部状态：将计时时间设为最大累计时间
        pauseOffsetMillis = maxElapsedMillis;
        startTimeMillis = 0; // 重置开始时间
        lapIndex = importedRecords.size(); // 序号从下一条开始

        // maxElapsedMillis 就是导入数据的累计总时间
        totalLapAccumulatedMillis = maxElapsedMillis;
        lastLapEndElapsedMillis = maxElapsedMillis; // 下一次分段的起点是这个累计时间

        // 3. 更新 LiveData 和持久化
        lapRecords.setValue(importedRecords);
        elapsedMillis.setValue(maxElapsedMillis); // 更新 UI 上的计时器显示

        // 4. 将导入的数据保存到 SharedPreferences (相当于重置按钮的操作)
        lapRepository.saveAllLapRecords(importedRecords);
        saveState(); // 保存 TimerViewModel 内部状态

        LogUtils.log(String.format(Locale.getDefault(), "【TimerViewModel】数据导入完成。计时器已重置为累计时间：%s", LapRecord.formatTime(maxElapsedMillis)));
        android.util.Log.i("TimerViewModel", "Import finished. Timer reset to: " + LapRecord.formatTime(maxElapsedMillis));
    }

    // 【2025-11-22 06:21】新增：更新已过时间（供 Handler 调用）
    // 功能作用：刷新时间显示
    // 新增时间：2025年11月22日 06:21
    public void updateElapsed(long millis) {
        elapsedMillis.setValue(millis);
    }

    // 【2025-11-22 06:22】新增：获取内部状态（供 MainActivity 计算 lap 时间）
    // 功能作用：暴露必要字段给 View 层计算使用
    // 新增时间：2025年11月22日 06:22
    public long getStartTimeMillis() { return startTimeMillis; }
    public long getPauseOffsetMillis() { return pauseOffsetMillis; }
    public long getLastLapEndElapsedMillis() { return lastLapEndElapsedMillis; }
    public long getTotalLapAccumulatedMillis() { return totalLapAccumulatedMillis; }
    public long getStartTimeForLap() { return startTimeForLap; }
    public int getLapIndex() { return lapIndex; }


}