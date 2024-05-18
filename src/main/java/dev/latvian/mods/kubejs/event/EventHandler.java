package dev.latvian.mods.kubejs.event;

import dev.latvian.mods.kubejs.DevProperties;
import dev.latvian.mods.kubejs.script.KubeJSContext;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.ScriptTypeHolder;
import dev.latvian.mods.kubejs.script.ScriptTypePredicate;
import dev.latvian.mods.kubejs.util.ListJS;
import dev.latvian.mods.rhino.BaseFunction;
import dev.latvian.mods.rhino.Context;
import dev.latvian.mods.rhino.RhinoException;
import dev.latvian.mods.rhino.Scriptable;
import dev.latvian.mods.rhino.Wrapper;
import dev.latvian.mods.rhino.util.HideFromJS;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * <h3>Example</h3>
 * <p><code>public static final EventHandler CLIENT_RIGHT_CLICKED = ItemEvents.GROUP.client("clientRightClicked", () -> ItemClickedEventJS.class).extra(ItemEvents.SUPPORTS_ITEM);</code></p>
 */
public final class EventHandler extends BaseFunction {
	public final EventGroup group;
	public final String name;
	public final ScriptTypePredicate scriptTypePredicate;
	public final Supplier<Class<? extends KubeEvent>> eventType;
	private boolean hasResult;
	public transient Extra extra;
	private EventHandlerContainer[] eventContainers;
	private Map<Object, EventHandlerContainer[]> extraEventContainers;

	EventHandler(EventGroup g, String n, ScriptTypePredicate st, Supplier<Class<? extends KubeEvent>> e) {
		group = g;
		name = n;
		scriptTypePredicate = st;
		eventType = e;
		hasResult = false;
		extra = null;
		eventContainers = null;
		extraEventContainers = null;
	}

	/**
	 * Allow event.cancel() to be called
	 */
	public EventHandler hasResult() {
		hasResult = true;
		return this;
	}

	public boolean getHasResult() {
		return hasResult;
	}

	@HideFromJS
	public EventHandler extra(Extra extra) {
		this.extra = extra;
		return this;
	}

	@HideFromJS
	public void clear(ScriptType type) {
		if (eventContainers != null) {
			eventContainers[type.ordinal()] = null;

			if (EventHandlerContainer.isEmpty(eventContainers)) {
				eventContainers = null;
			}
		}

		if (extraEventContainers != null) {
			var entries = extraEventContainers.entrySet().iterator();

			while (entries.hasNext()) {
				var entry = entries.next();
				entry.getValue()[type.ordinal()] = null;

				if (EventHandlerContainer.isEmpty(entry.getValue())) {
					entries.remove();
				}
			}

			if (extraEventContainers.isEmpty()) {
				extraEventContainers = null;
			}
		}
	}

	public boolean hasListeners() {
		return eventContainers != null || extraEventContainers != null;
	}

	/**
	 * Important! extraId won't be transformed for performance reasons. Only use this over {@link EventHandler#hasListeners()} if you think this will be more performant. Recommended only with identity extra IDs.
	 */
	public boolean hasListeners(Object extraId) {
		return eventContainers != null || extraEventContainers != null && extraEventContainers.containsKey(extraId);
	}

