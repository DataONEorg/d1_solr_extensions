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
package org.dataone.solr.response;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.request.SolrQueryRequest;
import org.apache.solr.response.QueryResponseWriter;
import org.apache.solr.response.SolrQueryResponse;
import org.dataone.service.types.v1.QueryEngineDescription;
import org.dataone.service.util.TypeMarshaller;
import org.dataone.solr.handler.SolrQueryEngineDescriptionHandler;
import org.jibx.runtime.JiBXException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Based on solr's XMLResponseWriter and XMLWriter
 * 
 * @author sroseboo
 * 
 */
public class QueryEngineDescriptionResponseWriter implements QueryResponseWriter {

    private static final String XML_START = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n";
    private static final String D1_XSLT = "<?xml-stylesheet type=\"text/xsl\" href=\"/cn/xslt/dataone.types.v1.xsl\"?>\n";
    private static Logger logger = LoggerFactory
            .getLogger(QueryEngineDescriptionResponseWriter.class);

    // private static final String QUERY_ENGINE_DESCRIPTION_START =
    // "<d1:queryEngineDescription xmlns:d1=\"http://ns.dataone.org/service/types/v1\">\n";
    // private static final String QUERY_ENGINE_DESCRIPTION_END =
    // "</d1:queryEngineDescription>\n";

    @Override
    public void write(Writer writer, SolrQueryRequest request, SolrQueryResponse response)
            throws IOException {

        writer.write(XML_START);
        writer.write(D1_XSLT);
        QueryEngineDescription qed = (QueryEngineDescription) response.getValues().get(
                SolrQueryEngineDescriptionHandler.RESPONSE_KEY);
        writeQueryEngineDescription(qed, writer);
    }

    private void writeQueryEngineDescription(QueryEngineDescription qed, Writer writer) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            TypeMarshaller.marshalTypeToOutputStream(qed, os);
        } catch (JiBXException jibxEx) {
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
