package de.srendi.advancedperipherals.common.addons.curios;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import de.srendi.advancedperipherals.common.addons.computercraft.owner.IPeripheralOwner;
import de.srendi.advancedperipherals.common.configuration.APConfig;
import de.srendi.advancedperipherals.common.util.ItemUtil;
import de.srendi.advancedperipherals.common.util.LuaConverter;
import de.srendi.advancedperipherals.lib.peripherals.IPeripheralPlugin;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.CuriosCapability;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class InventoryManagerCurioPlugin implements IPeripheralPlugin {

    private final IPeripheralOwner owner;

    public InventoryManagerCurioPlugin(IPeripheralOwner owner) {
        this.owner = owner;
    }

    private Player getOwnerPlayer() throws LuaException {
        if (owner.getOwner() == null) {
            throw new LuaException("The Inventory Manager doesn't have a memory card or it isn't bound to a player.");
        }
        if (owner.getOwner().position().distanceTo(new Vec3(owner.getPos().getX(), owner.getPos().getY(), owner.getPos().getZ())) > APConfig.PERIPHERALS_CONFIG.inventoryManagerRange.get()) {
            throw new LuaException("That player is out of range of the Inventory Manager. (" + APConfig.PERIPHERALS_CONFIG.inventoryManagerRange.get() + " blocks)");
        }
        return owner.getOwner();
    }

    protected Direction validateSide(String direction) throws LuaException {
        String dir = direction.toUpperCase(Locale.ROOT);

        return LuaConverter.getDirection(owner.getFacing(), dir);
    }

    @LuaFunction(mainThread = true)
    public final List<Object> getCurios() throws LuaException {
        List<Object> items = new ArrayList<>();
        AtomicInteger curioSlotCount = new AtomicInteger(105);
        getOwnerPlayer().getCapability(CuriosCapability.INVENTORY).ifPresent(handler -> {
            for (String key : handler.getCurios().keySet()) { //For every slot type
                ICurioStacksHandler stacksHandler = handler.getCurios().get(key);
                for (int i = 0; i < stacksHandler.getStacks().getSlots(); i++) { //For every slot of this type
                    if (!stacksHandler.getStacks().getStackInSlot(i).isEmpty()) {
                        items.add(LuaConverter.stackToObjectWithSlot(stacksHandler.getStacks().getStackInSlot(i), curioSlotCount.get()));
                    }
                    curioSlotCount.getAndIncrement();
                }
            }
        });

        return items;
    }

    @LuaFunction(mainThread = true)
    public final boolean removeCurioFromPlayer(String invDirection, int slot, Optional<String> item) throws LuaException {
        Direction direction = validateSide(invDirection);
        BlockEntity targetEntity = Objects.requireNonNull(owner.getLevel()).getBlockEntity(owner.getPos().relative(direction));
        IItemHandler inventoryTo = targetEntity != null ? targetEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).resolve().orElse(null) : null;

        if (inventoryTo == null) return false;

        LazyOptional<ICuriosItemHandler> itemHandlerOpt = getOwnerPlayer().getCapability(CuriosCapability.INVENTORY);
        if (itemHandlerOpt.isPresent()) {
            ICuriosItemHandler itemHandler = itemHandlerOpt.orElseThrow(RuntimeException::new); //This should never throw an exception because of the previous line
            for (int i = 0; i < inventoryTo.getSlots(); i++) {
                //Make sure there is an empty slot in the inventory manager container
                if (inventoryTo.getStackInSlot(i).isEmpty()) {

                    int curioSlotIndex = 105;
                    for (String key : itemHandler.getCurios().keySet()) { //For every slot type
                        ICurioStacksHandler stacksHandler = itemHandler.getCurios().get(key);
                        for (int curioSlotInHandler = 0; curioSlotInHandler < stacksHandler.getSlots(); curioSlotInHandler++) { //for every slot
                            if (slot == curioSlotIndex) {
                                if (!stacksHandler.getStacks().getStackInSlot(curioSlotInHandler).isEmpty()) {
                                    ItemStack curioStack = stacksHandler.getStacks().getStackInSlot(curioSlotInHandler);
                                    if (item.isPresent()) {
                                        if (Objects.requireNonNull(ItemUtil.getRegistryEntry(item.get(), ForgeRegistries.ITEMS)).asItem() == curioStack.getItem()) {
                                            //Transfer the items
                                            stacksHandler.getStacks().setStackInSlot(curioSlotInHandler, ItemStack.EMPTY);
                                            inventoryTo.insertItem(i, curioStack, false);
                                            return true;
                                        }
                                    } else {
                                        //Transfer the items
                                        stacksHandler.getStacks().setStackInSlot(curioSlotInHandler, ItemStack.EMPTY);
                                        inventoryTo.insertItem(i, curioStack, false);
                                        return true;
                                    }

                                }
                            }
                            curioSlotIndex++;
                        }
                    }
                }
            }
        }

        return false;
    }

    //Function to transfer a curio from inventory manager chest to a curio slot
    @LuaFunction(mainThread = true)
    public final boolean addCurioToPlayer(String invDirection, int slot, Optional<String> item) throws LuaException {
        ItemStack stack = ItemStack.EMPTY;
        if (item.isPresent()) {
            Item item1 = ItemUtil.getRegistryEntry(item.get(), ForgeRegistries.ITEMS);
            stack = new ItemStack(item1, 1);
        }

        Direction direction = validateSide(invDirection);
        BlockEntity targetEntity = Objects.requireNonNull(owner.getLevel()).getBlockEntity(owner.getPos().relative(direction));
        IItemHandler inventoryFrom = targetEntity != null ? targetEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, direction).resolve().orElse(null) : null;

        if (inventoryFrom == null) return false;

        TagKey<Item> curioKey = ItemTags.create(new ResourceLocation(CuriosApi.MODID, "curio")); //Generic curio

        LazyOptional<ICuriosItemHandler> itemHandlerOpt = getOwnerPlayer().getCapability(CuriosCapability.INVENTORY);
        if (itemHandlerOpt.isPresent()) {
            ICuriosItemHandler itemHandler = itemHandlerOpt.orElseThrow(RuntimeException::new); //This should never throw an exception because of the previous line
            for (int i = 0; i < inventoryFrom.getSlots(); i++) {
                if (stack.isEmpty()) { //Find the next item in this inventory manager container
                    stack = inventoryFrom.getStackInSlot(i).copy();
                    if (stack.isEmpty()) continue;
                }

                //If this item can go into the slot
                if (ItemHandlerHelper.canItemStacksStack(stack, inventoryFrom.getStackInSlot(i))) {
                    int curioSlotIndex = 105;
                    for (String key : itemHandler.getCurios().keySet()) { //For every slot type
                        ICurioStacksHandler stacksHandler = itemHandler.getCurios().get(key);
                        for (int curioSlotInHandler = 0; curioSlotInHandler < stacksHandler.getSlots(); curioSlotInHandler++) { //for every slot
                            if (slot == curioSlotIndex) {
                                TagKey<Item> slotKey = ItemTags.create(new ResourceLocation(CuriosApi.MODID, key)); //Specific slot

                                if (stack.is(slotKey) || stack.is(curioKey)) { //If this stack has a tag which allows it to be in this curio slot
                                    if (stacksHandler.getStacks().getStackInSlot(curioSlotInHandler).isEmpty()) {
                                        //Transfer the items
                                        stacksHandler.getStacks().setStackInSlot(curioSlotInHandler, stack);
                                        inventoryFrom.extractItem(i, stack.getCount(), false);
                                        return true;
                                    }
                                }
                            }
                            curioSlotIndex++;
                        }
                    }
                }
            }
        }

        return false;
    }
}
