package de.ellpeck.actuallyadditions.api.laser;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.Set;

public interface INetwork extends INBTSerializable<NBTTagCompound> {
	void addConnection(IConnectionPair pair);
	
	boolean removeConnection(IConnectionPair pair);
	
	Set<IConnectionPair> getAllConnections();
	
	Set<IConnectionPair> getConnectionsFor(BlockPos pos);
	
	int getChangeAmount();
	
	void incrementChangeAmount();
}
