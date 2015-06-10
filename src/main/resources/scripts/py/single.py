from timeout import init as timeOutInit
from loadbuilder import init as loadBuilderInit
#
from com.emc.mongoose.common.conf import RunTimeConfig
from com.emc.mongoose.common.logging import LogUtil, Markers
#
from com.emc.mongoose.core.api.io.task import IOTask
#
from org.apache.logging.log4j import Level, LogManager
#
from java.lang import System, Throwable, IllegalArgumentException, InterruptedException
from java.util import NoSuchElementException
#
LOG = LogManager.getLogger()
#
def init():
	loadBuilder = loadBuilderInit()
	try:
		loadType = IOTask.Type.valueOf(
			RunTimeConfig.getContext().getString(RunTimeConfig.KEY_SCENARIO_SINGLE_LOAD).upper()
		)
		LOG.debug(Markers.MSG, "Using load type: {}", loadType.name())
		loadBuilder.setLoadType(loadType)
	except NoSuchElementException:
		LOG.error(Markers.ERR, "No load type specified, try arg -Dscenario.single.load=<VALUE> to override")
	except IllegalArgumentException:
		LOG.error(Markers.ERR, "No such load type, it should be a constant from Load.Type enumeration")
	return loadBuilder
#
def build(loadBuilder):
	load = None
	if loadBuilder is None:
		LOG.fatal(Markers.ERR, "No load builder specified")
	else:
		try:
			load = loadBuilder.build()
		except Throwable as e:
			LogUtil.exception(LOG, Level.FATAL, e, "Failed to instantiate the load executor")
	return load
#
def execute(load):
	if load is None:
		LOG.fatal(Markers.ERR, "No load job specified")
	else:
		runTimeOut = timeOutInit()
		load.start()
		try:
			load.await(runTimeOut[0], runTimeOut[1])
		finally:
			load.close()
#
if __name__ == "__builtin__":
	loadBuilder = init()
	load = build(loadBuilder)
	try:
		execute(load)
	except InterruptedException as e:
		LOG.debug(Markers.MSG, "Single was interrupted")
	except Throwable as e:
		e.printStackTrace(System.err)
		LogUtil.exception(LOG, Level.ERROR, e, "Scenario failed")
	loadBuilder.close() # to exit normally
	LOG.info(Markers.MSG, "Scenario end")
