package com.example.timemanager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

/**
 * MainActivity
 * 修改时间：2025-11-19 15:15 - 深度重构：
 * 1. 修复后台切换导致的计时重置和负数溢出问题。
 * 2. 移除冗余的 formatTime 方法，统一调用 LapRecord.formatTime。
 * 3. 优化 LapRecord 对象创建逻辑，传递原始 Long 数据。
 */
public class MainActivity extends AppCompatActivity implements InputDialogFragment.InputDialogListener {

    private static final String TAG = "TimeManagerApp";

    // UI 控件变量
    private LinearLayout mainLayout;
    private TextView lblWeekday;
    private TextView lblSystemDate;
    private TextView lblSystemTime;
    private TextView lblTime;
    private Button btnStartPause;
    private Button btnLap;
    private Button btnReset;
    private Button btnExport;
    private Button btnMode;
    private LinearLayout lapHeaderRow;
    private androidx.recyclerview.widget.RecyclerView recyclerViewLaps;
    private LapAdapter lapAdapter;

    // 功能相关变量
    private Handler handler;
    private Timer systemTimeTimer;
    private long startTimeMillis; // 计时开始基准时间（每次开始/继续时更新）
    private long elapsedMillis; // 累计计时时间（不包括暂停时间）
    private long pauseOffsetMillis; // 暂停时的累计时间
    private boolean isRunning = false;
    private List<LapRecord> lapRecords;
    private int lapIndex = 0;
    private boolean isNight = false;
    private SharedPreferences sharedPreferences;

    // 分隔时间和累计分隔相关新增变量（对应C#软件逻辑）
    private long startTimeForLap; // 每次开始计时的时间（用于记录"开始时间"字段和计算分段时间）
    private long lastLapEndElapsedMillis = 0; // 上次分隔结束时的计时时间（用于计算本次分隔时间）
    private long totalLapAccumulatedMillis = 0; // 累计分隔时间总和（对应"分隔累计"列）

    // 文件导出相关
    private ActivityResultLauncher<String> fileSaverLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        loadModeState();

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 启动保活服务
        DaemonManager.startDaemonService(this);

        handler = new Handler();
        loadState();

        // 查找 UI 控件
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

        // 初始化RecyclerView
        lapRecords = lapRecords != null ? lapRecords : new ArrayList<>();
        recyclerViewLaps.setLayoutManager(new androidx.recyclerview.widget.LinearLayoutManager(this));
        lapAdapter = new LapAdapter(this, lapRecords);
        recyclerViewLaps.setAdapter(lapAdapter);

        // 根据加载的模式状态，手动应用主题颜色
        if (isNight) {
            applyDarkMode();
        } else {
            applyLightMode();
        }

        // 设置点击事件监听器
        btnStartPause.setOnClickListener(v -> toggleStartPause());
        btnLap.setOnClickListener(v -> recordLap());
        btnReset.setOnClickListener(v -> resetTimer());
        btnExport.setOnClickListener(v -> exportData());
        btnMode.setOnClickListener(v -> toggleMode());

