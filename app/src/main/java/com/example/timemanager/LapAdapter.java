package com.example.timemanager;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class LapAdapter extends RecyclerView.Adapter<LapAdapter.LapViewHolder> {

    private final List<LapRecord> lapRecords;
    private final Context context;
    private boolean isNightMode = false;

    public LapAdapter(Context context, List<LapRecord> lapRecords) {
        this.context = context;
        this.lapRecords = lapRecords;
    }

    public void setNightMode(boolean isNightMode) {
        this.isNightMode = isNightMode;
        // 模式切换时，通知列表刷新所有项，让 onBindViewHolder 重新设置颜色
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public LapViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.lap_item_row, parent, false);
        return new LapViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LapViewHolder holder, int position) {
        LapRecord record = lapRecords.get(position);

        holder.lblIndex.setText(String.valueOf(record.getIndex()));
        holder.lblLapTime.setText(record.getInterval());
        holder.lblTotalTime.setText(record.getLapTime());
        holder.lblStartTime.setText(record.getStartTime());
        holder.lblRecordTime.setText(record.getRecordTime());
        holder.lblCategory.setText(record.getCategory());
        holder.lblDetail.setText(record.getDetail());

        // 使用 ColorUtils 获取颜色
        int textColor = ColorUtils.getThemeColor(context, "colorOnSurface", isNightMode);
        int bgColor = ColorUtils.getThemeColor(context, "colorSurface", isNightMode);

        // 列表项背景色 (黑夜: #333333 | 白天: #EEEEEE)
        holder.itemView.setBackgroundColor(bgColor);

        // 列表项文本颜色 (黑夜: #EEEEEE | 白天: #121212)
        holder.lblIndex.setTextColor(textColor);
        holder.lblLapTime.setTextColor(textColor);
        holder.lblTotalTime.setTextColor(textColor);
        holder.lblStartTime.setTextColor(textColor);
        holder.lblRecordTime.setTextColor(textColor);
        holder.lblCategory.setTextColor(textColor);
        holder.lblDetail.setTextColor(textColor);
    }

    @Override
    public int getItemCount() {
        return lapRecords.size();
    }

    public static class LapViewHolder extends RecyclerView.ViewHolder {
        final TextView lblIndex;
        final TextView lblLapTime;
        final TextView lblTotalTime;
        final TextView lblStartTime;
        final TextView lblRecordTime;
        final TextView lblCategory;
        final TextView lblDetail;

        public LapViewHolder(@NonNull View itemView) {
            super(itemView);
            lblIndex = itemView.findViewById(R.id.lblIndex);
            lblLapTime = itemView.findViewById(R.id.lblLapTime);
            lblTotalTime = itemView.findViewById(R.id.lblTotalTime);
            lblStartTime = itemView.findViewById(R.id.lblStartTime);
            lblRecordTime = itemView.findViewById(R.id.lblRecordTime);
            lblCategory = itemView.findViewById(R.id.lblCategory);
            lblDetail = itemView.findViewById(R.id.lblDetail);
        }
    }
}