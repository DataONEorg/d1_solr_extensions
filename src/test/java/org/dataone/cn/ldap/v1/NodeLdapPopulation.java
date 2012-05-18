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

import java.util.ArrayList;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dataone.configuration.Settings;
import org.dataone.service.types.v1.Node;
import org.dataone.service.types.v1.NodeReference;
import org.dataone.service.types.v1.NodeState;
import org.dataone.service.types.v1.NodeType;
import org.dataone.service.types.v1.Person;
import org.dataone.service.types.v1.Service;
import org.dataone.service.types.v1.ServiceMethodRestriction;
import org.dataone.service.types.v1.Services;
import org.dataone.service.types.v1.Subject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.ldap.core.DirContextAdapter;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.ldap.core.DistinguishedName;
import org.springframework.ldap.core.LdapTemplate;
import org.springframework.stereotype.Component;

/**
 *
 * @author waltz
 */
@Component
@Qualifier("nodeLdapPopulation")
public class NodeLdapPopulation {

    public static List<Node> testNodeList = new ArrayList<Node>();
    public static List<Subject> testSubjectList = new ArrayList<Subject>();
    public static Log log = LogFactory.getLog(NodeLdapPopulation.class);
    private String primarySubject = Settings.getConfiguration().getString("testIdentity.primarySubject");
    private String adminSubject = Settings.getConfiguration().getString("testIdentity.adminSubject");
    static {
        // Need this or context will lowercase all the rdn s
        System.setProperty(DistinguishedName.KEY_CASE_FOLD_PROPERTY, DistinguishedName.KEY_CASE_FOLD_NONE);
    }
    @Autowired
    @Qualifier("ldapTemplate")
    private LdapTemplate ldapTemplate;


    protected void mapNodeToContext(Node node, DirContextOperations context) {

        context.setAttributeValue("objectclass", "device");
        context.setAttributeValue("objectclass", "d1Node");
        context.setAttributeValue("cn", node.getIdentifier().getValue());
        context.setAttributeValue("d1NodeId", node.getIdentifier().getValue());
        context.setAttributeValue("d1NodeName", node.getName());
        context.setAttributeValue("d1NodeDescription", node.getDescription());
        context.setAttributeValue("d1NodeBaseURL", node.getBaseURL());
        context.setAttributeValue("d1NodeReplicate", Boolean.toString(node.isReplicate()).toUpperCase());
        context.setAttributeValue("d1NodeSynchronize", Boolean.toString(node.isSynchronize()).toUpperCase());
        context.setAttributeValue("d1NodeType", node.getType().xmlValue());
        context.setAttributeValue("d1NodeState", node.getState().xmlValue());
        context.setAttributeValue("d1NodeApproved", Boolean.toString(Boolean.TRUE).toUpperCase());
        context.setAttributeValue("subject", node.getSubject(0).getValue());
        context.setAttributeValue("d1NodeContactSubject", node.getContactSubject(0).getValue());
    }
    protected void mapServiceToContext(org.dataone.service.types.v1.Service service, String nodeId, String nodeServiceId, DirContextOperations context) {
        context.setAttributeValue("objectclass", "d1NodeService");
        context.setAttributeValue("d1NodeServiceId", nodeServiceId);
        context.setAttributeValue("d1NodeId", nodeId);

        context.setAttributeValue("d1NodeServiceName", service.getName());
        context.setAttributeValue("d1NodeServiceVersion", service.getVersion());
        context.setAttributeValue("d1NodeServiceAvailable", Boolean.toString(service.getAvailable()).toUpperCase());
    }
    protected void mapServiceMethodRestriction(ServiceMethodRestriction restrict, String nodeId, String nodeServiceId, DirContextOperations context) {
        
        context.setAttributeValue("objectclass", "d1ServiceMethodRestriction");
        context.setAttributeValue("d1NodeServiceId", nodeServiceId);
        context.setAttributeValue("d1NodeId", nodeId);
        context.setAttributeValue("d1ServiceMethodName", restrict.getMethodName());

        if (restrict.getSubjectList() != null && !(restrict.getSubjectList().isEmpty())) {
            for (Subject subject : restrict.getSubjectList()) {
                context.setAttributeValue("d1AllowedSubject", subject.getValue());
            }
        }
    }
    public void deletePopulatedNodes() {
        for (Node node : testNodeList) {
            if ((node.getServices() != null) && (!node.getServices().getServiceList().isEmpty())) {
                for (Service service : node.getServices().getServiceList()) {
                    if (service.getRestrictionList() != null) {
                         for (ServiceMethodRestriction restrict: service.getRestrictionList()) {
                             deleteNodeServiceRestriction(node, service, restrict);
                         }
                    } else {
                        log.error("ServiceMethodRestriction is NULL!!!" + node.getName() + ":" + service.getName());
                    }
                    deleteNodeService(node, service);
                }
            }
            deleteNode(node);
        }
        testNodeList.clear();
    }