	public void listen(@Nullable Context cx, ScriptType type, @Nullable Object extraId, IEventHandler handler) {
		if (!type.manager.get().canListenEvents) {
			throw new IllegalStateException("Event handler '" + this + "' can only be registered during script loading!");
		}

		if (!scriptTypePredicate.test(type)) {
			throw new UnsupportedOperationException("Tried to register event handler '" + this + "' for invalid script type " + type + "! Valid script types: " + scriptTypePredicate.getValidTypes());
		}

		if (extraId != null && extra != null) {
			extraId = Wrapper.unwrapped(extraId);
			extraId = extra.transformer.transform(extraId);
		}

		if (extra != null && extra.required && extraId == null) {
			throw new IllegalArgumentException("Event handler '" + this + "' requires extra id!");
		}

		if (extra == null && extraId != null) {
			throw new IllegalArgumentException("Event handler '" + this + "' doesn't support extra id!");
		}

		if (extra != null && extraId != null && !extra.validator.test(extraId)) {
			throw new IllegalArgumentException("Event handler '" + this + "' doesn't accept id '" + extra.toString.transform(extraId) + "'!");
		}

		var line = new int[1];
		var source = cx == null ? "java" : Context.getSourcePositionFromStack(cx, line);

		EventHandlerContainer[] map;

		if (extraId == null) {
			if (eventContainers == null) {
				eventContainers = new EventHandlerContainer[ScriptType.VALUES.length];
			}

			map = eventContainers;
		} else {
			if (extraEventContainers == null) {
				extraEventContainers = extra.identity ? new IdentityHashMap<>() : new HashMap<>();
			}

			map = extraEventContainers.get(extraId);

			//noinspection Java8MapApi
			if (map == null) {
				map = new EventHandlerContainer[ScriptType.VALUES.length];
				extraEventContainers.put(extraId, map);
			}
		}

		var index = type.ordinal();

		if (map[index] == null) {
			map[index] = new EventHandlerContainer(extraId, handler, source, line[0]);
		} else {
			map[index].add(extraId, handler, source, line[0]);
		}
	}

	@HideFromJS
	public void listenJava(ScriptType type, @Nullable Object extraId, IEventHandler handler) {
		var b = type.manager.get().canListenEvents;
		type.manager.get().canListenEvents = true;
		listen(null, type, extraId, handler);
		type.manager.get().canListenEvents = b;
	}

	/**
	 * @see EventHandler#post(ScriptTypeHolder, Object, KubeEvent, EventExceptionHandler)
	 */
	public EventResult post(KubeEvent event) {
		if (scriptTypePredicate instanceof ScriptTypeHolder type) {
			return post(type, null, event);
		} else {
			throw new IllegalStateException("You must specify which script type to post event to");
		}
	}

	/**
	 * @see EventHandler#post(ScriptTypeHolder, Object, KubeEvent, EventExceptionHandler)
	 */
	public EventResult post(ScriptTypeHolder scriptType, KubeEvent event) {
		return post(scriptType, null, event);
	}

	/**
	 * @see EventHandler#post(ScriptTypeHolder, Object, KubeEvent, EventExceptionHandler)
	 */
	// sth, event, exh
	public EventResult post(ScriptTypeHolder scriptType, KubeEvent event, EventExceptionHandler exh) {
		return post(scriptType, null, event, exh);
	}

	/**
	 * @see EventHandler#post(ScriptTypeHolder, Object, KubeEvent, EventExceptionHandler)
	 */
	public EventResult post(KubeEvent event, @Nullable Object extraId) {
		if (scriptTypePredicate instanceof ScriptTypeHolder type) {
			return post(type, extraId, event);
		} else {
			throw new IllegalStateException("You must specify which script type to post event to");
		}
	}

	/**
	 * @see EventHandler#post(ScriptTypeHolder, Object, KubeEvent, EventExceptionHandler)
	 */
	public EventResult post(KubeEvent event, @Nullable Object extraId, EventExceptionHandler exh) {
		if (scriptTypePredicate instanceof ScriptTypeHolder type) {
			return post(type, extraId, event, exh);
		} else {
			throw new IllegalStateException("You must specify which script type to post event to");
		}
	}

	/**
	 * @see EventHandler#post(ScriptTypeHolder, Object, KubeEvent, EventExceptionHandler)
	 */
	public EventResult post(ScriptTypeHolder type, @Nullable Object extraId, KubeEvent event) {
		return post(type, extraId, event, null);
	}

