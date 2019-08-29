package io.github.javahub.web.config;

import java.util.concurrent.TimeUnit;

import javax.servlet.Filter;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.javahub.web.filter.LogFilter;
import okhttp3.ConnectionPool;
import okhttp3.OkHttpClient;

@EnableAutoConfiguration
@Configuration
public class BeanConfig {
    @Bean
    public FilterRegistrationBean<Filter> registFilter() {
        FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new LogFilter());
        registration.addUrlPatterns("/*");
        registration.setName("logFilter");
        registration.setOrder(1);
        return registration;
    }
    
    @Bean(name = "okHttpClient")
    public OkHttpClient getOkHttpClient() {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.readTimeout(40000, TimeUnit.MILLISECONDS).connectTimeout(40000, TimeUnit.MILLISECONDS);
        builder.connectionPool(new ConnectionPool(10, 5, TimeUnit.MINUTES));
        return builder.build();
    }

}