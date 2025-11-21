package com.example.timemanager;

import android.app.Dialog;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.DialogFragment;

import java.util.Arrays;
import java.util.List;

/**
 * InputDialogFragment（修正版：解决API兼容性问题）
 * 修改时间：20251117 22:25 - 修复API级别兼容性问题，支持Android 7.0+
 * 修改时间：20251117 22:35 - 将分类输入改为下拉选择框
 * 修改时间：20251118 12:42 - 修正Spinner颜色问题，使用CustomSpinnerAdapter
 * 修改时间：20251118 12:45 - 修正CustomSpinnerAdapter无法获取isNightMode状态的错误
 * 修改时间：20251118 12:59 - 修正：Spinner Adapter直接传入背景色，不再传递夜间模式状态。
 */
public class InputDialogFragment extends DialogFragment {

    public interface InputDialogListener {
        void onFinishInputDialog(String category, String detail);
    }

    private InputDialogListener listener;
    private Spinner spnCategory; // 修改时间：20251117 22:35 - 将EditText改为Spinner
    private EditText edtDetail;
    private Button btnOK; // 承担功能：引用对话框中的“确定”按钮，供外部调用点击事件
    private Button btnCancel;

    private String selectedCategory = ""; // 修改时间：20251117 22:35 - 存储选中的分类

    private static final List<String> CANDIDATE_LAYOUT_NAMES = Arrays.asList(
            "fragment_input_dialog",
            "fragment_inputdialog",
            "input_dialog",
            "dialog_input",
            "fragment_dialog_input",
            "dialog_input_fragment"
    );

