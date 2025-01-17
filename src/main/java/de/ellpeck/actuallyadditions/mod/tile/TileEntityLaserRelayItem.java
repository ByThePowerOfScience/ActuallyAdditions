/*
 * This file ("TileEntityLaserRelayItem.java") is part of the Actually Additions mod for Minecraft.
 * It is created and owned by Ellpeck and distributed
 * under the Actually Additions License to be found at
 * http://ellpeck.de/actaddlicense
 * View the source code at https://github.com/Ellpeck/ActuallyAdditions
 *
 * © 2015-2017 Ellpeck
 */

package de.ellpeck.actuallyadditions.mod.tile;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.ellpeck.actuallyadditions.api.laser.INetwork;
import org.cyclops.commoncapabilities.capability.itemhandler.SlotlessItemHandlerConfig;

import de.ellpeck.actuallyadditions.api.laser.IConnectionPair;
import de.ellpeck.actuallyadditions.api.laser.LaserType;
import de.ellpeck.actuallyadditions.api.laser.Network;
import de.ellpeck.actuallyadditions.mod.ActuallyAdditions;
import de.ellpeck.actuallyadditions.mod.tile.TileEntityItemViewer.GenericItemHandlerInfo;
import de.ellpeck.actuallyadditions.mod.util.StringUtil;
import de.ellpeck.actuallyadditions.mod.util.WorldUtil;
import de.ellpeck.actuallyadditions.mod.util.compat.SlotlessableItemHandlerWrapper;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextFormatting;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

public class TileEntityLaserRelayItem extends TileEntityLaserRelay {

    public final Map<BlockPos, SlotlessableItemHandlerWrapper> handlersAround = new ConcurrentHashMap<>();
    public int priority;

    public TileEntityLaserRelayItem(String name) {
        super(name, LaserType.ITEM);
    }

    public TileEntityLaserRelayItem() {
        this("laserRelayItem");
    }

    public int getPriority() {
        return this.priority;
    }

    public boolean isWhitelisted(ItemStack stack, boolean output) {
        return true;
    }

    @Override
    public boolean shouldSaveDataOnChangeOrWorldStart() {
        return true;
    }

    @Override
    public void saveDataOnChangeOrWorldStart() {
        Map<BlockPos, SlotlessableItemHandlerWrapper> old = new HashMap<>(this.handlersAround);
        boolean change = false;

        this.handlersAround.clear();
        for (int i = 0; i <= 5; i++) {
            EnumFacing side = WorldUtil.getDirectionBySidesInOrder(i);
            BlockPos pos = this.getPos().offset(side);
            if (this.world.isBlockLoaded(pos)) {
                TileEntity tile = this.world.getTileEntity(pos);
                if (tile != null && !(tile instanceof TileEntityItemViewer) && !(tile instanceof TileEntityLaserRelay)) {
                    IItemHandler itemHandler = null;
                    if (tile.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite())) {
                        itemHandler = tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side.getOpposite());
                    }

                    Object slotlessHandler = null;
                    if (ActuallyAdditions.commonCapsLoaded) {
                        if (tile.hasCapability(SlotlessItemHandlerConfig.CAPABILITY, side.getOpposite())) {
                            slotlessHandler = tile.getCapability(SlotlessItemHandlerConfig.CAPABILITY, side.getOpposite());
                        }
                    }

                    if (itemHandler != null || slotlessHandler != null) {
                        SlotlessableItemHandlerWrapper handler = new SlotlessableItemHandlerWrapper(itemHandler, slotlessHandler);
                        this.handlersAround.put(pos, handler);

                        SlotlessableItemHandlerWrapper oldHandler = old.get(pos);
                        if (oldHandler == null || !handler.equals(oldHandler)) {
                            change = true;
                        }
                    }
                }
            }
        }

        if (change || old.size() != this.handlersAround.size()) {
            INetwork network = this.getNetwork();
            if (network != null) {
                network.incrementChangeAmount();
            }
        }
    }

    public void getItemHandlersInNetwork(INetwork network, List<GenericItemHandlerInfo> storeList) {
        //Keeps track of all the Laser Relays and Item Handlers that have been checked already to make nothing run multiple times
        Set<BlockPos> alreadyChecked = new HashSet<>();

        for (IConnectionPair pair : network.getAllConnections()) {
            for (BlockPos relay : pair.getPositions()) {
                if (relay != null && this.world.isBlockLoaded(relay) && !alreadyChecked.contains(relay)) {
                    alreadyChecked.add(relay);
                    TileEntity aRelayTile = this.world.getTileEntity(relay);
                    if (aRelayTile instanceof TileEntityLaserRelayItem) {
                        TileEntityLaserRelayItem relayTile = (TileEntityLaserRelayItem) aRelayTile;
                        GenericItemHandlerInfo info = new GenericItemHandlerInfo(relayTile);

                        for (Map.Entry<BlockPos, SlotlessableItemHandlerWrapper> handler : relayTile.handlersAround.entrySet()) {
                            if (!alreadyChecked.contains(handler.getKey())) {
                                alreadyChecked.add(handler.getKey());

                                info.handlers.add(handler.getValue());
                            }
                        }

                        storeList.add(info);
                    }
                }
            }
        }
    }

    @Override
    public void writeSyncableNBT(NBTTagCompound compound, NBTType type) {
        super.writeSyncableNBT(compound, type);
        if (type != NBTType.SAVE_BLOCK) {
            compound.setInteger("Priority", this.priority);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public String getExtraDisplayString() {
        return StringUtil.localize("info." + ActuallyAdditions.MODID + ".laserRelay.item.extra") + ": " + TextFormatting.DARK_RED + this.getPriority() + TextFormatting.RESET;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public String getCompassDisplayString() {
        return TextFormatting.GREEN + StringUtil.localize("info." + ActuallyAdditions.MODID + ".laserRelay.item.display.1") + "\n" + StringUtil.localize("info." + ActuallyAdditions.MODID + ".laserRelay.item.display.2");
    }

    @Override
    public void onCompassAction(EntityPlayer player) {
        if (player.isSneaking()) {
            this.priority--;
        } else {
            this.priority++;
        }
    }

    @Override
    public void readSyncableNBT(NBTTagCompound compound, NBTType type) {
        super.readSyncableNBT(compound, type);
        if (type != NBTType.SAVE_BLOCK) {
            this.priority = compound.getInteger("Priority");
        }
    }
}
