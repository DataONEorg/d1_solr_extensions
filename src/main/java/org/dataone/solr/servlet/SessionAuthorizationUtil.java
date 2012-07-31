package org.dataone.solr.servlet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletResponse;

import org.dataone.client.auth.CertificateManager;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.cn.servlet.http.ProxyServletRequestWrapper;
import org.dataone.service.cn.impl.v1.CNIdentityLDAPImpl;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.exceptions.NotFound;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.Group;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;
import org.dataone.service.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provide base session authorization behavior. For use by concrete
 * implementations of the SessionAuthorizationFilterStrategy
 * 
 * @author sroseboo
 * 
 */
public class SessionAuthorizationUtil {

    private static Logger logger = LoggerFactory.getLogger(SessionAuthorizationUtil.class);
    private static final CNIdentityLDAPImpl identityService = new CNIdentityLDAPImpl();

    private SessionAuthorizationUtil() {
    }

    public static void handleNoCertificateManagerSession(ProxyServletRequestWrapper proxyRequest,
            ServletResponse response, FilterChain fc) throws ServletException, IOException,
            NotAuthorized {
        // providing no values to the parameters will result in public access
        logger.info("session is null: default to public");
        fc.doFilter(proxyRequest, response);
    }

    public static void addAuthenticatedSubjectsToRequest(ProxyServletRequestWrapper proxyRequest,
            Session session, Subject authorizedSubject) throws ServiceFailure, NotAuthorized,
            NotImplemented {
        List<String> authorizedSubjects = new ArrayList<String>();
        // add into the list the public subject and authenticated
        // subject psuedo users
        // since they will be indexed as subjects allowable to read
        authorizedSubjects.add(Constants.SUBJECT_PUBLIC);
        authorizedSubjects.add(Constants.SUBJECT_AUTHENTICATED_USER);

        SubjectInfo authorizedSubjectInfo = null;
        try {
            authorizedSubjectInfo = identityService.getSubjectInfo(session, authorizedSubject);
        } catch (NotFound e) {
            // if problem getting the subjectInfo, use the
            // subjectInfo provided with the certificate.
            authorizedSubjectInfo = session.getSubjectInfo();
        }
        if (authorizedSubjectInfo == null) {
            String standardizedName = CertificateManager.getInstance().standardizeDN(
                    authorizedSubject.getValue());
            authorizedSubjects.add(standardizedName);
        } else {
            // populate the authorizedSubjects list from the
            // subjectInfo.
            if (authorizedSubjectInfo.sizeGroupList() > 0) {
                for (Group authGroup : authorizedSubjectInfo.getGroupList()) {
                    try {
                        String standardizedName = CertificateManager.getInstance().standardizeDN(
                                authGroup.getSubject().getValue());
                        authorizedSubjects.add(standardizedName);
                        logger.info("found administrative subject");
                    } catch (IllegalArgumentException ex) {
                        logger.warn("Found improperly formatted group subject: "
                                + authGroup.getSubject().getValue() + "\n" + ex.getMessage());
                        authorizedSubjects.add(authGroup.getSubject().getValue());
                    }
                }
            }
            if (authorizedSubjectInfo.sizePersonList() > 0) {
                for (Person authPerson : authorizedSubjectInfo.getPersonList()) {
                    if (authPerson.getVerified() != null && authPerson.getVerified()) {
                        authorizedSubjects.add(Constants.SUBJECT_VERIFIED_USER);
                    }

                    try {
                        String standardizedName = CertificateManager.getInstance().standardizeDN(
                                authPerson.getSubject().getValue());
                        authorizedSubjects.add(standardizedName);
                    } catch (IllegalArgumentException ex) {
                        logger.error("Found improperly formatted person subject: "
                                + authPerson.getSubject().getValue() + "\n" + ex.getMessage());
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
