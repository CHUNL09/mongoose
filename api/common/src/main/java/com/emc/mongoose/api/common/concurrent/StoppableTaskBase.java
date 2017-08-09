package com.emc.mongoose.api.common.concurrent;

import java.io.IOException;

/**
 Created by andrey on 19.04.17.
 */
public abstract class StoppableTaskBase
implements StoppableTask {

	private volatile boolean isClosedFlag = false;

	@Override
	public void close()
	throws IOException {
		isClosedFlag = true;
		doClose();
	}

	@Override
	public void run() {
		if(!isClosedFlag) {
			invoke();
		}
	}

	@Override
	public final boolean isClosed() {
		return isClosedFlag;
	}

	/**
	 The task invocation method. Will not run if the task is stopped (closed).
	 */
	protected abstract void invoke();

	/**
	 Implement this method for the cleanup purposes.
	 @throws IOException
	 */
	protected abstract void doClose()
	throws IOException;
}