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

import org.phenotips.data.Patient;
import org.phenotips.data.permissions.AccessLevel;
import org.phenotips.data.permissions.Collaborator;
import org.phenotips.data.permissions.Owner;
import org.phenotips.data.permissions.PermissionsManager;
import org.phenotips.data.permissions.Visibility;

import org.xwiki.bridge.DocumentAccessBridge;
import org.xwiki.component.annotation.Component;
import org.xwiki.context.Execution;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.security.authorization.AuthorizationManager;
import org.xwiki.security.authorization.Right;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.user.api.XWikiGroupService;

/**
 * @version $Id$
 */
@Component
@Singleton
public class DefaultPatientAccessHelper implements PatientAccessHelper
{
    private static final EntityReference USER_CLASS = new EntityReference("XWikiUsers", EntityType.DOCUMENT,
        new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE));

    private static final EntityReference GROUP_CLASS = new EntityReference("XWikiGroups", EntityType.DOCUMENT,
        new EntityReference(XWiki.SYSTEM_SPACE, EntityType.SPACE));

    @Inject
    private Logger logger;

    @Inject
    private DocumentAccessBridge bridge;

    @Inject
    private Execution execution;

    @Inject
    @Named("currentmixed")
    private DocumentReferenceResolver<EntityReference> partialEntityResolver;

    @Inject
    @Named("currentmixed")
    private DocumentReferenceResolver<String> stringEntityResolver;

    @Inject
    private EntityReferenceSerializer<String> entitySerializer;

    @Inject
    private PermissionsManager manager;

    @Inject
    private AuthorizationManager rights;

    @Override
    public DocumentReference getCurrentUser()
    {
        return this.bridge.getCurrentUserReference();
    }

    @Override
    public boolean isAdministrator(Patient patient)
    {
        if (patient == null || patient.getDocumentReference() == null) {
            return false;
        }
        return this.rights.hasAccess(Right.ADMIN, getCurrentUser(), patient.getDocumentReference());
    }

    @Override
    public boolean isAdministrator(Patient patient, DocumentReference user)
    {
        if (patient == null || patient.getDocumentReference() == null) {
            return false;
        }
        return this.rights.hasAccess(Right.ADMIN, user, patient.getDocumentReference());
    }

    @Override
    public Owner getOwner(Patient patient)
    {
        if (patient == null || patient.getDocumentReference() == null) {
            return null;
        }
        DocumentReference classReference =
            this.partialEntityResolver.resolve(Owner.CLASS_REFERENCE, patient.getDocumentReference());
        String owner = this.getStringProperty(patient.getXDocument(), classReference, "owner");
        if (StringUtils.isNotBlank(owner) && !"null".equals(owner)) {
            return new DefaultOwner(this.stringEntityResolver.resolve(owner, patient.getDocumentReference()), this);
        }
        return new DefaultOwner(null, this);
    }

    @Override
    public boolean setOwner(Patient patient, EntityReference userOrGroup)
    {
        DocumentReference classReference =
            this.partialEntityResolver.resolve(Owner.CLASS_REFERENCE, patient.getDocumentReference());
        try {
            EntityReference previousOwner = getOwner(patient).getUser();
            DocumentReference absoluteUserOrGroup = this.partialEntityResolver.resolve(userOrGroup);
            String owner = userOrGroup != null ? this.entitySerializer.serialize(absoluteUserOrGroup) : "";
            this.setProperty(patient.getXDocument(), classReference, "owner", owner);
            if (!previousOwner.equals(userOrGroup)) {
                addCollaborator(patient,
                    new DefaultCollaborator(previousOwner, this.manager.resolveAccessLevel("manage"), null), false);
            }
            removeCollaborator(patient, new DefaultCollaborator(userOrGroup, null, null), false);

            XWikiContext context = getXWikiContext();
            context.getWiki().saveDocument(patient.getXDocument(), "Set owner: " + owner, true, context);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public Visibility getVisibility(Patient patient)
    {
        DocumentReference classReference =
            this.partialEntityResolver.resolve(Visibility.CLASS_REFERENCE, patient.getDocumentReference());
        String visibility = this.getStringProperty(patient.getXDocument(), classReference, "visibility");
        if (StringUtils.isNotBlank(visibility)) {
            return this.manager.resolveVisibility(visibility);
        }
        return null;
    }

    @Override
    public boolean setVisibility(Patient patient, Visibility visibility)
    {
        DocumentReference classReference =
            this.partialEntityResolver.resolve(Visibility.CLASS_REFERENCE, patient.getDocumentReference());
        try {
            String visibilityAsString = (visibility != null) ? visibility.getName() : "";
            String currentVisibility = this.getStringProperty(patient.getXDocument(), classReference, "visibility");
            if (!visibilityAsString.equals(currentVisibility)) {
                this.setProperty(patient.getXDocument(), classReference, "visibility", visibilityAsString);
                XWikiContext context = getXWikiContext();
                context.getWiki().saveDocument(patient.getXDocument(), "Set visibility: " + visibilityAsString,
                    true, context);
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public AccessLevel getAccessLevel(Patient patient, EntityReference user)
    {
        AccessLevel result = this.manager.resolveAccessLevel("none");
        if (patient == null || user == null) {
            return result;
        }
        try {
            EntityReference owner = getOwner(patient).getUser();
            Collection<Collaborator> collaborators = getCollaborators(patient);
            Set<DocumentReference> processedEntities = new HashSet<>();
            Queue<DocumentReference> entitiesToCheck = new LinkedList<>();
            entitiesToCheck.add((DocumentReference) user);
            AccessLevel currentItemAccess = null;
            DocumentReference currentItem;
            XWikiContext context = getXWikiContext();
            XWikiGroupService groupService = context.getWiki().getGroupService(context);
            while (!entitiesToCheck.isEmpty()) {
                currentItem = entitiesToCheck.poll();
                currentItemAccess = getAccessLevel(currentItem, owner, collaborators);
                if (currentItemAccess.compareTo(result) > 0) {
                    result = currentItemAccess;
                }
                processedEntities.add(currentItem);
                Collection<DocumentReference> groups =
                    groupService.getAllGroupsReferencesForMember(currentItem, 0, 0, context);
                groups.removeAll(processedEntities);
                entitiesToCheck.addAll(groups);
            }
        } catch (XWikiException ex) {
            this.logger.warn("Failed to compute access level for [{}] on [{}]: {}", user, patient.getId(),
                ex.getMessage());
        }
        return result;
    }

    @Override
    public Collection<Collaborator> getCollaborators(Patient patient)
    {
        try {
            XWikiDocument patientDoc = patient.getXDocument();
            DocumentReference classReference =
                this.partialEntityResolver.resolve(Collaborator.CLASS_REFERENCE, patient.getDocumentReference());
            Map<EntityReference, Collaborator> collaborators = new TreeMap<>();
            for (BaseObject o : patientDoc.getXObjects(classReference)) {
                if (o == null) {
                    continue;
                }
                String collaboratorName = o.getStringValue("collaborator");
                String accessName = o.getStringValue("access");
                if (StringUtils.isBlank(collaboratorName) || StringUtils.isBlank(accessName)) {
                    continue;
                }
                EntityReference userOrGroup =
                    this.stringEntityResolver.resolve(collaboratorName, patient.getDocumentReference());
                AccessLevel access = this.manager.resolveAccessLevel(accessName);
                if (collaborators.containsKey(userOrGroup)) {
                    Collaborator oldCollaborator = collaborators.get(userOrGroup);
                    AccessLevel oldAccess = oldCollaborator.getAccessLevel();
                    if (access.compareTo(oldAccess) <= 0) {
                        continue;
                    }
                }
                Collaborator collaborator = new DefaultCollaborator(userOrGroup, access, this);
                collaborators.put(userOrGroup, collaborator);
            }
            return collaborators.values();
        } catch (Exception e) {
            // This should not happen;
        }
        return Collections.emptySet();
    }

    @Override
    public boolean setCollaborators(Patient patient, Collection<Collaborator> newCollaborators)
    {
        try {
            XWikiDocument patientDoc = patient.getXDocument();
            DocumentReference classReference =
                this.partialEntityResolver.resolve(Collaborator.CLASS_REFERENCE, patient.getDocumentReference());
            XWikiContext context = getXWikiContext();
            patientDoc.removeXObjects(classReference);
            for (Collaborator collaborator : newCollaborators) {
                BaseObject o = patientDoc.newXObject(classReference, context);
                o.setStringValue("collaborator", this.entitySerializer.serialize(collaborator.getUser()));
                o.setStringValue("access", collaborator.getAccessLevel().getName());
            }
            patientDoc.setAuthorReference(getCurrentUser());
            patientDoc.setMetaDataDirty(true);
            context.getWiki().saveDocument(patientDoc, "Updated collaborators", true, context);
            return true;
        } catch (Exception e) {
            // This should not happen;
        }
        return false;
    }

    @Override
    public boolean addCollaborator(Patient patient, Collaborator collaborator)
    {
        return this.addCollaborator(patient, collaborator, true);
    }

    private boolean addCollaborator(Patient patient, Collaborator collaborator, boolean saveDocument)
    {
        try {
            XWikiDocument patientDoc = patient.getXDocument();
            DocumentReference classReference =
                this.partialEntityResolver.resolve(Collaborator.CLASS_REFERENCE, patient.getDocumentReference());
            XWikiContext context = (XWikiContext) this.execution.getContext().getProperty("xwikicontext");

            DocumentReference absoluteUserOrGroup = this.partialEntityResolver.resolve(collaborator.getUser());
            String user = collaborator.getUser() != null ? this.entitySerializer.serialize(absoluteUserOrGroup) : "";

            BaseObject o = patientDoc.getXObject(classReference, "collaborator", user, false);
            if (o == null) {
                o = patientDoc.newXObject(classReference, context);
            }

            o.setStringValue("collaborator", StringUtils.defaultString(user));
            o.setStringValue("access", collaborator.getAccessLevel().getName());

            if (saveDocument) {
                patientDoc.setAuthorReference(getCurrentUser());
                patientDoc.setMetaDataDirty(true);
                context.getWiki().saveDocument(patientDoc, "Added collaborator: " + user, true, context);
            }

            return true;
        } catch (Exception e) {
            // This should not happen;
        }
        return false;
    }

    @Override
    public boolean removeCollaborator(Patient patient, Collaborator collaborator)
    {
        return this.removeCollaborator(patient, collaborator, true);
    }

    private boolean removeCollaborator(Patient patient, Collaborator collaborator, boolean saveDocument)
    {
        try {
            XWikiDocument patientDoc = patient.getXDocument();
            DocumentReference classReference =
                this.partialEntityResolver.resolve(Collaborator.CLASS_REFERENCE, patient.getDocumentReference());
            XWikiContext context = (XWikiContext) this.execution.getContext().getProperty("xwikicontext");
            DocumentReference absoluteUserOrGroup = this.partialEntityResolver.resolve(collaborator.getUser());
            String user = collaborator.getUser() != null ? this.entitySerializer.serialize(absoluteUserOrGroup) : "";

            BaseObject o = patientDoc.getXObject(classReference, "collaborator", user, false);
            if (o != null) {
                patientDoc.removeXObject(o);
                context.getWiki().saveDocument(patientDoc, "Removed collaborator: " + user, true, context);
                return true;
            }
        } catch (Exception e) {
            // This should not happen;
        }
        return false;
    }

    @Override
    public String getType(EntityReference userOrGroup)
    {
        if (userOrGroup == null) {
            // Guest user
            return "user";
        }
        try {
            XWikiDocument doc = (XWikiDocument) this.bridge.getDocument((DocumentReference) userOrGroup);
            if (doc.getXObject(USER_CLASS) != null) {
                return "user";
            } else if (doc.getXObject(GROUP_CLASS) != null) {
                return "group";
            }
        } catch (Exception ex) {
            this.logger.warn("Failed to determine user type: {}", ex.getMessage(), ex);
        }
        return "unknown";
    }

    private AccessLevel getAccessLevel(EntityReference userOrGroup, EntityReference owner,
        Collection<Collaborator> collaborators)
    {
        if (userOrGroup.equals(owner)) {
            return this.manager.resolveAccessLevel("owner");
        }
        for (Collaborator collaborator : collaborators) {
            if (collaborator.getUser().equals(userOrGroup)) {
                return collaborator.getAccessLevel();
            }
        }
        return this.manager.resolveAccessLevel("none");
    }

    private XWikiContext getXWikiContext()
    {
        return (XWikiContext) this.execution.getContext().getProperty("xwikicontext");
    }

    private String getStringProperty(XWikiDocument doc, DocumentReference classReference, String propertyName)
    {
        try {
            BaseObject object = doc.getXObject(classReference);
            if (object != null) {
                String property = object.getStringValue(propertyName);
                if (!StringUtils.isEmpty(property)) {
                    return property;
                }
            }
        } catch (Exception ex) {
            this.logger.error("Failed to get object property", ex);
        }
        return null;
    }

    private void setProperty(XWikiDocument doc, DocumentReference classReference, String propertyName,
        Object propertyValue) throws Exception
    {
        XWikiContext xcontext = getXWikiContext();
        BaseObject obj = doc.getXObject(classReference, true, xcontext);
        if (obj != null) {
            obj.set(propertyName, propertyValue, xcontext);
            doc.setAuthorReference(getCurrentUser());
            doc.setMetaDataDirty(true);
        }
    }
}
