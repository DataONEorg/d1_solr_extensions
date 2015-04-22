package org.dataone.solr.servlet;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Update Service Filter to log request body of update requests.  
 * Logging to debug string value mangling.
 * 
 * @author sroseboo
 */
public class UpdateServiceSessionFilter implements Filter {

    protected static Log logger = LogFactory.getLog(UpdateServiceSessionFilter.class);

    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {

        RequestBodyHttpServletRequestWrapper requestWrapper = new RequestBodyHttpServletRequestWrapper(
                (HttpServletRequest) request);

        String body = requestWrapper.getBody();
        String uri = requestWrapper.getRequestURI();

        logger.warn("Logging Request Body for URL: " + uri + ": ");
        logger.warn(body);

        filterChain.doFilter(request, response);

    }

    public void destroy() {
        logger.debug("UpdateServiceSessionFilter.destroy invoked.");
    }

    public void init(FilterConfig arg0) throws ServletException {
        logger.debug("UpateServiceSessionFilter.init invoked.");
    }
}
