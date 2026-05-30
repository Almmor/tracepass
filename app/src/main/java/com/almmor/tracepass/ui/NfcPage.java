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
import android.app.Dialog;
import android.content.Intent;

import com.almmor.tracepass.CardDataManager;
import com.almmor.tracepass.R;
import com.almmor.tracepass.SPEC;
import com.almmor.tracepass.ThisApplication;
import com.almmor.tracepass.SPEC.EVENT;
import com.almmor.tracepass.nfc.bean.Application;
import com.almmor.tracepass.nfc.bean.Card;
import com.almmor.tracepass.nfc.reader.ReaderListener;

import java.util.Collection;

/**
 * NFC 读卡页面 - 集成卡片数据持久化
 */
public final class NfcPage implements ReaderListener {
	// 静态变量保留用于兼容性
	private static String sCardHtml = null;
	private static String[] sTransEntries = null;

	private static final String TAG = "READCARD_ACTION";
	private static final String RET = "READCARD_RESULT";
	private static final String STA = "READCARD_STATUS";

	private final Activity activity;
	private final CardDataManager cardManager;

	public NfcPage(Activity activity) {
		this.activity = activity;
		this.cardManager = CardDataManager.getInstance(activity);
	}

	public static boolean isSendByMe(Intent intent) {
		return intent != null && TAG.equals(intent.getAction());
	}

	public static boolean isNormalInfo(Intent intent) {
		return intent != null && intent.hasExtra(STA);
	}

	public static CharSequence getContent(Activity activity, Intent intent) {
		String info = intent.getStringExtra(RET);
		if (info == null || info.length() == 0)
			return null;

		return new SpanFormatter(getActionHandler(activity))
				.toSpanned(info);
	}

	public static SpanFormatter.ActionHandler getActionHandler(Activity activity) {
		return new Handler(activity);
	}

	private static final class Handler implements SpanFormatter.ActionHandler {
		private final Activity activity;

		Handler(Activity activity) {
			this.activity = activity;
		}

		@Override
		public void handleAction(CharSequence name) {
			final String action = name.toString();
			final Intent intent = new Intent(TAG);
			
			if (action != null && action.startsWith("t_action_trans_detail_")) {
				// 交易记录展开：提取索引，跳转到详情页
				int idx = 0;
				try {
					idx = Integer.parseInt(action.substring("t_action_trans_detail_".length()));
				} catch (NumberFormatException e) {
					idx = 0;
				}
				intent.putExtra("NFC_ACTION", "t_action_trans_expanded");
				intent.putExtra("TRANS_INDEX", idx);
				activity.setIntent(intent);
			} else {
				// Forward unknown/unhandled actions to AboutPage handler
				AboutPage.getActionHandler(activity).handleAction(name);
			}
		}
	}

	@Override
	public void onReadEvent(EVENT event, Object... objs) {
		if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
			return;
		}
		if (event == EVENT.IDLE) {
			showProgressBar();
		} else if (event == EVENT.FINISHED) {
			hideProgressBar();

			final Card card;
			if (objs != null && objs.length > 0)
				card = (Card) objs[0];
			else
				card = null;

			activity.setIntent(buildResult(card));
		}
	}

	private Intent buildResult(Card card) {
		final Intent ret = new Intent(TAG);

		if (card != null && !card.hasReadingException()) {
			if (card.isUnknownCard()) {
				ret.putExtra(RET, ThisApplication
						.getStringResource(R.string.info_nfc_unknown));
			} else {
				// 存储完整卡片 HTML
				sCardHtml = card.toHtml();
				ret.putExtra("CARD_HTML", sCardHtml);
				ret.putExtra(STA, 1);

				// 提取交易记录条目数组
				Collection<Application> apps = card.getApplications();
				if (apps != null && !apps.isEmpty()) {
					Application app = apps.iterator().next();
					Object prop = app.getProperty(SPEC.PROP.TRANSLOG);
					if (prop instanceof String[]) {
						sTransEntries = (String[]) prop;
					} else {
						sTransEntries = null;
					}
				} else {
					sTransEntries = null;
				}

				// 生成卡片唯一ID（使用卡片序列号或哈希）
				String cardId = generateCardId(card);
				
				// 保存到 CardDataManager
				cardManager.saveCardData(sCardHtml, sTransEntries, cardId);

				// 显示读卡成功提示，不再显示两个按钮
				StringBuilder sb = new StringBuilder();
				sb.append("<").append(SPEC.TAG_BLK).append(">");
				sb.append("<").append(SPEC.TAG_H1).append(">读卡成功</").append(SPEC.TAG_H1).append(">");
				sb.append("<br />");
				sb.append("<").append(SPEC.TAG_SP).append(" />");
				sb.append("<br />");
				sb.append("<").append(SPEC.TAG_TEXT).append(">");
				sb.append("卡片数据已保存，请使用底部导航栏查看详情");
				sb.append("</").append(SPEC.TAG_TEXT).append(">");
				sb.append("</").append(SPEC.TAG_BLK).append(">");
				ret.putExtra(RET, sb.toString());
			}
		} else {
			String error = ThisApplication.getStringResource(R.string.info_nfc_error);
			if (card != null && card.getReadingException() != null) {
				String detail = card.getReadingException().toString();
				error = error.replace("</t_tip>", "<br />" + detail + "</t_tip>");
			}
			ret.putExtra(RET, error);
		}

		return ret;
	}

	/**
	 * 生成卡片唯一ID
	 */
	private String generateCardId(Card card) {
		StringBuilder id = new StringBuilder();
		if (card.getApplications() != null && !card.getApplications().isEmpty()) {
			Application app = card.getApplications().iterator().next();
			Object serial = app.getProperty(SPEC.PROP.SERIAL);
			if (serial != null) {
				id.append(serial.toString());
			}
		}
		id.append("_").append(System.currentTimeMillis());
		return id.toString();
	}

	/**
	 * 获取当前卡片HTML（从CardDataManager）
	 */
	public static String getCurrentCardHtml(Activity activity) {
		return CardDataManager.getInstance(activity).getCardHtml();
	}

	/**
	 * 获取当前交易记录（从CardDataManager）
	 */
	public static String[] getCurrentTransEntries(Activity activity) {
		return CardDataManager.getInstance(activity).getTransEntries();
	}

	private void showProgressBar() {
		if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
		Dialog d = progressBar;
		if (d == null) {
			d = new Dialog(activity, R.style.progressBar);
			d.setCancelable(false);
			d.setContentView(R.layout.progress);
			progressBar = d;
		}

		if (!d.isShowing())
			d.show();
	}

	private void hideProgressBar() {
		if (activity == null || activity.isFinishing() || activity.isDestroyed()) return;
		final Dialog d = progressBar;
		if (d != null && d.isShowing())
			d.cancel();
	}

	private Dialog progressBar;
}
