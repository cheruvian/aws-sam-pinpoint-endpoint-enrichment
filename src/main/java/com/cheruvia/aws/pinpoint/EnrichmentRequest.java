package com.cheruvia.aws.pinpoint;
import lombok.Data;

@Data
public class EnrichmentRequest {
    private String method;
    private String endpointId;
    private EnrichedEndpoint endpoint;
}
