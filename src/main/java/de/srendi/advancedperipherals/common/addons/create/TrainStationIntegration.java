package de.srendi.advancedperipherals.common.addons.create;

import com.simibubi.create.content.logistics.trains.entity.Train;
import com.simibubi.create.content.logistics.trains.management.edgePoint.station.StationEditPacket;
import com.simibubi.create.content.logistics.trains.management.edgePoint.station.StationTileEntity;
import com.simibubi.create.content.logistics.trains.management.edgePoint.station.TrainEditPacket;
import com.simibubi.create.foundation.networking.AllPackets;
import dan200.computercraft.api.lua.LuaFunction;
import de.srendi.advancedperipherals.lib.peripherals.BlockEntityIntegrationPeripheral;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class TrainStationIntegration extends BlockEntityIntegrationPeripheral<StationTileEntity> {

    public TrainStationIntegration(BlockEntity entity) {
        super(entity);
    }

    @NotNull
    @Override
    public String getType() {
        return "trainStation";
    }

    @LuaFunction(mainThread = true)
    public final boolean assemble() {
        if (!isTrainPresent()) {
            return false;
        }
        if (blockEntity.getStation().assembling) {
            AllPackets.channel.sendToServer(StationEditPacket.tryAssemble(blockEntity.getBlockPos()));
            return true;
        }
        return false;
    }

    @LuaFunction(mainThread = true)
    public final boolean disassemble() {
        if (!isTrainPresent()) {
            return false;
        }
        if (blockEntity.getStation().getPresentTrain().canDisassemble()) {
            AllPackets.channel.sendToServer(StationEditPacket.configure(blockEntity.getBlockPos(), true, blockEntity.getStation().name));
            return true;
        }
        return false;
    }

    @LuaFunction(mainThread = true)
    public final String getStationName() {
        return blockEntity.getStation().name;
    }

    @LuaFunction(mainThread = true)
    public final void setStationName(@NotNull String name) {
        if(blockEntity.getStation().assembling) {
            AllPackets.channel.sendToServer(StationEditPacket.configure(blockEntity.getBlockPos(), true, name));
        } else {
            AllPackets.channel.sendToServer(StationEditPacket.configure(blockEntity.getBlockPos(), false, name));
        }
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getTrainName() {
        Map<String, Object> data = new HashMap<>();
        if (!isTrainPresent()) {
            data.put("success", false);
        } else {
            data.put("success", true);
            data.put("name", blockEntity.getStation().getPresentTrain().name.getString());
        }
        return data;
    }

    @LuaFunction(mainThread = true)
    public final boolean setTrainName(@NotNull String name) {
        if (!isTrainPresent()) {
            return false;
        }
        if (!name.isBlank()) {
            Train train = blockEntity.getStation().getPresentTrain();
            AllPackets.channel.sendToServer(new TrainEditPacket(train.id, name, train.icon.getId()));
            return true;
        }
        return false;
    }

    @LuaFunction(mainThread = true)
    public final int getCarriageCount() {
        if (!isTrainPresent()) {
            return 0;
        }
        return blockEntity.getStation().getPresentTrain().carriages.size();
    }

    @LuaFunction(mainThread = true)
    public final boolean isTrainPresent() {
        return blockEntity.getStation().getPresentTrain() != null;
    }
}
