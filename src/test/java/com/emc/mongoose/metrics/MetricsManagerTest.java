package com.emc.mongoose.metrics;

import com.emc.mongoose.Constants;
import com.emc.mongoose.concurrent.ServiceTaskExecutor;
import com.emc.mongoose.item.op.OpType;
import com.emc.mongoose.metrics.context.DistributedMetricsContext;
import com.emc.mongoose.metrics.context.DistributedMetricsContextImpl;
import com.emc.mongoose.metrics.context.MetricsContext;
import com.emc.mongoose.metrics.context.MetricsContextImpl;
import com.emc.mongoose.params.ItemSize;
import com.github.akurilov.commons.system.SizeInBytes;
import io.prometheus.client.exporter.MetricsServlet;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 @author veronika K. on 15.10.18 */
public class MetricsManagerTest {

	private static final int PORT = 1111;
	private static final String CONTEXT = "/metrics";
	private static final int ITERATION_COUNT = 10;
	private static final Double ACCURACY = 0.0001;
	private static final int MARK_BYTES = 10;
	private static final int MARK_DUR = 11; //dur must be more than lat (dur > lat)
	private static final int MARK_LAT = 10;
	private final String STEP_ID = MetricsManagerTest.class.getSimpleName();
	private final OpType OP_TYPE = OpType.CREATE;
	private final IntSupplier nodeCountSupplier = () -> 1;
	private final int concurrencyLimit = 0;
	private final int concurrencyThreshold = 0;
	private final SizeInBytes ITEM_DATA_SIZE = ItemSize.SMALL.getValue();
	private final int UPDATE_INTERVAL_SEC = 1;
	private Supplier<List<MetricsSnapshot>> snapshotsSupplier;
	private final Server server = new Server(PORT);
	//
	private DistributedMetricsContext distributedMetricsContext;
	private MetricsContext metricsContext;

	@Before
	public void setUp()
	throws Exception {
		//
		final ServletContextHandler context = new ServletContextHandler();
		context.setContextPath("/");
		server.setHandler(context);
		context.addServlet(new ServletHolder(new MetricsServlet()), CONTEXT);
		server.start();
		//
		metricsContext = new MetricsContextImpl<>(STEP_ID, OP_TYPE, () -> 1, concurrencyLimit,
			concurrencyThreshold, ITEM_DATA_SIZE, UPDATE_INTERVAL_SEC, true
		);
		snapshotsSupplier = () -> Arrays.asList(metricsContext.lastSnapshot());
		metricsContext.start();
		//
		distributedMetricsContext = new DistributedMetricsContextImpl(STEP_ID, OP_TYPE,
			nodeCountSupplier,
			concurrencyLimit, concurrencyThreshold, ITEM_DATA_SIZE, UPDATE_INTERVAL_SEC, true, true,
			true, true, snapshotsSupplier
		);
		distributedMetricsContext.start();
		//
	}

	@Test
	public void test()
	throws InterruptedException {
		final MetricsManager metricsMgr = new MetricsManagerImpl(ServiceTaskExecutor.INSTANCE);
		metricsMgr.register(distributedMetricsContext);
		for(int i = 0; i < ITERATION_COUNT; ++ i) {
			metricsContext.markSucc(MARK_BYTES, MARK_DUR, MARK_LAT);
			metricsContext.markFail();
			metricsContext.refreshLastSnapshot();
			//TimeUnit.SECONDS.sleep(1);
		}
		final String result = resultFromServer("http://localhost:" + PORT + CONTEXT);
		System.out.println(result);
		//
		final String[] metrics = { "count", "sum", "mean", "min", "max" };
		final Map expectedValues = new HashMap();
		//duration
		final Double[] values = { new Double(ITERATION_COUNT), }
		for(final String key : metrics) {
			expectedValues.put(key, new Double(ITERATION_COUNT));
		}
		testMetric(result, Constants.METRIC_NAME_DUR, expectedValues);
		//
		((MetricsManagerImpl) metricsMgr).doClose();
	}

	private String resultFromServer(final String urlPath) {
		final StringBuffer stringBuffer = new StringBuffer();
		try {
			final URL url = new URL(urlPath);
			final URLConnection conn = url.openConnection();
			final BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
			br.lines().forEach(l -> stringBuffer.append(l).append("\n"));
			br.close();
		} catch(final MalformedURLException ex) {
			ex.printStackTrace();
		} catch(final IOException e) {
			e.printStackTrace();
		} finally {
			return stringBuffer.toString();
		}
	}

	private void testMetric(
		final String resultOutput, final String metricName, final Map<String, Double> expectedValues
	) {
		for(final String key : expectedValues.keySet()) {
			final Pattern p = Pattern.compile(metricName + "_" + key + "\\{.+\\} .+");
			final Matcher m = p.matcher(resultOutput);
			Assert.assertEquals(m.find(), true);
			final Double actualValue = Double.valueOf(m.group().split("}")[1]);
			final Double expectedValue = Double.valueOf(expectedValues.get(key));
			Assert.assertEquals(actualValue, expectedValue, expectedValue * ACCURACY);
		}
	}
}
