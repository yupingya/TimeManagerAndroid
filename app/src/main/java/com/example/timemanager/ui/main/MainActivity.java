// 【2025-11-22 15:00】MVVM 重构终极版：MainActivity - 完全保留原有 UI 逻辑，仅从 ViewModel 同步状态
// 功能作用：确保日夜模式、计时器、弹窗样式 100% 与你原有行为一致
// 修改时间：2025年11月22日 15:00
package com.example.timemanager.ui.main;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.fragment.app.Fragment;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.example.timemanager.R;
import com.example.timemanager.data.model.LapRecord;
import com.example.timemanager.ui.adapter.LapAdapter;
import com.example.timemanager.ui.dialog.InputDialogFragment;
import com.example.timemanager.util.ColorUtils;
import com.example.timemanager.util.DaemonManager;
import com.example.timemanager.util.LogUtils;
import com.example.timemanager.viewmodel.TimerViewModel;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements InputDialogFragment.InputDialogListener {

    // 【2025-11-22 15:02】保留你原有的所有 UI 控件引用（严格使用 XML ID）
    private LinearLayout mainLayout;
    private TextView lblWeekday, lblSystemDate, lblSystemTime, lblTime;
    private Button btnStartPause, btnLap, btnReset, btnExport, btnMode;
    private LinearLayout lapHeaderRow;
    private RecyclerView recyclerViewLaps;
    private LapAdapter lapAdapter;

    // 【2025-11-22 15:03】保留你原有的 Handler 和 Runnable（计时器由 Activity 管理）
    private Handler handler = new Handler();
    private TimerViewModel viewModel;
    private boolean isNight = false; // 主题状态仍由 Activity 管理

    // 【2025-11-22 15:04】完全复用你原有的 updateTimerRunnable（关键！）
    private final Runnable updateTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (Boolean.TRUE.equals(viewModel.getIsRunning().getValue())) {
                long currentElapsed = System.currentTimeMillis() - viewModel.getStartTimeMillis();
                viewModel.updateElapsed(currentElapsed);
                lblTime.setText(LapRecord.formatTime(currentElapsed));
                handler.postDelayed(this, 10);
            }
        }
    };

    private final Runnable updateSystemTimeRunnable = new Runnable() {
        @Override
        public void run() {
            updateSystemTime();
            handler.postDelayed(this, 100);
        }
    };

    private ActivityResultLauncher<String> fileSaverLauncher;

    // 【2025-11-22 15:05】新增：供 InputDialogFragment 查询当前主题状态
    // 功能作用：确保弹窗样式与主界面一致
    // 新增时间：2025年11月22日 15:05
    public boolean isNightMode() {
        return isNight;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 【2025-11-22 15:06】初始化 ViewModel
        viewModel = new ViewModelProvider(this).get(TimerViewModel.class);

        setContentView(R.layout.activity_main);

        // 【2025-11-22 15:07】查找控件（完全复用你原有 ID）
        mainLayout = findViewById(R.id.main_layout);
        lblWeekday = findViewById(R.id.lblWeekday);
        lblSystemDate = findViewById(R.id.lblSystemDate);
        lblSystemTime = findViewById(R.id.lblSystemTime);
        lblTime = findViewById(R.id.lblTime);
        btnStartPause = findViewById(R.id.btnStartPause);
        btnLap = findViewById(R.id.btnLap);
        btnReset = findViewById(R.id.btnReset);
        btnExport = findViewById(R.id.btnExport);
        btnMode = findViewById(R.id.btnMode);
        lapHeaderRow = findViewById(R.id.lap_header_row);
        recyclerViewLaps = findViewById(R.id.recyclerViewLaps);

        // 【2025-11-22 15:08】初始化 RecyclerView
        lapAdapter = new LapAdapter(this, new ArrayList<>());
        recyclerViewLaps.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewLaps.setAdapter(lapAdapter);

        // 【2025-11-22 15:09】注册文件保存器
        fileSaverLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/vnd.ms-excel"),
                this::writeFileToUri
        );

        // 【2025-11-22 15:10】设置点击监听器
        btnStartPause.setOnClickListener(v -> viewModel.toggleStartPause());
        btnLap.setOnClickListener(v -> recordLap());
        btnReset.setOnClickListener(v -> viewModel.resetTimer());
        btnExport.setOnClickListener(v -> exportData());
        btnMode.setOnClickListener(v -> toggleMode());

        // 【2025-11-22 15:11】从 ViewModel 加载初始状态
        Boolean initialNight = viewModel.getIsNightMode().getValue();
        isNight = (initialNight != null) ? initialNight : false;
        if (isNight) {
            applyDarkMode();
        } else {
            applyLightMode();
        }

        // 【2025-11-22 15:12】启动保活服务
        DaemonManager.startDaemonService(this);
        LogUtils.log("用户启动应用程序：当前为" + (isNight ? "黑夜" : "白天") + "模式");

        // 【2025-11-22 15:13】观察 ViewModel 状态变化，同步 UI
        viewModel.getIsRunning().observe(this, running -> {
            if (running != null) {
                if (running) {
                    btnStartPause.setText(R.string.btn_pause);
                    handler.post(updateTimerRunnable);
                } else {
                    btnStartPause.setText(R.string.btn_start);
                    handler.removeCallbacks(updateTimerRunnable);
                }
                btnLap.setEnabled(running);
            }
        });

        viewModel.getElapsedMillis().observe(this, elapsed -> {
            if (elapsed != null) {
                lblTime.setText(LapRecord.formatTime(elapsed));
            }
        });

        viewModel.getLapRecords().observe(this, records -> {
            if (records != null) {
                lapAdapter.updateRecords(records);
                recyclerViewLaps.scrollToPosition(records.size() - 1);
            }
        });
    }

    // 【2025-11-22 15:14】完全复用你原有的 toggleMode / applyThemeColors
    private void toggleMode() {
        isNight = !isNight;
        viewModel.toggleNightMode();
        if (isNight) {
            applyDarkMode();
        } else {
            applyLightMode();
        }
        LogUtils.log("用户执行了“切换日夜模式”功能：当前模式为" + (isNight ? "黑夜" : "白天"));
    }

    private void applyDarkMode() {
        isNight = true;
        applyThemeColors(true);
    }

    private void applyLightMode() {
        isNight = false;
        applyThemeColors(false);
    }

    private void applyThemeColors(boolean isNightMode) {
        int colorSurface = ColorUtils.getThemeColor(this, "colorSurface", isNightMode);
        int colorOnSurface = ColorUtils.getThemeColor(this, "colorOnSurface", isNightMode);
        int colorPrimary = ColorUtils.getThemeColor(this, "colorPrimary", isNightMode);
        int colorOnPrimary = ColorUtils.getThemeColor(this, "colorOnPrimary", isNightMode);

        mainLayout.setBackgroundColor(colorSurface);
        recyclerViewLaps.setBackgroundColor(colorSurface);
        lblWeekday.setTextColor(colorOnSurface);
        lblSystemDate.setTextColor(colorOnSurface);
        lblSystemTime.setTextColor(colorOnSurface);
        lblTime.setTextColor(colorOnSurface);
        for (Button button : new Button[]{btnStartPause, btnLap, btnReset, btnExport, btnMode}) {
            ViewCompat.setBackgroundTintList(button, android.content.res.ColorStateList.valueOf(colorPrimary));
            button.setTextColor(colorOnPrimary);
        }
        lapHeaderRow.setBackgroundColor(colorSurface);
        setLapHeaderTextColor(lapHeaderRow, colorOnSurface);
        if (lapAdapter != null) {
            lapAdapter.setNightMode(isNightMode);
        }
        btnMode.setText(isNightMode ? R.string.btn_mode_day : R.string.btn_mode_night);
    }

    private void setLapHeaderTextColor(View headerView, int color) {
        if (headerView instanceof android.view.ViewGroup) {
            android.view.ViewGroup viewGroup = (android.view.ViewGroup) headerView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(color);
                } else if (child instanceof android.view.ViewGroup) {
                    setLapHeaderTextColor(child, color);
                }
            }
        }
    }

    // 【2025-11-22 15:15】完全复用你原有的系统时间更新逻辑
    private void updateSystemTime() {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Locale chineseLocale = Locale.CHINA;
        String[] weekdays = {"", "星期日", "星期一", "星期二", "星期三", "星期四", "星期五", "星期六"};
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.setTime(now);
        int dayOfWeek = cal.get(java.util.Calendar.DAY_OF_WEEK);
        lblWeekday.setText(dayOfWeek >= 1 && dayOfWeek <= 7 ? weekdays[dayOfWeek] : "");
        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy年MM月dd日", chineseLocale);
        lblSystemDate.setText(dateFormatter.format(now));
        SimpleDateFormat clockFormatter = new SimpleDateFormat("H:mm:ss", chineseLocale);
        String formattedTime = clockFormatter.format(now);
        long centiseconds = (nowMillis % 1000) / 10;
        lblSystemTime.setText(String.format(Locale.getDefault(), "%s.%02d", formattedTime, centiseconds));
    }

    // 【2025-11-22 15:16】完全复用你原有的 recordLap 逻辑
    private void recordLap() {
        if (!Boolean.TRUE.equals(viewModel.getIsRunning().getValue())) {
            Toast.makeText(this, R.string.toast_start_first, Toast.LENGTH_SHORT).show();
            return;
        }

        viewModel.toggleStartPause(); // 暂停计时

        long currentElapsed = viewModel.getElapsedMillis().getValue() != null ? viewModel.getElapsedMillis().getValue() : 0L;
        long currentLapTime = currentElapsed - viewModel.getLastLapEndElapsedMillis();
        long totalAccumulated = viewModel.getTotalLapAccumulatedMillis() + currentLapTime;

        SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String startTimeStr = timeFormatter.format(new Date(viewModel.getStartTimeForLap()));
        String recordTimeStr = timeFormatter.format(new Date(System.currentTimeMillis()));
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(System.currentTimeMillis()));

        InputDialogFragment dialog = new InputDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString("dateStr", dateStr);
        bundle.putLong("currentLapTime", currentLapTime);
        bundle.putLong("totalLapAccumulatedMillis", totalAccumulated);
        bundle.putString("startTimeStr", startTimeStr);
        bundle.putString("recordTimeStr", recordTimeStr);
        dialog.setArguments(bundle);
        dialog.show(getSupportFragmentManager(), "InputDialogFragment");
    }

    // 【2025-11-22 15:17】完全复用你原有的导出逻辑
    private void exportData() {
        if (viewModel.getLapRecords().getValue() == null || viewModel.getLapRecords().getValue().isEmpty()) {
            LogUtils.log("没有可导出的记录。");
            Toast.makeText(this, R.string.toast_no_records, Toast.LENGTH_SHORT).show();
            return;
        }
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String filename = "TimeManager_" + sdf.format(new Date()) + ".xls";
        try {
            fileSaverLauncher.launch(filename);
        } catch (Exception e) {
            LogUtils.log("系统发生“文件保存器启动失败”事件：" + e.getMessage());
            Toast.makeText(this, R.string.toast_file_saver_fail, Toast.LENGTH_SHORT).show();
        }
    }

    // 【2025-11-22 15:18】完全复用你原有的 onFinishInputDialog
    @Override
    public void onFinishInputDialog(String category, String detail) {
        // 【2025-11-22 15:36】优化写法：直接使用 var 或明确类型
        Fragment fragment = getSupportFragmentManager().findFragmentByTag("InputDialogFragment");
        if (fragment == null || !(fragment instanceof InputDialogFragment)) return;
        Bundle bundle = fragment.getArguments();
        if (bundle == null) return;

        String dateStr = bundle.getString("dateStr");
        long currentLapTime = bundle.getLong("currentLapTime");
        long totalLapAccumulated = bundle.getLong("totalLapAccumulatedMillis");
        String startTimeStr = bundle.getString("startTimeStr");
        String recordTimeStr = bundle.getString("recordTimeStr");

        LapRecord newRecord = new LapRecord(
                viewModel.getLapIndex() + 1,
                dateStr,
                currentLapTime,
                totalLapAccumulated,
                startTimeStr,
                recordTimeStr,
                System.currentTimeMillis(),
                category,
                detail
        );
        viewModel.addLapRecord(newRecord);
    }

    // 【2025-11-22 15:19】完全复用你原有的 writeFileToUri
    private void writeFileToUri(Uri uri) {
        if (uri == null) return;
        boolean success = com.example.timemanager.util.ExcelExportUtil.exportLapRecordsToExcel(this, uri, viewModel.getLapRecords().getValue());
        if (success) {
            LogUtils.log("数据已成功导出: " + uri.toString());
            Toast.makeText(this, R.string.toast_export_success, Toast.LENGTH_SHORT).show();
        } else {
            LogUtils.log("系统发生“导出失败”事件：Excel 文件写入异常");
            Toast.makeText(this, R.string.toast_export_fail, Toast.LENGTH_SHORT).show();
        }
    }

    // 【2025-11-22 15:20】关键修复：正确管理 Runnable 生命周期
    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updateSystemTimeRunnable);
        // 如果正在运行，恢复计时器
        if (Boolean.TRUE.equals(viewModel.getIsRunning().getValue())) {
            handler.post(updateTimerRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateSystemTimeRunnable);
        handler.removeCallbacks(updateTimerRunnable);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        DaemonManager.stopDaemonService(this);
        LogUtils.log("应用程序正常退出（非系统强杀）");
        getSharedPreferences("TimeManagerPrefs", Context.MODE_PRIVATE)
                .edit().putBoolean("wasCleanExit", true).apply();
    }
}