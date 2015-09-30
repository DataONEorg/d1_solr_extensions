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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.request.SolrRequestHandler;
import org.apache.solr.response.SolrQueryResponse;
import org.dataone.service.exceptions.ServiceFailure;

/**
 * Custom Solr SearchHandler to provide DataONE security filtering behavior.
 * Based on LoggingSearchHandler.
 * 
 * @author sroseboo
 * 
 */
public class SolrSearchHandler extends SearchHandler implements SolrRequestHandler {

    private static final String READ_PERMISSION_FIELD = "readPermission";
    private static final String RIGHTS_HOLDER_FIELD = "rightsHolder";
    private static final String WRITE_PERMISSION_FIELD = "writePermission";
    private static final String CHANGE_PERMISSION_FIELD = "changePermission";
    private static List<String> readPermissionFields = new ArrayList<String>();
    static {
        readPermissionFields.add(READ_PERMISSION_FIELD);
        readPermissionFields.add(RIGHTS_HOLDER_FIELD);
        readPermissionFields.add(WRITE_PERMISSION_FIELD);
        readPermissionFields.add(CHANGE_PERMISSION_FIELD);
    }

    private static Log logger = LogFactory.getLog(SolrSearchHandler.class);

    public SolrSearchHandler() {
    }

    public void handleRequestBody(SolrQueryRequest request, SolrQueryResponse response)
            throws Exception, InstantiationException, IllegalAccessException {
        // have to reset the parameters , so create a new parameters map
        // copy original params, add new params, set new param map in
        // SolrQueryRequest
        HttpServletRequest httpServletRequest = null;
        if (request.getContext().containsKey(SolrSearchHandlerUtil.CONTEXT_HTTP_REQUEST_KEY)) {
            httpServletRequest = (HttpServletRequest) request.getContext().get(
                    SolrSearchHandlerUtil.CONTEXT_HTTP_REQUEST_KEY);
            if (httpServletRequest == null) {
                throw new ServiceFailure("1490",
                        "Solr misconfigured. Context should have the request");
            }
        } else {
            throw new ServiceFailure("4310", "Solr misconfigured. Context should have the request");
        }

        HashMap<String, String[]> convertedSolrParams = SolrSearchHandlerUtil
                .getConvertedParameters(request.getParams());

        convertedSolrParams.remove("d1-pc");

        disableMLTResults(convertedSolrParams);

        SolrSearchHandlerUtil.logSolrParameters(convertedSolrParams);

        SolrSearchHandlerUtil.applyReadRestrictionQueryFilterParameters(httpServletRequest,
                convertedSolrParams, readPermissionFields);

        SolrSearchHandlerUtil.setNewSolrParameters(request, convertedSolrParams);

        SolrSearchHandlerUtil.logSolrParameters(convertedSolrParams);

        logger.debug("Solr Search Handler query: " + request.getParams().get(CommonParams.Q));
        logger.debug("Solr Search Handler query filter: "
                + request.getParams().get(CommonParams.FQ));
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
