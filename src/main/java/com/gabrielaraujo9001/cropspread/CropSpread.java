package com.gabrielaraujo9001.cropspread;

import com.gabrielaraujo9001.cropspread.config.SavedConfig;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

public class CropSpread implements ModInitializer {

    public static String MOD_ID = "cropspread";
    private int killSwitch = 100;

    @Override
    public void onInitialize() {
        // Adds an EVENT for when the player plants a crop seed
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClientSide()) {
                return InteractionResult.PASS;
            }

            MinecraftServer server = world.getServer();
            if (server == null) {
                return InteractionResult.PASS;
            }

            // Retrieve the saved block data from the server.
            SavedConfig savedConfig = SavedConfig.getSavedConfig(server);
            boolean isModEnabled = savedConfig.getIsModEnabled();

            if (!isModEnabled) {
                return InteractionResult.PASS;
            }

            // Check if the player clicked a Farmland block
            var targetBlock = hitResult.getBlockPos();
            if (world.getBlockState(targetBlock).is(Blocks.FARMLAND)) {
                System.out.println("Clicked on a Farmland block.");

                ItemStack stack = player.getItemInHand(hand);

                boolean isVanillaSeed = stack.is(Items.WHEAT_SEEDS)
                        || stack.is(Items.BEETROOT_SEEDS)
                        || stack.is(Items.POTATO)
                        || stack.is(Items.CARROT);

                if (isVanillaSeed) {
                    System.out.println("Planted a vanilla seed.");
                }

                if (isVanillaSeed && stack.getCount() - 1 > 0) {
                    spreadCropSeeds(player, world, hand, targetBlock);
                }
            }

            return InteractionResult.PASS;
        });

        // Registers a command for the mod
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(Commands.literal("cropspread")
                .then(Commands.argument("value", BoolArgumentType.bool())
                .executes(CropSpread::toggleModBehavior)));
        });
    }

    private static int toggleModBehavior(CommandContext<CommandSourceStack> context) {
        boolean value = BoolArgumentType.getBool(context, "value");

        MinecraftServer server = context.getSource().getLevel().getServer();
        SavedConfig savedConfig = SavedConfig.getSavedConfig(server);

        savedConfig.setModEnabled(value);
        context.getSource().sendSuccess(() -> Component.literal("Set /cropspread to %s".formatted(value)), true);

        return 1;
    }

    private void spreadCropSeeds(Player player, Level world, InteractionHand hand, BlockPos blockPos) {
        int count = 0;
        ItemStack stack = player.getItemInHand(hand);
        BlockItem cropItem = (BlockItem) stack.getItem();
        BlockState cropState = cropItem.getBlock().defaultBlockState();

        Queue<BlockPos> farmlandList = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        farmlandList.offer(blockPos);
        visited.add(blockPos);

        while (!farmlandList.isEmpty()) {
            int numberOfSeedsInHand = stack.getCount();

            if (count > killSwitch) {
                System.out.println("Breaking the loop because count is greater than" + killSwitch + ".");
                break;
            }

            if (numberOfSeedsInHand == 0) {
                System.out.println("No more seeds available.");
                break;
            }

            BlockPos currentFarmlandBlock = farmlandList.poll();
            assert currentFarmlandBlock != null;
            BlockPos plantPos = currentFarmlandBlock.above();
            BlockState plantState = world.getBlockState(plantPos);
            boolean hasPlant = plantState.getBlock() instanceof CropBlock || !plantState.isAir();

            if (!hasPlant) {
                IntegerProperty ageProperty = null;

                for (Property<?> property : cropState.getProperties()) {
                    if (property instanceof IntegerProperty intProp && property.getName().equals("age")) {
                        ageProperty = intProp;
                        break;
                    }
                }
                BlockState newCropState = cropState.setValue(ageProperty, 0);
                world.setBlock(plantPos, newCropState, 3);

                System.out.println("Planted a seed in X=" + plantPos.getX() + " Z=" + plantPos.getZ());

                if (!player.isCreative()) {
                    stack.shrink(1);
                }
            }

            if (farmlandList.size() <= numberOfSeedsInHand) {
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos adjacentSoilPos = currentFarmlandBlock.relative(direction);
                    BlockState adjacentSoilState = world.getBlockState(adjacentSoilPos);
                    BlockPos adjacentPlantPos = adjacentSoilPos.above();
                    BlockState adjacentPlantState = world.getBlockState(adjacentPlantPos);

                    boolean adjacentBlockHasPlant = adjacentPlantState.getBlock() instanceof CropBlock || !adjacentPlantState.isAir();

                    if (adjacentSoilState.is(Blocks.FARMLAND) && !adjacentBlockHasPlant && !visited.contains(adjacentSoilPos)) {
                        farmlandList.offer(adjacentSoilPos);
                        visited.add(adjacentSoilPos);
                    }
                }
            }

            count++;
        }
    }
}
