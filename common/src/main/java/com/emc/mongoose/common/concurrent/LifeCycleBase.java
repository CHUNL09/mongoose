package com.emc.mongoose.common.concurrent;

import java.util.concurrent.atomic.AtomicReference;

/**
 Created on 12.07.16.
 */
public abstract class LifeCycleBase
implements LifeCycle {

	protected final AtomicReference<State> state = new AtomicReference<>(State.INITIALIZED);

	protected enum State {
		INITIALIZED, STARTED, SHUTDOWN, INTERRUPTED
	}

	protected abstract void doStart();

	protected abstract void doShutdown();

	protected abstract void doInterrupt();

	@Override
	public final boolean isInitialized() {
		return state.get().equals(State.INITIALIZED);
	}

	@Override
	public final void start()
	throws IllegalStateException {
	}

	@Override
	public final boolean isStarted() {
		return state.get().equals(State.STARTED);
	}

	@Override
	public final void shutdown()
	throws IllegalStateException {
	}

	@Override
	public final boolean isShutdown() {
		return state.get().equals(State.SHUTDOWN);
	}

	@Override
	public final void interrupt()
	throws IllegalStateException {
	}

	@Override
	public final boolean isInterrupted() {
		return state.get().equals(State.INTERRUPTED);
	}
}