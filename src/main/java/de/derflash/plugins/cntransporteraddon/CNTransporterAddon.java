package de.derflash.plugins.cntransporteraddon;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.concurrent.Callable;

import net.minecraft.server.v1_5_R2.EntityPlayer;
import net.minecraft.server.v1_5_R2.PlayerInteractManager;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.craftbukkit.v1_5_R2.CraftServer;
import org.bukkit.craftbukkit.v1_5_R2.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import com.frdfsnlght.transporter.LocalGateImpl;
import com.frdfsnlght.transporter.Servers;
import com.frdfsnlght.transporter.Transporter;
import com.frdfsnlght.transporter.api.API;
import com.frdfsnlght.transporter.api.LocalGate;
import com.frdfsnlght.transporter.api.RemoteServer;
import com.frdfsnlght.transporter.api.event.LocalGateClosedEvent;
import com.frdfsnlght.transporter.api.event.LocalGateOpenedEvent;
import com.frdfsnlght.transporter.api.event.RemoteServerDisconnectEvent;
import com.google.common.collect.Sets;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.MaxChangedBlocksException;
import com.sk89q.worldedit.blocks.BaseBlock;
import com.sk89q.worldedit.blocks.BlockID;
import com.sk89q.worldedit.bukkit.BukkitWorld;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

public class CNTransporterAddon extends JavaPlugin implements Listener {
	
	static CNTransporterAddon p;

	
    public void onEnable() {
    	CNTransporterAddon.p = this;
    	
    	ConfigurationSection config = getConfig().getConfigurationSection("gates");
    	if (config != null) {
    		for (String serverName : config.getKeys(false)) setStatus(false, serverName);
    	}
    	
        getServer().getPluginManager().registerEvents(this, this);
    	getServer().getScheduler().runTaskTimerAsynchronously(this, newAsyncGatesCheckThread(), 600L, 600L);
		Bukkit.getMessenger().registerOutgoingPluginChannel(this, "cnt_summon");
    }
    
    
    
    public void sendAllToFallbackServer() {
    	final String fallbackServer = getConfig().getString("fallbackServer");

    	getLogger().info("Sending all players to: " + fallbackServer);
		getServer().broadcastMessage(ChatColor.AQUA + "Du wirst nun zum CNa Hub teleportiert.");

		ByteArrayOutputStream b = new ByteArrayOutputStream();
		DataOutputStream out = new DataOutputStream(b);
		
		Transporter trp = getTransporterPlugin();
		if (trp == null) {
	    	getLogger().warning("Transporter not found?!");
			return;
		}
		String thisServer = trp.getConfig().getString("network.bungeeServer");
//		String thisServer = getConfig().getString("bungeeServerName");
		
		if (thisServer == null) {
	    	getLogger().warning("Transporter not set up correctly?!");
			return;
		}
		
		try {
			out.writeUTF("[");
		    out.writeUTF(thisServer);
		    out.writeUTF("@");
		    out.writeUTF(fallbackServer);
			out.writeUTF("]");
		} catch (IOException e) {
		    // Can never happen
		}
		
		getServer().sendPluginMessage(CNTransporterAddon.p, "cnt_summon", b.toByteArray());
	}
    
    public void restartServerIn5() {
    	getServer().getScheduler().runTaskLater(CNTransporterAddon.p, new Thread() {
			@Override
			public void run() {
	        	getServer().shutdown();
			}}, 5 * 20L);
    }
    
    public void shutdownServerIn5() {
    	getServer().getScheduler().runTaskLater(CNTransporterAddon.p, new Thread() {
			@Override
			public void run() {
				
	        	// somebody needs to run this command ;-)
    	        CraftServer cServer = (CraftServer) getServer();
    	        CraftWorld cWorld = (CraftWorld) getServer().getWorlds().get(0);
    	        EntityPlayer fakeEntityPlayer = new EntityPlayer(cServer.getHandle().getServer(), cWorld.getHandle(), "Restarter", new PlayerInteractManager(cWorld.getHandle()));
    	        Player restarter = (Player) fakeEntityPlayer.getBukkitEntity();
    	        
    	        restarter.performCommand("stopwrapper admin:blacna");

			}}, 5 * 20L);
    }
    
