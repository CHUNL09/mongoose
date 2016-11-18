package com.emc.mongoose.load.monitor.metrics;

import com.emc.mongoose.model.io.IoType;
import com.emc.mongoose.ui.log.MessageBase;
import static com.emc.mongoose.common.Constants.M;
import static com.emc.mongoose.common.Constants.MIB;
import static com.emc.mongoose.common.api.SizeInBytes.formatFixedSize;

import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import org.apache.commons.lang.text.StrBuilder;

/**
 Created by kurila on 26.10.16.
 */
public final class MetricsStdoutLogMessage
extends MessageBase {
	
	private static final String TABLE_HEADER_LINES[] = new String[] {
		"______________________________________________________________________________________________________________",
		" Load | Total |       Count       |  Job  |    TP [op/s]    |        |  BW [MB/s]  |Latency [us]|Duration [us]",
		" Type | Concur|-------------------| Time  |-----------------|  Size  |-------------|------------|-------------",
		"      | rency |   Success  |Failed|  [s]  |  Mean  |  Last  |        | Mean | Last |    Mean    |    Mean     ",
		"------|-------|------------|------|-------|--------|--------|--------|------|------|------------|-------------"
	};
	
	private final String runId;
	private final Int2ObjectMap<IoStats.Snapshot> snapshots;
	private final Int2IntMap totalConcurrencyMap;
	
	public MetricsStdoutLogMessage(
		final String runId, final Int2ObjectMap<IoStats.Snapshot> snapshots,
		final Int2IntMap totalConcurrencyMap
	) {
		this.runId = runId;
		this.snapshots = snapshots;
		this.totalConcurrencyMap = totalConcurrencyMap;
	}

	@Override
	public final void formatTo(final StringBuilder buffer) {
		if(snapshots.size() == 1) {
			final int ioTypeCode = snapshots.keySet().iterator().nextInt();
			formatSingleSnapshot(
				buffer, runId, ioTypeCode, snapshots.get(ioTypeCode),
				totalConcurrencyMap.get(ioTypeCode)
			);
		} else {
			formatMultiSnapshot(buffer);
		}
	}

	private static void formatSingleSnapshot(
		final StringBuilder buffer, final String runId, final int ioTypeCode,
		final IoStats.Snapshot snapshot, final int totalConcurrency
	) {
		buffer
			.append(runId).append("\n\t")
			.append(IoType.values()[ioTypeCode]).append('-')
			.append(totalConcurrency).append(": n=(")
			.append(snapshot.getSuccCount()).append('/')
			.append(snapshot.getFailCount()).append("); t[s]=(")
			.append(formatFixedWidth(snapshot.getElapsedTime() / M, 7)).append('/')
			.append(formatFixedWidth(snapshot.getDurationSum() / M, 7)).append("); size=(")
			.append(formatFixedSize(snapshot.getByteCount())).append("); TP[op/s]=(")
			.append(formatFixedWidth(snapshot.getSuccRateMean(), 7)).append('/')
			.append(formatFixedWidth(snapshot.getSuccRateLast(), 7)).append("); BW[MB/s]=(")
			.append(formatFixedWidth(snapshot.getByteRateMean() / MIB, 6)).append('/')
			.append(formatFixedWidth(snapshot.getByteRateLast() / MIB, 6)).append("); dur[us]=(")
			.append((long) snapshot.getDurationAvg()).append('/')
			.append(snapshot.getDurationMin()).append('/')
			.append(snapshot.getDurationMax()).append("); lat[us]=(")
			.append((long) snapshot.getLatencyAvg()).append('/')
			.append(snapshot.getLatencyMin()).append('/')
			.append(snapshot.getLatencyMax()).append(')');
	}

	private void formatMultiSnapshot(final StringBuilder buffer) {
		final StrBuilder strb = new StrBuilder(runId).append(" metrics:");
		if(snapshots.size() > 0) {
			strb.appendNewLine();
			for(final String tableHeaderLine : TABLE_HEADER_LINES) {
				strb.append(tableHeaderLine).appendNewLine();
			}
			IoStats.Snapshot snapshot;
			for(final int ioTypeCode : snapshots.keySet()) {
				snapshot = snapshots.get(ioTypeCode);
				strb
					.appendFixedWidthPadLeft(IoType.values()[ioTypeCode].name(), 6, ' ')
					.append('|');
				strb
					.appendFixedWidthPadLeft(totalConcurrencyMap.get(ioTypeCode), 7, ' ')
					.append('|');
				strb.appendFixedWidthPadLeft(snapshot.getSuccCount(), 12, ' ').append(('|'));
				strb.appendFixedWidthPadLeft(snapshot.getFailCount(), 6, ' ').append('|');
				strb
					.appendFixedWidthPadLeft(
						formatFixedWidth(snapshot.getElapsedTime() / M, 7), 7, ' '
					)
					.append('|');
				strb.appendFixedWidthPadRight(snapshot.getSuccRateMean(), 8, ' ').append('|');
				strb.appendFixedWidthPadRight(snapshot.getSuccRateLast(), 8, ' ').append('|');
				strb
					.appendFixedWidthPadLeft(formatFixedSize(snapshot.getByteCount()), 8, ' ')
					.append('|');
				strb.appendFixedWidthPadRight(snapshot.getByteRateMean() / MIB, 6, ' ').append('|');
				strb.appendFixedWidthPadRight(snapshot.getByteRateLast() / MIB, 6, ' ').append('|');
				strb.appendFixedWidthPadLeft((long) snapshot.getLatencyAvg(), 12, ' ').append('|');
				strb.appendFixedWidthPadLeft((long) snapshot.getDurationAvg(), 12, ' ');
				strb.appendNewLine();
			}
			strb.appendPadding(110, '-');
		} else {
			strb.append(" not available yet");
		}
		buffer.append(strb.toString());
	}

	private static String formatFixedWidth(final double value, final int count) {
		final String valueStr = Double.toString(value);
		if(valueStr.length() > count) {
			return valueStr.substring(0, count);
		} else {
			return valueStr;
		}
	}
}
