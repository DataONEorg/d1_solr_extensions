package org.dataone.solr.response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.dataone.configuration.Settings;
import org.dataone.exceptions.MarshallingException;
import org.dataone.service.types.v1_1.QueryEngineDescription;
import org.dataone.service.util.TypeMarshaller;


/**
 * Based on solr's XMLResponseWriter and XMLWriter
 * 
 * @author sroseboo
 * @author slaughter
 * 
 */
public abstract class QueryEngineDescriptionResponseWriter implements QueryResponseWriter {

    private String D1_XSLT = null;
    private String responseKey;
    private static Log logger = LogFactory.getLog(QueryEngineDescriptionResponseWriter.class);
    private static final String STYLED_RESPONSE_PROP_NAME = "queryEngineDescriptionResponse.styled";
    private static final boolean styledResponse =
        Settings.getConfiguration().getBoolean(STYLED_RESPONSE_PROP_NAME, true);


    protected void setD1_XSLT(String D1_XSLT) {
        this.D1_XSLT = D1_XSLT;
    }

    protected void setResponseKey(String responseKey) {
        this.responseKey = responseKey;
    }

    @Override
    public void write(Writer writer, SolrQueryRequest request, SolrQueryResponse response) {

        QueryEngineDescription qed = (QueryEngineDescription) response.getValues().get(
                this.responseKey);
        writeQueryEngineDescription(qed, writer);
    }

    private void writeQueryEngineDescription(QueryEngineDescription qed, Writer writer) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            if (styledResponse) {
                logger.debug("Since setting of " + STYLED_RESPONSE_PROP_NAME + " is true, it"
                                 + " returns a styled response.");
                TypeMarshaller.marshalTypeToOutputStream(qed, os, D1_XSLT);
            } else {
                logger.debug("Since setting of " + STYLED_RESPONSE_PROP_NAME + " is false, it"
                                 + " returns an unstyled response.");
                TypeMarshaller.marshalTypeToOutputStream(qed, os);
            }

        } catch (MarshallingException jibxEx) {
            logger.error(jibxEx.getMessage(), jibxEx);
        } catch (IOException ioEx) {
            logger.error(ioEx.getMessage(), ioEx);
        }
        try {
            writer.write(os.toString("UTF-8"));
        } catch (IOException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override
    public void init(NamedList args) {
        // NO-OP
    }

    @Override
    public String getContentType(SolrQueryRequest request, SolrQueryResponse response) {
        return CONTENT_TYPE_XML_UTF8;
    }
}
