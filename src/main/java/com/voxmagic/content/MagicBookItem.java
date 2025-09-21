package com.voxmagic.content;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.WrittenBookContentComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.RawFilteredPair;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

public class MagicBookItem extends Item {
    private static final WrittenBookContentComponent GUIDE_CONTENT = new WrittenBookContentComponent(
            RawFilteredPair.of("\u041c\u0430\u0433\u0438\u0447\u0435\u0441\u043a\u0430\u044f \u043a\u043d\u0438\u0433\u0430"),
            "VoxMagic",
            0,
            List.of(RawFilteredPair.of(Text.translatable("text.voxmagic.magic_book.guide"))),
            true
    );

    public MagicBookItem(Settings settings) {
        super(settings);
    }

    @Override
    public ItemStack getDefaultStack() {
        ItemStack stack = super.getDefaultStack();
        stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, GUIDE_CONTENT);
        stack.set(DataComponentTypes.CUSTOM_NAME, displayName());
        return stack;
    }

    @Override
    public void postProcessComponents(ItemStack stack) {
        super.postProcessComponents(stack);
        if (stack.get(DataComponentTypes.WRITTEN_BOOK_CONTENT) == null) {
            stack.set(DataComponentTypes.WRITTEN_BOOK_CONTENT, GUIDE_CONTENT);
        }
        if (stack.get(DataComponentTypes.CUSTOM_NAME) == null) {
            stack.set(DataComponentTypes.CUSTOM_NAME, displayName());
        }
    }

    private static Text displayName() {
        return Text.literal("\u041c\u0430\u0433\u0438\u0447\u0435\u0441\u043a\u0430\u044f \u043a\u043d\u0438\u0433\u0430").formatted(Formatting.DARK_PURPLE, Formatting.ITALIC);
    }
}
