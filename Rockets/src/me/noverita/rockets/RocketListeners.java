package me.noverita.rockets;

import com.earth2me.essentials.Warps;
import com.earth2me.essentials.commands.WarpNotFoundException;
import com.google.common.base.Supplier;
import net.ess3.api.InvalidWorldException;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.spigotmc.event.entity.EntityDismountEvent;

import java.lang.reflect.Method;
import java.util.*;

public class RocketListeners implements Listener {
    //Keeps track of players that are actively using the inventory menu.
    //Needed so that using player.closeInventory() won't trigger a dismount
    private final Set<Player> selectingDestinations = new HashSet<>();

    // For some reason the game only tracks the things riding an entity, but not the other way around.
    private final Map<Player, Entity> mounts = new HashMap<>();

    // Hacky packet workaround to let entities with passengers be teleported.
    // The standard .teleport() method doesn't work.
    private final Method[] methods = ((Supplier<Method[]>) () -> {
        try {
            Method getHandle = Class.forName(Bukkit.getServer().getClass().getPackage().getName() + ".entity.CraftEntity").getDeclaredMethod("getHandle");
            return new Method[] {
                    getHandle, getHandle.getReturnType().getDeclaredMethod("b", double.class, double.class, double.class, float.class, float.class)
            };
        } catch (Exception ex) {
            return null;
        }
    }).get();

