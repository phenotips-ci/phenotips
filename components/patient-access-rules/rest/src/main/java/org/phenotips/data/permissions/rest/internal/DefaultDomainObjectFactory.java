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
import org.phenotips.data.permissions.Owner;
import org.phenotips.data.permissions.PatientAccess;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.permissions.Visibility;
import org.phenotips.data.permissions.internal.PatientAccessHelper;
import org.phenotips.data.permissions.rest.CollaboratorResource;
import org.phenotips.data.permissions.rest.DomainObjectFactory;
import org.phenotips.data.permissions.rest.model.CollaboratorRepresentation;
import org.phenotips.data.permissions.rest.model.CollaboratorsRepresentation;
import org.phenotips.data.permissions.rest.model.OwnerRepresentation;
import org.phenotips.data.permissions.rest.model.PrincipalRepresentation;
import org.phenotips.data.permissions.rest.model.PrincipalsRepresentation;
import org.phenotips.data.permissions.rest.model.UserSummary;
import org.phenotips.data.permissions.rest.model.VisibilityRepresentation;
import org.phenotips.groups.GroupManager;
import org.phenotips.rest.Autolinker;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.stability.Unstable;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.core.UriInfo;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.xpn.xwiki.api.Document;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Default implementation of {@link DomainObjectFactory}.
 *
 * @version $Id$
 * @since 1.3M2
 */
@Unstable
@Component
@Singleton
public class DefaultDomainObjectFactory implements DomainObjectFactory
{
    @Inject
    @Named("secure")
    private PermissionsManager manager;

    @Inject
    private NameAndEmailExtractor nameAndEmailExtractor;

    @Inject
    private EntityReferenceSerializer<String> entitySerializer;

    @Inject
    private Provider<Autolinker> autolinker;

    @Inject
    @Named("userOrGroup")
    private DocumentReferenceResolver<String> userOrGroupResolver;

    @Inject
    private GroupManager groupManager;

    @Inject
    private PatientAccessHelper helper;

    @Override
    public OwnerRepresentation createOwnerRepresentation(Patient patient)
    {
        PatientAccess patientAccess = this.manager.getPatientAccess(patient);
        Owner owner = patientAccess.getOwner();

        // links should be added at a later point, to allow the reuse of this method in different contexts

        return loadUserSummary(new OwnerRepresentation(), owner.getUser(), owner.getType());
    }

    private <E extends UserSummary> E loadUserSummary(E result, EntityReference reference, String type)
    {
        result.withId(this.entitySerializer.serialize(reference));
        result.withType(type);
        Pair<String, String> nameAndEmail = this.nameAndEmailExtractor.getNameAndEmail(type, reference);
        if (nameAndEmail != null) {
            if (!StringUtils.isBlank(nameAndEmail.getLeft())) {
                result.withName(nameAndEmail.getLeft());
            }
            if (!StringUtils.isBlank(nameAndEmail.getRight())) {
                result.withEmail(nameAndEmail.getRight());
            }
        }
        return result;
    }

    @Override
    public VisibilityRepresentation createVisibilityRepresentation(Patient patient)
    {
        PatientAccess patientAccess = this.manager.getPatientAccess(patient);
        Visibility visibility = patientAccess.getVisibility();

        return this.createVisibilityRepresentation(visibility);
    }

    @Override
    public VisibilityRepresentation createVisibilityRepresentation(Visibility visibility)
    {
        if (visibility == null) {
            return null;
        }
        return (new VisibilityRepresentation())
            .withLevel(visibility.getName())
            .withLabel(visibility.getLabel())
            .withDescription(visibility.getDescription());
    }

