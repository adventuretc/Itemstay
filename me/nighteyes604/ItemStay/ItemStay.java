package me.nighteyes604.ItemStay;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

/*
 * Changes
 * 
 * 1.5.5
 * Added item removal check as a workaround against item removal plugins
 * The material of saved items are now stored as names rather than numeric ID's
 * 
 * 1.5.5b
 * Now preventing miscellaneous inventory pick ups. (InventoryPickupItemEvent)
 * Fixed a respawn bug.
 * 
 * 1.5.6
 * Added:
 * Proper API (getItemList, registerItem, deregisterItem, etc.)
 * Reload and respawn command
 * 
 * Removed:
 * Unnecessary item type data from items.csv (the previous format is supported for loading, but saving is done in the new format)
 * 
 * Bug fixes:
 * Newly dropped items are no longer despawned if the display creation fails
 * Items are no longer respawned (duped) needlessly upon chunk loading
 * 
 * Note:
 * This update breaks compatibility with ChestIcon 1.1 and any other plugins that were using ItemStay before 1.5.6.
 * If you wish to use ChestIcon 1.1 or lower, please stay with ItemStay 1.5.5b.
 * As of the December of 2015, the maintainer of ChestIcon has been inactive for half a year.
 */

/*
 * TODO:
 * permission for /itemstay list
*/


public class ItemStay extends JavaPlugin
{
	List<FrozenItem> frozenItems;
	HashSet<String> saveNextItem;

	private static Logger logger = Bukkit.getServer().getLogger();

	private Boolean itemRemovalCheck;
	private int itemRemovalCheckInterval;

	private BukkitRunnable itemRemovalCheckRunnable;
	private BukkitTask itemRemovalCheckTask;

	//API methods

	public void onEnable()
	{
		frozenItems = new ArrayList<FrozenItem>(500);
		saveNextItem = new HashSet<String>();

		FrozenItem.setPlugin(this);

		loadConfig();
		loadDataFile();

		for (FrozenItem fi : frozenItems)
		{
			fi.respawn();
		}

		this.getServer().getPluginManager().registerEvents(new ItemStayListener(this), this);

		getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable()
		{
			public void run()
			{
				for (FrozenItem fi : frozenItems)
				{
					fi.respawnIfDead();
				}
			}
		}, 20 * 60 * 1, 20 * 60 * 30);

