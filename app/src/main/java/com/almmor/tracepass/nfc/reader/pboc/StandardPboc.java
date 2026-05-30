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

package com.almmor.tracepass.nfc.reader.pboc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.json.JSONObject;

import com.almmor.tracepass.SPEC;
import com.almmor.tracepass.data.TripReaderDataManager;
import com.almmor.tracepass.nfc.Util;
import com.almmor.tracepass.nfc.bean.Application;
import com.almmor.tracepass.nfc.bean.Card;
import com.almmor.tracepass.nfc.tech.Iso7816;

import android.content.Context;
import android.nfc.tech.IsoDep;
import android.util.Log;

public class StandardPboc {
	protected final static byte[] DFN_PSE = { (byte) '1', (byte) 'P',
			(byte) 'A', (byte) 'Y', (byte) '.', (byte) 'S', (byte) 'Y',
			(byte) 'S', (byte) '.', (byte) 'D', (byte) 'D', (byte) 'F',
			(byte) '0', (byte) '1', };

	protected final static byte[] DFN_PBOC = { (byte) 0xA0, (byte) 0x00,
			(byte) 0x00, (byte) 0x03, (byte) 0x33, (byte) 0x01, (byte) 0x01,
			(byte) 0x01, };

	protected final static byte[] DFN_PBOC_1 = { (byte) 0xA0, (byte) 0x00,
			(byte) 0x00, (byte) 0x03, (byte) 0x33, };

	protected final static byte[] DFN_PBOC_2 = { (byte) 0xA0, (byte) 0x00,
			(byte) 0x00, (byte) 0x03, (byte) 0x33, (byte) 0x01, };

	protected final static byte[] DFN_PBOC_3 = { (byte) 0xA0, (byte) 0x00,
			(byte) 0x00, (byte) 0x33, (byte) 0x01, };

	protected final static byte[] DFN_PBOC_4 = { (byte) 0xA0, (byte) 0x00,
			(byte) 0x00, (byte) 0x03, (byte) 0x33, (byte) 0x01, (byte) 0x01, };

	protected final static byte[] DFI_MF = { (byte) 0x3F, (byte) 0x00 };
	protected final static byte[] DFI_EP = { (byte) 0x10, (byte) 0x01 };
	protected final static byte[] DFN_PXX = { (byte) 'P' };

	protected final static int SFI_EXTRA = 21;
	protected final static int SFI_LOG = 24;

	protected final static byte TRANS_CSU = 6;
	protected final static byte TRANS_CSU_CPX = 9;

	protected final static int MAX_LOG = 10;

	protected enum HINT {
		STOP, GONEXT, RESETANDGONEXT,
	}

	public static void readCard(IsoDep tech, Card card) throws InstantiationException,
			IllegalAccessException, IOException, InvocationTargetException {
		readCard(null, tech, card);
	}

	public static void readCard(Context context, IsoDep tech, Card card) throws InstantiationException,
			IllegalAccessException, IOException, InvocationTargetException {

		final Iso7816.StdTag tag = new Iso7816.StdTag(tech);

		tag.connect();

		// 按原项目的方式分组读取器
		ArrayList<Class<? extends StandardPboc>> group1 = new ArrayList<Class<? extends StandardPboc>>();
		group1.add(BeijingMunicipal.class);
		group1.add(WuhanTong.class);
		group1.add(CityUnion.class);
		group1.add(TUnion.class);
		group1.add(ShenzhenTong.class);

		ArrayList<Class<? extends StandardPboc>> group2 = new ArrayList<Class<? extends StandardPboc>>();
		group2.add(StandardECash.class);

		@SuppressWarnings("unchecked")
		ArrayList<Class<? extends StandardPboc>>[] readerGroups = new ArrayList[] { group1, group2 };

		for (ArrayList<Class<? extends StandardPboc>> group : readerGroups) {
			HINT hint = HINT.RESETANDGONEXT;

			for (final Class<? extends StandardPboc> r : group) {
				final StandardPboc reader;
				try {
					Constructor<?> ctor = r.getDeclaredConstructors()[0];
					ctor.setAccessible(true);
					reader = (StandardPboc) ctor.newInstance();
					reader.setContext(context);
				} catch (InvocationTargetException ex) {
					Throwable t = ex.getTargetException();
					if (t instanceof RuntimeException) throw (RuntimeException) t;
					if (t instanceof IOException) throw (IOException) t;
					throw new RuntimeException(t);
				}

				switch (hint) {
				case RESETANDGONEXT:
					if (!reader.resetTag(tag))
						continue;
					// fall through to GONEXT
				case GONEXT:
					hint = reader.readCard(tag, card);
					break;
				default:
					break;
				}

				if (hint == HINT.STOP)
					break;
			}

			if (hint == HINT.STOP)
				break;
		}

		tag.close();
	}

