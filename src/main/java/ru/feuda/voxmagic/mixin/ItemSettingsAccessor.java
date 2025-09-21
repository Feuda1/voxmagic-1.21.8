package ru.feuda.voxmagic.mixin;

import net.minecraft.item.Item;
import net.minecraft.registry.RegistryKey;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Item.Settings.class)
public interface ItemSettingsAccessor {
    @Accessor("registryKey")
    void voxmagic$setRegistryKey(RegistryKey<Item> key);
}
