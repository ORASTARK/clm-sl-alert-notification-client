package com.sap.cloud.alert.notification.client.internal;

import com.sap.cloud.alert.notification.client.IAlertNotificationClient;
import com.sap.cloud.alert.notification.client.IRetryPolicy;
import com.sap.cloud.alert.notification.client.QueryParameter;
import com.sap.cloud.alert.notification.client.ServiceRegion;
import com.sap.cloud.alert.notification.client.exceptions.ClientRequestException;
import com.sap.cloud.alert.notification.client.model.CustomerResourceEvent;
import com.sap.cloud.alert.notification.client.model.PagedResponse;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

import static com.sap.cloud.alert.notification.client.internal.AlertNotificationClientUtils.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static org.apache.http.HttpHeaders.*;

public class AlertNotificationClient implements IAlertNotificationClient {

    private final HttpClient httpClient;
    private final IRetryPolicy retryPolicy;
    private final ServiceRegion serviceRegion;
    private final IAuthorizationHeader authorizationHeader;

    public AlertNotificationClient(
            HttpClient httpClient,
            IRetryPolicy retryPolicy,
            ServiceRegion serviceRegion,
            IAuthorizationHeader authorizationHeader
    ) {
        this.httpClient = requireNonNull(httpClient);
        this.retryPolicy = requireNonNull(retryPolicy);
        this.serviceRegion = requireNonNull(serviceRegion);
        this.authorizationHeader = requireNonNull(authorizationHeader);
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public IRetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

    public ServiceRegion getServiceRegion() {
        return serviceRegion;
    }

    public IAuthorizationHeader getAuthorizationHeader() {
        return authorizationHeader;
    }

    @Override
    public CustomerResourceEvent sendEvent(CustomerResourceEvent event) {
        URI requestURI = buildProducerURI(serviceRegion);
        HttpUriRequest httpRequest = createPostRequest(requestURI, event);

        return executeRequestWithRetry(httpRequest, CustomerResourceEvent.class);
    }

    @Override
    public PagedResponse getMatchedEvents(Map<QueryParameter, String> queryFilter) {
        URI requestURI = buildMatchedEventsURI(serviceRegion, queryFilter);
        HttpUriRequest httpRequest = createGetRequest(requestURI);

        return executeRequestWithRetry(httpRequest, PagedResponse.class);
    }

    @Override
    public PagedResponse getMatchedEvent(String eventId, Map<QueryParameter, String> queryFilter) {
        URI requestURI = buildMatchedEventsURI(serviceRegion, eventId, queryFilter);
        HttpUriRequest httpRequest = createGetRequest(requestURI);

        return executeRequestWithRetry(httpRequest, PagedResponse.class);
    }

    @Override
    public PagedResponse getUndeliveredEvents(Map<QueryParameter, String> queryFilter) {
        URI requestURI = buildUndeliveredEventsURI(serviceRegion, queryFilter);
        HttpUriRequest httpRequest = createGetRequest(requestURI);

        return executeRequestWithRetry(httpRequest, PagedResponse.class);
    }

    @Override
    public PagedResponse getUndeliveredEvent(String eventId, Map<QueryParameter, String> queryFilter) {
        URI requestURI = buildUndeliveredEventsURI(serviceRegion, eventId, queryFilter);
        HttpUriRequest httpRequest = createGetRequest(requestURI);

        return executeRequestWithRetry(httpRequest, PagedResponse.class);
    }

    protected HttpUriRequest createGetRequest(URI uri) {
        HttpGet request = new HttpGet(uri);
        request.setHeader(ACCEPT, APPLICATION_JSON);
        request.setHeader(AUTHORIZATION, authorizationHeader.getValue());

        return request;
    }

    protected HttpUriRequest createPostRequest(URI serviceUri, CustomerResourceEvent event) {
        HttpPost request = new HttpPost(serviceUri);
        request.setEntity(toEntity(event));
        request.setHeader(CONTENT_TYPE, APPLICATION_JSON);
        request.setHeader(AUTHORIZATION, authorizationHeader.getValue());

        return request;
    }

    private HttpEntity toEntity(CustomerResourceEvent event) {
        return new StringEntity(toJsonString(event), UTF_8.name());
    }

    private <T> T executeRequestWithRetry(HttpUriRequest request, Class<T> responseClass) {
        return retryPolicy.executeWithRetry(() -> executeRequest(request, responseClass));
    }

    private <T> T executeRequest(HttpUriRequest request, Class<T> responseClass) {
        HttpResponse response = null;
        try {
            response = httpClient.execute(request);

            assertSuccessfulResponse(response);

            return fromJsonString(EntityUtils.toString(response.getEntity(), UTF_8.name()), responseClass);
        } catch (IOException exception) {
            throw new ClientRequestException(exception);
        } finally {
            consumeQuietly(response);
        }
    }
}
