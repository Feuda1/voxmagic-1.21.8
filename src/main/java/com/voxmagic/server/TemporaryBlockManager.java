package com.voxmagic.server;

import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public final class TemporaryBlockManager {
    private static final List<RestoreTask> TASKS = new ArrayList<>();

    private TemporaryBlockManager() {}

    public static void scheduleRestore(World world, Map<BlockPos, BlockState> previousStates, double lifetimeSec) {
        if (previousStates.isEmpty()) return;
        int ticks = Math.max(1, (int) Math.round(lifetimeSec * 20));
        Map<BlockPos, BlockState> copy = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : previousStates.entrySet()) {
            copy.put(entry.getKey().toImmutable(), entry.getValue());
        }
        TASKS.add(new RestoreTask(world.getRegistryKey(), copy, ticks));
    }

    public static void tick(MinecraftServer server) {
        Iterator<RestoreTask> it = TASKS.iterator();
        while (it.hasNext()) {
            RestoreTask task = it.next();
            task.ticks--;
            if (task.ticks <= 0) {
                ServerWorld world = server.getWorld(task.dimension);
                if (world != null) {
                    for (Map.Entry<BlockPos, BlockState> entry : task.states.entrySet()) {
                        world.setBlockState(entry.getKey(), entry.getValue(), 3);
                    }
                }
                it.remove();
            }
        }
    }

    private static final class RestoreTask {
        final RegistryKey<World> dimension;
        final Map<BlockPos, BlockState> states;
        int ticks;

        RestoreTask(RegistryKey<World> dimension, Map<BlockPos, BlockState> states, int ticks) {
            this.dimension = dimension;
            this.states = states;
            this.ticks = ticks;
        }
    }
}

