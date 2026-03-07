package org.dataone.solr.servlet;

import org.dataone.configuration.Settings;
import org.junit.After;
import org.junit.Rule;

import org.junit.Test;
import uk.org.webcompere.systemstubs.rules.EnvironmentVariablesRule;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * A Junit test class for the SessionAuthorizationFilterStrategy class
 */
public class SessionAuthorizationFilterStrategyTest {
    private final static String ENV_NAME_D1_CN_URL = "D1_CN_URL";
    private final static String ENV_NAME_CN_ADMINS = "D1_CN_ADMINS"; // Optional. Separated by ;
    private final static String SETTING_NAME_D1_CN_URL = "D1Client.CN_URL";
    private final static String SETTING_NAME_CN_ADMINS = "cn.administrators";
    private final static String CN_SANDBOX_URL = "https://cn-sandbox.test.dataone.org/cn";
    private final static String CN_ADMIN1 = "http://orcid.org/0001-0005-9751-12345";
    private final static String CN_ADMIN2 = "http://orcid.org/0001-0005-9751-1234567";
    private final static String CN_ADMINS = CN_ADMIN1 + ";" + CN_ADMIN2;

    @Rule
    public EnvironmentVariablesRule environmentVariablesRule = new EnvironmentVariablesRule();


    @After
    public void tearDown() {
        environmentVariablesRule.set(ENV_NAME_D1_CN_URL, null);
        environmentVariablesRule.set(ENV_NAME_CN_ADMINS, null);
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
        // Set the env variables
        environmentVariablesRule.set(ENV_NAME_D1_CN_URL, CN_SANDBOX_URL);
        environmentVariablesRule.set(ENV_NAME_CN_ADMINS, CN_ADMINS);
        SessionAuthorizationFilterStrategy.readEnvVariables();
        assertEquals(CN_SANDBOX_URL,
                      Settings.getConfiguration().getString(SETTING_NAME_D1_CN_URL));
        ArrayList<String> admins = new ArrayList<>();
        admins.add(0, CN_ADMIN1);
        admins.add(1, CN_ADMIN2);
        assertEquals(admins, Settings.getConfiguration().getList(SETTING_NAME_CN_ADMINS));
    }

}
