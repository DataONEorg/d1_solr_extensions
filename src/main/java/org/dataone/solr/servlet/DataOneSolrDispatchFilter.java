package org.dataone.solr.servlet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.servlet.SolrDispatchFilter;
import org.apache.solr.servlet.SolrRequestParsers;
import org.dataone.solr.handler.component.SolrSearchHandler;

public class DataOneSolrDispatchFilter extends SolrDispatchFilter {

    private static Log logger = LogFactory.getLog(SolrSearchHandler.class);

    public DataOneSolrDispatchFilter() {
        logger.debug("setting solr request parsers add request headers to context to true");
        SolrRequestParsers.DEFAULT.setAddRequestHeadersToContext(true);
        logger.debug("SolrRequestParsers.DEFAULT.getAddRequestHeadersToContext is: "
                + SolrRequestParsers.DEFAULT.isAddRequestHeadersToContext());
    }
}
