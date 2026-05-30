package com.almmor.tracepass.nfc.reader;

import android.content.Context;
import android.nfc.Tag;
import android.nfc.tech.IsoDep;
import android.nfc.tech.NfcF;
import android.os.Handler;
import android.os.Looper;

import com.almmor.tracepass.SPEC;
import com.almmor.tracepass.nfc.Util;
import com.almmor.tracepass.nfc.bean.Card;
import com.almmor.tracepass.nfc.reader.pboc.StandardPboc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ReaderManager {

	private static final ExecutorService executor = Executors.newSingleThreadExecutor();
	private static final Handler mainHandler = new Handler(Looper.getMainLooper());

	public static void readCard(Tag tag, ReaderListener listener) {
		readCard(null, tag, listener);
	}

	public static void readCard(Context context, Tag tag, ReaderListener listener) {
		executor.execute(new ReadTask(context, tag, listener));
	}

	private static final class ReadTask implements Runnable {
		private final Context context;
		private final Tag tag;
		private final ReaderListener listener;

		ReadTask(Context context, Tag tag, ReaderListener listener) {
			this.context = context;
			this.tag = tag;
			this.listener = listener;
		}

		@Override
		public void run() {
			final Card card = new Card();

			try {
				postEvent(SPEC.EVENT.READING);

				card.setProperty(SPEC.PROP.ID, Util.toHexString(tag.getId()));

				final IsoDep isodep = IsoDep.get(tag);
				if (isodep != null)
					StandardPboc.readCard(context, isodep, card);

				final NfcF nfcf = NfcF.get(tag);
				if (nfcf != null)
					FelicaReader.readCard(nfcf, card);

				postEvent(SPEC.EVENT.IDLE);

			} catch (Exception e) {
				card.setProperty(SPEC.PROP.EXCEPTION, e);
				postEvent(SPEC.EVENT.ERROR);
			}

			postFinished(card);
		}

		private void postEvent(final SPEC.EVENT event) {
			mainHandler.post(new Runnable() {
				@Override
				public void run() {
					if (listener != null)
						listener.onReadEvent(event);
				}
			});
		}

		private void postFinished(final Card card) {
			mainHandler.post(new Runnable() {
				@Override
				public void run() {
					if (listener != null)
						listener.onReadEvent(SPEC.EVENT.FINISHED, card);
				}
			});
		}
	}
}