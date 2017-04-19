package com.emc.mongoose.common.concurrent;

import java.io.Closeable;

/**
 Created by andrey on 19.04.17.
 */
public interface SvcTask
extends Closeable, Runnable {

	boolean isClosed();

	boolean isActive();
}