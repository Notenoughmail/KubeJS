package dev.latvian.mods.kubejs.script.wrapper;

import dev.latvian.mods.kubejs.error.KubeRuntimeException;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.script.SourceLine;
import dev.latvian.mods.kubejs.util.Cast;
import dev.latvian.mods.rhino.Context;
import org.jspecify.annotations.NullMarked;

public sealed interface Result<T> {

    /**
     * A successful, non-null conversion result
     */
    static <T> Result<T> success(T val) {
        return new Success<>(val);
    }

    /**
     * A successful, null conversion result
     */
    static <T> Result<T> empty() {
        return Cast.to(Empty.INSTANCE);
    }

    /**
     * An unsuccessful conversion result
     */
    static <T> Result<T> error(Context ctx, String msg) {
        return new Error<>(SourceLine.of(ctx), msg);
    }

    record Success<T>(T value) implements Result<T> {}

    record Error<T>(SourceLine source, String msg) implements Result<T> {

        T handle(KubeJSContext ctx) {
            Context.throwAsScriptRuntimeEx(new KubeRuntimeException(msg).source(source), ctx);
            return null;
        }
    }

    final class Empty<T> implements Result<T> {
        private static final Empty<?> INSTANCE = new Empty<>();

        private Empty() {}
    }
}
