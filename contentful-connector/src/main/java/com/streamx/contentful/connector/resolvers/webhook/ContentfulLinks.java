package com.streamx.contentful.connector.resolvers.webhook;

import java.util.Set;

record ContentfulLinks(Set<String> assets, Set<String> entries) {
}
