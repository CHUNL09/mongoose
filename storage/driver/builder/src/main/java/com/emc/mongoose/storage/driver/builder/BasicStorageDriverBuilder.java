package com.emc.mongoose.storage.driver.builder;

import com.emc.mongoose.common.Constants;
import com.emc.mongoose.common.exception.UserShootHisFootException;
import com.emc.mongoose.model.data.ContentSource;
import com.emc.mongoose.model.data.ContentSourceUtil;
import com.emc.mongoose.model.io.task.IoTask;
import com.emc.mongoose.model.item.Item;
import com.emc.mongoose.model.item.ItemType;
import com.emc.mongoose.model.storage.StorageDriver;
import com.emc.mongoose.model.storage.StorageType;
import static com.emc.mongoose.ui.config.Config.ItemConfig;
import static com.emc.mongoose.ui.config.Config.LoadConfig;
import static com.emc.mongoose.ui.config.Config.StorageConfig;
import com.emc.mongoose.storage.driver.net.http.atmos.AtmosStorageDriver;
import com.emc.mongoose.storage.driver.net.http.s3.S3StorageDriver;
import com.emc.mongoose.storage.driver.net.http.swift.SwiftStorageDriver;
import com.emc.mongoose.storage.driver.nio.fs.BasicFileStorageDriver;
import com.emc.mongoose.ui.config.Config.ItemConfig.DataConfig.ContentConfig;
import com.emc.mongoose.ui.log.Markers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;

import java.io.IOException;

/**
 Created by andrey on 05.10.16.
 */
public class BasicStorageDriverBuilder<
	I extends Item, O extends IoTask<I>, T extends StorageDriver<I, O>
> implements StorageDriverBuilder<I, O, T> {

	private static final Logger LOG = LogManager.getLogger();

	private String jobName;
	private ItemConfig itemConfig;
	private LoadConfig loadConfig;
	private StorageConfig storageConfig;

	protected final ContentSource getContentSource()
	throws IOException {
		final ContentConfig contentConfig = itemConfig.getDataConfig().getContentConfig();
		return ContentSourceUtil.getInstance(
			contentConfig.getFile(), contentConfig.getSeed(), contentConfig.getRingSize()
		);
	}

	@Override
	public ItemConfig getItemConfig() {
		return itemConfig;
	}

	@Override
	public LoadConfig getLoadConfig() {
		return loadConfig;
	}

	@Override
	public StorageConfig getStorageConfig() {
		return storageConfig;
	}

	@Override
	public BasicStorageDriverBuilder<I, O, T> setJobName(final String jobName) {
		this.jobName = jobName;
		return this;
	}
	
	@Override
	public BasicStorageDriverBuilder<I, O, T> setItemConfig(final ItemConfig itemConfig) {
		this.itemConfig = itemConfig;
		return this;
	}
	
	@Override
	public BasicStorageDriverBuilder<I, O, T> setLoadConfig(final LoadConfig loadConfig) {
		this.loadConfig = loadConfig;
		return this;
	}
	
	@Override
	public BasicStorageDriverBuilder<I, O, T> setStorageConfig(final StorageConfig storageConfig) {
		this.storageConfig = storageConfig;
		return this;
	}
	
	@Override @SuppressWarnings("unchecked")
	public T build()
	throws UserShootHisFootException {

		ThreadContext.put(Constants.KEY_JOB_NAME, jobName);

		T driver = null;

		final ItemType itemType = ItemType.valueOf(itemConfig.getType().toUpperCase());
		final StorageType storageType = StorageType.valueOf(storageConfig.getType().toUpperCase());
		final boolean verifyFlag = itemConfig.getDataConfig().getVerify();
		
		if(StorageType.FS.equals(storageType)) {
			LOG.info(Markers.MSG, "Work on the filesystem");
			if(ItemType.PATH.equals(itemType)) {
				LOG.info(Markers.MSG, "Work on the directories");
				throw new AssertionError("Not implemented yet");
			} else {
				LOG.info(Markers.MSG, "Work on the files");
				driver = (T) new BasicFileStorageDriver<>(jobName, loadConfig, verifyFlag);
			}
		} else {
			if(StorageType.HTTP.equals(storageType)){
				final String apiType = storageConfig.getNetConfig().getHttpConfig().getApi();
				LOG.info(Markers.MSG, "Work via HTTP using \"{}\" cloud storage API", apiType);
				switch(apiType.toLowerCase()) {
					case API_ATMOS:
						driver = (T) new AtmosStorageDriver<>(
							jobName, loadConfig, storageConfig, verifyFlag
						);
						break;
					case API_S3:
						driver = (T) new S3StorageDriver<>(
							jobName, loadConfig, storageConfig, verifyFlag
						);
						break;
					case API_SWIFT:
						driver = (T) new SwiftStorageDriver<>(
							jobName, loadConfig, storageConfig, verifyFlag
						);
						break;
					default:
						throw new IllegalArgumentException("Unknown HTTP storage API: " + apiType);
				}
			} else {
				throw new UserShootHisFootException("Unsupported storage type");
			}
		}

		return driver;
	}
}
