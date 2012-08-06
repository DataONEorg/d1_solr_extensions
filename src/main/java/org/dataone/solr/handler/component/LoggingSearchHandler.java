/**
 * This work was created by participants in the DataONE project, and is jointly copyrighted by participating
 * institutions in DataONE. For more information on DataONE, see our web site at http://dataone.org.
 *
 * Copyright ${year}
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 *
 * $Id$
 */
package org.dataone.solr.handler.component;

import java.util.HashMap;

import org.apache.lucene.queryParser.ParseException;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.util.plugin.SolrCoreAware;

/**
 * Extends the Solr SearchHandler to add in filters based on whether or not a
 * user has been authenticated, and if authenticated, then whether or not the
 * user is an administrator
 * 
 * @author waltz
 */
public class LoggingSearchHandler extends SearchHandler implements SolrCoreAware {

    private static final String READ_PERMISSION_FIELD = "readPermission";

    /**
     * Handles a query request
     * 
     * Information about the request may be obtained from req and response
     * information may be set using rsp.
     * 
     * DataONE adds authentication information the the request parameters. The
     * parameters are then added to query filters in order to obtain a result
     * set that is appropriate for the level of authorization of the subject
     * requesting information
     * 
     * @param req
     * @param rsp
     * @throws Exception
     * @throws ParseException
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @author waltz
     */
    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception,
            ParseException, InstantiationException, IllegalAccessException {
        // have to reset the parameters , so create a new parameters map
        // copy original params, add new params, set new param map in
        // SolrQueryRequest
        SolrParams solrParams = req.getParams();
        HashMap<String, String[]> convertedSolrParams = SolrSearchHandlerUtil
                .getConvertedParameters(solrParams);

        SolrSearchHandlerUtil.logSolrParameters(convertedSolrParams);

        SolrSearchHandlerUtil.applyReadRestrictionQueryFilterParameters(solrParams,
                convertedSolrParams, READ_PERMISSION_FIELD);

        SolrSearchHandlerUtil.setNewSolrParameters(req, convertedSolrParams);

        SolrSearchHandlerUtil.logSolrParameters(convertedSolrParams);

        super.handleRequestBody(req, rsp);
    }
}
