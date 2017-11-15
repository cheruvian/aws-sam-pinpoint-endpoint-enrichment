package com.cheruvia.aws.pinpoint;

import com.amazonaws.services.pinpoint.model.EndpointResponse;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class EnrichedEndpoint extends EndpointResponse  {
    private String customField;
}
