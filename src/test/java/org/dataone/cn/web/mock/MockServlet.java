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

//import java.io.IOException;
import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Scanner;
//import java.util.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.dataone.cn.servlet.http.ParameterKeys;
import org.springframework.core.io.ClassPathResource;

public class MockServlet extends HttpServlet {

    /**
     * For unit testing, build a 2 member filter chain consisting of Filter
     * and this servlet (the endpoint).  
     * 
     */
    private static final long serialVersionUID = 1L;
    private BufferedReader br;
    private FileInputStream byteInput;
    static final int SIZE = 8192;

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        ArrayList<String> valuesArray = new ArrayList<String>();
        String[] subjectValues = req.getParameterValues(ParameterKeys.AUTHORIZED_SUBJECTS);
        if ((subjectValues != null) && (subjectValues.length > 0) ) {
            valuesArray.addAll(Arrays.asList(subjectValues));
        }
        String[] isAdminValues = req.getParameterValues(ParameterKeys.IS_CN_ADMINISTRATOR);
        if ( (isAdminValues != null) && (isAdminValues.length > 0) ) {
            valuesArray.addAll(Arrays.asList(isAdminValues));
        }
        res.setContentType("text/xml");
        ServletOutputStream out = res.getOutputStream();
        for (String line : valuesArray) {
            out.print(line);
        }
        out.flush();
        out.close();

    }
}
