package de.srendi.advancedperipherals.common.util.fakeplayer;

import com.mojang.authlib.GameProfile;
import dan200.computercraft.api.turtle.ITurtleAccess;
import dan200.computercraft.shared.util.WorldUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.WeakHashMap;
import java.util.function.Function;

public final class FakePlayerProviderTurtle {

    /*
    Highly inspired by https://github.com/SquidDev-CC/plethora/blob/minecraft-1.12/src/main/java/org/squiddev/plethora/integration/computercraft/FakePlayerProviderTurtle.java
    */
    private static final WeakHashMap<ITurtleAccess, APFakePlayer> registeredPlayers = new WeakHashMap<>();

    private FakePlayerProviderTurtle() {
    }

    public static APFakePlayer getPlayer(ITurtleAccess turtle, GameProfile profile) {
        return registeredPlayers.computeIfAbsent(turtle, iTurtleAccess -> registeredPlayers.put(turtle, new APFakePlayer((ServerLevel) turtle.getLevel(), null, profile)));
    }

    public static void load(APFakePlayer player, ITurtleAccess turtle) {
        Direction direction = turtle.getDirection();
        player.setLevel((ServerLevel) turtle.getLevel());
        BlockPos position = turtle.getPosition();
        // Player position
        float pitch = direction == Direction.UP ? -90 : direction == Direction.DOWN ? 90 : 0;
        float yaw = direction == Direction.SOUTH ? 0 : direction == Direction.WEST ? 90 : direction == Direction.NORTH ? 180 : -90;
        Vec3i sideVec = direction.getNormal();
        Direction.Axis a = direction.getAxis();
        Direction.AxisDirection ad = direction.getAxisDirection();
        double x = a == Direction.Axis.X && ad == Direction.AxisDirection.NEGATIVE ? -.5 : .5 + sideVec.getX() / 1.9D;
        double y = 0.5 + sideVec.getY() / 1.9D;
        double z = a == Direction.Axis.Z && ad == Direction.AxisDirection.NEGATIVE ? -.5 : .5 + sideVec.getZ() / 1.9D;
        player.moveTo(position.getX() + x, position.getY() + y, position.getZ() + z, yaw, pitch);
        // Player inventory
        Inventory playerInventory = player.getInventory();
        playerInventory.selected = 0;

        // Copy primary items into player inventory and empty the rest
        IItemHandler turtleInventory = turtle.getItemHandler();
        int size = turtleInventory.getSlots();
        int largerSize = playerInventory.getContainerSize();
        playerInventory.selected = turtle.getSelectedSlot();
        for (int i = 0; i < size; i++) {
            playerInventory.setItem(i, turtleInventory.getStackInSlot(i));
        }
        for (int i = size; i < largerSize; i++) {
            playerInventory.setItem(i, ItemStack.EMPTY);
        }

        // Add properties
        ItemStack activeStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!activeStack.isEmpty())
            player.getAttributes().addTransientAttributeModifiers(activeStack.getAttributeModifiers(EquipmentSlot.MAINHAND));

    }

    public static void unload(APFakePlayer player, ITurtleAccess turtle) {
        Inventory playerInventory = player.getInventory();
        playerInventory.selected = 0;

        // Remove properties
        ItemStack activeStack = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (!activeStack.isEmpty()) {
            player.getAttributes().removeAttributeModifiers(activeStack.getAttributeModifiers(EquipmentSlot.MAINHAND));
        }

        // Copy primary items into turtle inventory and then insert/drop the rest
        IItemHandlerModifiable turtleInventory = turtle.getItemHandler();
        int size = turtleInventory.getSlots();
        int largerSize = playerInventory.getContainerSize();
        playerInventory.selected = turtle.getSelectedSlot();
        for (int i = 0; i < size; i++) {
            turtleInventory.setStackInSlot(i, playerInventory.getItem(i));
            playerInventory.setItem(i, ItemStack.EMPTY);
        }

        for (int i = size; i < largerSize; i++) {
            ItemStack remaining = playerInventory.getItem(i);
            if (!remaining.isEmpty()) {
                remaining = ItemHandlerHelper.insertItem(turtleInventory, remaining, false);
                if (!remaining.isEmpty()) {
                    BlockPos position = turtle.getPosition();
                    WorldUtil.dropItemStack(remaining, turtle.getLevel(), position, turtle.getDirection().getOpposite());
                }
            }

            playerInventory.setItem(i, ItemStack.EMPTY);
        }
    }

    public static <T> T withPlayer(ITurtleAccess turtle, Function<APFakePlayer, T> function) {
        APFakePlayer player = getPlayer(turtle, turtle.getOwningPlayer());
        load(player, turtle);
        T result = function.apply(player);
        unload(player, turtle);
        return result;
    }

}
