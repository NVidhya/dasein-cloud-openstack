/**
 * Copyright (C) 2009-2012 enStratus Networks Inc
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.openstack.nova.ec2.compute;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.commons.codec.binary.Base64;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.compute.VirtualMachineSupport;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.VmStatistics;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.openstack.nova.ec2.NovaEC2;
import org.dasein.cloud.openstack.nova.ec2.NovaException;
import org.dasein.cloud.openstack.nova.ec2.NovaMethod;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

public class NovaServer implements VirtualMachineSupport {

	static public final String DESCRIBE_INSTANCES    = "DescribeInstances";
	static public final String GET_CONSOLE_OUTPUT    = "GetConsoleOutput";
	static public final String GET_METRIC_STATISTICS = "GetMetricStatistics";
	static public final String GET_PASSWORD_DATA     = "GetPasswordData";
	static public final String MONITOR_INSTANCES     = "MonitorInstances";
    static public final String REBOOT_INSTANCES      = "RebootInstances";
	static public final String RUN_INSTANCES         = "RunInstances";
    static public final String START_INSTANCES       = "StartInstances";
    static public final String STOP_INSTANCES        = "StopInstances";
	static public final String TERMINATE_INSTANCES   = "TerminateInstances";
	static public final String UNMONITOR_INSTANCES   = "UnmonitorInstances";
	
    static private List<VirtualMachineProduct> sixtyFours;
    static private List<VirtualMachineProduct> thirtyTwos;

    static {
        InputStream input = NovaServer.class.getResourceAsStream("/nova/server-products.xml");
        
        if( input != null ) {
            try {
                Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input);
                ArrayList<VirtualMachineProduct> x32s = new ArrayList<VirtualMachineProduct>();
                ArrayList<VirtualMachineProduct> x64s = new ArrayList<VirtualMachineProduct>();
                NodeList products = doc.getElementsByTagName("product");
                VirtualMachineProduct product;
                
                for( int i=0; i<products.getLength(); i++ ) {
                    Node prd = products.item(i);
                    
                    boolean x32 = prd.getAttributes().getNamedItem("x32").getNodeValue().trim().equalsIgnoreCase("true");
                    boolean x64 = prd.getAttributes().getNamedItem("x64").getNodeValue().trim().equalsIgnoreCase("true");
                    
                    product = new VirtualMachineProduct();
                    product.setProductId(prd.getAttributes().getNamedItem("productId").getNodeValue().trim());
                    product.setDiskSizeInGb(Integer.parseInt(prd.getAttributes().getNamedItem("diskSizeInGb").getNodeValue().trim()));
                    product.setRamInMb(Integer.parseInt(prd.getAttributes().getNamedItem("ramInMb").getNodeValue().trim()));
                    product.setCpuCount(Integer.parseInt(prd.getAttributes().getNamedItem("cpuCount").getNodeValue().trim()));
                    
                    NodeList attrs = prd.getChildNodes();
                    
                    for( int j=0; j<attrs.getLength(); j++ ) {
                        Node attr = attrs.item(j);
                        
                        if( attr.getNodeName().equals("name") ) {
                            product.setName(attr.getFirstChild().getNodeValue().trim());
                        }
                        else if( attr.getNodeName().equals("description") ) {
                            product.setDescription(attr.getFirstChild().getNodeValue().trim());
                        }
                    }
                    if( x32 ) {
                        x32s.add(product);
                    }
                    if( x64 ) {
                        x64s.add(product);
                    }
                }
                thirtyTwos = Collections.unmodifiableList(x32s);
                sixtyFours = Collections.unmodifiableList(x64s);
            }
            catch( Throwable t ) {
                t.printStackTrace();
            }
        }
        else {
            ArrayList<VirtualMachineProduct> sizes = new ArrayList<VirtualMachineProduct>();
            VirtualMachineProduct product = new VirtualMachineProduct();
            
            product = new VirtualMachineProduct();
            product.setProductId("m1.small");
            product.setName("Small Instance (m1.small)");
            product.setDescription("Small Instance (m1.small)");
            product.setCpuCount(1);
            product.setDiskSizeInGb(160);
            product.setRamInMb(1700);
            sizes.add(product);        
    
            product = new VirtualMachineProduct();
            product.setProductId("c1.medium");
            product.setName("High-CPU Medium Instance (c1.medium)");
            product.setDescription("High-CPU Medium Instance (c1.medium)");
            product.setCpuCount(5);
            product.setDiskSizeInGb(350);
            product.setRamInMb(1700);
            sizes.add(product);   
    
            product = new VirtualMachineProduct();
            product.setProductId("m1.large");
            product.setName("Large Instance (m1.large)");
            product.setDescription("Large Instance (m1.large)");
            product.setCpuCount(4);
            product.setDiskSizeInGb(850);
            product.setRamInMb(7500);
            sizes.add(product); 
            
            product = new VirtualMachineProduct();
            product.setProductId("m1.xlarge");
            product.setName("Extra Large Instance (m1.xlarge)");
            product.setDescription("Extra Large Instance (m1.xlarge)");
            product.setCpuCount(8);
            product.setDiskSizeInGb(1690);
            product.setRamInMb(15000);
            sizes.add(product); 
            
            product = new VirtualMachineProduct();
            product.setProductId("c1.xlarge");
            product.setName("High-CPU Extra Large Instance (c1.xlarge)");
            product.setDescription("High-CPU Extra Large Instance (c1.xlarge)");
            product.setCpuCount(20);
            product.setDiskSizeInGb(1690);
            product.setRamInMb(7000);
            sizes.add(product); 
            
            thirtyTwos = Collections.unmodifiableList(sizes);
            sixtyFours = Collections.unmodifiableList(sizes);
        }
    }
    
	private NovaEC2 provider = null;
	
	NovaServer(NovaEC2 provider) {
		this.provider = provider;
	}

	@Override
	public void boot(String instanceId) throws InternalException, CloudException {
        Map<String,String> parameters = provider.getStandardParameters(provider.getContext(), START_INSTANCES);
        NovaMethod method;
        
        parameters.put("InstanceId.1", instanceId);
        method = new NovaMethod(provider, provider.getEc2Url(), parameters);
        try {
            method.invoke();
        }
        catch( NovaException e ) {
            NovaEC2.getLogger(NovaServer.class, "std").error(e.getSummary());
            throw new CloudException(e);
        }
	}
	
    public VirtualMachine clone(String serverId, String intoDcId, String name, String description, boolean powerOn, String ... firewallIds) throws InternalException, CloudException {
        /*
        final ArrayList<Volume> oldVolumes = new ArrayList<Volume>();
        final ArrayList<Volume> newVolumes = new ArrayList<Volume>();
        final String id = serverId;
        final String zoneId = inZoneId;

        for( Volume volume : provider.getVolumeServices().list() ) {
            String svr = volume.getServerId();
            
            if( svr == null || !svr.equals(serverId)) {
                continue;
            }
            oldVolumes.add(volume);
        }
        Callable<ServerImage> imageTask = new Callable<ServerImage>() {
            @Override
            public ServerImage call() throws Exception {
                provider.getImageServices().create(id);
            }
            
        };
        Callable<Boolean> snapshotTask = new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
                for( Volume volume : oldVolumes ) {
                    String snapshotId = provider.getSnapshotServices().create(volume.getProviderVolumeId(), "Clone of " + volume.getName());
                    String volumeId = provider.getVolumeServices().create(snapshotId, volume.getSizeInGigabytes(), zoneId);
                    
                    newVolumes.add(provider.getVolumeServices().getVolume(volumeId));
                }
                return true;
            }
        };
        */
        throw new OperationNotSupportedException("AWS instances cannot be cloned.");
    }


    @Override
    public void enableAnalytics(String instanceId) throws InternalException, CloudException {
        // NO-OP
    }
    
	private Architecture getArchitecture(String size) {
		if( size.equals("m1.small") || size.equals("c1.medium") ) {
			return Architecture.I32;
		}
		else {
			return Architecture.I64;
		}
	}
    
	@Override
	public String getConsoleOutput(String instanceId) throws InternalException, CloudException {
		Map<String,String> parameters = provider.getStandardParameters(provider.getContext(), GET_CONSOLE_OUTPUT);
		long timestamp = -1L;
		String output = null;
		NovaMethod method;
        NodeList blocks;
		Document doc;
        
		parameters.put("InstanceId", instanceId);
        method = new NovaMethod(provider, provider.getEc2Url(), parameters);
        try {
        	doc = method.invoke();
        }
        catch( NovaException e ) {
        	String code = e.getCode();
        	
        	if( code != null && code.startsWith("InvalidInstanceID") ) {
        		return null;
        	}
        	NovaEC2.getLogger(NovaServer.class, "std").error(e.getSummary());
        	throw new CloudException(e);
        }
        blocks = doc.getElementsByTagName("timestamp");
        for( int i=0; i<blocks.getLength(); i++ ) {
            String ts = blocks.item(i).getFirstChild().getNodeValue();
        	
            timestamp = provider.parseTime(ts);
        	if( timestamp > -1L ) {
        		break;
        	}
        }
        blocks = doc.getElementsByTagName("output");
        for( int i=0; i<blocks.getLength(); i++ ) {
        	Node item = blocks.item(i);
        	
        	if( item.hasChildNodes() ) {
        	    output = item.getFirstChild().getNodeValue().trim();
        	    if( output != null ) {
        	        break;
        	    }
        	}
        }
        try {
			return new String(Base64.decodeBase64(output.getBytes("utf-8")), "utf-8");
		} 
        catch( UnsupportedEncodingException e ) {
        	NovaEC2.getLogger(NovaServer.class, "std").error(e);
        	e.printStackTrace();
        	throw new InternalException(e);
		}
	}

	@Override
	public Iterable<String> listFirewalls(String instanceId) throws InternalException, CloudException {
		Map<String,String> parameters = provider.getStandardParameters(provider.getContext(), DESCRIBE_INSTANCES);
		ArrayList<String> firewalls = new ArrayList<String>();
		NovaMethod method;
        NodeList blocks;
		Document doc;
        
        parameters.put("InstanceId.1", instanceId);
        method = new NovaMethod(provider, provider.getEc2Url(), parameters);
        try {
        	doc = method.invoke();
        }
        catch( NovaException e ) {
        	String code = e.getCode();
        	
        	if( code != null && code.startsWith("InvalidInstanceID") ) {
        		return firewalls;
        	}
        	NovaEC2.getLogger(NovaServer.class, "std").error(e.getSummary());
        	throw new CloudException(e);
        }
        blocks = doc.getElementsByTagName("groupSet");
        for( int i=0; i<blocks.getLength(); i++ ) {
        	NodeList items = blocks.item(i).getChildNodes();
        	
            for( int j=0; j<items.getLength(); j++ ) {
            	Node item = items.item(j);
            	
            	if( item.getNodeName().equals("item") ) {
            		NodeList ids = item.getChildNodes();
            		
            		for( int k=0; k<ids.getLength(); k++ ) {
            			Node id = ids.item(k);
            			
            			if( id.hasChildNodes() ) {
            			    firewalls.add(id.getFirstChild().getNodeValue().trim());
            			}
            		}
            	}
            }
        }
        return firewalls;
	}
	
	@Override
	public String getProviderTermForServer(Locale locale) {
		return "instance";
	}

	@Override
	public VirtualMachine getVirtualMachine(String instanceId) throws InternalException, CloudException {
		Map<String,String> parameters = provider.getStandardParameters(provider.getContext(), DESCRIBE_INSTANCES);
		NovaMethod method;
        NodeList blocks;
		Document doc;
        
        parameters.put("InstanceId.1", instanceId);
        method = new NovaMethod(provider, provider.getEc2Url(), parameters);
        try {
        	doc = method.invoke();
        }
        catch( NovaException e ) {
        	String code = e.getCode();
        	
        	if( code != null && code.startsWith("InvalidInstanceID") ) {
        		return null;
        	}
        	NovaEC2.getLogger(NovaServer.class, "std").error(e.getSummary());
        	throw new CloudException(e);
        }
        blocks = doc.getElementsByTagName("instancesSet");
        for( int i=0; i<blocks.getLength(); i++ ) {
        	NodeList instances = blocks.item(i).getChildNodes();
        	
            for( int j=0; j<instances.getLength(); j++ ) {
            	Node instance = instances.item(j);
            	
            	if( instance.getNodeName().equals("item") ) {
            		VirtualMachine server = toVirtualMachine(instance);
            		
            		if( server.getProviderVirtualMachineId().equals(instanceId) ) {
            			return server;
            		}
            	}
            }
        }
        return null;
	}
	
	@Override
	public VirtualMachineProduct getProduct(String sizeId) {
        System.out.println("Looking for: " + sizeId + " among " + get64s());
        for( VirtualMachineProduct product : get64s() ) {
            if( product.getProductId().equals(sizeId) ) {
                return product;
            }
        }
        for( VirtualMachineProduct product : get32s() ) {
            if( product.getProductId().equals(sizeId) ) {
                return product;
            }
        }
        return null;
	}
	
	private VmState getServerState(String state) {
        if( state.equals("pending") ) {
            return VmState.PENDING;
        }
        else if( state.equals("running") ) {
            return VmState.RUNNING;
        }
        else if( state.equals("terminating") || state.equals("stopping") ) {
            return VmState.STOPPING;
        }
        else if( state.equals("stopped") ) {
            return VmState.PAUSED;
        }
        else if( state.equals("shutting-down") ) {
            return VmState.STOPPING;
        }
        else if( state.equals("terminated") ) {
            return VmState.TERMINATED;
        }
        else if( state.equals("rebooting") ) {
            return VmState.REBOOTING;
        }
        NovaEC2.getLogger(NovaServer.class, "std").warn("Unknown server state: " + state);
        return VmState.PENDING;
	}

	@Override
	public VmStatistics getVMStatistics(String instanceId, long startTimestamp, long endTimestamp) throws InternalException, CloudException {
	    return new VmStatistics();
	}

    @Override
    public Iterable<VmStatistics> getVMStatisticsForPeriod(String instanceId, long startTimestamp, long endTimestamp) throws InternalException, CloudException {
        return Collections.emptyList();
    }
    
    @Override
    public boolean isSubscribed() throws InternalException, CloudException {
        Map<String,String> parameters = provider.getStandardParameters(provider.getContext(), DESCRIBE_INSTANCES);
        NovaMethod method = new NovaMethod(provider, provider.getEc2Url(), parameters);
        
        try {
            method.invoke();
            return true;
        }
        catch( NovaException e ) {
            if( e.getStatus() == HttpServletResponse.SC_UNAUTHORIZED || e.getStatus() == HttpServletResponse.SC_FORBIDDEN ) {
                return false;
            }
            String code = e.getCode();
            
            if( code != null && code.equals("SignatureDoesNotMatch") ) {
                return false;
            }
            NovaEC2.getLogger(NovaServer.class, "std").warn(e.getSummary());
            throw new CloudException(e);
        }
    }
    
    private List<VirtualMachineProduct> get64s() {
        return sixtyFours;
    }
    
    
    private List<VirtualMachineProduct> get32s() {
        return thirtyTwos;
    }
    
	@Override
	public Iterable<VirtualMachineProduct> listProducts(Architecture architecture) throws InternalException, CloudException {
		if( architecture == null ) {
		    return Collections.emptyList();
		}
		switch( architecture ) {
		case I32: return get32s();
		case I64: return get64s();
		default: return Collections.emptyList();
		}
	}
	
	private String guess(String privateDnsAddress) {
	    String dnsAddress = privateDnsAddress;
	    String[] parts = dnsAddress.split("\\.");
	    
	    if( parts != null & parts.length > 1 ) {
	        dnsAddress = parts[0];
	    }
	    if( dnsAddress.startsWith("ip-") ) {
	        dnsAddress = dnsAddress.replace('-', '.');
            return dnsAddress.substring(3);
	    }
	    return null;
	}
	
	@Override
    public VirtualMachine launch(String imageId, VirtualMachineProduct size, String inZoneId, String name, String description, String keypair, String inVlanId, boolean withMonitoring, boolean asImageSandbox, String ... protectedByFirewalls) throws CloudException, InternalException {
	    return launch(imageId, size, inZoneId, name, description, keypair, inVlanId, withMonitoring, asImageSandbox, protectedByFirewalls, new Tag[0]);
	}

	@Override
	public VirtualMachine launch(String imageId, VirtualMachineProduct size, String inZoneId, String name, String description, String keypair, String inVlanId, boolean withMonitoring, boolean asImageSandbox, String[] protectedByFirewalls, Tag ... tags) throws CloudException, InternalException {
		Map<String,String> parameters = provider.getStandardParameters(provider.getContext(), RUN_INSTANCES);
		NovaMethod method;
        NodeList blocks;
		Document doc;
        
        parameters.put("ImageId", imageId);
        parameters.put("MinCount", "1");
        parameters.put("MaxCount", "1");
        parameters.put("InstanceType", size.getProductId());
        if( protectedByFirewalls != null && protectedByFirewalls.length > 0 ) {
        	int i = 1;
        	
        	for( String id : protectedByFirewalls ) {
        		parameters.put("SecurityGroup." + (i++), id);
        	}
        }
        if( inZoneId != null ) {
        	parameters.put("Placement.AvailabilityZone", inZoneId);
        }
        if( keypair != null ) {
        	parameters.put("KeyName", keypair);
        }
        if( inVlanId != null ) {
        	parameters.put("SubnetId", inVlanId);
        }
        method = new NovaMethod(provider, provider.getEc2Url(), parameters);
        try {
        	doc = method.invoke();
        }
        catch( NovaException e ) {
            String code = e.getCode();
            
            if( code != null && code.equals("InsufficientInstanceCapacity") ) {
                return null;
            }
        	NovaEC2.getLogger(NovaServer.class, "std").error(e.getSummary());
        	throw new CloudException(e);
        }
        blocks = doc.getElementsByTagName("instancesSet");
        VirtualMachine server = null;
        for( int i=0; i<blocks.getLength(); i++ ) {
        	NodeList instances = blocks.item(i).getChildNodes();
        	
            for( int j=0; j<instances.getLength(); j++ ) {
            	Node instance = instances.item(j);
            	
            	if( instance.getNodeName().equals("item") ) {
            		server = toVirtualMachine(instance);
            		if( server != null ) {
            			break;
            		}
            	}
            }
        }
        if( server != null && keypair != null ) {
        	try {
                final String sid = server.getProviderVirtualMachineId();
                
        	    Callable<String> pwMethod = new Callable<String>() {
        	        public String call() throws CloudException {
        	            try {
        	                Map<String,String> params = provider.getStandardParameters(provider.getContext(), GET_PASSWORD_DATA);
        	                NovaMethod m;
        	                
        	                params.put("InstanceId", sid);
        	                m = new NovaMethod(provider, provider.getEc2Url(), params);
        	                
        	                Document doc = m.invoke();
        	                NodeList blocks = doc.getElementsByTagName("passwordData");
        	                
        	                if( blocks.getLength() > 0 ) {
        	                    Node pw = blocks.item(0);

        	                    if( pw.hasChildNodes() ) {
        	                        String password = pw.getFirstChild().getNodeValue();
        	                    
        	                        provider.release();
        	                        return password;
        	                    }
        	                    return null;
        	                }
        	                return null;
        	            }
        	            catch( Throwable t ) {
        	                throw new CloudException("Unable to retrieve password for " + sid + ", Let's hope it's Unix: " + t.getMessage());                       	                
        	            }
        	        }
        	    };
                
        	    provider.hold();
        	    try {
        	        String password = pwMethod.call();
        	    
                    if( password == null ) {
                        server.setRootPassword(null);
                        server.setPasswordCallback(pwMethod);
                    }
                    else {
                        server.setRootPassword(password);
                    }
                    server.setPlatform(Platform.WINDOWS);
        	    }
        	    catch( CloudException e ) {
        	        NovaEC2.getLogger(NovaServer.class, "std").warn(e.getMessage());
        	    }
        	}
        	catch( Throwable t ) {
                NovaEC2.getLogger(NovaServer.class, "std").warn("Unable to retrieve password for " + server.getProviderVirtualMachineId() + ", Let's hope it's Unix: " + t.getMessage());               
        	}
        }
        Tag[] toCreate;
        int i = 0;
        
        if( tags == null ) {
            toCreate = new Tag[2];
        }
        else {
            int count = 0;
            
            for( Tag t : tags ) {
                if( t.getKey().equalsIgnoreCase("name") || t.getKey().equalsIgnoreCase("description") ) {
                    continue;
                }
                count++;
            }
            toCreate = new Tag[count + 2];
            for( Tag t : tags ) {
                if( t.getKey().equalsIgnoreCase("name") || t.getKey().equalsIgnoreCase("description") ) {
                    continue;
                }
                toCreate[i++] = t;
            }
        }
        Tag t = new Tag();
        
        t.setKey("Name");
        t.setValue(name);
        toCreate[i++] = t;
        t = new Tag();
        t.setKey("Description");
        t.setValue(description);
        toCreate[i++] = t;
        provider.createTags(server.getProviderVirtualMachineId(), toCreate);
        while( server.getProviderDataCenterId().equals("unknown zone") ) {
            try { Thread.sleep(1000L); }
            catch( InterruptedException e ) { }
            try { 
                server = getVirtualMachine(server.getProviderVirtualMachineId());           
                if( server == null ) {
                    return null;
                }
            }
            catch( Throwable ignore ) {
                // ignore
            }
        }
        return server;
	}

	@Override
	public Iterable<VirtualMachine> listVirtualMachines() throws InternalException, CloudException {
		Map<String,String> parameters = provider.getStandardParameters(provider.getContext(), DESCRIBE_INSTANCES);
		NovaMethod method = new NovaMethod(provider, provider.getEc2Url(), parameters);
		ArrayList<VirtualMachine> list = new ArrayList<VirtualMachine>();
        NodeList blocks;
		Document doc;
        
        try {
        	doc = method.invoke();
        }
        catch( NovaException e ) {
        	NovaEC2.getLogger(NovaServer.class, "std").error(e.getSummary());
        	throw new CloudException(e);
        }
        blocks = doc.getElementsByTagName("instancesSet");
        for( int i=0; i<blocks.getLength(); i++ ) {
        	NodeList instances = blocks.item(i).getChildNodes();
        	
            for( int j=0; j<instances.getLength(); j++ ) {
            	Node instance = instances.item(j);
            	
            	if( instance.getNodeName().equals("item") ) {
            		list.add(toVirtualMachine(instance));
            	}
            }
        }
        return list;
	}

    @Override
    public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }

	@Override
	public void pause(String instanceId) throws InternalException, CloudException {
        Map<String,String> parameters = provider.getStandardParameters(provider.getContext(), STOP_INSTANCES);
        NovaMethod method;
        
        parameters.put("InstanceId.1", instanceId);
        method = new NovaMethod(provider, provider.getEc2Url(), parameters);
        try {
            method.invoke();
        }
        catch( NovaException e ) {
            NovaEC2.getLogger(NovaServer.class, "std").error(e.getSummary());
            throw new CloudException(e);
        }	    
	}

	@Override
	public void reboot(String instanceId) throws CloudException, InternalException {
		Map<String,String> parameters = provider.getStandardParameters(provider.getContext(), REBOOT_INSTANCES);
		NovaMethod method;
        
        parameters.put("InstanceId.1", instanceId);
        method = new NovaMethod(provider, provider.getEc2Url(), parameters);
        try {
        	method.invoke();
        }
        catch( NovaException e ) {
        	NovaEC2.getLogger(NovaServer.class, "std").error(e.getSummary());
        	throw new CloudException(e);
        }
	}

	private String resolve(String dnsName) {
        if( dnsName != null && dnsName.length() > 0 ) {
            InetAddress[] addresses;
            
            try {
                addresses = InetAddress.getAllByName(dnsName);
            }
            catch( UnknownHostException e ) {
                addresses = null;
            }
            if( addresses != null && addresses.length > 0 ) {
                dnsName = addresses[0].getHostAddress();
            }
            else {
                dnsName = dnsName.split("\\.")[0];
                dnsName = dnsName.replaceAll("-", "\\.");
                dnsName = dnsName.substring(4);
            }
        }        
        return dnsName;
	}

    @Override
    public boolean supportsAnalytics() throws CloudException, InternalException {
        return false;
    }
    
	@Override
	public void terminate(String instanceId) throws InternalException, CloudException {
		Map<String,String> parameters = provider.getStandardParameters(provider.getContext(), TERMINATE_INSTANCES);
		NovaMethod method;
        
        parameters.put("InstanceId.1", instanceId);
        method = new NovaMethod(provider, provider.getEc2Url(), parameters);
        try {
        	method.invoke();
        }
        catch( NovaException e ) {
        	NovaEC2.getLogger(NovaServer.class, "std").error(e.getSummary());
        	throw new CloudException(e);
        }
	}

	private VirtualMachine toVirtualMachine(Node instance) throws CloudException {
		NodeList attrs = instance.getChildNodes();
		VirtualMachine server = new VirtualMachine();

		server.setPersistent(false);
		server.setProviderOwnerId(provider.getContext().getAccountNumber());
		server.setCurrentState(VmState.PENDING);
		server.setName(null);
		server.setDescription(null);
		for( int i=0; i<attrs.getLength(); i++ ) {
			Node attr = attrs.item(i);
			String name;
			
			name = attr.getNodeName();
			if( name.equals("instanceId") ) {
				String value = attr.getFirstChild().getNodeValue().trim();
				
				server.setProviderVirtualMachineId(value);
			}
			else if( name.equals("imageId") ) {
				String value = attr.getFirstChild().getNodeValue().trim();
				
				server.setProviderMachineImageId(value);
			}
			else if( name.equals("instanceState") ) {
				NodeList details = attr.getChildNodes();
				
				for( int j=0; j<details.getLength(); j++ ) {
					Node detail = details.item(j);
					
					name = detail.getNodeName();
					if( name.equals("name") ) {
						String value = detail.getFirstChild().getNodeValue().trim();
						
						server.setCurrentState(getServerState(value));
					}
				}
			}
			else if( name.equals("privateDnsName") ) {
				if( attr.hasChildNodes() ) {
					String value = attr.getFirstChild().getNodeValue();
				
					server.setPrivateDnsAddress(value);
                    if( server.getPrivateIpAddresses() == null || server.getPrivateIpAddresses().length < 1 ) {
					    value = guess(value);
					    if( value != null ) {
					        server.setPrivateIpAddresses(new String[] { value });
					    }
					    else {
					        server.setPrivateIpAddresses(new String[0]);
					    }
					}
				}
			}
			else if( name.equals("dnsName") ) {
                if( attr.hasChildNodes() ) {
                    String value = attr.getFirstChild().getNodeValue();

                    server.setPublicDnsAddress(value);
					if( server.getPublicIpAddresses() == null || server.getPublicIpAddresses().length < 1 ) {
					    server.setPublicIpAddresses(new String[] { resolve(value) });
					}
				}
			}
            else if( name.equals("privateIpAddress") ) {
                if( attr.hasChildNodes() ) {
                    String value = attr.getFirstChild().getNodeValue();

                    server.setPrivateIpAddresses(new String[] { value });
                }
            }
            else if( name.equals("ipAddress") ) {
                if( attr.hasChildNodes() ) {
                    String value = attr.getFirstChild().getNodeValue();

                    server.setPublicIpAddresses(new String[] { value });
                }
            }
            else if( name.equals("rootDeviceType") ) {
                if( attr.hasChildNodes() ) {
                    server.setPersistent(attr.getFirstChild().getNodeValue().equalsIgnoreCase("ebs"));
                }                
            }
            else if( name.equals("tagSet") ) {
                if( attr.hasChildNodes() ) {
                    NodeList tags = attr.getChildNodes();
                    
                    for( int j=0; j<tags.getLength(); j++ ) {
                        Node tag = tags.item(j);
                        
                        if( tag.getNodeName().equals("item") && tag.hasChildNodes() ) {
                            NodeList parts = tag.getChildNodes();
                            String key = null, value = null;
                            
                            for( int k=0; k<parts.getLength(); k++ ) {
                                Node part = parts.item(k);
                                
                                if( part.getNodeName().equalsIgnoreCase("key") ) {
                                    if( part.hasChildNodes() ) {
                                        key = part.getFirstChild().getNodeValue().trim();
                                    }
                                }
                                else if( part.getNodeName().equalsIgnoreCase("value") ) {
                                    if( part.hasChildNodes() ) {
                                        value = part.getFirstChild().getNodeValue().trim();
                                    }
                                }
                            }
                            if( key != null ) {
                                if( key.equalsIgnoreCase("name") ) {
                                    server.setName(value);
                                }
                                else if( key.equalsIgnoreCase("description") ) {
                                    server.setDescription(value);
                                }
                                else {
                                    server.addTag(key, value);
                                }
                            }
                        }
                    }
                }
            }
			else if( name.equals("instanceType") && attr.hasChildNodes()) {
				String value = attr.getFirstChild().getNodeValue().trim();
				VirtualMachineProduct product = getProduct(value);
				
				server.setProduct(product);
                if( product == null ) {
                    server.setArchitecture(Architecture.I64);
                }
                else {
                    server.setArchitecture(getArchitecture(product.getProductId()));
                }
			}
			else if( name.equals("launchTime") ) {
				String value = attr.getFirstChild().getNodeValue().trim();

				server.setLastBootTimestamp(provider.parseTime(value));
				server.setCreationTimestamp(server.getLastBootTimestamp());
			}
			else if( name.equals("platform") ) {
			    if( attr.hasChildNodes() ) {
			        server.setPlatform(Platform.guess(attr.getFirstChild().getNodeValue()));
			    }
			}
			else if( name.equals("placement") ) {
				NodeList details = attr.getChildNodes();
				
				for( int j=0; j<details.getLength(); j++ ) {
					Node detail = details.item(j);
					
					name = detail.getNodeName();
					if( name.equals("availabilityZone") ) {
					    if( detail.hasChildNodes() ) {
					        String value = detail.getFirstChild().getNodeValue().trim();
						
					        server.setProviderDataCenterId(value);
					    }
					}
				}
			}
		}
		if( server.getPlatform() == null ) {
		    server.setPlatform(Platform.UNKNOWN);
		}
        server.setProviderRegionId(provider.getContext().getRegionId());
        if( server.getName() == null ) {
            server.setName(server.getProviderVirtualMachineId());
        }
        if( server.getDescription() == null ) {
            VirtualMachineProduct p = server.getProduct();
            
            server.setDescription(server.getName() + " (" + (p == null ? "unknown" : p.getName()) + ")");
        }
		return server;
	}
	
	@Override
	public void disableAnalytics(String instanceId) throws InternalException, CloudException {
	    // NO-OP
	}
}
