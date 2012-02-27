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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dataone.client.auth.CertificateManager;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.cn.servlet.http.ProxyServletRequestWrapper;
import org.dataone.configuration.Settings;
import org.dataone.service.cn.impl.v1.CNIdentityLDAPImpl;
import org.dataone.service.cn.impl.v1.NodeRegistryService;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.Group;
import org.dataone.service.types.v1.Node;
import org.dataone.service.types.v1.NodeState;
import org.dataone.service.types.v1.NodeType;
import org.dataone.service.types.v1.Person;

import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.dataone.service.types.v1.SubjectInfo;

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

    Logger logger =  LoggerFactory.getLogger(SessionAuthorizationFilter.class);
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
                logger.warn("removing attempt at supplying authorized user by client");
                proxyRequest.setParameterValues(ParameterKeys.AUTHORIZED_SUBJECTS, emptyValues);
            }
            if (proxyMap.containsKey(ParameterKeys.IS_CN_ADMINISTRATOR)) {
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
                    String[] isAdministrativeSubjectValue = {adminToken};
                    proxyRequest.setParameterValues(ParameterKeys.IS_CN_ADMINISTRATOR, isAdministrativeSubjectValue );
                } else {

                    List<String> authorizedSubjects = new ArrayList<String>();
                    SubjectInfo authorizedSubjectInfo = identityService.getSubjectInfo(session, authorizedSubject);
                    if (authorizedSubjectInfo.sizeGroupList() > 0) {
                        for (Group authGroup : authorizedSubjectInfo.getGroupList()) {
                            X500Principal principal = new X500Principal(authGroup.getSubject().getValue());
                            String standardizedName = principal.getName(X500Principal.RFC2253);
                            authorizedSubjects.add(standardizedName);
                        }
                    }
                    if (authorizedSubjectInfo.sizePersonList() > 0) {
                        for (Person authPerson : authorizedSubjectInfo.getPersonList()) {
                            X500Principal principal = new X500Principal(authPerson.getSubject().getValue());
                            String standardizedName = principal.getName(X500Principal.RFC2253);
                            authorizedSubjects.add(standardizedName);
                        }
                    }
                    if (!authorizedSubjects.isEmpty()) {
                        proxyRequest.setParameterValues(ParameterKeys.AUTHORIZED_SUBJECTS, authorizedSubjects.toArray(new String[0]));
                    }
                }
                fc.doFilter(proxyRequest, response);
            } else {

                fc.doFilter(proxyRequest, response);
            }
        } catch (ServiceFailure ex) {
            ex.setDetail_code("1490");
            String failure = ex.serialize(ex.FMT_XML);
            response.getOutputStream().write(failure.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (NotAuthorized ex) {
            ex.setDetail_code("1460");
            String failure = ex.serialize(ex.FMT_XML);
            response.getOutputStream().write(failure.getBytes());
            response.getOutputStream().flush();
            response.getOutputStream().close();
        } catch (NotImplemented ex) {
            ex.setDetail_code("1461");
            String failure = ex.serialize(ex.FMT_XML);
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
