/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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

import java.util.UUID;

import org.kohsuke.MetaInfServices;
import org.wildfly.clustering.marshalling.Externalizer;
import org.wildfly.clustering.web.hotrod.SessionKeyExternalizer;

/**
 * Externalizer for {@link SessionAttributeNamesKey}.
 * @author Paul Ferraro
 */
@MetaInfServices(Externalizer.class)
public class SessionAttributeNamesKeyExternalizer extends SessionKeyExternalizer<SessionAttributeNamesKey<UUID>> {

    @SuppressWarnings("unchecked")
    public SessionAttributeNamesKeyExternalizer() {
        super((Class<SessionAttributeNamesKey<UUID>>) (Class<?>) SessionAttributeNamesKey.class, id -> new SessionAttributeNamesKey<>(id));
    }
}