	protected Context context;

	protected void setContext(Context context) {
		this.context = context;
	}

	protected boolean resetTag(Iso7816.StdTag tag) throws IOException {
		return tag.selectByID(DFI_MF).isOkey() || tag.selectByName(DFN_PSE).isOkey();
	}

	protected byte[] getDfn() {
		return DFN_PBOC;
	}

	protected byte[][] getDfns() {
		return new byte[][] { DFN_PBOC, DFN_PBOC_1, DFN_PBOC_2, DFN_PBOC_3,
				DFN_PBOC_4 };
	}

	protected Object getApplicationId() {
		return "PBOC";
	}

	protected byte[] getMainApplicationId() {
		return DFI_EP;
	}

	protected SPEC.CUR getCurrency() {
		return SPEC.CUR.CNY;
	}

	protected boolean selectMainApplication(Iso7816.StdTag tag) throws IOException {
		final byte[] aid = getMainApplicationId();
		return ((aid.length == 2) ? tag.selectByID(aid) : tag.selectByName(aid)).isOkey();
	}

	protected HINT readCard(Iso7816.StdTag tag, Card card) throws IOException {

		/*--------------------------------------------------------------*/
		// select Main Application
		/*--------------------------------------------------------------*/
		if (!selectMainApplication(tag))
			return HINT.GONEXT;

		Iso7816.Response INFO, BALANCE;

		/*--------------------------------------------------------------*/
		// read card info file, binary (21)
		/*--------------------------------------------------------------*/
		INFO = tag.readBinary(SFI_EXTRA);

		/*--------------------------------------------------------------*/
		// read balance
		/*--------------------------------------------------------------*/
		BALANCE = tag.getBalance(0, true);

		/*--------------------------------------------------------------*/
		// read log file, record (24)
		/*--------------------------------------------------------------*/
		ArrayList<byte[]> LOG = readLog24(tag, SFI_LOG);

		/*--------------------------------------------------------------*/
		// build result
		/*--------------------------------------------------------------*/
		final Application app = createApplication();

		parseBalance(app, BALANCE);

		parseInfo21(app, INFO, 4, true);

		// 获取交易渠道
		String channel = getChannelFromAppId();
		parseLog24(app, channel, LOG);

		configApplication(app);

		card.addApplication(app);

		return HINT.STOP;
	}

	/**
	 * 根据应用ID获取交易渠道
	 */
	protected String getChannelFromAppId() {
		Object appId = getApplicationId();
		Log.d("NFCARD", "getChannelFromAppId: appId=" + appId);
		if (appId instanceof SPEC.APP) {
			SPEC.APP app = (SPEC.APP) appId;
			switch (app) {
			case TUNIONEC:
			case TUNIONEP:
				return "TU (\u4ea4\u901a\u8054\u5408)";
			case CITYUNION:
				return "CU (\u57ce\u5e02\u8054\u5408)";
			case BEIJINGMUNICIPAL:
			case SHENZHENTONG:
			case WUHANTONG:
				return "\u57ce\u5e02\u901a\u5361";
			default:
				return "\u5176\u4ed6";
			}
		}
		return "\u5176\u4ed6";
	}

	protected float parseBalance(Iso7816.Response data) {
		float ret = 0f;
		if (data.isOkey() && data.size() >= 4) {
			int n = Util.toInt(data.getBytes(), 0, 4);
			if (n > 1000000 || n < -1000000)
				n -= 0x80000000;

			ret = n / 100.0f;
		}
		return ret;
	}

