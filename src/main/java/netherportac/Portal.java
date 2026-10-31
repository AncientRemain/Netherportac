package netherportac;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

/** A connected group of nether portal blocks. */
final class Portal {

    /** Vanilla portals are at most 21x21 inside the frame. */
    private static final int MAX_BLOCKS = 21 * 21;
    private static final BlockFace[] NEIGHBOURS = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    final PortalKey key;
    private final List<Block> blocks;

    private Portal(PortalKey key, List<Block> blocks) {
        this.key = key;
        this.blocks = blocks;
    }

    /** Finds the portal an entity at {@code loc} is standing in, or null. */
    static Portal near(Location loc) {
        World world = loc.getWorld();
        if (world == null) {
            return null;
        }
        Block center = loc.getBlock();
        if (center.getType() == Material.PORTAL) {
            return flood(center);
        }
        Block best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 3; dy++) {
                for (int dz = -2; dz <= 2; dz++) {
                    Block b = center.getRelative(dx, dy, dz);
                    if (b.getType() != Material.PORTAL) {
                        continue;
                    }
                    double d = b.getLocation().add(0.5, 0, 0.5).distanceSquared(loc);
                    if (d < bestDist) {
                        bestDist = d;
                        best = b;
                    }
                }
            }
        }
        return best == null ? null : flood(best);
    }

    /** Looks up a remembered portal. Returns null if it has been broken. */
    static Portal at(PortalKey key, World world) {
        if (world == null) {
            return null;
        }
        Block b = world.getBlockAt(key.x, key.y, key.z);
        if (b.getType() != Material.PORTAL) {
            return null;
        }
        return flood(b);
    }

    private static Portal flood(Block start) {
        List<Block> found = new ArrayList<Block>();
        Set<Block> seen = new HashSet<Block>();
        ArrayDeque<Block> queue = new ArrayDeque<Block>();
        queue.add(start);
        seen.add(start);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        while (!queue.isEmpty() && found.size() < MAX_BLOCKS) {
            Block b = queue.poll();
            found.add(b);
            minX = Math.min(minX, b.getX());
            minY = Math.min(minY, b.getY());
            minZ = Math.min(minZ, b.getZ());
            for (BlockFace face : NEIGHBOURS) {
                Block n = b.getRelative(face);
                if (n.getType() == Material.PORTAL && seen.add(n)) {
                    queue.add(n);
                }
            }
        }
        return new Portal(new PortalKey(start.getWorld().getName(), minX, minY, minZ), found);
    }

    /** Where to put something arriving through this portal: middle of the bottom row. */
    Location arrival(float yaw, float pitch) {
        List<Block> bottom = new ArrayList<Block>();
        for (Block b : blocks) {
            if (b.getY() == key.y) {
                bottom.add(b);
            }
        }
        if (bottom.isEmpty()) {
            bottom = blocks;
        }
        // Bottom row runs along X or Z from the min corner, so sort by distance from it.
        bottom.sort((a, b) -> Integer.compare(a.getX() + a.getZ(), b.getX() + b.getZ()));
        Block mid = bottom.get(bottom.size() / 2);
        return new Location(mid.getWorld(), mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5, yaw, pitch);
    }
}
