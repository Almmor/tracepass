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

package com.almmor.tracepass;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.almmor.tracepass.nfc.NfcManager;
import com.almmor.tracepass.ui.AboutPage;
import com.almmor.tracepass.ui.MainPage;
import com.almmor.tracepass.ui.NfcPage;
import com.almmor.tracepass.ui.SettingsPage;
import com.almmor.tracepass.ui.SpanFormatter;

import org.json.JSONObject;

/**
 * 主活动 - 支持底部导航栏、小白条适配和卡片数据持久化
 */
public class MainActivity extends Activity {

    private enum NavItem {
        OVERVIEW, ACCOUNT, TRANSACTION, SETTINGS
    }

    private NavItem currentNav = NavItem.OVERVIEW;
    private CardDataManager cardManager;
    private NfcManager nfcManager;
    
    private TextView contentText;
    private ScrollView contentScroll;
    private ScrollView transScroll;
    private LinearLayout transListContainer;
    private LinearLayout bottomNav;
    private LinearLayout navOverview, navAccount, navTransaction, navSettings;
    private SettingsPage settingsPage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupImmersiveMode();
        setContentView(R.layout.activity_main);

        cardManager = CardDataManager.getInstance(this);
        // 每次进入APP初始化数据
        cardManager.clearCardData();
        nfcManager = new NfcManager(this);
        settingsPage = new SettingsPage(this);
        settingsPage.setCallback(new SettingsPage.SettingsCallback() {
            @Override
            public void onThemeChanged(boolean darkMode) {
                if (isFinishing()) return;
                if (currentNav == NavItem.SETTINGS) loadSettingsPage();
            }
            @Override
            public void onDataChanged() {
                if (isFinishing()) return;
                refreshCurrentPage();
            }
        });
        
