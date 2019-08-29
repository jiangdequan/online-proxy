package io.github.javahub.web.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * ProjectName: Kosan
 * <p>
 *
 * <p>
 *
 * @author jiangdq
 * <p>
 * @date 2019-08-03 22:35
 */
public class IoUtils {

    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IoUtils.class);

    public static void write(byte[] injectedHeader, InputStream inputStream, OutputStream outputStream) {
        try {
            byte[] data = new byte[4096];
            int len;
            if (null != injectedHeader) {
                outputStream.write(injectedHeader);
            }
            while (-1 != (len = inputStream.read(data))) {
                outputStream.write(data, 0, len);
            }
            outputStream.flush();
        } catch (Exception e) {
            LOGGER.error("write error", e);
        } finally {
            try {
                inputStream.close();
                outputStream.close();
            } catch (Exception e) {
                LOGGER.error("outputStream.close() error", e);
            }
        }
    }

    public static void write(InputStream inputStream, OutputStream outputStream) {
        write(null, inputStream, outputStream);
    }

    public static byte[] gzip(byte[] data) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            GZIPOutputStream gzip = new GZIPOutputStream(bos);
            gzip.write(data);
            gzip.finish();
            gzip.close();
            byte[] ret = bos.toByteArray();
            bos.close();
            return ret;
        } catch (Exception e) {

        }
        return null;
    }

    public static byte[] ungzip(byte[] data) throws Exception {
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        GZIPInputStream gzip = new GZIPInputStream(bis);
        byte[] buf = new byte[1024];
        int num = -1;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((num = gzip.read(buf, 0, buf.length)) != -1) {
            bos.write(buf, 0, num);
        }
        gzip.close();
        bis.close();
        byte[] ret = bos.toByteArray();
        bos.flush();
        bos.close();
        return ret;
    }
}
