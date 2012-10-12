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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.client.auth.CertificateManager;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.cn.servlet.http.ProxyServletRequestWrapper;
import org.dataone.service.cn.impl.v1.CNIdentityLDAPImpl;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.exceptions.NotFound;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;
import org.dataone.service.types.v1.util.AuthUtils;
import org.dataone.service.util.Constants;

/**
 * Provide base session authorization behavior. For use by concrete
 * implementations of the SessionAuthorizationFilterStrategy
 * 
 * @author sroseboo
 * 
 */
public class SessionAuthorizationUtil {

    protected static Log logger = LogFactory.getLog(SessionAuthorizationUtil.class);
    private static final CNIdentityLDAPImpl identityService = new CNIdentityLDAPImpl();

    private SessionAuthorizationUtil() {
    }

    public static void handleNoCertificateManagerSession(ProxyServletRequestWrapper proxyRequest,
            ServletResponse response, FilterChain fc) throws ServletException, IOException,
            NotAuthorized {
        // providing no values to the parameters will result in public access
        logger.debug("session is null: default to public");
        fc.doFilter(proxyRequest, response);
    }

    public static void addSubjectsToRequest(ProxyServletRequestWrapper proxyRequest, Session session) {
        Set<Subject> subjects = AuthUtils.authorizedClientSubjects(session);
        if (subjects.isEmpty() == false) {
            Set<String> subjectValues = new HashSet<String>();
            for (Subject subject : subjects) {
                subjectValues.add(subject.getValue());
            }
            proxyRequest.setParameterValues(ParameterKeys.AUTHORIZED_SUBJECTS,
                    subjectValues.toArray(new String[0]));
        }
    }

    public static void addAuthenticatedSubjectsToRequest(ProxyServletRequestWrapper proxyRequest,
            Session session, Subject authorizedSubject) throws ServiceFailure, NotAuthorized,
            NotImplemented {
        List<String> authorizedSubjects = new ArrayList<String>();
        // add into the list the public subject and authenticated
        // subject psuedo users since they will be indexed as subjects allowable
        // to read
        authorizedSubjects.add(Constants.SUBJECT_PUBLIC);
        authorizedSubjects.add(Constants.SUBJECT_AUTHENTICATED_USER);

        SubjectInfo authorizedSubjectInfo = null;
        try {
            authorizedSubjectInfo = identityService.getSubjectInfo(session, authorizedSubject);
        } catch (NotFound e) {
            // if problem getting the subjectInfo, use the
            // subjectInfo provided with the certificate.

            // XXX if the subject has had all rights revoked or for some reason
            // removed from the system, then this call will allow information
            // provided in the certificate to override changes to the system
            authorizedSubjectInfo = session.getSubjectInfo();
        }
        if (authorizedSubjectInfo == null) {
            String standardizedName = CertificateManager.getInstance().standardizeDN(
                    authorizedSubject.getValue());
            authorizedSubjects.add(standardizedName);
        } else {
            Set<Subject> subjectSet = new HashSet<Subject>();
            AuthUtils.findPersonsSubjects(subjectSet, authorizedSubjectInfo, authorizedSubject);
            for (Subject subject : subjectSet) {
                if (subject != null) {
                    if (Constants.SUBJECT_VERIFIED_USER.equals(subject.getValue())) {
                        authorizedSubjects.add(Constants.SUBJECT_VERIFIED_USER);
                    } else {
                        String standardizedName = CertificateManager.getInstance().standardizeDN(
                                subject.getValue());
                        authorizedSubjects.add(standardizedName);
                    }
                }
            }
        }
        if (!authorizedSubjects.isEmpty()) {
            proxyRequest.setParameterValues(ParameterKeys.AUTHORIZED_SUBJECTS,
                    authorizedSubjects.toArray(new String[0]));
        }
    }

}