	protected void parseBalance(Application app, Iso7816.Response... data) {

		float amount = 0f;
		for (Iso7816.Response rsp : data)
			amount += parseBalance(rsp);

		app.setProperty(SPEC.PROP.BALANCE, amount);
	}

	protected void parseInfo21(Application app, Iso7816.Response data, int dec, boolean bigEndian) {
		if (!data.isOkey() || data.size() < 30) {
			return;
		}

		final byte[] d = data.getBytes();

		{
			int n = bigEndian ? Util.toInt(d, 16, 4) : Util.toIntR(d, 19, 4);
			if (n > 100000 || n < -100000)
				n -= 0x80000000;

			app.setProperty(SPEC.PROP.ECASH, (float) n / dec);
		}

		{
			int n = Util.BCDtoInt(d, 2, 4);
			if (n < 0)
				n = Util.toInt(d, 2, 4);

			app.setProperty(SPEC.PROP.SERIAL, String.format("%d", 0xFFFFFFFFL & n));
		}

		if (d[9] != 0)
			app.setProperty(SPEC.PROP.VERSION, String.valueOf(d[9]));

		app.setProperty(SPEC.PROP.DATE, String.format("%02X%02X.%02X.%02X - %02X%02X.%02X.%02X",
				d[20], d[21], d[22], d[23], d[24], d[25], d[26], d[27]));
	}

	protected boolean addLog24(final Iso7816.Response r, ArrayList<byte[]> l) {
		if (!r.isOkey()) {
			Log.d("NFCARD", "addLog24: response not ok, sw=" + r.getSw12String());
			return false;
		}

		final byte[] raw = r.getBytes();
		Log.d("NFCARD", "addLog24: raw data length=" + raw.length + ", hex=" + Util.toHexString(raw));
		
		// 支持变长交易记录（交通联合卡等扩展格式）
		// 最小记录长度为23字节（标准PBOC格式）
		final int MIN_RECORD_LEN = 23;
		
		// 数据长度必须至少为23字节
		if (raw.length < MIN_RECORD_LEN) {
			Log.d("NFCARD", "addLog24: data too short, length=" + raw.length);
			return false;
		}

		// 如果数据长度正好是23的倍数，按标准格式分割
		if (raw.length % MIN_RECORD_LEN == 0) {
			int count = raw.length / MIN_RECORD_LEN;
			Log.d("NFCARD", "addLog24: standard format, " + count + " records");
			for (int s = 0; s < raw.length; s += MIN_RECORD_LEN) {
				l.add(Arrays.copyOfRange(raw, s, s + MIN_RECORD_LEN));
			}
		} else {
			// 变长格式：将整个记录作为一个条目
			// 交通联合卡等扩展格式
			Log.d("NFCARD", "addLog24: extended format, length=" + raw.length);
			l.add(raw);
		}

		return true;
	}

	protected ArrayList<byte[]> readLog24(Iso7816.StdTag tag, int sfi) throws IOException {
		final ArrayList<byte[]> ret = new ArrayList<byte[]>(MAX_LOG);
		final Iso7816.Response rsp = tag.readRecord(sfi);
		if (rsp.isOkey()) {
			addLog24(rsp, ret);
		} else {
			for (int i = 1; i <= MAX_LOG; ++i) {
				if (!addLog24(tag.readRecord(sfi, i), ret))
					break;
			}
		}

		return ret;
	}

	protected void parseLog24(Application app, String channel, ArrayList<byte[]>... logs) {
		final ArrayList<String> ret = new ArrayList<String>(MAX_LOG);

		for (final ArrayList<byte[]> log : logs) {
			if (log == null)
				continue;

			for (final byte[] v : log) {
				// 判断记录格式：标准PBOC(23字节) 或 扩展格式(交通联合卡等)
				if (v.length >= 23) {
					parseStandardLog(v, channel, ret);
				}
			}
		}

		if (!ret.isEmpty())
			app.setProperty(SPEC.PROP.TRANSLOG, ret.toArray(new String[ret.size()]));
	}

