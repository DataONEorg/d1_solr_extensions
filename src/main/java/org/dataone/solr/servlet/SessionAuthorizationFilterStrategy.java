package org.dataone.solr.servlet;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.dataone.cn.servlet.http.ProxyServletRequestWrapper;
import org.dataone.configuration.Settings;
import org.dataone.portal.PortalCertificateManager;
import org.dataone.service.exceptions.BaseException;
import org.dataone.service.exceptions.InvalidToken;
import org.dataone.service.exceptions.NotAuthorized;
import org.dataone.service.exceptions.NotImplemented;
import org.dataone.service.exceptions.ServiceFailure;
import org.dataone.service.types.v1.NodeState;
import org.dataone.service.types.v1.NodeType;
import org.dataone.service.types.v1.Session;
import org.dataone.service.types.v1.Subject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;


/**
 * Strategy for a pre-filter to SolrDispatchFilter. The strategy defines how to set authorization
 * information in a wrapped request's parameter map
 *
 * A DataONE SolrRequestHandler implementation can then create a filter based on the parameters, since
 * SolrRequestHandler does not have access to the request attributes where the session is stored
 *
 * @author waltz
 */
public abstract class SessionAuthorizationFilterStrategy implements Filter {

    protected static Log logger = LogFactory.getLog(SessionAuthorizationFilterStrategy.class);
    protected static List<Subject> cnAdministrativeSubjects = new ArrayList<Subject>();
    protected static List<Subject> mnAdministrativeSubjects = new ArrayList<Subject>();
    protected static List<Subject> serviceMethodRestrictionSubjects = new ArrayList<Subject>();
    protected static Map<String, List<Subject>> mnNodeNameToSubjectsMap = new HashMap<String,
        List<Subject>>();
    private long lastRefreshTimeMS = 0L;
    private long nodelistRefreshIntervalSeconds = 120L * 60L * 1000L; // 2 hours
    protected static String cnClientUrl = null;
    // The cn url which lists the nodes registered in cn. It will be cnClientUrl + "/v2/node"
    protected static String cnNodeListUrl = null;

    public final static String ENV_NAME_CN_SOLR_ADMIN_TOKEN = "D1_CN_SOLR_ADMIN_TOKEN";
    private final static String ENV_NAME_D1_CN_URL = "D1_CN_URL";
    private final static String ENV_NAME_CN_ADMINS = "D1_CN_ADMINS"; // Optional. Separated by ;
    public final static String SETTING_NAME_SOLR_ADMIN_TOKEN = "cn.solrAdministrator.token";
    private final static String SETTING_NAME_D1_CN_URL = "D1Client.CN_URL";
    private final static String SETTING_NAME_CN_ADMINS = "cn.administrators";
    private final static String DEFAULT_CN_URL = "https://cn.dataone.org/cn";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
                                                    .connectTimeout(Duration.ofSeconds(10)).build();

    private static String adminToken = null;


    /**
     * Allows concrete implementations of SessionAuthorizationFilterStrategy to determine
     * what access (if any) to allow
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
        throws ServiceFailure, NotAuthorized, NotImplemented, InvalidToken;

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
        readEnvVariables();
        adminToken = Settings.getConfiguration().getString(SETTING_NAME_SOLR_ADMIN_TOKEN);
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
     * Read the environmental variables and set them in the settings
     */
    static protected void readEnvVariables() {
        getCnClientUrl();// Set cnClientUrl from an env variable if it is null
        String cnAdminsStr = System.getenv(ENV_NAME_CN_ADMINS);
        if (cnAdminsStr != null && !cnAdminsStr.isBlank()) {
            List<String> cnAdmins = splitTextBySemicolon(cnAdminsStr);
            if (cnAdmins != null) {
                Settings.getConfiguration().setProperty(SETTING_NAME_CN_ADMINS, cnAdmins);
                logger.debug("Set " + cnAdmins + " to the setting " + SETTING_NAME_CN_ADMINS);
            }
        }
        String solrAdminToken = System.getenv(ENV_NAME_CN_SOLR_ADMIN_TOKEN);
        if (solrAdminToken != null && !solrAdminToken.isBlank()) {
            Settings.getConfiguration().setProperty(SETTING_NAME_SOLR_ADMIN_TOKEN, solrAdminToken);
            logger.debug("Set token to the setting " + SETTING_NAME_SOLR_ADMIN_TOKEN);
        } else {
            logger.warn("The env variable value of " + ENV_NAME_CN_SOLR_ADMIN_TOKEN + " is null "
                            + "and please set the env variable if you want to enable the CN "
                            + "subject (e.g. \"CN=urn:node:CN,DC=dataone,DC=org\") to be the solr"
                            + " administrator.");
        }
    }

    /**
     * Get the cnClientUrl. If it is not null, just return it. If it is null, try to read it from
     * an environmental variable; if it cannot be read, set it to the default value, which is the
     * production cn. And also set the setting value with it.
     */
    public static void getCnClientUrl() {
        if (cnClientUrl == null || cnClientUrl.isBlank()) {
            cnClientUrl = System.getenv(ENV_NAME_D1_CN_URL);
            if (cnClientUrl == null || cnClientUrl.isBlank()) {
                logger.debug("Cannot get the cnClientUrl from the env variable " + ENV_NAME_D1_CN_URL
                                 + " . So set it with the default value " + DEFAULT_CN_URL);
                cnClientUrl = DEFAULT_CN_URL;
            }
            Settings.getConfiguration().setProperty(SETTING_NAME_D1_CN_URL, cnClientUrl);
            logger.debug("Set " + cnClientUrl + " to the setting " + SETTING_NAME_D1_CN_URL);
        }
    }

