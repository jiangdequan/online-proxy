package io.github.javahub.web.controller.proxy;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.javahub.web.cache.InputStreamCache;
import io.github.javahub.web.cache.ThreadLocalCache;
import io.github.javahub.web.common.ReverseProxy;
import io.github.javahub.web.util.IoUtils;
import okhttp3.OkHttpClient;

@RestController
public class OnlineProxyController {

    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(OnlineProxyController.class);

    /**
     * auto inject HttpServletRequest, it is threadsafe
     */
    @Autowired
    private HttpServletRequest httpServletRequest;

    /**
     * auto inject HttpServletResponse, it is threadsafe
     */
    @Autowired
    private HttpServletResponse httpServletResponse;

    /**
     * auto inject OkHttpClient, request remote server
     */
    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;

    /**
     * domain
     */
    public static String domain;

    /**
     * the injected header for html
     */
    private String proxyHelper = "<!doctype html><meta http-equiv=\"content-security-policy\" content=\"frame-src 'self' 'unsafe-inline' file: data: blob: mediastream: filesystem: chrome-extension-resource: ; object-src 'self' 'unsafe-inline' file: data: blob: mediastream: filesystem: chrome-extension-resource: \"><base href=\"%s\"><script data-id=\"3\" src=\"/static/js/helper.js\"></script>\r\n\r\n";

    private byte[] injectHtml;

    private byte[] gzipInjectHtml;

    private byte[] chunkedGzipInjectHtml;

    /**
     * cache the helper.js
     */
    private static InputStreamCache helperJsInputStreamCache;

    /**
     * cache the home page
     */
    private static InputStreamCache indexHtmlInputStreamCache;

    static {
        if (null == helperJsInputStreamCache) {
            InputStream jsInputStream = OnlineProxyController.class.getClassLoader().getResourceAsStream("static/js/helper.js");
            helperJsInputStreamCache = new InputStreamCache(jsInputStream);
        }
        if (null == indexHtmlInputStreamCache) {
            InputStream htmlInputStream = OnlineProxyController.class.getClassLoader().getResourceAsStream("templates/proxy.html");
            indexHtmlInputStreamCache = new InputStreamCache(htmlInputStream);
        }
    }

    @RequestMapping({"/", ""})
    public void homePage(HttpServletResponse httpServletResponse) {
        try {
            getDomainAndSave();
            writeHtmlResource(injectHtml, indexHtmlInputStreamCache.getInputStream(),
                    httpServletResponse.getOutputStream());
        } catch (Exception e) {
            LOGGER.error("read helper.js error", e);
        }
    }

    @RequestMapping(value = {"/**"})
    public void onlineProxy() throws Exception {
        ReverseProxy reverseProxy = new ReverseProxy();
        ThreadLocalCache.cacheHtml(injectHtml);
        reverseProxy.service(httpServletRequest, httpServletResponse);
    }

    private void getDomainAndSave() {
        if (StringUtils.isNotBlank(this.domain)) {
            return;
        }
//        StringBuilder domainStr = new StringBuilder();
//        domainStr.append(httpServletRequest.getServerName()).append(":").append(httpServletRequest.getServerPort());
//        this.domain = domainStr.toString();
        try {
            URI uri = new URI(httpServletRequest.getRequestURL().toString());
            StringBuilder domain = new StringBuilder();
            domain.append(uri.getScheme()).append("://").append(uri.getHost()).append(":").append(uri.getPort()).append("/");
            this.domain = domain.toString();
            this.injectHtml = String.format(proxyHelper, domain).getBytes();
            this.gzipInjectHtml = IoUtils.gzip(injectHtml);
            byte[] sizeArray = (gzipInjectHtml.length + "\r\n").getBytes();
            int len = sizeArray.length + gzipInjectHtml.length;
            this.chunkedGzipInjectHtml = new byte[len];
            for (int i = 0; i < sizeArray.length; i++) {
                chunkedGzipInjectHtml[i] = sizeArray[i];
            }
            for (int i = 0; i < gzipInjectHtml.length; i++) {
                chunkedGzipInjectHtml[i + sizeArray.length] = gzipInjectHtml[i];
            }
        } catch (Exception e) {
            LOGGER.error("getDomain error", e);
        }
    }

    @RequestMapping("/static/js/helper.js")
    public void helpJs() {
        try {
            OutputStream outputStream = httpServletResponse.getOutputStream();
            writeJavaScriptResource(helperJsInputStreamCache.getInputStream(), outputStream);
        } catch (Exception e) {
            LOGGER.error("helpJs error", e);
        }
    }

    private void writeHtmlResource(byte[] injectedHeader, InputStream inputStream, OutputStream outputStream) {
        httpServletResponse.setContentType("text/html; charset=UTF-8");
        IoUtils.write(injectedHeader, inputStream, outputStream);
    }

    private void writeJavaScriptResource(InputStream inputStream, OutputStream outputStream) {
        httpServletResponse.setContentType("application/javascript");
        IoUtils.write(inputStream, outputStream);
    }
}
