package de.ellpeck.actuallyadditions.api.laser;

import net.minecraft.util.math.BlockPos;

import java.util.Set;

public interface INetwork {
	void addConnection(IConnectionPair pair);
	
	boolean removeConnection(IConnectionPair pair);
	
	Set<IConnectionPair> getAllConnections();
	
	Set<IConnectionPair> getConnectionsFor(BlockPos pos);
	
	int getChangeAmount();
	
	void incrementChangeAmount();
}
