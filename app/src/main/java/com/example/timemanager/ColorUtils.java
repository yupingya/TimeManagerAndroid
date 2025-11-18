// ColorUtils.java
package com.example.timemanager;

import android.content.Context;
import android.content.res.Configuration;
import androidx.core.content.ContextCompat;

public class ColorUtils {

    /**
     * 根据手动夜间模式状态获取颜色
     */
    public static int getColorByMode(Context context, boolean isNight, int colorResId) {
        Configuration configuration = new Configuration(context.getResources().getConfiguration());
        if (isNight) {
            configuration.uiMode &= ~Configuration.UI_MODE_NIGHT_MASK;
            configuration.uiMode |= Configuration.UI_MODE_NIGHT_YES;
        } else {
            configuration.uiMode &= ~Configuration.UI_MODE_NIGHT_MASK;
            configuration.uiMode |= Configuration.UI_MODE_NIGHT_NO;
        }

        Context themedContext = context.createConfigurationContext(configuration);
        return ContextCompat.getColor(themedContext, colorResId);
    }

    /**
     * 获取主题颜色
     */
    public static int getThemeColor(Context context, String attributeName, boolean isNight) {
        int colorResId;
        switch (attributeName) {
            case "colorSurface":
            case "colorPrimary":
                colorResId = isNight ? R.color.dark_gray : R.color.light_gray;
                break;
            case "colorOnSurface":
            case "colorOnPrimary":
                colorResId = isNight ? R.color.light_gray : R.color.black;
                break;
            default:
                colorResId = isNight ? R.color.dark_gray : R.color.light_gray;
        }
        return getColorByMode(context, isNight, colorResId);
    }

    /**
     * 直接获取特定颜色（简化调用）
     */
    public static int getDarkGray(Context context, boolean isNight) {
        return getColorByMode(context, isNight, R.color.dark_gray);
    }

    public static int getLightGray(Context context, boolean isNight) {
        return getColorByMode(context, isNight, R.color.light_gray);
    }

    public static int getBlack(Context context, boolean isNight) {
        return getColorByMode(context, isNight, R.color.black);
    }

    public static int getWhite(Context context, boolean isNight) {
        return getColorByMode(context, isNight, R.color.light_gray);
    }
}