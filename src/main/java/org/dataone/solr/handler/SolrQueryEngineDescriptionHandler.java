package org.dataone.solr.handler;

import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.PermissionNameProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrQueryEngineDescriptionHandler extends QueryEngineDescriptionHandler {

    private final String additionalInfo = "http://mule1.dataone.org/ArchitectureDocs-current/design/SearchMetadata.html";
    private final String SCHEMA_PROPERTIES_PATH = "/etc/dataone/index/solr/schema.properties";
    private final String DESCRIPTION_PATH = "/etc/dataone/index/solr/queryFieldDescriptions.properties";
    private final String SCHEMA_VERSION_PROPERTY = "schema-version=";
    public static final String RESPONSE_KEY = "queryEngineDescription";
    private final String QUERY_ENGINE_NAME = "solr";

    private Logger logger = LoggerFactory.getLogger(SolrQueryEngineDescriptionHandler.class);

    public SolrQueryEngineDescriptionHandler() {
        super.setAdditionalInfo(additionalInfo);
        super.setDescriptionPath(DESCRIPTION_PATH);
        super.setSchemaProperitesPath(SCHEMA_PROPERTIES_PATH);
        super.setSchemaVersionProperty(SCHEMA_VERSION_PROPERTY);
        super.setResponseKey(RESPONSE_KEY);
        super.setQueryEngineName(QUERY_ENGINE_NAME);
    }

    @Override
    public void close() {

    }

    @Override
    public PermissionNameProvider.Name getPermissionName(AuthorizationContext ctx) {
        return PermissionNameProvider.Name.READ_PERM;
    }
}