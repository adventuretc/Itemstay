package me.nighteyes604.ItemStay;

import java.util.Locale;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Vector;

public class FrozenItem
{
	private static ItemStay plugin;

	private String owner;
	private Item item;
	// for easier chunk access
	private Location location;
	private Material material;
	private short data;
	private String worldName;
	private int blockX, blockY, blockZ;

	private static Vector dropOffset = new Vector(0.5, 0.9, 0.5);

	//boolean requiresUpdate = true;

	// spawn offset: loc.getBlockX()+.5, loc.getBlockY()+.9, loc.getBlockZ()+.5

	FrozenItem(String player, Material mat, short d, String worldName, int x, int y, int z)
	{
		this.worldName = worldName;
		this.owner = player.toLowerCase(Locale.ENGLISH);
		this.blockX = x;
		this.blockY = y;
		this.blockZ = z;
		this.material = mat;
		this.data = d;
		this.location = new Location(plugin.getServer().getWorld(worldName), x, y, z);
		respawn();
	}

	void respawnIfDead()
	{
		if (!hasWorld())
			return;

		if (!location.getChunk().isLoaded())
			return;

		if (item == null)
		{
			place();
			return;
		}

		if (item != null)
			if (item.isDead())
			{
				place();
				return;
			}
	}

	void respawn()
	{
		if (!hasWorld())
			return;

		if (!location.getChunk().isLoaded())
			return;

		if (item != null)
		{
			destroy();
			place();
		}
		else
		{
			place();
		}
	}

	void destroy()
	{
		if (!hasWorld())
			return;

		removeDuplicateItems();
		item.remove();
	}

	private void removeDuplicateItems()
	{
		//temporarly disabled
		/*if (!hasWorld())
			return;

		for (Entity e : item.getNearbyEntities(0.5, 0.5, 0.5))
			if (e instanceof Item)
				if (!e.equals(item))
					if (((Item) e).getItemStack().hasItemMeta())
						if (((Item) e).getItemStack().getItemMeta().hasDisplayName())
							if (((Item) e).getItemStack().getItemMeta().getDisplayName().equals("ItemStay"))
								e.remove();*/
	}

	private void place()
	{
		ItemStack stack = new ItemStack(material, 1, data);
		item = location.getWorld().dropItem(new Location(location.getWorld(), location.getX() + dropOffset.getX(), location.getY() + dropOffset.getY(), location.getZ() + dropOffset.getZ()), stack);

		ItemMeta m = item.getItemStack().getItemMeta();
		m.setDisplayName("ItemStay");
		item.getItemStack().setItemMeta(m);

		item.setVelocity(new Vector(0, 0, 0));
		removeDuplicateItems();
	}

	boolean hasWorld()
	{
		if (plugin.getServer().getWorld(this.worldName) != null)
			return true;
		return false;
	}

	static void setPlugin(ItemStay plugin)
	{
		FrozenItem.plugin = plugin;
	}


	//API methods below

	public String getOwner()
	{
		return owner;
	}

	public Item getItem()
	{
		return item;
	}

	/**
	 * Returns the theoretical location of the Item.
	 * If the location didn't exist upon loading, returns null.
	 * @return The location of the item.
	 */
	public Location getLocation()
	{
		return location;
	}

	public Material getMaterial()
	{
		return material;
	}

	public short getData()
	{
		return data;
	}

	public String getWorldName()
	{
		return worldName;
	}

	public int getBlockX()
	{
		return blockX;
	}

	public int getBlockY()
	{
		return blockY;
	}

	public int getBlockZ()
	{
		return blockZ;
	}

	public boolean isOwnedBy(Player p)
	{
		if (this.owner.equals(p.getName().toLowerCase(Locale.ENGLISH)))
			return true;
		return false;
	}

	public int getId()
	{
		return plugin.frozenItems.indexOf(this);
	}
}