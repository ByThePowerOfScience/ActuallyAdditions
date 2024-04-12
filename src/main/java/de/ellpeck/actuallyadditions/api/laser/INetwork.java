package de.ellpeck.actuallyadditions.api.laser;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.Set;

public interface INetwork extends INBTSerializable<NBTTagCompound> {
	/**
	 * Forms a connection between two relays.
	 * @param pair An IConnectionPair holding the positions of the relays to connect.
	 */
	void addConnection(IConnectionPair pair);
	
	/**
	 *
	 * @param pair Object holding the positions of the relays to disconnect.
	 * @return True if connection was present, false otherwise.
	 */
	boolean removeConnection(IConnectionPair pair);
	
	Set<IConnectionPair> getAllConnections();
	
	Set<IConnectionPair> getConnectionsFor(BlockPos pos);
	
	int getChangeAmount();
	
	void incrementChangeAmount();
}
