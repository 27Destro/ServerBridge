/*******************************************************************************
 * MIT License
 *
 * Copyright (c) 2016 Valentin 'ThisIsMac' Marchaud
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *******************************************************************************/
package fr.vmarchaud.mineweb.bukkit;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.vmarchaud.mineweb.common.injector.NettyServer;
import fr.vmarchaud.mineweb.common.injector.WebThread;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.command.CommandSender;
import org.bukkit.command.Command;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import fr.vmarchaud.mineweb.bukkit.methods.BukkitGetBannedPlayers;
import fr.vmarchaud.mineweb.bukkit.methods.BukkitGetMOTD;
import fr.vmarchaud.mineweb.bukkit.methods.BukkitGetMaxPlayers;
import fr.vmarchaud.mineweb.bukkit.methods.BukkitGetVersion;
import fr.vmarchaud.mineweb.bukkit.methods.BukkitGetWhitelistedPlayers;
import fr.vmarchaud.mineweb.common.ICore;
import fr.vmarchaud.mineweb.common.IMethod;
import fr.vmarchaud.mineweb.common.RequestHandler;
import fr.vmarchaud.mineweb.common.CommandScheduler;
import fr.vmarchaud.mineweb.common.configuration.PluginConfiguration;
import fr.vmarchaud.mineweb.common.configuration.ScheduledStorage;
import fr.vmarchaud.mineweb.common.injector.NettyInjector;
import fr.vmarchaud.mineweb.common.injector.router.RouteMatcher;
import fr.vmarchaud.mineweb.common.methods.CommonGetPlayerCount;
import fr.vmarchaud.mineweb.common.methods.CommonGetPlayerList;
import fr.vmarchaud.mineweb.common.methods.CommonGetSystemStats;
import fr.vmarchaud.mineweb.common.methods.CommonGetTimestamp;
import fr.vmarchaud.mineweb.common.methods.CommonIsConnected;
import fr.vmarchaud.mineweb.common.methods.CommonPluginType;
import fr.vmarchaud.mineweb.common.methods.CommonRunCommand;
import fr.vmarchaud.mineweb.common.methods.CommonScheduledCommand;
import fr.vmarchaud.mineweb.utils.CustomLogFormatter;
import fr.vmarchaud.mineweb.utils.http.HttpResponseBuilder;

public class BukkitCore extends JavaPlugin implements ICore {
	
	public static ICore		instance;
	public static ICore get() {
		return instance;
	}
	
	private RouteMatcher				httpRouter;
	private NettyInjector				injector;
	private WebThread					nettyServerThread;
	private HashMap<String, IMethod>	methods;
	private RequestHandler				requestHandler;
	private PluginConfiguration			config;
	private ScheduledStorage			storage;
	private CommandScheduler			commandScheduler;
	private BukkitTask					task;
	
	/** Cached player list to not rely on Reflection on every request **/
	private HashSet<String>				players;
	
	private Logger						logger		= Logger.getLogger("Mineweb");
	
	private Gson						gson 		= new GsonBuilder().enableComplexMapKeySerialization().serializeNulls().create();
	private FileHandler					fileHandler;

	@Override
	public void onEnable() {
		instance = this;
		getDataFolder().mkdirs();

		// load config
		config = PluginConfiguration.load(new File(getDataFolder(), "config.json"), instance);
		storage = ScheduledStorage.load(new File(getDataFolder(), "commands.json"), instance);
		// setup logger
		setupLogger();
		
		// Init
		logger.info("Loading ...");
		methods = new HashMap<String, IMethod>();
		players = new HashSet<String>();
		if (config.getPort() == null)
			injector = new BukkitNettyInjector(this);
		else
			nettyServerThread = new WebThread(this);
		httpRouter = new RouteMatcher();
		logger.info("Registering route ...");
		registerRoutes();
		getServer().getPluginManager().registerEvents(new BukkitListeners(instance), this);
		
		// inject when we are ready
		if (config.getPort() == null) {
			logger.info("Injecting http server ...");
			injector.inject();
		} else {
			logger.info("Start http server ...");
			try {
				nettyServerThread.run();
			} catch (Exception e) {
				logger.info("HTTP server start failed!");
			}
		}

		logger.info("Registering methods ...");
		requestHandler = new RequestHandler(instance);
		registerMethods();
		logger.info("Starting CommandScheduler ...");
		commandScheduler = new CommandScheduler(instance, storage);
		task = getServer().getScheduler().runTaskTimerAsynchronously(this, commandScheduler, 0, 100);
		logger.info("Ready !");
	}
	
