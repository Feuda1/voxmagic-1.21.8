package com.voxmagic.content;

import com.voxmagic.VoxMagicMode;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import ru.feuda.voxmagic.mixin.ItemSettingsAccessor;

public final class ModItems {
    public static Item MAGIC_BOOK;

    public static void register() {
        Identifier id = id("magic_book");
        Item.Settings settings = new Item.Settings().maxCount(1);
        RegistryKey<Item> key = RegistryKey.of(RegistryKeys.ITEM, id);
        ((ItemSettingsAccessor) (Object) settings).voxmagic$setRegistryKey(key);

        Item item = new MagicBookItem(settings);
        MAGIC_BOOK = Registry.register(Registries.ITEM, id, item);
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.COMBAT).register(entries -> entries.add(new ItemStack(MAGIC_BOOK)));
    }

    public static Identifier id(String path) {
        return Identifier.of(VoxMagicMode.MOD_ID, path);
    }
}
