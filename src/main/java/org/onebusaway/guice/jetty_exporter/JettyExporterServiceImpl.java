/**
 * Copyright (C) 2011 Brian Ferris <bdferris@onebusaway.org>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.guice.jetty_exporter;

import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.servlet.Servlet;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.bio.SocketConnector;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;

public class JettyExporterServiceImpl {

  private final List<Server> _servers = new ArrayList<Server>();

  private final List<ServletSource> _services;

  public JettyExporterServiceImpl(List<ServletSource> services) {
    _services = services;
  }

  @PostConstruct
  public void start() throws Exception {

    Map<Integer, List<ServletSource>> servicesByPort = groupServicesByPort(_services);

    for (int port : servicesByPort.keySet()) {
    	
            
    	SocketConnector connector=new SocketConnector();
    	

    	String hostName=null;
        connector.setPort(port);
        
        Server server = new Server();
        
        server.addConnector(connector);
        
        
        Context context = new Context(server, "/", Context.SESSIONS);

		for (ServletSource service : servicesByPort.get(port)) {
			URL url = service.getUrl();
			
			hostName=url.getHost();

			Servlet servlet = service.getServlet();
			context.addServlet(new ServletHolder(servlet), url.getPath());
		}
      if(hostName!=null && hostName.length()>0)
      {
    	  connector.setHost(hostName);
      }
		
      _servers.add(server);
    }

    for (Server server : _servers) {
      server.start();
    }
  }

  @PreDestroy
  public void stop() throws Exception {
    for (Server server : _servers) {
      server.stop();
    }
    _servers.clear();
  }

  /****
   * Private Methods
   ****/

  private Map<Integer, List<ServletSource>> groupServicesByPort(
      List<ServletSource> services) {

    Map<Integer, List<ServletSource>> servicesByPort = new HashMap<Integer, List<ServletSource>>();

    for (ServletSource service : services) {
      URL url = service.getUrl();
      int port = url.getPort();
      List<ServletSource> servicesForPort = servicesByPort.get(port);
      if (servicesForPort == null) {
        servicesForPort = new ArrayList<ServletSource>();
        servicesByPort.put(port, servicesForPort);
      }
      servicesForPort.add(service);
    }

    /**
     * Services with a URL port of -1 indicate that no port has been specified.
     * If URLs of this form are present, determining the port to use for these
     * services depends on what other services have been specified.
     */
    List<ServletSource> servicesWithDefaultPort = servicesByPort.remove(-1);
    if (servicesWithDefaultPort != null) {
      if (servicesByPort.isEmpty()) {
        /**
         * If no other services have been specified, we just use the default
         * port 80 for the services with a default port value.
         */
        servicesByPort.put(80, servicesWithDefaultPort);
      } else {
        /**
         * If other services HAVE been specified, we bind the default port
         * services to EACH of the ports mentioned by other services. A little
         * bit counter-intuitive, I agree, but it allows the user to change a
         * primary client or server port and have secondary services
         * automatically use that new port.
         */
        for (List<ServletSource> sources : servicesByPort.values()) {
          sources.addAll(servicesWithDefaultPort);
        }
      }
    }

    return servicesByPort;
  }

}
