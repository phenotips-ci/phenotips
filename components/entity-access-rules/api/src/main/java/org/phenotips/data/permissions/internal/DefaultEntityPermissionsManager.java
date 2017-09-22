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
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.permissions.events.EntityRightsUpdatedEvent;
import org.phenotips.entities.PrimaryEntity;

import org.xwiki.component.annotation.Component;
import org.xwiki.observation.ObservationManager;

import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultEntityPermissionsManager implements EntityPermissionsManager
{
    @Inject
    private ObservationManager observationManager;

    @Inject
    private EntityAccessHelper helper;

    @Inject
    private EntityVisibilityManager visibilityManager;

    @Inject
    private EntityAccessManager accessManager;

    @Override
    public Collection<Visibility> listVisibilityOptions()
    {
        return this.visibilityManager.listVisibilityOptions();
    }

    @Override
    public Collection<Visibility> listAllVisibilityOptions()
    {
        return this.visibilityManager.listAllVisibilityOptions();
    }

    @Override
    public Visibility getDefaultVisibility()
    {
        return this.visibilityManager.getDefaultVisibility();
    }

    @Override
    public Visibility resolveVisibility(String name)
    {
        return this.visibilityManager.resolveVisibility(name);
    }

    @Override
    public Collection<AccessLevel> listAccessLevels()
    {
        return this.accessManager.listAccessLevels();
    }

    @Override
    public Collection<AccessLevel> listAllAccessLevels()
    {
        return this.accessManager.listAllAccessLevels();
    }

    @Override
    public AccessLevel resolveAccessLevel(String name)
    {
        return this.accessManager.resolveAccessLevel(name);
    }

    @Override
    public EntityAccess getEntityAccess(PrimaryEntity targetPatient)
    {
        return new DefaultEntityAccess(targetPatient, this.helper, this.accessManager, this.visibilityManager);
    }

    @Override
    public Collection<? extends PrimaryEntity> filterByVisibility(Collection<? extends PrimaryEntity> entities,
        Visibility requiredVisibility)
    {
        return this.visibilityManager.filterByVisibility(entities, requiredVisibility);
    }

    @Override
    public Iterator<? extends PrimaryEntity> filterByVisibility(Iterator<? extends PrimaryEntity> entities,
        Visibility requiredVisibility)
    {
        return this.visibilityManager.filterByVisibility(entities, requiredVisibility);
    }

    public void fireRightsUpdateEvent(String entityId)
    {
        this.observationManager.notify(new EntityRightsUpdatedEvent(entityId), null);
    }
}