    @Override
    public CollaboratorsRepresentation createCollaboratorsRepresentation(Patient patient, UriInfo uriInfo)
    {
        PatientAccess patientAccess = this.manager.getPatientAccess(patient);
        Collection<Collaborator> collaborators = patientAccess.getCollaborators();

        CollaboratorsRepresentation result = new CollaboratorsRepresentation();
        for (Collaborator collaborator : collaborators) {
            CollaboratorRepresentation collaboratorObject = this.createCollaboratorRepresentation(collaborator);

            collaboratorObject.withLinks(this.autolinker.get()
                .forSecondaryResource(CollaboratorResource.class, uriInfo)
                .withExtraParameters("collaborator-id", this.entitySerializer.serialize(collaborator.getUser()))
                .withGrantedRight(patientAccess.getAccessLevel().getGrantedRight())
                .build());

            result.withCollaborators(collaboratorObject);
        }

        return result;
    }

    @Override
    public CollaboratorRepresentation createCollaboratorRepresentation(Collaborator collaborator)
    {
        CollaboratorRepresentation result = this.loadUserSummary(
            new CollaboratorRepresentation(), collaborator.getUser(), collaborator.getType());
        result.withLevel(collaborator.getAccessLevel().getName());
        return result;
    }

    @Override
    public PrincipalsRepresentation createPrincipalsRepresentation(Patient patient, UriInfo uriInfo)
    {
        PrincipalsRepresentation result = new PrincipalsRepresentation();

        PatientAccess patientAccess = this.manager.getPatientAccess(patient);

        Set<String> addedPrincipals = new LinkedHashSet<>();

        Owner owner = patientAccess.getOwner();
        Document doc = this.helper.getDocument(owner.getUser());
        PrincipalRepresentation principal = this.createPrincipalRepresentation(owner.getUser(),
            owner.getType(), "manage", "owner", "", doc.getURL());
        result.withPrincipals(principal);
        addedPrincipals.add(owner.getUsername());

        if (owner.isGroup()) {
            fetchGroupMembers(addedPrincipals, result, principal.getId(), principal.getName(), "manage", "owner");
        }

        // fetchAdminUsers(addedPrincipals, result);

        Collection<Collaborator> collaborators = patientAccess.getCollaborators();
        for (Collaborator collaborator : collaborators) {
            if (addedPrincipals.contains(collaborator.getUsername())) {
                continue;
            }
            Document collaboratorDoc = this.helper.getDocument(collaborator.getUser());
            PrincipalRepresentation principalObject = this.createPrincipalRepresentation(collaborator.getUser(),
                collaborator.getType(), collaborator.getAccessLevel().getName(), "collaborator", "",
                collaboratorDoc.getURL());
            result.withPrincipals(principalObject);
            addedPrincipals.add(collaborator.getUsername());

            if (collaborator.isGroup()) {
                fetchGroupMembers(addedPrincipals, result, principalObject.getId(), principalObject.getName(),
                    principalObject.getLevel(),
                    "collaborator");
            }
        }

        return result;
    }

    private void fetchAdminUsers(Set<String> addedPrincipals, PrincipalsRepresentation result)
    {
        List<XWikiDocument> admins = this.groupManager.getAllAdminUsers();
        for (XWikiDocument admin : admins) {

        }
    }

    private void fetchGroupMembers(Set<String> addedPrincipals, PrincipalsRepresentation result, String groupId,
        String groupName,
        String level, String role)
    {
        Set<Document> groupMemberDocs = this.groupManager.getAllMembersForGroup(groupId);

        for (Document member : groupMemberDocs) {
            if (addedPrincipals.contains(member.getName())) {
                continue;
            }
            EntityReference ref = member.getDocument().getDocumentReference();
            PrincipalRepresentation object = this.createPrincipalRepresentation(ref,
                this.helper.getType(ref), level, role, groupName, member.getURL());
            result.withPrincipals(object);
            addedPrincipals.add(ref.getName());
        }
    }

    private PrincipalRepresentation createPrincipalRepresentation(EntityReference reference, String type, String level,
        String role, String group, String url)
    {
        PrincipalRepresentation result = this.loadUserSummary(new PrincipalRepresentation(), reference, type);
        result.withLevel(level);
        result.withRole(role);
        result.withGroup(group);
        result.withUrl(url);
        return result;
    }
}