    public boolean onCommand(final CommandSender sender, Command command, String cmdLabel, String[] args) {
    	if (args.length == 0) {
    		sender.sendMessage("Transporter Addon v" + getDescription().getVersion());
    		return true;
    	}
    	
    	String subCmd = args[0];
        
        if (subCmd.equalsIgnoreCase("restart") && (!(sender instanceof Player) || sender.hasPermission("CNTransporter.restart"))) {
        	
        	// now
        	if (args.length > 0 && args[0].equalsIgnoreCase("!")) {
				sendAllToFallbackServer();
				restartServerIn5();
        		
	        // default
        	} else {
        		getServer().broadcastMessage(ChatColor.RED + "Achtung! Der Server wird in 30 Sekunden neu gestartet. Du wirst vorher automatisch zum CNa Hub teleportiert...");

            	getServer().getScheduler().runTaskLater(this, new Thread() { public void run() {
    					sendAllToFallbackServer();
    					restartServerIn5();
    			}}, 30*20L);

        	}
        	
        } else if (subCmd.equalsIgnoreCase("stop") && (!(sender instanceof Player) || sender.hasPermission("CNTransporter.halt"))) {
        	
        	if (args.length > 0 && args[0].equalsIgnoreCase("!")) {
				sendAllToFallbackServer();
				shutdownServerIn5();

        	} else { 
        		getServer().broadcastMessage(ChatColor.RED + "Achtung! Der Server wird in 30 Sekunden gestoppt. Du wirst vorher automatisch zum CNa Hub teleportiert...");
        		
            	getServer().getScheduler().runTaskLater(this, new Thread() { public void run() {
    					sendAllToFallbackServer();
    					shutdownServerIn5();
    			}}, 30*20L);
        	}
        	
        } else if (subCmd.equalsIgnoreCase("fallback") && (!(sender instanceof Player) || sender.hasPermission("CNTransporter.fallback"))) {
			sendAllToFallbackServer();

        } else if (subCmd.equalsIgnoreCase("autoon") && (!(sender instanceof Player) || sender.hasPermission("CNTransporter.autoon"))) {
        	if (args.length > 1)  setAutoOn(args[1], true, sender);
        	
        } else if (subCmd.equalsIgnoreCase("autooff") && (!(sender instanceof Player) || sender.hasPermission("CNTransporter.autooff"))) {
        	if (args.length > 1)  setAutoOn(args[1], false, sender);
        	
        }
        
        return true;
    }
    
    private void setAutoOn(String string, boolean status, CommandSender sender) {
		LocalGate foundGate = findLocalGate(string);
		if (foundGate != null) {
        	RemoteServer server = getRemoteServerForGate(foundGate);
			getConfig().set("gates." + server.getName() + ".auto", status);
    		sender.sendMessage("AutoOn for " + foundGate.getName() + ": " + status);
    		
    		saveConfig();
		} else {
    		sender.sendMessage("Gate not found: " + string);
		}
	}

	private LocalGate findLocalGate(String gateName) {
		Iterator<LocalGate> it = getTransporterAPI().getLocalGates().iterator();
		while (it.hasNext()) {
			LocalGate check = it.next();
			if (check.getName().equalsIgnoreCase(gateName)) return check;
		}
		return null;
	}

	public Transporter getTransporterPlugin() {
		Plugin p = getServer().getPluginManager().getPlugin("Transporter");
		if ((p != null) && p.isEnabled()) {
			return (Transporter) p;
		}
		return null;
    }
    
    public API getTransporterAPI() {
    	Transporter p = getTransporterPlugin();
		if (p != null) {
		    return p.getAPI();
		}
		return null;
    }
    
    private Thread newAsyncGatesCheckThread() {
    	return new Thread() {

			@Override
			public void run() {
				API api = getTransporterAPI();
				if (api != null) {
				    Iterator<LocalGate> gates = api.getLocalGates().iterator();
				    while (gates.hasNext()) {
				    	final LocalGateImpl gate = (LocalGateImpl) gates.next();
				    	if (gate.getLinks() == null || gate.getLinks().size() == 0) continue;
				    	if (gate.isOpen()) continue;

				    	String link = gate.getLinks().get(0);
				    	link = link.substring(0, link.indexOf("."));

				    	if (!getConfig().getBoolean("gates." + link + ".auto")) continue;
				    	
				    	//getLogger().info("Gate: " + gate.getName() + " Linked: " + gate.isLinked() + " Open: " + gate.isOpen() + " Autoopen: " + autoOpen);
				    	
				    	getLogger().info("(Re)Opening auto gate: " + gate.getName());
				    	try {
				    		getServer().getScheduler().callSyncMethod(CNTransporterAddon.p, new Callable<Object>() {
								public Object call() throws Exception {
									gate.open();
									return null;
								}
				    		}).get();
						} catch (Exception e) {
						}
				    	
				    }
				    
				}
			}
    		
    	};
    }
    
