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
import org.dataone.service.types.v1_1.QueryEngineDescription;
import org.dataone.service.types.v1_1.QueryField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class QueryEngineDescriptionHandler extends RequestHandlerBase implements
        SolrCoreAware {

    private String additionalInfo = null;
    private String queryEngineName = null;
    // default value - will be set by inform()
    private String schemaVersion = null;
    // default value - will be set by inform()
    private String solrVersion = "";
    private String descriptionPath = null;
    private String schemaVersionProperty = null;
    private String responseKey = null;
    private String schemaPropertiesPath = null;
    private QueryEngineDescription qed = null;
    private Map<String, String> fieldDescriptions = null;
    private static Logger logger = LoggerFactory.getLogger(SolrQueryEngineDescriptionHandler.class);

    public QueryEngineDescriptionHandler() {
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) {
        rsp.add(responseKey, qed);
    }

    @Override
    public void inform(SolrCore core) {
        loadSchemaFieldDescriptions();
        qed = new QueryEngineDescription();
        qed.setName(queryEngineName);
        setSchemaVersionFromPropertiesFile(qed);
        setSolrVersion(qed);
        setAdditionalInfo(qed);

        IndexSchema schema = core.getLatestSchema();
        Map<String, SchemaField> fieldMap = schema.getFields();
        for (SchemaField schemaField : fieldMap.values()) {
            qed.addQueryField(createQueryFieldFromSchemaField(schemaField, this.fieldDescriptions));
        }
        Collections.sort(qed.getQueryFieldList(), new QueryFieldAlphaComparator());
    }

    protected void setAdditionalInfo(QueryEngineDescription qed) {
        List<String> info = new ArrayList<String>();
        info.add(this.additionalInfo);
        qed.setAdditionalInfoList(info);
    }

    /**
     * Based on org.apache.solr.handler.admin.SystemInfoHandler.getLuceneInfo()
     */
    protected void setSolrVersion(QueryEngineDescription qed) {
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

    protected void setSchemaVersionFromPropertiesFile(QueryEngineDescription qed) {
        File file = new File(schemaPropertiesPath);
        if (file.exists()) {
            try {
                List lines = FileUtils.readLines(file, "UTF-8");
                for (Object object : lines) {
                    String line = (String) object;
                    if (line.startsWith(schemaVersionProperty)) {
                        String version = StringUtils.substringAfter(line, schemaVersionProperty);
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

    protected void loadSchemaFieldDescriptions() {
        this.fieldDescriptions = new HashMap<String, String>();
        File file = new File(this.descriptionPath);
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

    protected void setAdditionalInfo(String additionalInfo) {
        this.additionalInfo = additionalInfo;
    }

    protected void setDescriptionPath(String descriptionPath) {
        this.descriptionPath = descriptionPath;
    }

    protected void setSchemaProperitesPath(String schemaPropertiesPath) {
        this.schemaPropertiesPath = schemaPropertiesPath;
    }

    protected void setSchemaVersionProperty(String schemaVersionProperty) {
        this.schemaVersionProperty = schemaVersionProperty;
    }

    protected void setResponseKey(String responseKey) {
        this.responseKey = responseKey;
    }

    protected void setQueryEngineName(String queryEngineName) {
        this.queryEngineName = queryEngineName;
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
        if (StringUtils.isNotBlank(fieldDescriptions.get(field.getName()))) {
            queryField.addDescription(fieldDescriptions.get(field.getName()));
        }
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
            return field1Name.compareToIgnoreCase(field2Name);
        }
    }
}
