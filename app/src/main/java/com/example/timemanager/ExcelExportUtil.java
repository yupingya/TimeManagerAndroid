package com.example.timemanager;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * ExcelExportUtil - 负责将分段记录导出为 Excel 兼容的 CSV 文件。
 */
public class ExcelExportUtil {

    private static final String TAG = "ExcelExportUtil";
    private static final String CSV_SEPARATOR = ","; // 使用逗号分隔符

    /**
     * 辅助方法：添加单元格内容并处理CSV格式（转义引号和逗号）。
     * @param sb 用于构建CSV行的 StringBuilder
     * @param value 单元格的值
     */
    private static void addCell(StringBuilder sb, String value) {
        if (value == null) {
            value = "";
        }
        // 处理内容中的双引号：替换为两个双引号
        String escapedValue = value.replace("\"", "\"\"");

        // 如果内容包含逗号、换行符或双引号，则需要用双引号包裹
        if (escapedValue.contains(CSV_SEPARATOR) || escapedValue.contains("\n") || value.contains("\"")) {
            sb.append("\"").append(escapedValue).append("\"");
        } else {
            sb.append(escapedValue);
        }
    }

    /**
     * 将分段记录导出为 CSV 文件。
     * @param context Context
     * @param lapRecords 要导出的分段记录列表
     * @param uri 文件保存的Uri
     * @throws IOException 写入文件失败时抛出异常
     */
    public static void exportRecords(Context context, List<LapRecord> lapRecords, Uri uri) throws IOException {

        // 确保文件流被正确关闭
        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8))) {

            // 写入 BOM (Byte Order Mark) 以兼容 Excel 中文乱码问题 (修改时间：20251119 16:09)
            writer.write('\ufeff');

            // 写入 CSV 头部 (R.string.export_header: 序号,分段时间（间隔）,累计时间,开始时间,记录时间,分段种类,具体事件)
            writer.write(context.getString(R.string.export_header));
            writer.newLine();

            // 使用 StringBuilder 构建每一行
            StringBuilder sb = new StringBuilder();

            // 写入记录
            for (LapRecord record : lapRecords) {
                sb.setLength(0); // 清空 StringBuilder

                // 1. 序号
                addCell(sb, String.valueOf(record.getIndex()));
                sb.append(CSV_SEPARATOR);

                // 2. 分段时间（间隔）
                addCell(sb, record.getLapTime());
                sb.append(CSV_SEPARATOR);

                // 3. 累计时间
                addCell(sb, record.getTotalTime());
                sb.append(CSV_SEPARATOR);

                // 4. 开始时间
                addCell(sb, record.getStartTime());
                sb.append(CSV_SEPARATOR);

                // 5. 记录时间 (原报错位置，已修正)
                // 错误修正：LapRecord 类中无 getDate() 方法，应使用 getRecordTime() (修改时间：20251119 16:09)
                addCell(sb, record.getRecordTime());
                sb.append(CSV_SEPARATOR);

                // 6. 分段种类
                addCell(sb, record.getCategory());
                sb.append(CSV_SEPARATOR);

                // 7. 具体事件
                addCell(sb, record.getDetail());

                // 写入行
                writer.write(sb.toString());
                writer.newLine();
            }

            writer.flush();

        } catch (IOException e) {
            Log.e(TAG, "Error writing CSV file: " + e.getMessage(), e);
            // 抛出新的 IOException 让调用者处理，如在 MainActivity 中显示 Toast
            throw new IOException(context.getString(R.string.toast_export_fail) + e.getMessage(), e);
        }
    }
}