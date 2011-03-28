package org.innectis.Nether;

import java.util.ListIterator;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.bukkit.World.Environment;

public class NetherPlayerListener extends PlayerListener {

	private NetherMain main;
	
	public NetherPlayerListener(NetherMain plugin) {
		main = plugin;
	}
	
	@Override
	public void onPlayerRespawn(PlayerRespawnEvent event) {		
		// Return nether-deaths to normal world
		if (event.getRespawnLocation().getWorld().getEnvironment().equals(Environment.NETHER)) {
			// For now just head to the first world there.
			World normal = main.getServer().getWorlds().get(0);
			if (!normal.getEnvironment().equals(Environment.NORMAL)) {
				// Don't teleport to a non-normal world
				return;
			}
			
			Location respawnLocation = normal.getSpawnLocation();
			System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + " respawns to normal world");
			event.setRespawnLocation(respawnLocation);
		}
	}
	
	@Override
	public void onPlayerMove(PlayerMoveEvent event) {
		Location loc = event.getTo();
		Block b = loc.getBlock();
		Player player = event.getPlayer();

		if (!b.getType().equals(Material.PORTAL) || main.playerInPortal(player.getName())) {
			// Not a portal, or player is still standing in a portal from earlier.
			return;
		}

		Location newLoc = main.EnterPortal(player);

		// if delay is 0, we need to do event.setTo() to keep it from causing a "player moved wrongly" error
		if (newLoc != null)
			event.setTo(newLoc);
	}
}
