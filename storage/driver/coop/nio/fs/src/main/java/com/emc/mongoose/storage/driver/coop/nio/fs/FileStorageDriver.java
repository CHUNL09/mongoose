package com.emc.mongoose.storage.driver.coop.nio.fs;

import com.emc.mongoose.base.data.DataCorruptionException;
import com.emc.mongoose.base.data.DataInput;
import com.emc.mongoose.base.data.DataSizeException;
import com.emc.mongoose.base.exception.OmgShootMyFootException;
import com.emc.mongoose.base.item.DataItem;
import com.emc.mongoose.base.item.Item;
import com.emc.mongoose.base.item.ItemFactory;
import com.emc.mongoose.base.item.op.OpType;
import com.emc.mongoose.base.item.op.Operation;
import com.emc.mongoose.base.item.op.data.DataOperation;
import com.emc.mongoose.base.item.op.path.PathOperation;
import com.emc.mongoose.base.logging.LogUtil;
import com.emc.mongoose.base.logging.Loggers;
import com.emc.mongoose.base.storage.Credential;
import com.emc.mongoose.storage.driver.coop.nio.NioStorageDriver;
import com.emc.mongoose.storage.driver.coop.nio.NioStorageDriverBase;

import com.github.akurilov.commons.collection.Range;
import com.github.akurilov.confuse.Config;

import org.apache.logging.log4j.Level;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.spi.AbstractInterruptibleChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 Created by kurila on 19.07.16.
 */
