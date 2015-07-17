package com.emc.mongoose.integ.distributed.single;
//
import com.emc.mongoose.common.conf.SizeUtil;
//
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.net.ServiceUtils;
import com.emc.mongoose.core.api.data.WSObject;
//
import com.emc.mongoose.util.client.api.StorageClient;
import com.emc.mongoose.util.client.api.StorageClientBuilder;
import com.emc.mongoose.util.client.impl.BasicWSClientBuilder;
//
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import org.junit.Assert;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.TimeUnit;
/**
 Created by kurila on 14.07.15.
 */
public final class WriteByCountTest {
	//
	private final static long COUNT_TO_WRITE = 100000;
	//
	private static StorageClient<WSObject> CLIENT;
	private static long COUNT_WRITTEN;
	private static Logger LOG;
	//
	@BeforeClass
	public static void setUpClass()
	throws Exception {
		final StorageClientBuilder<WSObject, StorageClient<WSObject>>
			clientBuilder = new BasicWSClientBuilder<>();
		CLIENT = clientBuilder
			.setLimitTime(0, TimeUnit.SECONDS)
			.setLimitCount(COUNT_TO_WRITE)
			.setClientMode(new String[] {ServiceUtils.getHostAddr()})
			.build();
		COUNT_WRITTEN = CLIENT.write(null, null, (short) 10, SizeUtil.toSize("10KB"));
		LOG = LogManager.getLogger();
		LOG.info(Markers.MSG, "Written {} items", COUNT_WRITTEN);
	}
	//
	@AfterClass
	public static void tearDownClass()
	throws Exception {
		CLIENT.close();
	}
	//
	@Test
	public void checkReturnedCount() {
		Assert.assertEquals(COUNT_WRITTEN, COUNT_TO_WRITE);
	}
}