        // 注册文件保存器
        fileSaverLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/vnd.ms-excel"),
                this::writeFileToUri);

        initSystemTimeTimer();

        // 修改时间：2025-11-19 15:18 - 保持后台恢复逻辑的修正
        // 关键点：Activity重建时，不调用 startTimer() 避免基准时间 startTimeMillis 被错误重置
        if (isRunning) {
            btnStartPause.setText(R.string.btn_pause);
            handler.post(updateTimerRunnable);
        } else {
            elapsedMillis = pauseOffsetMillis;
            updateTimerText();
            btnStartPause.setText(R.string.btn_start);
        }
    }

    private void toggleMode() {
        isNight = !isNight;

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isNight", isNight);
        editor.apply();

        if (isNight) {
            applyDarkMode();
        } else {
            applyLightMode();
        }
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

        for (Button button : new Button[] {btnStartPause, btnLap, btnReset, btnExport, btnMode}) {
            ViewCompat.setBackgroundTintList(button, ColorStateList.valueOf(colorPrimary));
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
        if (headerView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) headerView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(color);
                } else if (child instanceof ViewGroup) {
                    setLapHeaderTextColor(child, color);
                }
            }
        }
    }

    private void updateSystemTime() {
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        Locale chineseLocale = Locale.CHINA;

        SimpleDateFormat weekdayFormatter = new SimpleDateFormat("E", chineseLocale);
        lblWeekday.setText(weekdayFormatter.format(now));

        SimpleDateFormat dateFormatter = new SimpleDateFormat("yyyy年MM月dd日", chineseLocale);
        lblSystemDate.setText(dateFormatter.format(now));

        SimpleDateFormat clockFormatter = new SimpleDateFormat("H:mm:ss", chineseLocale);
        String formattedTime = clockFormatter.format(now);

        long centiseconds = (nowMillis % 1000) / 10;

        lblSystemTime.setText(String.format(Locale.getDefault(), "%s.%02d", formattedTime, centiseconds));
    }

    private void initSystemTimeTimer() {
        if (systemTimeTimer != null) {
            systemTimeTimer.cancel();
        }
        systemTimeTimer = new Timer();
        systemTimeTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                handler.post(MainActivity.this::updateSystemTime);
            }
        }, 0, 10);
    }

    private void startTimer() {
        isRunning = true;
        startTimeMillis = System.currentTimeMillis() - elapsedMillis;
        startTimeForLap = System.currentTimeMillis() - elapsedMillis;
        btnStartPause.setText(R.string.btn_pause);
        handler.post(updateTimerRunnable);
    }

    private void toggleStartPause() {
        if (isRunning) {
            isRunning = false;
            pauseOffsetMillis = elapsedMillis;
            handler.removeCallbacks(updateTimerRunnable);
            btnStartPause.setText(R.string.btn_start);
        } else {
            startTimer();
        }
        saveState();
    }

    private void recordLap() {
        if (!isRunning) {
            Toast.makeText(this, R.string.toast_start_first, Toast.LENGTH_SHORT).show();
            return;
        }

        isRunning = false;
        handler.removeCallbacks(updateTimerRunnable);
        btnStartPause.setText(R.string.btn_start);
        pauseOffsetMillis = elapsedMillis;

        long currentElapsed = elapsedMillis;
        long currentLapTime = currentElapsed - lastLapEndElapsedMillis;
        lastLapEndElapsedMillis = currentElapsed;
        totalLapAccumulatedMillis += currentLapTime;

        SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String startTimeStr = timeFormatter.format(new Date(startTimeForLap));
        String recordTimeStr = timeFormatter.format(new Date(System.currentTimeMillis()));
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(System.currentTimeMillis()));

        InputDialogFragment dialog = new InputDialogFragment();
        Bundle bundle = new Bundle();
        bundle.putString("dateStr", dateStr);
        bundle.putLong("currentLapTime", currentLapTime);
        bundle.putLong("totalLapAccumulatedMillis", totalLapAccumulatedMillis);
        bundle.putString("startTimeStr", startTimeStr);
        bundle.putString("recordTimeStr", recordTimeStr);
        dialog.setArguments(bundle);
        dialog.show(getSupportFragmentManager(), "InputDialogFragment");
    }

    private void resetTimer() {
        if (isRunning) {
            toggleStartPause();
        }
        startTimeMillis = 0;
        elapsedMillis = 0;
        pauseOffsetMillis = 0;
        lapIndex = 0;
        isRunning = false;
        startTimeForLap = 0;
        lastLapEndElapsedMillis = 0;
        totalLapAccumulatedMillis = 0;
        lapRecords.clear();
        lapAdapter.notifyDataSetChanged();
        updateTimerText();
        btnStartPause.setText(R.string.btn_start);
        saveState();
    }

    private final Runnable updateTimerRunnable = new Runnable() {
        @Override
        public void run() {
            elapsedMillis = System.currentTimeMillis() - startTimeMillis;
            updateTimerText();
            handler.postDelayed(this, 10);
        }
    };

    // 修改时间：2025-11-19 15:20 - 使用 LapRecord 中的静态方法进行格式化
    private void updateTimerText() {
        lblTime.setText(LapRecord.formatTime(elapsedMillis));
    }

    private void saveState() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isRunning", isRunning);
        editor.putLong("pauseOffsetMillis", pauseOffsetMillis);
        editor.putLong("startTimeMillis", startTimeMillis);
        editor.putInt("lapIndex", lapIndex);
        editor.putBoolean("isNight", isNight);
        editor.putLong("startTimeForLap", startTimeForLap);
        editor.putLong("lastLapEndElapsedMillis", lastLapEndElapsedMillis);
        editor.putLong("totalLapAccumulatedMillis", totalLapAccumulatedMillis);

        Gson gson = new Gson();
        String json = gson.toJson(lapRecords);
        editor.putString("lapRecords", json);

        editor.apply();
        Log.d(TAG, "State saved. isRunning: " + isRunning + ", Records: " + lapRecords.size());
    }

    private void loadModeState() {
        isNight = sharedPreferences.getBoolean("isNight", false);
    }

    private void loadState() {
        isRunning = sharedPreferences.getBoolean("isRunning", false);
        pauseOffsetMillis = sharedPreferences.getLong("pauseOffsetMillis", 0);
        startTimeMillis = sharedPreferences.getLong("startTimeMillis", 0);
        lapIndex = sharedPreferences.getInt("lapIndex", 0);
        isNight = sharedPreferences.getBoolean("isNight", false);
        startTimeForLap = sharedPreferences.getLong("startTimeForLap", 0);
        lastLapEndElapsedMillis = sharedPreferences.getLong("lastLapEndElapsedMillis", 0);
        totalLapAccumulatedMillis = sharedPreferences.getLong("totalLapAccumulatedMillis", 0);

        String json = sharedPreferences.getString("lapRecords", null);
        Gson gson = new Gson();
        Type type = new TypeToken<ArrayList<LapRecord>>() {}.getType();
        lapRecords = gson.fromJson(json, type);

        if (lapRecords == null) {
            lapRecords = new ArrayList<>();
        }

        Log.d(TAG, "State loaded. isRunning: " + isRunning + ", Records: " + lapRecords.size());
    }

    private void exportData() {
        if (lapRecords.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_records, Toast.LENGTH_SHORT).show();
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
        String filename = "TimeManager_" + sdf.format(new Date()) + ".xls";

        try {
            fileSaverLauncher.launch(filename);
        } catch (Exception e) {
            Log.e(TAG, "File Saver Launcher failed", e);
            Toast.makeText(this, R.string.toast_file_saver_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void writeFileToUri(Uri uri) {
        if (uri == null) {
            return;
        }
        boolean success = ExcelExportUtil.exportLapRecordsToExcel(this, uri, lapRecords);
        if (!success) {
            Log.e(TAG, "Export failed");
        }
    }

    /**
     * 输入对话框完成回调
     * 修改时间：2025-11-19 15:25 - 使用更新后的 LapRecord 构造函数，传递 long 原始值
     */
    @Override
    public void onFinishInputDialog(String category, String detail) {
        Bundle bundle = getSupportFragmentManager().findFragmentByTag("InputDialogFragment").getArguments();
        if (bundle == null) return;

        String dateStr = bundle.getString("dateStr");
        long currentLapTime = bundle.getLong("currentLapTime");
        long totalLapAccumulated = bundle.getLong("totalLapAccumulatedMillis");
        String startTimeStr = bundle.getString("startTimeStr");
        String recordTimeStr = bundle.getString("recordTimeStr");

        lapIndex++;

        // 直接传入 long 类型数据 (currentLapTime, totalLapAccumulated)
        // LapRecord 内部会自动处理格式化
        LapRecord newRecord = new LapRecord(
                lapIndex,
                dateStr,
                currentLapTime,         // 原始间隔时间 (long)
                totalLapAccumulated,    // 原始累计时间 (long)
                startTimeStr,
                recordTimeStr,
                System.currentTimeMillis(),
                category,
                detail
        );

        lapRecords.add(newRecord);
        lapAdapter.notifyItemInserted(lapRecords.size() - 1);
        recyclerViewLaps.scrollToPosition(lapRecords.size() - 1);

        lastLapEndElapsedMillis = elapsedMillis;

        saveState();
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (systemTimeTimer != null) {
            systemTimeTimer.cancel();
        }
        handler.removeCallbacksAndMessages(null);
        DaemonManager.stopDaemonService(this);
    }
}