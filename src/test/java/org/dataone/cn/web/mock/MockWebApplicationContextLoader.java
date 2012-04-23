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

package org.dataone.cn.web.mock;

import java.io.File;
import org.springframework.beans.factory.support.BeanDefinitionReader;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.FileSystemResourceLoader;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.test.context.support.*;
import org.springframework.util.ResourceUtils;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

/**
 *
 * @author rwaltz
 */
public class MockWebApplicationContextLoader extends AbstractContextLoader {

    public static final MockServletContext SERVLET_CONTEXT;
    public static DispatcherServlet testDispatcherServlet;

    static {
        // String basePath = "target/test-classes";
    String basePath = "src/test/resources/webapp";
        try {
            File path = ResourceUtils.getFile(basePath);
            //resolve absolute path
            basePath = "file:" + path.getAbsolutePath();
        } catch (Exception e) {
            System.out.println("Unable to resolve path: target/test-classes");
        }
        SERVLET_CONTEXT = new MockServletContext(basePath, new FileSystemResourceLoader());
    }

    protected BeanDefinitionReader createBeanDefinitionReader(final GenericApplicationContext context) {
        return new XmlBeanDefinitionReader(context);
    }

    @Override
    public final ConfigurableApplicationContext loadContext(final String... locations) throws Exception {

        final GenericWebApplicationContext webContext = new GenericWebApplicationContext();
        SERVLET_CONTEXT.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webContext);

        webContext.setServletContext(SERVLET_CONTEXT);

        createBeanDefinitionReader(webContext).loadBeanDefinitions(locations);
        AnnotationConfigUtils.registerAnnotationConfigProcessors(webContext);
        webContext.refresh();
        webContext.registerShutdownHook();
  
        return webContext;
    }

    @Override
    protected String getResourceSuffix() {
        return "-context.xml";
    }

    @Override
    public ApplicationContext loadContext(MergedContextConfiguration mcc) throws Exception {
        final GenericWebApplicationContext webContext = new GenericWebApplicationContext();
        SERVLET_CONTEXT.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, webContext);

        webContext.setServletContext(SERVLET_CONTEXT);
        
        createBeanDefinitionReader(webContext).loadBeanDefinitions(mcc.getLocations());
        AnnotationConfigUtils.registerAnnotationConfigProcessors(webContext);
        webContext.refresh();
        webContext.registerShutdownHook();

        return webContext;
    }
}
