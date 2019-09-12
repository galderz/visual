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

package org.infinispan.visualizer.poller;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:rtsang@redhat.com">Ray Tsang</a>
 */
public abstract class PollerManager<T> {
   private Logger logger = Logger.getLogger(PollerManager.class.getName());

   public static final long DEFAULT_REFRESH_RATE = 2000L;
   private long refreshRate = DEFAULT_REFRESH_RATE;

   private UpdateThread updateThread;

   public PollerManager() {
   }

   /**
    * Map with the data of the address and the key counts
    */
   private Map<String, T> infos = new ConcurrentHashMap<>();

   /**
    * Pollers
    */
   private Map<SocketAddress, PollerThread> pollers = new HashMap<>();

   abstract protected PollerThread createPollerThread(SocketAddress address, T info) throws Exception;

   abstract protected void updateClusterList();

   public void init(String threadName) {
      updateClusterList();

      logger.info("Start updated thread for: " + threadName);
      updateThread = new UpdateThread();
      updateThread.setName(threadName);
      updateThread.start();
   }

   public void destroy() {
      if (updateThread != null) {
         updateThread.abort();
         updateThread.interrupt();
      }
      for (PollerThread p : pollers.values()) {
         p.abort();
         p.interrupt();
      }
   }

   private String generateNodeId(SocketAddress socketAddress) {
      InetSocketAddress isa = (InetSocketAddress) socketAddress;
      InetAddress address = isa.getAddress();
      if (address == null) {
         try {
            address = InetAddress.getByName(isa.getHostName());
         } catch (UnknownHostException e) {
            throw new RuntimeException(e);
         }
      }

      String id = address.getCanonicalHostName() + "-"
         + isa.getPort();
      id = id.replaceAll("[^\\d]", "-");

      return id;
   }

   abstract protected T createNewInfo(String id, SocketAddress addr);

   protected void updateClusterList(Collection<SocketAddress> addrs) {
      logger.info("Update cluster list: " + addrs);

      Set<SocketAddress> pollersToStop = new HashSet<>();
      pollersToStop.addAll(pollers.keySet());

      for (SocketAddress addr : addrs) {
         pollersToStop.remove(addr);
         String id = generateNodeId(addr);
         logger.info("Generate id=" + id + " for address=" + addr);

//         // Don't send back temporary ids
//         if (!id.contains("----------------------------------------------"))
//            continue;

         if (!infos.containsKey(id)) {
            T info = createNewInfo(id, addr);

            PollerThread<?> newThread;
            try {
               newThread = createPollerThread(addr, info);
               newThread.setRefreshRate(refreshRate);
               newThread.start();
               infos.put(id, info);
               pollers.put(addr, newThread);
            } catch (Exception e) {
               e.printStackTrace();
            }
         }
      }
      for (SocketAddress addr : pollersToStop) {
         String id = generateNodeId(addr);
         infos.remove(id);
         PollerThread<?> p = pollers.remove(addr);
         if (p != null) {
            p.abort();
         }
      }
   }

   public Collection<T> getAllInfos() {
      //updateClusterList();
      return infos.values();
   }

   protected class UpdateThread extends Thread {
      private Logger logger;
      private static final long DEFAULT_REFRESH_RATE = 1000L;

      private volatile boolean running;
      private long refreshRate = DEFAULT_REFRESH_RATE;

      public UpdateThread() {
         Logger.getLogger(this.getClass().getName());
         setDaemon(true);
      }

      public void abort() {
         running = false;
      }

      public boolean isRunning() {
         return running;
      }

      @Override
      public void run() {
         running = true;
         while (running) {
            try {
               updateClusterList();
            } catch (Exception e) {
               logger.log(Level.SEVERE, "error when updating cluster list", e);
            }
            try {
               Thread.sleep(refreshRate);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }
      }

      public long getRefreshRate() {
         return refreshRate;
      }

      public void setRefreshRate(long refreshRate) {
         this.refreshRate = refreshRate;
      }
   }

   public long getRefreshRate() {
      return refreshRate;
   }

   public void setRefreshRate(long refreshRate) {
      this.refreshRate = refreshRate;
   }

}
