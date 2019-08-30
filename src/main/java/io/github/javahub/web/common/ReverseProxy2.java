package io.github.javahub.web.common;

import io.github.javahub.web.cache.ThreadLocalCache;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.CookieSpecs;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.AbortableHttpRequest;
import org.apache.http.config.SocketConfig;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.apache.http.message.BasicHttpEntityEnclosingRequest;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.message.HeaderGroup;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpCookie;
import java.net.URI;
import java.util.BitSet;
import java.util.Enumeration;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Map;

public class ReverseProxy2 {

    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(ReverseProxy2.class);

    /**
     * User agents shouldn't send the url fragment but what if it does?
     */
    protected boolean doSendUrlFragment = true;

    protected boolean doPreserveHost = false;

    protected boolean doPreserveCookies = false;

    protected boolean doForwardIP = true;

    private HttpClient proxyClient = createHttpClient();

    private Map<String, String> originalHeaders = new HashMap<>();

    private Map<String, String> modifiedHeaders = new HashMap<>();

    public void service(HttpServletRequest servletRequest, HttpServletResponse servletResponse) throws Exception {
        Enumeration<String> headerNames = servletRequest.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            String value = servletRequest.getHeader(name);
            // modified header
            if (StringUtils.startsWith(name, "--")) {
                name = StringUtils.replace(name, "--", "");
                modifiedHeaders.put(name, value);
            } else {
                // original header
                originalHeaders.put(name, value);
            }
            System.out.println(name + ": " + value);
        }
        // request method
        String method = servletRequest.getMethod();
        String proxyRequestUri = rewriteUrlFromRequest(servletRequest);
        LOGGER.info("proxyRequestUri from method rewriteUrlFromRequest: " + proxyRequestUri);
        HttpRequest proxyRequest;
        // spec: RFC 2616, sec 4.3: either of these two headers signal that there is a message body.
        if (servletRequest.getHeader(HttpHeaders.CONTENT_LENGTH) != null
                || servletRequest.getHeader(HttpHeaders.TRANSFER_ENCODING) != null) {
            proxyRequest = newProxyRequestWithEntity(method, proxyRequestUri, servletRequest);
        } else {
            proxyRequest = new BasicHttpRequest(method, proxyRequestUri);
        }

        copyRequestHeaders(servletRequest, proxyRequest);

        setXForwardedForHeader(servletRequest, proxyRequest);

