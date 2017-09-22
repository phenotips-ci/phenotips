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
import org.phenotips.entities.PrimaryEntity;

import org.xwiki.component.annotation.Component;

import java.util.Collection;
import java.util.Iterator;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Secure implementation of the permissions manager service, which checks the user's access rights before performing an
 * operation.
 *
 * @version $Id$
 * @since 1.3M2
 */
@Component
@Named("secure")
@Singleton
public class SecureEntityPermissionsManager implements EntityPermissionsManager
{
    @Inject
    private EntityPermissionsManager internalService;

    @Override
    public Collection<Visibility> listVisibilityOptions()
    {
        return this.internalService.listVisibilityOptions();
    }

    @Override
    public Collection<Visibility> listAllVisibilityOptions()
    {
        return this.internalService.listAllVisibilityOptions();
    }

    @Override
    public Visibility getDefaultVisibility()
    {
        return this.internalService.getDefaultVisibility();
    }

    @Override
    public Visibility resolveVisibility(String name)
    {
        return this.internalService.resolveVisibility(name);
    }

    @Override
    public Collection<AccessLevel> listAccessLevels()
    {
        return this.internalService.listAccessLevels();
    }

    @Override
    public Collection<AccessLevel> listAllAccessLevels()
    {
        return this.internalService.listAllAccessLevels();
    }

    @Override
    public AccessLevel resolveAccessLevel(String name)
    {
        return this.internalService.resolveAccessLevel(name);
    }

    @Override

    public EntityAccess getEntityAccess(PrimaryEntity targetEntity)
    {
        return new SecureEntityAccess(this.internalService.getEntityAccess(targetEntity), this.internalService);
    }

    @Override
    public Collection<? extends PrimaryEntity> filterByVisibility(Collection<? extends PrimaryEntity> entities,
        Visibility requiredVisibility)
    {
        return this.internalService.filterByVisibility(entities, requiredVisibility);
    }

    @Override
    public Iterator<? extends PrimaryEntity> filterByVisibility(Iterator<? extends PrimaryEntity> entities,
        Visibility requiredVisibility)
    {
        return this.internalService.filterByVisibility(entities, requiredVisibility);
    }

    @Override
    public void fireRightsUpdateEvent(String entityId)
    {
        this.internalService.fireRightsUpdateEvent(entityId);
    }
}
