package com.emc.mongoose.storage.driver.builder;

import com.emc.mongoose.common.exception.UserShootHisFootException;
import com.emc.mongoose.model.data.ContentSource;
import com.emc.mongoose.model.io.task.IoTask;
import com.emc.mongoose.model.item.Item;
import com.emc.mongoose.model.item.ItemType;
import com.emc.mongoose.model.storage.StorageDriver;
import com.emc.mongoose.model.storage.StorageType;
import static com.emc.mongoose.ui.config.Config.ItemConfig;
import static com.emc.mongoose.ui.config.Config.LoadConfig;
import static com.emc.mongoose.ui.config.Config.SocketConfig;
import static com.emc.mongoose.ui.config.Config.StorageConfig;
import com.emc.mongoose.storage.driver.net.http.s3.S3StorageDriver;
import com.emc.mongoose.storage.driver.net.http.swift.SwiftStorageDriver;
import com.emc.mongoose.storage.driver.nio.fs.BasicFileStorageDriver;
import com.emc.mongoose.ui.log.Markers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 Created by andrey on 05.10.16.
 */
public class BasicStorageDriverBuilder<
	I extends Item, O extends IoTask<I>, T extends StorageDriver<I, O>
> implements StorageDriverBuilder<I, O, T> {

	private static final Logger LOG = LogManager.getLogger();

	private String jobName;
	private ContentSource contentSrc;
	private ItemConfig itemConfig;
	private LoadConfig loadConfig;
	private StorageConfig storageConfig;
	private SocketConfig socketConfig;

	@Override
	public ContentSource getContentSource() {
		return contentSrc;
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
	public SocketConfig getSocketConfig() {
		return socketConfig;
	}

	@Override
	public StorageDriverBuilder<I, O, T> setJobName(final String jobName) {
		this.jobName = jobName;
		return this;
	}
	
	@Override
	public StorageDriverBuilder<I, O, T> setContentSource(final ContentSource contentSrc) {
		this.contentSrc = contentSrc;
		return this;
	}
	
	@Override
	public StorageDriverBuilder<I, O, T> setItemConfig(final ItemConfig itemConfig) {
		this.itemConfig = itemConfig;
		return this;
	}
	
	@Override
	public StorageDriverBuilder<I, O, T> setLoadConfig(final LoadConfig loadConfig) {
		this.loadConfig = loadConfig;
		return this;
	}
	
	@Override
	public StorageDriverBuilder<I, O, T> setStorageConfig(final StorageConfig storageConfig) {
		this.storageConfig = storageConfig;
		return this;
	}
	
	@Override
	public StorageDriverBuilder<I, O, T> setSocketConfig(final SocketConfig socketConfig) {
		this.socketConfig = socketConfig;
		return this;
	}

	@Override @SuppressWarnings("unchecked")
	public T build()
	throws UserShootHisFootException {
		
		T driver = null;

		final ItemType itemType = ItemType.valueOf(itemConfig.getType().toUpperCase());
		final StorageType storageType = StorageType.valueOf(storageConfig.getType().toUpperCase());
		final boolean verifyFlag = itemConfig.getDataConfig().getVerify();
		
		if(StorageType.FS.equals(storageType)) {
			LOG.info(Markers.MSG, "Work on the filesystem");
			if(ItemType.PATH.equals(itemType)) {
				LOG.info(Markers.MSG, "Work on the directories");
				// TODO directory load driver
			} else {
				LOG.info(Markers.MSG, "Work on the files");
				driver = (T) new BasicFileStorageDriver<>(jobName, loadConfig, verifyFlag);
			}
		} else if(StorageType.HTTP.equals(storageType)){
			final String apiType = storageConfig.getHttpConfig().getApi();
			LOG.info(Markers.MSG, "Work via HTTP using \"{}\" cloud storage API", apiType);
			if(ItemType.PATH.equals(itemType)) {
				// TODO container/bucket load driver
			} else {
				switch(apiType.toLowerCase()) {
					case API_S3:
						driver = (T) new S3StorageDriver<>(
							jobName, loadConfig, storageConfig, verifyFlag, socketConfig
						);
						break;
					case API_SWIFT:
						driver = (T) new SwiftStorageDriver<>(
							jobName, loadConfig, storageConfig, verifyFlag, socketConfig
						);
						break;
					default:
						throw new IllegalArgumentException("Unknown HTTP storage API: " + apiType);
				}
			}
		} else {
			throw new UserShootHisFootException("Unsupported storage type");
		}

		return driver;
	}
}
