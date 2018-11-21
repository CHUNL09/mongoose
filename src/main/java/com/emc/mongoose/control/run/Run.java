package com.emc.mongoose.control.run;

public interface Run
extends Runnable {

	/**
	 @return the count of the milliseconds since 1970-01-01 and the start
	 @throws if not started yet
	 */
	long startTimeMillis()
	throws IllegalStateException;

	/**
	 @return user comment for this run
	 */
	String comment();
}