public final class FileStorageDriver<I extends Item, O extends Operation<I>>
				extends NioStorageDriverBase<I, O>
				implements NioStorageDriver<I, O> {

	private final Map<DataOperation, FileChannel> srcOpenFiles = new ConcurrentHashMap<>();
	private final Map<String, File> dstParentDirs = new ConcurrentHashMap<>();
	private final Map<DataOperation, FileChannel> dstOpenFiles = new ConcurrentHashMap<>();

	public FileStorageDriver(
					final String stepId, final DataInput dataInput, final Config storageConfig, final boolean verifyFlag,
					final int batchSize) throws OmgShootMyFootException {
		super(stepId, dataInput, storageConfig, verifyFlag, batchSize);
		requestAuthTokenFunc = null; // do not use
	}

	private <F extends DataItem, D extends DataOperation<F>> FileChannel openDstFile(final D dataOp) {
		final String fileItemName = dataOp.item().name();
		final OpType opType = dataOp.type();
		final String dstPath = dataOp.dstPath();
		final Path itemPath;
		try {
			if (dstPath == null || dstPath.isEmpty() || fileItemName.startsWith(dstPath)) {
				itemPath = FsConstants.FS.getPath(fileItemName);
			} else {
				dstParentDirs.computeIfAbsent(dstPath, DirIoHelper::createParentDir);
				itemPath = FsConstants.FS.getPath(dstPath, fileItemName);
			}
			if (OpType.CREATE.equals(opType)) {
				return FsConstants.FS_PROVIDER.newFileChannel(itemPath, FsConstants.CREATE_OPEN_OPT);
			} else {
				return FsConstants.FS_PROVIDER.newFileChannel(itemPath, FsConstants.WRITE_OPEN_OPT);
			}
		} catch (final AccessDeniedException e) {
			dataOp.status(Operation.Status.RESP_FAIL_AUTH);
			LogUtil.exception(Level.DEBUG, e, "Access denied to open the output channel for the path \"{}\"", dstPath);
		} catch (final NoSuchFileException e) {
			dataOp.status(Operation.Status.FAIL_IO);
			LogUtil.exception(Level.DEBUG, e, "Failed to open the output channel for the path \"{}\"", dstPath);
		} catch (final FileSystemException e) {
			final long freeSpace = (new File(e.getFile())).getFreeSpace();
			if (freeSpace > 0) {
				dataOp.status(Operation.Status.FAIL_IO);
				LogUtil.exception(Level.DEBUG, e, "Failed to open the output channel for the path \"{}\"", dstPath);
			} else {
				dataOp.status(Operation.Status.RESP_FAIL_SPACE);
				LogUtil.exception(Level.DEBUG, e, "No free space for the path \"{}\"", dstPath);
			}
		} catch (final IOException e) {
			dataOp.status(Operation.Status.FAIL_IO);
			LogUtil.exception(Level.DEBUG, e, "Failed to open the output channel for the path \"{}\"", dstPath);
		} catch (final Throwable cause) {
			dataOp.status(Operation.Status.FAIL_UNKNOWN);
			LogUtil.exception(Level.WARN, cause, "Failed to open the output channel for the path \"{}\"", dstPath);
		}
		return null;
	}

	@Override
	protected final String requestNewPath(final String path) {
		final File pathFile = FsConstants.FS.getPath(path).toFile();
		if (!pathFile.exists()) {
			pathFile.mkdirs();
		}
		return path;
	}

	@Override
	protected final String requestNewAuthToken(final Credential credential) {
		throw new AssertionError("Should not be invoked");
	}

	@Override
	public List<I> list(
					final ItemFactory<I> itemFactory, final String path, final String prefix, final int idRadix,
					final I lastPrevItem, final int count) throws IOException {
		return ListingHelper.list(itemFactory, path, prefix, idRadix, lastPrevItem, count);
	}

	@Override
	public final void adjustIoBuffers(final long avgTransferSize, final OpType opType) {}

	@Override
	protected final void invokeNio(final O op) {
		if (op instanceof DataOperation) {
			invokeFileNio((DataOperation<? extends DataItem>) op);
		} else if (op instanceof PathOperation) {
			throw new AssertionError("Not implemented");
		} else {
			throw new AssertionError("Not implemented");
		}
	}

	protected final <F extends DataItem, D extends DataOperation<F>> void invokeFileNio(final D op) {

		FileChannel srcChannel = null;
		FileChannel dstChannel = null;

		try {

			final OpType opType = op.type();
			final F item = op.item();

			switch (opType) {

			case NOOP:
				finishOperation((O) op);
				break;

			case CREATE:
				dstChannel = dstOpenFiles.computeIfAbsent(op, this::openDstFile);
				srcChannel = srcOpenFiles.computeIfAbsent(op, FileIoHelper::openSrcFile);
				if (dstChannel == null) {
					break;
				}
				if (srcChannel == null) {
					if (op.status().equals(Operation.Status.FAIL_IO)) {
						break;
					} else {
						if (FileIoHelper.invokeCreate(item, op, dstChannel)) {
							finishOperation((O) op);
						}
					}
				} else { // copy the data from the src channel to the dst channel
					if (FileIoHelper.invokeCopy(item, op, srcChannel, dstChannel)) {
						finishOperation((O) op);
					}
				}
				break;

			case READ:
				srcChannel = srcOpenFiles.computeIfAbsent(op, FileIoHelper::openSrcFile);
				if (srcChannel == null) {
					break;
				}
				final List<Range> fixedRangesToRead = op.fixedRanges();
				if (verifyFlag) {
					try {
						if (fixedRangesToRead == null || fixedRangesToRead.isEmpty()) {
							if (op.hasMarkedRanges()) {
								if (FileIoHelper.invokeReadAndVerifyRandomRanges(
												item, op, srcChannel, op.markedRangesMaskPair())) {
									finishOperation((O) op);
								}
							} else {
								if (FileIoHelper.invokeReadAndVerify(item, op, srcChannel)) {
									finishOperation((O) op);
								}
							}
						} else {
							if (FileIoHelper.invokeReadAndVerifyFixedRanges(item, op, srcChannel, fixedRangesToRead)) {
								finishOperation((O) op);
							}
						}
					} catch (final DataSizeException e) {
						op.status(Operation.Status.RESP_FAIL_CORRUPT);
						final long countBytesDone = op.countBytesDone() + e.getOffset();
						op.countBytesDone(countBytesDone);
						Loggers.MSG.debug(
										"{}: content size mismatch, expected: {}, actual: {}", item.name(), item.size(),
										countBytesDone);
					} catch (final DataCorruptionException e) {
						op.status(Operation.Status.RESP_FAIL_CORRUPT);
						final long countBytesDone = op.countBytesDone() + e.getOffset();
						op.countBytesDone(countBytesDone);
						Loggers.MSG.debug(
										"{}: content mismatch @ offset {}, expected: {}, actual: {} ", item.name(),
										countBytesDone, String.format("\"0x%X\"", (int) (e.expected & 0xFF)),
										String.format("\"0x%X\"", (int) (e.actual & 0xFF)));
					}
				} else {
					if (fixedRangesToRead == null || fixedRangesToRead.isEmpty()) {
						if (op.hasMarkedRanges()) {
							if (FileIoHelper.invokeReadRandomRanges(item, op, srcChannel, op.markedRangesMaskPair())) {
								finishOperation((O) op);
							}
						} else {
							if (FileIoHelper.invokeRead(item, op, srcChannel)) {
								finishOperation((O) op);
							}
						}
					} else {
						if (FileIoHelper.invokeReadFixedRanges(item, op, srcChannel, fixedRangesToRead)) {
							finishOperation((O) op);
						}
					}
				}
				break;

			case UPDATE:
				dstChannel = dstOpenFiles.computeIfAbsent(op, this::openDstFile);
				if (dstChannel == null) {
					break;
				}
				final List<Range> fixedRangesToUpdate = op.fixedRanges();
				if (fixedRangesToUpdate == null || fixedRangesToUpdate.isEmpty()) {
					if (op.hasMarkedRanges()) {
						if (FileIoHelper.invokeRandomRangesUpdate(item, op, dstChannel)) {
							item.commitUpdatedRanges(op.markedRangesMaskPair());
							finishOperation((O) op);
						}
					} else {
						if (FileIoHelper.invokeOverwrite(item, op, dstChannel)) {
							finishOperation((O) op);
						}
					}
				} else {
					if (FileIoHelper.invokeFixedRangesUpdate(item, op, dstChannel, fixedRangesToUpdate)) {
						finishOperation((O) op);
					}
				}
				break;

			case DELETE:
				if (invokeDelete((O) op)) {
					finishOperation((O) op);
				}
				break;

			default:
				op.status(Operation.Status.FAIL_UNKNOWN);
				Loggers.ERR.fatal("Unknown load type \"{}\"", opType);
				break;
			}
		} catch (final FileNotFoundException e) {
			LogUtil.exception(Level.WARN, e, op.toString());
			op.status(Operation.Status.RESP_FAIL_NOT_FOUND);
		} catch (final AccessDeniedException e) {
			LogUtil.exception(Level.WARN, e, op.toString());
			op.status(Operation.Status.RESP_FAIL_AUTH);
		} catch (final ClosedChannelException e) {
			op.status(Operation.Status.INTERRUPTED);
		} catch (final IOException e) {
			LogUtil.exception(Level.WARN, e, op.toString());
			op.status(Operation.Status.FAIL_IO);
		} catch (final Throwable e) {
			if (!isClosed()) { // shared content source may be already closed from the load generator
				LogUtil.trace(Loggers.ERR, Level.ERROR, e, "File I/O invocation failure");
				op.status(Operation.Status.FAIL_UNKNOWN);
			} else {
				Loggers.ERR.debug("Load operation caused a failure while being interrupted: {}", op);
			}
		}

		if (!Operation.Status.ACTIVE.equals(op.status())) {

			if (srcChannel != null) {
				srcOpenFiles.remove(op);
				if (srcChannel.isOpen()) {
					try {
						srcChannel.close();
					} catch (final IOException e) {
						Loggers.ERR.warn("Failed to close the source file channel");
					}
				}
			}

			if (dstChannel != null) {
				dstOpenFiles.remove(op);
				if (dstChannel.isOpen()) {
					try {
						dstChannel.close();
					} catch (final IOException e) {
						Loggers.ERR.warn("Failed to close the destination file channel");
					}
				}
			}
		}
	}

	private boolean invokeDelete(final O op)
					throws IOException {
		final String dstPath = op.dstPath();
		final I fileItem = op.item();
		FsConstants.FS_PROVIDER.delete(
						dstPath == null ? Paths.get(fileItem.name()) : Paths.get(dstPath, fileItem.name()));
		return true;
	}

	@Override
	protected final void doClose()
					throws IOException {

		srcOpenFiles
						.values()
						.stream()
						.filter(AbstractInterruptibleChannel::isOpen)
						.forEach(
										channel -> {
											try {
												channel.close();
											} catch (final IOException e) {
												LogUtil.exception(Level.WARN, e, "Failed to close the source file channel {}", channel);
											}
										});
		srcOpenFiles.clear();

		dstOpenFiles
						.values()
						.stream()
						.filter(AbstractInterruptibleChannel::isOpen)
						.forEach(
										channel -> {
											try {
												channel.close();
											} catch (final IOException e) {
												LogUtil.exception(Level.WARN, e, "Failed to close the source file channel {}", channel);
											}
										});
		dstOpenFiles.clear();

		super.doClose();
	}

	@Override
	public final String toString() {
		return String.format(super.toString(), "fs");
	}
}