        initViews();
        adaptNavigationBarHeight();
        applySavedTheme();
        if (!isFinishing()) loadOverviewPage();
        if (!isFinishing()) onNewIntent(getIntent());
    }

    private void setupImmersiveMode() {
        if (isFinishing() || isDestroyed()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            if (window == null) return;
            window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
            window.setStatusBarColor(0x00000000);
            window.setNavigationBarColor(0x00000000);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.setNavigationBarContrastEnforced(false);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                WindowManager.LayoutParams params = window.getAttributes();
                params.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
                window.setAttributes(params);
            }
        }
    }

    private void adaptNavigationBarHeight() {
        if (isFinishing()) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Window window = getWindow();
            if (window == null) return;
            window.setDecorFitsSystemWindows(false);
            bottomNav = findViewById(R.id.bottomNav);
            if (bottomNav == null) return;
            bottomNav.setOnApplyWindowInsetsListener((v, insets) -> {
                if (isFinishing() || isDestroyed()) return insets;
                Insets navBars = insets.getInsets(WindowInsets.Type.navigationBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), 8 + navBars.bottom);
                return insets;
            });
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            bottomNav = findViewById(R.id.bottomNav);
            if (bottomNav == null) return;
            bottomNav.setOnApplyWindowInsetsListener((v, insets) -> {
                if (isFinishing() || isDestroyed()) return insets;
                int h = insets.getSystemWindowInsetBottom();
                if (h > 0) v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), 8 + h);
                return insets;
            });
        }
    }

    private void initViews() {
        contentText = findViewById(R.id.contentText);
        contentScroll = findViewById(R.id.contentScroll);
        transScroll = findViewById(R.id.transScroll);
        transListContainer = findViewById(R.id.transListContainer);
        
        Typeface tf = ThisApplication.getFontResource(R.string.font_oem1);
        TextView tvAppName = findViewById(R.id.txtAppName);
        tvAppName.setTypeface(tf);
        contentText.setMovementMethod(LinkMovementMethod.getInstance());
        
        navOverview = findViewById(R.id.navOverview);
        navAccount = findViewById(R.id.navAccount);
        navTransaction = findViewById(R.id.navTransaction);
        navSettings = findViewById(R.id.navSettings);
        updateNavSelection();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!isFinishing()) {
            nfcManager.readCard(intent, new NfcPage(this));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        nfcManager.onResume();
        if (!isFinishing() && currentNav == NavItem.SETTINGS) loadSettingsPage();
    }

    @Override
    protected void onPause() {
        super.onPause();
        nfcManager.onPause();
    }

    @Override
    protected void onDestroy() {
        nfcManager.onPause();
        super.onDestroy();
    }

    @Override
    public void setIntent(Intent intent) {
        if (isFinishing()) {
            super.setIntent(intent);
            return;
        }
        if (NfcPage.isSendByMe(intent)) {
            refreshCurrentPage();
        } else if (AboutPage.isSendByMe(intent)) {
            showAboutContent();
        } else {
            super.setIntent(intent);
        }
    }

    // ========== 导航 ==========

    public void onNavOverview(View v) {
        if (isFinishing()) return;
        if (currentNav != NavItem.OVERVIEW) { currentNav = NavItem.OVERVIEW; updateNavSelection(); loadOverviewPage(); }
    }
    public void onNavAccount(View v) {
        if (isFinishing()) return;
        if (currentNav != NavItem.ACCOUNT) { currentNav = NavItem.ACCOUNT; updateNavSelection(); loadAccountPage(); }
    }
    public void onNavTransaction(View v) {
        if (isFinishing()) return;
        if (currentNav != NavItem.TRANSACTION) { currentNav = NavItem.TRANSACTION; updateNavSelection(); loadTransactionPage(); }
    }
    public void onNavSettings(View v) {
        if (isFinishing()) return;
        if (currentNav != NavItem.SETTINGS) { currentNav = NavItem.SETTINGS; updateNavSelection(); loadSettingsPage(); }
    }
    public void onSwitch2AboutPage(View v) { if (!isFinishing()) showAboutContent(); }

    // ========== 页面切换 ==========

    private void showHtmlView() {
        contentScroll.setVisibility(View.VISIBLE);
        transScroll.setVisibility(View.GONE);
    }

    private void showTransView() {
        contentScroll.setVisibility(View.GONE);
        transScroll.setVisibility(View.VISIBLE);
    }

    private void loadOverviewPage() {
        showHtmlView();
        contentScroll.scrollTo(0, 0);
        if (cardManager.hasCardData()) {
            StringBuilder html = new StringBuilder();
            html.append("<").append(SPEC.TAG_BLK).append(">");
            html.append("<").append(SPEC.TAG_H1).append(">卡片概览</").append(SPEC.TAG_H1).append(">");
            html.append("<br /><").append(SPEC.TAG_SP).append(" /><br />");
            html.append("<").append(SPEC.TAG_TEXT).append(">卡片已读取，点击下方导航查看详细信息</").append(SPEC.TAG_TEXT).append(">");
            html.append("<br /><br />");
            html.append("<").append(SPEC.TAG_TIP).append(">账户：查看余额和卡片信息<br />交易：查看消费记录</").append(SPEC.TAG_TIP).append(">");
            html.append("</").append(SPEC.TAG_BLK).append(">");
            contentText.setText(new SpanFormatter(null).toSpanned(html.toString()));
        } else {
            contentText.setText(MainPage.getContent(this));
        }
    }

    private void loadAccountPage() {
        showHtmlView();
        contentScroll.scrollTo(0, 0);
        if (!cardManager.hasCardData()) { showNoDataMessage(); return; }
        String cardHtml = cardManager.getCardHtml();
        if (cardHtml == null || cardHtml.isEmpty()) {
            cardHtml = "<" + SPEC.TAG_TIP + ">无卡片数据</" + SPEC.TAG_TIP + ">";
        } else {
            cardHtml = "<" + SPEC.TAG_BLK + "><" + SPEC.TAG_H1 + ">账户详情</" + SPEC.TAG_H1 + ">"
                    + "<br /><" + SPEC.TAG_SP + " /><br />" + cardHtml + "</" + SPEC.TAG_BLK + ">";
        }
        contentText.setText(new SpanFormatter(NfcPage.getActionHandler(this)).toSpanned(cardHtml));
    }

    private void loadTransactionPage() {
        if (!cardManager.hasCardData()) { showHtmlView(); showNoDataMessage(); return; }
        showTransView();
        transScroll.scrollTo(0, 0);
        transListContainer.removeAllViews();

        String[] entries = cardManager.getTransEntries();
        if (entries == null || entries.length == 0) {
            showHtmlView();
            showNoDataMessage();
            return;
        }

        // 标题
        TextView title = new TextView(this);
        title.setText("交易记录");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        title.setTypeface(null, Typeface.BOLD);
        title.setPadding(0, 8, 0, 16);
        transListContainer.addView(title);

        // 添加每笔交易卡片
        for (int i = 0; i < entries.length; i++) {
            addTransCard(entries[i], i);
        }
    }

    /**
     * 添加一笔交易卡片（液态玻璃风格，可展开/收起）
     */
    private void addTransCard(String entry, int index) {
        JSONObject json;
        try {
            json = new JSONObject(entry);
        } catch (Exception e) {
            // 旧格式回退
            addFallbackCard(entry, index);
            return;
        }

        // 卡片容器
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_glass_trans_card);
        int dp8 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        int dp12 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        int dp16 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 16, getResources().getDisplayMetrics());
        card.setPadding(dp16, dp12, dp16, dp12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp8;
        card.setLayoutParams(lp);

        // === 收起状态的行 ===
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);
        headerRow.setClickable(true);
        headerRow.setFocusable(true);

        // 图标
        String icon = json.optString("icon", "\uD83D\uDCCB");
        TextView tvIcon = new TextView(this);
        tvIcon.setText(icon);
        tvIcon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iconLp.rightMargin = dp12;
        tvIcon.setLayoutParams(iconLp);
        headerRow.addView(tvIcon);

        // 中间信息（类型+时间+站点）
        LinearLayout midInfo = new LinearLayout(this);
        midInfo.setOrientation(LinearLayout.VERTICAL);
        midInfo.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvType = new TextView(this);
        tvType.setText(json.optString("type", "交易"));
        tvType.setTextColor(0xE6FFFFFF);
        tvType.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        tvType.setTypeface(null, Typeface.BOLD);
        midInfo.addView(tvType);

        TextView tvSubInfo = new TextView(this);
        String subInfo = json.optString("date", "") + " " + json.optString("time", "");
        String station = json.optString("station", "");
        String line = json.optString("line", "");
        if (!station.isEmpty() || !line.isEmpty()) {
            subInfo += " " + line + " " + station;
        }
        tvSubInfo.setText(subInfo);
        tvSubInfo.setTextColor(0x80FFFFFF);
        tvSubInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        midInfo.addView(tvSubInfo);

        headerRow.addView(midInfo);

        // 右侧金额
        TextView tvAmount = new TextView(this);
        String sign = json.optString("sign", "-");
        String amount = json.optString("amount", "0.00");
        tvAmount.setText(sign + "\u00A5" + amount);
        tvAmount.setTextColor(0x00d4ff);
        tvAmount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvAmount.setTypeface(null, Typeface.BOLD);
        tvAmount.setGravity(Gravity.END);
        headerRow.addView(tvAmount);

        // 展开箭头
        TextView tvArrow = new TextView(this);
        tvArrow.setText("\u25BC"); // ▼
        tvArrow.setTextColor(0x60FFFFFF);
        tvArrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
        LinearLayout.LayoutParams arrowLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        arrowLp.leftMargin = dp8;
        tvArrow.setLayoutParams(arrowLp);
        headerRow.addView(tvArrow);

        card.addView(headerRow);

        // === 展开详情区域（默认隐藏） ===
        LinearLayout detailPanel = new LinearLayout(this);
        detailPanel.setOrientation(LinearLayout.VERTICAL);
        detailPanel.setVisibility(View.GONE);
        detailPanel.setBackgroundResource(R.drawable.bg_glass_trans_detail);
        detailPanel.setPadding(dp12, dp12, dp12, dp12);
        LinearLayout.LayoutParams detailLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        detailLp.topMargin = dp8;
        detailPanel.setLayoutParams(detailLp);

        // 详情行
        String[][] detailFields = {
            {"SFI", json.optString("sfi", "-")},
            {"交易类型", json.optString("type", "-")},
            {"终端号", json.optString("terminal", "-")},
            {"时间戳", json.optString("timestamp", "-")},
            {"线路/站点", (json.optString("line", "") + " " + json.optString("station", "")).trim()},
            {"余额", "\u00A5" + json.optString("balance", "0.00")},
            {"城市", json.optString("city", "-")},
            {"预留字段", json.optString("reserved", "-")},
            {"交易通道", json.optString("channel", "NFC")},
        };

        for (String[] field : detailFields) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics()), 0, (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 3, getResources().getDisplayMetrics()));

            TextView label = new TextView(this);
            label.setText(field[0]);
            label.setTextColor(0x80FFFFFF);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            label.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.4f));
            row.addView(label);

            TextView value = new TextView(this);
            value.setText(field[1]);
            value.setTextColor(0xCCFFFFFF);
            value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            value.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f));
            row.addView(value);

            detailPanel.addView(row);
        }

        card.addView(detailPanel);

        // 点击展开/收起
        final LinearLayout detail = detailPanel;
        final TextView arrow = tvArrow;
        headerRow.setOnClickListener(v -> {
            if (detail.getVisibility() == View.GONE) {
                detail.setVisibility(View.VISIBLE);
                arrow.setText("\u25B2"); // ▲
            } else {
                detail.setVisibility(View.GONE);
                arrow.setText("\u25BC"); // ▼
            }
        });

        transListContainer.addView(card);
    }

    /**
     * 旧格式交易记录回退显示
     */
    private void addFallbackCard(String entry, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setBackgroundResource(R.drawable.bg_glass_trans_card);
        int dp12 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 12, getResources().getDisplayMetrics());
        int dp8 = (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 8, getResources().getDisplayMetrics());
        card.setPadding(dp12, dp12, dp12, dp12);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp8;
        card.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(entry);
        tv.setTextColor(0xCCFFFFFF);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        card.addView(tv);
        transListContainer.addView(card);
    }

    private void loadSettingsPage() {
        showHtmlView();
        contentScroll.scrollTo(0, 0);
        contentText.setText(settingsPage.getContent());
    }

    private void showAboutContent() {
        showHtmlView();
        contentText.setText(AboutPage.getContent(this));
    }

    private void showNoDataMessage() {
        showHtmlView();
        StringBuilder html = new StringBuilder();
        html.append("<").append(SPEC.TAG_BLK).append(">");
        html.append("<").append(SPEC.TAG_H1).append(">无卡片数据</").append(SPEC.TAG_H1).append(">");
        html.append("<br /><").append(SPEC.TAG_SP).append(" /><br />");
        html.append("<").append(SPEC.TAG_TEXT).append(">请先读取 NFC 卡片</").append(SPEC.TAG_TEXT).append(">");
        html.append("<br /><br />");
        html.append("<").append(SPEC.TAG_TIP).append(">将卡片靠近手机背面即可读取</").append(SPEC.TAG_TIP).append(">");
        html.append("</").append(SPEC.TAG_BLK).append(">");
        contentText.setText(new SpanFormatter(null).toSpanned(html.toString()));
    }

    private void refreshCurrentPage() {
        if (isFinishing()) return;
        switch (currentNav) {
            case OVERVIEW: loadOverviewPage(); break;
            case ACCOUNT: loadAccountPage(); break;
            case TRANSACTION: loadTransactionPage(); break;
            case SETTINGS: loadSettingsPage(); break;
        }
    }

    private void updateNavSelection() {
        if (isFinishing()) return;
        float sel = 1.0f, norm = 0.5f;
        navOverview.setAlpha(currentNav == NavItem.OVERVIEW ? sel : norm);
        navAccount.setAlpha(currentNav == NavItem.ACCOUNT ? sel : norm);
        navTransaction.setAlpha(currentNav == NavItem.TRANSACTION ? sel : norm);
        navSettings.setAlpha(currentNav == NavItem.SETTINGS ? sel : norm);
    }

    private void applySavedTheme() {
        if (isFinishing() || settingsPage == null) return;
        if (!settingsPage.isDarkMode()) {
            Window window = getWindow();
            if (window == null) return;
            window.setBackgroundDrawable(new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{0xFFF0F2F5, 0xFFE8ECF1, 0xFFDDE4ED}
            ));
            window.setStatusBarColor(0xFFE8ECF1);
            window.setNavigationBarColor(0xFFE8ECF1);
        }
    }

    @Override
    public void onBackPressed() {
        if (isFinishing()) { super.onBackPressed(); return; }
        if (currentNav != NavItem.OVERVIEW) {
            currentNav = NavItem.OVERVIEW;
            updateNavSelection();
            loadOverviewPage();
        } else {
            super.onBackPressed();
        }
    }
}
