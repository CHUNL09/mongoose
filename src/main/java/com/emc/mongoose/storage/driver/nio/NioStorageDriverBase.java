package com.emc.mongoose.storage.driver.nio;

import com.emc.mongoose.storage.driver.coop.CoopStorageDriverBase;
import static com.emc.mongoose.Constants.KEY_CLASS_NAME;
import static com.emc.mongoose.Constants.KEY_STEP_ID;
import static com.emc.mongoose.model.io.task.IoTask.Status.ACTIVE;
import static com.emc.mongoose.model.io.task.IoTask.Status.INTERRUPTED;
import static com.emc.mongoose.model.io.task.IoTask.Status.PENDING;
import com.emc.mongoose.model.exception.OmgShootMyFootException;
import com.emc.mongoose.model.concurrent.ThreadDump;
import com.emc.mongoose.model.data.DataInput;
import com.emc.mongoose.model.io.task.IoTask;
import com.emc.mongoose.model.item.Item;
import com.emc.mongoose.config.load.LoadConfig;
import com.emc.mongoose.config.storage.StorageConfig;
import com.emc.mongoose.logging.LogUtil;
import com.emc.mongoose.logging.Loggers;

import com.github.akurilov.fiber4j.ExclusiveFiberBase;
import com.github.akurilov.fiber4j.Fiber;
import com.github.akurilov.fiber4j.FibersExecutor;

import com.github.akurilov.commons.collection.OptLockArrayBuffer;
import com.github.akurilov.commons.collection.OptLockBuffer;
import com.github.akurilov.commons.concurrent.ThreadUtil;

import org.apache.logging.log4j.CloseableThreadContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.ThreadContext;

import java.io.IOException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 Created by kurila on 19.07.16.
 The multi-threaded non-blocking I/O storage driver.
 Note that this kind of storage driver uses the service coroutines facility to execute the I/O
 */
