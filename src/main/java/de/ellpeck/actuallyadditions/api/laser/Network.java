/*
 * This file ("Network.java") is part of the Actually Additions mod for Minecraft.
 * It is created and owned by Ellpeck and distributed
 * under the Actually Additions License to be found at
 * http://ellpeck.de/actaddlicense
 * View the source code at https://github.com/Ellpeck/ActuallyAdditions
 *
 * © 2015-2017 Ellpeck
 */

package de.ellpeck.actuallyadditions.api.laser;

import com.google.common.collect.ImmutableSet;
import de.ellpeck.actuallyadditions.api.ActuallyAdditionsAPI;
import de.ellpeck.actuallyadditions.mod.misc.apiimpl.ConnectionPair;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Collections;
import java.util.Set;

public class Network implements INetwork {

    public final Set<IConnectionPair> connections = Collections.synchronizedSet(new ObjectOpenHashSet<>());
    public int changeAmount;

    @Override
    public void addConnection(IConnectionPair pair) {
        this.connections.add(pair);
        this.incrementChangeAmount();
    }
    
    /**
     * @return Nothing because we don't do anything and instead force the connection handler to take care of it
     */
    @Override
    public Pair<Set<INetwork>, Set<BlockPos>> removeConnection(IConnectionPair toRemove, World world) {
        this.incrementChangeAmount();
        
        if (this.connections.remove(toRemove)) {
            shitTheBed(world);
        }
        
        return null;
    }
    
    /**
     * @return Nothing because we don't do anything and instead force the connection handler to take care of it
     */
    @Override
    public Pair<Set<INetwork>, Set<BlockPos>> removeRelay(BlockPos relayPos, World world) {
        this.incrementChangeAmount();
        
        this.connections.removeIf(pair -> pair.contains(relayPos));
        
        shitTheBed(world);
        
        return null;
    }
    
    /**
     * go insane and remove all of our connections, remove ourselves from the world, and freak out because we don't know where our own connections lead
     */
    private void shitTheBed(World world) {
        ActuallyAdditionsAPI.connectionHandler.removeNetwork(this, world);
        
        for (IConnectionPair pair : this.connections) {
            ActuallyAdditionsAPI.connectionHandler.addConnection(pair.getPositions()[0], pair.getPositions()[1], pair.getType(), world, pair.doesSuppressRender());
        }
    }
    
    public void absorbNetwork(INetwork other) {
        this.connections.addAll(other.getAllConnections());
    }
    
    
    @Override
    public Set<IConnectionPair> getAllConnections() {
        return this.connections;
    }
    
    @Override
    public Set<IConnectionPair> getConnectionsFor(BlockPos pos) {
        return connections.stream()
                          .filter(pair -> pair.contains(pos))
                          .collect(ImmutableSet.toImmutableSet());
    }
    
    @Override
    public boolean containsRelay(BlockPos pos) {
        for (IConnectionPair pair : this.connections) {
            if (pair.contains(pos)) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public int getChangeAmount() {
        return this.changeAmount;
    }
    
    @Override
    public void incrementChangeAmount() {
        this.changeAmount++;
    }
    
    @Override
    public String toString() {
        return this.connections.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null)
            return false;
        if (this == obj)
            return true;
        if (!(obj instanceof Network))
            return false;
        
        return this.getAllConnections().equals(((INetwork) obj).getAllConnections());
    }
    
    @Override
    public NBTTagCompound serializeNBT() {
        NBTTagList list = new NBTTagList();
        for (IConnectionPair pair : this.connections) {
            NBTTagCompound tag = new NBTTagCompound();
            pair.writeToNBT(tag);
            list.appendTag(tag);
        }
        NBTTagCompound compound = new NBTTagCompound();
        compound.setTag("Network", list);
        return compound;
    }
    
    @Override
    public void deserializeNBT(NBTTagCompound nbt) {
        NBTTagList list = nbt.getTagList("Network", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            ConnectionPair pair = new ConnectionPair();
            pair.readFromNBT(list.getCompoundTagAt(i));
            this.connections.add(pair);
        }
    }
}
