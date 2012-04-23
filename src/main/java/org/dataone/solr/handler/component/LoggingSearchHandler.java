/**
 * This work was created by participants in the DataONE project, and is
 * jointly copyrighted by participating institutions in DataONE. For 
 * more information on DataONE, see our web site at http://dataone.org.
 *
 *   Copyright ${year}
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 * 
 * $Id$
 */

package org.dataone.solr.handler.component;

import java.util.ArrayList;
import java.util.HashMap;
import org.apache.solr.handler.component.SearchHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.lucene.queryParser.ParseException;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.lang3.StringUtils;
import org.dataone.configuration.Settings;

/**
 * Extends the Solr SearchHandler to add in filters based on whether or not
 * a user has been authenticated, and if authenticated, then whether or not
 * the user is an administrator
 *
 * @author waltz
 */
public class LoggingSearchHandler extends SearchHandler implements SolrCoreAware {

    Logger logger = LoggerFactory.getLogger(LoggingSearchHandler.class);
    protected String administratorToken = Settings.getConfiguration().getString("cn.solrAdministrator.token");
    static private String publicFilterString = "isPublic:true";
    /**
     * Handles a query request
     *
     * Information about the request may be obtained from req and
     * response information may be set using rsp.
     *
     * DataONE adds authentication information the the request parameters.
     * The parameters are then added to query filters in order to
     * obtain a result set that is appropriate for the level of
     * authorization of the subject requesting information
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
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception, ParseException, InstantiationException, IllegalAccessException {
        // have to reset the parameters , so create a new parameters map
        // copy original params, add new params, set new param map in SolrQueryRequest
        SolrParams solrParams = req.getParams();
        String[] isAdministrator = solrParams.getParams(ParameterKeys.IS_CN_ADMINISTRATOR);
        boolean isAdmin = false;

        HashMap<String,String[]> convertedSolrParams = new HashMap<String,String[]>();

        convertedSolrParams.putAll(solrParams.toMultiMap(solrParams.toNamedList()));

        convertedSolrParams.remove(ParameterKeys.AUTHORIZED_SUBJECTS);

        for (String key: convertedSolrParams.keySet()) {
            logger.debug(key + " " + StringUtils.join(convertedSolrParams.get(key), " "));
        }
        // if the isAdministrator value of the solrParams is not
        // set or is of 0 length, then do not allow administrative access
        if ( (isAdministrator == null) || ((isAdministrator != null) && (isAdministrator.length == 0)) ) {
            // If isAdministator does not have anything filled in, then determine
            // if there is an authorized user, or just a public user
            // in either case, the user has access to all publically readable records
            logger.info("not an administrative user");

            String[] authorizedSubjects = solrParams.getParams(ParameterKeys.AUTHORIZED_SUBJECTS);

            if ((authorizedSubjects != null) && (authorizedSubjects.length > 0) ) {
                logger.info("found an authorized user");
                ArrayList<String> authorizedSubjectList = new ArrayList<String>();
                for (int i = 0; i < authorizedSubjects.length; ++i) {
                    // since subjects may have spaces in them, format the string
                    // in quotes
                    authorizedSubjectList.add("\"" + authorizedSubjects[i] + "\"");
                }
                String readPermissionFilterString = "readPermission:" + StringUtils.join(authorizedSubjectList, " OR readPermission:");
                logger.info(readPermissionFilterString);
                MultiMapSolrParams.addParam(CommonParams.FQ, readPermissionFilterString, convertedSolrParams);

            } else {
                logger.info("found a public user");
                 MultiMapSolrParams.addParam(CommonParams.FQ, publicFilterString, convertedSolrParams);
            }

        } else {
            // we need to check the value of the isAdministrator param value
            //
            // the isAdministrator param value should be set by a property
            // not readable by the public, in order to ensure that
            // a request HTTP QUERY does not attempt to gain administrative
            // access by spoofing the parameter

            // if the administratorToken is not set in a properties file
            // then do not allow administrative access
            
            if (((administratorToken == null) || administratorToken.equalsIgnoreCase(""))
                    || (!isAdministrator[0].equals(administratorToken))) {

                MultiMapSolrParams.addParam(CommonParams.FQ, publicFilterString, convertedSolrParams);
                logger.warn("an invalid administrative user got passed initial verification in SessionAuthorizationFilter");
            } else {
                logger.info("found an administrative user");
            }
        }
         req.setParams(new MultiMapSolrParams(convertedSolrParams));

        super.handleRequestBody(req, rsp);
    }
}