    // Temprorary debug method that allows for easy spawning of rockets.
    @EventHandler
    public void spawnRocket(PlayerInteractEvent event) {
        if (event.getItem() != null && event.getItem().getType() == Material.NETHER_STAR) {
            event.getPlayer().sendMessage("Rocket spawned!");

            //Spawn rocket
            Block b = event.getClickedBlock().getRelative(0, 1, 0);
            ArmorStand rocket = (ArmorStand) b.getWorld().spawnEntity(b.getLocation(), EntityType.ARMOR_STAND);

            // Visual appearance
            rocket.setGravity(false);
            rocket.setHelmet(new ItemStack(Material.IRON_BLOCK));
            rocket.setCustomName("Rocket");
            rocket.setInvisible(true);

            // Stop players from stealing the rocket's head.
            rocket.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);
            rocket.addEquipmentLock(EquipmentSlot.CHEST, ArmorStand.LockType.ADDING);
            rocket.addEquipmentLock(EquipmentSlot.LEGS, ArmorStand.LockType.ADDING);
            rocket.addEquipmentLock(EquipmentSlot.FEET, ArmorStand.LockType.ADDING);
        }
    }

    // As armour stands cannot normally be mounted, an extra listener is needed.
    // Also shows the menu.
    @EventHandler
    public void mountRocketEvent(PlayerInteractAtEntityEvent event) {
        Entity e = event.getRightClicked();
        if (e.getCustomName() != null && e.getCustomName().equals("Rocket") && e.getType() == EntityType.ARMOR_STAND && e.getPassengers().isEmpty()) {
            showMenu(event.getPlayer());
            e.addPassenger(event.getPlayer());
            mounts.put(event.getPlayer(), e);

            event.setCancelled(true); // Probably unnecessary, but it doesn't hurt.
        }
    }

    // Removes reference when an entity is dismounted
    // TODO - Vehicle death may not be accounted for. Need to test.
    @EventHandler
    public void dismountEvent(EntityDismountEvent event) {
        mounts.remove(event.getEntity());
    }

    // Shows player the inventory menu.
    public void showMenu(Player p) {
        Warps warps = Main.essentials.getWarps();
        Collection<String> warpNames = warps.getList();
        TreeSet<String> sortedWarpNames = new TreeSet<>(warpNames);
        ItemStack[] items = new ItemStack[54]; // Hard coded to support up to 54 warps, that should be plenty.

        int i = 0;
        for (String warpName: sortedWarpNames) {
            // Filters for specifically rocker warps, so that others can exist without showing up.
            // (Private staff warps for example)
            if (warpName.startsWith("rocket_")) {
                ItemStack temp = new ItemStack(Material.GREEN_CONCRETE); // TODO - make a way to set the material
                ItemMeta meta = temp.getItemMeta();
                meta.setDisplayName(ChatColor.RESET + "" + ChatColor.LIGHT_PURPLE + warpName.substring(7));
                temp.setItemMeta(meta);
                items[i] = temp;

                i += 1;
            }
        }

        // This should probably be done using inventory views, but I'm lazy and this works
        Inventory destinationsInventory = Bukkit.createInventory(null, 54, "Destinations");
        destinationsInventory.setContents(items);

        selectingDestinations.add(p);
        p.openInventory(destinationsInventory);
    }

    // Get player input from inventory menu. Also stops players from stealing the items.
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        ItemStack clicked = event.getCurrentItem();
        String inventoryName = event.getView().getTitle();
        if (inventoryName.equals("Destinations") && event.getInventory().getHolder() == null && clicked != null) {
            startLaunch(player, "rocket_" + clicked.getItemMeta().getDisplayName());
            selectingDestinations.remove(player);
            player.closeInventory();
            event.setCancelled(true);
        }
    }

    // Dismounts the player if they don't choose a location.
    @EventHandler
    public void onInventoryExit(InventoryCloseEvent event) {
        if (event.getView().getTitle().equals("Destinations") && selectingDestinations.contains(event.getPlayer())) {
            HumanEntity player = event.getPlayer();
            selectingDestinations.remove(player);
            Entity mount = mounts.remove(player);
            if (mount != null) {
                mount.removePassenger(player);
            }
        }
    }

    //Main launch loop
    // TODO - Play sound
    public void startLaunch(Player player, String destination) {
        Entity finalMount = mounts.get(player);

        int[] id = new int[1];
        double[] speed = new double[1];
        speed[0] = 0.1;
        id[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(Main.instance, () -> {
            Location loc = finalMount.getLocation().add(0, speed[0], 0);
            speed[0] += 0.01;
            // Janky teleport workaround
            try {
                methods[1].invoke(methods[0].invoke(finalMount), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            } catch (Exception ignored) {
            }
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 0, 0, -1, 0, 0.05);
            //finalMount.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, finalMount.getLocation(), 1);
            if (finalMount.getLocation().getY() > 400) {
                try {
                    // Janky teleport workaround
                    Location destinationLocation = Main.essentials.getWarps().getWarp(ChatColor.stripColor(destination));
                    try {
                        methods[1].invoke(
                                methods[0].invoke(finalMount),
                                destinationLocation.getX(),
                                400,
                                destinationLocation.getZ(),
                                destinationLocation.getYaw(),
                                destinationLocation.getPitch()
                        );
                    } catch (Exception ignored) {
                    }

                    Bukkit.getScheduler().cancelTask(id[0]);
                    startDescent(finalMount);
                } catch (WarpNotFoundException | InvalidWorldException e) {
                    e.printStackTrace();
                }
            }
        }, 1, 0);
    }

    // Descent loop after teleporting. When a block is encounted, it stops.
    // TODO - Play sound
    private void startDescent(Entity finalMount) {

        int[] id = new int[1];
        final World world = finalMount.getWorld();
        id[0] = Bukkit.getScheduler().scheduleSyncRepeatingTask(Main.instance, () -> {
            Location loc = finalMount.getLocation().add(0, -0.5, 0);
            // Janky teleport workaround
            try {
                methods[1].invoke(methods[0].invoke(finalMount), loc.getX(), loc.getY(), loc.getZ(), loc.getYaw(), loc.getPitch());
            } catch (Exception ignored) {
            }
            //finalMount.getWorld().spawnParticle(Particle.EXPLOSION_NORMAL, finalMount.getLocation(), 1);
            loc.getWorld().spawnParticle(Particle.FLAME, loc, 0, 0, -0.5, 0, 0.05);
            if (finalMount.getLocation().getY() < world.getMaxHeight() && !world.getBlockAt(finalMount.getLocation()).getType().isAir()) {
                for (Entity passenger: finalMount.getPassengers()) {
                    finalMount.removePassenger(passenger);
                    mounts.remove(passenger);
                }
                Bukkit.getScheduler().cancelTask(id[0]);
            }
        }, 1, 0);
    }

    @EventHandler
    private void onKillRocket(EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (entity.getType() == EntityType.ARMOR_STAND && entity.getCustomName().equals("Rocket")) {
            event.getDrops().clear();
            event.getDrops().add(new ItemStack(Material.NETHER_STAR));
        }
    }
}
