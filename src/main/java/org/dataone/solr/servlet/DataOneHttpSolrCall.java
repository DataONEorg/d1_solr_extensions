package org.dataone.solr.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.core.CoreContainer;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.servlet.HttpSolrCall;
import org.apache.solr.servlet.SolrDispatchFilter;

public class DataOneHttpSolrCall extends HttpSolrCall {

    private static Log logger = LogFactory.getLog(DataOneHttpSolrCall.class);

    public DataOneHttpSolrCall(SolrDispatchFilter solrDispatchFilter, CoreContainer cores,
            HttpServletRequest request, HttpServletResponse response, boolean retry) {
        super(solrDispatchFilter, cores, request, response, retry);
        logger.debug("instantiated DataOneHttpSolrCall");
    }

    @Override
    protected void execute(SolrQueryResponse rsp) {
        // a custom filter could add more stuff to the request before passing it on.
        // for example: sreq.getContext().put( "HttpServletRequest", req );
        // used for logging query stats in SolrCore.execute()
        solrReq.getContext().put("webapp", req.getContextPath());
        logger.debug("Adding httpRequest to solrQueryResponse context.");
        solrReq.getContext().put("httpRequest", req);
        solrReq.getCore().execute(handler, solrReq, rsp);
    }

}