    public InputDialogFragment() {
        // 必需的空构造
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof InputDialogListener) {
            listener = (InputDialogListener) context;
        } else {
            throw new RuntimeException(context.toString()
                    + " must implement InputDialogListener");
        }
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);

        int layoutResId = resolveLayoutResId();
        if (layoutResId == 0) {
            // 运行时清晰提示（方便小白知道怎么处理）
            throw new RuntimeException("未找到对话框布局资源。请在 res/layout 下放置一个布局文件，" +
                    "其文件名应为其中之一：" + CANDIDATE_LAYOUT_NAMES.toString() +
                    "，或把现有布局文件名告知我以便我调整代码。");
        }

        LayoutInflater inflater = requireActivity().getLayoutInflater();
        View view = inflater.inflate(layoutResId, null);

        // 尝试查找常见 id（若布局里 id 不同也有回退策略）
        // 修改时间：20251117 22:35 - 将EditText改为Spinner
        spnCategory = findViewByIdIfExists(view, R.id.spnCategory);
        edtDetail = findViewByIdIfExists(view, R.id.txtDetail);

        // 修改时间：20251117 22:35 - 设置Spinner选择监听器
        if (spnCategory != null) {
            spnCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                    selectedCategory = parent.getItemAtPosition(position).toString();
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                    selectedCategory = "";
                }
            });
        }

        btnOK = findViewByIdIfExists(view, R.id.btnOK);
        btnCancel = findViewByIdIfExists(view, R.id.btnCancel);
        if (btnOK == null) btnOK = findButtonFallback(view);
        if (btnCancel == null) btnCancel = findButtonFallbackExcept(view, btnOK);

        if (btnOK != null) {
            btnOK.setOnClickListener(v -> {
                // 修改时间：20251117 22:35 - 使用Spinner选中的值
                String category = selectedCategory;
                String detail = edtDetail != null ? edtDetail.getText().toString().trim() : "";
                if (listener != null) {
                    listener.onFinishInputDialog(category, detail);
                }
                dismiss();
            });
        }
        if (btnCancel != null) {
            // 【2025-11-22 04:40】修改：点击“取消”按钮时不再直接关闭，而是弹出二次确认弹窗
            // 功能作用：防止用户误点“取消”导致输入内容丢失，统一通过“确认操作”弹窗处理放弃逻辑
            // 修改时间：2025年11月22日 04:40
            btnCancel.setOnClickListener(v -> {
                try {
                    showDiscardConfirmation();
                } catch (Exception e) {
                    // 【2025-11-22 04:45】新增：记录“取消”按钮触发确认弹窗失败的日志
                    LogUtils.log("【InputDialogFragment.btnCancel】点击取消按钮时弹出确认框失败：" + e.getMessage());
                    Log.e("InputDialogFragment", "点击‘取消’按钮时弹出确认对话框失败", e);
                    // 安全兜底：若弹窗失败，仍允许关闭（避免卡死）
                    dismiss();
                }
            });
        }

        dialog.setContentView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));
            // 注意：窗口大小我们在 onStart() 里设置（因为视图创建后才能安全设置宽度）
        }

        // 【2025-11-22 03:00】修改：禁止对话框因点击外部或返回键自动关闭，
        // 改为由自定义逻辑处理，确保“继续编辑”能回到原对话框。
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        return dialog;
    }

    @Override
    public void onStart() {
        super.onStart();

        // 1) 确保对话框宽度为屏幕宽度的 90% —— 防止按钮被挤出界面
        // 修改时间：20251117 22:25 - 使用兼容性方案支持Android 7.0+
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            setDialogWidthCompatible(dialog);

            // 【2025-11-22 04:00】修复：增加空值检查并添加错误日志，防止因 getWindow() 返回 null 导致闪退
            try {
                // 监听返回键
                dialog.setOnKeyListener((d, keyCode, event) -> {
                    if (keyCode == android.view.KeyEvent.KEYCODE_BACK && event.getAction() == android.view.KeyEvent.ACTION_UP) {
                        showDiscardConfirmation();
                        return true;
                    }
                    return false;
                });

                // 监听点击外部区域 —— 关键：确保 getWindow() 不为 null
                android.view.Window window = dialog.getWindow();
                if (window != null) {
                    View contentView = window.getDecorView();
                    if (contentView != null) {
                        contentView.setOnTouchListener((v, event) -> {
                            if (event.getAction() == android.view.MotionEvent.ACTION_DOWN) {
                                Rect dialogBounds = new Rect();
                                window.getDecorView().getHitRect(dialogBounds);
                                if (!dialogBounds.contains((int) event.getX(), (int) event.getY())) {
                                    showDiscardConfirmation();
                                    return true;
                                }
                            }
                            return false;
                        });
                    } else {
                        LogUtils.log("【InputDialogFragment.onStart】警告：contentView 为 null，跳过触摸监听");
                    }
                } else {
                    LogUtils.log("【InputDialogFragment.onStart】警告：dialog.getWindow() 返回 null，跳过事件监听");
                }
            } catch (Exception e) {
                // 【2025-11-22 04:05】新增：记录 onStart 中事件监听器初始化失败的异常
                LogUtils.log("【InputDialogFragment.onStart】发生异常：设置返回键/外部点击监听失败 - " + e.getMessage());
                Log.e("InputDialogFragment", "onStart listener setup failed", e); // 保留 Logcat 错误
            }
        }

        // 2) 对视图应用主题颜色（白天/黑夜）
        View root = getView();
        if (root == null && getDialog() != null) {
            root = getDialog().findViewById(android.R.id.content);
        }
        if (root != null) {
            applyThemeColorsToDialog(root);
        }


    }

    /**
     * 设置对话框宽度的兼容性方法
     * 修改时间：20251117 22:25 - 支持Android 7.0+的所有版本
     */
    @SuppressWarnings("deprecation") // 修改时间：20251117 22:25 - 添加注解抑制过时API警告
    private void setDialogWidthCompatible(Dialog dialog) {
        int width;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) 及以上使用新的WindowMetrics API
            WindowManager windowManager = requireActivity().getSystemService(WindowManager.class);
            if (windowManager != null) {
                android.view.WindowMetrics windowMetrics = windowManager.getCurrentWindowMetrics();
                android.graphics.Rect bounds = windowMetrics.getBounds();
                width = (int) (bounds.width() * 0.90f);
            } else {
                // 回退到DisplayMetrics
                DisplayMetrics dm = new DisplayMetrics();
                requireActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
                width = (int) (dm.widthPixels * 0.90f);
            }
        } else {
            // Android 10 (API 29) 及以下使用DisplayMetrics
            DisplayMetrics dm = new DisplayMetrics();
            requireActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);
            width = (int) (dm.widthPixels * 0.90f);
        }

        dialog.getWindow().setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        listener = null;
    }

    // 【2025-11-22 03:05】新增功能：自定义对话框关闭拦截器
