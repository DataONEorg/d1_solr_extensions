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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
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
import org.dataone.service.cn.impl.v2.CNIdentityLDAPImpl;
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
 * Provide base session authorization behavior. For use by concrete implementations of the
 * SessionAuthorizationFilterStrategy
 *
 * @author sroseboo
 *
 */
public class SessionAuthorizationUtil {

    protected static Log logger = LogFactory.getLog(SessionAuthorizationUtil.class);
    private static final CNIdentityLDAPImpl identityService = new CNIdentityLDAPImpl();

    /**
     * The request attribute under which we store the array of X509Certificate objects representing the certificate
     * chain presented by our client, if any. (as an object of X509Certificate)
     * org.apache.catalina.Globals.CERTIFICATES_ATTR
     */
    private static final String CERTIFICATES_ATTR = "javax.servlet.request.X509Certificate";

    /**
     * The header name from which we get the PEM encoded X509Certificate object presented by our client, if any.
     * org.apache.catalina.valves.SSLValve
     */
    private static final String SSL_CLIENT_CERT_HEADER = "SSL_CLIENT_CERT";

    /**
     * The request attribute under which we store the name of the cipher suite being used on an SSL connection (as an
     * object of type java.lang.String). org.apache.catalina.Globals.CIPHER_SUITE_ATTR
     */
    private static final String CIPHER_SUITE_ATTR = "javax.servlet.request.cipher_suite";

    /**
     * The header name from which we get the cipher string presented by our client, if any.
     * org.apache.catalina.valves.SSLValve
     */
    private static final String SSL_CIPHER_HEADER = "SSL_CIPHER";

    /**
     * The request attribute under which we store the session id being used for this SSL connection (as an object of
     * type java.lang.String).
     */
    private static final String SSL_SESSION_ID_ATTR = "javax.servlet.request.ssl_session";

    /**
     * The header name from which we get the session id being used the apache SSL connection.
     */
    private static final String SSL_SESSIONID_HEADER = "SSL_SESSION_ID";

    /**
     * The request attribute under which we store the key size being used for this SSL connection (as an object of type
     * java.lang.Integer).
     */
    private static final String KEY_SIZE_ATTR = "javax.servlet.request.key_size";

    /**
     * The header name from which we get the key size of the the apache SSL connection.
     */
    private static final String SSL_CIPHER_USER_KEYSIZE_HEADER = "SSL_CIPHER_USEKEYSIZE";
    /**
     * The header name from which we determine if the ssl certificate is valid from the apache SSL connection.
     */
    private static final String SSL_CLIENT_VERIFY_HEADER = "SSL_CLIENT_VERIFY";

    /**
     * mod_header writes "(null)" when the ssl variable is not filled in
     */
    private static final String MOD_HEADER_NULL = "(null)";

    private SessionAuthorizationUtil() {
    }

    public static void handleNoCertificateManagerSession(ProxyServletRequestWrapper proxyRequest,
            ServletResponse response, FilterChain fc) throws ServletException, IOException,
            NotAuthorized {
        // providing no values to the parameters will result in public access
        logger.debug("session is null: default to public");
        fc.doFilter(proxyRequest, response);
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

    /**
     * Create the java ssl attributes needed for dataone authorization by reading headers.
     * The headers are populated by apache before rewrite and proxy
     * 
     * @param request
     * @return boolean
     *
    */
    public static boolean validateSSLAttributes(ProxyServletRequestWrapper request) {
        List<String> attributeNames = Collections.list(request.getAttributeNames());
        boolean rtn = false;

        // do not process any further if the attributes are already included
        // means that the ssl proxy connection to the solr instance is sending
        // correct certificate information to Java
        if (attributeNames.contains(CERTIFICATES_ATTR)) {
            rtn = true;
        } else {
            List<String> headerNames = Collections.list(request.getHeaderNames());
            if (headerNames.contains(SSL_CLIENT_VERIFY_HEADER)) {
                String verify = request.getHeader(SSL_CLIENT_VERIFY_HEADER);
                if ((verify != null) && verify.equals("SUCCESS")) {
                    /* the following code was unabashedly ripped and modified from org.apache.catalina.valves.SSLValve */

                    String x509ClientRequest = request.getHeader(SSL_CLIENT_CERT_HEADER);
                    if ((x509ClientRequest != null) && (!x509ClientRequest.equals(MOD_HEADER_NULL)) && x509ClientRequest.length() > 28) {
                        try {
                            /* mod_header converts the '\n' into ' ' so we have to rebuild the client certificate */
                            x509ClientRequest = x509ClientRequest.replace(' ', '\n');
                            StringBuilder rebuildX509ClientRequest = new StringBuilder();
                            rebuildX509ClientRequest.append("-----BEGIN CERTIFICATE-----\n");
                            rebuildX509ClientRequest.append(x509ClientRequest.substring(28, x509ClientRequest.length() - 26));
                            rebuildX509ClientRequest.append("\n-----END CERTIFICATE-----\n");
                            x509ClientRequest = rebuildX509ClientRequest.toString();
                            // ByteArrayInputStream bais = new ByteArrayInputStream(strcerts.getBytes("UTF-8"));
                            ByteArrayInputStream bais = new ByteArrayInputStream(x509ClientRequest.getBytes(Charset.defaultCharset()));
                            X509Certificate jsseCerts[] = null;

                            /* if we want to use BC or another provider, then we should determine a way
                             to indicate that we do not wish to use the default provider
                             */
                            CertificateFactory cf = CertificateFactory.getInstance("X.509");
                            X509Certificate cert = (X509Certificate) cf.generateCertificate(bais);
                            jsseCerts = new X509Certificate[1];
                            jsseCerts[0] = cert;
                            request.setAttribute(CERTIFICATES_ATTR, jsseCerts);
                            String sslCipherHeader = request.getHeader(SSL_CLIENT_CERT_HEADER);

                            if ((sslCipherHeader != null) && !(sslCipherHeader.equals(MOD_HEADER_NULL))) {
                                request.setAttribute(CIPHER_SUITE_ATTR, sslCipherHeader);
                            }

                            String sslSessionIdHeader = request.getHeader(SSL_SESSIONID_HEADER);
                            if (sslSessionIdHeader != null && !(sslSessionIdHeader.equals(MOD_HEADER_NULL))) {
                                request.setAttribute(SSL_SESSION_ID_ATTR, sslSessionIdHeader);
                            }

                            String sslCipherUserKeySizeHeader = request.getHeader(SSL_CIPHER_USER_KEYSIZE_HEADER);
                            if (sslCipherUserKeySizeHeader != null && !(sslCipherUserKeySizeHeader.equals(MOD_HEADER_NULL))) {
                                request.setAttribute(KEY_SIZE_ATTR, Integer.valueOf(sslCipherUserKeySizeHeader));
                            }
                            rtn = true;
                        } catch (java.security.cert.CertificateException e) {
                            logger.warn("sslValve.certError", e);

                        }

                    }

                }
            }
        }
        return rtn;
    }
}
