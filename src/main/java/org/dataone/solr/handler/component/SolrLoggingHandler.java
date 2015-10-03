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

import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.FacetParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.exceptions.ServiceFailure;

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
        HttpServletRequest httpServletRequest = null;

        if (req.getContext().containsKey(SolrSearchHandlerUtil.CONTEXT_HTTP_REQUEST_KEY)) {
            httpServletRequest = (HttpServletRequest) req.getContext().get(
                    SolrSearchHandlerUtil.CONTEXT_HTTP_REQUEST_KEY);
            if (httpServletRequest == null) {
                SolrSearchHandlerUtil.logSolrContext(req);
                throw new ServiceFailure("1490",
                        "Solr misconfigured. Context should have the request");
            }
        } else {
            SolrSearchHandlerUtil.logSolrContext(req);
            throw new ServiceFailure("1490", "Solr misconfigured. Context should have the request");
        }

        String[] isMNAdministrator = httpServletRequest
                .getParameterValues(ParameterKeys.IS_MN_ADMINISTRATOR);
        String[] isCNAdministrator = httpServletRequest
                .getParameterValues(ParameterKeys.IS_CN_ADMINISTRATOR);
        String[] authorizedSubjects = httpServletRequest
                .getParameterValues(ParameterKeys.AUTHORIZED_SUBJECTS);
        SolrParams requestParams = req.getParams();
        HashMap<String, String[]> convertedSolrParams = SolrSearchHandlerUtil
                .getConvertedParameters(requestParams);

        
        SolrSearchHandlerUtil.logSolrParameters(convertedSolrParams);
        if (SolrSearchHandlerUtil.isValidSolrParam(isMNAdministrator)
                || SolrSearchHandlerUtil.isValidSolrParam(isCNAdministrator)
                || SolrSearchHandlerUtil.isValidSolrParam(authorizedSubjects)) {

            if (SolrSearchHandlerUtil.isValidSolrParam(isCNAdministrator)
                    && !SolrSearchHandlerUtil.isCNAdministrator(isCNAdministrator)) {
                throw new NotAuthorized("1460", "Invalid Coordinating Node token");
            }

            logger.debug("found an Valid authorized user mn? "
                    + ArrayUtils.toString(isMNAdministrator) + " cn? "
                    + ArrayUtils.toString(isCNAdministrator) + " is authsubject? "
                    + ArrayUtils.toString(authorizedSubjects));

            // place a limit on the number of rows returned. When a user retrieves
            // tens of thousands # of rows, then jetty throws an out of memory error
            String[] rows = requestParams.getParams(CommonParams.ROWS);
            if (rows != null) {
                try {
                    for (int i = 0 ; i < rows.length; ++i) {
                        if (Integer.parseInt(rows[i]) > 10000) {
                            replaceParam(CommonParams.ROWS, "10000", convertedSolrParams);
                        }
                    }
                } catch (NumberFormatException ex) {
                    replaceParam(CommonParams.ROWS, "1000", convertedSolrParams);
                }
            }
            SolrSearchHandlerUtil.applyReadRestrictionQueryFilterParameters(httpServletRequest,
                    convertedSolrParams, READ_PERMISSION_FIELD);

            if (SolrSearchHandlerUtil.isValidSolrParam(isMNAdministrator)
                    && !SolrSearchHandlerUtil.isCNAdministrator(isCNAdministrator)) {
                applyMNAdministratorRestriction(convertedSolrParams, isMNAdministrator[0]);
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

    private void applyMNAdministratorRestriction(HashMap<String, String[]> convertedSolrParams,
            String memberNodeId) {
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