// 功能作用：当用户点击返回键、弹窗外区域或“取消”按钮时，弹出二次确认提示，
// 并确保原输入对话框保持打开状态，直到用户明确选择“取消”。
// 新增时间：2025年11月22日 03:05
    private void showDiscardConfirmation() {
        try {
            boolean isNight = isNightMode();
            int bgColor = ColorUtils.getThemeColor(requireContext(), "colorSurface", isNight);
            int textColor = ColorUtils.getThemeColor(requireContext(), "colorOnSurface", isNight);
            int buttonBgColor = ColorUtils.getThemeColor(requireContext(), "colorPrimary", isNight);
            int buttonTextColor = ColorUtils.getThemeColor(requireContext(), "colorOnPrimary", isNight);

            // 【2025-11-22 05:10】增强：为兼容 HyperOS/MIUI，使用自定义标题 TextView 替代系统标题
            // 功能作用：彻底解决 ROM 强制覆盖标题颜色和对齐方式的问题
            // 新增时间：2025年11月22日 05:10
            TextView customTitle = new TextView(requireContext());
            customTitle.setText("确认操作");
            customTitle.setTextColor(textColor);
            customTitle.setTextSize(20); // 与系统标题大小一致
            customTitle.setGravity(android.view.Gravity.CENTER);
            customTitle.setPadding(
                    (int) dpToPx(24),
                    (int) dpToPx(16),
                    (int) dpToPx(24),
                    (int) dpToPx(8)
            );

            AlertDialog confirmDialog = new AlertDialog.Builder(requireContext())
                    .setCustomTitle(customTitle) // ← 关键：使用自定义标题
                    .setMessage("您尚未保存分段记录。\n\n• 点击“保存”将记录当前输入\n• 点击“取消”将丢弃内容\n• 点击“继续编辑”可返回修改")
                    .setPositiveButton("保存", (d, which) -> {
                        if (btnOK != null) btnOK.performClick();
                    })
                    .setNegativeButton("取消", (d, which) -> {
                        dismiss(); // 用户明确放弃
                    })
                    .setNeutralButton("继续编辑", (d, which) -> {
                        // 保持原对话框打开
                    })
                    .create();

            // 设置背景色
            if (confirmDialog.getWindow() != null) {
                confirmDialog.getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(bgColor));
            }

            confirmDialog.setOnShowListener(d -> {
                try {
                    // 消息文本颜色
                    TextView messageView = confirmDialog.findViewById(android.R.id.message);
                    if (messageView != null) {
                        messageView.setTextColor(textColor);
                        messageView.invalidate();
                    }

                    // 按钮颜色
                    Button positive = confirmDialog.getButton(AlertDialog.BUTTON_POSITIVE);
                    Button negative = confirmDialog.getButton(AlertDialog.BUTTON_NEGATIVE);
                    Button neutral = confirmDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
                    for (Button btn : new Button[]{positive, negative, neutral}) {
                        if (btn != null) {
                            btn.setBackgroundColor(buttonBgColor);
                            btn.setTextColor(buttonTextColor);
                            btn.invalidate();
                        }
                    }
                } catch (Exception innerE) {
                    LogUtils.log("【InputDialogFragment.showDiscardConfirmation】设置弹窗样式时发生异常：" + innerE.getMessage());
                    Log.e("InputDialogFragment", "设置‘确认操作’对话框样式失败", innerE);
                }
            });

            confirmDialog.show();
        } catch (Exception e) {
            LogUtils.log("【InputDialogFragment.showDiscardConfirmation】弹出二次确认框失败：" + e.getMessage());
            Log.e("InputDialogFragment", "弹出‘确认操作’对话框失败", e);
            // 安全兜底
            dismiss();
        }
    }

    // ----------------------
    // 主题相关（读取宿主 Activity 的 preferences）
    // ----------------------

    private boolean isNightMode() {
        SharedPreferences prefs = requireActivity().getPreferences(Context.MODE_PRIVATE);
        return prefs.getBoolean("isNight", false);
    }

    /**
     * 应用颜色到对话框（并明确设置按钮背景 + 文字颜色）
     * 修改时间：20251117 22:35 - 添加Spinner颜色设置
     * 修改时间：20251118 12:42 - 修正Spinner颜色问题，使用CustomSpinnerAdapter
     * 修改时间：20251118 12:45 - 修正CustomSpinnerAdapter无法获取isNightMode状态的错误
     * 修改时间：20251118 12:59 - 修正：Spinner Adapter直接传入背景色，不再传递夜间模式状态。
     */
    private void applyThemeColorsToDialog(@NonNull View root) {
        boolean night = isNightMode();

        // 使用 ColorUtils 获取颜色
        // 使用优化后的 ColorUtils 方法
        int darkGray = ColorUtils.getDarkGray(requireContext(), night);   // #333333
        int lightGray = ColorUtils.getLightGray(requireContext(), night); // #EEEEEE
        int textDark = ColorUtils.getBlack(requireContext(), night);      // #121212
        int textLight = ColorUtils.getLightGray(requireContext(), night); // #EEEEEE
        int white = ColorUtils.getWhite(requireContext(), night);         // #FFFFFF

        final int bgColor;
        final int textColor;
        final int buttonBgColor;
        final int buttonTextColor;
        final int editTextBgColor;

        if (night) {
            // 修改时间：20251118 12:42 - 将背景色改为深色以适应夜间模式
            bgColor = darkGray;            // 对话框背景
            textColor = textLight;         // 普通文字颜色 (白色/浅灰色)
            buttonBgColor = textDark;      // 按钮背景 (深色)
            buttonTextColor = textLight;   // 按钮文字 (白色/浅灰色)
            editTextBgColor = darkGray;    // 输入框背景（夜间用深灰）
        } else {
            bgColor = lightGray;
            textColor = textDark;
            buttonBgColor = lightGray;
            buttonTextColor = textDark;
            editTextBgColor = white;       // 输入框背景（白天用白色）
        }

        // 设置根背景（容错）
        try { root.setBackgroundColor(bgColor); } catch (Exception ignored) {}

        // 修改时间：20251117 22:35 - 设置Spinner颜色
        Spinner spnCategory = root.findViewById(R.id.spnCategory);
        if (spnCategory != null) {
            // 1. 设置Spinner背景颜色
            spnCategory.setBackgroundColor(editTextBgColor);
            try {
                // 尝试设置背景 tint (针对 AppCompat/Material 主题)
                ViewCompat.setBackgroundTintList(spnCategory, ColorStateList.valueOf(editTextBgColor));
            } catch (Exception e) {
                spnCategory.setBackgroundColor(editTextBgColor);
            }

            // 2. 修改时间：20251118 12:59 - 修正：Spinner Adapter直接传入背景色
            String[] categoryOptions = getResources().getStringArray(R.array.category_options);
            CustomSpinnerAdapter adapter = new CustomSpinnerAdapter(
                    requireContext(),
                    android.R.layout.simple_spinner_dropdown_item,
                    Arrays.asList(categoryOptions),
                    textColor, // 使用正确的文本颜色
                    bgColor      // 传递对话框背景色作为下拉列表背景色
            );
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spnCategory.setAdapter(adapter);

            // 恢复选中的值
            if (!selectedCategory.isEmpty()) {
                int selectionPosition = adapter.getPosition(selectedCategory);
                spnCategory.setSelection(selectionPosition);
            }
        }

        // 直接设置输入框颜色
        EditText edtDetail = root.findViewById(R.id.txtDetail);

        if (edtDetail != null) {
            edtDetail.setTextColor(textColor);
            edtDetail.setBackgroundColor(editTextBgColor);
            edtDetail.setHintTextColor(adjustAlpha(textColor, 0.7f));
        }

        // 递归应用颜色（按钮背景 + 文字都显式设置）
        applyColorsRecursive(root, bgColor, textColor, buttonBgColor, buttonTextColor);

        // 修改时间：20251118 12:42 - 移除原有的循环设置Spinner文字颜色的代码（已由CustomSpinnerAdapter处理）
    }

    private void applyColorsRecursive(@NonNull View view,
                                      int bgColor,
                                      int textColor,
                                      int buttonBgColor,
                                      int buttonTextColor) {
        // 修改时间：20251118 12:42 - 修正 ScrollView 背景色应该使用对话框背景色
        if (view instanceof ScrollView){
            // 将 ScrollView 的背景设置为对话框的背景色
            ColorDrawable colorDrawable = new ColorDrawable(bgColor);
            view.setBackground(colorDrawable);
        }
        if (view instanceof TextView) {
            // 确保不影响到 Spinner 内部的 TextView
            if (!(view.getParent() instanceof Spinner)) {
                ((TextView) view).setTextColor(textColor);
            }
        }

        if (view instanceof Button) {
            Button b = (Button) view;
            // 明确设置背景 tint 和文字颜色，避免 drawable 样式覆盖
            ViewCompat.setBackgroundTintList(b, ColorStateList.valueOf(buttonBgColor));
            b.setTextColor(buttonTextColor);
            // 如果按钮上仍有默认的分层 Drawable（ripple 等），以上 tint 大多数情况下能正常生效
        }

        if (view instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) view;
            for (int i = 0; i < vg.getChildCount(); i++) {
                View child = vg.getChildAt(i);
                applyColorsRecursive(child, bgColor, textColor, buttonBgColor, buttonTextColor);
            }
        }
    }

    // ----------------------
    // 资源 & 视图查找辅助（不改布局结构，只做宽容查找）
    // ----------------------

    private int resolveLayoutResId() {
        Context ctx = requireContext();
        int resId;
        for (String name : CANDIDATE_LAYOUT_NAMES) {
            resId = ctx.getResources().getIdentifier(name, "layout", ctx.getPackageName());
            if (resId != 0) return resId;
        }
        return 0;
    }

    @Nullable
    @SuppressWarnings("unchecked") // 修改时间：20251117 22:20 - 添加注解抑制未经检查的类型转换警告
    private <T extends View> T findViewByIdIfExists(@NonNull View root, int id) {
        try {
            View v = root.findViewById(id);
            return (T) v;
        } catch (Exception ignored) {}
        return null;
    }

    @Nullable
    private EditText findEditTextFallback(@NonNull View root) {
        if (root instanceof EditText) return (EditText) root;
        if (root instanceof ViewGroup) {
            final ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                EditText result = findEditTextFallback(vg.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    @Nullable
    private EditText findEditTextFallbackExcept(@NonNull View root, @Nullable EditText except) {
        if (root instanceof EditText && root != except) return (EditText) root;
        if (root instanceof ViewGroup) {
            final ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                EditText result = findEditTextFallbackExcept(vg.getChildAt(i), except);
                if (result != null) return result;
            }
        }
        return null;
    }

    @Nullable
    private Button findButtonFallback(@NonNull View root) {
        if (root instanceof Button) return (Button) root;
        if (root instanceof ViewGroup) {
            final ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                Button result = findButtonFallback(vg.getChildAt(i));
                if (result != null) return result;
            }
        }
        return null;
    }

    @Nullable
    private Button findButtonFallbackExcept(@NonNull View root, @Nullable Button except) {
        if (root instanceof Button && root != except) return (Button) root;
        if (root instanceof ViewGroup) {
            final ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                Button result = findButtonFallbackExcept(vg.getChildAt(i), except);
                if (result != null) return result;
            }
        }
        return null;
    }
    // ----------------------
    // 颜色处理辅助方法
    // ----------------------
    /**
     * 调整颜色的透明度
     * @param color 原始颜色
     * @param factor 透明度因子 (0.0f - 1.0f)
     * @return 调整后的颜色
     */
    private int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }

    // 【2025-11-22 05:15】新增：DP 转 PX 工具方法，用于自定义标题内边距
// 功能作用：确保自定义标题在不同屏幕密度下显示一致
// 新增时间：2025年11月22日 05:15
    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}