	/**
	 * @return EventResult that can contain an object. What previously returned true on {@link KubeEvent#cancel()} now returns {@link EventResult#interruptFalse()}
	 * @see KubeEvent#cancel()
	 * @see KubeEvent#success()
	 * @see KubeEvent#exit()
	 * @see KubeEvent#cancel(Object)
	 * @see KubeEvent#success(Object)
	 * @see KubeEvent#exit(Object)
	 */
	public EventResult post(ScriptTypeHolder type, @Nullable Object extraId, KubeEvent event, EventExceptionHandler exh) {
		if (!hasListeners()) {
			return EventResult.PASS;
		}

		var scriptType = type.kjs$getScriptType();

		if (extraId != null && extra != null) {
			extraId = Wrapper.unwrapped(extraId);
			extraId = extra.transformer.transform(extraId);
		}

		if (extra != null && extra.required && extraId == null) {
			throw new IllegalArgumentException("Event handler '" + this + "' requires extra id!");
		}

		if (extra == null && extraId != null) {
			throw new IllegalArgumentException("Event handler '" + this + "' doesn't support extra id " + extraId + "!");
		}

		var eventResult = EventResult.PASS;

		try {
			var extraContainers = extraEventContainers == null ? null : extraEventContainers.get(extraId);

			if (extraContainers != null) {
				postToHandlers(scriptType, extraContainers, event, exh);

				if (!scriptType.isStartup()) {
					postToHandlers(ScriptType.STARTUP, extraContainers, event, exh);
				}
			}

			if (eventContainers != null) {
				postToHandlers(scriptType, eventContainers, event, exh);

				if (!scriptType.isStartup()) {
					postToHandlers(ScriptType.STARTUP, eventContainers, event, exh);
				}
			}
		} catch (EventExit exit) {
			if (exit.result.type() == EventResult.Type.ERROR) {
				if (DevProperties.get().debugInfo) {
					((Throwable) exit.result.value()).printStackTrace();
				}

				scriptType.console.error("Error in '" + this + "'", (Throwable) exit.result.value());
			} else {
				eventResult = exit.result;

				if (!getHasResult()) {
					scriptType.console.error("Error in '" + this + "'", new IllegalStateException("Event returned result when it's not cancellable"));
				}
			}
		} catch (RhinoException error) {
			scriptType.console.error("Error in '" + this + "'", error);
		}

		event.afterPosted(eventResult);
		return eventResult;
	}

	private void postToHandlers(ScriptType type, EventHandlerContainer[] containers, KubeEvent event, @Nullable EventExceptionHandler exh) throws EventExit {
		var handler = containers[type.ordinal()];

		if (handler != null) {
			handler.handle(event, exh);
		}
	}

	@Override
	public String toString() {
		return group + "." + name;
	}

	@Override
	public Object call(Context cx, Scriptable scope, Scriptable thisObj, Object[] args) {
		ScriptType type = ((KubeJSContext) cx).getType();

		try {
			if (args.length == 1) {
				listen(cx, type, null, (IEventHandler) cx.jsToJava(args[0], IEventHandler.class));
			} else if (args.length == 2) {
				var handler = (IEventHandler) cx.jsToJava(args[1], IEventHandler.class);

				for (var o : ListJS.orSelf(args[0])) {
					listen(cx, type, o, handler);
				}
			}
		} catch (Exception ex) {
			type.console.error(ex.getLocalizedMessage());
		}

		return null;
	}

	public void forEachListener(ScriptType type, Consumer<EventHandlerContainer> callback) {
		if (eventContainers != null) {
			var c = eventContainers[type.ordinal()];

			while (c != null) {
				callback.accept(c);
				c = c.child;
			}
		}

		if (extraEventContainers != null) {
			for (var entry : extraEventContainers.entrySet()) {
				var c = entry.getValue()[type.ordinal()];

				while (c != null) {
					callback.accept(c);
					c = c.child;
				}
			}
		}
	}

	public Set<Object> findUniqueExtraIds(ScriptType type) {
		if (!hasListeners()) {
			return Set.of();
		}

		var set = new HashSet<>();

		forEachListener(type, c -> {
			if (c.extraId != null) {
				set.add(c.extraId);
			}
		});

		return set;
	}
}