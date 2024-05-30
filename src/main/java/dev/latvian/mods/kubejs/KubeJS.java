package dev.latvian.mods.kubejs;

import com.google.common.base.Stopwatch;
import dev.latvian.mods.kubejs.bindings.event.StartupEvents;
import dev.latvian.mods.kubejs.client.KubeJSClient;
import dev.latvian.mods.kubejs.event.KubeStartupEvent;
import dev.latvian.mods.kubejs.gui.KubeJSMenus;
import dev.latvian.mods.kubejs.ingredient.KubeJSIngredients;
import dev.latvian.mods.kubejs.item.creativetab.CreativeTabCallbackForge;
import dev.latvian.mods.kubejs.item.creativetab.CreativeTabKubeEvent;
import dev.latvian.mods.kubejs.item.creativetab.KubeJSCreativeTabs;
import dev.latvian.mods.kubejs.neoforge.KubeJSNeoForgeClient;
import dev.latvian.mods.kubejs.net.KubeJSNet;
import dev.latvian.mods.kubejs.recipe.KubeJSRecipeSerializers;
import dev.latvian.mods.kubejs.recipe.schema.RecipeNamespace;
import dev.latvian.mods.kubejs.registry.RegistryInfo;
import dev.latvian.mods.kubejs.script.ConsoleLine;
import dev.latvian.mods.kubejs.script.PlatformWrapper;
import dev.latvian.mods.kubejs.script.ScriptFileInfo;
import dev.latvian.mods.kubejs.script.ScriptManager;
import dev.latvian.mods.kubejs.script.ScriptPack;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.script.ScriptsLoadedEvent;
import dev.latvian.mods.kubejs.script.data.GeneratedResourcePack;
import dev.latvian.mods.kubejs.util.Cast;
import dev.latvian.mods.kubejs.util.ConsoleJS;
import dev.latvian.mods.kubejs.util.KubeJSBackgroundThread;
import dev.latvian.mods.kubejs.util.KubeJSPlugins;
import dev.latvian.mods.kubejs.util.UtilsJS;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.DistExecutor;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforgespi.language.IModFileInfo;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;

