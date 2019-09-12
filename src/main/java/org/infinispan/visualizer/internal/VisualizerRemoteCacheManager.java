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

package org.infinispan.visualizer.internal;

import java.lang.reflect.Field;

import javax.enterprise.inject.Alternative;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.Configuration;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.SaslQop;
import org.infinispan.client.hotrod.impl.transport.netty.ChannelFactory;
import org.infinispan.commons.marshall.UTF8StringMarshaller;
import org.infinispan.visualizer.cdi.Resources;

/**
 * @author <a href="mailto:rtsang@redhat.com">Ray Tsang</a>
 */
@Alternative
public class VisualizerRemoteCacheManager extends RemoteCacheManager {
   private static final Class<? extends ChannelFactory> CHANNEL_FACTORY = VisualizerChannelFactory.class;

   private ServersRegistry registry;
   private PingThread pingThread;

   public VisualizerRemoteCacheManager() {
      super(getCacheProperties());
   }

   public ServersRegistry getRegistry() {
      return registry;
   }

   @Override
   public void start() {
      super.start();

      this.registry = new ServersRegistry();

      VisualizerChannelFactory factory = getTransportFactoryViaReflection();
      if (factory != null) {
         factory.setRegistry(registry);
         factory.updateServerRegistry();
      }

      pingThread = new PingThread(this);
      pingThread.start();
   }

   protected VisualizerChannelFactory getTransportFactoryViaReflection() {
      try {
         Field channelFactoryField = RemoteCacheManager.class.getDeclaredField("channelFactory");
         channelFactoryField.setAccessible(true);
         return (VisualizerChannelFactory) channelFactoryField.get(this);
      } catch (Exception e) {
         e.printStackTrace();
         return null;
      }
   }

   @Override
   public void stop() {
      if (pingThread != null) {
         pingThread.abort();
         pingThread.interrupt();
      }
      super.stop();
   }


   public static Configuration getCacheProperties() {
      ConfigurationBuilder builder = new ConfigurationBuilder();

      final String serverList = System.getProperty("infinispan.visualizer.serverList");

//      builder
//         .addServers(serverList)
//         .marshaller(new UTF8StringMarshaller())
//         .channelFactory(CHANNEL_FACTORY)
//         ;

      builder
         .addServers(serverList)
         .marshaller(new UTF8StringMarshaller())
         .channelFactory(CHANNEL_FACTORY)
         .security().authentication()
            .enable()
            .username(Resources.JMX_USERNAME)
            .password(Resources.JMX_PASSWORD)
            .realm("ApplicationRealm")
            .serverName("jdg-server")
            .saslMechanism("DIGEST-MD5")
            .saslQop(SaslQop.AUTH)
      ;

      return builder.build();
   }
}
