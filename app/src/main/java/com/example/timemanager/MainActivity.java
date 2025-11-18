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

    // 分隔时间和累计分隔相关新增变量（对应C#软件逻辑）- 修改时间：20251117 10:15
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

        // 注册文件保存器 - 修改时间：20251117 22:05 - 修改为Excel文件类型
        fileSaverLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/vnd.ms-excel"),
                this::writeFileToUri);

        initSystemTimeTimer();

        if (isRunning) {
            startTimer();
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

    /**
     * 统一的主题颜色应用方法
     */
    private void applyThemeColors(boolean isNightMode) {
        // 使用 ColorUtils 获取颜色
        int colorSurface = ColorUtils.getThemeColor(this, "colorSurface", isNightMode);
        int colorOnSurface = ColorUtils.getThemeColor(this, "colorOnSurface", isNightMode);
        int colorPrimary = ColorUtils.getThemeColor(this, "colorPrimary", isNightMode);
        int colorOnPrimary = ColorUtils.getThemeColor(this, "colorOnPrimary", isNightMode);

        // 1. 主布局和列表背景
        mainLayout.setBackgroundColor(colorSurface);
        recyclerViewLaps.setBackgroundColor(colorSurface);

        // 2. 日期和计时标签
        lblWeekday.setTextColor(colorOnSurface);
        lblSystemDate.setTextColor(colorOnSurface);
        lblSystemTime.setTextColor(colorOnSurface);
        lblTime.setTextColor(colorOnSurface);

        // 3. 功能按钮
        for (Button button : new Button[] {btnStartPause, btnLap, btnReset, btnExport, btnMode}) {
            ViewCompat.setBackgroundTintList(button, ColorStateList.valueOf(colorPrimary));
            button.setTextColor(colorOnPrimary);
        }

        // 4. 分段列表头
        lapHeaderRow.setBackgroundColor(colorSurface);
        setLapHeaderTextColor(lapHeaderRow, colorOnSurface);

        // 5. 通知 Adapter 切换模式
        if (lapAdapter != null) {
            lapAdapter.setNightMode(isNightMode);
        }

        // 6. 设置模式按钮文本
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
                    setLapHeaderTextColor(child, color); // 递归处理
                }
            }
        }
    }

    // ----------------------------------------------------
    // ⏰ 计时器和状态管理
    // ----------------------------------------------------

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

    /**
     * 开始计时 - 修改时间：20251117 10:18
     * 新增记录每次开始计时的时间（startTimeForLap），用于"开始时间"字段和分段时间计算
     */
    private void startTimer() {
        isRunning = true;
        // 计算本次开始计时的基准时间（确保累计时间连续）
        startTimeMillis = System.currentTimeMillis() - elapsedMillis;
        // 记录本次开始计时的实际时间（用于填充"开始时间"字段）
        startTimeForLap = System.currentTimeMillis() - elapsedMillis;
        btnStartPause.setText(R.string.btn_pause);
        handler.post(updateTimerRunnable);
    }

    /**
     * 切换开始/暂停状态 - 修改时间：20251117 10:20
     * 保持原有逻辑，仅确保startTimeForLap在开始时正确设置
     */
    private void toggleStartPause() {
        if (isRunning) {
            // 暂停计时
            isRunning = false;
            pauseOffsetMillis = elapsedMillis; // 保存当前累计时间
            handler.removeCallbacks(updateTimerRunnable);
            btnStartPause.setText(R.string.btn_start);
        } else {
            // 开始/继续计时（startTimer方法已处理startTimeForLap）
            startTimer();
        }
        saveState();
    }

    /**
     * 记录分段（分隔）- 修改时间：20251117 10:25
     * 按照C#软件逻辑修改：暂停计时→计算分隔时间→计算累计分隔时间→记录相关时间字段
     */
    private void recordLap() {
        if (!isRunning) {
            Toast.makeText(this, R.string.toast_start_first, Toast.LENGTH_SHORT).show();
            return;
        }

        // 1. 暂停计时（保持暂停状态，等待用户后续操作）
        isRunning = false;
        handler.removeCallbacks(updateTimerRunnable);
        btnStartPause.setText(R.string.btn_start);
        pauseOffsetMillis = elapsedMillis; // 保存当前累计时间

        // 2. 计算核心数据（按照C#逻辑）
        long currentElapsed = elapsedMillis; // 当前累计计时时间
        long currentLapTime = currentElapsed - lastLapEndElapsedMillis; // 本次分隔时间（当前-上次分隔结束时间）
        lastLapEndElapsedMillis = currentElapsed;
        totalLapAccumulatedMillis += currentLapTime; // 累计分隔时间（总和累加）

        // 3. 格式化时间字段（保持原有格式，不修改）
        SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String startTimeStr = timeFormatter.format(new Date(startTimeForLap)); // 开始时间（本次点击开始的时间）
        String recordTimeStr = timeFormatter.format(new Date(System.currentTimeMillis())); // 记录数据（点击分隔的时间）
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(System.currentTimeMillis())); // 日期

        // 4. 显示输入对话框（保持原有逻辑）
        InputDialogFragment dialog = new InputDialogFragment();
        // 通过Bundle传递计算好的临时数据（避免多线程问题）
        Bundle bundle = new Bundle();
        bundle.putString("dateStr", dateStr);
        bundle.putLong("currentLapTime", currentLapTime);
        bundle.putLong("totalLapAccumulatedMillis", totalLapAccumulatedMillis);
        bundle.putString("startTimeStr", startTimeStr);
        bundle.putString("recordTimeStr", recordTimeStr);
        dialog.setArguments(bundle);
        dialog.show(getSupportFragmentManager(), "InputDialogFragment");
    }

    /**
     * 重置计时器 - 修改时间：20251117 10:30
     * 新增重置分隔相关变量，确保状态完全重置
     */
    private void resetTimer() {
        if (isRunning) {
            toggleStartPause();
        }
        // 重置所有基础时间变量
        startTimeMillis = 0;
        elapsedMillis = 0;
        pauseOffsetMillis = 0;
        lapIndex = 0;
        isRunning = false;
        // 重置分隔相关变量（关键）
        startTimeForLap = 0;
        lastLapEndElapsedMillis = 0;
        totalLapAccumulatedMillis = 0;
        // 清空分段记录
        lapRecords.clear();
        lapAdapter.notifyDataSetChanged();
        // 更新UI
        updateTimerText();
        btnStartPause.setText(R.string.btn_start);
        saveState();
    }

    /**
     * 更新计时器的Runnable  -  未修改（保持原有逻辑）
     */
    private final Runnable updateTimerRunnable = new Runnable() {
        @Override
        public void run() {
            // 计算从基准时间到当前的累计时间
            elapsedMillis = System.currentTimeMillis() - startTimeMillis;
            updateTimerText();
            handler.postDelayed(this, 10);
        }
    };

    private void updateTimerText() {
        lblTime.setText(formatTime(elapsedMillis));
    }

    /**
     * 保存状态 - 修改时间：20251117 10:33
     * 新增分隔相关变量的保存，确保重启后状态恢复
     */
    private void saveState() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean("isRunning", isRunning);
        editor.putLong("pauseOffsetMillis", pauseOffsetMillis);
        editor.putLong("startTimeMillis", startTimeMillis);
        editor.putInt("lapIndex", lapIndex);
        editor.putBoolean("isNight", isNight);
        // 新增保存分隔相关变量
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

    /**
     * 加载状态 - 修改时间：20251117 10:35
     * 新增分隔相关变量的加载，确保重启后状态恢复
     */
    private void loadState() {
        isRunning = sharedPreferences.getBoolean("isRunning", false);
        pauseOffsetMillis = sharedPreferences.getLong("pauseOffsetMillis", 0);
        startTimeMillis = sharedPreferences.getLong("startTimeMillis", 0);
        lapIndex = sharedPreferences.getInt("lapIndex", 0);
        isNight = sharedPreferences.getBoolean("isNight", false);
        // 新增加载分隔相关变量
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

    /**
     * 导出数据 - 修改时间：20251117 22:05 - 修改为导出Excel文件
     */
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

    /**
     * 写入文件到URI - 修改时间：20251117 22:05 - 修改为使用Excel导出工具
     */
    private void writeFileToUri(Uri uri) {
        if (uri == null) {
            return;
        }

        // 使用新的Excel导出工具类导出数据
        boolean success = ExcelExportUtil.exportLapRecordsToExcel(this, uri, lapRecords);

        if (!success) {
            Log.e(TAG, "Export failed");
        }
    }

    /**
     * 输入对话框完成回调 - 修改时间：20251117 10:40
     * 按照C#逻辑构造LapRecord：分隔=本次分段时间，分隔累计=总累计时间，其他字段保持原有逻辑
     */
    @Override
    public void onFinishInputDialog(String category, String detail) {
        // 获取recordLap中传递的临时数据
        Bundle bundle = getSupportFragmentManager().findFragmentByTag("InputDialogFragment").getArguments();
        if (bundle == null) return;

        String dateStr = bundle.getString("dateStr");
        long currentLapTime = bundle.getLong("currentLapTime");
        long totalLapAccumulated = bundle.getLong("totalLapAccumulatedMillis");
        String startTimeStr = bundle.getString("startTimeStr");
        String recordTimeStr = bundle.getString("recordTimeStr");

        // 序号自增（保持原有逻辑）
        lapIndex++;

        // 构造LapRecord（严格对应字段顺序：序号、日期、分隔、分隔累计、开始时间、记录数据、时间戳、分段种类、具体事件）
        LapRecord newRecord = new LapRecord(
                lapIndex,
                dateStr,                    // 日期（不修改）
                formatTime(currentLapTime), // 分隔（本次分段时间，对应C#分段时间）
                formatTime(totalLapAccumulated), // 分隔累计（总累计时间，对应C#累计时间）
                startTimeStr,               // 开始时间（点击开始按钮时的时间，不修改）
                recordTimeStr,              // 记录数据（点击分隔按钮时的时间，不修改）
                System.currentTimeMillis(), // 系统时间戳（不修改）
                category,                   // 分段种类（不修改）
                detail                      // 具体事件（不修改）
        );

        lapRecords.add(newRecord);
        lapAdapter.notifyItemInserted(lapRecords.size() - 1);
        recyclerViewLaps.scrollToPosition(lapRecords.size() - 1);

        // 更新上次分隔结束时间基准（用于下次分段计算）
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
    }

    /**
     * 时间格式化工具 - 未修改（保持原有格式）
     */
    private String formatTime(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long centiseconds = (millis % 1000) / 10;

        return String.format(Locale.getDefault(), "%d:%02d:%02d.%02d", hours, minutes, seconds, centiseconds);
    }
}