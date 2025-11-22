package com.example.timemanager.ui.adapter;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import androidx.annotation.NonNull;
import java.util.List;

/**
 * CustomSpinnerAdapter
 * 修改时间：20251118 12:40 - 新增：自定义Spinner Adapter，用于控制不同模式下的文本颜色
 * 修改时间：20251118 12:45 - 修正：新增isNightMode参数，并用于设置下拉框背景色
 * 修改时间：20251118 12:59 - 修正：不再传递isNightMode状态，直接传递下拉框背景色
 */
public class CustomSpinnerAdapter extends ArrayAdapter<String> {

    private final int normalTextColor;
    private final int dropDownBackgroundColor; // 存储下拉框背景色

    public CustomSpinnerAdapter(@NonNull Context context, int resource, @NonNull List<String> objects, int textColor, int dropDownBgColor) {
        super(context, resource, objects);
        this.normalTextColor = textColor;
        this.dropDownBackgroundColor = dropDownBgColor; // 存储传入的背景色
    }

    // 控制未展开时Spinner的文本颜色（主显示视图）
    @NonNull
    @Override
    public View getView(int position, View convertView, @NonNull ViewGroup parent) {
        TextView view = (TextView) super.getView(position, convertView, parent);
        view.setTextColor(normalTextColor); // 强制设置颜色
        return view;
    }

    // 控制展开后下拉列表项的文本颜色和背景颜色
    @Override
    public View getDropDownView(int position, View convertView, @NonNull ViewGroup parent) {
        TextView view = (TextView) super.getDropDownView(position, convertView, parent);
        view.setTextColor(normalTextColor); // 强制设置颜色

        // 使用传入的背景色强制设定下拉列表背景色
        view.setBackgroundColor(dropDownBackgroundColor);

        return view;
    }
}