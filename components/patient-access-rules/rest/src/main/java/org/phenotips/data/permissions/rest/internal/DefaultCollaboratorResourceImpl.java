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
package org.phenotips.data.permissions.rest.internal;

import org.phenotips.data.Patient;
import org.phenotips.data.permissions.Collaborator;
import org.phenotips.data.permissions.PatientAccess;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.permissions.rest.CollaboratorResource;
import org.phenotips.data.permissions.rest.DomainObjectFactory;
import org.phenotips.data.permissions.rest.internal.utils.PatientAccessContext;
import org.phenotips.data.permissions.rest.internal.utils.SecureContextFactory;
import org.phenotips.data.permissions.rest.model.CollaboratorRepresentation;
import org.phenotips.rest.Autolinker;

import org.xwiki.component.annotation.Component;
import org.xwiki.container.Container;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.rest.XWikiResource;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

/**
 * Default implementation for {@link CollaboratorResource} using XWiki's support for REST resources.
 *
 * @version $Id$
 * @since 1.3M2
 */
@Component
@Named("org.phenotips.data.permissions.rest.internal.DefaultCollaboratorResourceImpl")
@Singleton
public class DefaultCollaboratorResourceImpl extends XWikiResource implements CollaboratorResource
{
    @Inject
    private Logger logger;

    @Inject
    private SecureContextFactory secureContextFactory;

    @Inject
    @Named("userOrGroup")
    private DocumentReferenceResolver<String> userOrGroupResolver;

    @Inject
    private DomainObjectFactory factory;

    @Inject
    private PermissionsManager manager;

    @Inject
    private Provider<Autolinker> autolinker;

    /** Needed for retrieving the `owner` parameter during the PUT request (as part of setting a new owner). */
    @Inject
    private Container container;

    @Override
    public CollaboratorRepresentation getCollaborator(String patientId, String collaboratorId)
    {
        this.logger.debug("Retrieving collaborator with id [{}] of patient record [{}] via REST", collaboratorId,
            patientId);
        // Besides getting the patient, checks that the user has view access
        PatientAccessContext patientAccessContext = this.secureContextFactory.getReadContext(patientId);

        CollaboratorRepresentation result;
        try {
            result = this.createCollaboratorRepresentation(patientAccessContext.getPatient(), collaboratorId.trim(),
                patientAccessContext.getPatientAccess());
        } catch (WebApplicationException ex) {
            this.logger.debug("Collaborator of patient record [{}] with id [{}] was not found",
                patientId, collaboratorId);
            throw ex;
        }

        // adding links relative to this context
        result.withLinks(this.autolinker.get().forResource(this.getClass(), this.uriInfo)
            .withGrantedRight(patientAccessContext.getPatientAccess().getAccessLevel().getGrantedRight())
            .build());
        return result;
    }

    @Override
    public Response setLevel(CollaboratorRepresentation collaborator, String patientId, String collaboratorId)
    {
        String level = collaborator.getLevel();
        if (StringUtils.isNotBlank(level)) {
            try {
                return setLevel(collaboratorId.trim(), level, patientId);
            } catch (Exception ex) {
                this.logger.debug("Changing collaborator's access level failed: the JSON was not properly formatted");
            }
        }
        throw new WebApplicationException(Response.Status.BAD_REQUEST);
    }

    @Override
    public Response setLevel(String patientId, String collaboratorId)
    {
        String level = (String) this.container.getRequest().getProperty("level");
        return setLevel(collaboratorId, level, patientId);
    }

    @Override
    public Response deleteCollaborator(String patientId, String collaboratorId)
    {
        this.logger.debug("Removing collaborator with id [{}] from patient record [{}] via REST", collaboratorId,
            patientId);
        // besides getting the patient, checks that the user has manage access
        PatientAccessContext patientAccessContext = this.secureContextFactory.getWriteContext(patientId);

        PatientAccess patientAccess = patientAccessContext.getPatientAccess();
        EntityReference collaboratorReference = this.userOrGroupResolver.resolve(collaboratorId);
        if (collaboratorReference == null) {
            // what would be a better status to indicate that the user/group id is not valid?
            // ideally, the status page should show some sort of a message indicating that the id was not found
            throw new WebApplicationException(Response.Status.NOT_FOUND);
        }

        if (!patientAccess.removeCollaborator(collaboratorReference)) {
            this.logger.error("Could not remove collaborator [{}] from patient record [{}]", collaboratorId, patientId);
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }

        this.manager.fireRightsUpdateEvent(patientId);
        return Response.ok().build();
    }

    private CollaboratorRepresentation createCollaboratorRepresentation(Patient patient, String id,
        PatientAccess patientAccess)
    {
        String collaboratorId = id.trim();
        EntityReference collaboratorReference = this.userOrGroupResolver.resolve(collaboratorId);
        if (collaboratorReference == null) {
            this.logger.debug("Invalid collaborator of patient record [{}] requested: [{}]",
                patient.getId(), collaboratorId);
            // what would be a better status to indicate that the user/group id is not valid?
            // ideally, the status page should show some sort of a message indicating that the id was not found
            throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
                .entity("Invalid collaborator").build());
        }

        for (Collaborator collaborator : patientAccess.getCollaborators()) {
            if (collaboratorReference.equals(collaborator.getUser())) {
                return this.factory.createCollaboratorRepresentation(collaborator);
            }
        }
        // same here
        this.logger.debug("Not a collaborator of patient record [{}] requested: [{}]",
            patient.getId(), collaboratorId);
        throw new WebApplicationException(Response.status(Response.Status.NOT_FOUND)
            .entity("Not a collaborator").build());
    }

    private Response setLevel(String collaboratorId, String accessLevelName, String patientId)
    {
        PatientAccessContext patientAccessContext = this.secureContextFactory.getWriteContext(patientId);
        patientAccessContext.checkCollaboratorInfo(collaboratorId, accessLevelName);
        PatientAccess patientAccess = patientAccessContext.getPatientAccess();
        EntityReference collaboratorReference = this.userOrGroupResolver.resolve(collaboratorId);
        patientAccess.addCollaborator(collaboratorReference, this.manager.resolveAccessLevel(accessLevelName));
        this.manager.fireRightsUpdateEvent(patientId);
        return Response.ok().build();
    }
}