@Mod(KubeJS.MOD_ID)
public class KubeJS {
	public static final String MOD_ID = "kubejs";
	public static final String MOD_NAME = "KubeJS";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_NAME);
	public static final int MC_VERSION_NUMBER = 2006;
	public static final String MC_VERSION_STRING = "1.20.6";
	public static String QUERY;
	public static final ResourceLocation ICONS_FONT = id("icons");
	public static final Component NAME_COMPONENT = Component.empty().append(Component.literal("K").withStyle(Style.EMPTY.withFont(ICONS_FONT))).append(" ").append(Component.literal(MOD_NAME));
	public static String VERSION = "0";
	private static final ThreadLocal<IEventBus> BUS = new ThreadLocal<>();

	public static Optional<IEventBus> eventBus() {
		return Optional.ofNullable(BUS.get());
	}

	public static ResourceLocation id(String path) {
		return new ResourceLocation(MOD_ID, path);
	}

	public static ModContainer thisMod;

	public static KubeJSCommon PROXY;

	private static ScriptManager startupScriptManager, clientScriptManager;

	public static ScriptManager getStartupScriptManager() {
		return startupScriptManager;
	}

	public static ScriptManager getClientScriptManager() {
		return clientScriptManager;
	}

	public KubeJS(IEventBus bus, Dist dist, ModContainer mod) throws Throwable {
		thisMod = mod;
		VERSION = mod.getModInfo().getVersion().toString();

		if (Files.notExists(KubeJSPaths.README)) {
			try {
				Files.writeString(KubeJSPaths.README, """
					Find out more info on the website: https://kubejs.com/
									
					Directory information:
									
					assets - Acts as a resource pack, you can put any client resources in here, like textures, models, etc. Example: assets/kubejs/textures/item/test_item.png
					data - Acts as a datapack, you can put any server resources in here, like loot tables, functions, etc. Example: data/kubejs/loot_tables/blocks/test_block.json
									
					startup_scripts - Scripts that get loaded once during game startup - Used for adding items and other things that can only happen while the game is loading (Can be reloaded with /kubejs reload_startup_scripts, but it may not work!)
					server_scripts - Scripts that get loaded every time server resources reload - Used for modifying recipes, tags, loot tables, and handling server events (Can be reloaded with /reload)
					client_scripts - Scripts that get loaded every time client resources reload - Used for JEI events, tooltips and other client side things (Can be reloaded with F3+T)
									
					config - KubeJS config storage. This is also the only directory that scripts can access other than world directory
					exported - Data dumps like texture atlases end up here
									
					You can find type-specific logs in logs/kubejs/ directory
					""".trim()
				);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}

		bus.addListener(KubeJSNet::register);

		BUS.set(bus);

		//noinspection removal
		PROXY = DistExecutor.safeRunForDist(() -> KubeJSClient::new, () -> KubeJSCommon::new);

		if (!PlatformWrapper.isGeneratingData()) {
			new KubeJSBackgroundThread().start();
			// Required to be called this way because ConsoleJS class hasn't been initialized yet
			ScriptType.STARTUP.console.setCapturingErrors(true);
		}

		var pluginTimer = Stopwatch.createStarted();
		LOGGER.info("Looking for KubeJS plugins...");
		var allMods = new ArrayList<>(ModList.get().getMods().stream().map(IModInfo::getOwningFile).map(IModFileInfo::getFile).toList());
		allMods.remove(thisMod.getModInfo().getOwningFile().getFile());
		allMods.add(0, thisMod.getModInfo().getOwningFile().getFile());
		KubeJSPlugins.load(allMods, dist == Dist.CLIENT);
		LOGGER.info("Done in " + pluginTimer.stop());

		KubeJSPlugins.forEachPlugin(KubeJSPlugin::init);

		startupScriptManager = new ScriptManager(ScriptType.STARTUP);
		clientScriptManager = new ScriptManager(ScriptType.CLIENT);

		startupScriptManager.reload(null);

		KubeJSPlugins.forEachPlugin(KubeJSPlugin::initStartup);

		PROXY.init(bus);

		for (var extraId : StartupEvents.REGISTRY.findUniqueExtraIds(ScriptType.STARTUP)) {
			if (extraId instanceof ResourceKey<?> key) {
				RegistryInfo.of((ResourceKey) key).fireRegistryEvent();
			}
		}

		GeneratedResourcePack.scanForInvalidFiles("kubejs/assets/", KubeJSPaths.ASSETS);
		GeneratedResourcePack.scanForInvalidFiles("kubejs/data/", KubeJSPaths.DATA);

		if (CommonProperties.get().serverOnly) {
			// FIXME ModLoadingContext.get().registerExtensionPoint(DisplayTest.class, () -> new DisplayTest(() -> DisplayTest.IGNORESERVERONLY, (a, b) -> true));
		} else {
			NeoForgeMod.enableMilkFluid();
			KubeJSIngredients.REGISTRY.register(bus);
			KubeJSCreativeTabs.REGISTRY.register(bus);
			// KubeJSComponents.REGISTRY.register(bus);
			KubeJSRecipeSerializers.REGISTRY.register(bus);
			KubeJSMenus.REGISTRY.register(bus);
		}

		if (dist == Dist.CLIENT) {
			new KubeJSNeoForgeClient(bus);
		}

		StartupEvents.INIT.post(ScriptType.STARTUP, KubeStartupEvent.BASIC);
		// KubeJSRegistries.chunkGenerators().register(new ResourceLocation(KubeJS.MOD_ID, "flat"), () -> KJSFlatLevelSource.CODEC);
	}

	public static void loadScripts(ScriptPack pack, Path dir, String path) {
		if (!path.isEmpty() && !path.endsWith("/")) {
			path += "/";
		}

		final var pathPrefix = path;

		try {
			for (var file : Files.walk(dir, 10, FileVisitOption.FOLLOW_LINKS).filter(Files::isRegularFile).toList()) {
				var fileName = dir.relativize(file).toString().replace(File.separatorChar, '/');

				if (fileName.endsWith(".js") || fileName.endsWith(".ts") && !fileName.endsWith(".d.ts")) {
					pack.info.scripts.add(new ScriptFileInfo(pack.info, pathPrefix + fileName));
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}

	public static Path verifyFilePath(Path path) throws IOException {
		if (!path.normalize().toAbsolutePath().startsWith(KubeJSPaths.GAMEDIR)) {
			throw new IOException("You can't access files outside Minecraft directory!");
		}

		return path;
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void initRegistries(RegisterEvent event) {
		var info = RegistryInfo.of((ResourceKey) event.getRegistryKey());
		info.registerObjects((id, supplier) -> event.register(Cast.to(info.key), id, supplier));
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void commonSetup(FMLCommonSetupEvent event) {
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void creativeTab(BuildCreativeModeTabContentsEvent event) {
		var tabId = event.getTabKey().location();

		if (StartupEvents.MODIFY_CREATIVE_TAB.hasListeners(tabId)) {
			StartupEvents.MODIFY_CREATIVE_TAB.post(ScriptType.STARTUP, tabId, new CreativeTabKubeEvent(event.getTab(), event.hasPermissions(), new CreativeTabCallbackForge(event)));
		}
	}

	@SubscribeEvent(priority = EventPriority.LOW)
	public static void loadComplete(FMLLoadCompleteEvent event) {
		KubeJSPlugins.forEachPlugin(KubeJSPlugin::afterInit);
		NeoForge.EVENT_BUS.post(new ScriptsLoadedEvent());
		StartupEvents.POST_INIT.post(ScriptType.STARTUP, KubeStartupEvent.BASIC);
		UtilsJS.postModificationEvents();
		RecipeNamespace.getAll();

		if (!ConsoleJS.STARTUP.errors.isEmpty()) {
			var list = new ArrayList<String>();
			list.add("Startup script errors:");

			var lines = ConsoleJS.STARTUP.errors.toArray(ConsoleLine.EMPTY_ARRAY);

			for (int i = 0; i < lines.length; i++) {
				list.add((i + 1) + ") " + lines[i]);
			}

			LOGGER.error(String.join("\n", list));

			ConsoleJS.STARTUP.flush(true);

			if (FMLLoader.getDist() == Dist.DEDICATED_SERVER || !CommonProperties.get().startupErrorGUI) {
				throw new RuntimeException("There were KubeJS startup script syntax errors! See logs/kubejs/startup.log for more info");
			}
		}

		ConsoleJS.STARTUP.setCapturingErrors(false);

		QUERY = "source=kubejs&mc=" + MC_VERSION_NUMBER + "&loader=" + PlatformWrapper.getName() + "&v=" + URLEncoder.encode(thisMod.getModInfo().getVersion().toString(), StandardCharsets.UTF_8);

		Util.nonCriticalIoPool().submit(() -> {
			try {
				var response = HttpClient.newBuilder()
					.followRedirects(HttpClient.Redirect.ALWAYS)
					.connectTimeout(Duration.ofSeconds(5L))
					.build()
					.send(HttpRequest.newBuilder().uri(URI.create("https://v.kubejs.com/update-check?" + QUERY)).GET().build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
				if (response.statusCode() == 200) {
					var body = response.body().trim();

					if (!body.isEmpty()) {
						ConsoleJS.STARTUP.info("Update available: " + body);
					}
				}
			} catch (Exception ignored) {
			}
		});
	}
}