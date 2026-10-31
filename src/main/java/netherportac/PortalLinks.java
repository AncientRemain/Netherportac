package netherportac;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Remembered portal links, saved in links.yml.
 *
 * Shared links are used by everyone (and by entities). Player links override
 * them, so each player goes back to the portal they personally came from.
 */
final class PortalLinks {

    private final File file;
    private final Logger log;
    private final Map<PortalKey, PortalKey> shared = new HashMap<PortalKey, PortalKey>();
    private final Map<UUID, Map<PortalKey, PortalKey>> players = new HashMap<UUID, Map<PortalKey, PortalKey>>();
    private boolean dirty;

    PortalLinks(File file, Logger log) {
        this.file = file;
        this.log = log;
    }

    PortalKey shared(PortalKey from) {
        return shared.get(from);
    }

    PortalKey player(UUID player, PortalKey from) {
        Map<PortalKey, PortalKey> links = players.get(player);
        return links == null ? null : links.get(from);
    }

    /** Remembers that {@code a} and {@code b} lead to each other. */
    void link(UUID player, PortalKey a, PortalKey b) {
        if (player != null) {
            Map<PortalKey, PortalKey> links = players.get(player);
            if (links == null) {
                links = new HashMap<PortalKey, PortalKey>();
                players.put(player, links);
            }
            links.put(a, b);
            links.put(b, a);
        }
        // The first trip through a portal decides where it goes for everyone else.
        if (!shared.containsKey(a)) {
            shared.put(a, b);
        }
        if (!shared.containsKey(b)) {
            shared.put(b, a);
        }
        dirty = true;
    }

    /** Forgets every link to or from {@code portal}, e.g. after it was broken. */
    void forget(PortalKey portal) {
        removeFrom(shared, portal);
        for (Map<PortalKey, PortalKey> links : players.values()) {
            removeFrom(links, portal);
        }
        dirty = true;
    }

    /** Re-keys links when a portal was rebuilt with a different lowest corner. */
    void moved(PortalKey oldKey, PortalKey newKey) {
        if (oldKey.equals(newKey)) {
            return;
        }
        rekey(shared, oldKey, newKey);
        for (Map<PortalKey, PortalKey> links : players.values()) {
            rekey(links, oldKey, newKey);
        }
        dirty = true;
    }

    int count() {
        return shared.size();
    }

    private static void removeFrom(Map<PortalKey, PortalKey> links, PortalKey portal) {
        links.remove(portal);
        links.values().removeIf(portal::equals);
    }

    private static void rekey(Map<PortalKey, PortalKey> links, PortalKey oldKey, PortalKey newKey) {
        PortalKey target = links.remove(oldKey);
        if (target != null) {
            links.put(newKey, target);
        }
        links.replaceAll((from, to) -> to.equals(oldKey) ? newKey : to);
    }

    void load() {
        shared.clear();
        players.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        readList(yml.getStringList("shared"), shared);
        ConfigurationSection section = yml.getConfigurationSection("players");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                try {
                    Map<PortalKey, PortalKey> links = new HashMap<PortalKey, PortalKey>();
                    readList(section.getStringList(id), links);
                    players.put(UUID.fromString(id), links);
                } catch (IllegalArgumentException e) {
                    log.warning("Skipping bad player id in links.yml: " + id);
                }
            }
        }
    }

    void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("shared", writeList(shared));
        for (Map.Entry<UUID, Map<PortalKey, PortalKey>> e : players.entrySet()) {
            if (!e.getValue().isEmpty()) {
                yml.set("players." + e.getKey(), writeList(e.getValue()));
            }
        }
        try {
            yml.save(file);
            dirty = false;
        } catch (IOException e) {
            log.severe("Could not save " + file + ": " + e.getMessage());
        }
    }

    private void readList(List<String> lines, Map<PortalKey, PortalKey> into) {
        for (String line : lines) {
            int arrow = line.indexOf(" -> ");
            PortalKey from = arrow < 0 ? null : PortalKey.parse(line.substring(0, arrow));
            PortalKey to = arrow < 0 ? null : PortalKey.parse(line.substring(arrow + 4));
            if (from == null || to == null) {
                log.warning("Skipping bad link in links.yml: " + line);
                continue;
            }
            into.put(from, to);
        }
    }

    private static List<String> writeList(Map<PortalKey, PortalKey> links) {
        List<String> lines = new ArrayList<String>();
        for (Map.Entry<PortalKey, PortalKey> e : links.entrySet()) {
            lines.add(e.getKey() + " -> " + e.getValue());
        }
        return lines;
    }
}
