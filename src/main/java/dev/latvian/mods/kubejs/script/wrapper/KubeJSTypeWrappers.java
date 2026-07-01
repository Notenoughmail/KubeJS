package dev.latvian.mods.kubejs.script.wrapper;

import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.util.Cast;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.type.TypeInfo;
import dev.latvian.mods.rhino.util.wrap.TypeWrapperFactory;
import dev.latvian.mods.rhino.util.wrap.TypeWrappers;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;

public class KubeJSTypeWrappers extends TypeWrappers {

    private final Map<Class<?>, Node<?>> nodes = new IdentityHashMap<>();
    private final Queue<Runnable> mapPrimers = new ArrayDeque<>();
    private final Map<Class<?>, Map<Class<?>, BakedWrapper<?, ?>>> bakedWrappers = new IdentityHashMap<>();

    public <F, T> void register(Class<F> fromType, Class<T> toType, int weight, ComposableWrapper<F, T> wrapper) {
        getNode(fromType).put(getNode(toType), new Edge<>(wrapper, weight));
    }

    private <F> Node<F> getNode(Class<F> clazz) {
        final Node<F> node = Cast.to(nodes.computeIfAbsent(clazz, this::createNode));
        while (!mapPrimers.isEmpty()) {
            mapPrimers.poll().run();
        }
        return node;
    }

    private <F> Node<F> createNode(Class<F> clazz) {
        final Node<F> node = new Node<>(clazz);
        iterateInheritance(clazz, inheritNode(node));
        return node;
    }

    private Consumer<Class<?>> inheritNode(Node<?> superClazz) {
        return c -> mapPrimers.offer(() -> {
            final Node<?> node = nodes.computeIfAbsent(c, Node::new);
            superClazz.put(node, Edge.inheritance());
            iterateInheritance(c, inheritNode(node));
        });
    }

    private static void iterateInheritance(Class<?> origin, Consumer<Class<?>> foundAssignable) {
        if (!origin.isArray() && !origin.isPrimitive()) {
            for (Class<?> c : origin.getInterfaces()) {
                foundAssignable.accept(c);
                iterateInheritance(c, foundAssignable);
            }
            origin = origin.getSuperclass();
            if (origin != null) {
                foundAssignable.accept(origin);
                iterateInheritance(origin, foundAssignable);
            }
        }
    }

    @Nullable
    @Override
    public TypeWrapperFactory<?> getWrapperFactory(@Nullable Object from, TypeInfo target) {
        if (target == TypeInfo.OBJECT) {
            return null;
        }

        final Class<?> to = target.asClass();
        if (!nodes.containsKey(to)) return null; // If no node was ever registered for it, then there must not be a possible wrapper sequence
        final Class<?> origin = from == null ? Object.class : from.getClass();
        if (nodes.get(origin).conversions().isEmpty()) return null; // The FROM type is a dead end and can't convert to anything

        if (!bakedWrappers.containsKey(origin)) return null;
        final Map<Class<?>, BakedWrapper<?, ?>> wrapperMap = bakedWrappers.get(origin);
        return wrapperMap.computeIfAbsent(to, _ -> solveWrapper(origin, to));
    }

    @Nullable
    private <F, T> BakedWrapper<F, T> solveWrapper(Class<F> from, Class<T> to) {
        // TODO: Run a Dijkstra over the graph
    }

    @Override
    public boolean hasWrapper(Object from, TypeInfo target) {
        return getWrapperFactory(from, target) != null;
    }

    public record Edge<F, T>(ComposableWrapper<F, T> wrapper, int weight) {
        static final Edge<?, ?> INHERITANCE = new Edge<>((c, f, t, b) -> Result.success(f), 1);

        static <F, T> Edge<F, T> inheritance() {
            return Cast.to(INHERITANCE);
        }
    }

    private record Node<F>(Class<F> clazz, Map<Node<?>, Edge<F, ?>> conversions) {

        Node(Class<F> clazz) {
            this(clazz, new IdentityHashMap<>());
        }

        <T> void put(Node<T> node, Edge<F, T> conversion) {
            final Edge<?, ?> prev = conversions.get(node);
            if (prev == null || prev.weight > conversion.weight) {
                conversions.put(node, conversion);
            }
        }

        @Nullable
        <T> Edge<F, T> getConversion(Node<T> node) {
            return Cast.to(conversions.get(node));
        }
    }

    private record BakedWrapper<F, T>(List<ComposableWrapper<?, ?>> wrappers, int totalWeight) implements TypeWrapperFactory<T> {

        @Override
        public T wrap(Context cx, Object from, TypeInfo target) {
            final KubeJSContext ctx = Cast.to(cx);
            Result<?> res = Result.success(from);
            final Iterator<ComposableWrapper<?, ?>> itr = wrappers.iterator();
            while (itr.hasNext()) {
                final ComposableWrapper<?, ?> wrapper = itr.next();
                switch (res) {
                    case Result.Success<?> s -> res = wrapper.wrap(ctx, Cast.to(s.value()), target, !itr.hasNext());
                    case Result.Empty<?> _ -> res = wrapper.wrap(ctx, null, target, !itr.hasNext());
                    case Result.Error<?> e -> Cast.to(e.handle(ctx));
                }
            }
            return switch (res) {
                case Result.Success<?> s -> Cast.to(s.value());
                case Result.Empty<?> _ -> null;
                case Result.Error<?> e -> Cast.to(e.handle(ctx));
            };
        }
    }
}
