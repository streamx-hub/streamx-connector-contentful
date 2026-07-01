# Contentful Connector

This processor is used to relay data from Contentful webhooks to the rest of StreamX mesh.
Uniquely, it does not use any incoming channels- the incoming channel is used just to accept http requests.
May be rewritten as a `detached` service.

## Configuration
### Basic
`streamx.contentful.connector.use-content-delivery-api`: if set to true, the webhook request
triggers a request to [CONTENT DELIVERY API](https://www.contentful.com/developers/docs/references/content-delivery-api/overview/) to get the webhook's target resource, ignoring all other
data in the webhook request; when set to false the connector will try to complete the webhook data
by resolving all links in the webhook request

`streamx.contentful.connector.include-level`: defines the number of link
levels the connector will render, MAXIMUM 10, see [REFERENCE](https://www.contentful.com/developers/docs/concepts/links/#link-level)


### Access
`streamx.contentful.connector.space-id`: the Contentful Space Id that the application is
connected to

`streamx.contentful.connector.environment`: the Contentful Environment Id that the
application is connected to

`streamx.contentful.connector.token`: the API Token generated in Contentful
(should be secret!)


### Content Delivery API Urls
See [REFERENCE](https://www.contentful.com/developers/docs/references/content-delivery-api/overview/)

`streamx.contentful.connector.contentful-assets-url`:
url to the assets part of the content delivery API

(default in the `application.properties` is
`https://cdn.contentful.com/spaces/${streamx.contentful.connector.space-id}/environments/${streamx.contentful.connector.environment}/assets` )

`streamx.contentful.connector.contentful-entries-url`:
url to the entries part of the content delivery API

(default in the `application.properties` is
`https://cdn.contentful.com/spaces/${streamx.contentful.connector.space-id}/environments/${streamx.contentful.connector.environment}/entries` )


### Retry strategy
(used to configure the Uni request sent see 
[REFERENCE](https://javadoc.io/doc/io.smallrye.reactive/mutiny/0.4.4/io/smallrye/mutiny/groups/UniRetry.html) )

`streamx.contentful.connector.resolve-asset-request-backoff-initial-seconds`:
max wait time before first retry (default `1`, used by the `withBackoff()` method)

`streamx.contentful.connector.resolve-asset-request-backoff-max-wait-seconds`:
max time to wait between retry attempts (default `10`, used by the `withBackoff()` method))

`streamx.contentful.connector.resolve-asset-request-backoff-max-retries`:
max retries before the request for assets will fail (default `3`, used by the `atMost()` method)


### Resource type mappings:
`streamx.contentful.connector.resource-type-mappings`: a map connecting Contentful
`contentType` property to `StreamX` event `type` defined in `Data`


### Topic mappings:
`streamx.contentful.connector.topic-mappings`: a map connecting Contentful
`topic` property from the `X-Contentful-Topic` header to `StreamX` data `type`


## Channels
Incoming channel: `data` (accepts http requests)
Outgoing channel: `resources`


## Example environment variables config
```
# Basic
streamx.contentful.connector.use-content-delivery-api=true
streamx.contentful.connector.include-level=10

# Access
# PLEASE NOTE: put these, especially the token, in the `secrets` folder, preferably encrypted
streamx.contentful.connector.space-id=5p4c31d0fy0urpr0j3c7
streamx.contentful.connector.environment=master
streamx.contentful.connector.token=0000000000000000000000000000000000000000000

# Content Delivery API Urls
streamx.contentful.connector.contentful-assets-url=https://cdn.contentful.com/spaces/${streamx.contentful.connector.space-id}/environments/${streamx.contentful.connector.environment}/assets
streamx.contentful.connector.contentful-entries-url=https://cdn.contentful.com/spaces/${streamx.contentful.connector.space-id}/environments/${streamx.contentful.connector.environment}/entries

# Retry strategy
streamx.contentful.connector.resolve-asset-request-backoff-initial-seconds=1
streamx.contentful.connector.resolve-asset-request-backoff-max-wait-seconds=10
streamx.contentful.connector.resolve-asset-request-backoff-max-retries=5

# Resource type mappings:
streamx.contentful.connector.resource-type-mappings."pageProduct"=data/product
streamx.contentful.connector.resource-type-mappings."pageLanding"=data/landing

# Topic mappings:
streamx.contentful.connector.topic-mappings."ContentManagement.Entry.publish"=com.streamx.blueprints.data.published.v1
streamx.contentful.connector.topic-mappings."ContentManagement.ContentType.publish"=com.streamx.blueprints.data.published.v1
streamx.contentful.connector.topic-mappings."ContentManagement.Asset.publish"=com.streamx.blueprints.data.published.v1

streamx.contentful.connector.topic-mappings."ContentManagement.Entry.unpublish"=com.streamx.blueprints.data.unpublished.v1
streamx.contentful.connector.topic-mappings."ContentManagement.ContentType.unpublish"=com.streamx.blueprints.data.unpublished.v1
streamx.contentful.connector.topic-mappings."ContentManagement.Asset.unpublish"=com.streamx.blueprints.data.unpublished.v1
```
