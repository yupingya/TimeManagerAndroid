package com.example.timemanager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.appcompat.app.AppCompatDelegate;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock; // 🌟 修正 1: 引入 SystemClock (修改时间：20251119 16:00)
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

public class MainActivity extends AppCompatActivity implements InputDialogFragment.InputDialogListener {

    private static final String TAG = "TimeManagerApp";

    // UI 控件变量
    private LinearLayout mainLayout;
    private TextView lblWeekday, lblSystemDate;
    private TextView lblTime;
    private Button btnStartPause, btnLap, btnReset, btnExport, btnMode;
    private LinearLayout lapHeaderRow;
    private RecyclerView recyclerViewLaps;
    private LapAdapter lapAdapter;

    // 计时器核心变量
    private Handler handler = new Handler();
    private Timer systemTimeTimer; // 用于更新系统时间
    private List<LapRecord> lapRecords;

    // 🌟 修正 2: 核心计时变量全部改为基于 SystemClock.elapsedRealtime() (修改时间：20251119 16:00)
    private long startTimeElapsedMillis = 0; // 计时开始时的 ELAPSED TIME (SystemClock.elapsedRealtime())
    private long totalPausedTimeElapsedMillis = 0; // 累计暂停时间（ELAPSED TIME）
    private long lastPauseTimeElapsedMillis = 0; // 上次暂停时的 ELAPSED TIME
    private long lastLapEndElapsedMillis = 0; // 上次分段结束时的总运行时间（ELAPSED TIME）

    private boolean isRunning = false;
    private boolean isNight = false; // 主题模式状态

