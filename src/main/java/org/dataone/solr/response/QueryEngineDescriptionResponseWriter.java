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
import org.dataone.service.types.v1_1.QueryEngineDescription;
import org.dataone.service.util.TypeMarshaller;
import org.jibx.runtime.JiBXException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static Logger logger = LoggerFactory
            .getLogger(QueryEngineDescriptionResponseWriter.class);


	protected void setD1_XSLT(String D1_XSLT) {
		this.D1_XSLT = D1_XSLT;
	}
	
	protected void setResponseKey(String responseKey) {
		this.responseKey = responseKey;
	}
	
    @Override
    public void write(Writer writer, SolrQueryRequest request, SolrQueryResponse response)
            throws IOException {

        QueryEngineDescription qed = (QueryEngineDescription) response.getValues().get(
                this.responseKey);
        writeQueryEngineDescription(qed, writer);
    }

    private void writeQueryEngineDescription(QueryEngineDescription qed, Writer writer) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        try {
            TypeMarshaller.marshalTypeToOutputStream(qed, os, D1_XSLT);
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
