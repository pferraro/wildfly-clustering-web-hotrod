/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.wildfly.clustering.web.hotrod.session.fine;

import java.io.NotSerializableException;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.infinispan.client.hotrod.Flag;
import org.infinispan.client.hotrod.RemoteCache;
import org.wildfly.clustering.ee.Mutator;
import org.wildfly.clustering.ee.cache.CacheProperties;
import org.wildfly.clustering.ee.cache.function.ConcurrentMapPutFunction;
import org.wildfly.clustering.ee.cache.function.ConcurrentMapRemoveFunction;
import org.wildfly.clustering.ee.cache.function.CopyOnWriteMapPutFunction;
import org.wildfly.clustering.ee.cache.function.CopyOnWriteMapRemoveFunction;
import org.wildfly.clustering.ee.hotrod.RemoteCacheEntryMutator;
import org.wildfly.clustering.marshalling.spi.Marshaller;
import org.wildfly.clustering.web.cache.session.SessionAttributeImmutability;
import org.wildfly.clustering.web.cache.session.SessionAttributes;

/**
 * Exposes session attributes for fine granularity sessions.
 * @author Paul Ferraro
 */
public class FineSessionAttributes<V> extends FineImmutableSessionAttributes<V> implements SessionAttributes {

    private final String id;
    private final RemoteCache<SessionAttributeNamesKey, Map<String, UUID>> namesCache;
    private final RemoteCache<SessionAttributeKey, V> attributeCache;
    private final Map<String, Mutator> mutations = new ConcurrentHashMap<>();
    private final Marshaller<Object, V> marshaller;
    private final CacheProperties properties;

    private volatile Map<String, UUID> names;

    public FineSessionAttributes(String id, Map<String, UUID> names, RemoteCache<SessionAttributeNamesKey, Map<String, UUID>> namesCache, RemoteCache<SessionAttributeKey, V> attributeCache, Marshaller<Object, V> marshaller, CacheProperties properties) {
        super(id, names, attributeCache, marshaller);
        this.id = id;
        this.names = names;
        this.namesCache = namesCache;
        this.attributeCache = attributeCache;
        this.marshaller = marshaller;
        this.properties = properties;
    }

    @Override
    public Object removeAttribute(String name) {
        UUID attributeId = this.names.remove(name);
        if (attributeId == null) return null;

        this.setNames(this.namesCache.withFlags(Flag.FORCE_RETURN_VALUE).computeIfPresent(this.createKey(), this.properties.isTransactional() ? new CopyOnWriteMapRemoveFunction<>(name) : new ConcurrentMapRemoveFunction<>(name)));

        Object result = this.read(name, this.attributeCache.withFlags(Flag.FORCE_RETURN_VALUE).remove(this.createKey(attributeId)));
        this.mutations.remove(name);
        return result;
    }

    @Override
    public Object setAttribute(String name, Object attribute) {
        if (attribute == null) {
            return this.removeAttribute(name);
        }
        if (!this.marshaller.isMarshallable(attribute)) {
            throw new IllegalArgumentException(new NotSerializableException(attribute.getClass().getName()));
        }

        V value = this.marshaller.write(attribute);
        UUID attributeId = this.names.get(name);
        if (attributeId == null) {
            UUID newAttributeId = UUID.randomUUID();
            this.setNames(this.namesCache.withFlags(Flag.FORCE_RETURN_VALUE).compute(this.createKey(), this.properties.isTransactional() ? new CopyOnWriteMapPutFunction<>(name, newAttributeId) : new ConcurrentMapPutFunction<>(name, newAttributeId)));
            attributeId = this.names.get(name);
        }

        Object result = this.read(name, this.attributeCache.withFlags(Flag.FORCE_RETURN_VALUE).put(this.createKey(attributeId), value));
        this.mutations.remove(name);
        return result;
    }

    @Override
    public Object getAttribute(String name) {
        UUID attributeId = this.names.get(name);
        if (attributeId == null) return null;

        SessionAttributeKey key = this.createKey(attributeId);
        V value = this.attributeCache.get(key);
        Object attribute = this.read(name, value);
        if (attribute != null) {
            // If the object is mutable, we need to indicate that the attribute should be replicated
            if (!SessionAttributeImmutability.INSTANCE.test(attribute)) {
                Mutator mutator = new RemoteCacheEntryMutator<>(this.attributeCache, key, value);
                // If cache is not transactional, mutate on close instead.
                if ((this.mutations.putIfAbsent(name, mutator) == null) && this.properties.isTransactional()) {
                    mutator.mutate();
                }
            }
        }
        return attribute;
    }

    @Override
    public void close() {
        if (!this.properties.isTransactional()) {
            for (Mutator mutator : this.mutations.values()) {
                mutator.mutate();
            }
        }
        this.mutations.clear();
    }

    private void setNames(Map<String, UUID> names) {
        this.names = (names != null) ? Collections.unmodifiableMap(names) : Collections.emptyMap();
    }

    private SessionAttributeNamesKey createKey() {
        return new SessionAttributeNamesKey(this.id);
    }

    private SessionAttributeKey createKey(UUID attributeId) {
        return new SessionAttributeKey(this.id, attributeId);
    }
}
