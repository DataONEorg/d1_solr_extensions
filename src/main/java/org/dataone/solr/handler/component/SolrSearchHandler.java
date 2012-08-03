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

import org.apache.commons.lang3.StringUtils;
import org.apache.lucene.queryParser.ParseException;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.configuration.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom Solr SearchHandler to provide DataONE security filtering behavior.
 * Based on LoggingSearchHandler.
 * 
 * @author sroseboo
 * 
 */
public class SolrSearchHandler extends SearchHandler implements SolrCoreAware {

    Logger logger = LoggerFactory.getLogger(SolrSearchHandler.class);
    protected String administratorToken = Settings.getConfiguration().getString(
            "cn.solrAdministrator.token");
    static private String publicFilterString = "isPublic:true";

    @Override
    public void handleRequestBody(SolrQueryRequest request, SolrQueryResponse response)
            throws Exception, ParseException, InstantiationException, IllegalAccessException {
        // have to reset the parameters , so create a new parameters map
        // copy original params, add new params, set new param map in
        // SolrQueryRequest
        SolrParams solrParams = request.getParams();
        HashMap<String, String[]> convertedSolrParams = new HashMap<String, String[]>();
        convertedSolrParams.putAll(SolrParams.toMultiMap(solrParams.toNamedList()));

        disableMLTResults(convertedSolrParams);

        logSolrParameters(convertedSolrParams);

        applySecurityQueryFilterSolrParameters(solrParams, convertedSolrParams);

        request.setParams(new MultiMapSolrParams(convertedSolrParams));

        logSolrParameters(convertedSolrParams);

        super.handleRequestBody(request, response);
    }

    private void applySecurityQueryFilterSolrParameters(SolrParams solrParams,
            HashMap<String, String[]> convertedSolrParams) {

        String[] isAdministrator = solrParams.getParams(ParameterKeys.IS_CN_ADMINISTRATOR);
        convertedSolrParams.remove(ParameterKeys.AUTHORIZED_SUBJECTS);
        if (notAdministrator(isAdministrator)) {
            logger.debug("not an administrative user");
            String[] authorizedSubjects = solrParams.getParams(ParameterKeys.AUTHORIZED_SUBJECTS);
            if ((authorizedSubjects != null) && (authorizedSubjects.length > 0)) {
                logger.debug("found an authorized user");
                ArrayList<String> authorizedSubjectList = new ArrayList<String>();
                for (int i = 0; i < authorizedSubjects.length; i++) {
                    // since subjects may have spaces in them, format the string
                    // in quotes
                    authorizedSubjectList.add("\"" + authorizedSubjects[i] + "\"");
                }
                String readPermissionFilterString = "readPermission:"
                        + StringUtils.join(authorizedSubjectList, " OR readPermission:");
                logger.info(readPermissionFilterString);
                MultiMapSolrParams.addParam(CommonParams.FQ, readPermissionFilterString,
                        convertedSolrParams);
            } else {
                logger.debug("found a public user");
                MultiMapSolrParams.addParam(CommonParams.FQ, publicFilterString,
                        convertedSolrParams);
            }
        } else {
            if (isAdministrator(isAdministrator)) {
                logger.debug("found an administrative user");
            } else {
                MultiMapSolrParams.addParam(CommonParams.FQ, publicFilterString,
                        convertedSolrParams);
                logger.warn("an invalid administrative user got passed initial verification in SessionAuthorizationFilter admin token: "
                        + isAdministrator[0]);
            }
        }
    }

    private boolean isAdministrator(String[] isAdministrator) {
        // we need to check the value of the isAdministrator param value
        //
        // the isAdministrator param value should be set by a property
        // not readable by the public, in order to ensure that
        // a request HTTP QUERY does not attempt to gain administrative
        // access by spoofing the parameter

        // if the administratorToken is not set in a properties file
        // then do not allow administrative access
        return (isAdministrator != null && StringUtils.isNotEmpty(administratorToken) && administratorToken
                .equals(isAdministrator[0]));
    }

    private boolean notAdministrator(String[] isAdministrator) {
        // if the isAdministrator value of the solrParams is not
        // set or is of 0 length, then do not allow administrative access
        return (isAdministrator == null) || (isAdministrator.length == 0);
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

    private void logSolrParameters(HashMap<String, String[]> convertedSolrParams) {
        if (logger.isDebugEnabled()) {
            for (String key : convertedSolrParams.keySet()) {
                logger.info(key + " " + StringUtils.join(convertedSolrParams.get(key), " "));
            }
        }
    }
}
