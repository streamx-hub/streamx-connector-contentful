# Contentful Connector

This project aims at providing a means of communication between [Contentful Headless CMS](https://www.contentful.com/)
and StreamX.

A connector is defined, that ingests data sent from Contentful via [Webhooks](https://www.contentful.com/developers/docs/webhooks/overview/)

The connector acts as a http api, listening for POST requests from the Contentful Webhook.
You need to make sure the connector is reachable by Contentful from the outside- both by ensuring
your endpoint is visible to the public internet, and the webhook is configured to communicate with
the endpoint.

One peculiarity of Contentful webhooks is that any assets / references are sent in the json body as 
encoded links- this forces the connector to add an additional processing step when receiving webhook
requests - namely after a field with an Asset link is found in the webhook json body, an additional 
request **from the connector back to Contentful** is sent, to get a url to the asset.

To optimize this process, requests can be batched (multiple links can be requested at once), and the
urls are cached in the connector itself - if an asset with the same id is detected, no request is
sent and the url is taken from the cache.

The preferred way to handle it is by using a different approach though- the webhook request is 
treated just as an event that needs to trigger a Contentful Content Delivery API request which
will return all data for the resource at once.

Additionally, to facilitate smoother integration of Contentful into existing StreamX architecture,
or *vice versa* the contentful connector offers an option to configure a mapping of Contentful
`contentType` properties to StreamX `type`s - this makes leveraging existing StreamX templating and
data processing features.

