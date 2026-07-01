package dev.latvian.mods.kubejs.script.wrapper;

import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.rhino.type.TypeInfo;
import org.jspecify.annotations.Nullable;

@FunctionalInterface
public interface ComposableWrapper<F, T> {

    static <F, T> ComposableWrapper<F, T> of(Stateless<F, T> whenFinal, Stateless<F, T> whenNotFinal) {
        return (c, f, t, b) -> b ?
                whenFinal.wrap(c, f, t) :
                whenNotFinal.wrap(c, f, t);
    }

    Result<T> wrap(KubeJSContext ctx, @Nullable F from, TypeInfo finalType, boolean isFinalType);

    @FunctionalInterface
    interface Stateless<F, T> {

        Result<T> wrap(KubeJSContext ctx, @Nullable F from, TypeInfo finalType);
    }
}
