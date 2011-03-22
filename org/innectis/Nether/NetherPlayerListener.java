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
	private static final boolean DEBUG = false;
	
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
		World world = loc.getWorld();
		Block b = loc.getBlock();

		if (!b.getType().equals(Material.PORTAL) || main.playerInPortal(event.getPlayer().getName())) {
			// Not a portal, or player is still standing in exit portal.
			return;
		}

		// add player to list of people who are in exit portals (or are in the process of portaling from one world to another)
		main.playerPortaled(event.getPlayer().getName());

		// player.getVelocity() sadly doesn't return movement vector, just gravity effect; so, we'll roll our own movement vector
		Vector velocity = new Vector(loc.getX() - event.getFrom().getX(), loc.getY() - event.getFrom().getY(), loc.getZ() - event.getFrom().getZ());

		int locX = loc.getBlockX();
		int locY = loc.getBlockY();
		int locZ = loc.getBlockZ();

		if (DEBUG)
			System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": Hit portal at " + locX + ", "+ locZ);

		// For better mapping between nether and normal, always use the lowest
		// xyz portal block
		while (world.getBlockAt(locX - 1, locY, locZ).getType().equals(Material.PORTAL))
			--locX;
		while (world.getBlockAt(locX, locY - 1, locZ).getType().equals(Material.PORTAL))
			--locY;
		while (world.getBlockAt(locX, locY, locZ - 1).getType().equals(Material.PORTAL))
			--locZ;

		if (DEBUG)
			System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": Using portal block:" + locX + ", " + locY + ", " + locZ);

		// Now check to see which way the portal is oriented.
		boolean orientX = world.getBlockAt(locX + 1, locY, locZ).getType().equals(Material.PORTAL);

		if (DEBUG) {
			if (orientX)
				System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": Portal is X oriented.");
			else
				System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": Portal is Y oriented.");
		}

		if (world.getEnvironment().equals(Environment.NORMAL)) {
			// First of all see if there IS a nether yet

			String netherName = main.worldName;
			// Here we use "netherworld"
			if((netherName == null) || netherName.isEmpty()) netherName = "netherworld";

			World nether = main.getServer().getWorld(netherName);
			if (nether == null) {
				event.getPlayer().sendMessage(ChatColor.RED + "First load of world " + netherName + ", please wait.");
				nether = main.getServer().createWorld(netherName, Environment.NETHER);
			}

			if (!nether.getEnvironment().equals(Environment.NETHER)) {
				// Don't teleport to a non-nether world
				System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": ERROR: Nether world not found, aborting transport.");
				return;
			}

			int locAdjX = (locX >= 0) ? (locX / main.ratio) : (((locX + 1) / main.ratio) - 1);
			int locAdjZ = (locZ >= 0) ? (locZ / main.ratio) : (((locZ + 1) / main.ratio) - 1);

			// Try to find a portal near where the player should land
			Block dest = nether.getBlockAt(locAdjX, locY, locAdjZ);
			NetherPortal portal = NetherPortal.findPortal(dest, 1, event.getPlayer().getName());
			if (portal == null) {
				// check the other portal column, just in case
				if (orientX)
					locAdjX = (locX + 1 >= 0) ? (locX + 1 / main.ratio) : (((locX + 2) / main.ratio) - 1);
				else
					locAdjZ = (locZ + 1 >= 0) ? (locZ + 1 / main.ratio) : (((locZ + 2) / main.ratio) - 1);
				dest = nether.getBlockAt(locAdjX, locY, locAdjZ);
				portal = NetherPortal.findPortal(dest, 1, event.getPlayer().getName());

				// still no matching portal found? OK, we'll create one
				if (portal == null) {
					portal = NetherPortal.createPortal(dest, orientX);
				}
			}

			// Go!
			Location spawn = portal.getSpawn(event.getPlayer().getLocation().getYaw(), velocity);
			nether.loadChunk(spawn.getBlock().getChunk());
			ProcessMoveTo(event.getPlayer(), spawn);
			event.setTo(spawn);

		} else if (world.getEnvironment().equals(Environment.NETHER)) {
			// For now just head to the first normal world there.
			ListIterator<World> worldIterator = main.getServer().getWorlds().listIterator();
			World normal = null;
			while (worldIterator.hasNext())
			{
				normal = worldIterator.next();
				if (normal.getEnvironment().equals(Environment.NORMAL))
					break;
				normal = null;	
			}

			if (null == normal) {
				// Don't teleport to a non-normal world
				System.out.println("NETHER_PLUGIN: " + event.getPlayer().getName() + ": ERROR: Normal world not found, aborting transport.");
				return;
			}

			// Try to find a portal near where the player should land
			Block dest = normal.getBlockAt(locX * main.ratio, locY, locZ * main.ratio);
			NetherPortal portal = NetherPortal.findPortal(dest, main.ratio, event.getPlayer().getName());
			if (portal == null) {
				// check the other portal column, just in case
				if (orientX)
					dest = normal.getBlockAt((locX + 1) * main.ratio, locY, locZ * main.ratio);
				else
					dest = normal.getBlockAt(locX * main.ratio, locY, (locZ + 1) * main.ratio);
				portal = NetherPortal.findPortal(dest, 1, event.getPlayer().getName());

				// still no matching portal found? OK, we'll create one
				if (portal == null) {
					portal = NetherPortal.createPortal(dest, orientX);
				}
			}

			// Go!
			Location spawn = portal.getSpawn(event.getPlayer().getLocation().getYaw(), velocity);
			normal.loadChunk(spawn.getBlock().getChunk());
			ProcessMoveTo(event.getPlayer(), spawn);
			event.setTo(spawn);
		}
	}

	public void ProcessMoveTo(Player player, Location location)	{
		if (location.getWorld().getEnvironment().equals(Environment.NETHER))
			System.out.println("NETHER_PLUGIN: " + player.getName() + " used portal to NETHER world");
		else
			System.out.println("NETHER_PLUGIN: " + player.getName() + " used portal to NORMAL world");

		player.teleportTo(location);
	}
}
