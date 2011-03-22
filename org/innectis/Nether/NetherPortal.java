package org.innectis.Nether;

import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.bukkit.World.Environment;

import java.util.ArrayList;

public class NetherPortal {
	private static final boolean DEBUG = false;
	
	private Block block;
	
	public NetherPortal(Block b) {
		block = b;
	}
	
	public Block getBlock() {
		return block;
	}
	
	public void setBlock(Block b) {
		block = b;
	}

	// Return a spawnable location
	public Location getSpawn(float yaw, Vector velocity) {
		// offset based on appropriate exit direction; remains barely inside portal at 0.19 away from center (which is 0.5), far enough for view to be unobscured
		double offsetVelocity = 0.69;  // default for west(z+)/south(x+) offset
		double offsetCenter = 1.0;  // for center of portal, in middle of the 2 portal block columns

		if (block.getRelative(1, 0, 0).getType().equals(Material.PORTAL) ||
				block.getRelative(-1, 0, 0).getType().equals(Material.PORTAL)) {
			// portal is in X direction
			if (velocity.getZ() < 0)
				offsetVelocity = 0.31;  // offset east(z-)
			if (block.getRelative(-1, 0, 0).getType().equals(Material.PORTAL))
				offsetCenter = 0;
			return new Location(block.getWorld(), block.getX() + offsetCenter,
					block.getY(), block.getZ() + offsetVelocity, yaw, 0);
		} else {
			// portal is in Z direction
			if (velocity.getX() < 0)
				offsetVelocity = 0.31;  // offset north(x-)
			if (block.getRelative(0, 0, -1).getType().equals(Material.PORTAL))
				offsetCenter = 0;
			return new Location(block.getWorld(), block.getX() + offsetVelocity,
					block.getY(), block.getZ() + offsetCenter, yaw, 0);
		}
	}

	// Output a debug representation of the search for a portal block
	public static void logSearch(char[][] a, int searchDistance, String playerName) {
		if (DEBUG) {
			for (int y = searchDistance - 1; y >= 0; --y) {
				String line = "";
				for (int x = 0; x < searchDistance; ++x) {
					if (a[x][y] != ' ')
						line += a[x][y];
					else
						line += '.';
				}
				System.out.println("NETHER_PLUGIN: " + playerName + ": " + line);
			}
		}
	}
	
	// ==============================
	// Check a column for portal blocks, starting with the players location
	public static NetherPortal checkCol(World world, int x, int y, int z) {
		// Portals are 3 blocks tall, so we only need to check every few blocks.
		// Start at the user's height and go outward.
		int d = z - 1;
		int u = z + 1;
		Block b = null;

		while (d >= 0 || u <= 127) {
			if (d >= 0)	{
				b = world.getBlockAt(x, d, y);
				if (b.getType().equals(Material.PORTAL)) {
					return new NetherPortal(b);
				}
				d -= 3;
			}
			if (u <= 127) {
				b = world.getBlockAt(x, u, y);
				if (b.getType().equals(Material.PORTAL)) {
					return new NetherPortal(b);
				}
				u += 3;
			}
		}

		return null;
	}

