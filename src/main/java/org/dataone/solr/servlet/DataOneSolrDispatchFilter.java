package org.dataone.solr.servlet;

import org.apache.solr.servlet.SolrDispatchFilter;
import org.apache.solr.servlet.SolrRequestParsers;

public class DataOneSolrDispatchFilter extends SolrDispatchFilter {

    public DataOneSolrDispatchFilter() {
        SolrRequestParsers.DEFAULT.setAddRequestHeadersToContext(true);
    }
}
