package io.github.javahub.web.common;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Enumeration;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class ReverseProxy1 {

    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ReverseProxy.class);

    private HttpServletRequest httpServletRequest;

    private HttpServletResponse httpServletResponse;

    private String remoteUrl;

    private OkHttpClient okHttpClient;

    private OutputStream outputStream;

    private URI realUri;

    public ReverseProxy1(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                        String realUrl, OkHttpClient okHttpClient) {
        this.httpServletRequest = httpServletRequest;
        this.httpServletResponse = httpServletResponse;
        this.remoteUrl = realUrl;
        this.okHttpClient = okHttpClient;
        try {
            this.outputStream = httpServletResponse.getOutputStream();
        } catch (IOException e) {
            LOGGER.error("httpServletResponse.getOutputStream error", e);
        }
        try {
            this.realUri = new URI(realUrl);
        } catch (URISyntaxException e) {
            LOGGER.error("new URI error", e);
        }
    }


    /**
     * reverse proxy
     */
    public void execute(String proxyHelpers) {
        Response response = requestRemoteServer(remoteUrl);
        // response is null
        if (null == response || null == response.body()) {
            writeToResponse("JavaHub: request remote server meet some errors!");
            return;
        }
        copyResponseHeaders(response, proxyHelpers);
    }

    /**
     * write some text to httpServletResponse
     *
     * @param text text
     */
    private void writeToResponse(String text) {
        writeToResponse(new ByteArrayInputStream(text.getBytes()));
    }

    /**
     * write Response to httpServletResponse with stream
     *
     * @param inputStream stream of Response
     */
    private void writeToResponse(InputStream inputStream) {
        try {
            byte[] data = new byte[4096];
            int len;
            while (-1 != (len = inputStream.read(data))) {
                outputStream.write(data, 0, len);
            }
            outputStream.flush();
        } catch (Exception e) {
            LOGGER.error("writeToResponse error:", e);
        } finally {
            try {
                outputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * add Response headers to httpServletResponse
     *
     * @param response Response
     */
    private void copyResponseHeaders(Response response, String proxyHelpers) {
        boolean isHtml = false;
        boolean chunked = false;
        boolean gzip = false;
        int contentLength = 0;
        for (String name : response.headers().names()) {
            String value = response.headers().get(name);
            if (StringUtils.equalsIgnoreCase(name, "Content-Length")) {
                contentLength = Integer.valueOf(value);
                continue;
            }
            if (StringUtils.equalsIgnoreCase(name, "Content-Type")
                    && StringUtils.containsIgnoreCase(value, "html")) {
                isHtml = true;
            }
            if (StringUtils.equalsIgnoreCase(name, "Transfer-Encoding")
                    && StringUtils.containsIgnoreCase(value, "chunked")) {
                chunked = true;
            }
            if (StringUtils.equalsIgnoreCase(name, "Content-Encoding")
                    && StringUtils.containsIgnoreCase(value, "gzip")) {
                gzip = true;
            }
            httpServletResponse.addHeader(name, value);
        }
        if (!isHtml) {
            if (contentLength > 0) {
                httpServletResponse.addHeader("Content-Length", String.valueOf(contentLength));
            }
            writeToResponse(response.body().byteStream());
            return;
        }

        byte[] orgBytes = proxyHelpers.getBytes();
        if (gzip) {
            orgBytes = compress(orgBytes);
        }
        try {
//            if (contentLength > 0) {
//                httpServletResponse.addHeader("Content-Length", String.valueOf(orgBytes.length + contentLength));
//            }
//            if (chunked) {
//                outputStream.write((Integer.toHexString(orgBytes.length) + "\r\n").getBytes());
//                outputStream.write(orgBytes);
//            } else {
            outputStream.write(orgBytes);
//            }
        } catch (IOException e) {
            LOGGER.error("outputStream.write() error", e);
        }
        writeToResponse(response.body().byteStream());
    }

    /**
     * request remote url
     *
     * @param remoteUrl remote url
     * @return Response
     */
    private Response requestRemoteServer(String remoteUrl) {
        Request.Builder builder = new Request.Builder().url(remoteUrl).get();
        copyRequestHeaders(builder);
        Request request = builder.build();
        try {
            // call remote server
            return okHttpClient.newCall(request).execute();
        } catch (Exception e) {
            LOGGER.error("requestRemoteServer error:", e);
        }
        return null;
    }

    /**
     * copy the request headers to Request.Builder
     *
     * @param builder Request.Builder
     */
    private void copyRequestHeaders(Request.Builder builder) {
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String value = httpServletRequest.getHeader(headerName);
            // change the host to remote server
            if (StringUtils.equalsIgnoreCase("host", headerName)) {
                value = "";// TODO
            }
            if (StringUtils.equalsIgnoreCase("Accept-Encoding", headerName)) {
                continue;
            }
            // add header
            builder.addHeader(headerName, value);
        }
    }

    public static byte[] compress(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip;
        try {
            gzip = new GZIPOutputStream(out);
            gzip.write(bytes);
            gzip.close();
        } catch (IOException e) {
            LOGGER.error("gzip compress error.", e);
        }
        return out.toByteArray();
    }

    public static byte[] uncompress(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        try {
            GZIPInputStream ungzip = new GZIPInputStream(in);
            byte[] buffer = new byte[256];
            int n;
            while ((n = ungzip.read(buffer)) >= 0) {
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            LOGGER.error("gzip uncompress error.", e);
        }

        return out.toByteArray();
    }
}
