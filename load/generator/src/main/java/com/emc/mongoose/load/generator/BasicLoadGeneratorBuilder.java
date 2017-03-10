package com.emc.mongoose.load.generator;

import com.emc.mongoose.common.api.ByteRange;
import com.emc.mongoose.common.api.SizeInBytes;
import com.emc.mongoose.common.exception.UserShootHisFootException;
import com.emc.mongoose.common.supply.BatchSupplier;
import com.emc.mongoose.common.supply.ConstantStringSupplier;
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.common.supply.RangePatternDefinedSupplier;
import com.emc.mongoose.model.io.task.IoTask;
import com.emc.mongoose.model.io.task.IoTaskBuilder;
import com.emc.mongoose.model.io.task.data.BasicDataIoTaskBuilder;
import com.emc.mongoose.model.io.task.path.BasicPathIoTaskBuilder;
import com.emc.mongoose.model.io.task.token.BasicTokenIoTaskBuilder;
import com.emc.mongoose.model.item.BasicDataItemFactory;
import com.emc.mongoose.model.item.ItemNameSupplier;
import com.emc.mongoose.model.item.CsvFileItemInput;
import com.emc.mongoose.model.item.DataItem;
import com.emc.mongoose.model.item.IoResultsItemInput;
import com.emc.mongoose.model.item.Item;
import com.emc.mongoose.model.item.ItemFactory;
import com.emc.mongoose.model.item.ItemNamingType;
import com.emc.mongoose.model.item.ItemType;
import com.emc.mongoose.model.item.NewDataItemInput;
import com.emc.mongoose.model.io.IoType;

import static com.emc.mongoose.common.supply.PatternDefinedSupplier.PATTERN_CHAR;
import static com.emc.mongoose.model.storage.StorageDriver.BUFF_SIZE_MIN;
import static com.emc.mongoose.ui.config.Config.ItemConfig.InputConfig;
import static com.emc.mongoose.ui.config.Config.ItemConfig.NamingConfig;
import static com.emc.mongoose.ui.config.Config.LoadConfig;
import static com.emc.mongoose.ui.config.Config.ItemConfig;
import static com.emc.mongoose.ui.config.Config.TestConfig.StepConfig.LimitConfig;
import com.emc.mongoose.model.item.NewItemInput;
import com.emc.mongoose.model.storage.StorageDriver;
import static com.emc.mongoose.ui.config.Config.ItemConfig.DataConfig.RangesConfig;
import static com.emc.mongoose.ui.config.Config.StorageConfig.AuthConfig;
import com.emc.mongoose.ui.log.LogUtil;
import com.emc.mongoose.ui.log.Markers;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 Created by andrey on 12.11.16.
 */
public class BasicLoadGeneratorBuilder<
	I extends Item, O extends IoTask<I>, T extends BasicLoadGenerator<I, O>
