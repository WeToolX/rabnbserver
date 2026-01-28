package com.ra.rabnbserver.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * 可重复读取的请求体包装类
 */
public class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;
    private final String overrideContentType;

    public CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body, String overrideContentType) {
        super(request);
        this.body = body == null ? new byte[0] : body;
        this.overrideContentType = overrideContentType;
    }

    @Override
    public int getContentLength() {
        return body.length;
    }

    @Override
    public long getContentLengthLong() {
        return body.length;
    }

    @Override
    public String getContentType() {
        return overrideContentType != null ? overrideContentType : super.getContentType();
    }

    @Override
    public String getHeader(String name) {
        if (overrideContentType != null && "Content-Type".equalsIgnoreCase(name)) {
            return overrideContentType;
        }
        return super.getHeader(name);
    }

    @Override
    public Enumeration<String> getHeaders(String name) {
        if (overrideContentType != null && "Content-Type".equalsIgnoreCase(name)) {
            return Collections.enumeration(List.of(overrideContentType));
        }
        return super.getHeaders(name);
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return inputStream.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener listener) {
            }

            @Override
            public int read() throws IOException {
                return inputStream.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