	/**
	 * 解析标准PBOC交易记录(23字节)
	 */
	protected void parseStandardLog(byte[] v, String channel, ArrayList<String> ret) {
		// 完全按照原项目的方式解析金额
		final int money = Util.toInt(v, 5, 4);
		final int over = Util.toInt(v, 2, 3);
		final byte transType = v[9];
		
		Log.d("NFCARD", "parseStandardLog: money=" + money + ", over=" + over + ", transType=" + transType);
		Log.d("NFCARD", "parseStandardLog: hex=" + Util.toHexString(v));
		
		// 原项目条件：money > 0 才是有效记录
		if (money > 0) {
			// v[9] 是交易类型：6或9表示消费(-)，其他表示充值(+)
			final char sign = (v[9] == TRANS_CSU || v[9] == TRANS_CSU_CPX) ? '-' : '+';

			// 交易类型
			String typeName;
			String typeIcon;
			if (v[9] == TRANS_CSU || v[9] == TRANS_CSU_CPX) {
				typeName = "\u5237\u5361\u6d88\u8d39"; // 刷卡消费
				typeIcon = "\ud83d\udcb3";
			} else {
				typeName = "\u5145\u503c"; // 充值
				typeIcon = "\ud83d\udcb0";
			}

			// 金额（转换为元）- 与原项目一致
			final float amount = money / 100.0f;
			
			// 终端号（十六进制）- 6字节
			final String terminalHex = Util.toHexString(v, 10, 6);
			
			// SFI
			final int sfi = v[0] & 0xFF;

			// 日期时间 - 与原项目一致
			final String dateStr = String.format("%02X%02X.%02X.%02X", v[16], v[17], v[18], v[19]);
			final String timeStr = String.format("%02X:%02X", v[20], v[21]);
			final String timestampStr = String.format("%02X%02X%02X%02X%02X%02X", v[16], v[17], v[18], v[19], v[20], v[21]);

			// 查询站点信息
			String city = "";
			String station = "";
			String line = "";
			if (context != null) {
				try {
					TripReaderDataManager dataManager = TripReaderDataManager.getInstance(context);
					TripReaderDataManager.StationInfo stationInfo = dataManager.findStationByTerminal(terminalHex);
					if (stationInfo != null) {
						city = stationInfo.city;
						station = stationInfo.station;
						line = stationInfo.line;
					}
				} catch (Exception e) {
					// 忽略数据查询错误
				}
			}

			// 透支金额（交易后余额）- 原项目中 [o:xxx] 就是这个值
			final float transBalance = over / 100.0f;

			try {
				JSONObject json = new JSONObject();
				json.put("icon", typeIcon);
				json.put("type", typeName);
				json.put("sign", String.valueOf(sign));
				json.put("amount", String.format("%.2f", amount));
				json.put("date", dateStr);
				json.put("time", timeStr);
				json.put("timestamp", timestampStr);
				json.put("terminal", terminalHex);
				json.put("sfi", String.valueOf(sfi));
				// 余额 - 使用透支金额作为交易后余额
				json.put("balance", String.format("%.2f", transBalance));
				json.put("overdraft", String.format("%.2f", transBalance));
				json.put("city", city);
				json.put("station", station);
				json.put("line", line);
				json.put("channel", channel);
				json.put("reserved", "");
				ret.add(json.toString());
			} catch (Exception e) {
				// 回退到简单格式 - 与原项目完全一致
				if (over > 0) {
					ret.add(String.format("%02X%02X.%02X.%02X %02X:%02X %c%.2f [o:%.2f] [%s]",
							v[16], v[17], v[18], v[19], v[20], v[21], sign,
							amount, transBalance, terminalHex));
				} else {
					ret.add(String.format("%02X%02X.%02X.%02X %02X:%02X %c%.2f [%s]",
							v[16], v[17], v[18], v[19], v[20], v[21], sign,
							amount, terminalHex));
				}
			}
		}
	}

	protected Application createApplication() {
		return new Application();
	}

	protected void configApplication(Application app) {
		app.setProperty(SPEC.PROP.ID, getApplicationId());
		app.setProperty(SPEC.PROP.CURRENCY, getCurrency());
	}
}
