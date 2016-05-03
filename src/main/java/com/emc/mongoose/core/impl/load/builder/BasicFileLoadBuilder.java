package com.emc.mongoose.core.impl.load.builder;
//
import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.conf.enums.LoadType;
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.common.log.LogUtil;
//
import com.emc.mongoose.core.api.item.container.Container;
import com.emc.mongoose.core.api.item.container.Directory;
import com.emc.mongoose.core.api.item.data.FileItem;
import com.emc.mongoose.core.api.io.conf.FileIoConfig;
import com.emc.mongoose.core.api.load.executor.FileLoadExecutor;
//
import com.emc.mongoose.core.impl.io.conf.BasicFileIoConfig;
import com.emc.mongoose.core.impl.item.data.CsvFileDataItemInput;
import com.emc.mongoose.core.impl.load.executor.BasicFileLoadExecutor;
import com.emc.mongoose.core.impl.load.executor.BasicMixedFileLoadExecutor;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
/**
 Created by kurila on 26.11.15.
 */
public class BasicFileLoadBuilder<T extends FileItem, U extends FileLoadExecutor<T>>
extends DataLoadBuilderBase<T, U> {
	//
	private final static Logger LOG = LogManager.getLogger();
	//
	public BasicFileLoadBuilder(final AppConfig appConfig)
	throws RemoteException {
		super(appConfig);
	}
	//
	@Override
	protected FileIoConfig<T, ? extends Directory<T>> getDefaultIoConfig() {
		return new BasicFileIoConfig<>();
	}
	//
	@Override
	public void invokePreConditions()
	throws IllegalStateException {
		// create parent directories
		final Container d = ioConfig.getDstContainer();
		final String parentDirectories = d == null ? null : d.getName();
		if(parentDirectories != null && !parentDirectories.isEmpty()) {
			try {
				Files.createDirectories(Paths.get(parentDirectories));
			} catch(final IOException e) {
				throw new IllegalStateException(
					"Failed to create target directories @ \"" + parentDirectories + "\""
				);
			}
		}
	}
	//
	@Override @SuppressWarnings("unchecked")
	protected U buildActually() {
		final LoadType loadType = ioConfig.getLoadType();
		if(LoadType.MIXED.equals(loadType)) {
			final Object inputFilesRaw = appConfig.getProperty(AppConfig.KEY_ITEM_SRC_FILE);
			final List<String> inputFiles;
			if(inputFilesRaw instanceof List) {
				inputFiles = (List<String>) inputFilesRaw;
			} else if(inputFilesRaw instanceof String){
				inputFiles = new ArrayList<>();
				inputFiles.add((String) inputFilesRaw);
			} else {
				throw new IllegalStateException(
					"Invalid configuration parameter type for " + AppConfig.KEY_ITEM_SRC_FILE +
						": \"" + inputFilesRaw + "\""
				);
			}
			final List<String> loadPatterns = (List<String>) appConfig
				.getProperty(AppConfig.KEY_LOAD_TYPE);
			final Map<LoadType, Input<T>> itemInputMap = new HashMap<>();
			final Map<LoadType, Integer> loadTypeWeightMap = LoadType
				.getMixedLoadWeights(loadPatterns);
			if(inputFiles.size()==1) {
				final Path singleInputPath = Paths.get(inputFiles.get(0));
				for(final LoadType nextLoadType : loadTypeWeightMap.keySet()) {
					try {
						itemInputMap.put(
							nextLoadType,
							LoadType.WRITE.equals(nextLoadType) ?
								getNewItemInput() :
								new CsvFileDataItemInput<>(
									singleInputPath, (Class<T>) ioConfig.getItemClass(),
									ioConfig.getContentSource()
								)
						);
					} catch(final NoSuchMethodException | IOException e) {
						LogUtil.exception(LOG, Level.ERROR, e, "Failed to build new item src");
					}
				}
			} else if(inputFiles.size() == loadPatterns.size()) {
				final Iterator<String> inputFilesIterator = inputFiles.iterator();
				String nextInputFile;
				for(final LoadType nextLoadType : loadTypeWeightMap.keySet()) {
					nextInputFile = inputFilesIterator.next();
					try {
						itemInputMap.put(
							nextLoadType,
							LoadType.WRITE.equals(nextLoadType) && nextInputFile == null ?
								getNewItemInput() :
								new CsvFileDataItemInput<>(
									Paths.get(nextInputFile), (Class<T>) ioConfig.getItemClass(),
									ioConfig.getContentSource()
								)
						);
					} catch(final NoSuchMethodException | IOException e) {
						LogUtil.exception(LOG, Level.ERROR, e, "Failed to build new item src");
					}
				}
			} else {
				throw new IllegalStateException(
					"Unable to map the list of " + inputFiles.size() + " input files to " +
						loadPatterns.size() + " load jobs"
				);
			}
			return (U) new BasicMixedFileLoadExecutor<>(
				appConfig, (FileIoConfig<T, ? extends Directory<T>>) ioConfig, threadCount,
				countLimit, sizeLimit, rateLimit, sizeConfig, rangesConfig,
				loadTypeWeightMap, itemInputMap
			);
		} else {
			return (U) new BasicFileLoadExecutor<>(
				appConfig, (FileIoConfig<T, ? extends Directory<T>>) ioConfig, threadCount,
				selectItemInput(), countLimit, sizeLimit, rateLimit, sizeConfig, rangesConfig
			);
		}
	}
}