		dealWithNewConfigValues();
	}

	public void onDisable()
	{
		save();

		for (FrozenItem fi : frozenItems)
		{
			fi.destroy();
		}
	}

	public boolean onCommand(CommandSender sender, Command cmd, String label, String args[])
	{
		if (!cmd.getName().equalsIgnoreCase("itemstay"))
			return false;

		Player p = null;

		if (sender instanceof Player)
			p = (Player) sender;

		if (args.length == 0)
		{
			if (!(sender instanceof Player))
			{
				sender.sendMessage("[ItemStay] You must be a player to use this command.");
				return true;
			}

			if (!(sender.hasPermission("itemstay.create")) && !(sender.hasPermission("itemstay.admin")))
			{
				sender.sendMessage("[ItemStay]" + ChatColor.GRAY + " You don't have permission to create displays.");
				return true;

			}
			if (!(saveNextItem.contains(sender.getName().toLowerCase())))
			{
				saveNextItem.add(sender.getName().toLowerCase());
				sender.sendMessage("[ItemStay]" + ChatColor.GRAY + " The next item you drop will be put on display.");
				save();
			}
			else
			{
				saveNextItem.remove(sender.getName().toLowerCase());
				sender.sendMessage("[ItemStay]" + ChatColor.GRAY + " Display creation cancelled.");
				save();
			}
			return true;
		}

		if (args[0].equalsIgnoreCase("respawn") && sender.hasPermission("itemstay.admin"))
		{
			for (FrozenItem fi : frozenItems)
				fi.respawn();

			sender.sendMessage("[ItemStay] Items were respawned.");
			return true;
		}

		if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("itemstay.admin"))
		{
			for (FrozenItem fi : frozenItems)
				fi.getItem().remove();

			frozenItems = new ArrayList<FrozenItem>(500);

			loadConfig();
			sender.sendMessage("[ItemStay] Configuration was reloaded.");

			loadDataFile();

			for (FrozenItem fi : frozenItems)
				fi.respawn();

			dealWithNewConfigValues();

			sender.sendMessage("[ItemStay] Items were loaded from file and respawned.");
			return true;
		}

		if (args[0].equalsIgnoreCase("list"))
		{
			if (!(sender instanceof Player))
			{
				sender.sendMessage("[ItemStay] You must be a player to use this command.");
				return true;
			}
			Player player = (Player) sender;
			List<String> list = new ArrayList<String>();

			for (FrozenItem fi : frozenItems)
			{
				if ((fi.getLocation().getWorld().equals(player.getLocation().getWorld())))
				{
					if (fi.getLocation().getWorld().getName().equalsIgnoreCase(player.getLocation().getWorld().getName()))
					{
						if (fi.getLocation().distanceSquared(player.getLocation()) <= 100)
						{
							list.add("   ID: " + ChatColor.GRAY + frozenItems.indexOf(fi) + ChatColor.WHITE + "   Owner: " + ChatColor.GRAY + fi.getOwner() + ChatColor.WHITE + "   Item: " + ChatColor.GRAY + fi.getItem().getItemStack().getType().name());
						}
					}
				}
			}

			if (list.size() == 0)
			{
				sender.sendMessage("[ItemStay]" + ChatColor.GRAY + " No nearby displays found.");
			}
			else
			{
				p.sendMessage("[ItemStay]" + ChatColor.GRAY + " Nearby displays:");
				for (String str : list)
				{
					p.sendMessage(str);
				}
			}
			return true;
		}

		if ((args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("delete")))
		{
			if (args.length < 2)
			{
				sender.sendMessage("[ItemStay]" + ChatColor.GRAY + " Missing display id. Use '/itemstay delete id'.");
				return true;
			}
			else
			{
				Integer id;
				try
				{
					id = Integer.parseInt(args[1]);
				}
				catch (Exception e)
				{
					sender.sendMessage("[ItemStay]" + ChatColor.GRAY + " Not a valid display id.");
					return true;
				}

				if (frozenItems.size() > id && id >= 0)
				{
					if ((frozenItems.get(id).isOwnedBy(p) && sender.hasPermission("itemstay.create")) || sender.hasPermission("itemstay.admin") || sender.hasPermission("itemstay.delete"))
					{
						sender.sendMessage("[ItemStay]" + ChatColor.GRAY + " Display " + ChatColor.WHITE + args[1] + ChatColor.GRAY + " removed.");
						frozenItems.get(id).destroy();
						frozenItems.remove(frozenItems.get(id));
						save();
						return true;
					}
					else
					{
						sender.sendMessage("[ItemStay]" + ChatColor.GRAY + " You don't have permission to remove display " + ChatColor.WHITE + args[1] + ChatColor.GRAY + ".");
						return true;
					}
				}
				else
				{
					sender.sendMessage("[ItemStay]" + ChatColor.GRAY + " Not a valid display id.");
					return true;
				}
			}
		}

		//help dialog

		sender.sendMessage("[ItemStay] " + ChatColor.GRAY + getDescription().getDescription());
		if (sender.hasPermission("itemstay.admin") || sender.hasPermission("itemstay.create"))
		{
			sender.sendMessage(ChatColor.GRAY + "Command List:");
			sender.sendMessage("   /itemstay" + ChatColor.GRAY + " to make the next item you drop a display.");
			sender.sendMessage("   /itemstay list" + ChatColor.GRAY + " to list nearby item displays with id's.");
			sender.sendMessage("   /itemstay delete [id]" + ChatColor.GRAY + " to delete the item display.");
			sender.sendMessage("   /itemstay respawn" + ChatColor.GRAY + " to respawn items.");
			sender.sendMessage("   /itemstay reload" + ChatColor.GRAY + " to reload items and config from file.");
		}
		return true;
	}

	/**
	 * Creates an item display from a dropped item.
	 * Warning: Does not remove the registered item!
	 * @param owner Name of the owner, to be saved.
	 * @param item A dropped item.
	 * @return Information about the outcome. Result value can be: SUCCESS, LOCATION_TAKEN
	 */
	public QInfo registerItem(String owner, Item item)
	{
		for (FrozenItem fi : frozenItems)
		{
			if (!fi.hasWorld())
				continue;

			if (fi.getLocation().equals(new Location(item.getWorld(), item.getLocation().getBlockX(), item.getLocation().getBlockY(), item.getLocation().getBlockZ())))
			{
				return new QInfo(fi.getId(), QResult.LOCATION_TAKEN);
			}
		}

		frozenItems.add(new FrozenItem(owner, item.getItemStack().getType(), item.getItemStack().getDurability(), item.getLocation().getWorld().getName(), item.getLocation().getBlockX(), item.getLocation().getBlockY(), item.getLocation().getBlockZ()));
		save();
		FrozenItem newfi = frozenItems.get(frozenItems.size() - 1);
		newfi.respawn();

		return new QInfo(newfi.getId(), QResult.SUCCESS);
	}

	/**
	 * Creates an item display from a specified ItemStack and Location.
	 * @param owner Name of the owner, to be saved.
	 * @param itemStack Can be any itemstack.
	 * @param location Can be any existing location. Always rounded to and administered as whole blocks.
	 * @return Information about the outcome. Result value can be: SUCCESS, LOCATION_TAKEN
	 */
	public QInfo registerItem(String owner, ItemStack itemStack, Location location)
	{
		for (FrozenItem fi : frozenItems)
		{
			if (!fi.hasWorld())
				continue;

			if (fi.getLocation().equals(new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ())))
			{
				return new QInfo(fi.getId(), QResult.LOCATION_TAKEN);
			}
		}

		frozenItems.add(new FrozenItem(owner, itemStack.getType(), itemStack.getDurability(), location.getWorld().getName(), location.getBlockX(), location.getBlockY(), location.getBlockZ()));
		save();
		FrozenItem newfi = frozenItems.get(frozenItems.size() - 1);
		newfi.respawn();

		return new QInfo(newfi.getId(), QResult.SUCCESS);
	}

	/**
	 * Removes an item display by its ItemStay ID.
	 * @return Information about the outcome. Result value can be: SUCCESS, NOT_FOUND
	 */
	public QInfo deregisterItem(int id)
	{
		try
		{
			frozenItems.get(id);
		}
		catch (IndexOutOfBoundsException e)
		{
			return new QInfo(id, QResult.NOT_FOUND);
		}

		frozenItems.get(id).destroy();
		frozenItems.remove(id);
		save();

		return new QInfo(id, QResult.SUCCESS);
	}

	/**
	 * Removes the item display at the given location.
	 * @param location Location of the item. Rounded to blocks internally.
	 * @return Information about the outcome. Result value can be: SUCCESS, NOT_FOUND
	 */
	public QInfo deregisterItem(Location location)
	{
		for (FrozenItem fi : frozenItems)
		{
			if (!fi.hasWorld())
				continue;

			if (fi.getLocation().equals(new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ())))
			{
				int tmp = fi.getId();
				fi.destroy();
				frozenItems.remove(fi);
				save();

				return new QInfo(tmp, QResult.SUCCESS);
			}
		}

		return new QInfo(-1, QResult.NOT_FOUND);
	}

	/**
	 * Returns an array of the stored items.
	 * Each Item is stored in a container class (FrozenItem).
	 * The item ID can be accessed via the FrozenItem class.
	 * Modifying any returned data may cause the plugin to disfunction.
	 * @return An array of the stored items.
	 */
	public FrozenItem[] getItemList()
	{
		return (FrozenItem[]) frozenItems.toArray(new FrozenItem[frozenItems.size()]);
	}

	//internal methods

	private void loadConfig()
	{
		final FileConfiguration config = this.getConfig();
		File file = new File(getDataFolder() + File.separator + "config.yml");

		if (!file.exists())
		{
			this.getLogger().info("Generating config file...");
			getConfig().addDefault("configversion (don't change)", 1);
			getConfig().set("item removal check", false);
			getConfig().set("item removal check interval (ticks)", 60);
			config.options().copyDefaults(true);
			this.saveConfig();
			this.getLogger().info("Config created!");
		}

		itemRemovalCheck = getConfig().getBoolean("item removal check");
		itemRemovalCheckInterval = getConfig().getInt("item removal check interval (ticks)");
	}

	private void dealWithNewConfigValues()
	{
		if (itemRemovalCheckTask != null)
			itemRemovalCheckTask.cancel();

		if (itemRemovalCheck)
		{
			itemRemovalCheckRunnable = new BukkitRunnable()
			{
				@Override
				public void run()
				{
					for (FrozenItem fi : frozenItems)
					{
						fi.respawnIfDead();
					}
				}
			};

			itemRemovalCheckTask = itemRemovalCheckRunnable.runTaskTimer(this, itemRemovalCheckInterval, itemRemovalCheckInterval);
		}
	}

	void save()
	{
		File folder = getDataFolder();
		if (!folder.exists())
		{
			folder.mkdir();
		}

		File datafile = new File(folder.getAbsolutePath() + "/items.csv");
		if (!datafile.exists())
		{
			try
			{
				datafile.createNewFile();
			}
			catch (Exception e)
			{
				return;
			}
		}

		try
		{
			FileOutputStream output = new FileOutputStream(datafile.getAbsoluteFile());
			BufferedWriter w = new BufferedWriter(new OutputStreamWriter(output));
			for (FrozenItem i : frozenItems)
			{
				try
				{
					//Format: steve,world,29,79,-9,WOOD,1
					String line = "";
					line += i.getOwner() + ",";
					line += i.getWorldName() + ",";
					line += i.getBlockX() + ",";
					line += i.getBlockY() + ",";
					line += i.getBlockZ() + ",";
					line += i.getMaterial().name() + ",";
					line += i.getData();
					line += "\n";
					w.write(line);
				}
				catch (Exception e)
				{
					logger.info("[ItemStay] Error saving item: " + i.getMaterial().toString() + " " + e.toString());
				}
			}
			w.flush();
			output.close();
		}
		catch (Exception e)
		{
			logger.info("[ItemStay] Error saving file.");
			e.printStackTrace();
		}
	}

	@SuppressWarnings("deprecation")
	private void loadDataFile()
	{
		File folder = getDataFolder();
		if (!folder.exists())
		{
			folder.mkdir();
		}
		File datafile = new File(folder.getAbsolutePath() + "/items.csv");
		if (datafile.exists())
		{
			FileInputStream input;
			InputStreamReader ir = null;
			BufferedReader r = null;
			try
			{
				input = new FileInputStream(datafile.getAbsoluteFile());
				ir = new InputStreamReader(input);
				r = new BufferedReader(ir);
			}
			catch (Exception e)
			{
				logger.info("[ItemStay] Error loading file. " + e.toString());
			}

			String locline = null;
			while (true)
			{
				try
				{
					locline = r.readLine();
				}
				catch (IOException e1)
				{
					e1.printStackTrace();
				}
				if (locline == null)
				{
					break;
				}

				String lineBits[] = locline.split(",");

				String player = lineBits[0];

				byte offset = 0;

				int x, y, z;

				x = Integer.valueOf(lineBits[2]);
				y = Integer.valueOf(lineBits[3]);
				z = Integer.valueOf(lineBits[4]);

				if (lineBits[5].equals("DROPPED_ITEM"))
					offset = 1;

				Material mat;
				try
				{
					//old system, numeric id
					mat = Material.getMaterial(Integer.parseInt(lineBits[5 + offset]));
				}
				catch (NumberFormatException e)
				{
					//new system, mat name
					mat = Material.getMaterial(lineBits[5 + offset]);
				}

				short data = Short.valueOf(lineBits[6 + offset]);

				FrozenItem fi = new FrozenItem(player, mat, data, lineBits[1], x, y, z);
				frozenItems.add(fi);
			}
			try
			{
				r.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
			try
			{
				ir.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}
}
