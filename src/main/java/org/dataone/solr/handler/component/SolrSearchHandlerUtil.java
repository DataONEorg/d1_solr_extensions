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
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.common.params.CommonParams;
import org.apache.solr.common.params.MultiMapSolrParams;
import org.apache.solr.common.params.SolrParams;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.configuration.Settings;

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

    private static Log logger = LogFactory.getLog(SolrSearchHandlerUtil.class);
    private static String publicFilterString = "isPublic:true";
    private static String cnAdministratorToken = Settings.getConfiguration().getString(
            "cn.solrAdministrator.token");
    //
    // this string is found in org.apache.solr.servlet.SolrRequestParsers, is is hardcoded and 
    // not publically available from what I can find
    //
    public static final String CONTEXT_HTTP_REQUEST_KEY = "httpRequest";
    
    public static void applyReadRestrictionQueryFilterParameters(HttpServletRequest httpServletRequest,
            HashMap<String, String[]> convertedSolrParams, String readField) {
        List<String> readFields = new ArrayList<String>();
        readFields.add(readField);
        applyReadRestrictionQueryFilterParameters(httpServletRequest, convertedSolrParams, readFields);
    }

    public static void applyReadRestrictionQueryFilterParameters(HttpServletRequest httpServletRequest,
            HashMap<String, String[]> convertedSolrParams, List<String> readFields) {

        String[] isAdministrator = httpServletRequest.getParameterValues(ParameterKeys.IS_CN_ADMINISTRATOR);
        if ((isAdministrator == null) || (isAdministrator.length == 0)) {
            // might be a membernode, and depending on the implemenation this
            // may have consequences
            isAdministrator = httpServletRequest.getParameterValues(ParameterKeys.IS_MN_ADMINISTRATOR);
        }
        convertedSolrParams.remove(ParameterKeys.AUTHORIZED_SUBJECTS);
        if (isInvalidSolrParam(isAdministrator)) {
            logger.debug("not an administrative user");
            String[] authorizedSubjects = httpServletRequest.getParameterValues(ParameterKeys.AUTHORIZED_SUBJECTS);
            if ((authorizedSubjects != null) && (authorizedSubjects.length > 0)) {
                logger.debug("found an authorized user");
                ArrayList<String> authorizedSubjectList = new ArrayList<String>();
                for (int i = 0; i < authorizedSubjects.length; i++) {
                    // since subjects may have spaces in them, format the string
                    // in quotes
                    authorizedSubjectList
                            .add("\"" + escapeQueryChars(authorizedSubjects[i]) + "\"");
                }

                StringBuffer readFqValue = new StringBuffer();
                readFqValue.append("(");
                for (Iterator<String> it = readFields.iterator(); it.hasNext();) {
                    String readField = (String) it.next();
                    readFqValue.append("(");
                    String readFieldString = readField + ":"
                            + StringUtils.join(authorizedSubjectList, " OR " + readField + ":");
                    readFqValue.append(readFieldString);
                    readFqValue.append(")");
                    System.out.println("****SEARCH SECURITY - read string for single field: "
                            + readFieldString);
                    System.out.println("****Search Security - full string: "
                            + readFqValue.toString());
                    if (it.hasNext()) {
                        readFqValue.append(" OR ");
                    }
                }
                readFqValue.append(")");
                System.out.println();
                logger.debug("**** Search security - full read permission string: "
                        + readFqValue.toString());
                MultiMapSolrParams.addParam(CommonParams.FQ, readFqValue.toString(),
                        convertedSolrParams);
             

            } else {
                logger.debug("found a public user");
                MultiMapSolrParams.addParam(CommonParams.FQ, publicFilterString,
                        convertedSolrParams);
            }
        } else {
            if (!isValidSolrParam(isAdministrator)) {
                MultiMapSolrParams.addParam(CommonParams.FQ, publicFilterString,
                        convertedSolrParams);
                logger.warn("an invalid administrative user got passed initial verification in SessionAuthorizationFilter admin token: "
                        + isAdministrator[0]);
            }
        }
    }

    public static boolean isValidSolrParam(String[] solrParam) {
        // we need to check the value of administrator and authenticated user param value
        //
        // the param values should be set by a property
        // not readable by the public, in order to ensure that
        // a request HTTP QUERY does not attempt to gain 
        // access by spoofing the parameter

        return ((solrParam != null) && (solrParam.length > 0) && StringUtils
                .isNotEmpty(solrParam[0]));
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
        return ((isAdministrator != null) && (isAdministrator.length > 0)
                && StringUtils.isNotEmpty(cnAdministratorToken) && cnAdministratorToken
                    .equals(isAdministrator[0]));
    }

    private static boolean isInvalidSolrParam(String[] solrParam) {
        // if the solr param value of the solrParams is not
        // set or is of 0 length, then return true
        return ((solrParam == null) || (solrParam.length == 0));
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
            logger.debug(name);
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
     * See:
     * http://lucene.apache.org/java/docs/queryparsersyntax.html#Escaping%20
     * Special%20Characters
     * 
     */
    public static String escapeQueryChars(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            // These characters are part of the query syntax and must be escaped
            if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' || c == ':'
                    || c == '^' || c == '[' || c == ']' || c == '\"' || c == '{' || c == '}'
                    || c == '~' || c == '*' || c == '?' || c == '|' || c == '&' || c == ';'
                    || Character.isWhitespace(c)) {
                sb.append('\\');
            }
            sb.append(c);
        }
        return sb.toString();
    }
}
