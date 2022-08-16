package com.almostreliable.summoningrituals.inventory;

import com.almostreliable.summoningrituals.Constants;
import com.almostreliable.summoningrituals.altar.AltarEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;

import static com.almostreliable.summoningrituals.Utils.f;

public class AltarInventory implements IItemHandlerModifiable, INBTSerializable<CompoundTag> {

    private final AltarEntity parent;
    private final AltarInvWrapper vanillaInv;
    private NonNullList<ItemStack> inputs;
    private ItemStack catalyst;

    public AltarInventory(AltarEntity parent) {
        this.parent = parent;
        vanillaInv = new AltarInvWrapper(this);
        inputs = NonNullList.create();
        catalyst = ItemStack.EMPTY;
    }

    @Override
    public CompoundTag serializeNBT() {
        var tagList = new ListTag();
        for (var slot = 0; slot < inputs.size(); slot++) {
            if (!inputs.get(slot).isEmpty()) {
                var itemTag = new CompoundTag();
                itemTag.putInt(Constants.SLOT, slot);
                inputs.get(slot).save(itemTag);
                tagList.add(itemTag);
            }
        }
        var tag = new CompoundTag();
        tag.putInt(Constants.SIZE, inputs.size());
        tag.put(Constants.ITEMS, tagList);
        tag.put(Constants.CATALYST, catalyst.serializeNBT());
        return tag;
    }

    @Override
    public void deserializeNBT(CompoundTag tag) {
        setSize(tag.contains(Constants.SIZE) ? tag.getInt(Constants.SIZE) : inputs.size());
        var tagList = tag.getList(Constants.ITEMS, Tag.TAG_COMPOUND);
        for (var i = 0; i < tagList.size(); i++) {
            var itemTags = tagList.getCompound(i);
            var slot = itemTags.getInt(Constants.SLOT);
            if (slot >= 0 && slot < inputs.size()) {
                inputs.set(slot, ItemStack.of(itemTags));
            }
        }
        catalyst = ItemStack.of(tag.getCompound(Constants.CATALYST));
    }

    @Override
    public void setStackInSlot(int slot, @NotNull ItemStack stack) {
        validateSlot(slot);
        inputs.set(slot, stack);
        onContentsChanged();
    }

    public ItemStack insertItem(ItemStack stack) {
        var remaining = stack;
        for (var i = 0; i < inputs.size(); i++) {
            remaining = insertItem(i, remaining, false);
            if (remaining.isEmpty()) {
                return ItemStack.EMPTY;
            }
        }
        if (inputs.add(remaining)) {
            onContentsChanged();
            return ItemStack.EMPTY;
        }
        return remaining;
    }

    private void onContentsChanged() {
        parent.setChanged();
        parent.sendUpdate();
    }

    private void validateSlot(int slot) {
        if (slot < 0 || slot >= inputs.size()) {
            throw new IllegalStateException(f("Slot {} is not in range [0,{})", slot, inputs.size()));
        }
    }

    private int getStackLimit(int slot, ItemStack stack) {
        return Math.min(getSlotLimit(slot), stack.getMaxStackSize());
    }

    @Override
    public int getSlots() {
        return inputs.size();
    }

    @NotNull
    @Override
    public ItemStack getStackInSlot(int slot) {
        validateSlot(slot);
        return inputs.get(slot);
    }

    @NotNull
    @Override
    public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        if (!isItemValid(slot, stack)) return stack;
        validateSlot(slot);

        var current = inputs.get(slot);
        var limit = getStackLimit(slot, stack);
        if (!current.isEmpty()) {
            if (!ItemHandlerHelper.canItemStacksStack(stack, current)) return stack;
            limit -= current.getCount();
        }
        if (limit <= 0) return stack;

        var reachedLimit = stack.getCount() > limit;
        if (!simulate) {
            if (current.isEmpty()) {
                inputs.set(slot, reachedLimit ? ItemHandlerHelper.copyStackWithSize(stack, limit) : stack);
            } else {
                current.grow(reachedLimit ? limit : stack.getCount());
            }
            onContentsChanged();
        }

        return reachedLimit ? ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - limit) : ItemStack.EMPTY;
    }

    @NotNull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) return ItemStack.EMPTY;
        validateSlot(slot);

        var current = inputs.get(slot);
        if (current.isEmpty()) return ItemStack.EMPTY;
        var toExtract = Math.min(amount, current.getMaxStackSize());

        if (current.getCount() <= toExtract) {
            if (!simulate) {
                inputs.set(slot, ItemStack.EMPTY);
                onContentsChanged();
                return current;
            }
            return current.copy();
        }
        if (!simulate) {
            inputs.set(slot, ItemHandlerHelper.copyStackWithSize(current, current.getCount() - toExtract));
            onContentsChanged();
        }

        return ItemHandlerHelper.copyStackWithSize(current, toExtract);
    }

    @Override
    public int getSlotLimit(int slot) {
        validateSlot(slot);
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @NotNull ItemStack stack) {
        return true;
    }

    public AltarEntity getParent() {
        return parent;
    }

    public AltarInvWrapper getVanillaInv() {
        return vanillaInv;
    }

    public NonNullList<ItemStack> getInputs() {
        return inputs;
    }

    public ItemStack getCatalyst() {
        return catalyst;
    }

    public void setCatalyst(ItemStack catalyst) {
        this.catalyst = catalyst;
        onContentsChanged();
    }

    private void setSize(int size) {
        inputs = NonNullList.withSize(size, ItemStack.EMPTY);
    }
}
