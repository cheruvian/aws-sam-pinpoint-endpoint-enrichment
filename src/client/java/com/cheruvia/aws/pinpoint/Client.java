package com.cheruvia.aws.pinpoint;

import com.amazonaws.auth.AWS4Signer;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.http.AWSRequestSigningApacheInterceptor;
import com.amazonaws.services.pinpoint.AmazonPinpoint;
import com.amazonaws.services.pinpoint.AmazonPinpointClient;
import com.amazonaws.services.pinpoint.model.EndpointRequest;
import com.amazonaws.services.pinpoint.model.EndpointResponse;
import com.amazonaws.services.pinpoint.model.GetEndpointRequest;
import com.amazonaws.services.pinpoint.model.GetEndpointResult;
import com.amazonaws.services.pinpoint.model.UpdateEndpointRequest;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

public class Client {
    private static final AWSCredentialsProvider credentialsProvider = new DefaultAWSCredentialsProviderChain();
    private static final String REGION = "us-east-1";
    private static final String ENDPOINT_URL = System.getenv("ENDPOINT_URL");
    private static final String APP_ID = System.getenv("APP_ID");

    private static final GsonBuilder GSON_BUILDER = new GsonBuilder()
            .setFieldNamingStrategy(FieldNamingPolicy.UPPER_CAMEL_CASE);
    private static final Gson GSON = GSON_BUILDER.create();


    private static final CloseableHttpClient httpClient = signingClientForServiceName("execute-api");
    private static final AmazonPinpoint pinpoint = AmazonPinpointClient.builder()
            .withCredentials(credentialsProvider)
            .build();


    public static void main(String[] args) throws IOException {
        System.out.println("Using APP_ID:" + APP_ID);
        System.out.println("Using ENDPOINT_URL:" + ENDPOINT_URL);
        final String endpointId = "1";

        // Will not have any filtered attributes
        System.out.println(GSON.toJson(getEnrichedEndpoint(endpointId)));

        // The full Endpoint
        GetEndpointResult endpoint = pinpoint.getEndpoint(
                new GetEndpointRequest()
                        .withEndpointId(endpointId)
                        .withApplicationId(APP_ID)
        );
        System.out.println(GSON.toJson(endpoint.getEndpointResponse()));



        EndpointRequest endpointRequest = GSON.fromJson(GSON.toJson(endpoint.getEndpointResponse()), EndpointRequest.class);
        UpdateEndpointRequest updateEndpointRequest = new UpdateEndpointRequest()
                .withEndpointId(endpointId)
                .withApplicationId(APP_ID)
                .withEndpointRequest(endpointRequest);

        // Will update using non restricted attribute
        endpointRequest.getAttributes().put("NewAttribute", Collections.singletonList("value"));
        updateEnrichedEndpoint(endpointId, endpointRequest);

        // Update restricted attribute.
        endpointRequest.getAttributes().put("SubscriptionType", Collections.singletonList("FREE"));
        // Using Pinpoint Client will succeed
        pinpoint.updateEndpoint(updateEndpointRequest);
        // Using Enriched Client will fail
        endpointRequest.getAttributes().put("SubscriptionType", Collections.singletonList("PAID"));
        updateEnrichedEndpoint(endpointId, endpointRequest);
    }

    private static EndpointResponse getEnrichedEndpoint(final String endpointId) throws IOException {
        HttpUriRequest request = new HttpGet(ENDPOINT_URL + "/my-endpoints/" + endpointId);
        String response = getResponseBody(request);
        System.out.println(response);
        //This will lose any custom fields you might have added
        return GSON.fromJson(response, EndpointResponse.class);
    }

    private static EndpointResponse updateEnrichedEndpoint(final String endpointId, final EndpointRequest endpointRequest) throws IOException {
        HttpPost request = new HttpPost(ENDPOINT_URL + "/my-endpoints/" + endpointId);
        request.setEntity(stringEntity(GSON.toJson(endpointRequest)));
        String response = getResponseBody(request);
        System.out.println("Update response: " + response);
        //This will lose any custom fields you might have added
        return GSON.fromJson(response, EndpointResponse.class);
    }

    static String getResponseBody(HttpUriRequest request) throws IOException {
        try (CloseableHttpResponse response = httpClient.execute(request)) {
            System.out.println(response.getStatusLine());
            StringBuilder builder = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
            try {
                while (br.ready()) {
                    builder.append(br.readLine());
                }
                br.close();
                return builder.toString();
            } catch (IOException e) {
                e.printStackTrace();
                throw e;
            }
        }
    }

    static CloseableHttpClient signingClientForServiceName(String serviceName) {
        AWS4Signer signer = new AWS4Signer();
        signer.setServiceName(serviceName);
        signer.setRegionName(System.getenv(REGION));

        HttpRequestInterceptor interceptor = new AWSRequestSigningApacheInterceptor(serviceName, signer, credentialsProvider);
        return HttpClients.custom()
                .addInterceptorLast(interceptor)
                .build();
    }

    static HttpEntity stringEntity(final String body) throws UnsupportedEncodingException {
        BasicHttpEntity httpEntity = new BasicHttpEntity();
        httpEntity.setContent(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8.name())));
        return httpEntity;
    }
}
