package com.emc.mongoose.metrics;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.lang.Math.floor;

/**
 @author veronika K. on 25.09.18 */
public class Snapshot {

	private double mean;
	private long count;
	private long sum;
	private double med;
	private long min;
	private long max;
	private final long[] values;

	private void init(){
		Arrays.sort(this.values);
		this.min = min();
		this.max = max();
		this.mean = mean();
		this.med = median();
		this.count = size();
		this.sum = new Double(this.count * this.mean).longValue();
	}

	public Snapshot(final Collection<Long> values) {
		final Object[] copy = values.toArray();
		this.values = new long[copy.length];
		for(int i = 0; i < copy.length; i++) {
			this.values[i] = (Long) copy[i];
		}
		init();
	}

	public Snapshot(final long[] values) {
		this.values = Arrays.copyOf(values, values.length);
		init();
	}

	public Snapshot(final List<Snapshot> snapshots) {
		for(Snapshot s : snapshots) {
			final long[] copy = Arrays.copyOf(s.values(),s.size());
			this.values = new long[copy.length];
			for(int i = 0; i < copy.length; i++) {
				this.values[i] = (Long) copy[i];
			}
		}
		init();
	}

	public double quantile(final double quantile) {
		if(quantile < 0.0 || quantile > 1.0 || Double.isNaN(quantile)) {
			throw new IllegalArgumentException(quantile + " is not in [0..1]");
		}
		if(values.length == 0) {
			return 0.0;
		}
		final double pos = quantile * (values.length + 1);
		final int index = (int) pos;
		if(index < 1) {
			return values[0];
		}
		if(index >= values.length) {
			return values[values.length - 1];
		}
		final double lower = values[index - 1];
		final double upper = values[index];
		return lower + (pos - floor(pos)) * (upper - lower);
	}

	public int size() {
		return values.length;
	}

	public long[] values() {
		return Arrays.copyOf(values, values.length);
	}

	public long max() {
		if(values.length == 0) {
			return 0;
		}
		return values[values.length - 1];
	}

	public long min() {
		if(values.length == 0) {
			return 0;
		}
		return values[0];
	}

	public double mean() {
		if(values.length == 0) {
			return 0;
		}
		double sum = 0;
		for(long value : values) {
			sum += value;
		}
		return sum / values.length;
	}

	public double median() {
		return quantile(0.5);
	}
}
