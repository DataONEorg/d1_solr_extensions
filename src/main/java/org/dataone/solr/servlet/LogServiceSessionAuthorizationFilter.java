package org.dataone.solr.servlet;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.cn.servlet.http.ProxyServletRequestWrapper;
import org.dataone.service.exceptions.InvalidToken;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;


public class LogServiceSessionAuthorizationFilter extends SessionAuthorizationFilterStrategy
        implements Filter {

    protected static Log logger = LogFactory.getLog(LogServiceSessionAuthorizationFilter.class);
    @Override
    protected void addAuthenticatedSubjectsToRequest(ProxyServletRequestWrapper proxyRequest,
            Session session, Subject authorizedSubject)
        throws ServiceFailure, NotAuthorized, NotImplemented, InvalidToken {
        logger.debug("solr search filter handling authorized cert with subject " + authorizedSubject.getValue());
        SessionAuthorizationUtil.addAuthenticatedSubjectsToRequest(proxyRequest, session,
                authorizedSubject);
    }

    @Override
    protected void handleNoCertificateManagerSession(ProxyServletRequestWrapper proxyRequest,
            ServletResponse response, FilterChain fc) throws ServletException, IOException,
            NotAuthorized {
        // public is not allowed to see any
        // NotAuthorized noAuth = new NotAuthorized("1460",
        //        "Logging is only available to Authenticated users");
        // throw noAuth;
        
        logger.debug("solr search filter handling no cert.");
        SessionAuthorizationUtil.handleNoCertificateManagerSession(proxyRequest, response, fc);
    }

    @Override
    protected String getServiceMethodName() {
        return "getLogRecords";
    }

}
