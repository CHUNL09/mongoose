package com.emc.mongoose.server.impl.load.builder;
//mongoose-common.jar
import com.emc.mongoose.common.conf.AppConfig;
import com.emc.mongoose.common.conf.Constants;
import com.emc.mongoose.common.conf.enums.LoadType;
import com.emc.mongoose.common.exceptions.DuplicateSvcNameException;
import com.emc.mongoose.common.io.Input;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.net.ServiceUtil;
//mongoose-core-api.jar
import com.emc.mongoose.core.api.item.data.HttpDataItem;
import com.emc.mongoose.core.api.load.executor.LoadExecutor;
import com.emc.mongoose.core.api.io.conf.HttpRequestConfig;
//mongoose-server-api.jar
import com.emc.mongoose.core.impl.item.data.CsvFileDataItemInput;
import com.emc.mongoose.server.api.load.executor.HttpDataLoadSvc;
import com.emc.mongoose.server.api.load.builder.HttpDataLoadBuilderSvc;
// mongoose-core-impl.jar
import com.emc.mongoose.core.impl.load.builder.BasicHttpDataLoadBuilder;
// mongoose-server-impl.jar
import com.emc.mongoose.server.impl.load.executor.BasicHttpDataLoadSvc;
//
import com.emc.mongoose.server.impl.load.executor.BasicMixedHttpDataLoadSvc;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.rmi.RemoteException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
/**
 Created by kurila on 30.05.14.
 */
public class BasicHttpDataLoadBuilderSvc<T extends HttpDataItem, U extends HttpDataLoadSvc<T>>
extends BasicHttpDataLoadBuilder<T, U>
implements HttpDataLoadBuilderSvc<T, U> {
	//
	private final static Logger LOG = LogManager.getLogger();
	private final static AtomicInteger FORK_COUNTER = new AtomicInteger(0);
	//
	private String name = getClass().getName();
	//
	public BasicHttpDataLoadBuilderSvc(final AppConfig appConfig)
	throws RemoteException {
		super(appConfig);
	}
	//
	@Override
	public final int fork()
	throws RemoteException {
		try {
			final BasicHttpDataLoadBuilderSvc<T, U>
				forkedSvc = (BasicHttpDataLoadBuilderSvc<T, U>) clone();
			final int forkNum = FORK_COUNTER.getAndIncrement();
			forkedSvc.name = name + forkNum;
			forkedSvc.start();
			LOG.info(Markers.MSG, "Service \"" + name + "\" started");
			return forkNum;
		} catch(final CloneNotSupportedException e) {
			throw new RemoteException(e.toString());
		}
	}
	//
	@Override
	public String buildRemotely()
	throws RemoteException {
		U loadSvc = build();
		LOG.info(Markers.MSG, appConfig.toString());
		ServiceUtil.create(loadSvc);
		return loadSvc.getName();
	}
	//
	@Override
	public final String getName() {
		return name;
	}
	//
	@Override
	public final int getNextInstanceNum(final String runId) {
		return LoadExecutor.NEXT_INSTANCE_NUM.get();
	}
	//
	@Override
	public final void setNextInstanceNum(final String runId, final int instanceN) {
		LoadExecutor.NEXT_INSTANCE_NUM.set(instanceN);
	}
	//
	@Override
	public final void invokePreConditions() {} // discard any precondition invocations in load server mode
	//
	@Override @SuppressWarnings("unchecked")
	protected final U buildActually()
	throws IllegalStateException {
		if(ioConfig == null) {
			throw new IllegalStateException("Should specify request builder instance before instancing");
		}
		//
		final LoadType loadType = ioConfig.getLoadType();
		final HttpRequestConfig httpReqConf = (HttpRequestConfig) ioConfig;
		// the statement below fixes hi-level API distributed mode usage and tests
		appConfig.setProperty(AppConfig.KEY_RUN_MODE, Constants.RUN_MODE_SERVER);
		//
		if(LoadType.MIXED.equals(loadType)) {
			final List<String> inputFiles = (List<String>) appConfig
				.getProperty(AppConfig.KEY_ITEM_SRC_FILE);
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
			return (U) new BasicMixedHttpDataLoadSvc<>(
				appConfig, httpReqConf, storageNodeAddrs, threadCount, countLimit, sizeLimit,
				rateLimit, sizeConfig, rangesConfig, loadTypeWeightMap, itemInputMap
			);
		} else {
			return (U) new BasicHttpDataLoadSvc<>(
				appConfig, httpReqConf, storageNodeAddrs, threadCount,
				itemInput == null ? getDefaultItemInput() : itemInput, countLimit, sizeLimit,
				rateLimit, sizeConfig, rangesConfig
			);
		}
	}
	//
	public final void start()
	throws RemoteException {
		LOG.debug(Markers.MSG, "Load builder service instance created");
		try {
		/*final RemoteStub stub = */
		ServiceUtil.create(this);
		/*LOG.debug(Markers.MSG, stub.toString());*/
		} catch (final DuplicateSvcNameException e) {
			LogUtil.exception(LOG, Level.ERROR, e, "Possible load service usage collision");
		}
		LOG.info(Markers.MSG, "Server started and waiting for the requests");
	}
	//
	@Override
	public void shutdown()
	throws RemoteException, IllegalStateException {
	}
	//
	@Override
	public void await()
	throws RemoteException, InterruptedException {
		await(Long.MAX_VALUE, TimeUnit.DAYS);
	}
	//
	@Override
	public void await(final long timeOut, final TimeUnit timeUnit)
	throws RemoteException, InterruptedException {
		timeUnit.sleep(timeOut);
	}
	//
	@Override
	public void interrupt()
	throws RemoteException {
	}
	//
	@Override
	public final void close()
	throws IOException {
		try {
			super.close();
		} finally {
			ServiceUtil.close(this);
		}
		LOG.info(Markers.MSG, "Service \"{}\" closed", name);
	}
	//
	@Override
	protected boolean itemsFileExists(final String filePathStr) {
		if(filePathStr != null && !filePathStr.isEmpty()) {
			final Path listFilePath = Paths.get(filePathStr);
			if(!Files.exists(listFilePath)) {
				LOG.debug(Markers.MSG, "Specified input file \"{}\" doesn't exists", listFilePath);
			} else if(!Files.isReadable(listFilePath)) {
				LOG.debug(Markers.MSG, "Specified input file \"{}\" isn't readable", listFilePath);
			} else if(Files.isDirectory(listFilePath)) {
				LOG.debug(Markers.MSG, "Specified input file \"{}\" is a directory", listFilePath);
			} else {
				return true;
			}
		}
		return false;
	}
}
