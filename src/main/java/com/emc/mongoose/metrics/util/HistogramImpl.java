package com.emc.mongoose.metrics.util;

import java.util.concurrent.atomic.LongAdder;

/**
 @author veronika K. on 01.10.18 */
public class HistogramImpl
implements Histogram {

	private final LongReservoir reservoir;
	private final LongAdder count;

	public HistogramImpl(final LongReservoir reservoir) {
		this.reservoir = reservoir;
		this.count = new LongAdder();
	}

	@Override
	public void update(final int value) {
		update((long) value);
	}

	@Override
	public void update(final long value) {
		count.increment();
		reservoir.update(value);
	}

	@Override
	public long count() {
		return count.longValue();
	}

	@Override
	public HistogramSnapshotImpl snapshot() {
		return new HistogramSnapshotImpl(reservoir.snapshot());
	}
}
