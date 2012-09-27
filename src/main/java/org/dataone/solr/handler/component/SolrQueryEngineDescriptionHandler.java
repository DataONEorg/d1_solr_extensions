package org.dataone.solr.handler.component;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

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
    private String name = "solr";
    private String additionalInfo = null;

    private Map<String, SchemaField> fieldMap = null;

    @Override
    public void inform(SolrCore core) {
        IndexSchema schema = core.getSchema();
        fieldMap = schema.getFields();
    }

    @Override
    public void handleRequestBody(SolrQueryRequest req, SolrQueryResponse rsp) throws Exception {
        rsp.add("queryEngineVersion", solrVersion);
        rsp.add("querySchemaVersion", schemaVersion);
        rsp.add("name", name);
        rsp.add("additionalInfo", additionalInfo);
        rsp.add("queryField", fieldMap.values());
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

}