	@Override
	public void onDisable() {
		if (task != null) task.cancel();
		if (commandScheduler != null) commandScheduler.save();
		if (logger != null) logger.info("Shutting down ...");
		if (fileHandler != null) fileHandler.close();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		if (cmd.getName().equalsIgnoreCase("mineweb")) {
			if (args.length == 1 && args[0].equalsIgnoreCase("reset")) {
				config = new PluginConfiguration(new File(getDataFolder(), "config.json"));
				config.save(instance);
				sender.sendMessage("MineWebBridge configuration reset!");
				logger.info("MineWebBridge configuration reset!");
				return true;
			}
			if (args.length == 2 && args[0].equalsIgnoreCase("port")) {
				config.setPort(Integer.parseInt(args[1]));
				config.save(instance);
				nettyServerThread = new WebThread(instance);
				logger.info("Try to start http server ...");
				try {
					nettyServerThread.run();
				} catch (Exception e) {
					sender.sendMessage("MineWebBridge port setup failed!");
					logger.info("HTTP server start failed!");
					return true;
				}
				sender.sendMessage("MineWebBridge port setup!");
				logger.info("MineWebBridge port setup!");
				return true;
			}
		}
		return false;
	}

	public void registerRoutes() {
		httpRouter.everyMatch((event) -> {
			logger.fine(String.format("[HTTP Request] %d %s on %s", event.getRes().getStatus().code(),
					event.getRequest().getMethod().toString(), event.getRequest().getUri()));
			return null;
		});
		
		httpRouter.get("/", (event) -> {
            return HttpResponseBuilder.ok();
        });
	}
	
	public void registerMethods() {
		// common methods
		methods.put("GET_PLAYER_LIST", new CommonGetPlayerList());
		methods.put("GET_PLAYER_COUNT", new CommonGetPlayerCount());
		methods.put("IS_CONNECTED", new CommonIsConnected());
		methods.put("GET_PLUGIN_TYPE", new CommonPluginType());
		methods.put("GET_SYSTEM_STATS", new CommonGetSystemStats());
		methods.put("RUN_COMMAND", new CommonRunCommand());
		methods.put("RUN_SCHEDULED_COMMAND", new CommonScheduledCommand());
		methods.put("GET_SERVER_TIMESTAMP", new CommonGetTimestamp());
		
		// bukkit methods
		methods.put("GET_BANNED_PLAYERS", new BukkitGetBannedPlayers());
		methods.put("GET_MAX_PLAYERS", new BukkitGetMaxPlayers());
		methods.put("GET_MOTD", new BukkitGetMOTD());
		methods.put("GET_VERSION", new BukkitGetVersion());
		methods.put("GET_WHITELISTED_PLAYERS", new BukkitGetWhitelistedPlayers());
		
	}
	
	public void setupLogger() {
		try {
			logger.setLevel(Level.parse(config.getLogLevel()));
			logger.setUseParentHandlers(false);
			new File(getDataFolder() + File.separator).mkdirs();
			fileHandler = new FileHandler(getDataFolder() + File.separator + "mineweb.log", true);
			fileHandler.setFormatter(new CustomLogFormatter());
			logger.addHandler(fileHandler);
		} catch (SecurityException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void runCommand(String command) {
		getServer().getScheduler().runTask(this, () -> {
			getServer().dispatchCommand(getServer().getConsoleSender(), command);
		});
	}

	@Override
	public RouteMatcher getHTTPRouter() {
		return httpRouter;
	}

	@Override
	public Object getPlugin() {
		return this;
	}

	@Override
	public EnumPluginType getType() {
		return EnumPluginType.BUKKIT;
	}

	@Override
	public Object getGameServer() {
		return this.getServer();
	}

	@Override
	public HashSet<String> getPlayers() {
		return players;
	}
	
	@Override
	public Logger logger() {
		return logger;
	}

	@Override
	public Gson gson() {
		return gson;
	}

	@Override
	public Map<String, IMethod> getMethods() {
		return methods;
	}

	@Override
	public PluginConfiguration config() {
		return config;
	}

	@Override
	public RequestHandler requestHandler() {
		return requestHandler;
	}

	@Override
	public CommandScheduler getCommandScheduler() {
		return commandScheduler;
	}
}
