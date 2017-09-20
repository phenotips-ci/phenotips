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
package org.phenotips.data.permissions.rest;

import org.phenotips.data.permissions.rest.model.PrincipalsRepresentation;
import org.phenotips.data.rest.PatientResource;
import org.phenotips.rest.ParentResource;
import org.phenotips.rest.RelatedResources;
import org.phenotips.rest.Relation;
import org.phenotips.rest.RequiredAccess;

import org.xwiki.component.annotation.Role;
import org.xwiki.stability.Unstable;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 * Resource for retrieving information about principals that have access to the patient record, in bulk, where patients
 * are identified by patient record's internal PhenoTips identifier.
 *
 * @version $Id$
 * @since 1.4
 */
@Unstable("New API introduced in 1.4")
@Role
@Path("/patients/{patient-id}/permissions/principals")
@Relation("https://phenotips.org/rel/principals")
@ParentResource(PermissionsResource.class)
@RelatedResources(PatientResource.class)
public interface PrincipalsResource
{
    /**
     * Retrieve information about users or groups that have any access to the patient record. If the indicated patient
     * record doesn't exist, or if the user sending the request doesn't have the right to view the target patient
     * record, an error is returned.
     *
     * @param patientId internal identifier of a patient record
     * @return REST representation of a collection of principals
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RequiredAccess("view")
    PrincipalsRepresentation getPrincipals(@PathParam("patient-id") String patientId);
}
