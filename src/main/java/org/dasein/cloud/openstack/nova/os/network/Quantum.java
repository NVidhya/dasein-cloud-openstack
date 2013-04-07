/**
 * Copyright (C) 2009-2012 Enstratius, Inc.
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

package org.dasein.cloud.openstack.nova.os.network;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.ComputeServices;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineSupport;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.NICCreateOptions;
import org.dasein.cloud.network.NetworkInterface;
import org.dasein.cloud.network.Networkable;
import org.dasein.cloud.network.RawAddress;
import org.dasein.cloud.network.RoutingTable;
import org.dasein.cloud.network.Subnet;
import org.dasein.cloud.network.SubnetState;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.network.VLANState;
import org.dasein.cloud.network.VLANSupport;
import org.dasein.cloud.openstack.nova.os.NovaMethod;
import org.dasein.cloud.openstack.nova.os.NovaOpenStack;
import org.dasein.cloud.util.APITrace;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Implements Quantum network support for OpenStack clouds with Quantum networking.
 * <p>Created by George Reese: 2/15/13 11:40 PM</p>
 * @author George Reese
 * @version 2013.04 initial version
 * @since 2013.04
 */
public class Quantum implements VLANSupport {
    static private final Logger logger = NovaOpenStack.getLogger(Quantum.class, "std");

    private NovaOpenStack provider;

    public Quantum(@Nonnull NovaOpenStack provider) {
        this.provider = provider;
    }

    private @Nonnull ProviderContext getContext() throws CloudException {
        ProviderContext ctx = getProvider().getContext();

        if( ctx == null ) {
            throw new CloudException("No context was providered for this request");
        }
        return ctx;
    }

    private @Nonnull NovaOpenStack getProvider() {
        return provider;
    }

    private @Nonnull String getTenantId() throws CloudException, InternalException {
        return ((NovaOpenStack)getProvider()).getAuthenticationContext().getTenantId();
    }

    @Override
    public boolean allowsNewSubnetCreation() throws CloudException, InternalException {
        return !((NovaOpenStack)getProvider()).isRackspace();
    }

    @Override
    public void assignRoutingTableToSubnet(@Nonnull String subnetId, @Nonnull String routingTableId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
    }

    @Override
    public void assignRoutingTableToVlan(@Nonnull String vlanId, @Nonnull String routingTableId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
    }

