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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public  class LogSolrQueryEngineDescriptionHandler extends QueryEngineDescriptionHandler {

    private final String additionalInfo = "http://mule1.dataone.org/ArchitectureDocs-current/design/LogAggregation.html";
    private final String SCHEMA_PROPERTIES_PATH = "/etc/dataone/index/solr/schema.properties";
    private final String DESCRIPTION_PATH = "/etc/dataone/event-index/eventIndexQueryFieldDescriptions.properties";
    private final String SCHEMA_VERSION_PROPERTY = "schema-version=";
    public final String RESPONSE_KEY = "LogSolrQueryEngineDescription";
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
}