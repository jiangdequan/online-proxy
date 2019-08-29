package io.github.javahub.web.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;

import org.slf4j.MDC;

import io.github.javahub.web.util.UuidUtils;

public class LogFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        MDC.put("TraceID", UuidUtils.generate());
        MDC.put("hostIP", request.getLocalAddr());
        chain.doFilter(request, response);
    }

}