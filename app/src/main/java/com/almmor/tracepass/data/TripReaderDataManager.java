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

package com.almmor.tracepass.data;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * TripReader数据管理器
 * 从tripreader-data项目加载站点、线路、卡片名称等数据
 */
public class TripReaderDataManager {
    private static final String TAG = "TripReaderData";
    private static final String DATA_PATH = "data";
    
    private static TripReaderDataManager instance;
    private final Context context;
    
    // 缓存数据
    private final Map<String, StationInfo> stationCache = new HashMap<>();
    private final Map<String, String> cardNameCache = new HashMap<>();
    private boolean dataLoaded = false;
    
    public static synchronized TripReaderDataManager getInstance(Context context) {
        if (instance == null) {
            instance = new TripReaderDataManager(context);
        }
        return instance;
    }
    
    private TripReaderDataManager(Context context) {
        this.context = context.getApplicationContext();
        loadCardNames();
    }
    
    /**
     * 站点信息类
     */
    public static class StationInfo {
        public final String city;
        public final String code;
        public final String type;
        public final String line;
        public final String station;
        
        public StationInfo(String city, String code, String type, String line, String station) {
            this.city = city;
            this.code = code;
            this.type = type;
            this.line = line;
            this.station = station;
        }
        
        @Override
        public String toString() {
            if (station != null && !station.isEmpty()) {
                return line + " " + station;
            }
            return line;
        }
    }
    
    /**
     * 加载卡片名称数据
     */
    private void loadCardNames() {
        try {
            AssetManager assets = context.getAssets();
            InputStream is = assets.open(DATA_PATH + "/cardname_tu.csv");
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // 跳过表头
                }
                
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    String iin = parts[0].trim();
                    String issuer = parts[1].trim();
                    String name = parts[2].trim();
                    if (!name.isEmpty()) {
                        cardNameCache.put(iin, name);
                        cardNameCache.put(issuer, name);
                    }
                }
            }
            reader.close();
            Log.d(TAG, "Loaded " + cardNameCache.size() + " card names");
        } catch (IOException e) {
            Log.e(TAG, "Failed to load card names", e);
        }
    }
    
    /**
     * 根据终端号查找站点信息
     * @param terminalHex 终端号（6字节十六进制）
     * @return 站点信息，找不到返回null
     */
    public StationInfo findStationByTerminal(String terminalHex) {
        if (terminalHex == null || terminalHex.length() < 6) {
            return null;
        }
        
        // 尝试从缓存查找
        StationInfo cached = stationCache.get(terminalHex);
        if (cached != null) {
            return cached;
        }
        
        // 从终端号提取城市代码和站点代码
        // 终端号格式：前2字节通常是城市代码，后4字节是站点/终端标识
        String cityCode = terminalHex.substring(0, 4);
        String stationCode = terminalHex.substring(4);
        
        // 根据城市代码加载对应城市数据
        String cityName = getCityNameByCode(cityCode);
        if (cityName != null) {
            StationInfo info = loadCityStationData(cityName, terminalHex);
            if (info != null) {
                stationCache.put(terminalHex, info);
                return info;
            }
        }
        
        return null;
    }
    
    /**
     * 根据城市代码获取城市名称
     */
    private String getCityNameByCode(String cityCode) {
        // 常见城市代码映射（根据tripreader-data目录结构）
        Map<String, String> cityMap = new HashMap<>();
        cityMap.put("0100", "beijing");
        cityMap.put("0200", "shanghai");
        cityMap.put("0300", "tianjin");
        cityMap.put("0400", "chongqing");
        // 可以根据需要添加更多城市
        
        return cityMap.get(cityCode.toUpperCase());
    }
    
    /**
     * 加载城市站点数据
     */
    private StationInfo loadCityStationData(String cityName, String terminalHex) {
        try {
            AssetManager assets = context.getAssets();
            String[] files = assets.list(DATA_PATH + "/" + cityName);
            
            if (files == null) return null;
            
            for (String file : files) {
                if (file.endsWith("_tu.csv") || file.endsWith("_cu.csv")) {
                    StationInfo info = searchInCsvFile(
                        DATA_PATH + "/" + cityName + "/" + file, 
                        terminalHex
                    );
                    if (info != null) {
                        return info;
                    }
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load city data: " + cityName, e);
        }
        return null;
    }
    
    /**
     * 在CSV文件中搜索站点信息
     */
    private StationInfo searchInCsvFile(String filePath, String terminalHex) {
        try {
            AssetManager assets = context.getAssets();
            InputStream is = assets.open(filePath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
            
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                
                String[] parts = line.split(",");
                if (parts.length >= 5) {
                    String code = parts[1].trim();
                    // 检查代码是否匹配终端号的后缀
                    if (terminalHex.toUpperCase().contains(code.toUpperCase())) {
                        String city = parts[0].trim();
                        String type = parts[2].trim();
                        String lineName = parts[3].trim();
                        String station = parts[4].trim();
                        
                        StationInfo info = new StationInfo(city, code, type, lineName, station);
                        reader.close();
                        return info;
                    }
                }
            }
            reader.close();
        } catch (IOException e) {
            Log.e(TAG, "Failed to search in file: " + filePath, e);
        }
        return null;
    }
    
    /**
     * 获取卡片名称
     * @param iin 发卡机构标识
     * @return 卡片名称，找不到返回null
     */
    public String getCardName(String iin) {
        return cardNameCache.get(iin);
    }
    
    /**
     * 根据终端号获取站点显示字符串
     */
    public String getStationDisplay(String terminalHex) {
        StationInfo info = findStationByTerminal(terminalHex);
        if (info != null) {
            return info.toString();
        }
        return null;
    }
}
