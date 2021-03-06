/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2010, Red Hat, Inc., and individual contributors
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

package org.wildfly.extension.mod_cluster;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.dmr.ModelNode;
import org.jboss.modcluster.ModClusterServiceMBean;
import org.jboss.msc.service.ServiceController;

import java.net.InetAddress;
import java.net.UnknownHostException;

import static org.wildfly.extension.mod_cluster.ModClusterLogger.ROOT_LOGGER;
import static org.wildfly.extension.mod_cluster.ModClusterSubsystemResourceDefinition.HOST;
import static org.wildfly.extension.mod_cluster.ModClusterSubsystemResourceDefinition.PORT;

/**
 * {@link OperationStepHandler} that handles removing a reverse proxy.
 *
 * @author Jean-Frederic Clere
 * @author Radoslav Husar
 */
public class ModClusterRemoveProxy implements OperationStepHandler {

    static final ModClusterRemoveProxy INSTANCE = new ModClusterRemoveProxy();

    static OperationDefinition getDefinition(ResourceDescriptionResolver descriptionResolver) {
        return new SimpleOperationDefinitionBuilder(CommonAttributes.REMOVE_PROXY, descriptionResolver)
                .addParameter(HOST)
                .addParameter(PORT)
                .setRuntimeOnly()
                .addAccessConstraint(ModClusterExtension.MOD_CLUSTER_PROXIES_DEF)
                .build();
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        if (context.isNormalServer() && context.getServiceRegistry(false).getService(ContainerEventHandlerService.SERVICE_NAME) != null) {
            context.addStep(new OperationStepHandler() {
                @Override
                public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
                    ServiceController<?> controller = context.getServiceRegistry(false).getService(ContainerEventHandlerService.SERVICE_NAME);
                    final ModClusterServiceMBean service = (ModClusterServiceMBean) controller.getValue();
                    ROOT_LOGGER.debugf("remove-proxy: %s", operation);

                    final String host = HOST.resolveModelAttribute(context, operation).asString();
                    final int port = PORT.resolveModelAttribute(context, operation).asInt();

                    // Keeping this test here to maintain same behavior as previous versions.
                    try {
                        InetAddress.getByName(host);
                    } catch (UnknownHostException e) {
                        throw new OperationFailedException(ModClusterLogger.ROOT_LOGGER.couldNotResolveProxyIpAddress(), e);
                    }

                    service.removeProxy(host, port);

                    context.completeStep(new OperationContext.RollbackHandler() {
                        @Override
                        public void handleRollback(OperationContext context, ModelNode operation) {
                            // TODO What if mod_cluster was never aware of this proxy?
                            service.addProxy(host, port);
                        }
                    });
                }
            }, OperationContext.Stage.RUNTIME);
        }

        context.stepCompleted();
    }
}
