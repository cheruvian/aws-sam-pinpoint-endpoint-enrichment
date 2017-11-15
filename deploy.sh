#!/usr/bin/env bash

if [ -z "$APP_ID" ]; then
    echo "\$APP_ID was not set. Set your Amazon Pinpoint APP_ID";
    exit 1;
fi

set -x
aws cloudformation deploy \
    --stack-name aws-sam-pinpoint-endpoint-enrichment \
    --capabilities CAPABILITY_NAMED_IAM \
    --template-file target/packaged-template.yaml \
    --parameter-overrides "PinpointAppId=$APP_ID"
