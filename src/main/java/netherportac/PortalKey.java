package netherportac;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * Identifies one nether portal by its world and the lowest corner of its
 * portal blocks. Stays the same no matter which block of the portal you
 * stand in.
 */
public final class PortalKey {

    final String world;
    final int x;
    final int y;
    final int z;

    PortalKey(String world, int x, int y, int z) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    static PortalKey parse(String s) {
        int at = s.lastIndexOf('@');
        if (at <= 0) {
            return null;
        }
        String[] parts = s.substring(at + 1).split(",");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new PortalKey(s.substring(0, at),
                    Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    Location toLocation(World w) {
        return new Location(w, x, y, z);
    }

    @Override
    public String toString() {
        return world + "@" + x + "," + y + "," + z;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PortalKey)) {
            return false;
        }
        PortalKey k = (PortalKey) o;
        return x == k.x && y == k.y && z == k.z && world.equals(k.world);
    }

    @Override
    public int hashCode() {
        return ((world.hashCode() * 31 + x) * 31 + y) * 31 + z;
    }
}
