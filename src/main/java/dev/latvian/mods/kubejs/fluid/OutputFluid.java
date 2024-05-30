package dev.latvian.mods.kubejs.fluid;

import dev.latvian.mods.kubejs.recipe.KubeRecipe;
import dev.latvian.mods.kubejs.recipe.OutputReplacement;
import dev.latvian.mods.kubejs.recipe.ReplacementMatch;

public interface OutputFluid extends FluidLike, OutputReplacement {
	@Override
	default Object replaceOutput(KubeRecipe recipe, ReplacementMatch match, OutputReplacement original) {
		if (original instanceof FluidLike o) {
			return kjs$copy(o.kjs$getAmount());
		}

		return kjs$copy(kjs$getAmount());
	}
}