>
implements LoadGeneratorBuilder<I, O, T> {

	private final static Logger LOG = LogManager.getLogger();

	private ItemConfig itemConfig;
	private LoadConfig loadConfig;
	private LimitConfig limitConfig;
	private ItemType itemType;
	private ItemFactory<I> itemFactory;
	private AuthConfig authConfig;
	private List<StorageDriver<I, O>> storageDrivers;
	private Input<I> itemInput = null;
	private SizeInBytes itemSizeEstimate = null;
	
	@Override
	public BasicLoadGeneratorBuilder<I, O, T> setItemConfig(final ItemConfig itemConfig) {
		this.itemConfig = itemConfig;
		return this;
	}

	@Override
	public BasicLoadGeneratorBuilder<I, O, T> setLoadConfig(final LoadConfig loadConfig) {
		this.loadConfig = loadConfig;
		return this;
	}

	@Override
	public BasicLoadGeneratorBuilder<I, O, T> setLimitConfig(final LimitConfig limitConfig) {
		this.limitConfig = limitConfig;
		return this;
	}

	@Override
	public BasicLoadGeneratorBuilder<I, O, T> setItemType(final ItemType itemType) {
		this.itemType = itemType;
		return this;
	}

	@Override
	public BasicLoadGeneratorBuilder<I, O, T> setItemFactory(final ItemFactory<I> itemFactory) {
		this.itemFactory = itemFactory;
		return this;
	}
	
	@Override
	public BasicLoadGeneratorBuilder<I, O, T> setAuthConfig(final AuthConfig authConfig) {
		this.authConfig = authConfig;
		return this;
	}
	
	@Override
	public BasicLoadGeneratorBuilder<I, O, T> setStorageDrivers(
		final List<StorageDriver<I, O>> storageDrivers
	) {
		this.storageDrivers = storageDrivers;
		return this;
	}
	
	@Override @SuppressWarnings("unchecked")
	public BasicLoadGeneratorBuilder<I, O, T> setItemInput(final Input<I> itemInput) {
		this.itemInput = itemInput;
		if(!(itemInput instanceof IoResultsItemInput)) {
			this.itemSizeEstimate = estimateDataItemSize((Input<DataItem>) itemInput);
		}
		return this;
	}
	
	@SuppressWarnings("unchecked")
	public T build()
	throws UserShootHisFootException {

		final IoType ioType = IoType.valueOf(loadConfig.getType().toUpperCase());
		final IoTaskBuilder<I, O> ioTaskBuilder;
		final long countLimit = limitConfig.getCount();
		final SizeInBytes sizeLimit = limitConfig.getSize();
		final boolean shuffleFlag = loadConfig.getGeneratorConfig().getShuffle();

		final InputConfig inputConfig = itemConfig.getInputConfig();
		
		final BatchSupplier<String> outputPathSupplier;
		if(IoType.CREATE.equals(ioType) && ItemType.DATA.equals(itemType)) {
			outputPathSupplier = getOutputPathSupplier();
		} else {
			outputPathSupplier = null;
		}
		
		if(ItemType.DATA.equals(itemType)) {
			final RangesConfig rangesConfig = itemConfig.getDataConfig().getRangesConfig();
			final List<String> fixedRangesConfig = rangesConfig.getFixed();
			final List<ByteRange> fixedRanges = new ArrayList<>();
			if(fixedRangesConfig != null) {
				for(final String fixedRangeConfig : fixedRangesConfig) {
					fixedRanges.add(new ByteRange(fixedRangeConfig));
				}
			}
			ioTaskBuilder = (IoTaskBuilder<I, O>) new BasicDataIoTaskBuilder()
				.setFixedRanges(fixedRanges)
				.setRandomRangesCount(rangesConfig.getRandom())
				.setSizeThreshold(rangesConfig.getThreshold().get());
		} else if(ItemType.PATH.equals(itemType)){
			ioTaskBuilder = (IoTaskBuilder<I, O>) new BasicPathIoTaskBuilder();
		} else {
			ioTaskBuilder = (IoTaskBuilder<I, O>) new BasicTokenIoTaskBuilder();
		}
		
		String itemInputPath = inputConfig.getPath();
		if(itemInputPath != null && !itemInputPath.startsWith("/")) {
			itemInputPath = "/" + itemInputPath;
		}
		
		final BatchSupplier<String> uidSupplier, secretSupplier;
		final String authFile = authConfig.getFile();
		if(authFile != null && !authFile.isEmpty()) {
			final Map<String, String> credentials = loadCredentials(authFile);
			ioTaskBuilder.setCredentialsMap(credentials);
		} else {
			
			final String uid = authConfig.getUid();
			if(uid == null) {
				uidSupplier = null;
			} else if(-1 != uid.indexOf(PATTERN_CHAR)) {
				uidSupplier = new RangePatternDefinedSupplier(uid);
			} else {
				uidSupplier = new ConstantStringSupplier(uid);
			}
			
			final String secret = authConfig.getSecret();
			if(secret == null) {
				secretSupplier = null;
			} else {
				secretSupplier = new ConstantStringSupplier(secret);
			}
			
			ioTaskBuilder
				.setUidSupplier(uidSupplier)
				.setSecretSupplier(secretSupplier);
		}
		
		ioTaskBuilder
			.setIoType(IoType.valueOf(loadConfig.getType().toUpperCase()))
			.setInputPath(itemInputPath)
			.setOutputPathSupplier(outputPathSupplier);

		// prevent the storage connections if noop
		// also don't create tocken if token load is configured
		if(!IoType.NOOP.equals(ioType) && !ItemType.TOKEN.equals(itemType)) {
			String authToken = null;
			try {
				for(final StorageDriver<I, O> nextDriver : storageDrivers) {
					if(authToken == null) {
						authToken = nextDriver.getAuthToken();
					} else {
						// distribute the auth token among the storage drivers
						nextDriver.setAuthToken(authToken);
					}
				}
			} catch(final RemoteException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to communicate with remote storage driver");
			}
		}

		final String itemInputFile = inputConfig.getFile();
		if(itemInput == null) {
			itemInput = getItemInput(ioType, itemInputFile, itemInputPath);
			if(ItemType.DATA.equals(itemType)) {
				itemSizeEstimate = estimateDataItemSize((Input<DataItem>) itemInput);
			} else {
				itemSizeEstimate = new SizeInBytes(BUFF_SIZE_MIN);
			}
		}

		if(itemSizeEstimate != null && ItemType.DATA.equals(itemType)) {
			try {
				for(final StorageDriver<I, O> storageDriver : storageDrivers) {
					try {
						storageDriver.adjustIoBuffers(itemSizeEstimate, ioType);
					} catch(final RemoteException e) {
						LogUtil.exception(
							LOG, Level.WARN, e,
							"Failed to adjust the storage driver buffer sizes"
						);
					}
				}
			} catch(final Exception e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to estimate the average data item size"
				);
			} finally {
				try {
					itemInput.reset();
				} catch(final IOException e) {
					LogUtil.exception(LOG, Level.WARN, e, "Failed to reset the item input");
				}
			}
		}

		return (T) new BasicLoadGenerator<>(
			itemInput, itemSizeEstimate, ioTaskBuilder, countLimit, sizeLimit,
			shuffleFlag
		);
	}
	
	private static SizeInBytes estimateDataItemSize(final Input<DataItem> itemInput) {
		final int maxCount = 0x100;
		final List<DataItem> items = new ArrayList<>(maxCount);
		int n = 0;
		try {
			while(n < maxCount) {
				n += itemInput.get(items, maxCount - n);
			}
		} catch(final EOFException ignored) {
		} catch(final IOException e) {
			LogUtil.exception(LOG, Level.WARN, e, "Failed to estimate the average data item size");
		}
		
		long sumSize = 0;
		long minSize = Long.MAX_VALUE;
		long maxSize = Long.MIN_VALUE;
		long nextSize;
		if(n > 0) {
			try {
				for(int i = 0; i < n; i++) {
					nextSize = items.get(i).size();
					sumSize += nextSize;
					if(nextSize < minSize) {
						minSize = nextSize;
					}
					if(nextSize > maxSize) {
						maxSize = nextSize;
					}
				}
			} catch(final IOException e) {
				throw new AssertionError(e);
			}
			return minSize == maxSize ?
				new SizeInBytes(sumSize / n) : new SizeInBytes(minSize, maxSize, 1);
		}
		return null;
	}

	private BatchSupplier<String> getOutputPathSupplier()
	throws UserShootHisFootException {
		final BatchSupplier<String> dstPathInput;
		final String t = itemConfig.getOutputConfig().getPath();
		if(t == null || t.isEmpty()) {
			final String dstPath = "/" + LogUtil.getDateTimeStamp();
			dstPathInput = new ConstantStringSupplier(dstPath);
			try {
				storageDrivers.get(0).createPath(dstPath);
			} catch(final IOException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to create the items output path \"{}\"",
					dstPath
				);
			}
		} else {
			dstPathInput = new RangePatternDefinedSupplier(t.startsWith("/") ? t : "/" + t);
			String dstPath = null;
			try {
				dstPath = dstPathInput.get();
				dstPathInput.reset();
				if(dstPath != null) {
					final int sepPos = dstPath.indexOf('/', 1);
					if(sepPos > 1) {
						// create only 1st level path
						dstPath = dstPath.substring(0, sepPos);
					}
					storageDrivers.get(0).createPath(dstPath);
				}
			} catch(final IOException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to create the items output path \"{}\"",
					dstPath
				);
			}
		}
		return dstPathInput;
	}

	@SuppressWarnings("unchecked")
	private Input<I> getItemInput(
		final IoType ioType, final String itemInputFile, final String itemInputPath
	) throws UserShootHisFootException {
		
		if(itemInputFile == null || itemInputFile.isEmpty()) {

			final NamingConfig namingConfig = itemConfig.getNamingConfig();
			final ItemNamingType namingType = ItemNamingType.valueOf(
				namingConfig.getType().toUpperCase()
			);
			final String namingPrefix = namingConfig.getPrefix();
			final int namingLength = namingConfig.getLength();
			final int namingRadix = namingConfig.getRadix();
			final long namingOffset = namingConfig.getOffset();

			if(itemInputPath == null || itemInputPath.isEmpty()) {
				if(IoType.CREATE.equals(ioType) || IoType.NOOP.equals(ioType)) {
					final ItemNameSupplier itemNameInput = new ItemNameSupplier(
						namingType, namingPrefix, namingLength, namingRadix, namingOffset
					);
					if(itemFactory instanceof BasicDataItemFactory) {
						final SizeInBytes size = itemConfig.getDataConfig().getSize();
						itemInput = (Input<I>) new NewDataItemInput(
							itemFactory, itemNameInput, size
						);
					} else {
						itemInput = new NewItemInput<>(itemFactory, itemNameInput);
					}
				} else {
					throw new UserShootHisFootException(
						"No input (file either path) is specified for non-create generator"
					);
				}
			} else {
				itemInput = new StorageItemInput<>(
					storageDrivers.get(0), itemFactory, itemInputPath, namingPrefix, namingRadix
				);
			}
		} else {
			try {
				itemInput = new CsvFileItemInput<>(Paths.get(itemInputFile), itemFactory);
			} catch(final NoSuchMethodException e) {
				throw new RuntimeException(e);
			} catch(final IOException e) {
				LogUtil.exception(
					LOG, Level.WARN, e, "Failed to use the item input file \"{}\"", itemInputFile
				);
			}
		}

		return itemInput;
	}
	
	private static Map<String, String> loadCredentials(final String file)
	throws UserShootHisFootException {
		final Map<String, String> credentials = new HashMap<>();
		try(final BufferedReader br = Files.newBufferedReader(Paths.get(file))) {
			String line;
			String parts[];
			int firstCommaPos;
			while(null != (line = br.readLine())) {
				firstCommaPos = line.indexOf(',');
				if(-1 == firstCommaPos) {
					LOG.warn(Markers.ERR, "Invalid credentials line: \"{}\"", line);
				} else {
					parts = line.split(",", 2);
					credentials.put(parts[0], parts[1]);
				}
			}
			LOG.info(
				Markers.MSG, "Loaded {} credential pairs from the file \"{}\"", credentials.size(),
				file
			);
		} catch(final IOException e) {
			LogUtil.exception(
				LOG, Level.WARN, e, "Failed to load the credenitals from the file \"{}\"", file
			);
		}
		return credentials;
	}
}
