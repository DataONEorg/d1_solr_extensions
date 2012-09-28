package org.dataone.solr.handler.component;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.solr.common.util.XML;
import org.apache.solr.core.SolrCore;
import org.apache.solr.handler.admin.LukeRequestHandler;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.SolrQueryResponse;
import org.apache.solr.schema.IndexSchema;
import org.apache.solr.schema.SchemaField;
import org.apache.solr.util.plugin.SolrCoreAware;

public class SolrQueryEngineDescriptionHandler extends LukeRequestHandler implements SolrCoreAware {

    private String solrVersion = "3.4";
    private String schemaVersion = "1.0";
    private static final String name = "solr";
    private String additionalInfo = null;
    private static final String SCHEMA_PROPERTIES_PATH = "/etc/dataone/index/solr/schema.properties";
    private static final String SCHEMA_VERSION_PROPERTY = "schema-version=";
    private List<SchemaFieldDescription> fieldDescriptions = null;

    public SolrQueryEngineDescriptionHandler() {
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        rsp.add("queryEngineVersion", solrVersion);
        rsp.add("querySchemaVersion", schemaVersion);
        rsp.add("name", name);
        rsp.add("additionalInfo", additionalInfo);
        rsp.add("queryField", fieldDescriptions);
    }

    @Override
    public void inform(SolrCore core) {
        IndexSchema schema = core.getSchema();
        setSchemaVersionFromPropertiesFile();
        setSolrVersion();
        Map<String, SchemaField> fieldMap = schema.getFields();
        fieldDescriptions = new ArrayList<SchemaFieldDescription>();
        for (SchemaField schemaField : fieldMap.values()) {
            fieldDescriptions.add(new SchemaFieldDescription(schemaField));
        }
    }

    /**
     * Based on org.apache.solr.handler.admin.SystemInfoHandler.getLuceneInfo()
     */
    private void setSolrVersion() {
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
    }

    private void setSchemaVersionFromPropertiesFile() {
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
                e.printStackTrace();
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

    public class SchemaFieldDescription {
        private String name = "";
        private String description = "";
        private String type = "";
        private boolean searchable;
        private boolean returnable;
        private boolean sortable;
        private boolean multivalued;

        public SchemaFieldDescription(SchemaField field) {
            this.name = field.getName();
            this.type = field.getType().getTypeName();
            this.description = "description text";
            this.searchable = field.indexed();
            this.returnable = field.stored();
            this.multivalued = field.multiValued();
            this.sortable = isSortable(field);
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

        @Override
        public String toString() {
            return "Name: " + name + ", Description: " + description + ", Type: " + type
                    + ", Searchable: " + searchable + ", Returnable: " + returnable
                    + ", Multivalued: " + multivalued + ", Sortable: " + sortable;
        }
    }
}
