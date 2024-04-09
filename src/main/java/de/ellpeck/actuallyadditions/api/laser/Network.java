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

import io.netty.util.internal.ConcurrentSet;
import net.minecraft.util.math.BlockPos;

import java.util.Set;
import java.util.stream.Collectors;

public class Network implements INetwork {

    public final Set<IConnectionPair> connections = new ConcurrentSet<>();
    public int changeAmount;

    @Override
    public void addConnection(IConnectionPair pair) {
        this.connections.add(pair);
        this.incrementChangeAmount();
    }
    
    @Override
    public boolean removeConnection(IConnectionPair pair) {
        this.incrementChangeAmount();
        return this.connections.remove(pair);
    }
    
    @Override
    public Set<IConnectionPair> getAllConnections() {
        return this.connections;
    }
    
    @Override
    public Set<IConnectionPair> getConnectionsFor(BlockPos pos) {
        return connections.stream()
                          .filter(pair -> pair.contains(pos))
                          .collect(Collectors.toSet());
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
        
        return this.getAllConnections().equals(((Network) obj).getAllConnections());
    }
}