public abstract class NioStorageDriverBase<I extends Item, O extends IoTask<I>>
extends CoopStorageDriverBase<I, O>
implements NioStorageDriver<I, O> {

	private final static String CLS_NAME = NioStorageDriverBase.class.getSimpleName();
	private final static FibersExecutor IO_EXECUTOR = new FibersExecutor(false);

	private final int ioWorkerCount;
	private final int ioTaskBuffCapacity;
	private final List<Fiber> ioFibers;
	private final OptLockBuffer<O> ioTaskBuffs[];
	private final AtomicLong rrc = new AtomicLong(0);

	@SuppressWarnings("unchecked")
	public NioStorageDriverBase(
		final String testStepId, final DataInput dataInput, final LoadConfig loadConfig,
		final StorageConfig storageConfig, final boolean verifyFlag
	) throws OmgShootMyFootException {
		super(testStepId, dataInput, loadConfig, storageConfig, verifyFlag);
		final int confWorkerCount = storageConfig.getDriverConfig().getThreads();
		if(confWorkerCount > 0) {
			ioWorkerCount = confWorkerCount;
		} else if(concurrencyLevel > 0) {
			ioWorkerCount = Math.min(concurrencyLevel, ThreadUtil.getHardwareThreadCount());
		} else {
			ioWorkerCount = ThreadUtil.getHardwareThreadCount();
		}
		ioFibers = new ArrayList<>(ioWorkerCount);
		ioTaskBuffs = new OptLockBuffer[ioWorkerCount];
		ioTaskBuffCapacity = Math.max(MIN_TASK_BUFF_CAPACITY, concurrencyLevel / ioWorkerCount);
		for(int i = 0; i < ioWorkerCount; i ++) {
			ioTaskBuffs[i] = new OptLockArrayBuffer<>(ioTaskBuffCapacity);
			ioFibers.add(new NioFiber(IO_EXECUTOR, ioTaskBuffs[i]));
		}
	}

	/**
	 The class represents the non-blocking I/O task execution algorithm.
	 The I/O task itself may correspond to a large data transfer so it can't be non-blocking.
	 So the I/O task may be invoked multiple times (in the reentrant manner).
	 */
	private final class NioFiber
	extends ExclusiveFiberBase {

		private final OptLockBuffer<O> ioTaskBuff;
		private final List<O> ioTaskLocalBuff;

		private int ioTaskBuffSize;
		private O ioTask;

		public NioFiber(final FibersExecutor executor, final OptLockBuffer<O> ioTaskBuff) {
			super(executor, ioTaskBuff);
			this.ioTaskBuff = ioTaskBuff;
			this.ioTaskLocalBuff = new ArrayList<>(ioTaskBuffCapacity);
		}

		@Override
		protected final void invokeTimedExclusively(final long startTimeNanos) {

			ThreadContext.put(KEY_STEP_ID, stepId);

			ioTaskBuffSize = ioTaskBuff.size();
			if(ioTaskBuffSize > 0) {
				try {
					for(int i = 0; i < ioTaskBuffSize; i ++) {
						ioTask = ioTaskBuff.get(i);
						// if timeout, put the task into the temporary buffer
						if(System.nanoTime() - startTimeNanos >= TIMEOUT_NANOS) {
							ioTaskLocalBuff.add(ioTask);
							continue;
						}
						// check if the task is invoked 1st time
						if(PENDING.equals(ioTask.status())) {
							// do not start the new task if the state is not more active
							if(!isStarted()) {
								continue;
							}
							// respect the configured concurrency level
							if(!concurrencyThrottle.tryAcquire()) {
								ioTaskLocalBuff.add(ioTask);
								continue;
							}
							// mark the task as active
							ioTask.startRequest();
							ioTask.finishRequest();
						}
						// perform non blocking I/O for the task
						invokeNio(ioTask);
						// remove the task from the buffer if it is not active more
						if(!ACTIVE.equals(ioTask.status())) {
							concurrencyThrottle.release();
							ioTaskCompleted(ioTask);
						} else {
							// the task remains in the buffer for the next iteration
							ioTaskLocalBuff.add(ioTask);
						}
					}
				} catch(final Throwable cause) {
					LogUtil.exception(Level.ERROR, cause, "I/O worker failure");
				} finally {
					// put the active tasks back into the buffer
					ioTaskBuff.clear();
					ioTaskBuffSize = ioTaskLocalBuff.size();
					if(ioTaskBuffSize > 0) {
						for(int i = 0; i < ioTaskBuffSize; i ++) {
							ioTaskBuff.add(ioTaskLocalBuff.get(i));
						}
						ioTaskLocalBuff.clear();
					}
				}
			}
		}

		@Override
		protected final void doClose() {
			ioTaskBuffSize = ioTaskBuff.size();
			Loggers.MSG.debug("Finish {} remaining active tasks finally", ioTaskBuffSize);
			for(int i = 0; i < ioTaskBuffSize; i ++) {
				ioTask = ioTaskBuff.get(i);
				if(ACTIVE.equals(ioTask.status())) {
					ioTask.status(INTERRUPTED);
					concurrencyThrottle.release();
					ioTaskCompleted(ioTask);
				}
			}
			Loggers.MSG.debug("Finish the remaining active tasks done");
		}
	}

	/**
	 Reentrant method which decorates the actual non-blocking create/read/etc I/O operation.
	 May change the task status or not change if the I/O operation is not completed during this
	 particular invocation
	 @param ioTask
	 */
	protected abstract void invokeNio(final O ioTask);

	@Override
	protected final void doStart()
	throws IllegalStateException {
		super.doStart();
		for(final Fiber ioFiber : ioFibers) {
			try {
				ioFiber.start();
			} catch(final RemoteException ignored) {
			}
		}
	}

	@Override
	protected final void doStop()
	throws IllegalStateException {
		for(final Fiber ioCoroutine : ioFibers) {
			try {
				ioCoroutine.stop();
			} catch(final RemoteException ignored) {
			}
		}
	}

	@Override
	protected final boolean submit(final O ioTask)
	throws IllegalStateException {
		OptLockBuffer<O> ioTaskBuff;
		for(int i = 0; i < ioWorkerCount; i ++) {
			if(!isStarted()) {
				throw new IllegalStateException();
			}
			ioTaskBuff = ioTaskBuffs[(int) (rrc.getAndIncrement() % ioWorkerCount)];
			if(ioTaskBuff.tryLock()) {
				try {
					return ioTaskBuff.size() < ioTaskBuffCapacity && ioTaskBuff.add(ioTask);
				} finally {
					ioTaskBuff.unlock();
				}
			} else {
				i ++;
			}
		}
		return false;
	}

	@Override
	protected final int submit(final List<O> ioTasks, final int from, final int to)
	throws IllegalStateException {
		OptLockBuffer<O> ioTaskBuff;
		int j = from;
		int k;
		int n;
		for(int i = 0; i < ioWorkerCount; i ++) {
			if(!isStarted()) {
				throw new IllegalStateException();
			}
			ioTaskBuff = ioTaskBuffs[(int) (rrc.getAndIncrement() % ioWorkerCount)];
			if(ioTaskBuff.tryLock()) {
				try {
					n = Math.min(to - j, ioTaskBuffCapacity - ioTaskBuff.size());
					for(k = 0; k < n; k ++) {
						ioTaskBuff.add(ioTasks.get(j + k));
					}
					j += n;
				} finally {
					ioTaskBuff.unlock();
				}
			}
		}
		return j - from;
	}

	@Override
	protected final int submit(final List<O> ioTasks)
	throws IllegalStateException {
		return submit(ioTasks, 0, ioTasks.size());
	}
	
	protected final void finishIoTask(final O ioTask) {
		try {
			ioTask.startResponse();
			ioTask.finishResponse();
			ioTask.status(IoTask.Status.SUCC);
		} catch(final IllegalStateException e) {
			LogUtil.exception(
				Level.WARN, e, "{}: finishing the I/O task which is in an invalid state",
				ioTask.toString()
			);
			ioTask.status(IoTask.Status.FAIL_UNKNOWN);
		}
	}
	
	@Override
	protected void doClose()
	throws IOException {
		super.doClose();
		for(final Fiber ioFiber : ioFibers) {
			ioFiber.close();
		}
		for(int i = 0; i < ioWorkerCount; i ++) {
			try(
				final CloseableThreadContext.Instance logCtx = CloseableThreadContext.put(
					KEY_CLASS_NAME, CLS_NAME
				)
			) {
				if(ioTaskBuffs[i].tryLock(Fiber.TIMEOUT_NANOS, TimeUnit.NANOSECONDS)) {
					ioTaskBuffs[i].clear();
				} else if(ioTaskBuffs[i].size() > 0){
					Loggers.ERR.debug(
						"Failed to obtain the I/O tasks buff lock in time, thread dump:\n{}",
						new ThreadDump().toString()
					);
				}
			} catch(final InterruptedException e) {
				LogUtil.exception(
					Level.WARN, e, "Unexpected failure, I/O tasks buff remains uncleared"
				);
			}
			ioTaskBuffs[i] = null;
		}
		ioFibers.clear();
	}
}
