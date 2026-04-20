package org.dataone.solr.handler;

import org.apache.solr.security.AuthorizationContext;
import org.apache.solr.security.PermissionNameProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public  class LogSolrQueryEngineDescriptionHandler extends QueryEngineDescriptionHandler {

    private final String additionalInfo = "http://mule1.dataone.org/ArchitectureDocs-current/design/LogAggregation.html";
    private final String SCHEMA_PROPERTIES_PATH = "/etc/dataone/index/solr/schema.properties";
    private final String DESCRIPTION_PATH = "/etc/dataone/event-index/eventIndexQueryFieldDescriptions.properties";
    private final String SCHEMA_VERSION_PROPERTY = "schema-version=";
    public static final String RESPONSE_KEY = "LogSolrQueryEngineDescription";
    private final String QUERY_ENGINE_NAME = "logsolr";

    private Logger logger = LoggerFactory.getLogger(LogSolrQueryEngineDescriptionHandler.class);

    public LogSolrQueryEngineDescriptionHandler () {
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