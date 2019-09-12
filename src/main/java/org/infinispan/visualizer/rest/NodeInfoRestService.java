/*
* JBoss, Home of Professional Open Source
* Copyright 2011 Red Hat Inc. and/or its affiliates and other
* contributors as indicated by the @author tags. All rights reserved.
* See the copyright.txt in the distribution for a full listing of
* individual contributors.
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

package org.infinispan.visualizer.rest;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import javax.annotation.PreDestroy;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.visualizer.cdi.Resources;
import org.infinispan.visualizer.internal.VisualizerChannelFactory;
import org.infinispan.visualizer.internal.VisualizerRemoteCacheManager;
import org.infinispan.visualizer.poller.PollerManager;
import org.infinispan.visualizer.poller.jmx.JmxCacheEntriesPollerManager;

/**
 * @author <a href="mailto:rtsang@redhat.com">Ray Tsang</a>
 */
@Path("/nodes")
@ApplicationScoped
public class NodeInfoRestService {
   private Logger logger = Logger.getLogger(NodeInfoRestService.class.getName());

   @Inject
   private VisualizerRemoteCacheManager cacheManager;

   @Inject
   private Resources resources;

   private Map<String, PollerManager<NodeInfo>> pollerManagers = new HashMap<>();

   @GET
   @Path("/{cacheName}")
   @Produces("application/json")
   public <K, V> Collection<NodeInfo> getAllNodeInfo(@PathParam(value = "cacheName") String cacheName,
                                                     @DefaultValue("false") @QueryParam("clear") Boolean clear,
                                                     @DefaultValue("false") @QueryParam("refresh") Boolean refresh) throws Exception {
      if (cacheName == null || "".equals(cacheName)) {
         cacheName = "default(dist_sync)";
      }

      logger.info("Call to getAllNodeInfo");

      if (!pollerManagers.containsKey(cacheName)) {
         JmxCacheEntriesPollerManager manager = resources.cacheEntriesPollerManager(cacheManager);
         manager.setCacheName(cacheName);
         manager.init("NodeInfoRestService");
         pollerManagers.put(cacheName, manager);
      }

      String[] parts = cacheName.split("\\(");
      if (parts.length > 0) {
         String name = parts[0];
         RemoteCache<K, V> cache = cacheManager.getCache(name);
         if (cache != null) {

            if (clear) {
               cache.clearAsync();
            }

            if (refresh) {
               Set<K> keys = cache.keySet();
               for (K key : keys) {
                  V value = cache.get(key);
                  cache.put(key, value);
               }
            }
         }
      }

      PollerManager<NodeInfo> manager = pollerManagers.get(cacheName);
      return manager.getAllInfos();
   }

   @PreDestroy
   protected void preDestroy() {
      for (PollerManager<NodeInfo> manager : pollerManagers.values()) {
         manager.destroy();
      }
   }
}
