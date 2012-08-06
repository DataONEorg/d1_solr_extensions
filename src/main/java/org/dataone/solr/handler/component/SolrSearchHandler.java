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
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.util.plugin.SolrCoreAware;

/**
 * Custom Solr SearchHandler to provide DataONE security filtering behavior.
 * Based on LoggingSearchHandler.
 * 
 * @author sroseboo
 * 
 */
public class SolrSearchHandler extends SearchHandler implements SolrCoreAware {

    private static final String READ_PERMISSION_FIELD = "readPermission";

    @Override
    public void handleRequestBody(SolrQueryRequest request, SolrQueryResponse response)
            throws Exception, ParseException, InstantiationException, IllegalAccessException {
        // have to reset the parameters , so create a new parameters map
        // copy original params, add new params, set new param map in
        // SolrQueryRequest
        SolrParams solrParams = request.getParams();
        HashMap<String, String[]> convertedSolrParams = SolrSearchHandlerUtil
                .getConvertedParameters(solrParams);

        disableMLTResults(convertedSolrParams);

        SolrSearchHandlerUtil.logSolrParameters(convertedSolrParams);

        SolrSearchHandlerUtil.applyReadRestrictionQueryFilterParameters(solrParams,
                convertedSolrParams, READ_PERMISSION_FIELD);

        SolrSearchHandlerUtil.setNewSolrParameters(request, convertedSolrParams);

        SolrSearchHandlerUtil.logSolrParameters(convertedSolrParams);

        super.handleRequestBody(request, response);
    }

    /**
     * MLT results do not adhere to query filter parameters and thus breaks
     * security filtering.
     * 
     * @param convertedSolrParams
     */
    private void disableMLTResults(HashMap<String, String[]> convertedSolrParams) {
        convertedSolrParams.remove("mlt");
        MultiMapSolrParams.addParam("mlt", "false", convertedSolrParams);
    }
}
