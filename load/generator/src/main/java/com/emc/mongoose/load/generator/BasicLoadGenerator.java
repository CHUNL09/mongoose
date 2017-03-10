package com.emc.mongoose.load.generator;

import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.common.concurrent.WeightThrottle;
import com.emc.mongoose.model.DaemonBase;
import com.emc.mongoose.common.concurrent.Throttle;
import com.emc.mongoose.common.io.Output;
import com.emc.mongoose.common.exception.UserShootHisFootException;
import static com.emc.mongoose.common.Constants.BATCH_SIZE;
import com.emc.mongoose.model.io.IoType;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.Markers;
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.model.io.task.IoTask;
import com.emc.mongoose.model.io.task.IoTaskBuilder;
import com.emc.mongoose.model.item.Item;
import com.emc.mongoose.model.load.LoadGenerator;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.EOFException;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

/**
 Created by kurila on 11.07.16.
 */
public class BasicLoadGenerator<I extends Item, O extends IoTask<I>>
extends DaemonBase
implements LoadGenerator<I, O> {

	private static final Logger LOG = LogManager.getLogger();

	private volatile WeightThrottle weightThrottle = null;
	private volatile Throttle<Object> rateThrottle = null;
	private volatile Output<O> ioTaskOutput;

	private final Input<I> itemInput;
	private final SizeInBytes itemSizeEstimate;
	private final Thread worker;
	private final long countLimit;
	private final boolean shuffleFlag;
	private final IoTaskBuilder<I, O> ioTaskBuilder;

	private long generatedIoTaskCount = 0;

	@SuppressWarnings("unchecked")
	public BasicLoadGenerator(
		final Input<I> itemInput, final SizeInBytes itemSizeEstimate,
		final IoTaskBuilder<I, O> ioTaskBuilder, final long countLimit, final SizeInBytes sizeLimit,
		final boolean shuffleFlag
	) throws UserShootHisFootException {

		this.itemInput = itemInput;
		this.itemSizeEstimate = itemSizeEstimate;
		this.ioTaskBuilder = ioTaskBuilder;
		if(countLimit > 0) {
			this.countLimit = countLimit;
		} else if(
			sizeLimit.get() > 0 && itemSizeEstimate.get() > 0 &&
				itemSizeEstimate.getMin() == itemSizeEstimate.getMax()
		) {
			this.countLimit = sizeLimit.get() / itemSizeEstimate.get();
		} else {
			this.countLimit = Long.MAX_VALUE;
		}
		this.shuffleFlag = shuffleFlag;

		final String ioStr = ioTaskBuilder.getIoType().toString();
		worker = new Thread(
			new GeneratorTask(),
			Character.toUpperCase(ioStr.charAt(0)) + ioStr.substring(1).toLowerCase() +
				(countLimit > 0 && countLimit < Long.MAX_VALUE ? Long.toString(countLimit) : "") +
				itemInput.toString()
		);
		worker.setDaemon(true);
	}
	
	@Override
	public final void setWeightThrottle(final WeightThrottle weightThrottle) {
		this.weightThrottle = weightThrottle;
	}

	@Override
	public final void setRateThrottle(final Throttle<Object> rateThrottle) {
		this.rateThrottle = rateThrottle;
	}

	@Override
	public final void setOutput(final Output<O> ioTaskOutput) {
		this.ioTaskOutput = ioTaskOutput;
	}

	@Override
	public final long getGeneratedIoTasksCount() {
		return generatedIoTaskCount;
	}

	@Override
	public final SizeInBytes getItemSizeEstimate() {
		return itemSizeEstimate;
	}

	@Override
	public final IoType getIoType() {
		return ioTaskBuilder.getIoType();
	}

	private final class GeneratorTask
	implements Runnable {

		private final Random rnd = new Random();

		@Override
		public final void run() {

			if(ioTaskOutput == null) {
				LOG.warn(
					Markers.ERR, "{}: no load I/O task output set, exiting",
					BasicLoadGenerator.this.toString()
				);
			}

			int n;
			int i, j, k;
			long remaining;
			List<I> items;

			while(!worker.isInterrupted()) {

				// find the limits and prepare the items buffer
				remaining = countLimit - generatedIoTaskCount;
				if(remaining <= 0) {
					break;
				}
				n = BATCH_SIZE > remaining ? (int) remaining : BATCH_SIZE;
				items = new ArrayList<>(n);

				// get the items from the input
				try {
					n = itemInput.get(items, n);
				} catch(final EOFException e) {
					LOG.debug(
						Markers.MSG, "{}: end of items input @ the count {}",
						BasicLoadGenerator.this.toString(),
						generatedIoTaskCount
					);
					break;
				} catch(final Exception e) {
					LogUtil.exception(
						LOG, Level.ERROR, e, "{}: failed to get the items from the input",
						BasicLoadGenerator.this.toString()
					);
					break;
				}

				if(worker.isInterrupted()) {
					break;
				}

				if(n > 0) {
					if(shuffleFlag) {
						Collections.shuffle(items, rnd);
					}
					try {
						// build the I/O tasks for the items got from the input
						final List<O> ioTasks = ioTaskBuilder.getInstances(items);
						i = j = 0;
						while(i < n) {
							// pass the throttles
							j += acquireThrottlesPermit(ioTasks, i, n);
							if(i == j) {
								LockSupport.parkNanos(1);
								continue;
							}
							k = i;
							while(k < j) {
								// feed the items to the I/O tasks consumer
								k += ioTaskOutput.put(ioTasks, k, j);
								LockSupport.parkNanos(1);
							}
							i = j;
						}
						generatedIoTaskCount += n;
					} catch(final EOFException e) {
						LOG.debug(
							Markers.MSG, "{}: finish due to output's EOF",
							BasicLoadGenerator.this.toString()
						);
						break;
					} catch(final RemoteException e) {
						final Throwable cause = e.getCause();
						if(cause instanceof EOFException) {
							LOG.debug(
								Markers.MSG, "{}: finish due to output's EOF",
								BasicLoadGenerator.this.toString()
							);
							break;
						} else if(!isStarted()){
							LogUtil.exception(LOG, Level.ERROR, cause, "Unexpected failure");
							e.printStackTrace(System.err);
						}
					} catch(final Exception e) {
						if(!isStarted()) {
							LogUtil.exception(LOG, Level.ERROR, e, "Unexpected failure");
							e.printStackTrace(System.err);
						}
					}
				} else {
					if(worker.isInterrupted()) {
						break;
					}
				}
			}

			LOG.debug(
				Markers.MSG, "{}: produced {} items", BasicLoadGenerator.this.toString(),
				generatedIoTaskCount
			);
			try {
				shutdown();
			} catch(final IllegalStateException ignored) {
			}
		}
	}

	private int acquireThrottlesPermit(final List<O> ioTasks, final int from, final int to) {
		int n = to - from;
		final int originCode = hashCode();
		if(weightThrottle != null) {
			n = weightThrottle.tryAcquire(originCode, n);
		}
		if(rateThrottle != null) {
			n = rateThrottle.tryAcquire(originCode, n);
		}
		return n;
	}

	@Override
	protected final void doStart()
	throws IllegalStateException {
		worker.start();
	}

	@Override
	protected final void doShutdown() {
		interrupt();
	}

	@Override
	protected final void doInterrupt() {
		worker.interrupt();
	}

	@Override
	public final boolean await(final long timeout, final TimeUnit timeUnit)
	throws InterruptedException {
		timeUnit.timedJoin(worker, timeout);
		return true;
	}

	@Override
	protected final void doClose()
	throws IOException {
		if(itemInput != null) {
			itemInput.close();
		}
		ioTaskBuilder.close();
	}
	
	@Override
	public final String toString() {
		return worker.getName();
	}
	
	@Override
	public final int hashCode() {
		return ioTaskBuilder.hashCode();
	}
}
