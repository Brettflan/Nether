package org.innectis.Nether;

import java.util.*;
import java.util.Map.Entry;

import org.bukkit.block.Block;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;
import org.bukkit.World;
import org.bukkit.World.Environment;

/**
 * Innectis Nether plugin class
 *
 * Features:
 *   Single-player style Nether portals
 *
 * @author Innectis
 */
public class NetherMain extends JavaPlugin
{
	private final NetherPlayerListener playerListener = new NetherPlayerListener(this);
	private Integer playerMonitor = null;

	// track players who are currently standing in exit portals, so we know not to teleport them again until after they step out
	public Set<String> playersInPortals = Collections.synchronizedSet(new HashSet<String>());
	// timers for players standing in entrance portals, for (optional) delayed portal triggering
	public Map<String, Integer> teleportTimers = Collections.synchronizedMap(new HashMap<String, Integer>());

	public static String worldName;		// Nether world name/folder... default = "netherworld"
	public static int ratio;			// compression ratio of Nether world vs. normal world... default = 8
	public static boolean debug;		// whether to output debug info to console... default = false
	public static int delay;			// delay (in seconds) from player stepping into a portal and it actually triggering... default = 5
	public static boolean showEntry;	// whether to show below portal entry text... default = true
	public static String entryText;		// text shown to player when they step into a portal
	public static boolean showExit;		// whether to show below portal exit text... default = true
	public static String exitText;		// text shown to player when they have just gone through a portal
	
	public void onEnable()
	{
		Configuration	properties = this.getConfiguration();

		// Check the config.yml
		worldName = properties.getString("nether-world-name");
		ratio = properties.getInt("compression-ratio", 8);
		debug = properties.getBoolean("debug-output", false);
		delay = properties.getInt("portal-delay-seconds", 5);
		showEntry = properties.getBoolean("show-entry-text", true);
		entryText = properties.getString("portal-entry-text");
		showExit = properties.getBoolean("show-exit-text", true);
		exitText = properties.getString("portal-exit-text");
		if((worldName == null) || worldName.isEmpty()){
			worldName = "netherworld";
			properties.setProperty("nether-world-name", worldName);
			properties.setProperty("compression-ratio", 8);
			properties.setProperty("debug-output", false);
			properties.setProperty("portal-delay-seconds", 5);
			properties.setProperty("show-entry-text", true);
			properties.setProperty("portal-entry-text", "You feel the pull of nether energies.");
			properties.setProperty("show-exit-text", true);
			properties.setProperty("portal-exit-text", "The portal has taken you... elsewhere.");
			properties.save();
		}

		// Register events
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener, Priority.Normal, this);
		pm.registerEvent(Event.Type.PLAYER_RESPAWN, playerListener, Priority.High, this);
		
		if (playerMonitor == null)
			playerMonitor = getServer().getScheduler().scheduleSyncRepeatingTask(this, new NetherPortalMonitor(), 10, 10);

		// Say hi
		PluginDescriptionFile pdfFile = this.getDescription();
		System.out.println(pdfFile.getName() + " v" + pdfFile.getVersion() + " enabled");