    /**
     * Set the cnNodeListUrl base on cnClientUrl
     */
    public static void setCnNodeListUrl() {
        if (cnNodeListUrl == null || cnNodeListUrl.isBlank()) {
            getCnClientUrl();
            if (cnClientUrl.endsWith("/")) {
                cnNodeListUrl = cnClientUrl + "v2/node";
            } else {
                cnNodeListUrl = cnClientUrl + "/v2/node";
            }
        }
    }

    /**
     * Split the text which semicolon separates into a list
     * @param text  the text will be parsed
     * @return a list string. Null is returned if the text is null or blank
     */
    static protected List<String> splitTextBySemicolon(String text) {
        ArrayList<String> list = null;
        if (text != null && !text.isBlank()) {
            list = new ArrayList<>();
            String[] parts = text.split(";");
            for (String part : parts) {
                String value = part.trim();
                if (!value.isEmpty()) {
                    logger.debug("add "+ value);
                    list.add(value);
                }
            }
        }
        return list;
    }

    /**
     * A util method to get response input stream from the given url
     * @param url  the url will be sent the request
     * @return the response input stream
     * @throws ServiceFailure
     */
    public static InputStream getResponse(String url) throws ServiceFailure {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url)).GET().build();
        HttpResponse<InputStream> response = null;
        try {
            response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException e) {
            throw new ServiceFailure("0000", "Cannot get the response from " + url + " since "
                + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceFailure("0000", "Cannot get the response from " + url + " since "
                + e.getMessage());
        }
        return response.body();
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
                            if (adminToken != null && !adminToken.isBlank()) {
                                String[] isAdministrativeSubjectValue = { adminToken };
                                proxyRequest.setParameterValues(ParameterKeys.IS_CN_ADMINISTRATOR,
                                                                isAdministrativeSubjectValue);
                            } else {
                                logger.warn("The solr admin token is not set by the env variable "
                                                + ENV_NAME_CN_SOLR_ADMIN_TOKEN
                                                + ". So the cn access is disabled.");
                            }
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
    protected static void cacheAdministrativeSubjectList() throws NotImplemented, ServiceFailure {
        cnAdministrativeSubjects.clear();
        mnAdministrativeSubjects.clear();
        serviceMethodRestrictionSubjects.clear();
        mnNodeNameToSubjectsMap.clear();
        List<String> nodeAdministrators = Settings.getConfiguration().getList("cn.administrators");
        if (nodeAdministrators != null) {
            for (String administrator : nodeAdministrators) {
                logger.debug("AdminList property entry " + administrator);
                Subject adminSubject = new Subject();
                adminSubject.setValue(administrator);
                cnAdministrativeSubjects.add(adminSubject);
            }
        }
        // Parse the node information from the result of the cnNodeUrl
        try {
            setCnNodeListUrl();
            logger.debug("The cn node list url is " + cnNodeListUrl);
            try (InputStream response = getResponse(cnNodeListUrl)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(response);
                // Normalize the XML structure
                doc.getDocumentElement().normalize();
                // Get all <node> elements
                Element root = doc.getDocumentElement(); // nodeList
                NodeList kids = root.getChildNodes();
                for (int i = 0; i < kids.getLength(); i++) {
                    Node nodeItem = kids.item(i);
                    if (nodeItem.getNodeType() == Node.ELEMENT_NODE && "node".equals(
                        nodeItem.getNodeName())) {
                        Element nodeElement = (Element) nodeItem;
                        String identifier = getTextContent(nodeElement, "identifier");
                        String type = nodeElement.getAttribute("type");
                        String state = nodeElement.getAttribute("state");
                        // Filter for cn/up or mn/up
                        if (state != null && state.equals(NodeState.UP.xmlValue())) {
                            // Get all subjects
                            NodeList children = nodeElement.getChildNodes();
                            List<Subject> subjectList = new ArrayList<>();
                            for (int j = 0; j < children.getLength(); j++) {
                                Node child = children.item(j);
                                if (child.getNodeType() == Node.ELEMENT_NODE && "subject".equals(
                                    child.getNodeName())) {
                                    String subjectStr = child.getTextContent();
                                    Subject subject = new Subject();
                                    subject.setValue(subjectStr);
                                    subjectList.add(subject);
                                    logger.debug(
                                        "Find the subject " + subjectStr + " for node " + identifier);                            }
                            }
                            if (!subjectList.isEmpty() && type != null) {
                                if (type.equals(NodeType.CN.xmlValue())) {
                                    logger.debug("Put all found subjects for CN " + identifier + " "
                                                     + "into the cn admin subject list.");
                                    cnAdministrativeSubjects.addAll(subjectList);
                                } else if (type.equals(NodeType.MN.xmlValue())) {
                                    logger.debug("Put all found subjects for MN " + identifier + " "
                                                     + "into the mn admin subject list.");
                                    mnAdministrativeSubjects.addAll(subjectList);
                                    mnNodeNameToSubjectsMap.put(identifier, subjectList);
                                }
                            }
                        }
                    }
                }
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new ServiceFailure("0000", e.getMessage());
        }
    }

    /* Helper method to get text content of a child element*/
    private static String getTextContent(Element parent, String tagName) {
        NodeList list = parent.getElementsByTagName(tagName);
        if (list.getLength() > 0) {
            return list.item(0).getTextContent();
        }
        return "";
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
