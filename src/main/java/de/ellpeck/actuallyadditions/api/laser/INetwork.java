package de.ellpeck.actuallyadditions.api.laser;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.util.INBTSerializable;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nullable;
import java.util.Set;

public interface INetwork extends INBTSerializable<NBTTagCompound> {
	/**
	 * Forms a connection between two relays.
	 * @param pair An IConnectionPair holding the positions of the relays to connect.
	 */
	void addConnection(IConnectionPair pair);
	
	
	/**
	 * Removes a connection between two positions.
	 * <p>Any nodes that are disconnected from this network after the change will be removed from this network.
	 * @param world A reference to the world. Likely unneeded.
	 * @return Pair of (or null if no change):
	 *      <p>Left: Set of new networks that have been created by this change, if any.
	 *      <p>Right: Set of relays that are no longer in ANY network due to this change.
	 * @implNote Relays that have been isolated should NOT have a new Network created for them.
	 *           <p>Implementers should also NOT register any networks created by this method.
	 */
	@Nullable
	Pair<Set<INetwork>, Set<BlockPos>> removeConnection(BlockPos first, BlockPos second, World world);
	
	/**
	 * Removes a connection between two positions.
	 * <p>Any nodes that are disconnected from this network after the change will be removed from this network.
	 * @param pair Object holding the positions of the relays to disconnect.
	 * @param world A reference to the world. Likely unneeded.
	 * @return Pair of (or null if no change):
	 *      <p>Left: Set of new networks that have been created by this change, if any.
	 *      <p>Right: Set of relays that are no longer in ANY network due to this change.
	 * @implNote Relays that have been isolated should NOT have a new Network created for them.
	 *           <p>Implementers should also NOT register any networks created by this method.
	 */
	@Nullable
	Pair<Set<INetwork>, Set<BlockPos>> removeConnection(IConnectionPair pair, World world);
	
	@Nullable
	Pair<Set<INetwork>, Set<BlockPos>> removeRelay(BlockPos pos, World world);
	
	/**
	 * Get a read-only view of connections in this network as a set of connection pairs.
	 */
	Set<IConnectionPair> getAllConnections();
	
	Set<IConnectionPair> getConnectionsFor(BlockPos pos);
	
	Set<BlockPos> getMembers();
	
	/**
	 * Absorb another network into this.
	 */
	void absorbNetwork(INetwork other);
	
	boolean containsRelay(BlockPos pos);
	
	int getChangeAmount();
	
	void incrementChangeAmount();
}
