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

package com.sinpo.xnfc.nfc.reader.pboc;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import com.sinpo.xnfc.SPEC;
import com.sinpo.xnfc.nfc.Util;
import com.sinpo.xnfc.nfc.bean.Application;
import com.sinpo.xnfc.nfc.bean.Card;
import com.sinpo.xnfc.nfc.tech.Iso7816;

import android.nfc.tech.IsoDep;
import android.util.Log;

@SuppressWarnings("unchecked")
public abstract class StandardPboc {
	private static final String TAG = "StandardPboc";
	
	private static Class<?>[][] readers = {
			{ BeijingMunicipal.class, WuhanTong.class, CityUnion.class, TUnion.class,
					ShenzhenTong.class, }, { StandardECash.class, } };

	public static void readCard(IsoDep tech, Card card) throws InstantiationException,
			IllegalAccessException, IOException {

		final Iso7816.StdTag tag = new Iso7816.StdTag(tech);

		tag.connect();

		for (final Class<?> g[] : readers) {
			HINT hint = HINT.RESETANDGONEXT;

			for (final Class<?> r : g) {

				final StandardPboc reader = (StandardPboc) r.newInstance();

				switch (hint) {

				case RESETANDGONEXT:
					if (!reader.resetTag(tag))
						continue;

				case GONEXT:
					hint = reader.readCard(tag, card);
					break;

				default:
					break;
				}

				if (hint == HINT.STOP)
					break;
			}
		}

		tag.close();
	}

	protected boolean resetTag(Iso7816.StdTag tag) throws IOException {
		return tag.selectByID(DFI_MF).isOkey() || tag.selectByName(DFN_PSE).isOkey();
	}

	protected enum HINT {
		STOP, GONEXT, RESETANDGONEXT,
	}

	protected final static byte[] DFI_MF = { (byte) 0x3F, (byte) 0x00 };
	protected final static byte[] DFI_EP = { (byte) 0x10, (byte) 0x01 };

	protected final static byte[] DFN_PSE = { (byte) '1', (byte) 'P', (byte) 'A', (byte) 'Y',
			(byte) '.', (byte) 'S', (byte) 'Y', (byte) 'S', (byte) '.', (byte) 'D', (byte) 'D',
			(byte) 'F', (byte) '0', (byte) '1', };

	protected final static byte[] DFN_PXX = { (byte) 'P' };

	protected final static int SFI_EXTRA = 21;

	protected static int MAX_LOG = 10;
	protected static int SFI_LOG = 24;
	protected final static int SFI_DETAIL = 30; // 0x1E 交易详情文件

	protected final static byte TRANS_CSU = 6;
	protected final static byte TRANS_CSU_CPX = 9;

	protected abstract Object getApplicationId();

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
		// read detail file, record (30 / 0x1E)
		/*--------------------------------------------------------------*/
		ArrayList<byte[]> DETAIL = readDetail30(tag, SFI_DETAIL);

		/*--------------------------------------------------------------*/
		// build result
		/*--------------------------------------------------------------*/
		final Application app = createApplication();

		parseBalance(app, BALANCE);

		parseInfo21(app, INFO, 4, true);

		parseLog24(app, LOG, DETAIL);

		configApplication(app);

		card.addApplication(app);

		return HINT.STOP;
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
		if (dec < 1 || dec > 10) {
			app.setProperty(SPEC.PROP.SERIAL, Util.toHexString(d, 10, 10));
		} else {
			final int sn = bigEndian ? Util.toIntR(d, 19, dec) : Util.toInt(d, 20 - dec, dec);

			app.setProperty(SPEC.PROP.SERIAL, String.format("%d", 0xFFFFFFFFL & sn));
		}

		if (d[9] != 0)
			app.setProperty(SPEC.PROP.VERSION, String.valueOf(d[9]));

