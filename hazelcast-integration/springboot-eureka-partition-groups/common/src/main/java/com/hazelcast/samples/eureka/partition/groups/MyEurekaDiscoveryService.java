package com.hazelcast.samples.eureka.partition.groups;

import com.hazelcast.nio.Address;
import com.hazelcast.spi.discovery.DiscoveryNode;
import com.hazelcast.spi.discovery.SimpleDiscoveryNode;
import com.hazelcast.spi.discovery.integration.DiscoveryService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.hazelcast.spi.partitiongroup.PartitionGroupMetaData.PARTITION_GROUP_ZONE;

/**
 * <P>A Hazelcast discovery service using Eureka.
 * </P>
 * <P>Eureka conectivity is provided by Spring, injecting an Eureka {@link DiscoveryClient}
 * object.
 * </P>
 * <P>This service does 2 things.
 * </P>
 * <OL>
 * <LI>{@link #discoverLocalMetadata}
 * <P>Called first, this method obtains the partition grouping data already stored in
 * Eureka by {@code my-eureka-server}. If this instance is a server and therefore storing
 * partitions, it needs this information to determine which partitions it can take.
 * </P>
 * <LI>{@link #discoverNodes}
 * <P>Called next, this method finds the nodes in the cluster to connect to. 
 * </P>
 * </OL>
 */
@Component
@Slf4j
public class MyEurekaDiscoveryService implements DiscoveryService {

	@Autowired
	private DiscoveryClient discoveryClient;
	
	@Value("${eureka.client.registerWithEureka:true}")
	public boolean registerWithEureka;

    /**
     * <P>If we are a server {@code registerWithEureka==true} we need to
     * look up the partition group metadata, so we know which partitions
     * this server can host.
     * </P>
     * <P>If we are a client we don't host partitions so can skip this.
     * </P>
     * <P><B>NOTE: <I>METHOD:</I></B> The method for setting the partition
     * group is that the metadata lists a host & port pairing with the
     * zone each should use. The zone is explicitly specified by
     * external configuration.
     * </P>
     * 
     * @return A map with one entry, the partition group for this host.
     */
    @Override
    public Map<String, Object> discoverLocalMetadata() {
    	String YML_SEPARATOR = ".";
        HashMap<String, Object> result = new HashMap<>();

        /* The metadata is only for partition groups, so we don't need this if we are
         * a Hazelcast client. Hazelcast servers register with Eureka, clients only read.
         */
        if (!this.registerWithEureka) {
        	return result;
        }
        
		log.info("\n--------------------------------------------------------------------------------");
		log.info("discoverLocalMetadata(): Hazelcast lookup to Eureka : start");

		// Find the web port this process is using.
		String port = String.valueOf(this.discoveryClient.getLocalServiceInstance().getPort());
		// Since this is a one machine example, we know which machine we are on. 
		String hostPort = "localhost" + YML_SEPARATOR + port;
				
		this.discoveryClient.getInstances(Constants.CLUSTER_NAME).forEach(
				(ServiceInstance serviceInstance) -> {

					String zone = 
							serviceInstance.getMetadata().get(
									Constants.HAZELCAST_ZONE_METADATA_KEY + YML_SEPARATOR + hostPort);

					if (zone!=null) {
						log.info("discoverLocalMetadata(): found zone '{}' for '{}'",
								zone, hostPort);
						result.put(PARTITION_GROUP_ZONE, zone);
					}
					
				});

		log.info("discoverLocalMetadata(): Hazelcast lookup to Eureka : end");
		log.info("\n--------------------------------------------------------------------------------");

		// No match will cause problems
		if (result.isEmpty()) {
			String message = String.format("discoverLocalMetadata(): found no zone for '%s'", hostPort);
			throw new RuntimeException(message);
		} else {
			return result;
		}
    }
    
    /**
     * <P>Provide a way to discover the other nodes in the cluster, that this
     * instance should connect to.
     * </P>
     * <P>Using the {@link DiscoveryClient} injected by Spring, we can connect
     * to Eureka and find all the nodes <B><U>currently</U></B> registered
     * with Eureka.
     * </P>
     * <P>When this instance starts this method is run before this node has
     * registered itself with Eureka, so the list is essentially the nodes
     * already in the cluster. If it is an empty list, this instance is the
     * first, and when it gets to the registration step it will record itself
     * for other servers to find.
     * </P>
     * <P>The ordering here is out of our control. Registering with Eureka
     * happens when the application is fully up (has connected with cluster
     * members).
     * </P>
     * <P>As a consequence of this, there is a race condition. If the first
     * two servers start at roughly the same time, they will run this method
     * at roughly the same time, and <I>before</I> each other has run the
     * registration step. So each will get an empty list from Eureka.
     * </P>
     * 
     * @return A list of {@code host}:{@code port} pairs.
     */
	@Override
	public Iterable<DiscoveryNode> discoverNodes() {
		List<DiscoveryNode> nodes = new ArrayList<>();

		log.info("\n--------------------------------------------------------------------------------");
		log.info("discoverNodes(): Hazelcast lookup to Eureka : start");
		
        // A mildly unnecessary lambda, to ensure we're not on the dark ages of Java 7
		this.discoveryClient.getInstances(Constants.CLUSTER_NAME).forEach(
				(ServiceInstance serviceInstance) -> {
					try {
						String host = serviceInstance.getMetadata().get("instanceHost");
						String port = serviceInstance.getMetadata().get("instancePort");
						
						if (host!=null && port!=null) {
							
							log.info("discoverNodes():  -> found {}:{}", host, port);

							Address address = new Address(host, Integer.valueOf(port));

							DiscoveryNode discoveryNode = 
									new SimpleDiscoveryNode(address);
							
							nodes.add(discoveryNode);
						}
						
					} catch (Exception e) {
						log.error("discoverNodes()", e);
					}
					
				});

		log.info("discoverNodes(): Hazelcast lookup to Eureka : end. Found {} item{}",
				nodes.size(), (nodes.size()==1 ? "" : "s"));
		log.info("--------------------------------------------------------------------------------\n");
		
		return nodes;
	}

	/**
	 * <P>Part of the interface, but not used.
	 * </P>
	 */
	@Override
	public void start() {
	}
	
	/**
	 * <P>Part of the interface, but not used.
	 * </P>
	 */
	@Override
	public void destroy() {
	}

}
