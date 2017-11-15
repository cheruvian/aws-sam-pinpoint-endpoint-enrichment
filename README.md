# aws-sam-pinpoint-endpoint-enrichment
AWS SAM Template for an Lambda backed API Gateway wrapper for Amazon Pinpoint. 

This template provides a wrapper around the Amazon Pinpoint Update/Get Endpoint API

This allows you to do things like:

* Prevent Pinpoint Endpoints from updating certain "server side only" custom attributes (eg Paid vs Free)
* Prevent Pinpoint Endpoints from receiving their full Endpoint state (eg filtering out certain Attributes)
* Enriching Pinpoint Endpoints with server side targeting information (eg adding metadata the device isn't know about)
* Enriching the Pinpoint Endpoint sent down to the device (eg annotating Endpoints with information that you choose not to store in Pinpoint)

## AWS Deployment

1. Configure your AWS credentials and region.
1. Set an environment variable named `S3_BUCKET`. This will be used for uploading your lambda function artifacts.
1. Set an environment variable named `APP_ID`. This is the Amazon Pinpoint ApplicationId that your endpoints belong to.
1. Simply run `bash bpd.sh` to `b`uild, `p`ackage and `d`eploy.   

## Usage

For a sample usage check out the `src/client` package to see how it works.