package io.github.javahub.web.util;

import java.util.UUID;

public class UuidUtils {

    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "").toLowerCase();
    }
}
