package org.dataone.solr.servlet;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class RequestBodyHttpServletRequestWrapper extends HttpServletRequestWrapper {

    protected static Log logger = LogFactory.getLog(RequestBodyHttpServletRequestWrapper.class);

    private final String requestBody;

    public RequestBodyHttpServletRequestWrapper(HttpServletRequest request) throws IOException {

        super(request);

        if (request == null) {
            logger.warn("Request is null");
        }

        StringBuilder stringBuilder = new StringBuilder("");
        BufferedReader bufferedReader = null;
        try {
            InputStream inputStream = request.getInputStream();
            if (inputStream != null) {
                if (request.getCharacterEncoding() != null) {
                    logger.warn("Request char encoding is: " + request.getCharacterEncoding());
                    bufferedReader = new BufferedReader(new InputStreamReader(inputStream,
                            request.getCharacterEncoding()));
                } else {
                    logger.warn("Request char encoding is null, using utf-8");
                    bufferedReader = new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
                }
                char[] charBuffer = new char[128];
                int bytesRead = -1;
                while ((bytesRead = bufferedReader.read(charBuffer)) > 0) {
                    stringBuilder.append(charBuffer, 0, bytesRead);
                }
            }
        } catch (IOException ioe) {
            throw ioe;
        } finally {
            if (bufferedReader != null) {
                try {
                    bufferedReader.close();
                } catch (IOException ioe) {
                    throw ioe;
                }
            }
        }
        requestBody = stringBuilder.toString();
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {

        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
                requestBody.getBytes("UTF-8"));

        //        final ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(
        //                requestBody.getBytes());

        ServletInputStream servletInputStream = new ServletInputStream() {
            public int read() throws IOException {
                return byteArrayInputStream.read();
            }
        };

        return servletInputStream;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getInputStream(), "UTF-8"));
        //return new BufferedReader(new InputStreamReader(this.getInputStream()));
    }

    public String getBody() {
        return this.requestBody;
    }

}