/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dataone.solr.servlet;

import java.io.IOException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.security.auth.x500.X500Principal;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.dataone.client.auth.CertificateManager;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.cn.servlet.http.ProxyServletRequestWrapper;
import org.dataone.configuration.Settings;
import org.dataone.service.cn.impl.v1.CNIdentityLDAPImpl;
import org.dataone.service.cn.impl.v1.NodeRegistryService;
import org.dataone.service.exceptions.*;
import org.dataone.service.types.v1.Group;
import org.dataone.service.types.v1.Node;
import org.dataone.service.types.v1.NodeState;
import org.dataone.service.types.v1.NodeType;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;
import org.dataone.service.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pre-filter to SolrDispatchFilter.
 * It sets authorization information in a wrapped request's parameter map
 *
 * A DataONE SolrRequestHandler implementation can then create a filter
 * based on the parameters, since  SolrRequestHandler does not have
 * access to the request attributes where the session is stored
 *
 * @author waltz
 */
public class SessionAuthorizationFilter implements Filter {

    Logger logger = LoggerFactory.getLogger(SessionAuthorizationFilter.class);
    static private DateFormat df = DateFormat.getDateTimeInstance();
    NodeRegistryService nodeRegistryService = new NodeRegistryService();
    CNIdentityLDAPImpl identityService = new CNIdentityLDAPImpl();
    static private List<Subject> administrativeSubjects = new ArrayList<Subject>();
    static String adminToken = Settings.getConfiguration().getString("cn.solrAdministrator.token");
    private long lastRefreshTimeMS = 0L;
    // refresh the nodelist from ldap every 5 minutes
    private long nodelistRefreshIntervalSeconds = 5L * 60L * 1000L;

    /**
     * Initialize the filter by pre-caching a list of administrative subjects
     * 
     * @param fc
     * @throws ServletException
     * @author waltz
     */
    @Override
    public void init(FilterConfig fc) throws ServletException {
        logger.info("init SessionAuthorizationFilter");
        try {
            cacheAdministrativeSubjectList();
        } catch (NotImplemented ex) {
            logger.error(ex.serialize(ex.FMT_XML));
        } catch (ServiceFailure ex) {
            logger.error(ex.serialize(ex.FMT_XML));
        }
        lastRefreshTimeMS = new Date().getTime();
    }