        HttpResponse proxyResponse = null;
        try {
            // Execute the request
            proxyResponse = doExecute(servletRequest, servletResponse, proxyRequest);

            // Process the response:
            // Pass the response code. This method with the "reason phrase" is deprecated
            // but it's the
            // only way to pass the reason along too.
            int statusCode = proxyResponse.getStatusLine().getStatusCode();
            // noinspection deprecation
            servletResponse.setStatus(statusCode, proxyResponse.getStatusLine().getReasonPhrase());

            // Copying response headers to make sure SESSIONID or other Cookie which comes
            // from the remote server will be saved in client when the proxied url was
            // redirected to another one. See issue
            // [#51](https://github.com/mitre/HTTP-Proxy-Servlet/issues/51)
            copyResponseHeaders(proxyResponse, servletRequest, servletResponse);

            if (statusCode == HttpServletResponse.SC_NOT_MODIFIED) {
                // 304 needs special handling. See:
                // http://www.ics.uci.edu/pub/ietf/http/rfc1945.html#Code304
                // Don't send body entity/content!
                servletResponse.setIntHeader(HttpHeaders.CONTENT_LENGTH, 0);
            } else {
                // Send the content to the client
                copyResponseEntity(proxyResponse, servletResponse, proxyRequest, servletRequest);
            }

        } catch (Exception e) {
            handleRequestException(proxyRequest, e);
        } finally {
            // make sure the entire entity was consumed, so the connection is released
            if (proxyResponse != null) {
                EntityUtils.consumeQuietly(proxyResponse.getEntity());
            }
            // Note: Don't need to close servlet outputStream:
            // http://stackoverflow.com/questions/1159168/should-one-call-close-on-httpservletresponse-getoutputstream-getwriter
        }
    }

    protected void handleRequestException(HttpRequest proxyRequest, Exception e) throws ServletException, IOException {
        // abort request, according to best practice with HttpClient
        if (proxyRequest instanceof AbortableHttpRequest) {
            AbortableHttpRequest abortableHttpRequest = (AbortableHttpRequest) proxyRequest;
            abortableHttpRequest.abort();
        }
        if (e instanceof RuntimeException) {
            throw (RuntimeException) e;
        }
        if (e instanceof ServletException) {
            throw (ServletException) e;
        }
        // noinspection ConstantConditions
        if (e instanceof IOException) {
            throw (IOException) e;
        }
        throw new RuntimeException(e);
    }

    /**
     * Copy proxied response headers back to the servlet client.
     */
    protected void copyResponseHeaders(HttpResponse proxyResponse, HttpServletRequest servletRequest,
                                       HttpServletResponse servletResponse) {
        for (Header header : proxyResponse.getAllHeaders()) {
            copyResponseHeader(servletRequest, servletResponse, header);
        }
    }

    /**
     * Copy a proxied response header back to the servlet client. This is easily
     * overwritten to filter out certain headers if desired.
     */
    protected void copyResponseHeader(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
                                      Header header) {
        String headerName = header.getName();
        if (hopByHopHeaders.containsHeader(headerName)) {
            return;
        }
        String headerValue = header.getValue();
        if (headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE)
                || headerName.equalsIgnoreCase(org.apache.http.cookie.SM.SET_COOKIE2)) {
            copyProxyCookie(servletRequest, servletResponse, headerValue);
        } else if (headerName.equalsIgnoreCase(HttpHeaders.LOCATION)) {
            // LOCATION Header may have to be rewritten.
            servletResponse.addHeader(headerName, rewriteUrlFromResponse(servletRequest, headerValue));
        } else {
            servletResponse.addHeader(headerName, headerValue);
        }
    }

    /**
     * Copy response body data (the entity) from the proxy to the servlet client.
     */
    protected void copyResponseEntity(HttpResponse proxyResponse, HttpServletResponse servletResponse,
                                      HttpRequest proxyRequest, HttpServletRequest servletRequest) throws IOException {
        HttpEntity entity = proxyResponse.getEntity();
        if (entity != null) {
            OutputStream servletOutputStream = servletResponse.getOutputStream();
//            injectHtml(proxyResponse, servletOutputStream);
            entity.writeTo(servletOutputStream);
        }
    }

    /**
     * For a redirect response from the target server, this translates
     * {@code theUrl} to redirect to and translates it to one the original client
     * can use.
     */
    protected String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {
        // TODO document example paths
        final String targetUri = getTargetUri(servletRequest);
        if (theUrl.startsWith(targetUri)) {
            /*-
             * The URL points back to the back-end server.
             * Instead of returning it verbatim we replace the target path with our
             * source path in a way that should instruct the original client to
             * request the URL pointed through this Proxy.
             * We do this by taking the current request and rewriting the path part
             * using this servlet's absolute path and the path from the returned URL
             * after the base target URL.
             */
            StringBuffer curUrl = servletRequest.getRequestURL();// no query
            int pos;
            // Skip the protocol part
            if ((pos = curUrl.indexOf("://")) >= 0) {
                // Skip the authority part
                // + 3 to skip the separator between protocol and authority
                if ((pos = curUrl.indexOf("/", pos + 3)) >= 0) {
                    // Trim everything after the authority part.
                    curUrl.setLength(pos);
                }
            }
            // Context path starts with a / if it is not blank
            curUrl.append(servletRequest.getContextPath());
            // Servlet path starts with a / if it is not blank
            curUrl.append(servletRequest.getServletPath());
            curUrl.append(theUrl, targetUri.length(), theUrl.length());
            return curUrl.toString();
        }
        return theUrl;
    }

    /**
     * Copy cookie from the proxy to the servlet client. Replaces cookie path to
     * local path and renames cookie to avoid collisions.
     */
    protected void copyProxyCookie(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
                                   String headerValue) {
        // build path for resulting cookie
        String path = servletRequest.getContextPath(); // path starts with / or is empty string
        path += servletRequest.getServletPath(); // servlet path starts with / or is empty string
        if (path.isEmpty()) {
            path = "/";
        }

        for (HttpCookie cookie : HttpCookie.parse(headerValue)) {
            // set cookie name prefixed w/ a proxy value so it won't collide w/ other
            // cookies
            String proxyCookieName = doPreserveCookies ? cookie.getName()
                    : getCookieNamePrefix(cookie.getName()) + cookie.getName();
            Cookie servletCookie = new Cookie(proxyCookieName, cookie.getValue());
            servletCookie.setComment(cookie.getComment());
            servletCookie.setMaxAge((int) cookie.getMaxAge());
            servletCookie.setPath(path); // set to the path of the proxy servlet
            // don't set cookie domain
            servletCookie.setSecure(cookie.getSecure());
            servletCookie.setVersion(cookie.getVersion());
            servletCookie.setHttpOnly(cookie.isHttpOnly());
            servletResponse.addCookie(servletCookie);
        }
    }

    protected boolean doLog = false;

    protected HttpResponse doExecute(HttpServletRequest servletRequest, HttpServletResponse servletResponse,
                                     HttpRequest proxyRequest) throws IOException {
        if (doLog) {
            LOGGER.info("proxy " + servletRequest.getMethod() + " uri: " + servletRequest.getRequestURI() + " -- "
                    + proxyRequest.getRequestLine().getUri());
        }
        return proxyClient.execute(getTargetHost(servletRequest), proxyRequest);
    }

    /**
     * Called from {@link #init(javax.servlet.ServletConfig)}. HttpClient offers
     * many opportunities for customization. In any case, it should be thread-safe.
     */
    protected HttpClient createHttpClient() {
        HttpClientBuilder clientBuilder = HttpClientBuilder.create().setDefaultRequestConfig(buildRequestConfig())
                .setDefaultSocketConfig(buildSocketConfig());

        clientBuilder.setMaxConnTotal(10);

        if (false) {
            clientBuilder = clientBuilder.useSystemProperties();
        }
        return clientBuilder.build();
    }

    /**
     * Sub-classes can override specific behaviour of
     * {@link RequestConfig}.
     */
    protected RequestConfig buildRequestConfig() {
        // we handle them in the servlet instead
        return RequestConfig.custom().setRedirectsEnabled(false).setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                .setConnectTimeout(5000).setSocketTimeout(10000).setConnectionRequestTimeout(5000).build();
    }

    /**
     * Sub-classes can override specific behaviour of
     * {@link SocketConfig}.
     */
    protected SocketConfig buildSocketConfig() {

        return SocketConfig.custom().setSoTimeout(5000).build();
    }

    private void setXForwardedForHeader(HttpServletRequest servletRequest, HttpRequest proxyRequest) {
        if (doForwardIP) {
            String forHeaderName = "X-Forwarded-For";
            String forHeader = servletRequest.getRemoteAddr();
            String existingForHeader = servletRequest.getHeader(forHeaderName);
            if (existingForHeader != null) {
                forHeader = existingForHeader + ", " + forHeader;
            }
            // LOGGER.info("setXForwardedForHeader, " + forHeaderName + ": " + forHeader);
            proxyRequest.setHeader(forHeaderName, forHeader);

            String protoHeaderName = "X-Forwarded-Proto";
            String protoHeader = servletRequest.getScheme();
            // LOGGER.info("setXForwardedForHeader, " + protoHeaderName + ": " + protoHeader);
            proxyRequest.setHeader(protoHeaderName, protoHeader);
        }
    }

    /**
     * Copy request headers from the servlet client to the proxy request. This is
     * easily overridden to add your own.
     */
    protected void copyRequestHeaders(HttpServletRequest servletRequest, HttpRequest proxyRequest) {
        // Get an Enumeration of all of the header names sent by the client
        Enumeration<String> enumerationOfHeaderNames = servletRequest.getHeaderNames();
        while (enumerationOfHeaderNames.hasMoreElements()) {
            String headerName = enumerationOfHeaderNames.nextElement();
            copyRequestHeader(servletRequest, proxyRequest, headerName);
        }
    }

    /**
     * Copy a request header from the servlet client to the proxy request. This is
     * easily overridden to filter out certain headers if desired.
     */
    protected void copyRequestHeader(HttpServletRequest servletRequest, HttpRequest proxyRequest, String headerName) {
        // Instead the content-length is effectively set via InputStreamEntity
        if (headerName.equalsIgnoreCase(HttpHeaders.CONTENT_LENGTH)) {
            return;
        }
        if (hopByHopHeaders.containsHeader(headerName)) {
            return;
        }

        Enumeration<String> headers = servletRequest.getHeaders(headerName);

        // sometimes more than one value
        while (headers.hasMoreElements()) {
            String headerValue = headers.nextElement();
            // In case the proxy host is running multiple virtual servers,
            // rewrite the Host header to ensure that we get content from
            // the correct virtual server
            if (!doPreserveHost && headerName.equalsIgnoreCase(HttpHeaders.HOST)) {
                HttpHost host = getTargetHost(servletRequest);
                headerValue = host.getHostName();
                if (host.getPort() != -1) {
                    headerValue += ":" + host.getPort();
                }
            } else if (!doPreserveCookies && headerName.equalsIgnoreCase(org.apache.http.cookie.SM.COOKIE)) {
                headerValue = getRealCookie(headerValue);
            }
            if (StringUtils.equalsIgnoreCase(headerName, "host")
                    && (StringUtils.equalsIgnoreCase(headerValue, ThreadLocalCache.getCurrentHost())
                    || StringUtils.startsWithIgnoreCase(headerValue, ThreadLocalCache.getCurrentHost()))) {
                headerValue = ThreadLocalCache.getCurrentHost();
            }
//             LOGGER.info("copyRequestHeader," + headerName + ": " + headerValue);
            proxyRequest.addHeader(headerName, headerValue);
        }
    }

    /**
     * Take any client cookies that were originally from the proxy and prepare them
     * to send to the proxy. This relies on cookie headers being set correctly
     * according to RFC 6265 Sec 5.4. This also blocks any local cookies from being
     * sent to the proxy.
     */
    protected String getRealCookie(String cookieValue) {
        StringBuilder escapedCookie = new StringBuilder();
        String cookies[] = cookieValue.split("[;,]");
        for (String cookie : cookies) {
            String cookieSplit[] = cookie.split("=");
            if (cookieSplit.length == 2) {
                String cookieName = cookieSplit[0].trim();
                if (cookieName.startsWith(getCookieNamePrefix(cookieName))) {
                    cookieName = cookieName.substring(getCookieNamePrefix(cookieName).length());
                    if (escapedCookie.length() > 0) {
                        escapedCookie.append("; ");
                    }
                    escapedCookie.append(cookieName).append("=").append(cookieSplit[1].trim());
                }
            }
        }
        return escapedCookie.toString();
    }

    /**
     * The string prefixing rewritten cookies.
     */
    protected String getCookieNamePrefix(String name) {
        // TODO
        return "";
    }

    /**
     * @param servletRequest
     * @return
     */
    protected HttpHost getTargetHost(HttpServletRequest servletRequest) {
        String requestUri = getTargetUri(servletRequest);
        String host = "";
        int port = 443;
        String scheme = "https";
        int startIndex = -1;
        if (-1 != (startIndex = StringUtils.indexOf(requestUri, ":/"))) {
            scheme = StringUtils.substring(requestUri, 0, startIndex);
            requestUri = StringUtils.substring(requestUri, startIndex + 2);
        }
        if (!StringUtils.startsWith(requestUri, "/")) {
            requestUri = "/" + requestUri;
        }
        host = StringUtils.substringBetween(requestUri, "/", "/");
        port = StringUtils.equalsIgnoreCase(scheme, "https") ? 443 : 80;
        ThreadLocalCache.cacheCurrentHost(host);
        if (StringUtils.contains(host, ":")) {
            String[] datas = StringUtils.split(host, ":");
            host = datas[0];
            port = Integer.parseInt(datas[1]);
        }
        return new HttpHost(host, port, scheme);
    }

    public static void main(String[] args) {
        String text = "/dlswbr.baidu.com:8888/heicha/mw/abclite-2016-s.js";
        String requestUri = text;
        String host = "";
        int port = 443;
        String scheme = "https";
        int startIndex = -1;
        if (-1 != (startIndex = StringUtils.indexOf(requestUri, ":/"))) {
            scheme = StringUtils.substring(requestUri, 0, startIndex);
            requestUri = StringUtils.substring(requestUri, startIndex + 2);
        }
        if (!StringUtils.startsWith(requestUri, "/")) {
            requestUri = "/" + requestUri;
        }
        host = StringUtils.substringBetween(requestUri, "/", "/");

        port = StringUtils.equalsIgnoreCase(scheme, "https") ? 443 : 80;
        if (StringUtils.contains(host, ":")) {
            String[] datas = StringUtils.split(host, ":");
            host = datas[0];
            port = Integer.parseInt(datas[1]);
        }
        System.out.println(host);
        System.out.println(port);
        System.out.println(scheme);


//        String result = StringUtils.substringBetween(text, "/", "/");
//        System.out.println(result);
//        try {
//            URI uri = new URI("https://dlswbr.baidu.com/heicha/mw/abclite-2016-s.js");
//            System.out.println(uri.getHost());
//            System.out.println(uri.getPort());
//            System.out.println(uri.getScheme());
//        } catch (Exception e) {
//            LOGGER.error("getTargetHost error", e);
//        }
    }

    protected HttpRequest newProxyRequestWithEntity(String method, String proxyRequestUri,
                                                    HttpServletRequest servletRequest) throws IOException {
        HttpEntityEnclosingRequest eProxyRequest = new BasicHttpEntityEnclosingRequest(method, proxyRequestUri);
        // Add the input entity (streamed) note: we don't bother ensuring we close the servletInputStream since the container handles it
        eProxyRequest
                .setEntity(new InputStreamEntity(servletRequest.getInputStream(), getContentLength(servletRequest)));
        return eProxyRequest;
    }

    /**
     * Get the header value as a long in order to more correctly proxy very large requests
     * @param request
     * @return
     */
    private long getContentLength(HttpServletRequest request) {
        String contentLengthHeader = request.getHeader("Content-Length");
        if (contentLengthHeader != null) {
            return Long.parseLong(contentLengthHeader);
        }
        return -1L;
    }

    /**
     * Reads the request URI from {@code servletRequest} and rewrites it,
     * considering targetUri. It's used to make the new request.
     */
    protected String rewriteUrlFromRequest(HttpServletRequest servletRequest) {
        StringBuilder uri = new StringBuilder(500);
        uri.append(getTargetUri(servletRequest));
        // Handle the path given to the servlet
        String pathInfo = rewritePathInfoFromRequest(servletRequest);
        // ex: /my/path.html
        if (pathInfo != null) {
            // getPathInfo() returns decoded string, so we need encodeUriQuery to encode "%"
            // characters
            uri.append(encodeUriQuery(pathInfo, true));
        }
        // Handle the query string & fragment
        String queryString = servletRequest.getQueryString();// ex:(following '?'): name=value&foo=bar#fragment
        String fragment = null;
        // split off fragment from queryString, updating queryString if found
        if (queryString != null) {
            int fragIdx = queryString.indexOf('#');
            if (fragIdx >= 0) {
                fragment = queryString.substring(fragIdx + 1);
                queryString = queryString.substring(0, fragIdx);
            }
        }

        queryString = rewriteQueryStringFromRequest(servletRequest, queryString);
        if (queryString != null && queryString.length() > 0) {
            uri.append('?');
            // queryString is not decoded, so we need encodeUriQuery not to encode "%"
            // characters, to avoid double-encoding
            uri.append(encodeUriQuery(queryString, false));
        }

        if (doSendUrlFragment && fragment != null) {
            uri.append('#');
            // fragment is not decoded, so we need encodeUriQuery not to encode "%"
            // characters, to avoid double-encoding
            uri.append(encodeUriQuery(fragment, false));
        }
        return uri.toString();
    }

    /**
     * @param servletRequest
     * @return
     */
    protected String getTargetUri(HttpServletRequest servletRequest) {
        return modifiedHeaders.get("url");
//        String requestUri = servletRequest.getRequestURI();
//        if (StringUtils.startsWith(requestUri, "/-----")) {
//            requestUri = StringUtils.replace(requestUri, "/-----", "");
//        }
//        if (!StringUtils.containsIgnoreCase(requestUri, OnlineProxyController.domain)) {
//            return requestUri;
//        }
//        requestUri = servletRequest.getHeader("referer");
//        int startIndex = -1;
//        if (StringUtils.isNotBlank(requestUri) && -1 != (startIndex = StringUtils.indexOf(requestUri, "/-----"))) {
//            return StringUtils.substring(requestUri, startIndex + 6);
//        }
//        return null;
    }

    /**
     * Allow overrides of
     * {@link HttpServletRequest#getPathInfo()}. Useful when
     * url-pattern of servlet-mapping (web.xml) requires manipulation.
     */
    protected String rewritePathInfoFromRequest(HttpServletRequest servletRequest) {
        return servletRequest.getPathInfo();
    }

    /**
     * Encodes characters in the query or fragment part of the URI.
     *
     * <p>
     * Unfortunately, an incoming URI sometimes has characters disallowed by the
     * spec. HttpClient insists that the outgoing proxied request has a valid URI
     * because it uses Java's {@link URI}. To be more forgiving, we must escape the
     * problematic characters. See the URI class for the spec.
     *
     * @param in            example: name=value&amp;foo=bar#fragment
     * @param encodePercent determine whether percent characters need to be encoded
     */
    protected CharSequence encodeUriQuery(CharSequence in, boolean encodePercent) {
        // Note that I can't simply use URI.java to encode because it will escape
        // pre-existing escaped things.
        StringBuilder outBuf = null;
        Formatter formatter = null;
        for (int i = 0; i < in.length(); i++) {
            char c = in.charAt(i);
            boolean escape = true;
            if (c < 128) {
                if (asciiQueryChars.get((int) c) && !(encodePercent && c == '%')) {
                    escape = false;
                }
            } else if (!Character.isISOControl(c) && !Character.isSpaceChar(c)) {// not-ascii
                escape = false;
            }
            if (!escape) {
                if (outBuf != null) {
                    outBuf.append(c);
                }
            } else {
                // escape
                if (outBuf == null) {
                    outBuf = new StringBuilder(in.length() + 5 * 3);
                    outBuf.append(in, 0, i);
                    formatter = new Formatter(outBuf);
                }
                // leading %, 0 padded, width 2, capital hex
                formatter.format("%%%02X", (int) c);// TODO
            }
        }
        return outBuf != null ? outBuf : in;
    }

    protected String rewriteQueryStringFromRequest(HttpServletRequest servletRequest, String queryString) {
        return queryString;
    }

    protected static final BitSet asciiQueryChars;

    static {
        // plus alphanum
        char[] c_unreserved = "_-!.~'()*".toCharArray();
        char[] c_punct = ",;:$&+=".toCharArray();
        // plus punct
        char[] c_reserved = "?/[]@".toCharArray();

        asciiQueryChars = new BitSet(128);
        for (char c = 'a'; c <= 'z'; c++) {
            asciiQueryChars.set((int) c);
        }
        for (char c = 'A'; c <= 'Z'; c++) {
            asciiQueryChars.set((int) c);
        }
        for (char c = '0'; c <= '9'; c++) {
            asciiQueryChars.set((int) c);
        }
        for (char c : c_unreserved) {
            asciiQueryChars.set((int) c);
        }
        for (char c : c_punct) {
            asciiQueryChars.set((int) c);
        }
        for (char c : c_reserved) {
            asciiQueryChars.set((int) c);
        }

        // leave existing percent escapes in place
        asciiQueryChars.set((int) '%');
    }

    protected static final HeaderGroup hopByHopHeaders;

    static {
        hopByHopHeaders = new HeaderGroup();
        String[] headers = new String[]{"Connection", "Keep-Alive", "Proxy-Authenticate", "Proxy-Authorization", "TE",
                "Trailers", "Transfer-Encoding", "Upgrade", "Referer"};
        for (String header : headers) {
            hopByHopHeaders.addHeader(new BasicHeader(header, null));
        }
    }
}
