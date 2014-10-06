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

import org.dataone.solr.handler.SolrQueryEngineDescriptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Based on solr's XMLResponseWriter and XMLWriter
 * 
 * @author sroseboo
 * @author slaughter
 * 
 */
public class SolrQueryEngineDescriptionResponseWriter extends QueryEngineDescriptionResponseWriter {

    private static final String D1_XSLT = "/cn/xslt/dataone.types.v1.xsl";
    private static Logger logger = LoggerFactory
            .getLogger(SolrQueryEngineDescriptionResponseWriter.class);

    public SolrQueryEngineDescriptionResponseWriter() {
        super.setD1_XSLT(D1_XSLT);
        super.setResponseKey(SolrQueryEngineDescriptionHandler.RESPONSE_KEY);
    }
}