    /*
     * If the session has a certificate, determine the authorized subjects by
     * pulling a subjectInfo from LDAP. Set the subjects in a parameter named
     * authorizedSubjects.
     *
     *
     * The certificate may also be from a CN.  If so, set the isCnAdministrator param to a token.
     *
     * If the request does not have either authorizedSubjects or isCnAdministrator, then it
     * should be considered a public request
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
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain fc) throws IOException, ServletException {
        try {
            logger.debug("Authorization certificate filter");
            String[] emptyValues = {};
            ProxyServletRequestWrapper proxyRequest = new ProxyServletRequestWrapper((HttpServletRequest) request);
            Map proxyMap = proxyRequest.getParameterMap();
            if (proxyMap.containsKey(ParameterKeys.AUTHORIZED_SUBJECTS)) {
                // clear out any unwanted attempts at hacking
                logger.warn("removing attempt at supplying authorized user by client");
                proxyRequest.setParameterValues(ParameterKeys.AUTHORIZED_SUBJECTS, emptyValues);
            }
            if (proxyMap.containsKey(ParameterKeys.IS_CN_ADMINISTRATOR)) {
                // clear out any unwanted attempts at hacking
                logger.warn("removing attempt at supplying authorized administrative user by client");
                proxyRequest.setParameterValues(ParameterKeys.IS_CN_ADMINISTRATOR, emptyValues);
            }
            // check if we have the certificate (session) already
            Session session = CertificateManager.getInstance().getSession((HttpServletRequest) request);
            if (session != null) {
                // we have a authenticated user, maybe an administrator or 
                if (isTimeForRefresh()) {
                    cacheAdministrativeSubjectList();
                }
                Subject authorizedSubject = session.getSubject();
                if (administrativeSubjects.contains(authorizedSubject)) {
                    // set administrative access
                    logger.info("found administrative subject");
                    String[] isAdministrativeSubjectValue = {adminToken};
                    proxyRequest.setParameterValues(ParameterKeys.IS_CN_ADMINISTRATOR, isAdministrativeSubjectValue);
                } else {

                    List<String> authorizedSubjects = new ArrayList<String>();
                    // add into the list the public subject and authenticated subject psuedo users
                    // since they will be indexed as subjects allowable to read
                    authorizedSubjects.add(Constants.SUBJECT_PUBLIC);
                    authorizedSubjects.add(Constants.SUBJECT_AUTHENTICATED_USER);
                    
                    SubjectInfo authorizedSubjectInfo = null;
					try {
						authorizedSubjectInfo = identityService.getSubjectInfo(session, authorizedSubject);
					} catch (NotFound e) {
						// if problem getting the subjectInfo, use the subjectInfo
						// provided with the certificate.  
						authorizedSubjectInfo = session.getSubjectInfo();
					}
					if (authorizedSubjectInfo == null) {
						String standardizedName = CertificateManager.getInstance().standardizeDN(authorizedSubject.getValue());
						authorizedSubjects.add(standardizedName);
					} else {
						// populate the authorizedSubjects list from the subjectInfo.
						if (authorizedSubjectInfo.sizeGroupList() > 0) {
							for (Group authGroup : authorizedSubjectInfo.getGroupList()) {
								try {
									String standardizedName = CertificateManager.getInstance().standardizeDN(authGroup.getSubject().getValue());
									authorizedSubjects.add(standardizedName);
									logger.info("found administrative subject");
								} catch (IllegalArgumentException ex) {
									logger.warn("Found improperly formatted group subject: " + authGroup.getSubject().getValue() + "\n" + ex.getMessage());
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
									String standardizedName = CertificateManager.getInstance().standardizeDN(authPerson.getSubject().getValue());
									authorizedSubjects.add(standardizedName);
								} catch (IllegalArgumentException ex) {
									logger.error("Found improperly formatted person subject: " + authPerson.getSubject().getValue() + "\n" + ex.getMessage());
								}
							}
						}
					}
                    if (!authorizedSubjects.isEmpty()) {
                        proxyRequest.setParameterValues(ParameterKeys.AUTHORIZED_SUBJECTS, authorizedSubjects.toArray(new String[0]));
                    }
                }
                fc.doFilter(proxyRequest, response);
            } else {
                logger.info("session is null: default to public");
                // providing no values to the parameters will result in public access
                fc.doFilter(proxyRequest, response);
            }
        } catch (ServiceFailure ex) {
            ex.setDetail_code("1490");
            String failure = ex.serialize(ex.FMT_XML);
            ((HttpServletResponse) response).setStatus(500);
            response.getOutputStream().write(failure.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (NotAuthorized ex) {
            ex.setDetail_code("1460");
            String failure = ex.serialize(ex.FMT_XML);
            ((HttpServletResponse) response).setStatus(401);
            response.getOutputStream().write(failure.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (NotImplemented ex) {
            ex.setDetail_code("1461");
            String failure = ex.serialize(ex.FMT_XML);
            ((HttpServletResponse) response).setStatus(400);
            response.getOutputStream().write(failure.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (InvalidToken ex) {
            ex.setDetail_code("1470");
            String failure = ex.serialize(ex.FMT_XML);
            ((HttpServletResponse) response).setStatus(401);
            response.getOutputStream().write(failure.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        }
    }

    /*
     * refreshes an array of subjects listed as CN's in the nodelist. 
     * the array is a static class variable
     *
     * @author waltz
     * @throws NotImplemented
     * @throws ServiceFailure
     * @returns void
     */
    public void cacheAdministrativeSubjectList() throws NotImplemented, ServiceFailure {
        administrativeSubjects.clear();

        List<Node> nodeList = nodeRegistryService.listNodes().getNodeList();
        for (Node node : nodeList) {
            if (node.getType().equals(NodeType.CN) && node.getState().equals(NodeState.UP)) {
                administrativeSubjects.addAll(node.getSubjectList());
            }
        }

    }

    /**
     * determines if it is time to refresh the AdministrativeSubjectList derived from nodelist information cache.
     * The refresh interval helps to minimize unnecessary access to LDAP.
     *
     * @author waltz
     * @return boolean.  true if time to refresh
     */
    private Boolean isTimeForRefresh() {
        Date now = new Date();
        long nowMS = now.getTime();

        // convert seconds to milliseconds

        if ((nowMS - this.lastRefreshTimeMS) > nodelistRefreshIntervalSeconds) {
            this.lastRefreshTimeMS = nowMS;
            logger.info("nodelist refresh: new cached time: " + df.format(now));
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
