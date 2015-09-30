package org.dataone.solr.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.servlet.HttpSolrCall;
import org.apache.solr.servlet.SolrDispatchFilter;
import org.apache.solr.servlet.SolrRequestParsers;

public class DataOneSolrDispatchFilter extends SolrDispatchFilter {

    private static Log logger = LogFactory.getLog(DataOneSolrDispatchFilter.class);

    public DataOneSolrDispatchFilter() {
        logger.debug("setting solr request parsers add request headers to context to true");
        SolrRequestParsers.DEFAULT.setAddRequestHeadersToContext(true);
        logger.debug("SolrRequestParsers.DEFAULT.getAddRequestHeadersToContext is: "
                + SolrRequestParsers.DEFAULT.isAddRequestHeadersToContext());
    }

    @Override
    protected HttpSolrCall getHttpSolrCall(HttpServletRequest request,
            HttpServletResponse response, boolean retry) {
        return new DataOneHttpSolrCall(this, cores, request, response, retry);
    }
}
