package netherportac;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Fixes nether portal linking by remembering which portal each trip started
 * from and sending the return trip straight back there.
 */
public final class NetherPortac extends JavaPlugin implements Listener {

    /** A player who just stepped into a portal and hasn't arrived yet. */
    private static final class Trip {
        final PortalKey from;
        final long started;

        Trip(PortalKey from) {
            this.from = from;
            this.started = System.currentTimeMillis();
        }
    }

    private static final long TRIP_TIMEOUT_MS = 10000;

    private final Map<UUID, Trip> trips = new HashMap<UUID, Trip>();
    private PortalLinks links;
    private boolean perPlayer;
    private boolean linkEntities;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readConfig();
        links = new PortalLinks(new File(getDataFolder(), "links.yml"), getLogger());
        links.load();
        getServer().getPluginManager().registerEvents(this, this);
        long interval = Math.max(5, getConfig().getLong("save-interval-seconds", 60)) * 20L;
        getServer().getScheduler().runTaskTimer(this, links::saveIfDirty, interval, interval);
        getLogger().info("Loaded " + links.count() + " portal links.");
    }

    @Override
    public void onDisable() {
        if (links != null) {
            links.save();
        }
    }

    private void readConfig() {
        reloadConfig();
        perPlayer = getConfig().getBoolean("per-player-links", true);
        linkEntities = getConfig().getBoolean("link-entities", true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (event.getCause() != TeleportCause.NETHER_PORTAL) {
            return;
        }
        Player player = event.getPlayer();
        Portal from = Portal.near(event.getFrom());
        if (from == null) {
            return;
        }
        trips.put(player.getUniqueId(), new Trip(from.key));

        Location dest = null;
        if (perPlayer) {
            dest = resolve(links.player(player.getUniqueId(), from.key), player);
        }
        if (dest == null) {
            dest = resolve(links.shared(from.key), player);
        }
        if (dest != null) {
            event.setTo(dest);
            event.useTravelAgent(false);
        }
        // Otherwise vanilla finds or builds a portal and we link it on arrival.
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityPortal(EntityPortalEvent event) {
        if (!linkEntities) {
            return;
        }
        Portal from = Portal.near(event.getFrom());
        if (from == null) {
            return;
        }
        Location dest = resolve(links.shared(from.key), event.getEntity());
        if (dest != null) {
            event.setTo(dest);
            event.useTravelAgent(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Trip trip = trips.remove(player.getUniqueId());
        if (trip == null || System.currentTimeMillis() - trip.started > TRIP_TIMEOUT_MS) {
            return;
        }
        // Wait a tick so a portal vanilla just built is fully in place.
        getServer().getScheduler().runTaskLater(this, () -> {
            if (!player.isOnline()) {
                return;
            }
            Portal arrived = Portal.near(player.getLocation());
            if (arrived != null && !arrived.key.world.equals(trip.from.world)) {
                links.link(player.getUniqueId(), trip.from, arrived.key);
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        trips.remove(event.getPlayer().getUniqueId());
    }

    /** Turns a remembered portal into an arrival spot, dropping it if it was broken. */
    private Location resolve(PortalKey target, Entity entity) {
        if (target == null) {
            return null;
        }
        World world = getServer().getWorld(target.world);
        if (world == null) {
            return null;
        }
        Portal portal = Portal.at(target, world);
        if (portal == null) {
            links.forget(target);
            return null;
        }
        links.moved(target, portal.key);
        Location here = entity.getLocation();
        return portal.arrival(here.getYaw(), here.getPitch());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "info" : args[0].toLowerCase();
        if (sub.equals("reload")) {
            readConfig();
            sender.sendMessage(ChatColor.GREEN + "NetherPortac config reloaded.");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Stand in a portal in-game to use this.");
            return true;
        }
        Player player = (Player) sender;
        Portal portal = Portal.near(player.getLocation());
        if (portal == null) {
            player.sendMessage(ChatColor.RED + "Stand in a nether portal first.");
            return true;
        }
        if (sub.equals("unlink")) {
            links.forget(portal.key);
            player.sendMessage(ChatColor.GREEN + "Forgot all links for this portal. The next trip relinks it.");
            return true;
        }
        if (sub.equals("info")) {
            player.sendMessage(ChatColor.GOLD + "Portal " + portal.key);
            player.sendMessage(ChatColor.GRAY + "Shared link: " + describe(links.shared(portal.key)));
            player.sendMessage(ChatColor.GRAY + "Your link: " + describe(links.player(player.getUniqueId(), portal.key)));
            return true;
        }
        return false;
    }

    private static String describe(PortalKey key) {
        return key == null ? "none (vanilla)" : key.toString();
    }
}
