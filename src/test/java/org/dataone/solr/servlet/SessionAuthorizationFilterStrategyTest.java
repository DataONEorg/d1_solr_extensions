package org.dataone.solr.servlet;

import org.dataone.configuration.Settings;
import org.dataone.service.types.v1.Subject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

import org.junit.Test;
import uk.org.webcompere.systemstubs.rules.EnvironmentVariablesRule;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * A Junit test class for the SessionAuthorizationFilterStrategy class
 */
public class SessionAuthorizationFilterStrategyTest {
    public final static String ENV_NAME_D1_CN_URL = "D1_CN_URL";
    public final static String ENV_NAME_CN_ADMINS = "D1_CN_ADMINS"; // Optional. Separated by ;
    public final static String ENV_NAME_CN_SOLR_ADMIN_TOKEN = "D1_CN_SOLR_ADMIN_TOKEN";
    public final static String SETTING_NAME_D1_CN_URL = "D1Client.CN_URL";
    public final static String SETTING_NAME_CN_ADMINS = "cn.administrators";
    public final static String SETTING_NAME_SOLR_ADMIN_TOKEN = "cn.solrAdministrator.token";
    private final static String CN_SANDBOX_URL = "https://cn-sandbox.test.dataone.org/cn";
    private final static String CN_ADMIN1 = "http://orcid.org/0001-0005-9751-12345";
    private final static String CN_ADMIN2 = "http://orcid.org/0001-0005-9751-1234567";
    private final static String CN_ADMINS = CN_ADMIN1 + ";" + CN_ADMIN2;
    private final static String TOKEN = "123456";

    @Rule
    public EnvironmentVariablesRule environmentVariablesRule = new EnvironmentVariablesRule();


    @Before
    public void setUp() {
        Settings.getConfiguration().clearProperty(SETTING_NAME_D1_CN_URL);
        Settings.getConfiguration().clearProperty(SETTING_NAME_CN_ADMINS);
        Settings.getConfiguration().clearProperty(SETTING_NAME_SOLR_ADMIN_TOKEN);
    }

    @After
    public void tearDown() {
        environmentVariablesRule.set(ENV_NAME_D1_CN_URL, null);
        environmentVariablesRule.set(ENV_NAME_CN_ADMINS, null);
        environmentVariablesRule.set(ENV_NAME_CN_SOLR_ADMIN_TOKEN, null);
        Settings.getConfiguration().clearProperty(SETTING_NAME_D1_CN_URL);
        Settings.getConfiguration().clearProperty(SETTING_NAME_CN_ADMINS);
        Settings.getConfiguration().clearProperty(SETTING_NAME_SOLR_ADMIN_TOKEN);
        SessionAuthorizationFilterStrategy.cnClientUrl = null;
        SessionAuthorizationFilterStrategy.cnNodeListUrl = null;
    }
    /**
     * Test the readEnvVariables method
     * @throws Exception
     */
    @Test
    public void testReadEnvVariables() throws Exception {
        // No env variables for the cn url. Default production one will be used
        SessionAuthorizationFilterStrategy.readEnvVariables();
        assertEquals(
            "https://cn.dataone.org/cn",
            Settings.getConfiguration().getString(SETTING_NAME_D1_CN_URL));
        assertNull(Settings.getConfiguration().getString(SETTING_NAME_CN_ADMINS));
        assertNull(Settings.getConfiguration().getString(SETTING_NAME_SOLR_ADMIN_TOKEN));
        // Set the env variables
        SessionAuthorizationFilterStrategy.cnClientUrl = null;
        environmentVariablesRule.set(ENV_NAME_D1_CN_URL, CN_SANDBOX_URL);
        environmentVariablesRule.set(ENV_NAME_CN_ADMINS, CN_ADMINS);
        environmentVariablesRule.set(ENV_NAME_CN_SOLR_ADMIN_TOKEN, TOKEN);
        SessionAuthorizationFilterStrategy.readEnvVariables();
        assertEquals(CN_SANDBOX_URL,
                      Settings.getConfiguration().getString(SETTING_NAME_D1_CN_URL));
        ArrayList<String> admins = new ArrayList<>();
        admins.add(0, CN_ADMIN1);
        admins.add(1, CN_ADMIN2);
        assertEquals(admins, Settings.getConfiguration().getList(SETTING_NAME_CN_ADMINS));
        assertEquals(TOKEN, Settings.getConfiguration().getString(SETTING_NAME_SOLR_ADMIN_TOKEN));
    }

    /**
     * Test the splitTextBySemicolon method
     * @throws Exception
     */
    @Test
    public void testSplitTextBySemicolon() throws Exception {
        String text = null;
        assertNull(SessionAuthorizationFilterStrategy.splitTextBySemicolon(text));
        text = " ";
        assertNull(SessionAuthorizationFilterStrategy.splitTextBySemicolon(text));
        text = CN_ADMINS;
        ArrayList<String> admins = new ArrayList<>();
        admins.add(0, CN_ADMIN1);
        admins.add(1, CN_ADMIN2);
        assertEquals(admins, SessionAuthorizationFilterStrategy.splitTextBySemicolon(text));
        text = "abc";
        ArrayList<String> result = new ArrayList<>();
        result.add(text);
        assertEquals(result, SessionAuthorizationFilterStrategy.splitTextBySemicolon(text));
    }

