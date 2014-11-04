/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.sling.replication.serialization.impl;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import org.apache.sling.api.resource.*;
import org.apache.sling.replication.communication.ReplicationRequest;
import org.apache.sling.replication.packaging.ReplicationPackage;
import org.apache.sling.replication.serialization.ReplicationPackageBuilder;
import org.apache.sling.replication.serialization.ReplicationPackageBuildingException;
import org.apache.sling.replication.serialization.ReplicationPackageReadingException;

import java.io.InputStream;
import java.util.*;

public class ResourceSharedReplicationPackageBuilder implements ReplicationPackageBuilder {

    private String PN_ORIGINAL_ID = "original.package.id";
    private String PN_ORIGINAL_ACTION = "original.package.action";
    private String PN_ORIGINAL_PATHS = "original.package.paths";

    private String PACKAGE_NAME_PREFIX = "replpackage";
    private String sharedPackagesRoot = "/var/sling/replication/";

    private final ReplicationPackageBuilder replicationPackageBuilder;

    public ResourceSharedReplicationPackageBuilder(ReplicationPackageBuilder replicationPackageExporter) {
        this.replicationPackageBuilder = replicationPackageExporter;
    }

    @CheckForNull
    public ReplicationPackage createPackage(@Nonnull ResourceResolver resourceResolver, @Nonnull ReplicationRequest request) throws ReplicationPackageBuildingException {
        ReplicationPackage replicationPackage = replicationPackageBuilder.createPackage(resourceResolver, request);

        if (replicationPackage == null) {
            return null;
        }

        try {
            String packagePath = generatePathFromId(resourceResolver, replicationPackage);

            return new ResourceSharedReplicationPackage(resourceResolver, packagePath, replicationPackage);
        }
        catch (PersistenceException e) {
            throw new ReplicationPackageBuildingException(e);
        }
    }

    @CheckForNull
    public ReplicationPackage readPackage(@Nonnull ResourceResolver resourceResolver, @Nonnull InputStream stream) throws ReplicationPackageReadingException {
        ReplicationPackage replicationPackage = replicationPackageBuilder.readPackage(resourceResolver, stream);

        if (replicationPackage == null) {
            return null;
        }

        try {
            String packagePath = generatePathFromId(resourceResolver, replicationPackage);

            return new ResourceSharedReplicationPackage(resourceResolver, packagePath, replicationPackage);
        }
        catch (PersistenceException e) {
            throw new ReplicationPackageReadingException(e);
        }
    }

    public ReplicationPackage getPackage(@Nonnull ResourceResolver resourceResolver, @Nonnull String replicationPackageId) {
        String originalPackageId = retrieveIdFromPath(resourceResolver, replicationPackageId);

        if (originalPackageId == null) {
            return null;
        }

        ReplicationPackage replicationPackage = replicationPackageBuilder.getPackage(resourceResolver, originalPackageId);

        if (replicationPackage == null) {
            return null;
        }

        return new ResourceSharedReplicationPackage(resourceResolver, replicationPackageId, replicationPackage);
    }

    public boolean installPackage(@Nonnull ResourceResolver resourceResolver, @Nonnull ReplicationPackage replicationPackage) throws ReplicationPackageReadingException {
        if (! (replicationPackage instanceof ResourceSharedReplicationPackage)) {
            return false;
        }

        ResourceSharedReplicationPackage sharedReplicationPackage = (ResourceSharedReplicationPackage) replicationPackage;

        ReplicationPackage originalPackage = sharedReplicationPackage.getPackage();
        return replicationPackageBuilder.installPackage(resourceResolver, originalPackage);
    }


    private String generatePathFromId(ResourceResolver resourceResolver, ReplicationPackage replicationPackage) throws PersistenceException {
        String name = PACKAGE_NAME_PREFIX + "_" + System.currentTimeMillis() + "_" +  UUID.randomUUID();
        String packagePath = sharedPackagesRoot + name;

        Map<String, Object> properties = new HashMap<String, Object>();
        properties.put(PN_ORIGINAL_ID, replicationPackage.getId());
        properties.put(PN_ORIGINAL_ACTION, replicationPackage.getAction());
        properties.put(PN_ORIGINAL_PATHS, replicationPackage.getPaths());

        Resource resource = ResourceUtil.getOrCreateResource(resourceResolver, packagePath, "nt:unstructured", "sling:Folder", false);

        ModifiableValueMap valueMap = resource.adaptTo(ModifiableValueMap.class);
        valueMap.putAll(properties);

        resourceResolver.create(resource, ResourceSharedReplicationPackage.REFERENCE_ROOT_NODE,
                Collections.singletonMap(ResourceResolver.PROPERTY_RESOURCE_TYPE, (Object)"nt:unstructured"));

        resourceResolver.commit();
        return packagePath;

    }

    private String retrieveIdFromPath(ResourceResolver resourceResolver, String packagePath) {
        if (!packagePath.startsWith(sharedPackagesRoot)) {
            return null;
        }

        Resource resource = resourceResolver.getResource(packagePath);

        if (resource == null) {
            return null;
        }

        ValueMap properties = resource.adaptTo(ValueMap.class);

        if (properties == null) {
            return null;
        }


        return properties.get(PN_ORIGINAL_ID, null);
    }
}
