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
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.cn.servlet.http.ProxyServletRequestWrapper;
import org.dataone.configuration.Settings;
import org.dataone.portal.PortalCertificateManager;
import org.dataone.service.cn.v2.NodeRegistryService;
import org.dataone.service.cn.v2.impl.NodeRegistryServiceImpl;
import org.dataone.service.exceptions.BaseException;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.NodeState;
import org.dataone.service.types.v1.NodeType;
import org.dataone.service.types.v1.Service;
import org.dataone.service.types.v1.ServiceMethodRestriction;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v2.Node;

/**
 * Strategy for a pre-filter to SolrDispatchFilter. The strategy defines how to set authorization information in a
 * wrapped request's parameter map
 *
 * A DataONE SolrRequestHandler implementation can then create a filter based on the parameters, since
 * SolrRequestHandler does not have access to the request attributes where the session is stored
 *
 * @author waltz
 */
public abstract class SessionAuthorizationFilterStrategy implements Filter {

    protected static Log logger = LogFactory.getLog(SessionAuthorizationFilterStrategy.class);

    private static NodeRegistryService nodeRegistryService = new NodeRegistryServiceImpl();
    private static String adminToken = Settings.getConfiguration().getString(
            "cn.solrAdministrator.token");

    private List<Subject> cnAdministrativeSubjects = new ArrayList<Subject>();
    private List<Subject> mnAdministrativeSubjects = new ArrayList<Subject>();
    private List<Subject> serviceMethodRestrictionSubjects = new ArrayList<Subject>();
    private Map<String, List<Subject>> mnNodeNameToSubjectsMap = new HashMap<String, List<Subject>>();
    private long lastRefreshTimeMS = 0L;
    private long nodelistRefreshIntervalSeconds = 5L * 60L * 1000L; // 5 minutes

    /**
     * Allows concrete implementations of SessionAuthorizationFilterStrategy to determine what access (if any) to allow
     * requests that do have session information available from the dataONE CertificateManager.
     *
     * Called from doFilter
     *
     * @param proxyRequest
     * @param response
     * @param filterChain
     * @throws ServletException
     * @throws IOException
     * @throws NotAuthorized
     */
    protected abstract void handleNoCertificateManagerSession(
            ProxyServletRequestWrapper proxyRequest, ServletResponse response,
            FilterChain filterChain) throws ServletException, IOException, NotAuthorized;

    /**
     * Allows concrete implementations of SessionAuthorizationFilterStrategy to determine how/what authenticated
     * subjects are added to the request's parameter values - ParameterKeys.AUTHORIZED_SUBJECTS, as well as if public
     * user and authenticated user constants are provided.
     *
     * Called from doFilter
     *
     * @param proxyRequest
     * @param session
     * @param authorizedSubject
     * @throws ServiceFailure
     * @throws NotAuthorized
     * @throws NotImplemented
     */
    protected abstract void addAuthenticatedSubjectsToRequest(
            ProxyServletRequestWrapper proxyRequest, Session session, Subject authorizedSubject)
            throws ServiceFailure, NotAuthorized, NotImplemented;

    /**
     * The service name to look up for additional admin users defined for the services service method restrictions.
     *
     * @return Name of service.
     */
    protected abstract String getServiceMethodName();

    /**
     * Initialize the filter by pre-caching a list of administrative subjects
     *
     * @param fc
     * @throws ServletException
     * @author waltz
     */
    @Override
    public void init(FilterConfig fc) throws ServletException {
        try {
            logger.debug("about to cache admin");
            cacheAdministrativeSubjectList();
        } catch (NotImplemented ex) {
            logger.error(ex.serialize(BaseException.FMT_XML));
        } catch (ServiceFailure ex) {
            logger.error(ex.serialize(BaseException.FMT_XML));
        }
        lastRefreshTimeMS = new Date().getTime();
        logger.debug("init SessionAuthorizationFilter: " + this.getClass().getName());
    }

