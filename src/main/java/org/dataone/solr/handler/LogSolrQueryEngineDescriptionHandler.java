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
package org.dataone.solr.handler;

import java.net.URL;

import org.apache.solr.core.SolrCore;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.dataone.service.types.v1_1.QueryEngineDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public  class LogSolrQueryEngineDescriptionHandler extends QueryEngineDescriptionHandler {

    private static final String NAME = "logsolr";
    // default value - will be set by inform()
    private String solrVersion = "";
    // default value - will be set by inform()
    private String schemaVersion = "";

    private String additionalInfo = "http://mule1.dataone.org/ArchitectureDocs-current/design/LogAggregation.html";
    private String SCHEMA_PROPERTIES_PATH = "/etc/dataone/index/solr/schema.properties";
    private static final String DESCRIPTION_PATH = "/etc/dataone/event-index/eventIndexQueryFieldDescriptions.properties";
    private static final String SCHEMA_VERSION_PROPERTY = "schema-version=";
    public static final String RESPONSE_KEY = "LogSolrQueryEngineDescription";

    private static Logger logger = LoggerFactory.getLogger(LogSolrQueryEngineDescriptionHandler.class);

    public LogSolrQueryEngineDescriptionHandler () {
    	super.setAdditionalInfo(additionalInfo);
    	super.setSchemaProperitesPath(SCHEMA_PROPERTIES_PATH);
    	super.setDescriptionPath(DESCRIPTION_PATH);
    	super.setDescriptionPath(SCHEMA_VERSION_PROPERTY);
    	super.setQueryEngineName(NAME);
    	super.setResponseKey(RESPONSE_KEY);
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) {
    	super.handleRequestBody(req, rsp);
    }

    @Override
    public void inform(SolrCore core) {
    	super.inform(core);
    }

    @Override
    public void setAdditionalInfo(QueryEngineDescription qed) {
    	super.setAdditionalInfo(qed);
    }

    /**
     * Based on org.apache.solr.handler.admin.SystemInfoHandler.getLuceneInfo()
     */
    public void setSolrVersion(QueryEngineDescription qed) {
    	super.setSolrVersion(qed);
    }

    public void setSchemaVersionFromPropertiesFile(QueryEngineDescription qed) {
    	super.setSchemaVersionFromPropertiesFile(qed);
    }

    public void loadSchemaFieldDescriptions() {
    	super.loadSchemaFieldDescriptions();
    }

    // ////////////////////// SolrInfoMBeans methods //////////////////////
    @Override
    public String getDescription() {
        return "LogSolr Query Engine Description Handler.";
    }

    @Override
    public String getVersion() {
        return "Version 1.0";
    }

    @Override
    public String getSourceId() {
        return "LogSolrQueryEngineDescriptionHandler.java";
    }

    @Override
    public String getSource() {
    	return super.getSource();
    }

    @Override
    public URL[] getDocs() {
    	return super.getDocs();
    }
}
