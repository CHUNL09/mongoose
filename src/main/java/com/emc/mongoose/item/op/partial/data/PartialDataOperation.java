package com.emc.mongoose.item.op.partial.data;

import com.emc.mongoose.item.DataItem;
import com.emc.mongoose.item.op.composite.data.CompositeDataOperation;
import com.emc.mongoose.item.op.data.DataOperation;
import com.emc.mongoose.item.op.partial.PartialOperation;

/** Created by andrey on 25.11.16. */
public interface PartialDataOperation<I extends DataItem>
    extends DataOperation<I>, PartialOperation<I> {

  @Override
  I item();

  @Override
  CompositeDataOperation<I> parent();
}
