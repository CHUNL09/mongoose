package com.emc.mongoose.load.step.type.weighted;

import com.emc.mongoose.env.Extension;
import com.emc.mongoose.env.ExtensionBase;
import com.emc.mongoose.load.step.LoadStepFactory;

import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.SchemaProvider;

import com.github.akurilov.confuse.io.json.JsonSchemaProviderBase;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.emc.mongoose.Constants.APP_NAME;

public class WeightedLoadStepExtension<T extends WeightedLoadStep>
extends ExtensionBase
implements LoadStepFactory<T> {

	private static final SchemaProvider SCHEMA_PROVIDER = new JsonSchemaProviderBase() {

		@Override
		protected final InputStream schemaInputStream() {
			return getClass().getResourceAsStream("/config-schema-load-generator-weight.json");
		}

		@Override
		public final String id() {
			return APP_NAME;
		}
	};

	private static final String DEFAULTS_FILE_NAME = "defaults-load-generator-weight.json";

	private static final List<String> RES_INSTALL_FILES = Collections.unmodifiableList(
		Arrays.asList("config/" + DEFAULTS_FILE_NAME)
	);

	@Override
	public String id() {
		return WeightedLoadStep.TYPE;
	}

	@Override @SuppressWarnings("unchecked")
	public T create(
		final Config baseConfig, final List<Extension> extensions,
		final List<Map<String, Object>> overrides
	) {
		return (T) new WeightedLoadStep(baseConfig, extensions, overrides);
	}

	@Override
	protected final SchemaProvider schemaProvider() {
		return SCHEMA_PROVIDER;
	}

	@Override
	protected final String defaultsFileName() {
		return DEFAULTS_FILE_NAME;
	}

	@Override
	protected final List<String> resourceFilesToInstall() {
		return RES_INSTALL_FILES;
	}
}
