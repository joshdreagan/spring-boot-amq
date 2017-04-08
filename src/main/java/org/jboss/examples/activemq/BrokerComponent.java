/*
 * Copyright (C) Red Hat, Inc.
 * http://www.redhat.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.examples.activemq;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import org.apache.activemq.broker.BrokerPlugin;
import org.apache.activemq.broker.BrokerService;
import org.apache.activemq.broker.SslContext;
import org.apache.activemq.broker.TransportConnector;
import org.apache.activemq.broker.jmx.ManagementContext;
import org.apache.activemq.broker.region.policy.ConstantPendingMessageLimitStrategy;
import org.apache.activemq.broker.region.policy.PendingMessageLimitStrategy;
import org.apache.activemq.broker.region.policy.PolicyEntry;
import org.apache.activemq.broker.region.policy.PolicyMap;
import org.apache.activemq.command.ActiveMQDestination;
import org.apache.activemq.command.ActiveMQTopic;
import org.apache.activemq.filter.DestinationMapEntry;
import org.apache.activemq.network.ConditionalNetworkBridgeFilterFactory;
import org.apache.activemq.network.DiscoveryNetworkConnector;
import org.apache.activemq.network.NetworkBridgeFilterFactory;
import org.apache.activemq.network.NetworkConnector;
import org.apache.activemq.security.AuthenticationUser;
import org.apache.activemq.security.AuthorizationEntry;
import org.apache.activemq.security.AuthorizationMap;
import org.apache.activemq.security.AuthorizationPlugin;
import org.apache.activemq.security.DefaultAuthorizationMap;
import org.apache.activemq.security.SimpleAuthenticationPlugin;
import org.apache.activemq.spring.SpringSslContext;
import org.apache.activemq.store.PersistenceAdapter;
import org.apache.activemq.store.kahadb.KahaDBPersistenceAdapter;
import org.apache.activemq.transport.discovery.DiscoveryAgent;
import org.apache.activemq.transport.discovery.simple.SimpleDiscoveryAgent;
import org.apache.activemq.usage.MemoryUsage;
import org.apache.activemq.usage.StoreUsage;
import org.apache.activemq.usage.SystemUsage;
import org.apache.activemq.usage.TempUsage;
import org.apache.activemq.util.DefaultIOExceptionHandler;
import org.apache.activemq.util.IOExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class BrokerComponent {
  
  @Autowired(required = true)
  protected BrokerConfig config;
  
  @Bean
  BrokerService broker() throws Exception {
    BrokerService broker = new BrokerService();
    broker.setBrokerName(config.getName());
    broker.setDataDirectory(config.getDataDirectory());
    broker.setPlugins(brokerPlugins());
    broker.setDestinationPolicy(destinationPolicy());
    broker.setDestinations(destinations());
    broker.setManagementContext(managementContext());
    broker.setIoExceptionHandler(ioExceptionHandler());
    broker.setPersistenceAdapter(persistenceAdapter());
    broker.setSystemUsage(systemUsage());
    broker.setSslContext(sslContext());
    broker.setNetworkConnectors(networkConnectors());
    broker.setTransportConnectors(transportConnectors());
    return broker;
  }
  
  @Bean
  BrokerPlugin[] brokerPlugins() throws Exception {
    return new BrokerPlugin[] { authenticationPlugin(), authorizationPlugin() };
  }
  
  @Bean
  BrokerPlugin authenticationPlugin() throws Exception {
    // Realistically, I would probably use the JaasAuthenticationPlugin here.
    SimpleAuthenticationPlugin simpleAuthenticationPlugin = new SimpleAuthenticationPlugin();
    
    simpleAuthenticationPlugin.setAnonymousAccessAllowed(true);
    simpleAuthenticationPlugin.setAnonymousUser("anonymous");
    simpleAuthenticationPlugin.setAnonymousGroup("anonymous");
    
    List<AuthenticationUser> users = new ArrayList<>();
    users.add(new AuthenticationUser("admin", "admin", "admin,user"));
    users.add(new AuthenticationUser("user", "password", "user"));
    simpleAuthenticationPlugin.setUsers(users);
    
    return simpleAuthenticationPlugin;
  }
  
  @Bean
  BrokerPlugin authorizationPlugin() throws Exception {
    AuthorizationPlugin authorizationPlugin = new AuthorizationPlugin();
    authorizationPlugin.setMap(authorizationMap());
    return authorizationPlugin;
  }
  
  @Bean
  AuthorizationMap authorizationMap() throws Exception {
    DefaultAuthorizationMap authorizationMap = new DefaultAuthorizationMap();
    
    List<DestinationMapEntry> destinationMapEntries = new ArrayList<>();
    
    AuthorizationEntry queueAuthorizationEntry = new AuthorizationEntry();
    queueAuthorizationEntry.setQueue(">");
    queueAuthorizationEntry.setRead("admin,user");
    queueAuthorizationEntry.setWrite("admin,user");
    queueAuthorizationEntry.setAdmin("admin,user");
    destinationMapEntries.add(queueAuthorizationEntry);
    
    AuthorizationEntry topicAuthorizationEntry = new AuthorizationEntry();
    topicAuthorizationEntry.setTopic(">");
    topicAuthorizationEntry.setRead("admin,user");
    topicAuthorizationEntry.setWrite("admin,user");
    topicAuthorizationEntry.setAdmin("admin,user");
    destinationMapEntries.add(topicAuthorizationEntry);
    
    AuthorizationEntry advisoryTopicAuthorizationEntry = new AuthorizationEntry();
    advisoryTopicAuthorizationEntry.setTopic("ActiveMQ.Advisory.>");
    advisoryTopicAuthorizationEntry.setRead("admin,user,anonymous");
    advisoryTopicAuthorizationEntry.setWrite("admin,user");
    advisoryTopicAuthorizationEntry.setAdmin("admin,user");
    destinationMapEntries.add(advisoryTopicAuthorizationEntry);
    
    authorizationMap.setAuthorizationEntries(destinationMapEntries);
    
    return authorizationMap;
  }
  
  @Bean
  PolicyMap destinationPolicy() throws Exception {
    PolicyMap policyMap = new PolicyMap();
    List<PolicyEntry> policyEntries = new ArrayList<>();
    policyEntries.add(topicPolicyEntry());
    policyEntries.add(queuePolicyEntry());
    policyMap.setPolicyEntries(policyEntries);
    return policyMap;
  }
  
  @Bean
  PolicyEntry topicPolicyEntry() throws Exception {
    PolicyEntry topicPolicyEntry = new PolicyEntry();
    topicPolicyEntry.setTopic(">");
    topicPolicyEntry.setProducerFlowControl(true);
    topicPolicyEntry.setPendingMessageLimitStrategy(topicPendingMessageLimitStrategy());
    return topicPolicyEntry;
  }
  
  @Bean
  PendingMessageLimitStrategy topicPendingMessageLimitStrategy() throws Exception {
    ConstantPendingMessageLimitStrategy topicPendingMessageLimitStrategy = new ConstantPendingMessageLimitStrategy();
    topicPendingMessageLimitStrategy.setLimit(1000);
    return topicPendingMessageLimitStrategy;
  }
  
  @Bean
  PolicyEntry queuePolicyEntry() throws Exception {
    PolicyEntry queuePolicyEntry = new PolicyEntry();
    queuePolicyEntry.setQueue(">");
    queuePolicyEntry.setProducerFlowControl(true);
    queuePolicyEntry.setNetworkBridgeFilterFactory(queueNetworkBridgeFilterFactory());
    return queuePolicyEntry;
  }
  
  @Bean
  NetworkBridgeFilterFactory queueNetworkBridgeFilterFactory() throws Exception {
    ConditionalNetworkBridgeFilterFactory queueNetworkBridgeFilterFactory = new ConditionalNetworkBridgeFilterFactory();
    queueNetworkBridgeFilterFactory.setReplayWhenNoConsumers(true);
    return queueNetworkBridgeFilterFactory;
  }
  
  @Bean
  ActiveMQDestination[] destinations() throws Exception {
    return new ActiveMQDestination[] { new ActiveMQTopic("ActiveMQ.Advisory.Connection") };
  }
  
  @Bean
  ManagementContext managementContext() throws Exception {
    ManagementContext managementContext = new ManagementContext();
    managementContext.setCreateConnector(false);
    return managementContext;
  }
  
  @Bean
  IOExceptionHandler ioExceptionHandler() throws Exception {
    DefaultIOExceptionHandler defaultIoExceptionHandler = new DefaultIOExceptionHandler();
    defaultIoExceptionHandler.setIgnoreNoSpaceErrors(false);
    return defaultIoExceptionHandler;
  }
  
  @Bean
  PersistenceAdapter persistenceAdapter() throws Exception {
    KahaDBPersistenceAdapter kahaDbPersistenceAdapter = new KahaDBPersistenceAdapter();
    kahaDbPersistenceAdapter.setDirectory(new File(config.getDataDirectory(), "kahadb"));
    return kahaDbPersistenceAdapter;
  }
  
  @Bean
  SystemUsage systemUsage() throws Exception {
    SystemUsage systemUsage = new SystemUsage();
    
    MemoryUsage memoryUsage = new MemoryUsage();
    memoryUsage.setPercentOfJvmHeap(70);
    systemUsage.setMemoryUsage(memoryUsage);
    
    StoreUsage storeUsage = new StoreUsage();
    storeUsage.setLimit(100L * 1024L * 1024L * 1024L); // 100 GB
    systemUsage.setStoreUsage(storeUsage);
    
    TempUsage tempUsage = new TempUsage();
    tempUsage.setLimit(50L * 1024L * 1024L * 1024L); // 50 GB
    systemUsage.setTempUsage(tempUsage);
    
    return systemUsage;
  }
  
  @Bean
  SslContext sslContext() throws Exception {
    SpringSslContext springSslContext = new SpringSslContext();
    springSslContext.setKeyStore(config.getSsl().getKeyStore());
    springSslContext.setKeyStorePassword(config.getSsl().getKeyStorePassword());
    return springSslContext;
  }
  
  @Bean
  List<NetworkConnector> networkConnectors() throws Exception {
    List<NetworkConnector> networkConnectors = new ArrayList<>();
    networkConnectors.add(meshNetworkConnector());
    return networkConnectors;
  }

  @Bean
  NetworkConnector meshNetworkConnector() throws Exception {
    DiscoveryNetworkConnector meshNetworkConnector = new DiscoveryNetworkConnector();
    meshNetworkConnector.setDiscoveryAgent(discoveryAgent());
    meshNetworkConnector.setBrokerURL(config.getNetwork().getUrl());
    meshNetworkConnector.setUserName(config.getNetwork().getUsername());
    meshNetworkConnector.setPassword(config.getNetwork().getPassword());
    meshNetworkConnector.setMessageTTL(-1);
    meshNetworkConnector.setConsumerTTL(1);
    return meshNetworkConnector;
  }
  
  @Bean
  DiscoveryAgent discoveryAgent() {
    SimpleDiscoveryAgent discoveryAgent = new SimpleDiscoveryAgent();
    return discoveryAgent;
  }
  
  @Bean
  List<TransportConnector> transportConnectors() throws Exception {
    List<TransportConnector> transportConnectors = new ArrayList<>();
    transportConnectors.add(vmTransportConnector());
    transportConnectors.add(openWireTransportConnector());
    transportConnectors.add(openWireSslTransportConnector());
    return transportConnectors;
  }
  
  @Bean
  TransportConnector vmTransportConnector() throws Exception {
    TransportConnector vmTransportConnector = new TransportConnector();
    vmTransportConnector.setName("vm");
    vmTransportConnector.setUri(URI.create(String.format("vm://%s?create=false", config.getName())));
    return vmTransportConnector;
  }
  
  @Bean
  TransportConnector openWireTransportConnector() throws Exception {
    TransportConnector openWireTransportConnector = new TransportConnector();
    openWireTransportConnector.setName("openwire");
    openWireTransportConnector.setUri(URI.create(String.format("tcp://%s:61616?maximumConnections=1000", config.getBindAddress())));
    openWireTransportConnector.setRebalanceClusterClients(true);
    openWireTransportConnector.setUpdateClusterClients(true);
    openWireTransportConnector.setUpdateClusterClientsOnRemove(false);
    return openWireTransportConnector;
  }
  
  @Bean
  TransportConnector openWireSslTransportConnector() throws Exception {
    TransportConnector openWireSslTransportConnector = new TransportConnector();
    openWireSslTransportConnector.setName("openwire+ssl");
    openWireSslTransportConnector.setUri(URI.create(String.format("ssl://%s:61617?maximumConnections=1000&needClientAuth=false", config.getBindAddress())));
    openWireSslTransportConnector.setRebalanceClusterClients(true);
    openWireSslTransportConnector.setUpdateClusterClients(true);
    openWireSslTransportConnector.setUpdateClusterClientsOnRemove(false);
    return openWireSslTransportConnector;
  }
}
