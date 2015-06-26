package ellpeck.actuallyadditions.items;

import cofh.api.energy.ItemEnergyContainer;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import ellpeck.actuallyadditions.ActuallyAdditions;
import ellpeck.actuallyadditions.config.values.ConfigFloatValues;
import ellpeck.actuallyadditions.config.values.ConfigIntValues;
import ellpeck.actuallyadditions.inventory.GuiHandler;
import ellpeck.actuallyadditions.items.tools.ItemAllToolAA;
import ellpeck.actuallyadditions.util.INameableItem;
import ellpeck.actuallyadditions.util.ItemUtil;
import ellpeck.actuallyadditions.util.KeyUtil;
import ellpeck.actuallyadditions.util.ModUtil;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.world.World;
import org.apache.logging.log4j.Level;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unchecked")
public class ItemDrill extends ItemEnergyContainer implements INameableItem{

    private static final Set allSet = Sets.newHashSet();
    static{
        allSet.addAll(ItemAllToolAA.pickSet);
        allSet.addAll(ItemAllToolAA.shovelSet);
    }

    public ItemDrill(){
        super(500000, 5000);
        this.setMaxStackSize(1);
        this.setHasSubtypes(true);
    }

    public static float defaultEfficiency = ConfigFloatValues.DRILL_DAMAGE.getValue();
    public static int energyUsePerBlockOrHit = ConfigIntValues.DRILL_ENERGY_USE.getValue();

