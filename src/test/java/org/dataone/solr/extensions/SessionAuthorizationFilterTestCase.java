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

package org.dataone.solr.extensions;


import static org.junit.Assert.*;
import static org.junit.matchers.JUnitMatchers.*;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
//import org.springframework.test.web.*;
import java.util.Map;
import javax.annotation.Resource;

import org.junit.Before;
import org.junit.Test;


import javax.servlet.*;
import javax.servlet.http.*;
//import org.dataone.cn.ldap.v1.NodeLdapPopulation;
import org.dataone.client.auth.CertificateManager;
import org.dataone.cn.auth.X509CertificateGenerator;
import org.dataone.cn.ldap.v1.NodeLdapPopulation;
import org.dataone.cn.ldap.v1.SubjectLdapPopulation;
import org.dataone.cn.servlet.http.BufferedServletResponseWrapper;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.cn.web.mock.MockServlet;
import org.dataone.cn.web.mock.MockWebApplicationContextLoader;
import org.dataone.configuration.Settings;
import org.dataone.solr.servlet.SessionAuthorizationFilter;
import org.junit.After;
import org.junit.runner.RunWith;


import org.springframework.core.io.FileSystemResourceLoader;  //FileSystemResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.mock.web.*;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

/**
 * Unit test for simple App.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = {"classpath:/webapp/mockController-dispatcher.xml", "classpath:/webapp/mockController-beans.xml"}, loader = MockWebApplicationContextLoader.class)
public class SessionAuthorizationFilterTestCase
{
    private NodeLdapPopulation cnLdapPopulation;
    private SubjectLdapPopulation subjectLdapPopulation;
    private X509CertificateGenerator x509CertificateGenerator;
    private String primarySubject = Settings.getConfiguration().getString("testIdentity.primarySubject");
    private String  administratorToken = Settings.getConfiguration().getString("cn.solrAdministrator.token");
//    private NodeLdapPopulation cnLdapPopulation;


    @Resource
    public void setCNLdapPopulation(NodeLdapPopulation ldapPopulation) {
        this.cnLdapPopulation = ldapPopulation;
    }
    @Resource
    public void setCNLdapPopulation(SubjectLdapPopulation subjectLdapPopulation) {
        this.subjectLdapPopulation = subjectLdapPopulation;
    }
    @Resource
    public void setX509CertificateGenerator(X509CertificateGenerator x509CertificateGenerator) {
        this.x509CertificateGenerator = x509CertificateGenerator;
    }
    @Before
    public void before() throws Exception {
        cnLdapPopulation.populateTestCN();
        subjectLdapPopulation.populateTestIdentities();
    }
    @After
    public void after() throws Exception {
        cnLdapPopulation.deletePopulatedNodes();
        subjectLdapPopulation.deletePopulatedSubjects();
    }

    @Test
    public void testInit() {

        // Init reads in the parameters from webapp configuration file
        // (not caching the nodelist anymore - no reason to...)
        MockFilterConfig fc = new MockFilterConfig(MockWebApplicationContextLoader.SERVLET_CONTEXT, "ResolveFilter");

        SessionAuthorizationFilter saf = new SessionAuthorizationFilter();
        try {
            saf.init(fc);
        } catch (ServletException se) {
            //se.printStackTrace();
            fail("servlet exception at ResolveFilter.init(fc)");
        }

    }
    // test the default case that a certificate is not passed in
    // no parameters should be set indicating public access only
    @Test
    public void testDoPublicFilter() throws FileNotFoundException {

        HashMap<String, String[]> params = new HashMap<String, String[]>();
        BufferedServletResponseWrapper responseWrapper = callDoFilter("/cn/v1/log", params, null);

        // examine contents of the response
        assertTrue("response should be null", responseWrapper.getBufferSize() == 0);
        assertTrue("response should be 0 length", responseWrapper.getBuffer().length == 0);

    }

    // make certain that if someone tries to pass in authorization via parameters
    // that the parameters are scrubbed
    @Test
    public void testDoPublicWipeFilter() throws FileNotFoundException {

        HashMap<String, String[]> params = new HashMap<String, String[]>();
        String[] values ={"cn=testtest,dc=Ddataone,dc=org"};
        params.put(ParameterKeys.AUTHORIZED_SUBJECTS, values);
        params.put(ParameterKeys.IS_CN_ADMINISTRATOR, values);
        BufferedServletResponseWrapper responseWrapper = callDoFilter("/cn/v1/log?" +ParameterKeys.AUTHORIZED_SUBJECTS + "=cn%3Dtesttest,dc%3Ddataone,dc%3Dorg", params, null );

        // examine contents of the response
        assertTrue("response should be null", responseWrapper.getBufferSize() == 0);
        assertTrue("response should be 0 length", responseWrapper.getBuffer().length == 0);

    }
    // pass in a certificate that contains a subject that
    // can be authorized in LDAP
    @Test
    public void testDoAuthorizedSubjectFilter() throws Exception {
        x509CertificateGenerator.storeSelfSignedCertificate(Settings.getConfiguration().getString("testIdentity.primarySubjectCN"));
        X509Certificate certificate[] = {CertificateManager.getInstance().loadCertificate()};
        HashMap<String, String[]> params = new HashMap<String, String[]>();
        BufferedServletResponseWrapper responseWrapper = callDoFilter("/cn/v1/log", params, certificate);

        // examine contents of the response
        assertTrue("response is non-null", responseWrapper.getBufferSize() > 0);
        assertTrue("response is non-null", responseWrapper.getBuffer().length > 0);

        String content = new String(responseWrapper.getBuffer());
        
        assertThat("response should contain " + primarySubject, content, containsString(primarySubject));
    }
    // pass in a token that has administrative permission
    // only CNs have administrative rights to logging
    @Test
    public void testDoAdministrativeSubjectFilter() throws  Exception {
        x509CertificateGenerator.storeSelfSignedCertificate(Settings.getConfiguration().getString("testIdentity.adminSubjectCN"));
        X509Certificate certificate[] = {CertificateManager.getInstance().loadCertificate()};
        HashMap<String, String[]> params = new HashMap<String, String[]>();
        BufferedServletResponseWrapper responseWrapper = callDoFilter("/cn/v1/log", params, certificate);

        // examine contents of the response
        assertTrue("response is non-null", responseWrapper.getBufferSize() > 0);
        assertTrue("response is non-null", responseWrapper.getBuffer().length > 0);

        String content = new String(responseWrapper.getBuffer());

        assertThat("response should contain the admin token " + administratorToken, content, containsString(administratorToken));

    }
    // ==========================================================================================================
    private BufferedServletResponseWrapper callDoFilter(String url, Map<String, String[]> params, X509Certificate certificate[]) {

        ResourceLoader fsrl = new FileSystemResourceLoader();
        ServletContext sc = new MockServletContext("src/test/webapp", fsrl);
        MockFilterConfig fc = new MockFilterConfig(MockWebApplicationContextLoader.SERVLET_CONTEXT, "SessionAuthorizationFilter");

        SessionAuthorizationFilter saf = new SessionAuthorizationFilter();
        try {
            saf.init(fc);
        } catch (ServletException se) {
            //se.printStackTrace();
            fail("servlet exception at ResolveFilter.init(fc)");
        }

        MockHttpServletRequest request = new MockHttpServletRequest(fc.getServletContext(), "GET", url);
        request.addHeader("accept", (Object) "text/xml");
        if (certificate != null) {
        request.setAttribute("javax.servlet.request.X509Certificate", certificate);
        }
        if (!params.isEmpty()) {
            request.addParameters(params);
        }
        MockServlet testServlet = new MockServlet();

        FilterChain chain = new PassThroughFilterChain(testServlet);

        HttpServletResponse response = new MockHttpServletResponse();
        // need to wrap the response to examine
        BufferedServletResponseWrapper responseWrapper =
                new BufferedServletResponseWrapper((HttpServletResponse) response);

        try {
            saf.doFilter(request, responseWrapper, chain);
        } catch (ServletException se) {
            fail("servlet exception at ResolveFilter.doFilter(): " + se);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            fail("IO exception at ResolveFilter.doFilter(): " + ioe);
        }
        return responseWrapper;
    }

}