    @Override
    public void attachNetworkInterface(@Nonnull String nicId, @Nonnull String vmId, int index) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
    }

    @Override
    public String createInternetGateway(@Nonnull String forVlanId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
    }

    @Nonnull
    @Override
    public String createRoutingTable(@Nonnull String forVlanId, @Nonnull String name, @Nonnull String description) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
    }

    @Nonnull
    @Override
    public NetworkInterface createNetworkInterface(@Nonnull NICCreateOptions options) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
    }

    @Override
    public void addRouteToAddress(@Nonnull String toRoutingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String address) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void addRouteToGateway(@Nonnull String toRoutingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String gatewayId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void addRouteToNetworkInterface(@Nonnull String toRoutingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String nicId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public void addRouteToVirtualMachine(@Nonnull String toRoutingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String vmId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public boolean allowsNewNetworkInterfaceCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean allowsNewVlanCreation() throws CloudException, InternalException {
        return true;
    }

    public @Nonnull String createPort(@Nonnull String subnetId, @Nonnull String vmName) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.createPort");
        try {
            Subnet subnet = getSubnet(subnetId);

            if( subnet == null ) {
                throw new CloudException("No such subnet: " + subnetId);
            }
            HashMap<String,Object> wrapper = new HashMap<String,Object>();
            HashMap<String,Object> json = new HashMap<String,Object>();
            NovaMethod method = new NovaMethod((NovaOpenStack)getProvider());

            json.put("name", "Port for " + vmName);
            json.put("network_id", subnet.getProviderVlanId());

            ArrayList<Map<String,Object>> ips = new ArrayList<Map<String, Object>>();
            HashMap<String,Object> ip = new HashMap<String, Object>();

            ip.put("subnet_id", subnetId);
            ips.add(ip);

            json.put("fixed_ips", ips);

            wrapper.put("port", json);

            JSONObject result = method.postServers("/networks/" + subnet.getProviderVlanId() + "/ports", null, new JSONObject(wrapper), false);

            if( result != null && result.has("port") ) {
                try {
                    JSONObject ob = result.getJSONObject("port");

                    if( ob.has("id") ) {
                        return ob.getString("id");
                    }
                }
                catch( JSONException e ) {
                    logger.error("Unable to understand create response: " + e.getMessage());
                    throw new CloudException(e);
                }
            }
            logger.error("No port was created by the create attempt, and no error was returned");
            throw new CloudException("No port was created");

        }
        finally {
            APITrace.end();
        }
    }

    @Nonnull
    @Override
    public Subnet createSubnet(@Nonnull String cidr, @Nonnull String inProviderVlanId, @Nonnull String name, @Nonnull String description) throws CloudException, InternalException {
        if( !allowsNewSubnetCreation() ) {
            throw new OperationNotSupportedException("Subnets are not currently implemented for " + getProvider().getCloudName());
        }
        APITrace.begin(getProvider(), "VLAN.createSubnet");
        try {
            VLAN vlan = getVlan(inProviderVlanId);

            if( vlan == null ) {
                throw new CloudException("No such VLAN: " + inProviderVlanId);
            }

            HashMap<String,Object> wrapper = new HashMap<String,Object>();
            HashMap<String,Object> json = new HashMap<String,Object>();
            HashMap<String,Object> md = new HashMap<String, Object>();
            NovaMethod method = new NovaMethod((NovaOpenStack)getProvider());

            json.put("name", name);
            json.put("cidr", cidr);
            json.put("network_id", vlan.getProviderVlanId());

            IPVersion[] versions = new IPVersion[] { IPVersion.IPV4 };

            if( versions.length < 1 ) {
                json.put("ip_version", "4");
            }
            else if( versions[0].equals(IPVersion.IPV6) ) {
                json.put("ip_version", "6");
            }
            else {
                json.put("ip_version", "4");
            }
            md.put("org.dasein.description", description);
            json.put("metadata", md);

            wrapper.put("subnet", json);

            JSONObject result = method.postServers("/subnets", null, new JSONObject(wrapper), false);

            if( result != null && result.has("subnet") ) {
                try {
                    JSONObject ob = result.getJSONObject("subnet");
                    Subnet subnet = toSubnet(result.getJSONObject("subnet"), vlan);

                    if( subnet == null ) {
                        throw new CloudException("No matching subnet was generated from " + ob.toString());
                    }
                    return subnet;
                }
                catch( JSONException e ) {
                    logger.error("Unable to understand create response: " + e.getMessage());
                    throw new CloudException(e);
                }
            }
            logger.error("No subnet was created by the create attempt, and no error was returned");
            throw new CloudException("No subnet was created");

        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull VLAN createVlan(@Nonnull String cidr, @Nonnull String name, @Nonnull String description, @Nonnull String domainName, @Nonnull String[] dnsServers, @Nonnull String[] ntpServers) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.createVlan");
        try {
            HashMap<String,Object> wrapper = new HashMap<String,Object>();
            HashMap<String,Object> json = new HashMap<String,Object>();
            HashMap<String,Object> md = new HashMap<String, Object>();
            NovaMethod method = new NovaMethod((NovaOpenStack)getProvider());

            md.put("org.dasein.description", description);
            md.put("org.dasein.domain", domainName);
            if( dnsServers.length > 0 ) {
                for(int i=0; i<dnsServers.length; i++ ) {
                    md.put("org.dasein.dns." + (i+1), dnsServers[i]);
                }
            }
            if( ntpServers.length > 0 ) {
                for(int i=0; i<ntpServers.length; i++ ) {
                    md.put("org.dasein.ntp." + (i+1), ntpServers[i]);
                }
            }
            json.put("metadata", md);
            json.put("label", name);
            json.put("cidr", cidr);
            wrapper.put("network", json);
            JSONObject result = method.postServers(getNetworkResource(), null, new JSONObject(wrapper), false);

            if( result != null && result.has("network") ) {
                try {
                    JSONObject ob = result.getJSONObject("network");
                    VLAN vlan = toVLAN(result.getJSONObject("network"));

                    if( vlan == null ) {
                        throw new CloudException("No matching network was generated from " + ob.toString());
                    }
                    return vlan;
                }
                catch( JSONException e ) {
                    logger.error("Unable to understand create response: " + e.getMessage());
                    throw new CloudException(e);
                }
            }
            logger.error("No VLAN was created by the create attempt, and no error was returned");
            throw new CloudException("No VLAN was created");

        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void detachNetworkInterface(@Nonnull String nicId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    public int getMaxNetworkInterfaceCount() throws CloudException, InternalException {
        return -2;
    }

    @Override
    public int getMaxVlanCount() throws CloudException, InternalException {
        return -2;
    }

    private @Nonnull String getNetworkResource() {
        if( ((NovaOpenStack)getProvider()).isRackspace() ) {
            return "/os-networksv2";
        }
        else {
            return "/networks";
        }
    }

    @Override
    public @Nonnull String getProviderTermForNetworkInterface(@Nonnull Locale locale) {
        return "network interface";
    }

    @Override
    public @Nonnull String getProviderTermForSubnet(@Nonnull Locale locale) {
        return "subnet";
    }

    @Override
    public @Nonnull String getProviderTermForVlan(@Nonnull Locale locale) {
        return "network";
    }

    @Override
    public NetworkInterface getNetworkInterface(@Nonnull String nicId) throws CloudException, InternalException {
        return null;
    }

    @Override
    public RoutingTable getRoutingTableForSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        return null;
    }

    @Nonnull
    @Override
    public Requirement getRoutingTableSupport() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public RoutingTable getRoutingTableForVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        return null;
    }

    @Override
    public Subnet getSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.getSubnet");
        try {
            NovaMethod method = new NovaMethod((NovaOpenStack)getProvider());
            JSONObject ob = method.getServers("/subnets", subnetId, false);

            try {
                if( ob != null && ob.has("subnet") ) {
                    Subnet subnet = toSubnet(ob.getJSONObject("subnet"), null);

                    if( subnet != null ) {
                        return subnet;
                    }
                }
            }
            catch( JSONException e ) {
                logger.error("Unable to identify expected values in JSON: " + e.getMessage());
                e.printStackTrace();
                throw new CloudException(CloudErrorType.COMMUNICATION, 200, "invalidJson", "Missing JSON element for subnet in " + ob.toString());
            }
            return null;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Requirement getSubnetSupport() {
        return (((NovaOpenStack)getProvider()).isRackspace() ? Requirement.NONE : Requirement.REQUIRED);
    }

    @Override
    public VLAN getVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.getVlan");
        try {
            if( vlanId.equals("00000000-0000-0000-0000-000000000000") || vlanId.equals("11111111-1111-1111-1111-111111111111") ) {
                return null;
            }
            NovaMethod method = new NovaMethod((NovaOpenStack)getProvider());
            JSONObject ob = method.getServers(getNetworkResource(), vlanId, false);

            try {
                if( ob != null && ob.has("network") ) {
                    VLAN v = toVLAN(ob.getJSONObject("network"));

                    if( v != null ) {
                        return v;
                    }
                }
            }
            catch( JSONException e ) {
                logger.error("Unable to identify expected values in JSON: " + e.getMessage());
                e.printStackTrace();
                throw new CloudException(CloudErrorType.COMMUNICATION, 200, "invalidJson", "Missing JSON element for networks in " + ob.toString());
            }
            return null;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public boolean isNetworkInterfaceSupportEnabled() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.isSubscribed");
        try {
            NovaMethod method = new NovaMethod((NovaOpenStack)getProvider());
            JSONObject ob = method.getServers(getNetworkResource(), null, false);

            return (ob != null && ob.has("networks"));
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public boolean isSubnetDataCenterConstrained() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isVlanDataCenterConstrained() throws CloudException, InternalException {
        return false;
    }

    @Override
    public Collection<String> listFirewallIdsForNIC(@Nonnull String nicId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public Iterable<ResourceStatus> listNetworkInterfaceStatus() throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public Iterable<NetworkInterface> listNetworkInterfaces() throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public Iterable<NetworkInterface> listNetworkInterfacesForVM(@Nonnull String forVmId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public Iterable<NetworkInterface> listNetworkInterfacesInSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Nonnull
    @Override
    public Iterable<NetworkInterface> listNetworkInterfacesInVLAN(@Nonnull String vlanId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<Networkable> listResources(@Nonnull String inVlanId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.listResources");
        try {
            ArrayList<Networkable> list = new ArrayList<Networkable>();
            ComputeServices services = getProvider().getComputeServices();

            if( services != null ) {
                VirtualMachineSupport vmSupport = services.getVirtualMachineSupport();

                if( vmSupport != null ) {
                    for( VirtualMachine vm : vmSupport.listVirtualMachines() ) {
                        if( inVlanId.equals(vm.getProviderVlanId()) ) {
                            list.add(vm);
                        }
                    }
                }
            }
            return list;
        }
        finally {
            APITrace.end();
        }
    }

    @Nonnull
    @Override
    public Iterable<RoutingTable> listRoutingTables(@Nonnull String inVlanId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<Subnet> listSubnets(@Nonnull String inVlanId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.listSubnets");
        try {
            NovaMethod method = new NovaMethod((NovaOpenStack)getProvider());
            JSONObject ob = method.getServers("/subnets", null, false);
            ArrayList<Subnet> subnets = new ArrayList<Subnet>();

            try {
                if( ob != null && ob.has("subnets") ) {
                    JSONArray list = ob.getJSONArray("subnets");

                    for( int i=0; i<list.length(); i++ ) {
                        Subnet subnet = toSubnet(list.getJSONObject(i), null);

                        if( subnet != null && subnet.getProviderVlanId().equals(inVlanId) ) {
                            subnets.add(subnet);
                        }
                    }
                }
            }
            catch( JSONException e ) {
                logger.error("Unable to identify expected values in JSON: " + e.getMessage());
                e.printStackTrace();
                throw new CloudException(CloudErrorType.COMMUNICATION, 200, "invalidJson", "Missing JSON element for subnets in " + ob.toString());
            }
            return subnets;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
        ArrayList<IPVersion> versions = new ArrayList<IPVersion>();

        versions.add(IPVersion.IPV4);
        versions.add(IPVersion.IPV6);
        return versions;
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listVlanStatus() throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.listVlanStatus");
        try {
            NovaMethod method = new NovaMethod((NovaOpenStack)getProvider());
            JSONObject ob = method.getServers(getNetworkResource(), null, false);
            ArrayList<ResourceStatus> networks = new ArrayList<ResourceStatus>();

            try {
                if( ob != null && ob.has("networks") ) {
                    JSONArray list = ob.getJSONArray("networks");

                    for( int i=0; i<list.length(); i++ ) {
                        JSONObject net = list.getJSONObject(i);
                        ResourceStatus status = toStatus(net);

                        if( status != null ) {
                            if( status.getProviderResourceId().equals("00000000-0000-0000-0000-000000000000") || status.getProviderResourceId().equals("11111111-1111-1111-1111-111111111111") ) {
                                continue;
                            }
                            networks.add(status);
                        }

                    }
                }
            }
            catch( JSONException e ) {
                logger.error("Unable to identify expected values in JSON: " + e.getMessage());
                e.printStackTrace();
                throw new CloudException(CloudErrorType.COMMUNICATION, 200, "invalidJson", "Missing JSON element for networks in " + ob.toString());
            }
            return networks;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public @Nonnull Iterable<VLAN> listVlans() throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.listVlans");
        try {
            NovaMethod method = new NovaMethod((NovaOpenStack)getProvider());
            JSONObject ob = method.getServers(getNetworkResource(), null, false);
            ArrayList<VLAN> networks = new ArrayList<VLAN>();

            try {
                if( ob != null && ob.has("networks") ) {
                    JSONArray list = ob.getJSONArray("networks");

                    for( int i=0; i<list.length(); i++ ) {
                        VLAN v = toVLAN(list.getJSONObject(i));

                        if( v != null ) {
                            if( v.getProviderVlanId().equals("00000000-0000-0000-0000-000000000000") || v.getProviderVlanId().equals("11111111-1111-1111-1111-111111111111") ) {
                                continue;
                            }
                            networks.add(v);
                        }
                    }
                }
            }
            catch( JSONException e ) {
                logger.error("Unable to identify expected values in JSON: " + e.getMessage());
                e.printStackTrace();
                throw new CloudException(CloudErrorType.COMMUNICATION, 200, "invalidJson", "Missing JSON element for networks in " + ob.toString());
            }
            return networks;
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void removeInternetGateway(@Nonnull String forVlanId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
    }

    @Override
    public void removeNetworkInterface(@Nonnull String nicId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
    }

    @Override
    public void removeRoute(@Nonnull String inRoutingTableId, @Nonnull String destinationCidr) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
    }

    @Override
    public void removeRoutingTable(@Nonnull String routingTableId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Not supported");
    }

    @Override
    public void removeSubnet(String subnetId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.removeSubnet");
        try {
            NovaMethod method = new NovaMethod((NovaOpenStack)getProvider());

            method.deleteServers("/subnets", subnetId);
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public void removeVlan(String vlanId) throws CloudException, InternalException {
        APITrace.begin(getProvider(), "VLAN.removeVlan");
        try {
            NovaMethod method = new NovaMethod((NovaOpenStack)getProvider());

            method.deleteServers(getNetworkResource(), vlanId);
        }
        finally {
            APITrace.end();
        }
    }

    @Override
    public boolean supportsInternetGatewayCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsRawAddressRouting() throws CloudException, InternalException {
        return false;
    }

    private @Nonnull VLANState toState(@Nonnull String s) {
        if( s.equalsIgnoreCase("active") ) {
            return VLANState.AVAILABLE;
        }
        else if( s.equalsIgnoreCase("build") ) {
            return VLANState.PENDING;
        }
        return VLANState.PENDING;
    }

    private @Nullable ResourceStatus toStatus(@Nonnull JSONObject network) throws CloudException, InternalException {
        try {
            String id = (network.has("id") ? network.getString("id") : null);

            if( id == null ) {
                return null;
            }
            VLANState s = (network.has("status") ? toState(network.getString("status")) : VLANState.AVAILABLE);

            return new ResourceStatus(id, s);
        }
        catch( JSONException e ) {
            throw new CloudException("Invalid JSON from cloud: " + e.getMessage());
        }
    }

    private @Nullable Subnet toSubnet(@Nonnull JSONObject json, @Nullable VLAN vlan) throws CloudException, InternalException {
        try {
            if( vlan == null ) {
                String vlanId = (json.has("network_id") ? json.getString("network_id") : null);

                if( vlanId == null ) {
                    return null;
                }
                vlan = getVlan(vlanId);
                if( vlan == null ) {
                    return null;
                }
            }
            String subnetId;

            if( json.has("id") ) {
                subnetId = json.getString("id");
            }
            else {
                return null;
            }

            String cidr = (json.has("cidr") ? json.getString("cidr") : vlan.getCidr());
            String name = (json.has("name") ? json.getString("name") : null);
            String description = (json.has("description") ? json.getString("description") : null);

            HashMap<String,String> metadata = new HashMap<String, String>();

            if( json.has("metadata") ) {
                JSONObject md = json.getJSONObject("metadata");
                String[] names = JSONObject.getNames(md);

                if( names != null && names.length > 0 ) {
                    for( String n : names ) {
                        String value = md.getString(n);

                        if( value != null ) {
                            metadata.put(n, value);
                            if( n.equals("org.dasein.description") && description == null ) {
                                description = value;
                            }
                            else if( n.equals("org.dasein.name") && name == null ) {
                                name = value;
                            }
                        }
                    }
                }
            }
            if( name == null ) {
                name = subnetId + " - " + cidr;
            }
            if( description == null ) {
                description = name;
            }
            IPVersion traffic = IPVersion.IPV4;

            if( json.has("ip_version") ) {
                String version = json.getString("ip_version");

                if( version.equals("6") ) {
                    traffic = IPVersion.IPV6;
                }
            }
            Subnet subnet = new Subnet();

            subnet.setProviderOwnerId(vlan.getProviderOwnerId());
            subnet.setProviderRegionId(vlan.getProviderRegionId());
            subnet.setProviderSubnetId(subnetId);
            subnet.setCurrentState(SubnetState.AVAILABLE);
            subnet.setName(name);
            subnet.setDescription(description);
            subnet.setCidr(cidr);
            /*
            if( json.has("allocation_pools") ) {
                JSONArray p = json.getJSONArray("allocation_pools");

                if( p.length() > 0 ) {
                    AllocationPool[] pools = new AllocationPool[p.length()];

                    for( int i=0; i<p.length(); i++ ) {
                        JSONObject ob = p.getJSONObject(i);
                        String start = null, end = null;

                        if( ob.has("start") ) {
                            start = ob.getString("start");
                        }
                        if( ob.has("end") ) {
                            end = ob.getString("end");
                        }
                        if( start == null ) {
                            start = end;
                        }
                        else if( end == null ) {
                            end = start;
                        }
                        if( start != null ) {
                            pools[i] = AllocationPool.getInstance(new RawAddress(start), new RawAddress(end));
                        }
                    }
                    // begin hocus pocus to deal with the external possibility of a bad allocation pool
                    int count = 0;

                    for( AllocationPool pool : pools ) {
                        if( pool != null ) {
                            count++;
                        }
                    }
                    if( count != pools.length ) {
                        ArrayList<AllocationPool> list = new ArrayList<AllocationPool>();

                        for( AllocationPool pool : pools ) {
                            if( pool != null ) {
                                list.add(pool);
                            }
                        }
                        pools = list.toArray(new AllocationPool[list.size()]);
                    }
                    // end hocus pocus
                    subnet.havingAllocationPools(pools);
                }
            }
            if( json.has("gateway_ip") ) {
                subnet.setGateway(new RawAddress(json.getString("gateway_ip")));
            }
            */
            if( !metadata.isEmpty() ) {
                subnet.setTags(metadata);
            }
            return subnet;
        }
        catch( JSONException e ) {
            throw new CloudException("Invalid JSON from cloud: " + e.getMessage());
        }
    }

    private @Nullable VLAN toVLAN(@Nonnull JSONObject network) throws CloudException, InternalException {
        try {
            VLAN v = new VLAN();

            v.setProviderOwnerId(getTenantId());
            v.setCurrentState(VLANState.AVAILABLE);
            v.setProviderRegionId(getContext().getRegionId());
            if( network.has("id") ) {
                v.setProviderVlanId(network.getString("id"));
            }
            if( network.has("name") ) {
                v.setName(network.getString("name"));
            }
            else if( network.has("label") ) {
                v.setName(network.getString("label"));
            }
            if( network.has("cidr") ) {
                v.setCidr(network.getString("cidr"));
            }
            if( network.has("status") ) {
                v.setCurrentState(toState(network.getString("status")));
            }
            if( network.has("metadata") ) {
                JSONObject md = network.getJSONObject("metadata");
                String[] names = JSONObject.getNames(md);

                if( names != null && names.length > 0 ) {
                    for( String n : names ) {
                        String value = md.getString(n);

                        if( value != null ) {
                            if( n.equals("org.dasein.description") && v.getDescription() == null ) {
                                v.setDescription(value);
                            }
                            else if( n.equals("org.dasein.domain") && v.getDomainName() == null ) {
                                v.setDomainName(value);
                            }
                            else if( n.startsWith("org.dasein.dns.") && !n.equals("org.dasein.dsn.") && v.getDnsServers().length < 1 ) {
                                ArrayList<String> dns = new ArrayList<String>();

                                try {
                                    int idx = Integer.parseInt(n.substring("org.dasein.dns.".length() + 1));

                                    dns.ensureCapacity(idx);
                                    dns.set(idx-1, value);
                                }
                                catch( NumberFormatException ignore ) {
                                    // ignore
                                }
                                ArrayList<String> real = new ArrayList<String>();

                                for( String item : dns ) {
                                    if( item != null ) {
                                        real.add(item);
                                    }
                                }
                                v.setDnsServers(real.toArray(new String[real.size()]));
                            }
                            else if( n.startsWith("org.dasein.ntp.") && !n.equals("org.dasein.ntp.") && v.getNtpServers().length < 1 ) {
                                ArrayList<String> ntp = new ArrayList<String>();

                                try {
                                    int idx = Integer.parseInt(n.substring("org.dasein.ntp.".length() + 1));

                                    ntp.ensureCapacity(idx);
                                    ntp.set(idx-1, value);
                                }
                                catch( NumberFormatException ignore ) {
                                    // ignore
                                }
                                ArrayList<String> real = new ArrayList<String>();

                                for( String item : ntp ) {
                                    if( item != null ) {
                                        real.add(item);
                                    }
                                }
                                v.setNtpServers(real.toArray(new String[real.size()]));
                            }
                        }
                    }
                }
            }
            if( v.getProviderVlanId() == null ) {
                return null;
            }
            if( v.getCidr() == null ) {
                v.setCidr("0.0.0.0/0");
            }
            if( v.getName() == null ) {
                v.setName(v.getCidr());
                if( v.getName() == null ) {
                    v.setName(v.getProviderVlanId());
                }
            }
            if( v.getDescription() == null ) {
                v.setDescription(v.getName());
            }
            v.setSupportedTraffic(new IPVersion[] { IPVersion.IPV4, IPVersion.IPV6 });
            return v;
        }
        catch( JSONException e ) {
            throw new CloudException("Invalid JSON from cloud: " + e.getMessage());
        }
    }

    @Nonnull
    @Override
    public String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }
}
