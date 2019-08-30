package io.github.javahub.web.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletResponse;
import java.io.InputStream;
import java.io.OutputStream;

public class IoUtils {

    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(IoUtils.class);

    public static void write(InputStream inputStream, OutputStream outputStream) {
        try {
            byte[] data = new byte[102400];
            int len;
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

    public static void writeHtml(HttpServletResponse httpServletResponse, InputStream inputStream) {
        try {
            httpServletResponse.setContentType("text/html; charset=UTF-8");
            write(inputStream, httpServletResponse.getOutputStream());
        } catch (Exception e) {
            LOGGER.error("writeHtml error", e);
        }
    }
}
