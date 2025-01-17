/*
 * This file ("ILaserRelayConnectionHandler.java") is part of the Actually Additions mod for Minecraft.
 * It is created and owned by Ellpeck and distributed
 * under the Actually Additions License to be found at
 * http://ellpeck.de/actaddlicense
 * View the source code at https://github.com/Ellpeck/ActuallyAdditions
 *
 * © 2015-2017 Ellpeck
 */

package de.ellpeck.actuallyadditions.api.laser;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nullable;
import java.util.Set;

/**
 * This is the internal laser relay connection handler.
 * Use ActuallyAdditionsAPI.connectionHandler for calling
 * This is not supposed to be implemented.
 * <p>
 * The network system is built in a way that doesn't need the individual
 * positions to be Laser Relays, it relies only on BlockPos
 */
public interface ILaserRelayConnectionHandler {

    Set<IConnectionPair> getConnectionsFor(BlockPos relay, World world);

    void removeRelayFromNetwork(BlockPos relay, World world);

    @Nullable
    INetwork getNetworkFor(BlockPos relay, World world);

    boolean addConnection(BlockPos firstRelay, BlockPos secondRelay, LaserType type, World world);

    boolean addConnection(BlockPos firstRelay, BlockPos secondRelay, LaserType type, World world, boolean suppressConnectionRender);

    boolean addConnection(BlockPos firstRelay, BlockPos secondRelay, LaserType type, World world, boolean suppressConnectionRender, boolean removeIfConnected);

    void removeConnection(World world, BlockPos firstRelay, BlockPos secondRelay);
    
    void removeNetwork(INetwork network, World world);

    LaserType getTypeFromLaser(TileEntity tile);

    LaserType getTypeFromLaser(BlockPos pos, World world);
}
