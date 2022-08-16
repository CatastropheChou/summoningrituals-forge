package com.almostreliable.summoningrituals.altar;

import com.almostreliable.summoningrituals.Constants;
import com.almostreliable.summoningrituals.Setup;
import com.almostreliable.summoningrituals.Utils;
import com.almostreliable.summoningrituals.inventory.AltarInventory;
import com.almostreliable.summoningrituals.recipe.AltarRecipe;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.CapabilityItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AltarEntity extends BlockEntity {

    public final AltarInventory inventory;
    // TODO: decide if the cap is needed | if automation is required the catalyst logic needs to be replaced
    private final LazyOptional<AltarInventory> inventoryCap;

    @Nullable private AltarRecipe recipeCache;
    private int progress;

    public AltarEntity(BlockPos pos, BlockState state) {
        super(Setup.ALTAR_ENTITY.get(), pos, state);
        inventory = new AltarInventory(this);
        inventoryCap = LazyOptional.of(() -> inventory);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(Constants.INVENTORY)) inventory.deserializeNBT(tag.getCompound(Constants.INVENTORY));
        if (tag.contains(Constants.PROGRESS)) progress = tag.getInt(Constants.PROGRESS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(Constants.INVENTORY, inventory.serializeNBT());
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        var tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    public InteractionResult handleInteraction(ServerPlayer player, InteractionHand hand) {
        if (player.getItemInHand(hand).isEmpty()) return InteractionResult.PASS;
        if (progress > 0) {
            Utils.sendPlayerMessage(player, "in_progress", ChatFormatting.RED);
            return InteractionResult.PASS;
        }

        if (player.isShiftKeyDown()) {
            if (inventory.getInputs().isEmpty()) {
                Utils.sendPlayerMessage(player, "no_inputs", ChatFormatting.RED);
                return InteractionResult.PASS;
            }
            inventory.setCatalyst(player.getItemInHand(hand));
            player.setItemInHand(hand, ItemStack.EMPTY);
            handleSummoning(player);
            return InteractionResult.SUCCESS;
        }

        var remaining = inventory.insertItem(player.getItemInHand(hand));
        player.setItemInHand(hand, remaining);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        inventoryCap.invalidate();
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (!remove && cap.equals(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY)) {
            return inventoryCap.cast();
        }
        return super.getCapability(cap, side);
    }

    public void sendUpdate() {
        if (level == null || level.isClientSide) return;
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 1 | 2);
    }

    private void handleSummoning(ServerPlayer player) {
        assert level != null && !level.isClientSide;

        var recipe = findRecipe();
        if (recipe == null) return;

        // TODO: check weather, daytime, block below, sacrifices
        if (!checkWeather(recipe.getWeather(), player)) return;

        // TODO: actually implement recipe processing, this is only for testing
        inventory.getInputs().clear();
        inventory.setCatalyst(ItemStack.EMPTY);
        level.addFreshEntity(new ItemEntity(
            level,
            worldPosition.getX(),
            worldPosition.getY() + 2.0,
            worldPosition.getZ(),
            (ItemStack) recipe.getOutput().getEntry()
        ));
    }

    @Nullable
    private AltarRecipe findRecipe() {
        assert level != null && !level.isClientSide;
        if (recipeCache != null && recipeCache.matches(inventory.getVanillaInv(), level)) {
            return recipeCache;
        }
        var recipeManager = Utils.getRecipeManager(level);
        return recipeManager.getRecipeFor(Setup.ALTAR_RECIPE.type().get(), inventory.getVanillaInv(), level)
            .orElse(null);
    }

    private boolean checkWeather(String weather, ServerPlayer player) {
        assert level != null && !level.isClientSide;
        if (!weather.equals("any")) {
            switch (weather) {
                case "rain":
                    if (!level.isRaining()) {
                        Utils.sendPlayerMessage(player, "no_rain", ChatFormatting.GOLD);
                        return false;
                    }
                case "thunder":
                    if (!level.isThundering()) {
                        Utils.sendPlayerMessage(player, "no_thunder", ChatFormatting.GOLD);
                        return false;
                    }
                case "sun":
                    if (level.isRaining() || level.isThundering()) {
                        Utils.sendPlayerMessage(player, "no_sun", ChatFormatting.GOLD);
                        return false;
                    }
                default:
                    throw new IllegalArgumentException("Unknown weather: " + weather);
            }
        }
        return true;
    }

    private void changeActivityState(boolean state) {
        if (level == null || level.isClientSide) return;
        var oldState = level.getBlockState(worldPosition);
        if (!oldState.getValue(AltarBlock.ACTIVE).equals(state)) {
            level.setBlockAndUpdate(worldPosition, oldState.setValue(AltarBlock.ACTIVE, state));
        }
    }
}
