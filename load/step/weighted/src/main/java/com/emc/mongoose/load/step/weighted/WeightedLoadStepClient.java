package com.emc.mongoose.load.step.weighted;

import com.emc.mongoose.base.env.Extension;
import com.emc.mongoose.base.exception.InterruptRunException;
import com.emc.mongoose.base.item.op.OpType;
import com.emc.mongoose.base.load.step.client.LoadStepClient;
import com.emc.mongoose.base.load.step.client.LoadStepClientBase;
import com.emc.mongoose.base.logging.LogUtil;
import com.emc.mongoose.base.metrics.MetricsManager;
import com.github.akurilov.commons.reflection.TypeUtil;
import com.github.akurilov.commons.system.SizeInBytes;
import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.exceptions.InvalidValuePathException;
import com.github.akurilov.confuse.exceptions.InvalidValueTypeException;
import com.github.akurilov.confuse.impl.BasicConfig;
import org.apache.logging.log4j.Level;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static com.github.akurilov.commons.collection.TreeUtil.reduceForest;
import static com.github.akurilov.confuse.Config.deepToMap;

public class WeightedLoadStepClient
				extends LoadStepClientBase {

	public WeightedLoadStepClient(
					final Config config, final List<Extension> extensions, final List<Config> ctxConfigs,
					final MetricsManager metricsManager) {
		super(config, extensions, ctxConfigs, metricsManager);
	}

	@Override
	public String getTypeName() {
		return WeightedLoadStepExtension.TYPE;
	}

	@Override
	@SuppressWarnings("unchecked")
	protected <T extends LoadStepClient> T copyInstance(final Config config, final List<Config> ctxConfigs) {
		return (T) new WeightedLoadStepClient(config, extensions, ctxConfigs, metricsMgr);
	}

	@Override
	protected void init()
					throws InterruptRunException, IllegalStateException {
		final String autoStepId = "weighted_" + LogUtil.getDateTimeStamp();
		final Config stepConfig = config.configVal("load-step");
		if (stepConfig.boolVal("idAutoGenerated")) {
			stepConfig.val("id", autoStepId);
		}
		final int subStepCount = ctxConfigs.size();
		// 2nd pass: initialize the sub steps
		for (int originIndex = 0; originIndex < subStepCount; originIndex++) {
			final Map<String, Object> mergedConfigTree = reduceForest(
							Arrays.asList(deepToMap(config), deepToMap(ctxConfigs.get(originIndex))));
			final Config subConfig;
			try {
				subConfig = new BasicConfig(config.pathSep(), config.schema(), mergedConfigTree);
			} catch (final InvalidValueTypeException | InvalidValuePathException e) {
				LogUtil.exception(Level.FATAL, e, "Scenario syntax error");
				throw new InterruptRunException(e);
			}
			final OpType opType = OpType.valueOf(subConfig.stringVal("load-op-type").toUpperCase());
			final int concurrencyLimit = config.intVal("storage-driver-limit-concurrency");
			final Config outputConfig = subConfig.configVal("output");
			final Config metricsConfig = outputConfig.configVal("metrics");
			final SizeInBytes itemDataSize;
			final Object itemDataSizeRaw = subConfig.val("item-data-size");
			if (itemDataSizeRaw instanceof String) {
				itemDataSize = new SizeInBytes((String) itemDataSizeRaw);
			} else {
				itemDataSize = new SizeInBytes(TypeUtil.typeConvert(itemDataSizeRaw, long.class));
			}
			final boolean colorFlag = outputConfig.boolVal("color");
			initMetrics(originIndex, opType, concurrencyLimit, metricsConfig, itemDataSize, colorFlag);
		}
	}
}