    private void deleteNode(Node node) {
        DistinguishedName dn = new DistinguishedName();
        dn.add("dc","dataone");
        dn.add("cn", node.getIdentifier().getValue());
        log.info("deleting : " + dn.toString());
        ldapTemplate.unbind(dn);
    }
    private void deleteNodeService(Node node, Service service) {
        String d1NodeServiceId = service.getName() + "-" + service.getVersion();
        DistinguishedName dn = new DistinguishedName();
        dn.add("dc","dataone");
        dn.add("cn", node.getIdentifier().getValue());
        dn.add("d1NodeServiceId",d1NodeServiceId);
        log.info("deleting : " + dn.toString());
        ldapTemplate.unbind(dn);
    }
    private void deleteNodeServiceRestriction(Node node, Service service, ServiceMethodRestriction restrict) {
        String d1NodeServiceId = service.getName() + "-" + service.getVersion();
        DistinguishedName dn = new DistinguishedName();
        dn.add("dc","dataone");
        dn.add("cn", node.getIdentifier().getValue());
        dn.add("d1NodeServiceId",d1NodeServiceId);
        dn.add("d1ServiceMethodName", restrict.getMethodName());

        log.info("deleting : " + dn.toString());
        ldapTemplate.unbind(dn);
    }
    public void populateTestCN() {

        Node sqrmCNNode = new Node();
        String sqrmId = "urn:node:sqrm1";
        NodeReference sq1dNodeReference = new NodeReference();
        sq1dNodeReference.setValue(sqrmId);
        sqrmCNNode.setIdentifier(sq1dNodeReference);
        sqrmCNNode.setName("squirm1");
        sqrmCNNode.setDescription("this is a squirm test");
        sqrmCNNode.setBaseURL("https://my.squirm1.test/cn");
        sqrmCNNode.setReplicate(false);
        sqrmCNNode.setSynchronize(false);
        sqrmCNNode.setState(NodeState.UP);
        sqrmCNNode.setType(NodeType.CN);
        Subject sqrmSubject = new Subject();
        sqrmSubject.setValue(adminSubject);
        sqrmCNNode.addSubject(sqrmSubject);

        Subject sqrmContactSubject = new Subject();
        sqrmContactSubject.setValue(primarySubject);
        sqrmCNNode.addContactSubject(sqrmContactSubject);

        Services sqrmservices = new Services();
        Service sqrmcoreService = new Service();
        sqrmcoreService.setName("CNCore");
        sqrmcoreService.setVersion("v1");
        sqrmcoreService.setAvailable(Boolean.TRUE);

        Service sqrmreadService = new Service();
        sqrmreadService.setName("CNRead");
        sqrmreadService.setVersion("v1");
        sqrmreadService.setAvailable(Boolean.TRUE);

        Service sqrmAuthorizationService = new Service();
        sqrmAuthorizationService.setName("CNAuthorization");
        sqrmAuthorizationService.setVersion("v1");
        sqrmAuthorizationService.setAvailable(Boolean.TRUE);

        Service sqrmIdentityService = new Service();
        sqrmIdentityService.setName("CNIdentity");
        sqrmIdentityService.setVersion("v1");
        sqrmIdentityService.setAvailable(Boolean.TRUE);

        Service sqrmReplicationService = new Service();
        sqrmReplicationService.setName("CNReplication");
        sqrmReplicationService.setVersion("v1");
        sqrmReplicationService.setAvailable(Boolean.TRUE);


        Service sqrmRegisterService = new Service();
        sqrmRegisterService.setName("CNRegister");
        sqrmRegisterService.setVersion("v1");
        sqrmRegisterService.setAvailable(Boolean.TRUE);

        ServiceMethodRestriction restrictIdentity = new ServiceMethodRestriction();
        restrictIdentity.setMethodName("mapIdentity");
        Subject restrictToSubject = new Subject();
        restrictToSubject.setValue(primarySubject);
        restrictIdentity.addSubject(restrictToSubject);

        sqrmIdentityService.addRestriction(restrictIdentity);
        
        sqrmservices.addService(sqrmcoreService);
        sqrmservices.addService(sqrmreadService);
        sqrmservices.addService(sqrmAuthorizationService);
        sqrmservices.addService(sqrmIdentityService);
        sqrmservices.addService(sqrmReplicationService);
        sqrmservices.addService(sqrmRegisterService);

        sqrmCNNode.setServices(sqrmservices);
        // because we use a base DN, only need to supply the RDN
        DistinguishedName dn = new DistinguishedName();
        dn.add("dc","dataone");
        dn.add("cn", sqrmId);

        DirContextAdapter context = new DirContextAdapter(dn);
        mapNodeToContext(sqrmCNNode, context);
        ldapTemplate.bind(dn, context, null);

        for (Service service : sqrmCNNode.getServices().getServiceList()) {
            String d1NodeServiceId = service.getName() + "-" + service.getVersion();
            log.info("sqrm1 adding service " + d1NodeServiceId);
            DistinguishedName dnService = new DistinguishedName();
            dnService.add("dc","dataone");
            dnService.add("cn", sqrmId);
            dnService.add("d1NodeServiceId", d1NodeServiceId);
            context = new DirContextAdapter(dnService);
            mapServiceToContext(service, sqrmId, d1NodeServiceId, context);
            ldapTemplate.bind(dnService, context, null);
            for (ServiceMethodRestriction restrict: service.getRestrictionList()) {
                DistinguishedName dnServiceRestriction = new DistinguishedName();
                dnServiceRestriction.add("dc","dataone");
                dnServiceRestriction.add("cn", sqrmId);
                dnServiceRestriction.add("d1NodeServiceId", d1NodeServiceId);
                dnServiceRestriction.add("d1ServiceMethodName", restrict.getMethodName());
                log.info("sqrm adding restriction " + restrict.getMethodName());
                context = new DirContextAdapter(dnServiceRestriction);
                mapServiceMethodRestriction(restrict, sqrmId, d1NodeServiceId, context);
                ldapTemplate.bind(dnServiceRestriction, context, null);
            }
        }

        testNodeList.add(sqrmCNNode);
    }
}