		app.setProperty(SPEC.PROP.DATE, String.format("%02X%02X.%02X.%02X - %02X%02X.%02X.%02X",
				d[20], d[21], d[22], d[23], d[24], d[25], d[26], d[27]));
	}

	protected boolean addLog24(final Iso7816.Response r, ArrayList<byte[]> l) {
		if (!r.isOkey())
			return false;

		final byte[] raw = r.getBytes();
		final int N = raw.length - 23;
		if (N < 0)
			return false;

		for (int s = 0, e = 0; s <= N; s = e) {
			l.add(Arrays.copyOfRange(raw, s, (e = s + 23)));
		}

		return true;
	}
	
	/**
	 * 添加单条日志记录（用于逐条读取）
	 */
	protected boolean addSingleLog24(final Iso7816.Response r, ArrayList<byte[]> l) {
		if (!r.isOkey())
			return false;

		final byte[] raw = r.getBytes();
		// 单条记录读取，只要长度>=23就添加
		if (raw.length >= 23) {
			l.add(Arrays.copyOfRange(raw, 0, 23));
			return true;
		}
		return false;
	}

	protected boolean addDetail30(final Iso7816.Response r, ArrayList<byte[]> l) {
		if (!r.isOkey())
			return false;

		final byte[] raw = r.getBytes();
		// SFI 0x1E 每条记录至少包含: 交易类型(1) + 辅助类型(1) + 终端号(8) + 时间戳(6)
		// + 线路站点(7) + 余额(4) + 城市(2) + 收单机构(8) + 预留(6) = 43字节
		// 但不同卡片可能长度不同，使用最小安全长度
		final int RECORD_LEN = 43;
		final int N = raw.length - RECORD_LEN;
		if (N < 0)
			return false;

		for (int s = 0, e = 0; s <= N; s = e) {
			l.add(Arrays.copyOfRange(raw, s, (e = s + RECORD_LEN)));
		}

		return true;
	}
	
	/**
	 * 添加单条详情记录（用于逐条读取）
	 */
	protected boolean addSingleDetail30(final Iso7816.Response r, ArrayList<byte[]> l) {
		if (!r.isOkey())
			return false;

		final byte[] raw = r.getBytes();
		// 单条记录读取，只要长度>=37就添加（收单机构字段需要37字节）
		if (raw.length >= 37) {
			l.add(Arrays.copyOfRange(raw, 0, Math.min(raw.length, 43)));
			return true;
		}
		return false;
	}

	protected ArrayList<byte[]> readLog24(Iso7816.StdTag tag, int sfi) throws IOException {
		final ArrayList<byte[]> ret = new ArrayList<byte[]>(MAX_LOG);
		Log.d(TAG, "readLog24: sfi=" + sfi);
		
		final Iso7816.Response rsp = tag.readRecord(sfi);
		Log.d(TAG, "readLog24: batch read rsp.isOkey()=" + rsp.isOkey() + ", size=" + rsp.size());
		
		if (rsp.isOkey()) {
			// 批量读取成功，分割多条记录
			boolean added = addLog24(rsp, ret);
			Log.d(TAG, "readLog24: batch addLog24=" + added + ", ret.size()=" + ret.size());
		} else {
			// 批量读取失败，尝试逐条读取
			// 循环记录文件可能从任意位置开始，尝试读取最多MAX_LOG条
			Log.d(TAG, "readLog24: trying single record read");
			int consecutiveEmpty = 0;
			for (int i = 1; i <= MAX_LOG; ++i) {
				Iso7816.Response singleRsp = tag.readRecord(sfi, i);
				Log.d(TAG, "readLog24: single read[" + i + "] rsp.isOkey()=" + singleRsp.isOkey() + ", size=" + singleRsp.size());
				if (addSingleLog24(singleRsp, ret)) {
					consecutiveEmpty = 0;
				} else {
					consecutiveEmpty++;
					// 连续2条读取失败则认为到达末尾
					if (consecutiveEmpty >= 2)
						break;
				}
			}
			Log.d(TAG, "readLog24: single read done, ret.size()=" + ret.size());
		}

		return ret;
	}

	protected ArrayList<byte[]> readDetail30(Iso7816.StdTag tag, int sfi) throws IOException {
		final ArrayList<byte[]> ret = new ArrayList<byte[]>(MAX_LOG);
		final Iso7816.Response rsp = tag.readRecord(sfi);
		if (rsp.isOkey()) {
			// 批量读取成功，分割多条记录
			addDetail30(rsp, ret);
		} else {
			// 批量读取失败，尝试逐条读取
			int consecutiveEmpty = 0;
			for (int i = 1; i <= MAX_LOG; ++i) {
				Iso7816.Response singleRsp = tag.readRecord(sfi, i);
				if (addSingleDetail30(singleRsp, ret)) {
					consecutiveEmpty = 0;
				} else {
					consecutiveEmpty++;
					if (consecutiveEmpty >= 2)
						break;
				}
			}
		}

		return ret;
	}

	protected void parseLog24(Application app, ArrayList<byte[]>... logs) {
		final ArrayList<String> ret = new ArrayList<String>(MAX_LOG);
		Log.d(TAG, "parseLog24 called with " + logs.length + " log arrays");

		// logs[0] = SFI 0x18 标准交易记录 (23字节/条)
		// logs[1] = SFI 0x1E 交易详情记录 (43字节/条，可选)
		ArrayList<byte[]> details = (logs.length > 1) ? logs[1] : null;
		if (details != null) {
			Log.d(TAG, "Details count: " + details.size());
		}

		for (final ArrayList<byte[]> log : logs) {
			if (log == null || log == details)
				continue;
			
			Log.d(TAG, "Processing log with " + log.size() + " records");

			for (int idx = 0; idx < log.size(); idx++) {
				final byte[] v = log.get(idx);
				Log.d(TAG, "Record[" + idx + "] length=" + v.length);
				
				// 检查记录是否为空（全0或全FF）
				boolean isEmpty = true;
				for (byte b : v) {
					if (b != 0 && b != (byte) 0xFF) {
						isEmpty = false;
						break;
					}
				}
				if (isEmpty) {
					Log.d(TAG, "Record[" + idx + "] is empty (all 0 or FF)");
					continue;
				}
				
				// PBOC规范：交易金额在字节6-9 (索引5-8)
				final int money = Util.toInt(v, 5, 4);
				Log.d(TAG, "Record[" + idx + "] money=" + money);
				
				// 交易类型在字节10 (索引9)
				final byte transType = v[9];
				Log.d(TAG, "Record[" + idx + "] transType=" + String.format("%02X", transType));
				
				// 判断交易类型：06=圈存(充值), 09=圈存(复合), 其他=消费
				// 圈存是存入(+)钱变多，消费是支出(-)钱变少
				final char s = (transType == TRANS_CSU || transType == TRANS_CSU_CPX) ? '+' : '-';

				// 透支限额在字节3-5 (索引2-4)
				final int over = Util.toInt(v, 2, 3);
				Log.d(TAG, "Record[" + idx + "] over=" + over);
				
				String slog;

				// 尝试匹配对应的 SFI 0x1E 详情记录
				String detailInfo = "";
				if (details != null && idx < details.size()) {
					detailInfo = parseDetail30(details.get(idx));
					Log.d(TAG, "Record[" + idx + "] detailInfo=" + detailInfo);
				}

				// 日期在字节17-20 (索引16-19): YY MM DD
				// 时间在字节21-23 (索引20-22): HH MM SS (但只显示HH MM)
				if (over > 0) {
					slog = String
							.format("%02X%02X.%02X.%02X %02X:%02X %c%.2f [o:%.2f] [%02X%02X%02X%02X%02X%02X]",
									v[16], v[17], v[18], v[19], v[20], v[21], s,
									(money / 100.0f), (over / 100.0f), v[10], v[11], v[12],
									v[13], v[14], v[15]);
				} else {
					slog = String.format(
							"%02X%02X.%02X.%02X %02X:%02X %c%.2f [%02X%02X%02X%02X%02X%02X]",
								v[16], v[17], v[18], v[19], v[20], v[21], s, (money / 100.0f),
								v[10], v[11], v[12], v[13], v[14], v[15]);
				}

				// 如果有详情信息，追加到交易记录后面
				if (detailInfo.length() > 0) {
					slog = slog + " " + detailInfo;
				}

				Log.d(TAG, "Record[" + idx + "] formatted: " + slog);
				ret.add(slog);
			}
		}

		Log.d(TAG, "parseLog24 returning " + ret.size() + " records");
		if (!ret.isEmpty())
			app.setProperty(SPEC.PROP.TRANSLOG, ret.toArray(new String[ret.size()]));
	}

	/**
	 * 解析 SFI 0x1E (30) 交易详情记录
	 * 记录格式 (43字节):
	 *   [0]    交易类型
	 *   [1]    辅助类型
	 *   [2..9] 终端号 (8字节)
	 *   [10..15] 时间戳 YYMMDDHHMMSS (6字节)
	 *   [16..22] 线路和站点 (7字节)
	 *   [23..26] 交易后余额 (4字节, 大端)
	 *   [27..28] 城市代码 (2字节)
	 *   [29..36] 收单机构 (8字节)
	 *   [37..42] 预留字段 (6字节)
	 */
	protected String parseDetail30(byte[] d) {
		if (d == null || d.length < 37)
			return "";

		final StringBuilder sb = new StringBuilder();

		// 线路和站点信息
		if (d.length >= 23) {
			String route = String.format("%02X%02X%02X%02X%02X%02X%02X",
					d[16], d[17], d[18], d[19], d[20], d[21], d[22]);
			sb.append("[line:").append(route).append("]");
		}

		// 交易后余额
		if (d.length >= 27) {
			int balance = Util.toInt(d, 23, 4);
			sb.append("[bal:").append(String.format("%.2f", balance / 100.0f)).append("]");
		}

		// 城市代码
		if (d.length >= 29) {
			String city = String.format("%02X%02X", d[27], d[28]);
			sb.append("[city:").append(city).append("]");
		}

		// 收单机构
		if (d.length >= 37) {
			String acquirer = String.format("%02X%02X%02X%02X%02X%02X%02X%02X",
					d[29], d[30], d[31], d[32], d[33], d[34], d[35], d[36]);
			sb.append("[acq:").append(acquirer).append("]");
		}

		return sb.toString();
	}

	protected Application createApplication() {
		return new Application();
	}

	protected void configApplication(Application app) {
		app.setProperty(SPEC.PROP.ID, getApplicationId());
		app.setProperty(SPEC.PROP.CURRENCY, getCurrency());
	}
}
