/* NFCard is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 3 of the License, or
(at your option) any later version.

NFCard is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Wget.  If not, see <http://www.gnu.org/licenses/>.

Additional permission under GNU GPL version 3 section 7 */

package com.almmor.tracepass.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Environment;
import android.view.Window;
import android.widget.Toast;

import com.almmor.tracepass.CardDataManager;
import com.almmor.tracepass.R;
import com.almmor.tracepass.SPEC;
import com.almmor.tracepass.ThisApplication;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 设置页面 - 支持深色/浅色主题切换和数据导入导出
 * 界面内直接交互，不使用弹窗
 */
public class SettingsPage {
    
    private static final String PREFS_NAME = "app_settings";
    private static final String KEY_DARK_MODE = "dark_mode";
    
    private final Activity activity;
    private final SharedPreferences prefs;
    private SettingsCallback callback;
    
    public interface SettingsCallback {
        void onThemeChanged(boolean darkMode);
        void onDataChanged();
    }
    
    public SettingsPage(Activity activity) {
        this.activity = activity;
        this.prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    
    public void setCallback(SettingsCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 切换主题模式 - 实时生效
     */
    public void toggleTheme() {
        boolean newDarkMode = !isDarkMode();
        prefs.edit().putBoolean(KEY_DARK_MODE, newDarkMode).apply();
        applyTheme(newDarkMode);
        
        if (callback != null) {
            callback.onThemeChanged(newDarkMode);
        }
        
        Toast.makeText(activity, 
            "已切换到" + (newDarkMode ? "深色" : "浅色") + "模式", 
            Toast.LENGTH_SHORT).show();
    }
    
    /**
     * 实时应用主题
     */
    public void applyTheme(boolean darkMode) {
        Window window = activity.getWindow();
        
        if (darkMode) {
            window.setBackgroundDrawable(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF1a1a2e, 0xFF16213e, 0xFF0f3460}
            ));
            window.setStatusBarColor(0x00000000);
            window.setNavigationBarColor(0x00000000);
        } else {
            window.setBackgroundDrawable(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFF0F2F5, 0xFFE8ECF1, 0xFFDDE4ED}
            ));
            window.setStatusBarColor(0xFFE8ECF1);
            window.setNavigationBarColor(0xFFE8ECF1);
        }
    }
    
    /**
     * 检查是否为深色模式
     */
    public boolean isDarkMode() {
        return prefs.getBoolean(KEY_DARK_MODE, true);
    }
    
    /**
     * 导出卡片数据到 JSON 文件
     */
    public void exportCardData() {
        CardDataManager cardManager = CardDataManager.getInstance(activity);
        
        if (!cardManager.hasCardData()) {
            Toast.makeText(activity, "没有可导出的卡片数据", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            JSONObject exportData = new JSONObject();
            exportData.put("export_time", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).format(new Date()));
            exportData.put("app_version", ThisApplication.version());
            exportData.put("card_id", cardManager.getCardId());
            exportData.put("card_html", cardManager.getCardHtml());
            
            if (cardManager.getTransEntries() != null) {
                JSONArray transArray = new JSONArray();
                for (String entry : cardManager.getTransEntries()) {
                    transArray.put(entry);
                }
                exportData.put("transactions", transArray);
            }
            
            String fileName = "nfcard_export_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CHINA).format(new Date()) + ".json";
            File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (!downloadDir.exists()) downloadDir.mkdirs();
            File exportFile = new File(downloadDir, fileName);
            
            FileOutputStream fos = new FileOutputStream(exportFile);
            fos.write(exportData.toString(2).getBytes("UTF-8"));
            fos.close();
            
            Toast.makeText(activity, "数据已导出到:\n" + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
            
        } catch (JSONException | IOException e) {
            Toast.makeText(activity, "导出失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 从 JSON 文件导入卡片数据 - 显示文件选择列表
     */
    public void importCardData() {
        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File[] files = downloadDir.listFiles((dir, name) -> name.startsWith("nfcard_export_") && name.endsWith(".json"));
        
        if (files == null || files.length == 0) {
            Toast.makeText(activity, "未找到可导入的数据文件\n请将导出文件放入 Downloads 目录", Toast.LENGTH_LONG).show();
            return;
        }
        
        List<File> fileList = new ArrayList<>();
        for (File f : files) fileList.add(f);
        fileList.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        
        // 使用弹窗选择文件（这是导入功能必需的）
        String[] fileNames = new String[fileList.size()];
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA);
        for (int i = 0; i < fileList.size(); i++) {
            fileNames[i] = sdf.format(new Date(fileList.get(i).lastModified())) + "\n" + fileList.get(i).getName();
        }
        
        new AlertDialog.Builder(activity, getDialogTheme())
            .setTitle("选择要导入的文件")
            .setItems(fileNames, (dialog, which) -> {
                importFromFile(fileList.get(which));
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 从指定文件导入数据
     */
    private void importFromFile(File file) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            
            JSONObject data = new JSONObject(sb.toString());
            
            String cardHtml = data.optString("card_html", null);
            String cardId = data.optString("card_id", null);
            
            JSONArray transArray = data.optJSONArray("transactions");
            String[] transEntries = null;
            if (transArray != null) {
                transEntries = new String[transArray.length()];
                for (int i = 0; i < transArray.length(); i++) {
                    transEntries[i] = transArray.getString(i);
                }
            }
            
            if (cardHtml != null && !cardHtml.isEmpty()) {
                CardDataManager.getInstance(activity).saveCardData(cardHtml, transEntries, cardId);
                Toast.makeText(activity, "数据导入成功", Toast.LENGTH_SHORT).show();
                if (callback != null) {
                    callback.onDataChanged();
                }
            } else {
                Toast.makeText(activity, "文件中没有有效的卡片数据", Toast.LENGTH_SHORT).show();
            }
            
        } catch (IOException | JSONException e) {
            Toast.makeText(activity, "导入失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * 清除所有数据
     */
    public void clearAllData() {
        new AlertDialog.Builder(activity, getDialogTheme())
            .setTitle("确认清除")
            .setMessage("这将清除所有卡片数据和设置，确定继续吗？")
            .setPositiveButton("确定", (dialog, which) -> {
                CardDataManager.getInstance(activity).clearCardData();
                prefs.edit().clear().apply();
                applyTheme(true);
                Toast.makeText(activity, "所有数据已清除", Toast.LENGTH_SHORT).show();
                if (callback != null) {
                    callback.onDataChanged();
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    /**
     * 显示关于信息
     */
    public void showAbout() {
        new AlertDialog.Builder(activity, getDialogTheme())
            .setTitle("关于 " + ThisApplication.name())
            .setMessage("版本: " + ThisApplication.version() + "\n\n" +
                       "基于 NFCard 开源项目\n" +
                       "支持 NFC 卡片读取与管理\n\n" +
                       "\u00A9 2024 迹录 TracePass")
            .setPositiveButton("确定", null)
            .show();
    }
    
    /**
     * 获取当前主题对应的 Dialog 样式
     */
    private int getDialogTheme() {
        return isDarkMode() ? 
            android.R.style.Theme_Material_Dialog_Alert : 
            android.R.style.Theme_Material_Light_Dialog_Alert;
    }
    
    /**
     * 获取设置页面内容 - 界面内可交互
     * 使用 t_action 标签实现点击事件
     */
    public CharSequence getContent() {
        StringBuilder html = new StringBuilder();
        html.append("<").append(SPEC.TAG_BLK).append(">");
        html.append("<").append(SPEC.TAG_H1).append(">设置</").append(SPEC.TAG_H1).append(">");
        html.append("<br />");
        html.append("<").append(SPEC.TAG_SP).append(" />");
        html.append("<br />");
        
        // 主题切换 - 可点击
        html.append("<").append(SPEC.TAG_LAB).append(">\u2699 主题设置</").append(SPEC.TAG_LAB).append(">");
        html.append("<br /><br />");
        html.append("<t_action_toggle_theme>");
        html.append("<").append(SPEC.TAG_TEXT).append(">");
        html.append(isDarkMode() ? "\u263E 当前：深色模式（点击切换浅色）" : "\u2600 当前：浅色模式（点击切换深色）");
        html.append("</").append(SPEC.TAG_TEXT).append(">");
        html.append("</t_action_toggle_theme>");
        html.append("<br /><br />");
        
        // 数据管理
        html.append("<").append(SPEC.TAG_LAB).append(">\uD83D\uDCCA 数据管理</").append(SPEC.TAG_LAB).append(">");
        html.append("<br /><br />");
        html.append("<").append(SPEC.TAG_TEXT).append(">");
        CardDataManager cardManager = CardDataManager.getInstance(activity);
        if (cardManager.hasCardData()) {
            html.append("已保存卡片数据");
        } else {
            html.append("暂无卡片数据");
        }
        html.append("</").append(SPEC.TAG_TEXT).append(">");
        html.append("<br /><br />");
        
        // 导出数据 - 可点击
        html.append("<t_action_export>");
        html.append("<").append(SPEC.TAG_TEXT).append(">");
        html.append("\uD83D\uDCE4 导出卡片数据");
        html.append("</").append(SPEC.TAG_TEXT).append(">");
        html.append("</t_action_export>");
        html.append("<br /><br />");
        
        // 导入数据 - 可点击
        html.append("<t_action_import>");
        html.append("<").append(SPEC.TAG_TEXT).append(">");
        html.append("\uD83D\uDCE5 导入卡片数据");
        html.append("</").append(SPEC.TAG_TEXT).append(">");
        html.append("</t_action_import>");
        html.append("<br /><br />");
        
        // 清除数据 - 可点击
        html.append("<t_action_clear>");
        html.append("<").append(SPEC.TAG_TEXT).append(">");
        html.append("\uD83D\uDDD1 清除所有数据");
        html.append("</").append(SPEC.TAG_TEXT).append(">");
        html.append("</t_action_clear>");
        html.append("<br /><br />");
        
        // 关于 - 可点击
        html.append("<t_action_about>");
        html.append("<").append(SPEC.TAG_TEXT).append(">");
        html.append("\u2139 关于应用");
        html.append("</").append(SPEC.TAG_TEXT).append(">");
        html.append("</t_action_about>");
        html.append("<br /><br />");
        
        html.append("<").append(SPEC.TAG_TIP).append(">");
        html.append("点击上方选项执行相应操作");
        html.append("</").append(SPEC.TAG_TIP).append(">");
        
        html.append("</").append(SPEC.TAG_BLK).append(">");
        
        return new SpanFormatter(getActionHandler()).toSpanned(html.toString());
    }
    
    /**
     * 获取设置页面的 ActionHandler
     */
    private SpanFormatter.ActionHandler getActionHandler() {
        return name -> {
            String action = name.toString();
            switch (action) {
                case "t_action_toggle_theme":
                    toggleTheme();
                    break;
                case "t_action_export":
                    exportCardData();
                    break;
                case "t_action_import":
                    importCardData();
                    break;
                case "t_action_clear":
                    clearAllData();
                    break;
                case "t_action_about":
                    showAbout();
                    break;
            }
        };
    }
}
