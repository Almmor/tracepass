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

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡片数据管理器 - 持久化存储当前卡片数据
 * 实现卡片数据在页面切换时的保持，直到读取新卡片才更新
 */
public class CardDataManager {
    private static final String PREFS_NAME = "card_data_prefs";
    private static final String KEY_CARD_HTML = "card_html";
    private static final String KEY_TRANS_ENTRIES = "trans_entries";
    private static final String KEY_CARD_ID = "card_id";
    private static final String KEY_HAS_DATA = "has_data";
    
    private static CardDataManager instance;
    private final SharedPreferences prefs;
    
    // 内存缓存
    private String cardHtml;
    private String[] transEntries;
    private String cardId;
    private boolean hasData;
    
    private CardDataManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadFromPrefs();
    }
    
    public static synchronized CardDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new CardDataManager(context);
        }
        return instance;
    }
    
    /**
     * 从 SharedPreferences 加载数据
     */
    private void loadFromPrefs() {
        hasData = prefs.getBoolean(KEY_HAS_DATA, false);
        cardHtml = prefs.getString(KEY_CARD_HTML, null);
        cardId = prefs.getString(KEY_CARD_ID, null);
        
        String transJson = prefs.getString(KEY_TRANS_ENTRIES, null);
        if (transJson != null) {
            try {
                JSONArray jsonArray = new JSONArray(transJson);
                transEntries = new String[jsonArray.length()];
                for (int i = 0; i < jsonArray.length(); i++) {
                    transEntries[i] = jsonArray.getString(i);
                }
            } catch (JSONException e) {
                transEntries = null;
            }
        }
    }
    
    /**
     * 保存卡片数据
     */
    public void saveCardData(String html, String[] entries, String id) {
        this.cardHtml = html;
        this.transEntries = entries;
        this.cardId = id;
        this.hasData = true;
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_HAS_DATA, true);
        editor.putString(KEY_CARD_HTML, html);
        editor.putString(KEY_CARD_ID, id);
        
        if (entries != null) {
            JSONArray jsonArray = new JSONArray();
            for (String entry : entries) {
                jsonArray.put(entry);
            }
            editor.putString(KEY_TRANS_ENTRIES, jsonArray.toString());
        } else {
            editor.remove(KEY_TRANS_ENTRIES);
        }
        
        editor.apply();
    }
    
    /**
     * 清除卡片数据
     */
    public void clearCardData() {
        this.cardHtml = null;
        this.transEntries = null;
        this.cardId = null;
        this.hasData = false;
        
        SharedPreferences.Editor editor = prefs.edit();
        editor.clear();
        editor.apply();
    }
    
    /**
     * 检查是否有卡片数据
     */
    public boolean hasCardData() {
        return hasData && cardHtml != null;
    }
    
    /**
     * 获取卡片 HTML
     */
    public String getCardHtml() {
        return cardHtml;
    }
    
    /**
     * 获取交易记录
     */
    public String[] getTransEntries() {
        return transEntries;
    }
    
    /**
     * 获取卡片 ID
     */
    public String getCardId() {
        return cardId;
    }
    
    /**
     * 检查是否是同一张卡片
     */
    public boolean isSameCard(String id) {
        return hasData && cardId != null && cardId.equals(id);
    }
}