    /**
     * The strategy method that defines how and what subjects are added to the request's parameter values.
     *
     * If the session has a certificate, determine the authorized subjects by pulling a subjectInfo from LDAP. Set the
     * subjects in a parameter named authorizedSubjects.
     *
     *
     * The certificate may also be from a CN. If so, set the isCnAdministrator param to a token.
     *
     * If the request does not have either authorizedSubjects or isCnAdministrator, then it should be considered a
     * public request
     *
     * @author waltz
     * @param request
     * @param response
     * @param fc
     * @throws IOException
     * @throws ServletException
     * @returns void
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain fc)
            throws IOException, ServletException {

        logger.debug("SessionAuthorizationFilterStrategy doFilter invoked by: "
                + this.getClass().getName());

        try {
            if (request instanceof HttpServletRequest) {
                String[] emptyValues = {};
                ProxyServletRequestWrapper proxyRequest = new ProxyServletRequestWrapper(
                        (HttpServletRequest) request);

                Map proxyMap = proxyRequest.getParameterMap();
                if (proxyMap.containsKey(ParameterKeys.AUTHORIZED_SUBJECTS)) {
                    // clear out any unwanted attempts at hacking
                    logger.debug("removing attempt at supplying authorized user by client");
                    proxyRequest.setParameterValues(ParameterKeys.AUTHORIZED_SUBJECTS, emptyValues);
                }
                if (proxyMap.containsKey(ParameterKeys.IS_CN_ADMINISTRATOR)) {
                    // clear out any unwanted attempts at hacking
                    logger.debug("removing attempt at supplying authorized administrative user by client");
                    proxyRequest.setParameterValues(ParameterKeys.IS_CN_ADMINISTRATOR, emptyValues);
                }
                if (proxyMap.containsKey(ParameterKeys.IS_MN_ADMINISTRATOR)) {
                    // clear out any unwanted attempts at hacking
                    logger.debug("removing attempt at supplying authorized administrative user by client");
                    proxyRequest.setParameterValues(ParameterKeys.IS_MN_ADMINISTRATOR, emptyValues);
                }

                boolean hasValidSSL = SessionAuthorizationUtil.validateSSLAttributes(proxyRequest);
                logger.debug("valid SSL: " + hasValidSSL);
                if (hasValidSSL) {
                    // check if we have the certificate (session) already
                    Session session = PortalCertificateManager.getInstance().getSession(
                            (HttpServletRequest) request);
                    if (session != null) {
                        // we have a authenticated user, maybe an administrator or
                        if (isTimeForRefresh()) {
                            cacheAdministrativeSubjectList();
                        }
                        Subject authorizedSubject = session.getSubject();
                        logger.debug("Solr Session Auth found subject: "
                                + authorizedSubject.getValue());

                        // The subject may be a CN or a CN administrator, in which case
                        // authorization is granted for full access to records
                        // The subject may be a MN, in which case, depending on the
                        // service,
                        // the records may be filtered in some way...
                        //
                        // For some unknown reason, the endpoint may allow access to
                        // certain subjects
                        // in which case, access is restricted to only those subjects on
                        // the list
                        //
                        // Lastly, the subject may be a valid authorized subject, so
                        // restrict based on the user permissions
                        if (cnAdministrativeSubjects.contains(authorizedSubject)) {
                            // set administrative access
                            logger.debug(authorizedSubject.getValue() + " is a cn administrator");
                            String[] isAdministrativeSubjectValue = { adminToken };
                            proxyRequest.setParameterValues(ParameterKeys.IS_CN_ADMINISTRATOR,
                                    isAdministrativeSubjectValue);
                        } else if (mnAdministrativeSubjects.contains(authorizedSubject)) {
                            for (String mnIdentifier : mnNodeNameToSubjectsMap.keySet()) {
                                List<Subject> mnSubjectList = mnNodeNameToSubjectsMap
                                        .get(mnIdentifier);
                                if (mnSubjectList != null
                                        && mnSubjectList.contains(authorizedSubject)) {
                                    String[] mnAdministratorParamValue = { mnIdentifier };
                                    logger.debug(authorizedSubject.getValue()
                                            + " is a mn administrator");
                                    proxyRequest.setParameterValues(
                                            ParameterKeys.IS_MN_ADMINISTRATOR,
                                            mnAdministratorParamValue);
                                }
                            }
                        } else {
                            if (!serviceMethodRestrictionSubjects.isEmpty()) {
                                if (serviceMethodRestrictionSubjects.contains(authorizedSubject)) {
                                    addAuthenticatedSubjectsToRequest(proxyRequest, session,
                                            authorizedSubject);
                                } else {
                                    logger.debug("Solr Session auth - "
                                            + authorizedSubject.getValue()
                                            + " not found in restricted list");
                                    handleNoCertificateManagerSession(proxyRequest, response, fc);
                                }
                            } else {
                                logger.debug(authorizedSubject.getValue() + " is authorized");
                                addAuthenticatedSubjectsToRequest(proxyRequest, session,
                                        authorizedSubject);
                            }
                        }
                        fc.doFilter(proxyRequest, response);
                    } else {
                        logger.debug("Solr Session auth - NO SESSION");
                        handleNoCertificateManagerSession(proxyRequest, response, fc);
                    }
                } else {
                    logger.debug("Invalidate SSL Attributes");
                    handleNoCertificateManagerSession(proxyRequest, response, fc);
                }
            } else {
                throw new NotImplemented("1461", "ServletRequest is not a HttpServletRequest!?");
            }

        } catch (ServiceFailure ex) {
            ex.setDetail_code("1490");
            String failure = ex.serialize(BaseException.FMT_XML);
            ((HttpServletResponse) response).setStatus(500);
            response.getOutputStream().write(failure.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (NotAuthorized ex) {
            ex.setDetail_code("1460");
            String failure = ex.serialize(BaseException.FMT_XML);
            ((HttpServletResponse) response).setStatus(401);
            response.getOutputStream().write(failure.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (NotImplemented ex) {
            ex.setDetail_code("1461");
            String failure = ex.serialize(BaseException.FMT_XML);
            ((HttpServletResponse) response).setStatus(400);
            response.getOutputStream().write(failure.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (Exception ex) {
            logger.error(ex.getMessage(), ex);
            ServiceFailure sfe = new ServiceFailure("1490", ex.getClass() + ": " + ex.getMessage());
            sfe.setStackTrace(ex.getStackTrace());
            String failure = sfe.serialize(BaseException.FMT_XML);
            ((HttpServletResponse) response).setStatus(500);
            response.getOutputStream().write(failure.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        }
    }

    /*
     * refreshes an array of subjects listed as CN's in the nodelist. the array
     * is a static class variable
     * 
     * @author waltz
     * 
     * @throws NotImplemented
     * 
     * @throws ServiceFailure
     * 
     * @returns void
     */
    private void cacheAdministrativeSubjectList() throws NotImplemented, ServiceFailure {
        cnAdministrativeSubjects.clear();
        mnAdministrativeSubjects.clear();
        serviceMethodRestrictionSubjects.clear();
        List<String> nodeAdministrators = Settings.getConfiguration().getList("cn.administrators");
        if (nodeAdministrators != null) {
            for (String administrator : nodeAdministrators) {
                logger.debug("AdminList property entry " + administrator);
                Subject adminSubject = new Subject();
                adminSubject.setValue(administrator);
                cnAdministrativeSubjects.add(adminSubject);
            }
        }
        List<Node> nodeList = nodeRegistryService.listNodes().getNodeList();
        for (Node node : nodeList) {
            if (node.getType().equals(NodeType.CN) && node.getState().equals(NodeState.UP)) {
                for (Subject subject : node.getSubjectList()) {
                    logger.debug("AdminList entry CN subject: " + subject.getValue());
                }
                cnAdministrativeSubjects.addAll(node.getSubjectList());
                List<Service> cnServices = node.getServices().getServiceList();
                for (Service service : cnServices) {
                    if (service.getName().equalsIgnoreCase("CNCore")) {
                        if ((service.getRestrictionList() != null)
                                && !service.getRestrictionList().isEmpty()) {
                            List<ServiceMethodRestriction> serviceMethodRestrictionList = service
                                    .getRestrictionList();
                            for (ServiceMethodRestriction serviceMethodRestriction : serviceMethodRestrictionList) {
                                if (serviceMethodRestriction.getMethodName().equalsIgnoreCase(
                                        getServiceMethodName())) {
                                    if (serviceMethodRestriction.getSubjectList() != null) {
                                        serviceMethodRestrictionSubjects
                                                .addAll(serviceMethodRestriction.getSubjectList());
                                        for (Subject subject : serviceMethodRestriction
                                                .getSubjectList()) {
                                            logger.debug("AdminList entry CN subject: "
                                                    + subject.getValue());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (node.getType().equals(NodeType.MN) && node.getState().equals(NodeState.UP)) {
                if (node.getSubjectList() != null && !(node.getSubjectList().isEmpty())) {
                    mnAdministrativeSubjects.addAll(node.getSubjectList());
                    mnNodeNameToSubjectsMap.put(node.getIdentifier().getValue(),
                            node.getSubjectList());
                    for (Subject subject : node.getSubjectList()) {
                        logger.debug("AdminList entry CN subject: " + subject.getValue());
                    }
                }
            }
        }
    }

    /**
     * determines if it is time to refresh the AdministrativeSubjectList derived from nodelist information cache. The
     * refresh interval helps to minimize unnecessary access to LDAP.
     *
     * @author waltz
     * @return boolean. true if time to refresh
     */
    private Boolean isTimeForRefresh() {
        long nowMS = System.currentTimeMillis();
        if ((nowMS - this.lastRefreshTimeMS) > nodelistRefreshIntervalSeconds) {
            this.lastRefreshTimeMS = nowMS;
            logger.info("nodelist refreshed");
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void destroy() {
        logger.info("destroy SessionAuthorizationFilter");
    }
}
