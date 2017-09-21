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
package org.phenotips.studies.family.internal;

import org.phenotips.entities.internal.AbstractPrimaryEntityManager;
import org.phenotips.studies.family.Family;

import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Implementation of family data access service using XWiki as the storage backend, where families in documents having
 * an object of type {@code PhenoTips.FamilyClass}.
 *
 * @version $Id$
 * @since 1.4
 */
@Named("Family")
@Singleton
public class FamilyEntityManager extends AbstractPrimaryEntityManager<Family>
{
    @Override
    public EntityReference getDataSpace()
    {
        return Family.DATA_SPACE;
    }

    @Override
    protected Class<? extends Family> getEntityClass()
    {
        return PhenotipsFamily.class;
    }

    @Override
    protected DocumentReference getEntityXClassReference()
    {
        return this.referenceResolver.resolve(Family.CLASS_REFERENCE);
    }

    @Override
    public String getIdPrefix()
    {
        return "FAM";
    }
}
