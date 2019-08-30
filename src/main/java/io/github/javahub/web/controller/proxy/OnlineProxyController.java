package io.github.javahub.web.controller.proxy;

import io.github.javahub.web.cache.StaticResourceCache;
import io.github.javahub.web.common.ReverseProxy2;
import io.github.javahub.web.util.IoUtils;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@RestController
public class OnlineProxyController {

    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(OnlineProxyController.class);

    /**
     * auto inject HttpServletRequest, it is threadsafe
     */
    @Autowired
    private HttpServletRequest httpServletRequest;

    /**
     * auto inject HttpServletResponse, it is threadsafe
     */
    @Autowired
    private HttpServletResponse httpServletResponse;

    /**
     * auto inject OkHttpClient, request remote server
     */
    @Resource(name = "okHttpClient")
    private OkHttpClient okHttpClient;

    @RequestMapping({"/", ""})
    public void homePage() {
        try {
            httpServletResponse.setContentType("text/html; charset=UTF-8");
            IoUtils.write(StaticResourceCache.get("index").getInputStream(), httpServletResponse.getOutputStream());
        } catch (Exception e) {
            LOGGER.error("homePage error", e);
        }
    }

    @RequestMapping(value = {"/http"})
    public void http() throws Exception {
        ReverseProxy2 reverseProxy = new ReverseProxy2();
        reverseProxy.service(httpServletRequest, httpServletResponse);
    }

    @RequestMapping(value = {"/__sys__/helper.js"})
    public void helperjs() {
        try {
            httpServletResponse.setContentType("application/javascript");
            IoUtils.write(StaticResourceCache.get("helper").getInputStream(), httpServletResponse.getOutputStream());
        } catch (Exception e) {
            LOGGER.error("helperjs error", e);
        }
    }

    @RequestMapping(value = {"/**"})
    public void helper() {
        try {
            System.out.println(httpServletRequest.getRequestURI());
            httpServletResponse.setContentType("application/javascript");
            IoUtils.write(StaticResourceCache.get("helper").getInputStream(), httpServletResponse.getOutputStream());
        } catch (Exception e) {
            LOGGER.error("helpJs error", e);
        }
    }

    @RequestMapping("/sw.js")
    public void sw() {
        try {
            httpServletResponse.setContentType("application/javascript");
            IoUtils.write(StaticResourceCache.get("helper").getInputStream(), httpServletResponse.getOutputStream());
        } catch (Exception e) {
            LOGGER.error("sw error", e);
        }
    }

    @RequestMapping("/conf.js")
    public void conf() {
        try {
            httpServletResponse.setContentType("application/javascript");
            IoUtils.write(StaticResourceCache.get("conf").getInputStream(), httpServletResponse.getOutputStream());
        } catch (Exception e) {
            LOGGER.error("conf error", e);
        }
    }

    @RequestMapping("/bundle.c33e24c5.js")
    public void bundle() {
        try {
            httpServletResponse.setContentType("application/javascript");
            IoUtils.write(StaticResourceCache.get("bundle").getInputStream(), httpServletResponse.getOutputStream());
        } catch (Exception e) {
            LOGGER.error("bundle error", e);
        }
    }
}
