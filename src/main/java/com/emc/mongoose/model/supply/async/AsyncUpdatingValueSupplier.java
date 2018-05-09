package com.emc.mongoose.model.supply.async;

import com.emc.mongoose.model.exception.OmgDoesNotPerformException;
import com.github.akurilov.concurrent.InitCallable;
import com.github.akurilov.concurrent.coroutine.Coroutine;
import com.github.akurilov.concurrent.coroutine.CoroutinesExecutor;
import com.github.akurilov.concurrent.coroutine.ExclusiveCoroutineBase;

import com.emc.mongoose.model.supply.BasicUpdatingValueSupplier;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 Created by kurila on 10.02.16.
 */
public class AsyncUpdatingValueSupplier<T>
extends BasicUpdatingValueSupplier<T> {
	
	private static final Logger LOG = Logger.getLogger(AsyncUpdatingValueSupplier.class.getName());
	
	private final Coroutine updateTask;
	
	public AsyncUpdatingValueSupplier(
		final CoroutinesExecutor executor, final T initialValue, final InitCallable<T> updateAction
	) throws OmgDoesNotPerformException {

		super(initialValue, null);
		if(updateAction == null) {
			throw new NullPointerException("Argument should not be null");
		}

		updateTask = new ExclusiveCoroutineBase(executor) {

			@Override
			protected final void invokeTimedExclusively(final long startTimeNanos) {
				try {
					lastValue = updateAction.call();
				} catch(final Exception e) {
					LOG.log(Level.WARNING, "Failed to execute the value update action", e);
					e.printStackTrace(System.err);
				}
			}

			@Override
			protected final void doClose()
			throws IOException {
				lastValue = null;
			}
		};

		try {
			updateTask.start();
		} catch(final RemoteException ignored) {
		}
	}
	
	public static abstract class InitializedCallableBase<T>
	implements InitCallable<T> {
		@Override
		public final boolean isInitialized() {
			return true;
		}
	}
	
	@Override
	public final T get() {
		// do not refresh on the request
		return lastValue;
	}
	
	@Override
	public final int get(final List<T> buffer, final int limit) {
		int count = 0;
		for(; count < limit; count ++) {
			buffer.add(lastValue);
		}
		return count;
	}
	
	@Override
	public long skip(final long count) {
		return 0;
	}
	
	@Override
	public void close()
	throws IOException {
		super.close();
		updateTask.close();
	}
}