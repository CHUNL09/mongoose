package com.emc.mongoose.supply.async;

import com.emc.mongoose.exception.OmgDoesNotPerformException;
import com.emc.mongoose.supply.RangeDefinedSupplier;
import com.github.akurilov.fiber4j.FibersExecutor;
import java.text.Format;
import java.util.Date;
import org.apache.commons.lang.time.FastDateFormat;

public final class AsyncRangeDefinedDateFormattingSupplier
    extends AsyncRangeDefinedSupplierBase<Date> {

  private final Format format;
  private final RangeDefinedSupplier<Long> longGenerator;

  public AsyncRangeDefinedDateFormattingSupplier(
      final FibersExecutor executor,
      final long seed,
      final Date minValue,
      final Date maxValue,
      final String formatString)
      throws OmgDoesNotPerformException {
    super(executor, seed, minValue, maxValue);
    this.format =
        formatString == null || formatString.isEmpty()
            ? null
            : FastDateFormat.getInstance(formatString);
    longGenerator =
        new AsyncRangeDefinedLongFormattingSupplier(
            executor, seed, minValue.getTime(), maxValue.getTime(), null);
  }

  @Override
  protected final Date computeRange(final Date minValue, final Date maxValue) {
    return null;
  }

  @Override
  protected final Date rangeValue() {
    return new Date(longGenerator.value());
  }

  @Override
  protected final Date singleValue() {
    return new Date(longGenerator.value());
  }

  @Override
  protected final String toString(final Date value) {
    return format == null ? value.toString() : format.format(value);
  }

  @Override
  public final boolean isInitialized() {
    return longGenerator != null;
  }
}
