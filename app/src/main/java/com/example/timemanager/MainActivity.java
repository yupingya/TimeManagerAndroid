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

    /** 日志标签，用于标识当前应用的日志信息 */
    private static final String TAG = "TimeManagerApp";

    // UI 控件变量
    /** 主布局容器，用于设置整体背景颜色等样式 */
    private LinearLayout mainLayout;
    /** 显示星期的文本控件 */
    private TextView lblWeekday;
    /** 显示系统日期的文本控件 */
    private TextView lblSystemDate;
    /** 显示系统时间（包含毫秒）的文本控件 */
    private TextView lblSystemTime;
    /** 显示计时器当前时间的文本控件 */
    private TextView lblTime;
    /** 开始/暂停计时器的按钮 */
    private Button btnStartPause;
    /** 记录分段时间的按钮 */
    private Button btnLap;
    /** 重置计时器的按钮 */
    private Button btnReset;
    /** 导出记录数据的按钮 */
    private Button btnExport;
    /** 切换日夜模式的按钮 */
    private Button btnMode;
    /** 分段列表头部布局（标题行） */
    private LinearLayout lapHeaderRow;
    /** 显示分段记录的RecyclerView */
    private androidx.recyclerview.widget.RecyclerView recyclerViewLaps;
    /** 分段记录的适配器，用于RecyclerView的数据绑定 */
    private LapAdapter lapAdapter;

    // 功能相关变量
    /** 用于在主线程更新UI的Handler */
    private Handler handler;
    /** 用于定时更新系统时间显示的Timer */
    private Timer systemTimeTimer;
    /** 计时开始基准时间（每次开始/继续时更新），用于计算累计计时时间 */
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

    /**
     * 活动创建时的初始化方法
     * 时间：初始创建
     * 功能：加载保存的模式状态、设置布局、初始化控件、注册监听器、启动系统时间计时器等
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        sharedPreferences = getPreferences(Context.MODE_PRIVATE);
        loadModeState();

        super.onCreate(savedInstanceState);
        // 【2025-11-20 16:10】新增：记录应用启动日志
        LogUtils.log("Application started. isNight=" + isNight + ", isRunning=" + isRunning + ", Records=" + (lapRecords != null ? lapRecords.size() : 0));
        setContentView(R.layout.activity_main);

        // 启动保活服务
        DaemonManager.startDaemonService(this);

        // 原有初始化逻辑保持不变
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

    /**
     * 切换日夜模式
     * 时间：初始创建
     * 功能：切换isNight状态，保存模式状态到SharedPreferences，并应用对应模式的主题
     */
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

    /**
     * 应用深色模式
     * 时间：初始创建
     * 功能：设置isNight为true，并调用统一主题应用方法应用深色模式颜色
     */
    private void applyDarkMode() {
        isNight = true;
        applyThemeColors(true);
    }

    /**
     * 应用浅色模式
     * 时间：初始创建
     * 功能：设置isNight为false，并调用统一主题应用方法应用浅色模式颜色
     */
    private void applyLightMode() {
        isNight = false;
        applyThemeColors(false);
    }

    /**
     * 统一的主题颜色应用方法
     * 时间：初始创建
     * 功能：根据日夜模式状态，为所有UI元素设置对应的主题颜色（背景、文本、按钮等）
     * @param isNightMode 是否为夜间模式
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

    /**
     * 设置分段列表头文本颜色
     * 时间：初始创建
     * 功能：递归设置分段列表头部所有文本控件的颜色
     * @param headerView 列表头布局视图
     * @param color 要设置的文本颜色
     */
    private void setLapHeaderTextColor(View headerView, int color) {
        if (headerView instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) headerView;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                if (child instanceof TextView) {
                    ((TextView) child).setTextColor(color);
                } else if (child instanceof ViewGroup) {
                    setLapHeaderTextColor(child, color);    // 递归处理
                }
            }
        }
    }

    // ----------------------------------------------------
    // ⏰ 计时器和状态管理
    // ----------------------------------------------------

    /**
     * 更新系统时间显示
     * 时间：初始创建
     * 功能：获取当前系统时间，格式化后更新星期、日期、时间（含毫秒）的显示
     */
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

    /**
     * 更新系统计算器
     * 【2025-11-20 14:06】新增：使用 Handler 安全更新系统时间，避免 Timer 积压问题
     * 功能：每 100ms 更新一次右上角的系统时间（含毫秒），视觉流畅且不卡顿
     */
    private static final long SYSTEM_TIME_UPDATE_INTERVAL = 100;
    private final Runnable updateSystemTimeRunnable = new Runnable() {
        @Override
        public void run() {
            updateSystemTime(); // 复用原有方法，无需改动
            handler.postDelayed(this, SYSTEM_TIME_UPDATE_INTERVAL);
        }
    };

    /**
     * 开始计时
     * 时间：20251119 20:05
     * 功能：设置计时器为运行状态，计算计时基准时间，记录本次开始计时的实际时间（用于"开始时间"字段），启动计时器更新任务
     */
    private void startTimer() {
        // 【2025-11-20 16:12】新增：记录计时开始
        LogUtils.log("Timer started. startTimeForLap=" + new java.util.Date(startTimeForLap));
        isRunning = true;
        startTimeMillis = System.currentTimeMillis() - elapsedMillis;
        // 关键修改：将开始时间改为点击开始按钮时的当前系统时间，不再减去已累计时间
        startTimeForLap = System.currentTimeMillis();
        btnStartPause.setText(R.string.btn_pause);
        handler.post(updateTimerRunnable);
    }

    /**
     * 切换开始/暂停状态
     * 时间：20251117 10:20
     * 功能：如果正在运行则暂停计时（保存当前累计时间），如果已暂停则调用startTimer继续计时，最后保存状态
     */
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

    /**
     * 记录分段（分隔）
     * 时间：20251117 10:25
     * 功能：当计时器运行时，暂停计时并计算本次分段时间、累计分段时间，显示输入对话框获取分段信息
     */
    private void recordLap() {
        // 【2025-11-20 16:13】新增：记录分段操作触发
        LogUtils.log("Lap recorded. currentElapsed=" + elapsedMillis + "ms");
        if (!isRunning) {
            Toast.makeText(this, R.string.toast_start_first, Toast.LENGTH_SHORT).show();
            return;
        }
        // 1. 暂停计时（保持暂停状态，等待用户后续操作）
        isRunning = false;
        handler.removeCallbacks(updateTimerRunnable);
        btnStartPause.setText(R.string.btn_start);
        pauseOffsetMillis = elapsedMillis;
        // 2. 计算核心数据（按照C#逻辑）
        long currentElapsed = elapsedMillis;
        long currentLapTime = currentElapsed - lastLapEndElapsedMillis;
        lastLapEndElapsedMillis = currentElapsed;
        totalLapAccumulatedMillis += currentLapTime;
        // 3. 格式化时间字段（保持原有格式，不修改）
        SimpleDateFormat timeFormatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
        String startTimeStr = timeFormatter.format(new Date(startTimeForLap));
        String recordTimeStr = timeFormatter.format(new Date(System.currentTimeMillis()));
        String dateStr = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(System.currentTimeMillis()));

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
     * 重置计时器
     * 时间：20251117 10:30
     * 功能：停止计时器（如果正在运行），重置所有计时相关变量（包括分段相关变量），清空分段记录并更新UI
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
        updateTimerText();
        btnStartPause.setText(R.string.btn_start);
        saveState();
    }

    /**
     * 更新计时器的Runnable
     * 时间：未修改（初始创建）
     * 功能：计算当前累计计时时间，更新计时器文本，并延迟10毫秒再次执行（实现持续计时）
     */
    private final Runnable updateTimerRunnable = new Runnable() {
        @Override
        public void run() {
            elapsedMillis = System.currentTimeMillis() - startTimeMillis;
            updateTimerText();
            handler.postDelayed(this, 10);
        }
    };

    /**
     * 更新计时器显示文本
     * 时间：初始创建
     * 功能：调用formatTime方法格式化累计时间，并更新到lblTime控件
     */
    // 修改时间：2025-11-19 15:20 - 使用 LapRecord 中的静态方法进行格式化
    private void updateTimerText() {
        lblTime.setText(LapRecord.formatTime(elapsedMillis));
    }

    /**
     * 保存状态
     * 时间：20251117 10:33
     * 功能：将计时器运行状态、时间变量、分段记录等数据保存到SharedPreferences，确保应用重启后可恢复状态
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

    /**
     * 加载模式状态
     * 时间：初始创建
     * 功能：从SharedPreferences加载日夜模式状态（isNight）
     */
    private void loadModeState() {
        isNight = sharedPreferences.getBoolean("isNight", false);
    }

    /**
     * 加载状态
     * 时间：20251117 10:35
     * 功能：从SharedPreferences加载计时器所有状态数据（运行状态、时间变量、分段记录、分段相关变量等）
     */
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

    /**
     * 导出数据
     * 时间：20251117 22:05
     * 功能：当存在分段记录时，启动文件保存活动，导出数据为Excel文件
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
     * 写入文件到URI
     * 时间：20251117 22:05
     * 功能：通过ExcelExportUtil工具类将分段记录数据写入到指定的Uri（导出为Excel文件）
     * @param uri 文件保存的Uri
     */
    private void writeFileToUri(Uri uri) {
        if (uri == null) {
            return;
        }
        boolean success = ExcelExportUtil.exportLapRecordsToExcel(this, uri, lapRecords);
        // 【2025-11-20 16:15】新增：记录数据导出结果
        if (success) {
            LogUtils.log("Data exported successfully to: " + uri.toString());
        } else {
            LogUtils.log("Data export failed.");
        }
        if (!success) {
            Log.e(TAG, "Export failed");
        }
    }

    /**
     * 修改时间：2025-11-19 15:25 - 使用更新后的 LapRecord 构造函数，传递 long 原始值
     * 输入对话框完成回调
     * 时间：20251117 10:40
     * 功能：获取用户输入的分段类别和详情，结合之前计算的时间数据创建LapRecord并添加到列表，更新UI和状态
     * @param category 分段种类
     * @param detail 具体事件
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

        // 直接传入 long 类型数据 (currentLapTime, totalLapAccumulated)
        // LapRecord 内部会自动处理格式化
        // 构造LapRecord（严格对应字段顺序：序号、日期、分隔、分隔累计、开始时间、记录数据、时间戳、分段种类、具体事件）
        LapRecord newRecord = new LapRecord(
                lapIndex,
                dateStr,                        // 日期（不修改）
                currentLapTime,                 // 原始间隔时间 (long)
                totalLapAccumulated,            // 原始累计时间 (long)
                startTimeStr,                   // 开始时间（点击开始按钮时的时间，不修改）
                recordTimeStr,                  // 记录数据（点击分隔按钮时的时间，不修改）
                System.currentTimeMillis(),     // 系统时间戳（不修改）
                category,                       // 分段种类（不修改）
                detail                          // 具体事件（不修改）
        );

        lapRecords.add(newRecord);
        lapAdapter.notifyItemInserted(lapRecords.size() - 1);
        recyclerViewLaps.scrollToPosition(lapRecords.size() - 1);
        // 更新上次分隔结束时间基准（用于下次分段计算）
        lastLapEndElapsedMillis = elapsedMillis;

        saveState();
    }

    /**
     * 活动停止时的回调
     * 时间：初始创建
     * 功能：调用saveState保存当前状态
     */
    @Override
    protected void onStop() {
        super.onStop();
        saveState();
    }

    // 【2025-11-20 14:09】新增：Activity 进入前台时启动系统时间显示更新
    @Override
    protected void onResume() {
        super.onResume();
        handler.post(updateSystemTimeRunnable);
    }

    // 【2025-11-20 14:09】新增：Activity 进入后台时停止系统时间更新，节省资源
    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(updateSystemTimeRunnable);
    }
    /**
     * 活动销毁时的回调
     * 时间：初始创建（含新增停止服务代码）
     * 功能：取消系统时间计时器，移除Handler的所有回调，停止保活服务
     */
    // 【2025-11-20 14:10】重构：统一清理所有 Handler 回调，并停止保活服务
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 【2025-11-20 14:10】移除：不再需要 systemTimeTimer.cancel()
        handler.removeCallbacksAndMessages(null); // 包含 updateTimerRunnable 和 updateSystemTimeRunnable
        // 停止保活服务（保留原有功能）
        DaemonManager.stopDaemonService(this);
        // 【2025-11-20 16:17】新增：记录应用销毁
        LogUtils.log("Application destroyed.");
    }
}