    @Override
    public double getDurabilityForDisplay(ItemStack stack){
        double energyDif = getMaxEnergyStored(stack)-getEnergyStored(stack);
        double maxAmount = getMaxEnergyStored(stack);
        return energyDif/maxAmount;
    }

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int hitSide, float hitX, float hitY, float hitZ){
        ItemStack upgrade = this.getHasUpgradeAsStack(stack, ItemDrillUpgrade.UpgradeType.PLACER);
        if(upgrade != null){
            int slot = ItemDrillUpgrade.getSlotToPlaceFrom(upgrade);
            if(slot >= 0 && slot < InventoryPlayer.getHotbarSize()){
                ItemStack anEquip =player.inventory.getStackInSlot(slot);
                if(anEquip != null && anEquip != stack){
                    ItemStack equip = anEquip.copy();
                    if(!world.isRemote){
                        try{
                            if(equip.tryPlaceItemIntoWorld(player, world, x, y, z, hitSide, hitX, hitY, hitZ)){
                                if(!player.capabilities.isCreativeMode) player.inventory.setInventorySlotContents(slot, equip.stackSize <= 0 ? null : equip.copy());
                                player.inventoryContainer.detectAndSendChanges();
                                return true;
                            }
                        }
                        catch(Exception e){
                            player.addChatComponentMessage(new ChatComponentText("Ouch! That really hurt! You must have done something wrong, don't do that again please!"));
                            ModUtil.LOGGER.log(Level.ERROR, "Player "+player.getDisplayName()+" who should place a Block using a Drill at "+player.posX+", "+player.posY+", "+player.posZ+" in World "+world.provider.dimensionId+" threw an Exception! Don't let that happen again!");
                        }
                    }
                    else return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean showDurabilityBar(ItemStack itemStack){
        return true;
    }

    @Override
    public void onCreated(ItemStack stack, World world, EntityPlayer player){
        this.setEnergy(stack, 0);
    }

    public float getEfficiencyFromUpgrade(ItemStack stack){
        float efficiency = defaultEfficiency;
        if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.SPEED)){
            if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.SPEED_II)){
                if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.SPEED_III)) efficiency += 37.0F;
                else efficiency += 28.0F;
            }
            else efficiency += 15.0F;
        }
        return efficiency;
    }

    public int getEnergyUsePerBlock(ItemStack stack){
        int use = energyUsePerBlockOrHit;

        if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.SPEED)){
            use += ConfigIntValues.DRILL_SPEED_EXTRA_USE.getValue();
            if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.SPEED_II)){
                use += ConfigIntValues.DRILL_SPEED_II_EXTRA_USE.getValue();
                if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.SPEED_III)) use += ConfigIntValues.DRILL_SPEED_III_EXTRA_USE.getValue();
            }
        }

        if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.SILK_TOUCH)) use += ConfigIntValues.DRILL_SILK_EXTRA_USE.getValue();

        if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.FORTUNE)){
            use += ConfigIntValues.DRILL_FORTUNE_EXTRA_USE.getValue();
            if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.FORTUNE_II)) use += ConfigIntValues.DRILL_FORTUNE_II_EXTRA_USE.getValue();
        }

        if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.THREE_BY_THREE)){
            use += ConfigIntValues.DRILL_THREE_BY_THREE_EXTRA_USE.getValue();
            if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.FIVE_BY_FIVE)) use += ConfigIntValues.DRILL_FIVE_BY_FIVE_EXTRA_USE.getValue();
        }

        return use;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean hasEffect(ItemStack stack, int pass){
        return false;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack){
        return EnumRarity.epic;
    }

    public boolean getHasUpgrade(ItemStack stack, ItemDrillUpgrade.UpgradeType upgrade){
        return this.getHasUpgradeAsStack(stack, upgrade) != null;
    }

    public ItemStack getHasUpgradeAsStack(ItemStack stack, ItemDrillUpgrade.UpgradeType upgrade){
        NBTTagCompound compound = stack.getTagCompound();
        if(compound == null) return null;

        ItemStack[] slots = this.getSlotsFromNBT(stack);
        if(slots != null && slots.length > 0){
            for(ItemStack slotStack : slots){
                if(slotStack != null && slotStack.getItem() instanceof ItemDrillUpgrade){
                    if(((ItemDrillUpgrade)slotStack.getItem()).type == upgrade) return slotStack;
                }
            }
        }
        return null;
    }

    @Override
    public IIcon getIcon(ItemStack stack, int pass){
        return this.itemIcon;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister iconReg){
        this.itemIcon = iconReg.registerIcon(ModUtil.MOD_ID_LOWER + ":" + this.getName());
    }

    public void setEnergy(ItemStack stack, int energy){
        NBTTagCompound compound = stack.getTagCompound();
        if(compound == null) compound = new NBTTagCompound();
        compound.setInteger("Energy", energy);
        stack.setTagCompound(compound);
    }

    public void writeSlotsToNBT(ItemStack[] slots, ItemStack stack){
        NBTTagCompound compound = stack.getTagCompound();
        if(compound == null) compound = new NBTTagCompound();

        if(slots != null && slots.length > 0){
            compound.setInteger("SlotAmount", slots.length);
            NBTTagList tagList = new NBTTagList();
            for(int currentIndex = 0; currentIndex < slots.length; currentIndex++){
                if(slots[currentIndex] != null){
                    NBTTagCompound tagCompound = new NBTTagCompound();
                    tagCompound.setByte("Slot", (byte)currentIndex);
                    slots[currentIndex].writeToNBT(tagCompound);
                    tagList.appendTag(tagCompound);
                }
            }
            compound.setTag("Items", tagList);
        }
        stack.setTagCompound(compound);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tabs, List list){
        ItemStack stackFull = new ItemStack(this);
        this.setEnergy(stackFull, this.getMaxEnergyStored(stackFull));
        list.add(stackFull);

        ItemStack stackEmpty = new ItemStack(this);
        this.setEnergy(stackEmpty, 0);
        list.add(stackEmpty);
    }

    public ItemStack[] getSlotsFromNBT(ItemStack stack){
        NBTTagCompound compound = stack.getTagCompound();
        if(compound == null) return null;

        int slotAmount = compound.getInteger("SlotAmount");
        ItemStack[] slots = new ItemStack[slotAmount];

        if(slots.length > 0){
            NBTTagList tagList = compound.getTagList("Items", 10);
            for(int i = 0; i < tagList.tagCount(); i++){
                NBTTagCompound tagCompound = tagList.getCompoundTagAt(i);
                byte slotIndex = tagCompound.getByte("Slot");
                if(slotIndex >= 0 && slotIndex < slots.length){
                    slots[slotIndex] = ItemStack.loadItemStackFromNBT(tagCompound);
                }
            }
        }
        return slots;
    }

    public void breakBlocks(ItemStack stack, int radius, World world, int x, int y, int z, EntityPlayer player){
        int xRange = radius;
        int yRange = radius;
        int zRange = 0;

        MovingObjectPosition pos = this.getMovingObjectPositionFromPlayer(world, player, false);
        if(pos != null){
            int side = pos.sideHit;
            if(side == 0 || side == 1){
                zRange = radius;
                yRange = 0;
            }
            if(side == 4 || side == 5){
                xRange = 0;
                zRange = radius;
            }

            for(int xPos = x-xRange; xPos <= x+xRange; xPos++){
                for(int yPos = y-yRange; yPos <= y+yRange; yPos++){
                    for(int zPos = z-zRange; zPos <= z+zRange; zPos++){
                        int use = this.getEnergyUsePerBlock(stack);
                        if(this.getEnergyStored(stack) >= use){
                            Block block = world.getBlock(xPos, yPos, zPos);
                            float hardness = block.getBlockHardness(world, xPos, yPos, zPos);
                            if(hardness > -1.0F && this.canHarvestBlock(block, stack)){
                                this.extractEnergy(stack, use, false);

                                ArrayList<ItemStack> drops = new ArrayList<ItemStack>();
                                int meta = world.getBlockMetadata(xPos, yPos, zPos);

                                if(block.canSilkHarvest(world, player, xPos, yPos, zPos, meta) && this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.SILK_TOUCH)){
                                    drops.add(new ItemStack(block, 1, meta));
                                }
                                else{
                                    int fortune = this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.FORTUNE) ? (this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.FORTUNE_II) ? 3 : 1) : 0;
                                    drops.addAll(block.getDrops(world, xPos, yPos, zPos, meta, fortune));
                                    block.dropXpOnBlockBreak(world, x, y, z, block.getExpDrop(world, meta, fortune));
                                }

                                if(!(x == xPos && y == yPos && z == zPos)){
                                    world.playAuxSFX(2001, xPos, yPos, zPos, Block.getIdFromBlock(block)+(meta << 12));
                                }
                                world.setBlockToAir(xPos, yPos, zPos);
                                for(ItemStack theDrop : drops){
                                    EntityItem item = new EntityItem(world, xPos+0.5, yPos+0.5, zPos+0.5, theDrop);
                                    item.delayBeforeCanPickup = 10;
                                    world.spawnEntityInWorld(item);
                                }
                            }
                        }
                        else return;
                    }
                }
            }
        }
    }

    @Override
    public String getName(){
        return "itemDrill";
    }

    @Override
    public String getOredictName(){
        return this.getName();
    }

    @Override
    public boolean onBlockDestroyed(ItemStack stack, World world, Block block, int x, int y, int z, EntityLivingBase living){
        if(living instanceof EntityPlayer){
            EntityPlayer player = (EntityPlayer)living;
            int use = this.getEnergyUsePerBlock(stack);
            if(this.getEnergyStored(stack) >= use){
                if(!world.isRemote){
                    if(!living.isSneaking() && this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.THREE_BY_THREE)){
                        if(this.getHasUpgrade(stack, ItemDrillUpgrade.UpgradeType.FIVE_BY_FIVE)){
                            this.breakBlocks(stack, 30, world, x, y, z, player);
                        }
                        else this.breakBlocks(stack, 1, world, x, y, z, player);
                    }
                    else this.breakBlocks(stack, 0, world, x, y, z, player);
                }
            }
        }
        return true;
    }

    @Override
    public float func_150893_a(ItemStack stack, Block block){
        if(this.getEnergyStored(stack) < this.getEnergyUsePerBlock(stack)) return 0.0F;
        if(block.getMaterial() == Material.iron || block.getMaterial() == Material.anvil || block.getMaterial() == Material.rock || allSet.contains(block)) return this.getEfficiencyFromUpgrade(stack);
        else return super.func_150893_a(stack, block);
    }

    @Override
    public boolean hitEntity(ItemStack stack, EntityLivingBase entity1, EntityLivingBase entity2){
        int use = this.getEnergyUsePerBlock(stack);
        if(this.getEnergyStored(stack) >= use){
            this.extractEnergy(stack, use, false);
        }
        return true;
    }

    @Override
    public boolean canHarvestBlock(Block block, ItemStack stack){
        return this.func_150893_a(stack, block) > super.func_150893_a(stack, block);
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player){
        if(!world.isRemote && player.isSneaking() && stack == player.getCurrentEquippedItem()){
            player.openGui(ActuallyAdditions.instance, GuiHandler.DRILL_ID, world, (int)player.posX, (int)player.posY, (int)player.posZ);
        }
        return stack;
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass){
        return ToolMaterial.EMERALD.getHarvestLevel();
    }

    @Override
    public Set<String> getToolClasses(ItemStack stack){
        HashSet<String> hashSet = new HashSet<String>();
        hashSet.add("pickaxe");
        hashSet.add("shovel");
        return hashSet;
    }

    @Override
    public float getDigSpeed(ItemStack stack, Block block, int meta){
        return this.func_150893_a(stack, block);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean isHeld){
        ItemUtil.addInformation(this, list, 3, "");
        if(KeyUtil.isShiftPressed()){
            list.add(this.getEnergyStored(stack) + "/" + this.getMaxEnergyStored(stack) + " RF");
        }
    }

    @Override
    public Multimap getAttributeModifiers(ItemStack stack){
        Multimap map = super.getAttributeModifiers(stack);
        map.put(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(), new AttributeModifier(field_111210_e, "Tool modifier", this.getEnergyStored(stack) >= energyUsePerBlockOrHit ? 8.0F : 0.0F, 0));
        return map;
    }

    @Override
    public boolean getShareTag(){
        return true;
    }
}