    /**
     * Test the method of cacheAdministrativeSubjectList with the default production cn.
     * @throws Exception
     */
    @Test
    public void testCacheAdministrativeSubjectList() throws Exception {
        SessionAuthorizationFilterStrategy.cacheAdministrativeSubjectList();
        assertEquals(
            "https://cn.dataone.org/cn/v2/node", SessionAuthorizationFilterStrategy.cnNodeListUrl);
        assertEquals(3, SessionAuthorizationFilterStrategy.cnAdministrativeSubjects.size());
        Subject cnSubject = new Subject();
        cnSubject.setValue("CN=urn:node:CN,DC=dataone,DC=org");
        assertTrue(SessionAuthorizationFilterStrategy.cnAdministrativeSubjects.contains(cnSubject));
        assertEquals(SessionAuthorizationFilterStrategy.mnAdministrativeSubjects.size(),
                     SessionAuthorizationFilterStrategy.mnNodeNameToSubjectsMap.size());
        Subject knbSubject = new Subject();
        knbSubject.setValue("CN=urn:node:KNB,DC=dataone,DC=org");
        Subject adcSubject = new Subject();
        adcSubject.setValue("CN=urn:node:ARCTIC,DC=dataone,DC=org");
        assertTrue(
            SessionAuthorizationFilterStrategy.mnAdministrativeSubjects.contains(knbSubject));
        assertTrue(
            SessionAuthorizationFilterStrategy.mnAdministrativeSubjects.contains(adcSubject));
        assertEquals(adcSubject, SessionAuthorizationFilterStrategy.mnNodeNameToSubjectsMap.get(
            "urn:node:ARCTIC").get(0));
        assertEquals(1, SessionAuthorizationFilterStrategy.mnNodeNameToSubjectsMap.get(
            "urn:node:ARCTIC").size());
        assertEquals(knbSubject, SessionAuthorizationFilterStrategy.mnNodeNameToSubjectsMap.get(
            "urn:node:KNB").get(0));
        assertEquals(1, SessionAuthorizationFilterStrategy.mnNodeNameToSubjectsMap.get(
            "urn:node:KNB").size());
    }

    /**
     * Test the method of cacheAdministrativeSubjectList with some environmental variables settings
     * @throws Exception
     */
    @Test
    public void testCacheAdministrativeSubjectListWithEnvs() throws Exception {
        // Set the env variables
        environmentVariablesRule.set(ENV_NAME_D1_CN_URL, CN_SANDBOX_URL);
        environmentVariablesRule.set(ENV_NAME_CN_ADMINS, CN_ADMINS);
        SessionAuthorizationFilterStrategy.cnClientUrl = null; //Clear the url
        SessionAuthorizationFilterStrategy.cnNodeListUrl = null; //Clear the url
        SessionAuthorizationFilterStrategy.readEnvVariables();
        SessionAuthorizationFilterStrategy.cacheAdministrativeSubjectList();
        assertEquals(
            "https://cn-sandbox.test.dataone.org/cn/v2/node",
            SessionAuthorizationFilterStrategy.cnNodeListUrl);
        assertEquals(5, SessionAuthorizationFilterStrategy.cnAdministrativeSubjects.size());
        Subject cnSubject = new Subject();
        cnSubject.setValue("CN=urn:node:cnSandbox,DC=dataone,DC=org");
        assertTrue(SessionAuthorizationFilterStrategy.cnAdministrativeSubjects.contains(cnSubject));
        Subject cnSubject2 = new Subject();
        cnSubject2.setValue(CN_ADMIN1);
        assertTrue(
            SessionAuthorizationFilterStrategy.cnAdministrativeSubjects.contains(cnSubject2));
        Subject cnSubject3 = new Subject();
        cnSubject3.setValue(CN_ADMIN2);
        assertTrue(
            SessionAuthorizationFilterStrategy.cnAdministrativeSubjects.contains(cnSubject3));
        assertEquals(SessionAuthorizationFilterStrategy.mnAdministrativeSubjects.size(),
                     SessionAuthorizationFilterStrategy.mnNodeNameToSubjectsMap.size());
        Subject subject1 = new Subject();
        subject1.setValue("CN=urn:node:mnSandboxUCSB1,DC=dataone,DC=org");
        Subject subject2 = new Subject();
        subject2.setValue("CN=urn:node:mnSandboxUCSB2,DC=dataone,DC=org");
        assertTrue(
            SessionAuthorizationFilterStrategy.mnAdministrativeSubjects.contains(subject1));
        assertTrue(
            SessionAuthorizationFilterStrategy.mnAdministrativeSubjects.contains(subject2));
        assertEquals(subject1, SessionAuthorizationFilterStrategy.mnNodeNameToSubjectsMap.get(
            "urn:node:mnSandboxUCSB1").get(0));
        assertEquals(1, SessionAuthorizationFilterStrategy.mnNodeNameToSubjectsMap.get(
            "urn:node:mnSandboxUCSB1").size());
        assertEquals(subject2, SessionAuthorizationFilterStrategy.mnNodeNameToSubjectsMap.get(
            "urn:node:mnSandboxUCSB2").get(0));
        assertEquals(1, SessionAuthorizationFilterStrategy.mnNodeNameToSubjectsMap.get(
            "urn:node:mnSandboxUCSB2").size());
    }

}