    public RemoteServer getRemoteServerForGate(LocalGate localGate) {
        for (String link : localGate.getLinks()) {
        	return Servers.getRemoteServer(link.substring(0, link.indexOf(".")));
        }
        return null;
    }
    
    @EventHandler
    public void onLocalGateOpenedEvent(LocalGateOpenedEvent event) {
    	RemoteServer server = getRemoteServerForGate(event.getLocalGate());
    	if (server != null) {
        	setStatus(true, server.getName());
        	newAsyncGatesCheckThread().start();
    	}
    }

    @EventHandler
    public void onLocalGateClosedEvent(LocalGateClosedEvent event) {
    	RemoteServer server = getRemoteServerForGate(event.getLocalGate());
    	if (server != null) {
        	setStatus(false, server.getName());
        	newAsyncGatesCheckThread().start();
    	}
    }

    @EventHandler
    public void onRemoteServerDisconnectEvent(RemoteServerDisconnectEvent event) {
    	RemoteServer server = event.getRemoteServer();
    	
		Iterator<LocalGate> it = getTransporterAPI().getLocalGates().iterator();
		while (it.hasNext()) {
			LocalGateImpl check = (LocalGateImpl) it.next();
			if (check.getLinks() != null && check.getLinks().size() > 0 && check.getLinks().get(0).startsWith(server.getName())) {
				check.close();
			}
		}
    }
    
    public synchronized void setStatus(boolean status, String serverName) {
    	getLogger().info("Setting gate status for " + serverName + ": " + status);

    	ConfigurationSection config = getConfig().getConfigurationSection("gates." + serverName);
    	if (config == null) return;
    	
    	String worldName = config.getString("world");
    	String portalRegionName = config.getString("portalRegion");
    	String labelRegionName = config.getString("labelRegion");
    	String portalTitle = config.getString("portalTitle");
    	
    	if (worldName == null || portalRegionName == null || labelRegionName == null) {
    		getLogger().warning("Error in config");
    		return;
    	}

		Plugin p = getServer().getPluginManager().getPlugin("WorldGuard");
		if (p == null || !p.isEnabled()) {
    		getLogger().warning("WorldGuard not found");
    		return;
		}
		
		World world = Bukkit.getWorld(worldName);
		WorldGuardPlugin wg = ((WorldGuardPlugin)p);
    	RegionManager rm = wg.getRegionManager(world);
		EditSession session = new EditSession(new BukkitWorld(world), 1500000);
    	
		ProtectedRegion labelRegion = rm.getRegion(labelRegionName);
		CuboidRegion labelRegionC = new CuboidRegion(labelRegion.getMinimumPoint(), labelRegion.getMaximumPoint());

		ProtectedRegion portalRegion = rm.getRegion(portalRegionName);
		if (portalRegion == null) {
    		getLogger().warning("WorldGuard region '" + portalRegionName + "' not found");
    		return;
		}
		
		CuboidRegion portalRegionC = new CuboidRegion(portalRegion.getMinimumPoint(), portalRegion.getMaximumPoint());
		try {
			session.replaceBlocks(labelRegionC, Sets.newHashSet(new BaseBlock(status ? 152 : 133)), new BaseBlock(status ? 133 : 152));

			session.replaceBlocks(labelRegionC, Sets.newHashSet(new BaseBlock(status ? BlockID.BEDROCK : BlockID.REDSTONE_LAMP_ON)), new BaseBlock(status ? BlockID.REDSTONE_LAMP_ON : BlockID.BEDROCK));
			session.replaceBlocks(labelRegionC, Sets.newHashSet(new BaseBlock(status ? 88 : 89)), new BaseBlock(status ? 89 : 88));

			session.replaceBlocks(portalRegionC, Sets.newHashSet(new BaseBlock(status ? BlockID.BEDROCK : BlockID.REDSTONE_LAMP_ON)), new BaseBlock(status ? BlockID.REDSTONE_LAMP_ON : BlockID.BEDROCK));
			session.replaceBlocks(portalRegionC, Sets.newHashSet(new BaseBlock(status ? 88 : 89)), new BaseBlock(status ? 89 : 88));
			
			session.flushQueue();
			
		} catch (MaxChangedBlocksException e) {
			e.printStackTrace();
		}
		
		getServer().broadcastMessage(ChatColor.AQUA + "Portal " + (status ? ChatColor.GREEN + "geöffnet" : ChatColor.RED + "geschlossen") + ChatColor.AQUA + ": " + ChatColor.DARK_AQUA + portalTitle);

    }
    
}