	// ==============================
	// Check for nearby portal within specified search distance
	// Should return nearest first.
	public static NetherPortal findPortal(Block dest, int searchDistance, String playerName) {
		World world = dest.getWorld();
		
		int startX = dest.getX();		
		int startY = dest.getZ();		
		int startZ = dest.getY();

		int x = (searchDistance / 2), y = (searchDistance / 2);
		
		// Check middle block first
		NetherPortal np = checkCol(world, startX + x, startY + y, startZ);

		// Going IN to the nether, the search distance should be 1, and if
		// there's already a portal,
		// it will occupy this block.
		if (searchDistance < 2 || null != np)
			return np;

		// Since a portal is 2 blocks wide, we only need to
		// check every other column.  We'll flip this flag
		// after each check.
		// We can't skip checks on the outer edge of the 8x8 block
		boolean checkColumn = false;
		
		// Start in the middle and loop outward.
		//
		//  8
		// [^][6][>][>][>][>][>][6]
		// [^][^][4][>][>][>][4][v]
		// [^][^][^][2][>][2][v][v]
		// [^][^][^][^][0][v][v][v]
		// [^][^][^][1][1][v][v][v]
		// [^][^][3][<][<][3][v][v]
		// [^][5][<][<][<][<][5][v]
		// [7][<][<][<][<][<][<][7]

		char[][] c;
		if (DEBUG) {
			c = new char[searchDistance][searchDistance];
			c[x][y] = 'S';
			
			System.out.println("NETHER_PLUGIN: " + playerName + ": Starting portal search at (" + (x + startX) + ", " + (y + startY) + ").");
		}

		int sign = -1;
		for (int n = 1; n <= searchDistance; ++n) {
			// go in [sign] direction along the y axis [n] times
			// go in [sign] direction along the x axis [n] times
			// reverse [sign] and increment [n]
			for (int xy = 0; xy < 2; ++xy) {
				for (int i = 1; i <= n; ++i) {
					if (0 == xy)
						y += sign;
					else
						x += sign;

					// If we're on the outer edge of the search space, we have to check every single column, 
					// since the portal could be straddled across two search spaces.
					if (0 == x || (searchDistance-1) == x || 0 == y || (searchDistance-1) == y)
						checkColumn = true;
					
					if (checkColumn)
						np = checkCol(world, x + startX, y + startY, startZ);
					
					
					if (null != np) {
						if (DEBUG) {
							c[x][y] = 'X';
							logSearch(c, searchDistance, playerName);
						}
						return np;
					}

					if (DEBUG) {
						if (checkColumn) {							
							if (0 == xy) {
								if (sign < 0)
									c[x][y] = 'v';
								else
									c[x][y] = '^';
							} else {
								if (sign < 0)
									c[x][y] = '<';
								else
									c[x][y] = '>';
							}
						}
						else
							c[x][y] = ' ';
					}
					
					// I flip my bits back and forth, I flip my bits back and forth, I flip
					// my bits back and forth, I flip my bits back and forth, I flip my bits
					// back and forth
					// When we're on the outer edge of the search space we'll manually set this
					// value to true.
					checkColumn = !checkColumn;
		
					// Because we start going down, left, up, right, we'll
					// always end traveling along the y axis
					// on the first iteration where n == searchDistance and
					// we'll only need to travel n-1 blocks
					if (0 == xy && n == searchDistance && i + 1 == n) {
						if (DEBUG)
							logSearch(c, searchDistance, playerName);

						// Didn't find a portal
						return null;
					}
				}
			}

			sign *= -1;
		}
		
		if (DEBUG)
			logSearch(c, searchDistance, playerName);

		// Didn't find a portal
		return null;
	}
	
	// Create a new portal at the specified block, fudging position if needed
	// Will occasionally end up making portals in bad places, but let's hope not
	public static NetherPortal createPortal(Block dest, boolean orientX) {
		World world = dest.getWorld();

		// Not too high or too low overall
		if (dest.getY() > 120) {
			dest = world.getBlockAt(dest.getX(), 120, dest.getZ());
		} else if (dest.getY() < 8) {
			dest = world.getBlockAt(dest.getX(), 8, dest.getZ());
		}

		// Search for an area along the y axis that is suitable.
		// Will check nearest blocks to dest first.
		
		Block		checkBlock, chosenBlock = dest;
		int			quality, chosenQuality = 0;

		for(int y1 = dest.getY(), y2 = dest.getY(); (y1 > 8) || (y2 < 120); --y1, ++y2){
			// Look below.
			if(y1 > 8){
				checkBlock = world.getBlockAt(dest.getX(), y1, dest.getZ());
				quality = checkPortalQuality(checkBlock, orientX);

				if(quality > chosenQuality){
					chosenQuality = quality;
					chosenBlock = checkBlock;

					if(quality >= 28) break;
				}
			}

			// Look above.
			if(y2 < 120) if(y2 != y1){
				checkBlock = world.getBlockAt(dest.getX(), y2, dest.getZ());
				quality = checkPortalQuality(checkBlock, orientX);

				if(quality > chosenQuality){
					chosenQuality = quality;
					chosenBlock = checkBlock;

					if(quality >= 28) break;
				}
			}
		}

		dest = chosenBlock;

		// Create the physical portal
		int x = dest.getX(), y = dest.getY(), z = dest.getZ();
		
		ArrayList<Block> columns = new ArrayList<Block>();
		for (int x2 = x - 4; x2 <= x + 5; ++x2) {
			for (int z2 = z - 4; z2 <= z + 5; ++z2) {
				int dx = x - x2, dz = z - z2;
				if (dx * dx + dz * dz < 12) {
					columns.add(world.getBlockAt(x2, 0, z2));
				}
			}
		}
		
		// Clear area around portal
		for (Block col : columns) {
			// Stone platform, if needed.
			checkBlock = world.getBlockAt(col.getX(), y - 1, col.getZ());
			if(!canStand(checkBlock.getTypeId())){
				if(world.getEnvironment() == Environment.NETHER) checkBlock.setType(Material.NETHERRACK);
				else checkBlock.setType(Material.STONE);
			}

			// Air pocket.
			for (int yd = 0; yd < 4; ++yd) {
				checkBlock = world.getBlockAt(col.getX(), y + yd, col.getZ());
				if(!canBreathe(checkBlock.getTypeId())) checkBlock.setType(Material.AIR);
			}

			// Roof, if needed.
			checkBlock = world.getBlockAt(col.getX(), y + 3, col.getZ());
			if(canFall(checkBlock.getTypeId())){
				if(world.getEnvironment() == Environment.NETHER) checkBlock.setType(Material.NETHERRACK);
				else checkBlock.setType(Material.STONE);
			}
		}
		
		// Build obsidian frame
		for (int xd = -1; xd < 3; ++xd) {
			for (int yd = -1; yd < 4; ++yd) {
				if (xd == -1 || yd == -1 || xd == 2 || yd == 3) {
					if (orientX)
						world.getBlockAt(x + xd, y + yd, z).setType(Material.OBSIDIAN);
					else
						world.getBlockAt(x, y + yd, z + xd).setType(Material.OBSIDIAN);
				}

				// Be sure the portal is full of only air, at this point.
				if((xd == 0 || xd == 1) && yd > -1 && yd < 3){
					if (orientX)
						world.getBlockAt(x + xd, y + yd, z).setType(Material.AIR);
					else
						world.getBlockAt(x, y + yd, z + xd).setType(Material.AIR);
				}
			}
		}
		
		// Set it alight!
		dest.setType(Material.FIRE);
		
		return new NetherPortal(dest);
	}

