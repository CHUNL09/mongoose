package com.emc.mongoose.integ.core.single;
import com.emc.mongoose.common.conf.RunTimeConfig;
import com.emc.mongoose.common.conf.SizeUtil;
import com.emc.mongoose.common.log.Markers;
import com.emc.mongoose.common.log.appenders.RunIdFileManager;
import com.emc.mongoose.integ.base.LoggingTestBase;
import com.emc.mongoose.integ.base.WSMockTestBase;
import com.emc.mongoose.integ.suite.StdOutInterceptorTestSuite;
import com.emc.mongoose.integ.tools.BufferingOutputStream;
import com.emc.mongoose.integ.tools.ContentGetter;
import com.emc.mongoose.integ.tools.LogValidator;
import com.emc.mongoose.integ.tools.TestConstants;
import com.emc.mongoose.run.scenario.runner.ScriptMockRunner;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
/**
 Created by andrey on 15.10.15.
 */
public class CustomUniformSeqSizeReadTest
extends WSMockTestBase {

	private static BufferingOutputStream STD_OUTPUT_STREAM;

	private static final int LIMIT_COUNT = 1000;
	private static final String DATA_SIZE = "765KB";
	private static final String RUN_ID = CustomUniformSeqSizeReadTest.class.getCanonicalName();

	private static final String
		CREATE_RUN_ID = RUN_ID + TestConstants.LOAD_CREATE,
		READ_RUN_ID = RUN_ID + TestConstants.LOAD_READ;

	private final static String DEFAULT_RING_SIZE = "4MB";

	@BeforeClass
	public static void setUpClass()
	throws Exception{
		System.setProperty(RunTimeConfig.KEY_RUN_ID, CREATE_RUN_ID);
		System.setProperty(RunTimeConfig.KEY_DATA_SRC_RING_SIZE, "1000000");
		System.setProperty(RunTimeConfig.KEY_DATA_SRC_RING_UNIFORM_SIZE, "12321");
		WSMockTestBase.setUpClass();
		//
		RunTimeConfig rtConfig = RunTimeConfig.getContext();
		rtConfig.set(RunTimeConfig.KEY_LOAD_LIMIT_COUNT, Integer.toString(LIMIT_COUNT));
		rtConfig.set(RunTimeConfig.KEY_DATA_SIZE_MAX, DATA_SIZE);
		rtConfig.set(RunTimeConfig.KEY_DATA_SIZE_MIN, DATA_SIZE);
		rtConfig.set(RunTimeConfig.KEY_API_S3_BUCKET, TestConstants.BUCKET_NAME);
		RunTimeConfig.setContext(rtConfig);
		//
		final Logger logger = LogManager.getLogger();
		logger.info(Markers.MSG, RunTimeConfig.getContext().toString());
		//  write
		new ScriptMockRunner().run();
		//
		RunIdFileManager.flushAll();
		//
		System.setProperty(RunTimeConfig.KEY_RUN_ID, READ_RUN_ID);
		LoggingTestBase.setUpClass();
		//
		rtConfig = RunTimeConfig.getContext();
		rtConfig.set(
			RunTimeConfig.KEY_DATA_SRC_FPATH,
			LogValidator.getDataItemsFile(CREATE_RUN_ID).getPath()
		);
		rtConfig.set(
			RunTimeConfig.KEY_SCENARIO_SINGLE_LOAD, TestConstants.LOAD_READ.toLowerCase()
		);
		rtConfig.set(RunTimeConfig.KEY_API_S3_BUCKET, TestConstants.BUCKET_NAME);
		//
		logger.info(Markers.MSG, rtConfig);
		//  read
		try(
			final BufferingOutputStream
				stdOutStream = StdOutInterceptorTestSuite.getStdOutBufferingStream()
		) {
			new ScriptMockRunner().run();
			//  Wait for "Scenario end" message
			TimeUnit.SECONDS.sleep(5);
			STD_OUTPUT_STREAM = stdOutStream;
		}
		//
		RunIdFileManager.flushAll();
	}

	@AfterClass
	public static void tearDownClass()
	throws Exception {
		WSMockTestBase.tearDownClass();
		System.setProperty(RunTimeConfig.KEY_DATA_SRC_RING_SIZE, DEFAULT_RING_SIZE);
		System.setProperty(RunTimeConfig.KEY_DATA_SRC_RING_UNIFORM_SIZE, DEFAULT_RING_SIZE);
	}

	@Test
	public void shouldGetAllDataItemsFromServerAndDataSizeIsCorrect()
	throws Exception {
		//  Read data.items.csv file
		final File dataItemsFile = LogValidator.getDataItemsFile(CREATE_RUN_ID);
		Assert.assertTrue("data.items.csv file doesn't exist", dataItemsFile.exists());
		//
		try(
			final BufferedReader
				in = Files.newBufferedReader(dataItemsFile.toPath(), StandardCharsets.UTF_8)
		) {
			int actualDataSize;
			//
			final Iterable<CSVRecord> recIter = CSVFormat.RFC4180.parse(in);
			for(final CSVRecord nextRec : recIter) {
				try {
					actualDataSize = ContentGetter.getDataSize(
						nextRec.get(0), TestConstants.BUCKET_NAME
					);
					Assert.assertEquals(
						"Size of data item isn't correct", SizeUtil.toSize(DATA_SIZE), actualDataSize
					);
				} catch (final IOException e) {
					Assert.fail(String.format("Failed to get data item %s from server", nextRec.get(0)));
				}
			}
		}
	}

	@Test
	public void shouldCreateAllFilesWithLogsAfterWriteScenario()
	throws Exception {
		Path expectedFile = LogValidator.getMessageFile(CREATE_RUN_ID).toPath();
		//  Check that messages.log exists
		Assert.assertTrue("messages.log file of create load doesn't exist", Files.exists(expectedFile));

		expectedFile = LogValidator.getPerfAvgFile(CREATE_RUN_ID).toPath();
		//  Check that perf.avg.csv file exists
		Assert.assertTrue("perf.avg.csv file of create load doesn't exist", Files.exists(expectedFile));

		expectedFile = LogValidator.getPerfSumFile(CREATE_RUN_ID).toPath();
		//  Check that perf.sum.csv file exists
		Assert.assertTrue("perf.sum.csv file of create load doesn't exist", Files.exists(expectedFile));

		expectedFile = LogValidator.getPerfTraceFile(CREATE_RUN_ID).toPath();
		//  Check that perf.trace.csv file exists
		Assert.assertTrue("perf.trace.csv file of create load doesn't exist", Files.exists(expectedFile));

		expectedFile = LogValidator.getDataItemsFile(CREATE_RUN_ID).toPath();
		//  Check that data.items.csv file exists
		Assert.assertTrue("data.items.csv file of create load doesn't exist", Files.exists(expectedFile));
	}

	@Test
	public void shouldCreateAllFilesWithLogsAfterReadScenario()
	throws Exception {
		Path expectedFile = LogValidator.getMessageFile(READ_RUN_ID).toPath();
		//  Check that messages.log file is contained
		Assert.assertTrue("messages.log file of read load doesn't exist", Files.exists(expectedFile));

		expectedFile = LogValidator.getPerfAvgFile(READ_RUN_ID).toPath();
		//  Check that perf.avg.csv file is contained
		Assert.assertTrue("perf.avg.csv file of read load doesn't exist", Files.exists(expectedFile));

		expectedFile = LogValidator.getPerfSumFile(READ_RUN_ID).toPath();
		//  Check that perf.sum.csv file is contained
		Assert.assertTrue("perf.sum.csv file of read load doesn't exist", Files.exists(expectedFile));

		expectedFile = LogValidator.getPerfTraceFile(READ_RUN_ID).toPath();
		//  Check that perf.trace.csv file is contained
		Assert.assertTrue("perf.trace.csv file of read load doesn't exist", Files.exists(expectedFile));

		expectedFile = LogValidator.getDataItemsFile(READ_RUN_ID).toPath();
		//  Check that data.items.csv file is contained
		Assert.assertTrue("data.items.csv file of read load doesn't exist", Files.exists(expectedFile));
	}

	@Test
	public void shouldCreateCorrectDataItemsFileAfterReadScenario()
	throws Exception {
		//  Get data.items.csv file of read scenario run
		final File readDataItemFile = LogValidator.getDataItemsFile(READ_RUN_ID);
		Assert.assertTrue("data.items.csv file doesn't exist", readDataItemFile.exists());
		//
		try(
			final BufferedReader
				in = Files.newBufferedReader(readDataItemFile.toPath(), StandardCharsets.UTF_8)
		) {
			LogValidator.assertCorrectDataItemsCSV(in);
		}
	}

	@Test
	public void shouldCreateCorrectPerfSumFileAfterReadScenario()
	throws Exception {
		// Get perf.sum.csv file of read scenario run
		final File readPerfSumFile = LogValidator.getPerfSumFile(READ_RUN_ID);
		Assert.assertTrue("perf.sum.csv file doesn't exist", readPerfSumFile.exists());
		//
		try(
			final BufferedReader
				in = Files.newBufferedReader(readPerfSumFile.toPath(), StandardCharsets.UTF_8)
		) {
			LogValidator.assertCorrectPerfSumCSV(in);
		}
	}

	@Test
	public void shouldCreateCorrectPerfAvgFileAfterReadScenario()
	throws Exception {
		// Get perf.avg.csv file
		final File readPerfAvgFile = LogValidator.getPerfAvgFile(READ_RUN_ID);
		Assert.assertTrue("perfAvg.csv file doesn't exist", readPerfAvgFile.exists());
		//
		try(
			final BufferedReader
				in = Files.newBufferedReader(readPerfAvgFile.toPath(), StandardCharsets.UTF_8)
		) {
			LogValidator.assertCorrectPerfAvgCSV(in);
		}
	}

	@Test
	public void shouldCreateCorrectPerfTraceFileAfterReadScenario()
	throws Exception {
		// Get perf.trace.csv file
		final File readPerfTraceFile = LogValidator.getPerfTraceFile(READ_RUN_ID);
		Assert.assertTrue("perf.trace.csv file doesn't exist", readPerfTraceFile.exists());
		//
		try(
			final BufferedReader
				in = Files.newBufferedReader(readPerfTraceFile.toPath(), StandardCharsets.UTF_8)
		) {
			LogValidator.assertCorrectPerfTraceCSV(in);
		}
	}

	@Test
	public void shouldWriteAllDataItemsInCorrectSize()
	throws Exception {
		//  Read data.items.csv file
		final File dataItemsFile = LogValidator.getDataItemsFile(CREATE_RUN_ID);
		Assert.assertTrue("data.items.csv file of create load doesn't exist", dataItemsFile.exists());
		//
		try(
			final BufferedReader
				in = Files.newBufferedReader(dataItemsFile.toPath(), StandardCharsets.UTF_8)
		) {
			//
			int countDataItems = 0;
			final Iterable<CSVRecord> recIter = CSVFormat.RFC4180.parse(in);
			for(final CSVRecord nextRec : recIter) {
				Assert.assertEquals(
					"Size of data item isn't correct",
					Long.toString(SizeUtil.toSize(DATA_SIZE)), nextRec.get(2)
				);
				countDataItems++;
			}
			//  Check that there are 10 lines in data.items.csv file
			Assert.assertEquals(
				"Not correct information about created data items", LIMIT_COUNT, countDataItems
			);
		}
	}

	@Test
	public void shouldReportCorrectCountOfReadObjectToSummaryLogFile()
	throws Exception {
		//  Read perf.summary file
		final File perfSumFile = LogValidator.getPerfSumFile(READ_RUN_ID);

		//  Check that file exists
		Assert.assertTrue("perf.sum.csv file of read load doesn't exist", perfSumFile.exists());

		try(
			final BufferedReader
				in = Files.newBufferedReader(perfSumFile.toPath(), StandardCharsets.UTF_8)
		) {
			boolean firstRow = true;
			//
			final Iterable<CSVRecord> recIter = CSVFormat.RFC4180.parse(in);
			for(final CSVRecord nextRec : recIter) {
				if (firstRow) {
					firstRow = false;
				} else if (nextRec.size() == 23) {
					Assert.assertTrue(
						"Count of success is not integer", LogValidator.isInteger(nextRec.get(7))
					);
					Assert.assertEquals(
						"Count of success isn't correct", Integer.toString(LIMIT_COUNT), nextRec.get(7)
					);
				}
			}
		}
	}
}
