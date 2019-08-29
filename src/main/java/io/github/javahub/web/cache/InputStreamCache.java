package io.github.javahub.web.cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * ProjectName: Kosan
 * <p>
 *
 * <p>
 *
 * @author jiangdq
 * <p>
 * @date 2019-08-04 00:10
 */
public class InputStreamCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(InputStreamCache.class);

    /**
     * 将InputStream中的字节保存到ByteArrayOutputStream中。
     */
    private ByteArrayOutputStream byteArrayOutputStream = null;

    public InputStreamCache(InputStream inputStream) {
        if (ObjectUtils.isEmpty(inputStream)) {
            return;
        }

        byteArrayOutputStream = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int len;
        try {
            while ((len = inputStream.read(buffer)) > -1) {
                byteArrayOutputStream.write(buffer, 0, len);
            }
            byteArrayOutputStream.flush();
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
        }
    }

    public InputStream getInputStream() {
        if (ObjectUtils.isEmpty(byteArrayOutputStream)) {
            return null;
        }

        return new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
    }
}
