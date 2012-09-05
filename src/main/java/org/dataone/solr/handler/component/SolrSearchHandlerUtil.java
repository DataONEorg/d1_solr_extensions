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
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.configuration.Settings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class to provide shared behavior among extensions/customizations of
 * SolrSearchHandler classes. The main scope of this utility is to provide
 * modification of solr search parameters to provide security authorization
 * restrictions regarding which solr records a DataONE user is allowed to read.
 * 
 * Also provides a replacement for SolrParams.toMultiMap to support fields like
 * facet.field which appear more than one time as request parameters.
 * 
 * @author sroseboo
 * 
 */
public class SolrSearchHandlerUtil {

    private static Logger logger = LoggerFactory.getLogger(SolrSearchHandlerUtil.class);
    private static String publicFilterString = "isPublic:true";
    private static String cnAdministratorToken = Settings.getConfiguration().getString(
            "cn.solrAdministrator.token");

    public static void applyReadRestrictionQueryFilterParameters(SolrParams solrParams,
            HashMap<String, String[]> convertedSolrParams, String readField) {

        String[] isAdministrator = solrParams.getParams(ParameterKeys.IS_CN_ADMINISTRATOR);
        if ((isAdministrator == null) || (isAdministrator.length == 0)) {
            // might be a membernode, and depending on the implemenation this may have consequences
            isAdministrator = solrParams.getParams(ParameterKeys.IS_MN_ADMINISTRATOR);
        }
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
                    authorizedSubjectList.add("\"" + escapeQueryChars(authorizedSubjects[i]) + "\"");
                }
                String readPermissionFilterString = readField + ":"
                        + StringUtils.join(authorizedSubjectList, " OR " + readField + ":");
                logger.debug("read permission string: " + readPermissionFilterString);
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
    public static boolean isAdministrator(String[] isAdministrator) {
        // we need to check the value of the isAdministrator param value
        //
        // the isAdministrator param value should be set by a property
        // not readable by the public, in order to ensure that
        // a request HTTP QUERY does not attempt to gain administrative
        // access by spoofing the parameter

        // if the cnAdministratorToken is not set in a properties file
        // then do not allow administrative access
        return (isAdministrator != null && StringUtils.isNotEmpty(cnAdministratorToken));
    }
    public static boolean isCNAdministrator(String[] isAdministrator) {
        // we need to check the value of the isAdministrator param value
        //
        // the isAdministrator param value should be set by a property
        // not readable by the public, in order to ensure that
        // a request HTTP QUERY does not attempt to gain administrative
        // access by spoofing the parameter

        // if the cnAdministratorToken is not set in a properties file
        // then do not allow administrative access
        return (isAdministrator != null && StringUtils.isNotEmpty(cnAdministratorToken) && cnAdministratorToken
                .equals(isAdministrator[0]));
    }

    private static boolean notAdministrator(String[] isAdministrator) {
        // if the isAdministrator value of the solrParams is not
        // set or is of 0 length, then do not allow administrative access
        return (isAdministrator == null) || (isAdministrator.length == 0);
    }

    public static void logSolrParameters(HashMap<String, String[]> convertedSolrParams) {
        if (logger.isDebugEnabled()) {
            for (String key : convertedSolrParams.keySet()) {
                logger.debug("key : " + key);
                for (int i = 0; i < convertedSolrParams.get(key).length; i++) {
                    String value = convertedSolrParams.get(key)[i];
                    logger.debug("value: " + value);
                }
            }
        }
    }

    public static HashMap<String, String[]> getConvertedParameters(SolrParams solrParams) {
        HashMap<String, String[]> convertedSolrParams = new HashMap<String, String[]>();
        convertedSolrParams.putAll(SolrSearchHandlerUtil.toMultiMap(solrParams.toNamedList()));
        return convertedSolrParams;
    }

    public static void setNewSolrParameters(SolrQueryRequest request,
            HashMap<String, String[]> convertedSolrParams) {
        request.setParams(new MultiMapSolrParams(convertedSolrParams));
    }

    /**
     * This method is a replacement for SolrParams.toMultiMap. This method was
     * updated in later versions in particular with this fix:
     * https://issues.apache.org/jira/browse/SOLR-1666 replaces to address
     * issues dealing with request parameters that can occur multiple times in a
     * request, for example facet.field. The current version does not properly
     * handle this conversion causing errors.
     * 
     **/
    public static Map<String, String[]> toMultiMap(NamedList params) {
        HashMap<String, String[]> map = new HashMap<String, String[]>();
        for (int i = 0; i < params.size(); i++) {
            String name = params.getName(i);
            Object value = params.getVal(i);
            if (value instanceof String[]) {
                for (String val : (String[]) value) {
                    MultiMapSolrParams.addParam(name, val, map);
                }
            } else {
                MultiMapSolrParams.addParam(name, value.toString(), map);
            }
        }
        return map;
    }
    
    /**
     * 
     * See: http://lucene.apache.org/java/docs/queryparsersyntax.html#Escaping%20Special%20Characters
     * 
     */
    public static String escapeQueryChars(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // These characters are part of the query syntax and must be escaped
            if (c == '\\' || c == '+' || c == '-' || c == '!'
                    || c == '(' || c == ')' || c == ':'
                    || c == '^' || c == '[' || c == ']' || c == '\"'
                    || c == '{' || c == '}' || c == '~'
                    || c == '*' || c == '?' || c == '|' || c == '&'
                    || c == ';' || Character.isWhitespace(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
