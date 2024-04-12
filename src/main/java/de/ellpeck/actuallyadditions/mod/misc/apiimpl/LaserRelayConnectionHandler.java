/*
 * This file ("LaserRelayConnectionHandler.java") is part of the Actually Additions mod for Minecraft.
 * It is created and owned by Ellpeck and distributed
 * under the Actually Additions License to be found at
 * http://ellpeck.de/actaddlicense
 * View the source code at https://github.com/Ellpeck/ActuallyAdditions
 *
 * © 2015-2017 Ellpeck
 */

package de.ellpeck.actuallyadditions.mod.misc.apiimpl;

import com.google.common.collect.ImmutableSet;
import de.ellpeck.actuallyadditions.api.laser.IConnectionPair;
import de.ellpeck.actuallyadditions.api.laser.ILaserRelayConnectionHandler;
import de.ellpeck.actuallyadditions.api.laser.INetwork;
import de.ellpeck.actuallyadditions.api.laser.LaserType;
import de.ellpeck.actuallyadditions.api.laser.Network;
import de.ellpeck.actuallyadditions.mod.data.WorldData;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityLaserRelay;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LaserRelayConnectionHandler implements ILaserRelayConnectionHandler {
    
    public static NBTTagCompound writeNetworkToNBT(INetwork network) {
        return network.serializeNBT();
    }
    
    public static INetwork readNetworkFromNBT(NBTTagCompound tag) {
        INetwork network = new Network();
        network.deserializeNBT(tag);
        return network;
    }
    
    /**
     * Merges two laserRelayNetworks together
     * (Actually puts everything from the second network into the first one and removes the second one)
     */
    private void mergeNetworks(INetwork firstNetwork, INetwork secondNetwork, World world) {
        firstNetwork.absorbNetwork(secondNetwork);
        secondNetwork.incrementChangeAmount();
        
        secondNetwork.getMembers()
                     .forEach(pos -> networkLookupMap.put(pos, firstNetwork));
        
        WorldData data = WorldData.get(world);
        data.removeNetwork(secondNetwork);
        //System.out.println("Merged Two Networks!");
    }
    
    
    
    private Map<BlockPos, INetwork> networkLookupMap = new Object2ObjectOpenHashMap<>();

    /**
     * Gets all Connections for a Relay
     */
    @Override
    public Set<IConnectionPair> getConnectionsFor(BlockPos relay, World world) {
        INetwork networkFor = getNetworkFor(relay, world);
        if (networkFor != null) {
            return networkFor.getConnectionsFor(relay);
        } else {
            return ImmutableSet.of();
        }
    }

    /**
     * Removes a Relay from its Network
     */
    @Override
    public void removeRelayFromNetwork(BlockPos relayPos, World world) {
        INetwork networkFor = this.getNetworkFor(relayPos, world);
        
        if (networkFor == null)
            return;
        
        Pair<Set<INetwork>, Set<BlockPos>> newNetworks_orphanedNodes = networkFor.removeRelay(relayPos, world);
        
        networkLookupMap.remove(relayPos);
        
        if (newNetworks_orphanedNodes == null)
            return;
        
        
        Set<INetwork> newNetworks = newNetworks_orphanedNodes.getLeft();
        if (!newNetworks.isEmpty()) {
            handleNewNetworks(newNetworks, world);
        }
        
        Set<BlockPos> orphanedNodes = newNetworks_orphanedNodes.getRight();
        if (!orphanedNodes.isEmpty()) {
            handleOrphanedNodes(orphanedNodes);
        }
    }
    
    @Override
    public void removeNetwork(INetwork network, World world) {
        WorldData.get(world).removeNetwork(network);
        
        network.getMembers().forEach(networkLookupMap::remove);
    }
    
    /**
     * Gets a Network for a Relay
     */
    @Override
    public INetwork getNetworkFor(BlockPos relay, World world) {
        if (world != null) { // TODO sync with world data
            return this.networkLookupMap.get(relay);
        }
        return null;
    }

    @Override
    public boolean addConnection(BlockPos firstRelay, BlockPos secondRelay, LaserType type, World world) {
        return this.addConnection(firstRelay, secondRelay, type, world, false);
    }

    /**
     * Adds a new connection between two relays
     * (Puts it into the correct network!)
     */
    @Override
    public boolean addConnection(BlockPos firstRelay, BlockPos secondRelay, LaserType type, World world, boolean suppressConnectionRender) {
        return this.addConnection(firstRelay, secondRelay, type, world, suppressConnectionRender, false);
    }

    @Override
    public boolean addConnection(BlockPos firstRelay, BlockPos secondRelay, LaserType type, World world, boolean suppressConnectionRender, boolean removeIfConnected) {
        if (firstRelay == null
            || secondRelay == null
            || Objects.equals(firstRelay, secondRelay)
        ) {
            return false;
        }
        
        INetwork firstNetwork = this.getNetworkFor(firstRelay, world);
        INetwork secondNetwork = this.getNetworkFor(secondRelay, world);
        
        ConnectionPair newPair = new ConnectionPair(firstRelay, secondRelay, type, suppressConnectionRender);
        
        //No Network exists
        if (firstNetwork == null && secondNetwork == null) {
            firstNetwork = new Network();
            firstNetwork.addConnection(newPair);
            networkLookupMap.put(firstRelay, firstNetwork);
            WorldData.get(world).registerNetwork(firstNetwork);
        }
        //The same Network
        else if (firstNetwork == secondNetwork) {
            if (removeIfConnected) {
                this.removeConnection(world, firstRelay, secondRelay);
                return true;
            } else {
                return false;
            }
        }
        //Both relays have laserRelayNetworks
        else if (firstNetwork != null && secondNetwork != null) {
            mergeNetworks(firstNetwork, secondNetwork, world);
            firstNetwork.addConnection(newPair);
        }
        //Only first network exists
        else if (firstNetwork != null) {
            firstNetwork.addConnection(newPair);
            networkLookupMap.put(secondRelay, firstNetwork);
        }
        //Only second network exists
        else {
            secondNetwork.addConnection(newPair);
            networkLookupMap.put(firstRelay, secondNetwork);
        }
        //System.out.println("Connected "+firstRelay.toString()+" to "+secondRelay.toString());
        //System.out.println(firstNetwork == null ? secondNetwork.toString() : firstNetwork.toString());
        //System.out.println(laserRelayNetworks);
        WorldData.get(world).markDirty();
        return true;
    }

    @Override
    public void removeConnection(World world, BlockPos firstRelay, BlockPos secondRelay) {
        if (world != null && firstRelay != null && secondRelay != null) {
            INetwork network = this.getNetworkFor(firstRelay, world);

            if (network != null) {
                Pair<Set<INetwork>, Set<BlockPos>> newNetworks_orphanedNodes = network.removeConnection(firstRelay, secondRelay, world);
                if (newNetworks_orphanedNodes != null) {
                    handleOrphanedNodes(newNetworks_orphanedNodes.getRight());
                    handleNewNetworks(newNetworks_orphanedNodes.getLeft(), world);
                }
                
                WorldData.get(world).markDirty();
            }
        }
    }
    
    private void handleNewNetworks(Set<INetwork> newNetworks, World world) {
        WorldData data = WorldData.get(world);
        newNetworks.forEach(data::registerNetwork);
    }
    
    private void handleOrphanedNodes(Set<BlockPos> orphanedNodes) {
        orphanedNodes.forEach(networkLookupMap::remove);
    }

    
    @Override
    public LaserType getTypeFromLaser(TileEntity tile) {
        if (tile instanceof TileEntityLaserRelay) {
            return ((TileEntityLaserRelay) tile).type;
        } else {
            return null;
        }
    }

    @Override
    public LaserType getTypeFromLaser(BlockPos pos, World world) {
        return this.getTypeFromLaser(world.getTileEntity(pos));
    }
}