	// Returns a value 0-28.
	private static int checkPortalQuality(Block checkBlock, boolean orientX){
		int	quality = 0;
		int xVal = orientX ? 1 : 0;
		int zVal = orientX ? 0 : 1;

		// Check inside frame. Priority high-low, total 18.
		if(canBreathe(checkBlock.getTypeId())) quality += 6;
		if(canBreathe(checkBlock.getRelative(xVal, 0, zVal).getTypeId())) quality += 6;
		if(canBreathe(checkBlock.getRelative(0, 1, 0).getTypeId())) quality += 2;
		if(canBreathe(checkBlock.getRelative(xVal, 1, zVal).getTypeId())) quality += 2;
		if(canBreathe(checkBlock.getRelative(0, 2, 0).getTypeId())) quality += 1;
		if(canBreathe(checkBlock.getRelative(xVal, 2, zVal).getTypeId())) quality += 1;

		// Check ground under frame.  Priority mid, total 6.
		if(canStand(checkBlock.getRelative(0, -1, 0).getTypeId())) quality += 3;
		if(canStand(checkBlock.getRelative(xVal, -1, zVal).getTypeId())) quality += 3;

		// Check ground around frame.  Priority low, total 4.
		if(canStand(checkBlock.getRelative(1, -1, 1).getTypeId())) quality += 1;
		if (orientX)
		{
			if(canStand(checkBlock.getRelative(0, -1, -1).getTypeId())) quality += 1;
			if(canStand(checkBlock.getRelative(1, -1, -1).getTypeId())) quality += 1;
			if(canStand(checkBlock.getRelative(0, -1, 1).getTypeId())) quality += 1;
		}
		else
		{
			if(canStand(checkBlock.getRelative(-1, -1, 0).getTypeId())) quality += 1;
			if(canStand(checkBlock.getRelative(-1, -1, 1).getTypeId())) quality += 1;
			if(canStand(checkBlock.getRelative(1, -1, 0).getTypeId())) quality += 1;
		}

		return(quality);
	}

	private static boolean canStand(int mat){
		// Leave out the types that have to be atop a solid block, though not plants,
		// and others you don't want to destroy too: 55,63,64,65,66,68,69,70,71,72,75,76,77,93,94
		final int[] notSupporting = {0,6,8,9,10,11,37,38,39,40,50,51,59,83,85,90};
		
		for(int x = 0; x < notSupporting.length; ++x){
			if(mat == notSupporting[x]) return(false);
		}
		
		return(true);
	}

	private static boolean canBreathe(int mat){
		// All the types that include a breathable air pocket, not including fire.
		final int[] isBreathable = {0,6,37,38,39,40,50,55,59,63,64,65,66,68,69,70,71,72,75,76,77,83,93,94};
		
		for(int x = 0; x < isBreathable.length; ++x){
			if(mat == isBreathable[x]) return(true);
		}
		
		return(false);
	}

	private static boolean canFall(int mat){
		// All the types that can fall on the player, causing much pain.
		final int[] isUnstable = {8,9,10,11,12,13};
		
		for(int x = 0; x < isUnstable.length; ++x){
			if(mat == isUnstable[x]) return(true);
		}
		
		return(false);
	}
}
