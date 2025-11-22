// ExcelImportUtil.java
// 【2025-11-22 18:35】新增：Excel数据导入工具类
// 功能作用：负责从文件URI读取内容（假设为CSV或类CSV格式），解析为LapRecord列表，并计算最大累计时间。
// 新增时间：2025年11月22日 18:35
package com.example.timemanager.util;

import android.content.Context;
import android.net.Uri;
import android.widget.Toast;

import com.example.timemanager.R;
import com.example.timemanager.data.model.LapRecord;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Excel数据导入工具类
 * 假设导入的文件是CSV或类似的分隔符文件。
 * 格式要求（8列）：序号,日期,间隔,间隔累计,开始时间,记录时间,分段种类,具体事件
 */
public class ExcelImportUtil {
    private static final String TAG = "ExcelImportUtil";

    /**
     * 从URI读取文件内容，解析为 LapRecord 列表，并返回最大累计时间。
     * @param context Context
     * @param uri 文件 URI
     * @param resultList 导入的 LapRecord 列表会被填充到此
     * @return 最大累计毫秒数
     */
    public static long importLapRecordsFromUri(Context context, Uri uri, List<LapRecord> resultList) {
        if (uri == null || resultList == null) {
            Toast.makeText(context, R.string.toast_import_fail, Toast.LENGTH_SHORT).show();
            LogUtils.log("【ExcelImportUtil】系统发生“导入失败”事件：URI为空或列表对象为空。");
            android.util.Log.e(TAG, "Import failed: URI or resultList is null.");
            return 0L;
        }

        long maxLapTimeMillis = 0L;
        resultList.clear();

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             // 假设文件编码为 UTF-8 (CSV常见编码)
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"))) {

            // 读取并忽略文件头 (第一行)
            String headerLine = reader.readLine();
            if (headerLine == null) {
                Toast.makeText(context, R.string.toast_import_fail_empty, Toast.LENGTH_LONG).show();
                LogUtils.log("【ExcelImportUtil】导入失败：文件内容为空。");
                return 0L;
            }

            // 检查文件头是否包含关键列名，增加健壮性
            if (!headerLine.contains("序号") || !headerLine.contains("间隔累计") || !headerLine.contains("具体事件")) {
                Toast.makeText(context, R.string.toast_import_fail_header, Toast.LENGTH_LONG).show();
                LogUtils.log("【ExcelImportUtil】导入失败：文件头不匹配预期的8列格式。");
                return 0L;
            }

            String line;
            int lineNumber = 1; // 从第一行数据开始计数

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                // 假设数据是以逗号分隔 (CSV 格式)
                String[] tokens = line.split(",", -1); // -1 确保尾部空字符串也被计入

                // 必须严格是 8 列
                if (tokens.length < 8) {
                    LogUtils.log(String.format(Locale.getDefault(), "【ExcelImportUtil】第 %d 行数据不完整，跳过。列数: %d, 行内容: %s", lineNumber, tokens.length, line));
                    android.util.Log.w(TAG, String.format("Skipping line %d: Incomplete data. Tokens: %d", lineNumber, tokens.length));
                    continue;
                }

                try {
                    // 1. 序号 (Index) - tokens[0]
                    int index = Integer.parseInt(tokens[0].trim());
                    // 2. 日期 (Date) - tokens[1]
                    String date = tokens[1].trim();
                    // 3. 间隔 (Interval String) - tokens[2]
                    String intervalString = tokens[2].trim();
                    // 4. 间隔累计 (LapTime String) - tokens[3] -> 这是我们要设置的计时总时长
                    String lapTimeString = tokens[3].trim();
                    long totalAccumulatedMillis = LapRecord.parseTime(lapTimeString);
                    // 5. 开始时间 (StartTime) - tokens[4]
                    String startTime = tokens[4].trim();
                    // 6. 记录时间 (RecordTime) - tokens[5]
                    String recordTime = tokens[5].trim();
                    // 7. 分段种类 (Category) - tokens[6]
                    String category = tokens[6].trim();
                    // 8. 具体事件 (Detail) - tokens[7]
                    String detail = tokens[7].trim();

                    // 将 interval String 转为 long，用于构造 LapRecord（LapRecord 会将其转回 String）
                    long intervalMillis = LapRecord.parseTime(intervalString);

                    // LapRecord 构造函数需要 recordSystemTimeMillis。导入时我们设为 0L。
                    LapRecord record = new LapRecord(
                            index,
                            date,
                            intervalMillis,
                            totalAccumulatedMillis,
                            startTime,
                            recordTime,
                            0L, // 导入数据的时间戳设为 0
                            category,
                            detail
                    );

                    resultList.add(record);

                    // 找出最大的累计时间，用于设置计时器
                    if (totalAccumulatedMillis > maxLapTimeMillis) {
                        maxLapTimeMillis = totalAccumulatedMillis;
                    }

                } catch (NumberFormatException e) {
                    LogUtils.log(String.format(Locale.getDefault(), "【ExcelImportUtil】第 %d 行数字或时间格式错误，跳过。内容: %s", lineNumber, line));
                    android.util.Log.e(TAG, "Number format error at line " + lineNumber, e);
                }
            }

            LogUtils.log(String.format(Locale.getDefault(), "【ExcelImportUtil】导入成功，总计 %d 条记录，最大累计时间 %s", resultList.size(), LapRecord.formatTime(maxLapTimeMillis)));
            Toast.makeText(context, String.format(Locale.getDefault(), "成功解析 %d 条记录", resultList.size()), Toast.LENGTH_SHORT).show();
            return maxLapTimeMillis;

        } catch (Exception e) {
            Toast.makeText(context, R.string.toast_import_fail, Toast.LENGTH_LONG).show();
            LogUtils.log("【ExcelImportUtil】导入文件操作失败：" + e.getMessage());
            android.util.Log.e(TAG, "Error importing records from URI", e);
            return 0L;
        }
    }
}