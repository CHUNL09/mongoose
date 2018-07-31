package com.emc.mongoose.exception;

/**
 Should be thrown in case of catching {@code java.lang.InterruptedException}.
 Extends the {@code java.lang.RuntimeException} in order to be passed on the uppermost level of the stacktrace.
 */
public class InterruptRunException
extends RuntimeException {

	public InterruptRunException() {
		super();
	}

	public InterruptRunException(final String msg) {
		super(msg);
	}

	public InterruptRunException(final String msg, final Throwable cause) {
		super(msg, cause);
	}

	public InterruptRunException(final Throwable cause) {
		super(cause);
	}
}
