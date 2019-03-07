package com.emc.mongoose.base.supply.async;

import com.emc.mongoose.base.exception.OmgDoesNotPerformException;
import com.github.akurilov.fiber4j.FibersExecutor;
import java.text.DecimalFormat;
import java.text.NumberFormat;

public final class AsyncRangeDefinedLongFormattingSupplier
				extends AsyncRangeDefinedSupplierBase<Long> {

	private final NumberFormat format;

	public AsyncRangeDefinedLongFormattingSupplier(
					final FibersExecutor executor,
					final long seed,
					final long minValue,
					final long maxValue,
					final String formatStr)
					throws OmgDoesNotPerformException {
		super(executor, seed, minValue, maxValue);
		this.format = formatStr == null || formatStr.isEmpty() ? null : new DecimalFormat(formatStr);
	}

	@Override
	protected final Long computeRange(final Long minValue, final Long maxValue) {
		return maxValue - minValue + 1;
	}

	@Override
	protected final Long rangeValue() {
		return minValue() + rnd.nextLong(range());
	}

	@Override
	protected final Long singleValue() {
		return rnd.nextLong();
	}

	@Override
	protected String toString(final Long value) {
		return format == null ? value.toString() : format.format(value);
	}
}
