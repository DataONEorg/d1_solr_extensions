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


public class LogServiceSessionAuthorizationFilter extends SessionAuthorizationFilterStrategy
        implements Filter {

    @Override
    protected void addAuthenticatedSubjectsToRequest(ProxyServletRequestWrapper proxyRequest,
            Session session, Subject authorizedSubject) throws ServiceFailure, NotAuthorized,
            NotImplemented {
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
