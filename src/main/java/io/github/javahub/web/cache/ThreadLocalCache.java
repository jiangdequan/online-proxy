package io.github.javahub.web.cache;

public class ThreadLocalCache {
    private static final ThreadLocal<byte[]> INJECT_HTML = new ThreadLocal<>();
    private static final ThreadLocal<String> HOSTS = new ThreadLocal<>();

    public static void cacheCurrentHost(String host) {
        HOSTS.set(host);
    }

    public static String getCurrentHost() {
        return HOSTS.get();
    }
    public static void cacheHtml(byte[] htmlBytes) {
        INJECT_HTML.set(htmlBytes);
    }

    public static byte[] getHtml() {
        return INJECT_HTML.get();
    }
}
