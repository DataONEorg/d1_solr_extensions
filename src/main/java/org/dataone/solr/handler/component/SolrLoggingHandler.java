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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.service.exceptions.NotAuthorized;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Extends the Solr SearchHandler to add in filters based on whether or not a
 * user has been authenticated, and if authenticated, then whether or not the
 * user is an administrator
 * 
 * @author waltz
 */
public class SolrLoggingHandler extends SearchHandler {

    private static final String READ_PERMISSION_FIELD = "readPermission";
    private static Log logger = LogFactory.getLog(SolrLoggingHandler.class);

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
            InstantiationException, IllegalAccessException {
        // have to reset the parameters , so create a new parameters map
        // copy original params, add new params, set new param map in
        // SolrQueryRequest

        SolrParams solrParams = req.getParams();
        HashMap<String, String[]> convertedSolrParams = SolrSearchHandlerUtil
                .getConvertedParameters(solrParams);
        String[] isMNAdministrator = solrParams.getParams(ParameterKeys.IS_MN_ADMINISTRATOR);
        String[] isCNAdministrator = solrParams.getParams(ParameterKeys.IS_CN_ADMINISTRATOR);
        String[] authorizedSubjects = solrParams.getParams(ParameterKeys.AUTHORIZED_SUBJECTS);
        SolrSearchHandlerUtil.logSolrParameters(convertedSolrParams);
        if (SolrSearchHandlerUtil.isValidSolrParam(isMNAdministrator)
                || SolrSearchHandlerUtil.isValidSolrParam(isCNAdministrator)
                || SolrSearchHandlerUtil.isValidSolrParam(authorizedSubjects)) {
            if (SolrSearchHandlerUtil.isValidSolrParam(isCNAdministrator)
                    && !SolrSearchHandlerUtil.isCNAdministrator(isCNAdministrator)) {
                throw new NotAuthorized("1460", "Invalid Coordinating Node token");
            }
            
            logger.debug("found an Valid authorized user mn? " + isMNAdministrator + " cn? " + isCNAdministrator + " is authsubject? " + authorizedSubjects);
            SolrSearchHandlerUtil.applyReadRestrictionQueryFilterParameters(solrParams,
                    convertedSolrParams, READ_PERMISSION_FIELD);

            if (SolrSearchHandlerUtil.isValidSolrParam(isMNAdministrator)
                    && !SolrSearchHandlerUtil.isCNAdministrator(isCNAdministrator)) {
                applyMNAdministratorRestriction(solrParams, convertedSolrParams,
                        isMNAdministrator[0]);
            }
        } else {

            // Task #3886: Filter the d1-cn-log index based on a public role
            //this is a non-authorized user call. just return summary information and
            //redact sensitive columns from facets
            // For the first version of the d1_dashboard application, filter Solr queries to provide public access to only the summary information returned by Solr. This requires that queries by the public user
            // 1) should be accepted
            // 2) should have the rows parameter set to 0 despite the input prior to executing the query
            // 3) queries that include facets should redact the ipAddress, userAgent,readPermission and subject fields from the facet.field parameter prior to executing the query
            // 4) facet.prefix should be entirely removed
            // 5)  facet.query should redact any queries on the ipAddress, userAgent,readPermission and subject

            replaceParam(CommonParams.ROWS, "0", convertedSolrParams);
            if (convertedSolrParams.containsKey(FacetParams.FACET_FIELD)) {
                removeParamValue(FacetParams.FACET_FIELD, "ipAddress", convertedSolrParams);
                removeParamValue(FacetParams.FACET_FIELD, "readPermission", convertedSolrParams);
                removeParamValue(FacetParams.FACET_FIELD, "subject", convertedSolrParams);
                removeParamValue(FacetParams.FACET_FIELD, "rightsHolder", convertedSolrParams);
            }
            if (convertedSolrParams.containsKey(FacetParams.FACET_QUERY)) {
                removeParamValue(FacetParams.FACET_QUERY, "ipAddress", convertedSolrParams);
                removeParamValue(FacetParams.FACET_QUERY, "readPermission", convertedSolrParams);
                removeParamValue(FacetParams.FACET_QUERY, "subject", convertedSolrParams);
                removeParamValue(FacetParams.FACET_QUERY, "rightsHolder", convertedSolrParams);
            }
            if (convertedSolrParams.containsKey(FacetParams.FACET_PREFIX)) {
                convertedSolrParams.remove(FacetParams.FACET_PREFIX);
            }
        }
        SolrSearchHandlerUtil.setNewSolrParameters(req, convertedSolrParams);

        SolrSearchHandlerUtil.logSolrParameters(convertedSolrParams);

        super.handleRequestBody(req, rsp);
    }

    private void applyMNAdministratorRestriction(SolrParams solrParams,
            HashMap<String, String[]> convertedSolrParams, String memberNodeId) {
        logger.debug("found an Membernode user");
        String mnFilterString = "nodeId:" + SolrSearchHandlerUtil.escapeQueryChars(memberNodeId);
        MultiMapSolrParams.addParam(CommonParams.FQ, mnFilterString, convertedSolrParams);

    }

    public static void replaceParam(String name, String val, Map<String, String[]> map) {

        String[] arr = new String[] { val };
        map.put(name, arr);

    }

    public static void removeParamValue(String name, String val, Map<String, String[]> map) {

        if (map.containsKey(name)) {
            String[] arr = map.get(name);
            ArrayList<String> redactFromList = new ArrayList<String>(Arrays.asList(arr));
            ArrayList<String> redactFullEntryList = new ArrayList<String>();
            for (int i = 0; i < redactFromList.size(); ++i) {
                if (redactFromList.get(i).contains(val)) {
                    redactFullEntryList.add(redactFromList.get(i));
                }
            }
            if (redactFromList.removeAll(redactFullEntryList)) {

                map.put(name, redactFromList.toArray(new String[0]));
            }

        }
    }
}
