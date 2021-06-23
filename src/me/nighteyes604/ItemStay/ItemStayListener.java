package me.nighteyes604.ItemStay;

import java.util.ArrayList;
import java.util.List;

import me.minebuilders.clearlag.events.EntityRemoveEvent;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.meta.Damageable;

class ItemStayListener implements Listener
{
	private static ItemStay plugin;

	ItemStayListener(ItemStay p)
	{
		plugin = p;
	}

	//Try to prevent despawning from unknown causes
	@EventHandler(priority = EventPriority.NORMAL)
	public void onItemDespawnEvent(ItemDespawnEvent event)
	{
		for (FrozenItem fi : plugin.frozenItems)
		{
			if (!fi.hasWorldAndChunk())
				continue;

			if (event.getEntity().equals(fi.getItem()))
			{
				event.setCancelled(true);
			}
		}
	}

	//Prevent pickup by misc. inventories/entities
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onInventoryPickupItemEvent(final InventoryPickupItemEvent e)
	{
		if (!e.getItem().getItemStack().hasItemMeta())
		{
			return;
		}
		if (!e.getItem().getItemStack().getItemMeta().hasDisplayName())
		{
			return;
		}
		if (e.getItem().getItemStack().getItemMeta().getDisplayName().equals("ItemStay"))
		{
			e.setCancelled(true);
			return;
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void onChunkLoadEvent(final ChunkLoadEvent event)
	{
		for (FrozenItem fi : plugin.frozenItems)
		{
			if (!fi.hasWorldAndChunk())
				continue;

			if (fi.getLocation().getChunk().equals(event.getChunk()))
			{
				fi.respawnIfDead();
			}
		}
	}

	//Handle item placing
	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void onPlayerDropItemEvent(final PlayerDropItemEvent event)
	{
		if (plugin.saveNextItem.contains(event.getPlayer().getName().toLowerCase()))
		{
			plugin.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable()
			{
				@Override
				public void run()
				{
					if (event.getItemDrop() == null)
						return;

					Item item = event.getItemDrop();

					for (FrozenItem fi : plugin.frozenItems)
					{
						if (!fi.hasWorldAndChunk())
							continue;

						if (fi.getLocation().equals(new Location(item.getWorld(), item.getLocation().getBlockX(), item.getLocation().getBlockY(), item.getLocation().getBlockZ())))
						{
							event.getPlayer().sendMessage("[ItemStay]" + ChatColor.GRAY + " An item is already frozen there.");
							return;
						}
					}
					short damage = (short)((Damageable)item.getItemStack().getItemMeta()).getDamage();
					plugin.frozenItems.add(new FrozenItem(event.getPlayer().getName(), item.getItemStack().getType(), damage, item.getLocation().getWorld().getName(), item.getLocation().getBlockX(), item.getLocation().getBlockY(), item.getLocation().getBlockZ()));
					item.remove();
					plugin.save();
					FrozenItem fi = plugin.frozenItems.get(plugin.frozenItems.size() - 1);
					fi.respawn();
					event.getPlayer().sendMessage("[ItemStay]" + ChatColor.GRAY + " Item display created. " + ChatColor.WHITE + "ID: " + ChatColor.GRAY + fi.getId() + ChatColor.WHITE + " Owner: " + ChatColor.GRAY + fi.getOwner() + ChatColor.WHITE + " Item: " + ChatColor.GRAY + fi.getItem().getItemStack().getType().name());
					plugin.saveNextItem.remove(event.getPlayer().getName().toLowerCase());
				}
			}, 20);
		}
	}

	//Prevent player pickup
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void onEntityPickupItemEvent(EntityPickupItemEvent event)
	{
		if (!event.getItem().getItemStack().hasItemMeta())
			return;

		if (!event.getItem().getItemStack().getItemMeta().hasDisplayName())
			return;

		if (event.getItem().getItemStack().getItemMeta().getDisplayName().equals("ItemStay"))
		{
			event.setCancelled(true);
			return;
		}
	}


	//Defeat Clearlag
	@EventHandler(priority = EventPriority.HIGH)
	public void onEntityRemoveEvent(EntityRemoveEvent event)
	{
		List<Item> li = new ArrayList<Item>();

		for (int i = 0; i < plugin.frozenItems.size(); i++)
		{
			li.add(plugin.frozenItems.get(i).getItem());
		}

		event.getEntityList().removeAll(li);
	}

}