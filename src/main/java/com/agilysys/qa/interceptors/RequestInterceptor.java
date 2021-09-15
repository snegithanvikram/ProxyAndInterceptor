package com.agilysys.qa.interceptors;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.extension.requestfilter.RequestFilterAction;
import com.github.tomakehurst.wiremock.extension.requestfilter.StubRequestFilter;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.Response;

public class RequestInterceptor extends StubRequestFilter {
    private static final int PROPERTY_CACHE_EXPIRE = 30;
    private static final int TENANT_CACHE_EXPIRE = 30;

    protected static final Pattern PROPERTY_PATTERN =
          Pattern.compile("/user-service/property/tenants/([0-9]+)/properties/([0-9]+)");
    protected static final Pattern TENANT_PATTERN = Pattern.compile("/user-service/tenant/tenants/([0-9]+)");

    protected static final String USER_SERVICE = "user-service";
    protected static final String ACCEPT_ENCODING = "Accept-Encoding";
    protected static final String GZIP = "gzip";

    private final Cache<PropertyCacheKey, Response> propertyCache;

    private final Cache<TenantCacheKey, Response> tenantCache;

    private static RequestInterceptor instance = null;

    public RequestInterceptor() {
        propertyCache =
              Caffeine.newBuilder().expireAfterWrite(PROPERTY_CACHE_EXPIRE, TimeUnit.MINUTES).maximumSize(3000).build();
        tenantCache =
              Caffeine.newBuilder().expireAfterWrite(TENANT_CACHE_EXPIRE, TimeUnit.MINUTES).maximumSize(1000).build();
    }

    @Override
    public RequestFilterAction filter(Request request) {

        if (request.getMethod().isOneOf(RequestMethod.GET) && request.getUrl().contains(USER_SERVICE)) {
            Matcher propertyMatcher = PROPERTY_PATTERN.matcher(request.getUrl());
            Matcher tenantMatcher = TENANT_PATTERN.matcher(request.getUrl());

            if (propertyMatcher.matches()) {

                propertyMatcher = PROPERTY_PATTERN.matcher(request.getUrl());
                String tenantId = "";
                String propertyId = "";
                if (propertyMatcher.find()) {
                    tenantId = propertyMatcher.group(1);
                    propertyId = propertyMatcher.group(2);
                }
                Response property = null;
                String encodingHeader = request.getHeader(ACCEPT_ENCODING);
                String encodingType;
                if (encodingHeader.contains(GZIP)) {
                    encodingType = GZIP;

                } else {
                    encodingType = encodingHeader;
                }
                property = getInstance().getPropertyCache().getIfPresent(new PropertyCacheKey(tenantId, propertyId, encodingType));
                if (property != null) {
                    return RequestFilterAction.stopWith(
                          ResponseDefinitionBuilder.responseDefinition().withBody(property.getBody())
                                .withStatus(property.getStatus()).withHeaders(property.getHeaders()).build());
                }

            } else if (tenantMatcher.matches()) {
                tenantMatcher = TENANT_PATTERN.matcher(request.getUrl());
                String tenantId = "";
                if (tenantMatcher.find()) {
                    tenantId = tenantMatcher.group(1);
                }

                Response tenant = null;
                String encodingHeader = request.getHeader(ACCEPT_ENCODING);
                String encodingType;
                if (encodingHeader.contains(GZIP)) {
                    encodingType = GZIP;

                } else {
                    encodingType = encodingHeader;
                }
                tenant = getInstance().getTenantCache().getIfPresent(new TenantCacheKey(tenantId, encodingType));
                if (tenant != null) {
                    return RequestFilterAction.stopWith(
                          ResponseDefinitionBuilder.responseDefinition().withBody(tenant.getBody())
                                .withStatus(tenant.getStatus()).withHeaders(tenant.getHeaders()).build());
                }
            }
        }

        return RequestFilterAction.continueWith(request);
    }

    @Override
    public String getName() {
        return "RequestInterceptor";
    }

    public static class PropertyCacheKey {

        private final String tenantID;
        private final String propertyID;
        private final String encodingType;

        public PropertyCacheKey(String tenantID, String propertyID, String encodingType) {
            this.tenantID = tenantID;
            this.propertyID = propertyID;
            this.encodingType = encodingType;
        }

        public String getTenantID() {
            return tenantID;
        }

        public String getPropertyID() {
            return propertyID;
        }

        public String getEncoding() {
            return encodingType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PropertyCacheKey that = (PropertyCacheKey) o;
            return Objects.equals(tenantID, that.tenantID) && Objects.equals(propertyID, that.propertyID) &&
                  Objects.equals(encodingType, that.encodingType);
        }

        @Override
        public int hashCode() {

            return Objects.hash(tenantID, propertyID, encodingType);
        }

    }

    public static class TenantCacheKey {
        private final String tenantId;
        private final String encodingType;

        public TenantCacheKey(String tenantId, String encodingType) {
            this.tenantId = tenantId;
            this.encodingType = encodingType;
        }

        public String getTenantId() {
            return tenantId;
        }

        public String getEncodingType() {
            return encodingType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TenantCacheKey that = (TenantCacheKey) o;
            return Objects.equals(tenantId, that.tenantId) && Objects.equals(encodingType, that.encodingType);
        }

        @Override
        public int hashCode() {

            return Objects.hash(tenantId, encodingType);
        }
    }

    public Cache<PropertyCacheKey, Response> getPropertyCache() {
        return propertyCache;
    }

    public Cache<TenantCacheKey, Response> getTenantCache() {
        return tenantCache;
    }

    public static RequestInterceptor getInstance() {
        if (instance == null) {
            instance = new RequestInterceptor();
        }
        return instance;
    }
}
