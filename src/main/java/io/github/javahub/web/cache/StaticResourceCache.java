package io.github.javahub.web.cache;

import io.github.javahub.web.controller.proxy.OnlineProxyController;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class StaticResourceCache {

    private static Map<String, InputStreamCache> RESOURCES = new HashMap<>();

    static {
        // cache helper.js
        InputStream inputStream = StaticResourceCache.class.getClassLoader().getResourceAsStream("static/js/helper.js");
        RESOURCES.put("helper", new InputStreamCache(inputStream));

        // cache sw.js
        inputStream = OnlineProxyController.class.getClassLoader().getResourceAsStream("static/js/sw.js");
        RESOURCES.put("sw", new InputStreamCache(inputStream));

        // cache conf.js
        inputStream = OnlineProxyController.class.getClassLoader().getResourceAsStream("static/js/conf.js");
        RESOURCES.put("conf", new InputStreamCache(inputStream));

        // cache bundle.c33e24c5.js
        inputStream = OnlineProxyController.class.getClassLoader().getResourceAsStream("static/js/bundle.c33e24c5.js");
        RESOURCES.put("bundle", new InputStreamCache(inputStream));

        // cache home page
        inputStream = OnlineProxyController.class.getClassLoader().getResourceAsStream("templates/test.html");
        RESOURCES.put("index", new InputStreamCache(inputStream));
    }

    public static InputStreamCache get(String key) {
        return RESOURCES.get(key);
    }
}
