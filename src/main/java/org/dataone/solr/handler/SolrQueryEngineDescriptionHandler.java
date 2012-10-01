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

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.util.XML;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.RequestHandlerBase;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.util.plugin.SolrCoreAware;
import org.dataone.service.types.v1.QueryEngineDescription;
import org.dataone.service.types.v1.QueryField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SolrQueryEngineDescriptionHandler extends RequestHandlerBase implements SolrCoreAware {

    private String solrVersion = "3.4";
    private String schemaVersion = "1.0";
    private static final String NAME = "solr";
    private String additionalInfo = "http://mule1.dataone.org/ArchitectureDocs-current/design/SearchMetadata.html";
    private static final String SCHEMA_PROPERTIES_PATH = "/etc/dataone/index/solr/schema.properties";
    private static final String DESCRIPTION_PATH = "etc/dataone/index/solr/queryFieldDescriptions.properties";
    private static final String SCHEMA_VERSION_PROPERTY = "schema-version=";
    public static final String RESPONSE_KEY = "queryEngineDescription";
    private QueryEngineDescription qed = null;
    private Map<String, String> fieldDescriptions = null;

    private static Logger logger = LoggerFactory.getLogger(SolrQueryEngineDescriptionHandler.class);

    public SolrQueryEngineDescriptionHandler() {
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        rsp.add(RESPONSE_KEY, qed);
    }

    @Override
    public void inform(SolrCore core) {
        loadSchemaFieldDescriptions(fieldDescriptions);
        qed = new QueryEngineDescription();
        qed.setName(NAME);
        setSchemaVersionFromPropertiesFile(qed);
        setSolrVersion(qed);
        setAdditionalInfo(qed);

        IndexSchema schema = core.getSchema();
        Map<String, SchemaField> fieldMap = schema.getFields();
        for (SchemaField schemaField : fieldMap.values()) {
            qed.addQueryField(createQueryFieldFromSchemaField(schemaField, fieldDescriptions));
        }
        Collections.sort(qed.getQueryFieldList(), new QueryFieldAlphaComparator());
    }

    private void setAdditionalInfo(QueryEngineDescription qed) {
        List<String> info = new ArrayList<String>();
        info.add(this.additionalInfo);
        qed.setAdditionalInfoList(info);
    }

    /**
     * Based on org.apache.solr.handler.admin.SystemInfoHandler.getLuceneInfo()
     */
    private void setSolrVersion(QueryEngineDescription qed) {
        String solrSpecVersion = "";

        Package p = SolrCore.class.getPackage();
        StringWriter tmp = new StringWriter();
        solrSpecVersion = p.getSpecificationVersion();
        if (null != solrSpecVersion) {
            try {
                XML.escapeCharData(solrSpecVersion, tmp);
            } catch (IOException e) {
                e.printStackTrace();
            }
            solrSpecVersion = tmp.toString();
        }
        if (StringUtils.isNotBlank(solrSpecVersion)) {
            this.solrVersion = solrSpecVersion;
        }
        qed.setQueryEngineVersion(this.solrVersion);
    }

    private void setSchemaVersionFromPropertiesFile(QueryEngineDescription qed) {
        File file = new File(SCHEMA_PROPERTIES_PATH);
        if (file.exists()) {
            try {
                List lines = FileUtils.readLines(file, "UTF-8");
                for (Object object : lines) {
                    String line = (String) object;
                    if (line.startsWith(SCHEMA_VERSION_PROPERTY)) {
                        String version = StringUtils.substringAfter(line, SCHEMA_VERSION_PROPERTY);
                        if (StringUtils.isNotBlank(version)) {
                            this.schemaVersion = version;
                        }
                    }
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        qed.setQuerySchemaVersion(this.schemaVersion);
    }

    private void loadSchemaFieldDescriptions(Map<String, String> fieldDescriptions) {
        fieldDescriptions = new HashMap<String, String>();
        File file = new File(DESCRIPTION_PATH);
        if (file.exists()) {
            try {
                List lines = FileUtils.readLines(file, "UTF-8");
                for (Object object : lines) {
                    String line = (String) object;
                    String[] tokens = StringUtils.split(line, "=");
                    if (tokens.length == 2) {
                        String name = tokens[0].trim();
                        String description = tokens[1].trim();
                        fieldDescriptions.put(name, description);
                    }
                }
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
    }

    // ////////////////////// SolrInfoMBeans methods //////////////////////
    @Override
    public String getDescription() {
        return "Solr Query Engine Description Handler.";
    }

    @Override
    public String getVersion() {
        return "Version 1.0";
    }

    @Override
    public String getSourceId() {
        return "SolrQueryEngineDescriptionHandler.java";
    }

    @Override
    public String getSource() {
        return "https://repository.dataone.org/";
    }

    @Override
    public URL[] getDocs() {
        try {
            return new URL[] { new URL("http://mule1.dataone.org/ArchitectureDocs-current/") };
        } catch (MalformedURLException ex) {
            return null;
        }
    }

    private QueryField createQueryFieldFromSchemaField(SchemaField field,
            Map<String, String> fieldDescriptions) {
        QueryField queryField = new QueryField();
        queryField.setName(field.getName());
        queryField.setType(field.getType().getTypeName());
        queryField.addDescription(fieldDescriptions.get(field.getName()));
        queryField.setSearchable(field.indexed());
        queryField.setReturnable(field.stored());
        queryField.setMultivalued(field.multiValued());
        queryField.setSortable(isSortable(field));
        return queryField;
    }

    private boolean isSortable(SchemaField field) {
        String type = field.getType().getTypeName();
        if ("int".equals(type) || "long".equals(type) || "float".equals(type)
                || "double".equals(type)) {
            return false;
        } else {
            return true;
        }
    }

    private class QueryFieldAlphaComparator implements Comparator<QueryField> {
        public int compare(QueryField arg0, QueryField arg1) {
            String field1Name = arg0.getName();
            String field2Name = arg1.getName();
            return field1Name.compareTo(field2Name);
        }
    }
}
