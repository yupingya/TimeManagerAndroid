package com.example.timemanager;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Locale;

/**
 * Excel导出工具类 - 使用轻量级实现避免兼容性问题
 * 修改时间：20251117 22:05 - 替换POI库为轻量级实现
 */
public class ExcelExportUtil {
    private static final String TAG = "ExcelExportUtil";

    /**
     * 导出分段记录数据到XLSX文件
     * 修改时间：20251117 22:05 - 使用轻量级实现生成Excel文件
     */
    public static boolean exportLapRecordsToExcel(Context context, Uri uri,
                                                  List<LapRecord> lapRecords,
                                                  int successMsg, int failMsg) {
        if (uri == null || lapRecords == null || lapRecords.isEmpty()) {
            return false;
        }

        try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
            // 生成Excel XML内容
            String excelContent = generateExcelContent(lapRecords);

            // 写入到输出流
            outputStream.write(excelContent.getBytes("UTF-8"));
            outputStream.flush();

            if (successMsg != 0) {
                Toast.makeText(context, successMsg, Toast.LENGTH_LONG).show();
            }
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Error writing Excel file: ", e);
            if (failMsg != 0) {
                String errorMsg = context.getString(failMsg) + e.getMessage();
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
            }
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error: ", e);
            if (failMsg != 0) {
                String errorMsg = context.getString(failMsg) + e.getMessage();
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
            }
            return false;
        }
    }

    /**
     * 生成Excel XML格式内容
     * 修改时间：20251117 22:05 - 手动生成Excel XML格式
     */
    private static String generateExcelContent(List<LapRecord> lapRecords) {
        StringBuilder sb = new StringBuilder();

        // Excel XML头部
        sb.append("<?xml version=\"1.0\"?>\n");
        sb.append("<?mso-application progid=\"Excel.Sheet\"?>\n");
        sb.append("<Workbook xmlns=\"urn:schemas-microsoft-com:office:spreadsheet\"\n");
        sb.append(" xmlns:o=\"urn:schemas-microsoft-com:office:office\"\n");
        sb.append(" xmlns:x=\"urn:schemas-microsoft-com:office:excel\"\n");
        sb.append(" xmlns:ss=\"urn:schemas-microsoft-com:office:spreadsheet\"\n");
        sb.append(" xmlns:html=\"http://www.w3.org/TR/REC-html40\">\n");

        // 样式定义
        sb.append(" <Styles>\n");
        sb.append("  <Style ss:ID=\"Default\" ss:Name=\"Normal\">\n");
        sb.append("   <Alignment ss:Vertical=\"Center\"/>\n");
        sb.append("  </Style>\n");
        sb.append("  <Style ss:ID=\"Header\">\n");
        sb.append("   <Font ss:Bold=\"1\" ss:Color=\"#FFFFFF\"/>\n");
        sb.append("   <Interior ss:Color=\"#366092\" ss:Pattern=\"Solid\"/>\n");
        sb.append("   <Alignment ss:Horizontal=\"Center\" ss:Vertical=\"Center\"/>\n");
        sb.append("  </Style>\n");
        sb.append("  <Style ss:ID=\"Data\">\n");
        sb.append("   <Alignment ss:Vertical=\"Center\"/>\n");
        sb.append("  </Style>\n");
        sb.append(" </Styles>\n");

        // 工作表
        sb.append(" <Worksheet ss:Name=\"分段记录\">\n");
        sb.append("  <Table>\n");

        // 表头
        sb.append("   <Row>\n");
        String[] headers = {"序号", "日期", "间隔", "间隔累计", "开始时间", "记录时间", "分段种类", "具体事件"};
        for (String header : headers) {
            sb.append("    <Cell ss:StyleID=\"Header\"><Data ss:Type=\"String\">").append(escapeXml(header)).append("</Data></Cell>\n");
        }
        sb.append("   </Row>\n");

        // 数据行
        for (LapRecord record : lapRecords) {
            sb.append("   <Row>\n");
            addCell(sb, String.valueOf(record.getIndex()));
            addCell(sb, record.getDate());
            addCell(sb, record.getInterval());
            addCell(sb, record.getLapTime());
            addCell(sb, record.getStartTime());
            addCell(sb, record.getRecordTime());
            addCell(sb, record.getCategory());
            addCell(sb, record.getDetail());
            sb.append("   </Row>\n");
        }

        sb.append("  </Table>\n");
        sb.append(" </Worksheet>\n");
        sb.append("</Workbook>");

        return sb.toString();
    }

    /**
     * 添加单元格
     */
    private static void addCell(StringBuilder sb, String value) {
        sb.append("    <Cell ss:StyleID=\"Data\"><Data ss:Type=\"String\">")
                .append(escapeXml(value))
                .append("</Data></Cell>\n");
    }

    /**
     * XML转义
     */
    private static String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    /**
     * 重载方法，使用默认的成功失败消息
     * 修改时间：20251117 22:05 - 使用轻量级实现生成Excel文件
     */
    public static boolean exportLapRecordsToExcel(Context context, Uri uri, List<LapRecord> lapRecords) {
        int successMsg = context.getResources().getIdentifier("toast_export_success", "string", context.getPackageName());
        int failMsg = context.getResources().getIdentifier("toast_export_fail", "string", context.getPackageName());
        return exportLapRecordsToExcel(context, uri, lapRecords, successMsg, failMsg);
    }
}