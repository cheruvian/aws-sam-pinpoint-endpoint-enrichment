package com.cheruvia.aws.pinpoint;

import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.pinpoint.AmazonPinpoint;
import com.amazonaws.services.pinpoint.AmazonPinpointClient;
import com.amazonaws.services.pinpoint.model.BadRequestException;
import com.amazonaws.services.pinpoint.model.EndpointRequest;
import com.amazonaws.services.pinpoint.model.GetEndpointRequest;
import com.amazonaws.services.pinpoint.model.GetEndpointResult;
import com.amazonaws.services.pinpoint.model.InternalServerErrorException;
import com.amazonaws.services.pinpoint.model.UpdateEndpointRequest;
import com.amazonaws.services.pinpoint.model.UpdateEndpointResult;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class EndpointEnrichmentHandler implements RequestStreamHandler {
    private static final GsonBuilder GSON_BUILDER = new GsonBuilder()
            .setFieldNamingStrategy(FieldNamingPolicy.UPPER_CAMEL_CASE);
    private static final Gson GSON = GSON_BUILDER.create();
    private static final String APP_ID_ENV_VAR = "APP_ID";
    private static final String APP_ID = System.getenv(APP_ID_ENV_VAR);
    /**
     * Hidden attributes that should not be returned to the device but can still be used for targeting.
     */
    private static final List<String> HIDDEN_ATTRIBUTES = Collections.singletonList("DeviceType");
    /**
     * Server side attributes that are used for targeting but device should not be allowed to update.
     */
    private static final List<String> SERVER_SIDE_ONLY_ATTRIBUTES = Collections.singletonList("SubscriptionType");

    private final AmazonPinpoint amazonPinpoint;

    public EndpointEnrichmentHandler() {
        amazonPinpoint = AmazonPinpointClient.builder()
                .withCredentials(new DefaultAWSCredentialsProviderChain())
                .build();
    }

    @Override
    public void handleRequest(InputStream inputStream, OutputStream outputStream, Context context) throws IOException {
        final EnrichmentRequest enrichmentRequest = parseRequest(inputStream, context);
        final Object response;
        final String method = enrichmentRequest.getMethod();
        switch (method) {
            case "GET":
                response = handleGetRequest(enrichmentRequest, context);
                break;
            case "POST":
                response = handlePostRequest(enrichmentRequest, context);
                break;
            default:
                throw new InternalServerErrorException("Internal Service Exception");
        }

        OutputStreamWriter writer = new OutputStreamWriter(outputStream, "UTF-8");
        writer.write(GSON.toJson(response));
        writer.close();
    }

    private EnrichmentRequest parseRequest(final InputStream inputStream, final Context context) throws IOException {
        final StringBuilder stringBuilder = new StringBuilder();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        while (reader.ready()) {
            stringBuilder.append(reader.readLine());
        }
        context.getLogger().log(stringBuilder.toString());

        final EnrichmentRequest enrichmentRequest = GSON.fromJson(stringBuilder.toString(), EnrichmentRequest.class);
        //Validation hack since EnrichmentRequest actually wraps a response object.
        if (enrichmentRequest.getEndpoint().getId() != null) {
            throw new BadRequestException("Endpoint Id should be specified in the URI not the body");
        }

        return enrichmentRequest;
    }

    private EnrichedEndpoint handleGetRequest(final EnrichmentRequest enrichmentRequest, final Context context) {
        final GetEndpointResult endpointResponse = amazonPinpoint.getEndpoint(
                new GetEndpointRequest()
                        .withApplicationId(APP_ID)
                        .withEndpointId(enrichmentRequest.getEndpointId())
        );

        //Any additional filtering or enrichment can go here.
        if (endpointResponse.getEndpointResponse().getAttributes() != null) {
            HIDDEN_ATTRIBUTES.forEach(attribute -> endpointResponse.getEndpointResponse().getAttributes().remove(attribute));
        }

        //Hack to map from EndpointResponse to EnrichedEndpoint
        final EnrichedEndpoint enrichedEndpoint = GSON.fromJson(GSON.toJson(endpointResponse.getEndpointResponse()), EnrichedEndpoint.class);
        //You can set custom fields or make additional network calls here as well
        enrichedEndpoint.setCustomField("CUSTOM_FIELD");

        return enrichedEndpoint;
    }


    private EnrichedEndpoint handlePostRequest(final EnrichmentRequest enrichmentRequest, final Context context) {
        //You can ensure that devices never update certain fields.
        final GetEndpointResult endpointResponse = amazonPinpoint.getEndpoint(
                new GetEndpointRequest()
                        .withApplicationId(APP_ID)
                        .withEndpointId(enrichmentRequest.getEndpointId())
        );

        if (endpointResponse.getEndpointResponse().getAttributes() == null) {
            endpointResponse.getEndpointResponse().setAttributes(new HashMap<>());
        }
        if (enrichmentRequest.getEndpoint().getAttributes() == null) {
            enrichmentRequest.getEndpoint().setAttributes(new HashMap<>());
        }
        for (String key : SERVER_SIDE_ONLY_ATTRIBUTES) {
            final List<String> newValues = enrichmentRequest.getEndpoint().getAttributes().get(key);
            final List<String> existingValues = endpointResponse.getEndpointResponse().getAttributes().get(key);
            if (newValues != null && !newValues.equals(existingValues)) {
                throw new BadRequestException("Device is not authorized to update the " + key + " attribute");
            }
        }

        //You can also make additional network calls here as well or modify the endpoint before updating it in Pinpoint.
        //TODO: DyanmoDB calls etc...
        boolean isPaid = Math.random() > 0.5;
        enrichmentRequest.getEndpoint()
                .getAttributes()
                .put(
                        "SubscriptionType",
                        Collections.singletonList(isPaid ? "PAID" : "FREE")
                );

        final UpdateEndpointResult updateEndpointResult = amazonPinpoint.updateEndpoint(
                new UpdateEndpointRequest()
                        .withApplicationId(APP_ID)
                        .withEndpointId(enrichmentRequest.getEndpointId())
                        //Hack to map from EnrichedEndpoint to EndpointRequest
                        .withEndpointRequest(GSON.fromJson(GSON.toJson(enrichmentRequest.getEndpoint()), EndpointRequest.class))
        );
        context.getLogger().log(updateEndpointResult.toString());

        return handleGetRequest(enrichmentRequest, context);
    }
}
