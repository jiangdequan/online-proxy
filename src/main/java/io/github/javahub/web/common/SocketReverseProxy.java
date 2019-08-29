package io.github.javahub.web.common;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;

public class SocketReverseProxy {

    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(SocketReverseProxy.class);

    private HttpServletRequest httpServletRequest;

    private HttpServletResponse httpServletResponse;

    private String realUrl;

    private OutputStream outputStream;

    private URI realUri;

    private byte[] injectHtml;

    private byte[] gzipInjectHtml;

    private byte[] chunkedGzipInjectHtml;

    public SocketReverseProxy(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse,
                              String realUrl) {
        this.httpServletRequest = httpServletRequest;
        this.httpServletResponse = httpServletResponse;
        this.realUrl = realUrl;
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

    public void tunnel(byte[] injectHtml, byte[] gzipInjectHtml, byte[] chunkedGzipInjectHtml) {
        this.injectHtml = injectHtml;
        this.gzipInjectHtml = gzipInjectHtml;
        this.chunkedGzipInjectHtml = chunkedGzipInjectHtml;

        String scheme = realUri.getScheme();
        String host = realUri.getHost();
        int port = realUri.getPort();
        String requestMessage = generateRequestMessage();
        switch (scheme) {
            case "https":
                port = (-1 == port) ? 443 : port;
                tunnel4Https(host, port, requestMessage);
                break;
            case "http":
                port = (-1 == port) ? 80 : port;
                tunnel4Http(host, port, requestMessage);
                break;
            default:

        }
    }

    private void tunnel4Https(String host, int port, String requestMessage) {
        try (Socket remoteSocket = SSLSocketFactory.getDefault().createSocket(host, port);
             InputStream remoteInputStream = remoteSocket.getInputStream();
             OutputStream remoteOutputStream = remoteSocket.getOutputStream();) {

            // timeout in 8s
            remoteSocket.setSoTimeout(8000);

            // send the mock header
            remoteOutputStream.write(requestMessage.getBytes());
            remoteOutputStream.flush();

            // remote to client
            pipe2(remoteInputStream, outputStream);
        } catch (UnknownHostException e) {
            LOGGER.error("tunnel4Https error", e);
        } catch (IOException e) {
            LOGGER.error("tunnel4Https error", e);
        }
    }

    private void tunnel4Http(String host, int port, String requestMessage) {
        try (Socket remoteSocket = SocketFactory.getDefault().createSocket(host, port);
             InputStream remoteInputStream = remoteSocket.getInputStream();
             OutputStream remoteOutputStream = remoteSocket.getOutputStream();) {

            // timeout in 8s
            remoteSocket.setSoTimeout(8000);

            // send the mock header
            remoteOutputStream.write(requestMessage.getBytes());
            remoteOutputStream.flush();

            // remote to client
            pipe2(remoteInputStream, outputStream);
        } catch (UnknownHostException e) {
            LOGGER.error("tunnel4Http error", e);
        } catch (IOException e) {
            LOGGER.error("tunnel4Http error", e);
        }
    }

    private void pipe2(InputStream inputStream, OutputStream outputStream) {
        try {
            int b;
            List<Byte> responseHeaders = new ArrayList<>();
            List<Byte> lines = new ArrayList<>();
            List<String> responseMessage = new ArrayList<>();
            boolean flag = false;
            Stack<Integer> stack = new Stack();
            stack.push(10);
            stack.push(13);
            stack.push(10);
            while (-1 != (b = inputStream.read())) {
                byte bt = (byte) b;
                lines.add(bt);
                if (b == 10) {
                    flag = true;
                    byte[] byteArray = list2Array(lines);
                    responseMessage.add(new String(byteArray, "UTF-8"));
                    System.out.print(new String(byteArray, "UTF-8"));
                    responseHeaders.addAll(lines);
                    lines.clear();
                }
                if (flag) {
                    if (stack.pop() != b) {
                        flag = false;
                        stack.removeAllElements();
                        stack.push(10);
                        stack.push(13);
                        stack.push(10);
                        continue;
                    }
                    if (stack.empty()) {
                        break;
                    }
                }
            }

            Map<String, String> headerMap = headerMap(responseMessage);
            byte[] injectHtml = handleInjectHtml(headerMap);

            // write response message
            byte[] messageArray = list2Array(responseHeaders);
            headerMap.forEach((k, v) -> {
                httpServletResponse.addHeader(k, v);
            });
//            outputStream.write(messageArray);

            // if content-type is html, then write inject html
            if (null != injectHtml) {
                outputStream.write(injectHtml);
            }

            // write the response body
            byte[] data = new byte[4096];
            int len;
            while (-1 != (len = inputStream.read(data))) {
                outputStream.write(data, 0, len);
            }
            outputStream.flush();
        } catch (Exception e) {
            LOGGER.error("pipe error", e);
        }
    }

    private byte[] handleInjectHtml(Map<String, String> headerMap) {
        String contentType = headerMap.get("content-type");
        if (!StringUtils.containsIgnoreCase(contentType, "html")) {
            return null;
        }

        byte[] finalInjectHtml = injectHtml;
        String contentEncoding = headerMap.get("content-encoding");
        if (StringUtils.equalsIgnoreCase(contentEncoding, "gzip")) {
            finalInjectHtml = gzipInjectHtml;
        }

        String transferEncoding = headerMap.get("transfer-encoding");
        if (StringUtils.equalsIgnoreCase(transferEncoding, "chunked")) {
            finalInjectHtml = chunkedGzipInjectHtml;
        }
        return finalInjectHtml;
    }

    private Map<String, String> headerMap(List<String> responseMessage) {
        Map<String, String> headerMap = new HashMap<>();
        for (String line : responseMessage) {
            if (!StringUtils.contains(line, ": ")) {
                continue;
            }
            String[] headerData = StringUtils.split(line, ": ");
            headerMap.put(headerData[0].toLowerCase(), headerData[1]);
        }
        return headerMap;
    }

    private byte[] list2Array(List<Byte> byteList) {
        byte[] bytes = new byte[byteList.size()];
        for (int i = 0; i < bytes.length; i++) {
            bytes[i] = byteList.get(i);
        }
        return bytes;
    }

    private void pipe(InputStream inputStream, OutputStream outputStream) {
        try {
            byte[] data = new byte[4096];
            int len;
            while (-1 != (len = inputStream.read(data))) {
                outputStream.write(data, 0, len);
            }
            outputStream.flush();
        } catch (Exception e) {
            LOGGER.error("pipe error", e);
        }
    }

    private String generateRequestMessage() {
        String host = getHost();
        String requestLine = generateRequestLine();
        String requestHeaders = generateRequestHeaders(host);
        String queryString = realUri.getQuery();
        StringBuilder requestMessage = new StringBuilder();
        requestMessage.append(requestLine)
                .append(requestHeaders).append("\r\n");
        if (StringUtils.isNotBlank(queryString)) {
            requestMessage.append("\r\n").append(queryString);
        }
        return requestMessage.toString();
    }

    private String getHost() {
        String host = realUri.getHost();
        int port = realUri.getPort();
        if (-1 == port) {
            return host;
        }
        StringBuilder realHost = new StringBuilder();
        realHost.append(host).append(":").append(port);
        return realHost.toString();
    }

    private String generateRequestLine() {
        String method = httpServletRequest.getMethod();
        String uri = realUri.getPath();
        String protocol = httpServletRequest.getProtocol();
        StringBuilder requestLine = new StringBuilder();
        requestLine.append(method).append(" ").append(uri).append(" ").append(protocol).append("\r\n");
        return requestLine.toString();
    }

    private String generateRequestHeaders(String host) {
        StringBuilder requestHeaders = new StringBuilder();
        Enumeration<String> headerNames = httpServletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String value = httpServletRequest.getHeader(headerName);
            // change the host to remote server
            if (StringUtils.equalsIgnoreCase("host", headerName)) {
                value = host;
            }
            // add header
            requestHeaders.append(headerName).append(": ").append(value).append("\r\n");
        }
        return requestHeaders.toString();
    }
}
