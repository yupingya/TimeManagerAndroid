package com.example.timemanager;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.content.ContextCompat; // 保持原有引入，虽然实际使用主题属性，但为完整性保留

import java.util.List;

public class LapAdapter extends RecyclerView.Adapter<LapAdapter.LapViewHolder> {

    private final List<LapRecord> lapRecords;
    private final Context context;
    // 如果之前的版本有 isNightMode，这里可以保持移除，因为颜色逻辑应在 XML 中使用主题属性控制。

    public LapAdapter(Context context, List<LapRecord> lapRecords) {
        this.context = context;
        this.lapRecords = lapRecords;
    }

    // 如果之前的版本有 setNightMode 方法，这里可以保持移除。
    /*
    public void setNightMode(boolean isNightMode) {
        // ... (如果该方法已移除，则不需再添加)
        // notifyDataSetChanged();
    }
    */

    @NonNull
    @Override
    public LapViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 使用 activity_main.xml 中列表项的布局（lap_item_row.xml）
        View view = LayoutInflater.from(context).inflate(R.layout.lap_item_row, parent, false);
        return new LapViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LapViewHolder holder, int position) {
        LapRecord record = lapRecords.get(position);

        holder.lblIndex.setText(String.valueOf(record.getIndex()));

        // 错误修正：将 record.getInterval() 替换为 record.getLapTime()
        // getInterval() 找不到符号，LapRecord 中对应方法为 getLapTime() (修改时间：20251119 16:12)
        holder.lblLapTime.setText(record.getLapTime());

        holder.lblTotalTime.setText(record.getTotalTime());
        holder.lblStartTime.setText(record.getStartTime());
        holder.lblRecordTime.setText(record.getRecordTime());
        holder.lblCategory.setText(record.getCategory());
        holder.lblDetail.setText(record.getDetail());
    }

    @Override
    public int getItemCount() {
        return lapRecords.size();
    }

    // 假设您的列表项布局（lap_item_row.xml）包含以下TextViews
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
            // ⚠️ 注意：这里的ID必须与您项目中用于列表项的布局文件中的ID一致。
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