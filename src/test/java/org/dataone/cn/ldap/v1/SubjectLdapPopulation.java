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

package org.dataone.cn.ldap.v1;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.configuration.Settings;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ldap.core.ContextMapper;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.core.DnParser;
import org.springframework.ldap.core.DnParserImpl;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.ldap.core.ParseException;
import org.springframework.ldap.core.support.AbstractContextMapper;
import org.springframework.ldap.filter.AndFilter;
import org.springframework.ldap.filter.EqualsFilter;
import org.springframework.stereotype.Component;

/**
 *
 * @author waltz
 */
@Component
@Qualifier("subjectLdapPopulation")
public class SubjectLdapPopulation {

    public static List<String> testSubjectList = new ArrayList<String>();
    public static Log log = LogFactory.getLog(SubjectLdapPopulation.class);
    private String primarySubjectCN = Settings.getConfiguration().getString("testIdentity.primarySubjectCN");
    private String primarySubject = Settings.getConfiguration().getString("testIdentity.primarySubject");

    static {
        // Need this or context will lowercase all the rdn s
        System.setProperty(DistinguishedName.KEY_CASE_FOLD_PROPERTY, DistinguishedName.KEY_CASE_FOLD_UPPER);
    }
    @Autowired
    @Qualifier("ldapTemplate")
    private LdapTemplate ldapTemplate;

    public void populateTestIdentities() {
        Subject testSubject1 = new Subject();
        testSubject1.setValue(primarySubject);
        Person testPerson1 = new Person();
        testPerson1.setSubject(testSubject1);
        testPerson1.addGivenName("Testest");
        testPerson1.setFamilyName(primarySubjectCN);
        testPerson1.addEmail("testt@nothing.info");
        testPerson1.setVerified(Boolean.TRUE);
        // because we use a base DN, only need to supply the RDN
        DistinguishedName dn1 = new DistinguishedName();
        dn1.add("dc", "dataone");
        dn1.add("cn", primarySubjectCN);

        DirContextAdapter context1 = new DirContextAdapter(dn1);
        mapPersonToContext(testPerson1, context1);
        ldapTemplate.bind(dn1, context1, null);
        testSubjectList.add(dn1.toCompactString());

    }

    protected void mapPersonToContext(Person person, DirContextOperations context) {
        context.setAttributeValues("objectclass", new String[]{"top", "person", "organizationalPerson", "inetOrgPerson", "d1Principal"});
        context.setAttributeValue("cn", person.getFamilyName());
        context.setAttributeValue("sn", person.getFamilyName());
        context.setAttributeValues("givenName", person.getGivenNameList().toArray());
        context.setAttributeValues("mail", person.getEmailList().toArray());
        context.setAttributeValue("isVerified", Boolean.toString(person.getVerified()).toUpperCase());

    }

    public void deletePopulatedSubjects() {
        for (String subject : testSubjectList) {
            try {
                deleteSubject(subject);
            } catch (ParseException ex) {
                log.error("Deleting Subject failed: " + ex.getMessage() + " for " + subject);
            }
        }
        testSubjectList.clear();
    }

    private void deleteSubject(String subject) throws ParseException {
        ByteArrayInputStream subjectBytes = new ByteArrayInputStream(subject.getBytes());
        DnParser dnParser = new DnParserImpl(subjectBytes);

        DistinguishedName dn = dnParser.dn();
        DistinguishedName org = new DistinguishedName();
        org.add("DC", "org");
        if (dn.startsWith(org)) {
            dn.removeFirst();
        }
        ldapTemplate.unbind(dn);
    }

    public void deleteReservation(String pid) {
        AndFilter filter = new AndFilter();
        filter.and(new EqualsFilter("objectClass", "d1Reservation"));
        filter.and(new EqualsFilter("identifier", pid));

        List allDns = ldapTemplate.search(DistinguishedName.EMPTY_PATH, filter.encode(), getDNContextMapper());
        for (Object o : allDns) {
            DistinguishedName dn = (DistinguishedName) o;
            log.info("DELETE RESERVATION: " + dn.toCompactString());
            ldapTemplate.unbind(dn);
        }
    }

    protected ContextMapper getDNContextMapper() {
        return new DnContextMapper();
    }

    private static class DnContextMapper extends AbstractContextMapper {

        public Object doMapFromContext(DirContextOperations context) {
            DistinguishedName dn = new DistinguishedName(context.getDn());

            return dn;
        }
    }
}
