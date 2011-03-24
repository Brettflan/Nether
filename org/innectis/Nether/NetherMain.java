package org.innectis.Nether;

import java.util.*;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.Material;
import org.bukkit.plugin.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;

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
	private int PlayerMonitor = -1;

	// track players who are currently standing in exit portals, so we know not to teleport them again until after they step out
	public Set<String> playersInPortals = Collections.synchronizedSet(new HashSet<String>());

	public static String worldName;		// Nether world name/folder... default = "netherworld"
	public static int ratio;			// compression ratio of Nether world vs. normal world... default = 8
	public static boolean debug;		// whether to output debug info to console
	
	public void onEnable()
	{
		Configuration	properties = this.getConfiguration();

		// Check the config.yml
		worldName = properties.getString("nether-world-name");
		ratio = properties.getInt("compression-ratio", 8);
		debug = properties.getBoolean("debug-output", false);
		if((worldName == null) || worldName.isEmpty()){
			properties.setProperty("nether-world-name", "netherworld");
			properties.setProperty("compression-ratio", 8);
			properties.setProperty("debug-output", false);
			properties.save();
		}

		// Register events
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvent(Event.Type.PLAYER_MOVE, playerListener, Priority.Normal, this);
		pm.registerEvent(Event.Type.PLAYER_RESPAWN, playerListener, Priority.High, this);
		
		if (PlayerMonitor == -1)
			PlayerMonitor = getServer().getScheduler().scheduleSyncRepeatingTask(this, new NetherPortalMonitor(), 10, 10);

		// Say hi
		PluginDescriptionFile pdfFile = this.getDescription();
		System.out.println(pdfFile.getName() + " v" + pdfFile.getVersion() + " enabled");
	}

	public void onDisable() {
		getServer().getScheduler().cancelTask(PlayerMonitor);
		PlayerMonitor = -1;
	}

	public boolean playerInPortal(String player) {
		return playersInPortals.contains(player);
	}

	public void playerPortaled(String player) {
		playersInPortals.add(player);
	}

	// this task runs every few ticks, clearing players from playersInPortals set as needed
	class NetherPortalMonitor implements Runnable
	{
//		boolean tick = false;	// timing debug

		public void run() {
			if (playersInPortals.isEmpty())
				return;

//			tick = !tick;

			// loop through list of players
			for (Iterator<String> i = playersInPortals.iterator(); i.hasNext();)
			{
				String playerName = i.next();
				Player player = getServer().getPlayer(playerName);
				if (player == null)
				{
					// player of specified name doesn't exist; maybe logged off
					i.remove();
					continue;
				}
				Block b = player.getLocation().getBlock();
				if (!b.getType().equals(Material.PORTAL))
				{
					// player is no longer standing in portal
					i.remove();
					continue;
				}

				// debug message for timing, sent to player standing in portal
//				player.sendMessage(tick ? "tick..." : "...tock");
			}
		}
	}
}
