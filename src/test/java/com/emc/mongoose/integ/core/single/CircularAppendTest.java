package com.emc.mongoose.integ.core.single;

import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.conf.SizeUtil;
import com.emc.mongoose.common.log.appenders.RunIdFileManager;
import com.emc.mongoose.core.api.data.WSObject;
import com.emc.mongoose.core.api.data.model.ItemDst;
import com.emc.mongoose.core.api.io.task.IOTask;
import com.emc.mongoose.core.impl.data.BasicWSObject;
import com.emc.mongoose.core.impl.data.content.ContentSourceBase;
import com.emc.mongoose.core.impl.data.model.CSVFileItemDst;
import com.emc.mongoose.integ.base.StandaloneClientTestBase;
import com.emc.mongoose.integ.suite.StdOutInterceptorTestSuite;
import com.emc.mongoose.integ.tools.BufferingOutputStream;
import com.emc.mongoose.util.client.api.StorageClient;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;

import static com.emc.mongoose.integ.tools.LogPatterns.CONSOLE_METRICS_AVG;
import static com.emc.mongoose.integ.tools.LogPatterns.CONSOLE_METRICS_SUM;

/**
 * Created by gusakk on 03.11.15.
 */
public class CircularAppendTest
extends StandaloneClientTestBase {
	//
	private static final int ITEM_MAX_QUEUE_SIZE = 65536;
	private static final int BATCH_SIZE = 100;
	//
	private static final int WRITE_COUNT = 1234;
	private static final int APPEND_COUNT = 12340;
	//
	private static final int COUNT_OF_APPEND = 10;
	private static final int MIN_SIZE_AFTER_APPEND = 1280;
	//
	private static long COUNT_WRITTEN, COUNT_APPENDED;
	private static byte[] STD_OUT_CONTENT;

	//
	@BeforeClass
	public static void setUpClass()
	throws Exception {
		System.setProperty(
			RunTimeConfig.KEY_RUN_ID, CircularAppendTest.class.getCanonicalName()
		);
		StandaloneClientTestBase.setUpClass();
		//
		final RunTimeConfig rtConfig = RunTimeConfig.getContext();
		rtConfig.set(RunTimeConfig.KEY_ITEM_SRC_CIRCULAR, true);
		rtConfig.set(RunTimeConfig.KEY_ITEM_QUEUE_MAX_SIZE, ITEM_MAX_QUEUE_SIZE);
		rtConfig.set(RunTimeConfig.KEY_ITEM_SRC_BATCH_SIZE, BATCH_SIZE);
		RunTimeConfig.setContext(rtConfig);
		//
		try (
			final StorageClient<WSObject> client = CLIENT_BUILDER
				.setAPI("s3")
				.setLimitTime(0, TimeUnit.SECONDS)
				.setLimitCount(WRITE_COUNT)
				.build()
		) {
			final ItemDst<WSObject> writeOutput = new CSVFileItemDst<WSObject>(
				BasicWSObject.class, ContentSourceBase.getDefault()
			);
			COUNT_WRITTEN = client.write(
				null, writeOutput, WRITE_COUNT, 1, SizeUtil.toSize("128B")
			);
			TimeUnit.SECONDS.sleep(1);
			//
			try (
				final BufferingOutputStream
					stdOutInterceptorStream = StdOutInterceptorTestSuite.getStdOutBufferingStream()
			) {
				stdOutInterceptorStream.reset();
				if (COUNT_WRITTEN > 0) {
					COUNT_APPENDED = client.append(writeOutput.getItemSrc(), null, APPEND_COUNT, 10,
						SizeUtil.toSize("128B"));
				} else {
					throw new IllegalStateException("Failed to append");
				}
				TimeUnit.SECONDS.sleep(1);
				STD_OUT_CONTENT = stdOutInterceptorStream.toByteArray();
			}
		}
		//
		RunIdFileManager.flushAll();
	}
	//
	@AfterClass
	public static void tearDownClass()
	throws Exception {
		StdOutInterceptorTestSuite.reset();
		StandaloneClientTestBase.tearDownClass();
	}
	//
	@Test
	public void checkAppendedCount()
	throws Exception {
		Assert.assertEquals(COUNT_WRITTEN * COUNT_OF_APPEND, COUNT_APPENDED);
	}
	//
	@Test
	public void checkDataItemsSize()
	throws Exception {
		try (
			final BufferedReader
				in = Files.newBufferedReader(FILE_LOG_DATA_ITEMS.toPath(), StandardCharsets.UTF_8)
		) {
			final Iterable<CSVRecord> recIter = CSVFormat.RFC4180.parse(in);
			for (final CSVRecord nextRec : recIter) {
				Assert.assertTrue(Integer.parseInt(nextRec.get(2)) >= MIN_SIZE_AFTER_APPEND);
			}
		}
	}
	//
	@Test
	public void checkConsoleAvgMetricsLogging()
	throws Exception {
		boolean passed = false;
		long lastSuccCount = 0;
		try (
			final BufferedReader in = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(STD_OUT_CONTENT))
			)
		) {
			String nextStdOutLine;
			Matcher m;
			do {
				nextStdOutLine = in.readLine();
				if (nextStdOutLine == null) {
					break;
				} else {
					m = CONSOLE_METRICS_AVG.matcher(nextStdOutLine);
					if (m.find()) {
						Assert.assertTrue(
							"Load type is not " + IOTask.Type.APPEND.name() + ": " + m.group("typeLoad"),
							IOTask.Type.APPEND.name().equalsIgnoreCase(m.group("typeLoad"))
						);
						long
							nextSuccCount = Long.parseLong(m.group("countSucc")),
							nextFailCount = Long.parseLong(m.group("countFail"));
						Assert.assertTrue(
							"Next deleted items count " + nextSuccCount +
								" is less than previous: " + lastSuccCount,
							nextSuccCount >= lastSuccCount
						);
						lastSuccCount = nextSuccCount;
						Assert.assertTrue("There are failures reported", nextFailCount == 0);
						passed = true;
					}
				}
			} while (true);
		}
		Assert.assertTrue(
			"Average metrics line matching the pattern was not met in the stdout", passed
		);
	}
	//
	@Test
	public void checkConsoleSumMetricsLogging()
	throws Exception {
		boolean passed = false;
		try (
			final BufferedReader in = new BufferedReader(
				new InputStreamReader(new ByteArrayInputStream(STD_OUT_CONTENT))
			)
		) {
			String nextStdOutLine;
			Matcher m;
			do {
				nextStdOutLine = in.readLine();
				if (nextStdOutLine == null) {
					break;
				} else {
					m = CONSOLE_METRICS_SUM.matcher(nextStdOutLine);
					if (m.find()) {
						Assert.assertTrue(
							"Load type is not " + IOTask.Type.APPEND.name() + ": " + m.group("typeLoad"),
							IOTask.Type.APPEND.name().equalsIgnoreCase(m.group("typeLoad"))
						);
						long
							countLimit = Long.parseLong(m.group("countLimit")),
							countSucc = Long.parseLong(m.group("countSucc")),
							countFail = Long.parseLong(m.group("countFail"));
						Assert.assertTrue(
							"Deleted items count " + countSucc +
								" is not equal to the limit: " + countLimit,
							countSucc == countLimit
						);
						Assert.assertTrue("There are failures reported", countFail == 0);
						Assert.assertFalse("Summary metrics are printed twice at least", passed);
						passed = true;
					}
				}
			} while (true);
		}
		Assert.assertTrue(
			"Summary metrics line matching the pattern was not met in the stdout", passed
		);
	}
}
