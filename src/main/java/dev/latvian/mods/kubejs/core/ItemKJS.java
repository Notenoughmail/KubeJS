package dev.latvian.mods.kubejs.core;

import dev.latvian.mods.kubejs.item.FoodBuilder;
import dev.latvian.mods.kubejs.item.ItemBuilder;
import dev.latvian.mods.kubejs.item.ItemStackKey;
import dev.latvian.mods.kubejs.item.MutableToolTier;
import dev.latvian.mods.kubejs.registry.RegistryInfo;
import dev.latvian.mods.kubejs.util.UtilsJS;
import dev.latvian.mods.rhino.util.RemapPrefixForJS;
import net.minecraft.Util;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TieredItem;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

@RemapPrefixForJS("kjs$")
public interface ItemKJS extends IngredientSupplierKJS {
	@Nullable
	default ItemBuilder kjs$getItemBuilder() {
		throw new NoMixinException();
	}

	default Item kjs$self() {
		throw new NoMixinException();
	}

	default ResourceLocation kjs$getIdLocation() {
		return UtilsJS.UNKNOWN_ID;
	}

	default String kjs$getId() {
		return kjs$getIdLocation().toString();
	}

	default String kjs$getMod() {
		return kjs$getIdLocation().getNamespace();
	}

	default String kjs$getCreativeTab() {
		var id = RegistryInfo.ITEM.getId(kjs$self());
		return id == null ? "unknown" : id.getNamespace();
	}

	default void kjs$setItemBuilder(ItemBuilder b) {
		throw new NoMixinException();
	}

	default CompoundTag kjs$getTypeData() {
		throw new NoMixinException();
	}

	default void kjs$setMaxStackSize(int i) {
		throw new NoMixinException();
	}

	default void kjs$setMaxDamage(int i) {
		throw new NoMixinException();
	}

	default void kjs$setCraftingRemainder(Item i) {
		throw new NoMixinException();
	}

	default void kjs$setFireResistant(boolean b) {
		throw new NoMixinException();
	}

	default void kjs$setRarity(Rarity r) {
		throw new NoMixinException();
	}

	default void kjs$setBurnTime(int i) {
		throw new NoMixinException();
	}

	default void kjs$setFoodProperties(FoodProperties properties) {
		throw new NoMixinException();
	}

	default void kjs$setTier(Consumer<MutableToolTier> c) {
		if (this instanceof TieredItem tiered) {
			tiered.tier = Util.make(new MutableToolTier(tiered.tier), c);
		} else {
			throw new IllegalArgumentException("Item is not a tool/tiered item!");
		}
	}

	default void kjs$setFoodProperties(Consumer<FoodBuilder> consumer) {
		var fp = kjs$self().components().get(DataComponents.FOOD);
		var builder = fp == null ? new FoodBuilder() : new FoodBuilder(fp);
		consumer.accept(builder);
		kjs$setFoodProperties(builder.build());
	}

	default void kjs$setNameKey(String key) {
		throw new NoMixinException();
	}

	default ItemStackKey kjs$getTypeItemStackKey() {
		throw new NoMixinException();
	}
}