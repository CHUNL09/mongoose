package com.emc.mongoose.storage.mock.api;

import com.emc.mongoose.common.collection.Listable;
import com.emc.mongoose.model.item.MutableDataItem;

import java.io.Closeable;
import java.util.Collection;

/**
 Created on 19.07.16.
 */
public interface ObjectContainerMock<T extends MutableDataItem>
extends Closeable, Listable<T> {

	T get (final String key);

	T put(final String key, final T value);

	T remove(final String key);

	int size();

	Collection<T> values();
}
