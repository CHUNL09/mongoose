package com.emc.mongoose.monitor;

import com.emc.mongoose.common.config.LoadType;
import com.emc.mongoose.common.io.BasicDataIoTask;
import com.emc.mongoose.common.io.DataIoTask;
import com.emc.mongoose.common.item.BasicDataItem;
import com.emc.mongoose.common.item.DataItem;
import com.emc.mongoose.common.load.Driver;
import com.emc.mongoose.common.load.Generator;
import com.emc.mongoose.common.load.Monitor;
import com.emc.mongoose.common.log.LogUtil;
import com.emc.mongoose.generator.GeneratorMock;
import com.emc.mongoose.storage.driver.DriverMock;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 Created by kurila on 11.07.16.
 */
public class Main {

	static {
		LogUtil.init();
	}

	public static <I extends DataItem, O extends DataIoTask<I>> void main(final String... args)
	throws IOException {

		final int generatorCount = Runtime.getRuntime().availableProcessors();
		final List<Generator<I, O>> generators = new ArrayList<>(generatorCount);
		for(int i = 0; i < generatorCount; i ++) {
			final List<Driver<I, O>> drivers = new ArrayList<>(2);
			drivers.add(new DriverMock<>());
			drivers.add(new DriverMock<>());
			generators.add(
				new GeneratorMock<>(
					drivers, LoadType.CREATE, (Class<I>) BasicDataItem.class,
					(Class) BasicDataIoTask.class
				)
			);
		}

		try(final Monitor<I, O> monitor = new MonitorMock<>(generators)) {
			monitor.start();
			monitor.await();
		} catch(final Throwable e) {
			e.printStackTrace(System.out);
		}

		for(final Generator<I, O> generator : generators) {
			generator.close();
		}
		generators.clear();
	}
}
