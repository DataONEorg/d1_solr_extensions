            /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataone.solr.handler.component;

import java.util.ArrayList;
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


        // if the isAdministrator value of the solrParams is not
        // set or is of 0 length, then do not allow administrative access
        if ( ((isAdministrator != null) && (isAdministrator.length == 0)) || (isAdministrator == null)) {
            // If isAdministator does not have anything filled in, then determine
            // if there is an authorized user, or just a public user
            // in either case, the user has access to all publically readable records
            logger.debug("not an administrative user");
            MultiMapSolrParams convertedSolrParams = new MultiMapSolrParams(solrParams.toMultiMap(solrParams.toNamedList()));

            convertedSolrParams.addParam(CommonParams.FQ, publicFilterString, convertedSolrParams.getMap());

            String[] authorizedSubjects = solrParams.getParams(ParameterKeys.AUTHORIZED_SUBJECTS);


            if ((authorizedSubjects != null) && (authorizedSubjects.length > 0) ) {
                logger.debug("found an authorized user");
                ArrayList<String> authorizedSubjectList = new ArrayList<String>();
                for (int i = 0; i < authorizedSubjects.length; ++i) {
                    // since subjects may have spaces in them, format the string
                    // in quotes
                    authorizedSubjectList.add("\"" + authorizedSubjects[i] + "\"");
                }
                String readPermissionFilterString = "readPermission: " + StringUtils.join(authorizedSubjectList, " OR ");
                convertedSolrParams.addParam(CommonParams.FQ, readPermissionFilterString, convertedSolrParams.getMap());

            } else {
                logger.debug("found a public user");
            }

            req.setParams(convertedSolrParams);
        } else {
            // we need to check the value of the isAdministrator param value
            //
            // the isAdministrator param value should be set by a property
            // not readable by the public, in order to ensure that
            // a request HTTP QUERY does not attempt to gain administrative
            // access by spoofing the parameter

            // if the administratorToken is not set in a properties file
            // then do not allow administrative access
            logger.debug("found an administrative user");
            if (((administratorToken == null) || administratorToken.equalsIgnoreCase(""))
                    || (!isAdministrator[0].equals(administratorToken))) {
                MultiMapSolrParams convertedSolrParams = new MultiMapSolrParams(solrParams.toMultiMap(solrParams.toNamedList()));
                convertedSolrParams.addParam(CommonParams.FQ, publicFilterString, convertedSolrParams.getMap());
                req.setParams(convertedSolrParams);
            }
        }


        super.handleRequestBody(req, rsp);
    }
}