		// Go ahead and preload netherworld now
		getServer().createWorld(worldName, Environment.NETHER);
		System.out.println(pdfFile.getName() + ": preloaded nether world [" + worldName + "]");
	}

	public void onDisable() {
		// let's clean up after ourselves
		getServer().getScheduler().cancelTask(playerMonitor);
		playerMonitor = null;
		playersInPortals.clear();
		for (Entry<String, Integer> tt : teleportTimers.entrySet())
		{
			Integer timer = tt.getValue();
			if (timer != null && timer >= 0)
				getServer().getScheduler().cancelTask(tt.getValue());
		}
		teleportTimers.clear();
	}



	public boolean playerInPortal(String player) {
		return playersInPortals.contains(player);
	}

	public void playerPortaled(String player) {
		CancelTimer(player);
		playersInPortals.add(player);
	}

	public void CancelTimer(String player)
	{
		// using "Integer" instead of "int" since returned value might be null
		Integer timer = teleportTimers.remove(player);
		if (timer == null)
			return;
		getServer().getScheduler().cancelTask(timer);
	}

	// this task runs every few ticks, clearing players from playersInPortals set as needed
	private class NetherPortalMonitor implements Runnable
	{
		public void run() {
			if (playersInPortals.isEmpty())
				return;

			// loop through list of players
			for (Iterator<String> p = playersInPortals.iterator(); p.hasNext();)
			{
				String playerName = p.next();
				Player player = getServer().getPlayer(playerName);
				if (player == null || !player.isOnline() || player.getLocation() == null)
				{
					// player of specified name isn't accessible; maybe logged off
					p.remove();
					continue;
				}
				Block b = player.getLocation().getBlock();
				if (!b.getType().equals(Material.PORTAL))
				{
					// player is no longer standing in portal
					p.remove();
					continue;
				}
			}
		}
	}

	public class TeleportTimer implements Runnable
	{
		private long started;		// time this task was initiated
		private long delay;			// how long to delay the teleport
		public Player player;		// the person doing the teleport
		private NetherMain main;	// pointer to the main plugin

		public TeleportTimer(Player player, long delay, NetherMain plugin)
		{
			this.started = Calendar.getInstance().getTimeInMillis();
			this.delay = delay;
			this.player = player;
			this.main = plugin;
		}

		public void run()
		{
			if (player == null || !player.isOnline() || player.getLocation() == null)
			{
				// player of specified name isn't accessible; maybe logged off
				playersInPortals.remove(player.getName());
				main.CancelTimer(player.getName());
				return;
			}
			else if (!player.getLocation().getBlock().getType().equals(Material.PORTAL))
			{
				// player is no longer standing in portal
				playersInPortals.remove(player.getName());
				main.CancelTimer(player.getName());
				return;
			}

			long now = Calendar.getInstance().getTimeInMillis();
			if (now > started + delay)
			{  // it's go time
				GoThroughPortal(player);
				main.CancelTimer(player.getName());
			}
			//else  // uncomment for timing debug
			//	player.sendMessage("ms: " + (started + delay - now));

		}
	}
	public Location EnterPortal(Player player)
	{
		// add player to list of people who are in the process of portaling from one world to another
		playerPortaled(player.getName());

		if (showEntry && !entryText.isEmpty())
			player.sendMessage(ChatColor.LIGHT_PURPLE + entryText);

		if (delay > 0)
		{
			int timer = getServer().getScheduler().scheduleSyncRepeatingTask(this, new TeleportTimer(player, (delay * 1000), this), 10, 10);
			teleportTimers.put(player.getName(), timer);
			return null;
		}
		else
			return GoThroughPortal(player);
	}

	public Location GoThroughPortal(Player player)
	{
		Location loc = player.getLocation();
		World world = loc.getWorld();

		int locX = loc.getBlockX();
		int locY = loc.getBlockY();
		int locZ = loc.getBlockZ();

		if (debug)
			System.out.println("NETHER_PLUGIN: " + player.getName() + " just entered a portal at: " + locX + ", " + locY + ", "+ locZ);

		// For better mapping between nether and normal, always use the lowest
		// xyz portal block
		while (world.getBlockAt(locX - 1, locY, locZ).getType().equals(Material.PORTAL))
			--locX;
		while (world.getBlockAt(locX, locY - 1, locZ).getType().equals(Material.PORTAL))
			--locY;
		while (world.getBlockAt(locX, locY, locZ - 1).getType().equals(Material.PORTAL))
			--locZ;

		if (debug)
			System.out.println("NETHER_PLUGIN: " + player.getName() + ": Using portal block: " + locX + ", " + locY + ", " + locZ);

		// Now check to see which way the portal is oriented.
		boolean orientX = world.getBlockAt(locX + 1, locY, locZ).getType().equals(Material.PORTAL);

		if (debug) {
			if (orientX)
				System.out.println("NETHER_PLUGIN: " + player.getName() + ": Portal is X oriented.");
			else
				System.out.println("NETHER_PLUGIN: " + player.getName() + ": Portal is Y oriented.");
		}

		if (world.getEnvironment().equals(Environment.NORMAL)) {
			// First of all see if there IS a nether yet

			String netherName = worldName;
			// Here we use "netherworld"
			if((netherName == null) || netherName.isEmpty()) netherName = "netherworld";

			World nether = getServer().getWorld(netherName);
			if (nether == null) {
				player.sendMessage(ChatColor.RED + "Loading world " + netherName + ", please wait.");
				nether = getServer().createWorld(netherName, Environment.NETHER);
			}

			if (!nether.getEnvironment().equals(Environment.NETHER)) {
				// Don't teleport to a non-nether world
				System.out.println("NETHER_PLUGIN: " + player.getName() + ": ERROR: Nether world not found, aborting transport.");
				return null;
			}

			int locAdjX = (locX >= 0) ? (locX / ratio) : (((locX + 1) / ratio) - 1);
			int locAdjZ = (locZ >= 0) ? (locZ / ratio) : (((locZ + 1) / ratio) - 1);

			// Try to find a portal near where the player should land
			Block dest = nether.getBlockAt(locAdjX, locY, locAdjZ);
			NetherPortal portal = NetherPortal.findPortal(dest, 1, player.getName());
			if (portal == null) {
				// check the other portal column, just in case
				if (orientX)
					locAdjX = (locX + 1 >= 0) ? ((locX + 1) / ratio) : (((locX + 2) / ratio) - 1);
				else
					locAdjZ = (locZ + 1 >= 0) ? ((locZ + 1) / ratio) : (((locZ + 2) / ratio) - 1);

				Block dest2 = nether.getBlockAt(locAdjX, locY, locAdjZ);
				if (dest2 != dest)
					portal = NetherPortal.findPortal(dest2, 1, player.getName());

				// still no matching portal found? OK, we'll create one
				if (portal == null) {
					portal = NetherPortal.createPortal(dest, orientX);
				}
			}

			// Go!
			Location spawn = portal.getSpawn(player.getLocation().getYaw());
			nether.loadChunk(spawn.getBlock().getChunk());
			ProcessMoveTo(player, spawn);
			return spawn;

		} else if (world.getEnvironment().equals(Environment.NETHER)) {
			// For now just head to the first normal world there.
			ListIterator<World> worldIterator = getServer().getWorlds().listIterator();
			World normal = null;
			while (worldIterator.hasNext())
			{
				normal = worldIterator.next();
				if (normal.getEnvironment().equals(Environment.NORMAL))
					break;
				normal = null;
			}

			if (normal == null) {
				// Don't teleport to a non-normal world
				System.out.println("NETHER_PLUGIN: " + player.getName() + ": ERROR: Normal world not found, aborting transport.");
				return null;
			}

			// Try to find a portal near where the player should land
			Block dest = normal.getBlockAt(locX * ratio, locY, locZ * ratio);
			NetherPortal portal = NetherPortal.findPortal(dest, ratio, player.getName());
			if (portal == null) {
				// check the other portal column, just in case
				Block dest2;
				if (orientX)
					dest2 = normal.getBlockAt((locX + 1) * ratio, locY, locZ * ratio);
				else
					dest2 = normal.getBlockAt(locX * ratio, locY, (locZ + 1) * ratio);
				portal = NetherPortal.findPortal(dest2, ratio, player.getName());

				// still no matching portal found? OK, we'll create one
				if (portal == null) {
					portal = NetherPortal.createPortal(dest, orientX);
				}
			}

			// Go!
			Location spawn = portal.getSpawn(player.getLocation().getYaw());
			normal.loadChunk(spawn.getBlock().getChunk());
			ProcessMoveTo(player, spawn);
			return spawn;
		}
		else
			return null;
	}

	public void ProcessMoveTo(Player player, Location location)	{
		String nameWorld = (location.getWorld().getEnvironment().equals(Environment.NETHER)) ? "NETHER" : "NORMAL";

		System.out.println("NETHER_PLUGIN: " + player.getName() + " used portal at (" +
				player.getLocation().getBlockX() + ", " + player.getLocation().getBlockY() + ", " + player.getLocation().getBlockZ() +
				") to " + nameWorld + " world (" +
				location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")");

		if (showExit && !exitText.isEmpty())
			player.sendMessage(ChatColor.DARK_PURPLE + exitText);

		player.teleportTo(location);
	}
}
