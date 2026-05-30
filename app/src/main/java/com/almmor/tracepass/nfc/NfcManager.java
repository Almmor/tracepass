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

package com.almmor.tracepass.nfc;

import static android.nfc.NfcAdapter.EXTRA_TAG;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcF;
import android.os.Build;

import com.almmor.tracepass.nfc.reader.ReaderListener;
import com.almmor.tracepass.nfc.reader.ReaderManager;

public final class NfcManager {

	private final Activity activity;
	private NfcAdapter nfcAdapter;
	private PendingIntent pendingIntent;

	private static String[][] TECHLISTS;
	private static IntentFilter[] TAGFILTERS;
	private int status;

	static {
		try {
			TECHLISTS = new String[][] { { IsoDep.class.getName() },
					{ NfcF.class.getName() }, };

			TAGFILTERS = new IntentFilter[] { new IntentFilter(
					NfcAdapter.ACTION_TECH_DISCOVERED, "*/*") };
		} catch (Exception e) {
		}
	}

	public NfcManager(Activity activity) {
		this.activity = activity;
		nfcAdapter = NfcAdapter.getDefaultAdapter(activity);
		
		// NFC 前台派发需要 MUTABLE PendingIntent（系统需填充 TAG）
		int flags = PendingIntent.FLAG_UPDATE_CURRENT;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			flags |= PendingIntent.FLAG_MUTABLE;
		}
		pendingIntent = PendingIntent.getActivity(activity, 0, new Intent(
				activity, activity.getClass())
				.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), flags);

		setupBeam(true);

		status = getStatus();
	}

	public void onPause() {
		if (nfcAdapter != null)
			nfcAdapter.disableForegroundDispatch(activity);
	}

	public void onResume() {
		if (nfcAdapter != null)
			nfcAdapter.enableForegroundDispatch(activity, pendingIntent,
					TAGFILTERS, TECHLISTS);
	}

	public boolean updateStatus() {

		int sta = getStatus();
		if (sta != status) {
			status = sta;
			return true;
		}

		return false;
	}

	public boolean readCard(Intent intent, ReaderListener listener) {
		final Tag tag;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
			tag = (Tag) intent.getParcelableExtra(EXTRA_TAG, Tag.class);
		} else {
			tag = (Tag) intent.getParcelableExtra(EXTRA_TAG);
		}
		if (tag != null) {
			ReaderManager.readCard(activity, tag, listener);
			return true;
		}
		return false;
	}

	private int getStatus() {
		return (nfcAdapter == null) ? -1 : nfcAdapter.isEnabled() ? 1 : 0;
	}

	@SuppressWarnings("deprecation")
	private void setupBeam(boolean enable) {
		// setNdefPushMessage 在 API 33+ 已废弃，使用反射避免编译错误
		if (nfcAdapter != null && enable) {
			try {
				nfcAdapter.getClass().getMethod("setNdefPushMessage",
						NdefMessage.class, Activity.class)
						.invoke(nfcAdapter, createNdefMessage(), activity);
			} catch (Exception e) {
				// API 33+ 已移除此方法，忽略
			}
		}
	}

	NdefMessage createNdefMessage() {

		String uri = "3play.google.com/store/apps/details?id=com.almmor.tracepass";
		byte[] data = uri.getBytes();

		// about this '3'.. see NdefRecord.createUri which need api level 14
		data[0] = 3;

		NdefRecord record = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,
				NdefRecord.RTD_URI, null, data);

		return new NdefMessage(new NdefRecord[] { record });
	}
}
