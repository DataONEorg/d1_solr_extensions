package org.dataone.solr.servlet;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;

import org.dataone.cn.servlet.http.ProxyServletRequestWrapper;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;

public class SearchServiceSessionAuthorizationFilter extends SessionAuthorizationFilterStrategy
        implements Filter {

    protected void handleNoCertificateManagerSession(ProxyServletRequestWrapper proxyRequest,
            ServletResponse response, FilterChain fc) throws ServletException, IOException,
            NotAuthorized {
        SessionAuthorizationUtil.handleNoCertificateManagerSession(proxyRequest, response, fc);
    }

    protected void addAuthenticatedSubjectsToRequest(ProxyServletRequestWrapper proxyRequest,
            Session session, Subject authorizedSubject) throws ServiceFailure, NotAuthorized,
            NotImplemented {
        SessionAuthorizationUtil.addAuthenticatedSubjectsToRequest(proxyRequest, session,
                authorizedSubject);
    }

    protected String getServiceMethodName() {
        return "search";
    }

}
