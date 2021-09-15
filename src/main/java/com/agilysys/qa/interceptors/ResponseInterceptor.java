package com.agilysys.qa.interceptors;

import static com.agilysys.qa.interceptors.RequestInterceptor.ACCEPT_ENCODING;
import static com.agilysys.qa.interceptors.RequestInterceptor.GZIP;
import static com.agilysys.qa.interceptors.RequestInterceptor.PROPERTY_PATTERN;
import static com.agilysys.qa.interceptors.RequestInterceptor.TENANT_PATTERN;
import static com.agilysys.qa.interceptors.RequestInterceptor.USER_SERVICE;

import java.util.regex.Matcher;

import com.agilysys.qa.interceptors.RequestInterceptor.PropertyCacheKey;
import com.agilysys.qa.interceptors.RequestInterceptor.TenantCacheKey;
import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseTransformer;
import com.github.tomakehurst.wiremock.http.HttpHeader;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.RequestMethod;
import com.github.tomakehurst.wiremock.http.Response;

public class ResponseInterceptor extends ResponseTransformer {
    protected static final String CONTENT_ENCODING = "content-encoding";

    @Override
    public Response transform(Request request, Response response, FileSource fileSource, Parameters parameters) {
        if (response.isFromProxy()) {
            if ((request.getMethod().isOneOf(RequestMethod.GET) || request.getMethod().isOneOf(RequestMethod.PUT)) &&
                  request.getUrl().contains(USER_SERVICE)) {
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

                    if (response.getStatus() == 200 || response.getStatus() == 201) {
                        String encodingHeader = request.getHeader(ACCEPT_ENCODING);
                        String encodingType;
                        if (encodingHeader.contains(GZIP)) {
                            encodingType = GZIP;

                        } else {
                            encodingType = encodingHeader;
                        }

                        RequestInterceptor.getInstance().getPropertyCache()
                              .put(new PropertyCacheKey(tenantId, propertyId, encodingType), response);

                    }

                } else if (tenantMatcher.matches()) {
                    tenantMatcher = TENANT_PATTERN.matcher(request.getUrl());
                    String tenantId = "";
                    if (tenantMatcher.find()) {
                        tenantId = tenantMatcher.group(1);
                    }

                    if (response.getStatus() == 200 || response.getStatus() == 201) {
                        String encodingHeader = request.getHeader(ACCEPT_ENCODING);
                        String encodingType;
                        if (encodingHeader.contains(GZIP)) {
                            encodingType = GZIP;

                        } else {
                            encodingType = encodingHeader;
                        }

                        RequestInterceptor.getInstance().getTenantCache()
                              .put(new TenantCacheKey(tenantId, encodingType), response);

                    }
                }
            }
        }

        return response;
    }

    @Override
    public String getName() {
        return "ResponseTransformer";
    }

    @Override
    public boolean applyGlobally() {
        return true;
    }
}