    // 文件导出相关
    private ActivityResultLauncher<String> createDocumentLauncher;
    private SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()); // 用于系统时间
    private SimpleDateFormat recordTimeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()); // 用于记录时间

    // ====================================================================
    // 1. Activity 生命周期
    // ====================================================================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        initRecyclerView();
        initListeners();
        initExportLauncher();

        // 🌟 修正 3: 移除 AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        // 主题切换完全由 isNight 变量和 applyTheme 方法控制。 (修改时间：20251119 16:00)

        loadState();

        // 确保计时器在加载状态后立即开始更新（无论是计时器还是系统时间）
        startSystemTimeUpdater();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 🌟 修正 4: 恢复计时状态。如果 App 在后台被杀死，isRunning=true，这里会重新启动计时。 (修改时间：20251119 16:00)
        if (isRunning) {
            long timeSpentKilled = SystemClock.elapsedRealtime() - lastPauseTimeElapsedMillis;
            totalPausedTimeElapsedMillis += timeSpentKilled;
            // 注意：这里不需要修改 startTimeElapsedMillis，因为 elapsedRealtime() 是连续的。
            // 只需要确保如果处于运行状态，计时器恢复更新。
            startTimer();
            btnStartPause.setText(R.string.btn_pause);
        } else {
            // 如果是暂停状态，但进程被杀死，我们需要修正 totalPausedTimeElapsedMillis
            // 以便下次 start 时，能准确计算出上次暂停了多久。
            if(lastPauseTimeElapsedMillis > 0) {
                long timeSpentPaused = SystemClock.elapsedRealtime() - lastPauseTimeElapsedMillis;
                totalPausedTimeElapsedMillis += timeSpentPaused;
                lastPauseTimeElapsedMillis = SystemClock.elapsedRealtime(); // 更新暂停结束时间
            }
        }
        applyTheme();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 🌟 修正 5: 在 onPause 时更新 lastPauseTimeElapsedMillis，用于计算进程被杀死后流逝的真实时间 (修改时间：20251119 16:00)
        if (isRunning) {
            // 如果计时器在运行，记录当前的 elapsedRealtime() 作为潜在的 "被杀" 时间点
            // totalPausedTimeElapsedMillis 不需要更新
            lastPauseTimeElapsedMillis = SystemClock.elapsedRealtime();
        } else if (lastPauseTimeElapsedMillis > 0) {
            // 如果处于暂停状态，更新 totalPausedTimeElapsedMillis
            // 计入从上次暂停到本次 onPause 之间流逝的时间
            long timeSpentPaused = SystemClock.elapsedRealtime() - lastPauseTimeElapsedMillis;
            totalPausedTimeElapsedMillis += timeSpentPaused;
            lastPauseTimeElapsedMillis = SystemClock.elapsedRealtime();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        saveState();
        // 🌟 修正 6: 在 onStop 时，如果计时器正在运行，更新 lastPauseTimeElapsedMillis (修改时间：20251119 16:00)
        if (isRunning) {
            lastPauseTimeElapsedMillis = SystemClock.elapsedRealtime();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (systemTimeTimer != null) {
            systemTimeTimer.cancel();
        }
        handler.removeCallbacksAndMessages(null);
    }

    // ====================================================================
    // 2. 初始化方法
    // ====================================================================

    private void initViews() {
        mainLayout = findViewById(R.id.main_layout);
        lblWeekday = findViewById(R.id.lblWeekday);
        lblSystemDate = findViewById(R.id.lblSystemDate);
        lblTime = findViewById(R.id.lblTime);
        btnStartPause = findViewById(R.id.btnStartPause);
        btnLap = findViewById(R.id.btnLap);
        btnReset = findViewById(R.id.btnReset);
        btnExport = findViewById(R.id.btnExport);
        btnMode = findViewById(R.id.btnMode);
        lapHeaderRow = findViewById(R.id.lap_header_row);
        recyclerViewLaps = findViewById(R.id.recyclerViewLaps);
    }

    private void initRecyclerView() {
        lapRecords = new ArrayList<>();
        lapAdapter = new LapAdapter(this, lapRecords);
        recyclerViewLaps.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewLaps.setAdapter(lapAdapter);
    }

    private void initListeners() {
        btnStartPause.setOnClickListener(v -> toggleStartPause());
        btnLap.setOnClickListener(v -> handleLap());
        btnReset.setOnClickListener(v -> resetTimer());
        btnExport.setOnClickListener(v -> exportRecords());
        btnMode.setOnClickListener(v -> toggleMode());
    }

    private void initExportLauncher() {
        createDocumentLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("text/csv"), uri -> {
            if (uri != null) {
                writeCsvToFile(uri);
            } else {
                Toast.makeText(MainActivity.this, R.string.toast_file_saver_fail, Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ====================================================================
    // 3. 计时器核心逻辑
    // ====================================================================

    private Runnable updateTimeTask = new Runnable() {
        public void run() {
            if (isRunning) {
                updateTimerDisplay();
                handler.postDelayed(this, 10); // 每 10 毫秒刷新一次
            }
        }
    };

    private void startTimer() {
        if (!isRunning) {
            // 🌟 修正 7: 切换计时基准为 SystemClock.elapsedRealtime() (修改时间：20251119 16:00)
            if (startTimeElapsedMillis == 0) {
                // 第一次启动
                startTimeElapsedMillis = SystemClock.elapsedRealtime();
            } else {
                // 从暂停恢复
                long timeSpentPaused = SystemClock.elapsedRealtime() - lastPauseTimeElapsedMillis;
                totalPausedTimeElapsedMillis += timeSpentPaused;
            }

            isRunning = true;
            handler.post(updateTimeTask);
            btnStartPause.setText(R.string.btn_pause);
            saveState(); // 保存运行状态
        }
    }

    private void pauseTimer() {
        if (isRunning) {
            handler.removeCallbacks(updateTimeTask);
            isRunning = false;
            // 🌟 修正 8: 切换暂停时间基准 (修改时间：20251119 16:00)
            lastPauseTimeElapsedMillis = SystemClock.elapsedRealtime();
            btnStartPause.setText(R.string.btn_start);
            saveState(); // 保存暂停状态
        }
    }

    private void updateTimerDisplay() {
        // 🌟 修正 9: 基于 elapsedRealtime() 计算总运行时间 (修改时间：20251119 16:00)
        long elapsedMillis = SystemClock.elapsedRealtime() - startTimeElapsedMillis - totalPausedTimeElapsedMillis;

        // 确保时间不为负数 (尽管使用 elapsedRealtime() 不太可能)
        if (elapsedMillis < 0) elapsedMillis = 0;

        lblTime.setText(formatTime(elapsedMillis));
        updateSystemTimeDisplay();
    }

    private void startSystemTimeUpdater() {
        if (systemTimeTimer != null) {
            systemTimeTimer.cancel();
        }
        systemTimeTimer = new Timer();
        systemTimeTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // 由于 TimerTask 运行在非 UI 线程，必须使用 runOnUiThread
                runOnUiThread(() -> updateSystemTimeDisplay());
            }
        }, 0, 1000); // 每秒更新一次
    }

    private void updateSystemTimeDisplay() {
        long currentSystemTime = System.currentTimeMillis();
        // 显示星期几
        lblWeekday.setText(new SimpleDateFormat("E", Locale.getDefault()).format(new Date(currentSystemTime)));
        // 显示日期和时间
        lblSystemDate.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date(currentSystemTime)));
    }

    private void toggleStartPause() {
        if (isRunning) {
            pauseTimer();
        } else {
            startTimer();
        }
    }

    private void resetTimer() {
        pauseTimer();

        // 🌟 修正 10: 重置所有 elapsed 计时变量 (修改时间：20251119 16:00)
        startTimeElapsedMillis = 0;
        totalPausedTimeElapsedMillis = 0;
        lastPauseTimeElapsedMillis = 0;
        lastLapEndElapsedMillis = 0;

        lblTime.setText(getString(R.string.default_time));

        lapRecords.clear();
        lapAdapter.notifyDataSetChanged();

        saveState();
    }

    private void handleLap() {
        if (!isRunning && startTimeElapsedMillis == 0) {
            Toast.makeText(this, R.string.toast_start_first, Toast.LENGTH_SHORT).show();
            return;
        }

        // 暂停计时，以便用户可以输入分段信息
        pauseTimer();

        // 显示对话框
        InputDialogFragment dialog = new InputDialogFragment();
        dialog.show(getSupportFragmentManager(), "InputDialogFragment");
    }

    @Override
    public void onFinishInputDialog(String category, String detail) {
        // 计时器已经在 handleLap 中被暂停，现在记录分段
        recordLap(category, detail);
    }

    private void recordLap(String category, String detail) {
        // 1. 获取当前总运行时间（基于 elapsedRealtime()）
        // 🌟 修正 11: 基于 elapsedRealtime() 计算当前总运行时间 (修改时间：20251119 16:00)
        long elapsedMillis = SystemClock.elapsedRealtime() - startTimeElapsedMillis - totalPausedTimeElapsedMillis;

        // 2. 计算本次分段时间（间隔）
        // 🌟 修正 12: 修正负间隔溢出问题，利用已持久化的 lastLapEndElapsedMillis (修改时间：20251119 16:00)
        long currentLapTime = elapsedMillis - lastLapEndElapsedMillis;

        // 防止出现负值（虽然理论上不会，但安全起见）
        if (currentLapTime < 0) {
            Log.w(TAG, "Negative Lap Time detected. Resetting to 0. (Last Lap End: " + lastLapEndElapsedMillis + ", Current Elapsed: " + elapsedMillis + ")");
            currentLapTime = 0;
        }

        // 3. 记录分段
        int index = lapRecords.size() + 1;

        // 记录系统时间 (使用 System.currentTimeMillis() 因为这是真实挂钟时间)
        long currentSystemTime = System.currentTimeMillis();
        // 计算本次分段的开始时间
        String startTimeStr = lapRecords.isEmpty()
                ? recordTimeFormat.format(new Date(currentSystemTime - elapsedMillis))
                : lapRecords.get(lapRecords.size() - 1).getRecordTime();
        String recordTimeStr = recordTimeFormat.format(new Date(currentSystemTime));

        LapRecord newRecord = new LapRecord(
                index,
                formatTime(currentLapTime),
                formatTime(elapsedMillis),
                startTimeStr,
                recordTimeStr,
                currentSystemTime, // 记录系统时间戳
                category,
                detail
        );

        lapRecords.add(newRecord);
        lapAdapter.notifyItemInserted(lapRecords.size() - 1);
        recyclerViewLaps.scrollToPosition(lapRecords.size() - 1);

        // 4. 更新下次分段的起始时间基准
        lastLapEndElapsedMillis = elapsedMillis; // 更新为当前的总运行时间

        // 5. 重新开始计时（从暂停状态切换回运行状态）
        startTimer();

        saveState();
    }

    // ====================================================================
    // 4. 主题、持久化与工具方法
    // ====================================================================

    // 🌟 修正 13: 修正 saveState()，持久化所有基于 elapsedRealtime() 的计时变量和 lastLapEndElapsedMillis (修改时间：20251119 16:00)
    private void saveState() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();

        editor.putBoolean("isRunning", isRunning);
        editor.putBoolean("isNight", isNight);

        // 🌟 修正 13-1: 持久化基于 elapsedRealtime() 的变量
        editor.putLong("startTimeElapsedMillis", startTimeElapsedMillis);
        editor.putLong("totalPausedTimeElapsedMillis", totalPausedTimeElapsedMillis);
        editor.putLong("lastPauseTimeElapsedMillis", lastPauseTimeElapsedMillis);
        editor.putLong("lastLapEndElapsedMillis", lastLapEndElapsedMillis); // 修复负间隔溢出的关键点

        // 持久化 lapRecords 列表
        Gson gson = new Gson();
        String jsonRecords = gson.toJson(lapRecords);
        editor.putString("lapRecords", jsonRecords);

        editor.apply();
    }

    private List<LapRecord> loadLapRecords() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        String jsonRecords = sharedPref.getString("lapRecords", null);

        List<LapRecord> loadedRecords = new ArrayList<>();
        if (jsonRecords != null) {
            Gson gson = new Gson();
            Type type = new TypeToken<ArrayList<LapRecord>>() {}.getType();
            List<LapRecord> tempRecords = gson.fromJson(jsonRecords, type);
            if (tempRecords != null) {
                loadedRecords.addAll(tempRecords);
            }
        }
        return loadedRecords;
    }

    // 🌟 修正 14: 修正 loadState()，加载所有基于 elapsedRealtime() 的计时变量和 lastLapEndElapsedMillis (修改时间：20251119 16:00)
    private void loadState() {
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);

        // 加载计时状态
        isRunning = sharedPref.getBoolean("isRunning", false);
        isNight = sharedPref.getBoolean("isNight", false);

        // 🌟 修正 14-1: 加载基于 elapsedRealtime() 的变量
        startTimeElapsedMillis = sharedPref.getLong("startTimeElapsedMillis", 0);
        totalPausedTimeElapsedMillis = sharedPref.getLong("totalPausedTimeElapsedMillis", 0);
        lastPauseTimeElapsedMillis = sharedPref.getLong("lastPauseTimeElapsedMillis", 0);
        lastLapEndElapsedMillis = sharedPref.getLong("lastLapEndElapsedMillis", 0); // 修复负间隔溢出的关键点

        // 加载 lapRecords 列表
        lapRecords.clear();
        List<LapRecord> loadedRecords = loadLapRecords();
        if (loadedRecords != null) {
            lapRecords.addAll(loadedRecords);
        }

        // 恢复 UI 显示
        if (startTimeElapsedMillis > 0) {
            // 计算总运行时间 (无论运行或暂停)
            long totalRunningTime;
            if (isRunning) {
                // 如果是运行状态，计算当前时间
                totalRunningTime = SystemClock.elapsedRealtime() - startTimeElapsedMillis - totalPausedTimeElapsedMillis;
                btnStartPause.setText(R.string.btn_pause);
                // onResume会处理 startTimer()
            } else {
                // 如果是暂停状态，显示暂停时的总运行时间
                totalRunningTime = lastPauseTimeElapsedMillis - startTimeElapsedMillis - totalPausedTimeElapsedMillis;
                btnStartPause.setText(R.string.btn_start);
                // onResume会更新 totalPausedTimeElapsedMillis
            }
            if (totalRunningTime < 0) totalRunningTime = 0;
            lblTime.setText(formatTime(totalRunningTime));
        }

        applyTheme();
    }

    /**
     * 时间格式化工具
     */
    private String formatTime(long millis) {
        // 🌟 修正 15: 确保负数时间被格式化为 0 (修改时间：20251119 16:00)
        if (millis < 0) {
            return "00:00:00.00";
        }
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long centiseconds = (millis % 1000) / 10;

        return String.format(Locale.getDefault(),
                "%d:%02d:%02d.%02d",
                hours, minutes, seconds, centiseconds);
    }

    // --- 主题切换逻辑 (保留或基于您之前提供的代码) ---
    private void toggleMode() {
        isNight = !isNight;
        applyTheme();
        saveState();
    }

    private void applyTheme() {
        if (isNight) {
            applyNightMode();
        } else {
            applyLightMode();
        }
    }

    private void applyNightMode() {
        isNight = true;
        // 使用 ColorUtils 或 ContextCompat 获取颜色
        int dark_gray = ContextCompat.getColor(this, R.color.dark_gray);
        int light_gray = ContextCompat.getColor(this, R.color.light_gray);
        int white = ContextCompat.getColor(this, R.color.white);

        // 主要背景色: 使用深色
        mainLayout.setBackgroundColor(dark_gray);
        recyclerViewLaps.setBackgroundColor(dark_gray);

        // 文字颜色: 使用浅色
        lblWeekday.setTextColor(light_gray);
        lblSystemDate.setTextColor(light_gray);
        lblTime.setTextColor(light_gray);

        // 按钮主题色
        for (Button button : new Button[] {btnStartPause, btnLap, btnReset, btnExport, btnMode}) {
            // 设置按钮背景为主题背景色
            Drawable wrappedDrawable = DrawableCompat.wrap(ContextCompat.getDrawable(this, R.drawable.rounded_button_bg).mutate());
            DrawableCompat.setTintList(wrappedDrawable, ColorStateList.valueOf(light_gray));
            ViewCompat.setBackground(button, wrappedDrawable);
            button.setTextColor(dark_gray); // 按钮文字用深色
        }

        // 列表头
        lapHeaderRow.setBackgroundColor(dark_gray);
        setLapHeaderTextColor(lapHeaderRow, light_gray);

        // 模式按钮文字
        btnMode.setText(R.string.btn_mode_day);

        // 通知适配器刷新（虽然适配器已优化为使用主题属性，但为兼容性保留）
        // lapAdapter.setNightMode(true); // 假设 setNightMode 方法被删除或不使用

        // Log.d(TAG, "Applied Night Mode");
    }

    private void applyLightMode() {
        isNight = false;
        // 使用 ColorUtils 或 ContextCompat 获取颜色
        int black = ContextCompat.getColor(this, R.color.black);
        int white = ContextCompat.getColor(this, R.color.white);
        int light_gray = ContextCompat.getColor(this, R.color.light_gray);

        // 主要背景色: 使用浅色
        mainLayout.setBackgroundColor(white);
        recyclerViewLaps.setBackgroundColor(white);

        // 文字颜色: 使用深色
        lblWeekday.setTextColor(black);
        lblSystemDate.setTextColor(black);
        lblTime.setTextColor(black);

        // 按钮主题色
        for (Button button : new Button[] {btnStartPause, btnLap, btnReset, btnExport, btnMode}) {
            // 设置按钮背景为主题背景色
            Drawable wrappedDrawable = DrawableCompat.wrap(ContextCompat.getDrawable(this, R.drawable.rounded_button_bg).mutate());
            DrawableCompat.setTintList(wrappedDrawable, ColorStateList.valueOf(light_gray));
            ViewCompat.setBackground(button, wrappedDrawable);
            button.setTextColor(black); // 按钮文字用深色
        }

        // 列表头
        lapHeaderRow.setBackgroundColor(white);
        setLapHeaderTextColor(lapHeaderRow, black);

        // 模式按钮文字
        btnMode.setText(R.string.btn_mode_night);

        // 通知适配器刷新
        // lapAdapter.setNightMode(false); // 假设 setNightMode 方法被删除或不使用

        // Log.d(TAG, "Applied Light Mode");
    }

    private void setLapHeaderTextColor(View headerView, int color) {
        if (headerView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) headerView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(color);
                }
            }
        }
    }

    // --- 文件导出逻辑 (保留) ---
    private void exportRecords() {
        if (lapRecords.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_records, Toast.LENGTH_SHORT).show();
            return;
        }

        // 触发文件选择器，让用户选择保存位置
        String fileName = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + "_TimeRecords.csv";
        try {
            createDocumentLauncher.launch(fileName);
        } catch (Exception e) {
            Log.e(TAG, "Failed to launch document creator: " + e.getMessage());
            Toast.makeText(this, R.string.toast_file_saver_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void writeCsvToFile(Uri uri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(uri);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            // 写入 BOM 以兼容 Excel 中文乱码问题 (修改时间：20251119 16:00)
            writer.write('\ufeff');

            // 写入 CSV 头部
            writer.write(getString(R.string.export_header));
            writer.newLine();

            // 写入记录
            for (LapRecord record : lapRecords) {
                String line = String.format(Locale.getDefault(),
                        "%d,%s,%s,%s,%s,\"%s\",\"%s\"", // 字段用双引号包裹，防止逗号干扰
                        record.getIndex(),
                        record.getLapTime(),
                        record.getTotalTime(),
                        record.getStartTime(),
                        record.getRecordTime(),
                        record.getCategory().replace("\"", "\"\""), // 处理种类中的引号
                        record.getDetail().replace("\"", "\"\"")    // 处理详情中的引号
                );
                writer.write(line);
                writer.newLine();
            }

            writer.flush();
            Toast.makeText(this, R.string.toast_export_success, Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            Log.e(TAG, "Error writing CSV file: " + e.getMessage(), e);
            Toast.makeText(this, getString(R.string.toast_export_fail) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}