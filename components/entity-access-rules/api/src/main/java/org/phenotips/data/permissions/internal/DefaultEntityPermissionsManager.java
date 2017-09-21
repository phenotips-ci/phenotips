/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses/
 */
package org.phenotips.data.permissions.internal;

import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.EntityAccess;
import org.phenotips.data.permissions.EntityPermissionsManager;
import org.phenotips.data.permissions.PermissionsConfiguration;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.permissions.events.EntityRightsUpdatedEvent;
import org.phenotips.entities.PrimaryEntity;

import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLookupException;
import org.xwiki.component.manager.ComponentManager;
import org.xwiki.observation.ObservationManager;

import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeSet;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultEntityPermissionsManager implements EntityPermissionsManager
{
    @Inject
    private Logger logger;

    @Inject
    private ObservationManager observationManager;

    @Inject
    @Named("context")
    private Provider<ComponentManager> componentManager;

    @Inject
    private PermissionsConfiguration configuration;

    @Override
    public Collection<Visibility> listVisibilityOptions()
    {
        Collection<Visibility> result = new TreeSet<>();
        for (Visibility visibility : listAllVisibilityOptions()) {
            if (!visibility.isDisabled()) {
                result.add(visibility);
            }
        }
        return result;
    }

    @Override
    public Collection<Visibility> listAllVisibilityOptions()
    {
        try {
            Collection<Visibility> result = new TreeSet<>();
            result.addAll(this.componentManager.get().<Visibility>getInstanceList(Visibility.class));
            return result;
        } catch (ComponentLookupException ex) {
            return Collections.emptyList();
        }
    }

    @Override
    public Visibility getDefaultVisibility()
    {
        return resolveVisibility(this.configuration.getDefaultVisibility());
    }

    @Override
    public Visibility resolveVisibility(String name)
    {
        try {
            if (StringUtils.isNotBlank(name)) {
                return this.componentManager.get().getInstance(Visibility.class, name);
            }
        } catch (ComponentLookupException ex) {
            this.logger.warn("Invalid entity visibility requested: {}", name);
        }
        return null;
    }

    @Override
    public Collection<AccessLevel> listAccessLevels()
    {
        try {
            Collection<AccessLevel> result = new TreeSet<>();
            result.addAll(this.componentManager.get().<AccessLevel>getInstanceList(AccessLevel.class));
            Iterator<AccessLevel> it = result.iterator();
            while (it.hasNext()) {
                if (!it.next().isAssignable()) {
                    it.remove();
                }
            }
            return result;
        } catch (ComponentLookupException ex) {
            return Collections.emptyList();
        }
    }

    @Override
    public AccessLevel resolveAccessLevel(String name)
    {
        try {
            if (StringUtils.isNotBlank(name)) {
                return this.componentManager.get().getInstance(AccessLevel.class, name);
            }
        } catch (ComponentLookupException ex) {
            this.logger.warn("Invalid entity access level requested: {}", name);
        }
        return null;
    }

    @Override
    public EntityAccess getEntityAccess(PrimaryEntity targetPatient)
    {
        return new DefaultEntityAccess(targetPatient, getHelper(), this);
    }

    @Override
    public Collection<? extends PrimaryEntity> filterByVisibility(Collection<? extends PrimaryEntity> entities,
        Visibility requiredVisibility)
    {
        if (requiredVisibility == null) {
            return entities;
        }
        Collection<PrimaryEntity> entitiesWithVisibility = new LinkedList<>();
        if (entities == null || entities.isEmpty()) {
            return entitiesWithVisibility;
        }
        for (PrimaryEntity entity : entities) {
            if (entity != null) {
                Visibility entityVisibility = this.getEntityAccess(entity).getVisibility();
                if (requiredVisibility.compareTo(entityVisibility) <= 0) {
                    entitiesWithVisibility.add(entity);
                }
            }
        }

        return entitiesWithVisibility;
    }

    @Override
    public Iterator<? extends PrimaryEntity> filterByVisibility(Iterator<? extends PrimaryEntity> entities,
        Visibility requiredVisibility)
    {
        if (requiredVisibility == null) {
            return entities;
        }
        if (entities == null || !entities.hasNext()) {
            return Collections.emptyIterator();
        }
        return new FilteringIterator(entities, requiredVisibility, this);
    }

    private EntityAccessHelper getHelper()
    {
        try {
            return this.componentManager.get().getInstance(EntityAccessHelper.class);
        } catch (ComponentLookupException ex) {
            this.logger.error("Mandatory component [EntityAccessHelper] missing: {}", ex.getMessage(), ex);
        }
        return null;
    }

    public void fireRightsUpdateEvent(String entityId)
    {
        this.observationManager.notify(new EntityRightsUpdatedEvent(entityId), null);
    }
}
