package com.emc.mongoose.webui;
//
import com.emc.mongoose.common.log.LogUtil;
//
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
//
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
//
import java.io.IOException;
/**
 * Created by gusakk on 02/10/14.
 */
public final class MainServlet
extends HttpServlet {
	//
	private static final Logger LOG = LogManager.getLogger();
	private static final String RT_CONFIG_NAME = "runTimeConfig";
 	//
	@Override
	public final void doGet(final HttpServletRequest request, final HttpServletResponse response) {
		//
		request.getSession(true).setAttribute("runmodes", CommonServlet.THREADS_MAP.keySet());
		request.getSession(true).setAttribute("stopped", CommonServlet.STOPPED_RUN_MODES);
		request.getSession(true).setAttribute("chartsMap", CommonServlet.CHARTS_MAP);
		request.setAttribute(RT_CONFIG_NAME, CommonServlet.getLastRunTimeConfig());
		try {
			request.getRequestDispatcher("index.html").forward(request, response);
		} catch (final IOException|ServletException e) {
			LogUtil.exception(LOG, Level.DEBUG, e, "Failed forwarding to index.jsp");
		}
	}

}
