package io.github.javahub.web.cache;

/**
 * ProjectName: Kosan
 * <p>
 *
 * <p>
 *
 * @author jiangdq
 * <p>
 * @date 2019-08-21 08:37
 */
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
