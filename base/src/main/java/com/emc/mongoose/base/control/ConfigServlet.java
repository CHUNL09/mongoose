package com.emc.mongoose.base.control;

import static com.emc.mongoose.base.config.ConfigUtil.writerWithPrettyPrinter;

import com.emc.mongoose.base.config.ConfigUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.akurilov.confuse.Config;
import com.github.akurilov.confuse.io.json.TypeNames;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** @author veronika K. on 26.10.18 */
public class ConfigServlet extends HttpServlet {

  private static final String SCHEMA_PATH = "schema";
  private static final String CONTEXT_SEP = "/";

  private final Config config;

  public ConfigServlet(final Config config) {
    this.config = config;
  }

  @Override
  protected final void doGet(final HttpServletRequest req, final HttpServletResponse resp)
      throws IOException {
    final String[] contexts = req.getRequestURI().split(CONTEXT_SEP);
    if (contexts.length == 2) {
      getConfig(resp);
    } else if (contexts[2].equals(SCHEMA_PATH)) {
      getSchema(resp);
    } else {
      resp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      resp.getWriter().print("<ERROR> Such URI not found : " + req.getRequestURI());
    }
  }

  private void getSchema(final HttpServletResponse resp) throws IOException {
    String schemaStr =
        writerWithPrettyPrinter(new ObjectMapper()).writeValueAsString(config.schema());
    for (final String k : TypeNames.MAP.keySet()) {
      final String v = TypeNames.MAP.get(k).getTypeName();
      schemaStr = schemaStr.replaceAll(v, k);
    }
    resp.setStatus(HttpServletResponse.SC_OK);
    final PrintWriter respWriter = resp.getWriter();
    respWriter.print(schemaStr);
    respWriter.println();
  }

  private void getConfig(final HttpServletResponse resp) throws IOException {
    resp.setStatus(HttpServletResponse.SC_OK);
    final PrintWriter respWriter = resp.getWriter();
    respWriter.print(ConfigUtil.toString(config));
    respWriter.println();
  }
}
