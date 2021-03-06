// $Id$
/*
 * CraftBook Copyright (C) 2010 sk89q <http://www.sk89q.com>
 * 
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program. If not,
 * see <http://www.gnu.org/licenses/>.
 */

package com.sk89q.craftbook.mech;

import java.util.HashSet;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.inventory.ItemStack;

import com.sk89q.craftbook.AbstractCraftBookMechanic;
import com.sk89q.craftbook.ChangedSign;
import com.sk89q.craftbook.LocalPlayer;
import com.sk89q.craftbook.bukkit.CraftBookPlugin;
import com.sk89q.craftbook.bukkit.util.BukkitUtil;
import com.sk89q.craftbook.util.BlockUtil;
import com.sk89q.craftbook.util.EventUtil;
import com.sk89q.craftbook.util.ItemInfo;
import com.sk89q.craftbook.util.ProtectionUtil;
import com.sk89q.craftbook.util.SignUtil;
import com.sk89q.craftbook.util.events.SignClickEvent;
import com.sk89q.craftbook.util.events.SourcedBlockRedstoneEvent;
import com.sk89q.worldedit.BlockWorldVector;
import com.sk89q.worldedit.Vector;
import com.sk89q.worldedit.regions.CuboidRegion;

/**
 * Handler for gates. Gates are merely fence blocks. When they are closed or open, a nearby fence will be found,
 * the algorithm will traverse to the
 * top-most connected fence block, and then proceed to recurse to the sides up to a certain number of fences. To the
 * fences that it gets to, it will
 * iterate over the blocks below to open or close the gate.
 *
 * @author sk89q
 */
public class Gate extends AbstractCraftBookMechanic {

    /**
     * Toggles the gate closest to a location.
     *
     * @param player
     * @param pt
     * @param smallSearchSize
     * @param close null to toggle, true to close, false to open
     *
     * @return true if a gate was found and blocks were changed; false otherwise.
     */
    public boolean toggleGates(LocalPlayer player, Block block, boolean smallSearchSize, Boolean close) {

        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();

        boolean foundGate = false;

        Set<GateColumn> visitedColumns = new HashSet<GateColumn>();

        if (smallSearchSize) {
            // Toggle nearby gates
            for (int x1 = x - 1; x1 <= x + 1; x1++) {
                for (int y1 = y - 2; y1 <= y + 1; y1++) {
                    for (int z1 = z - 1; z1 <= z + 1; z1++) {
                        if (recurseColumn(player, BukkitUtil.toChangedSign(block), block.getWorld().getBlockAt(x1, y1, z1), visitedColumns, close, smallSearchSize)) {
                            foundGate = true;
                        }
                    }
                }
            }
        } else {
            // Toggle nearby gates
            for (int x1 = x - CraftBookPlugin.inst().getConfiguration().gateSearchRadius; x1 <= x + CraftBookPlugin.inst().getConfiguration().gateSearchRadius; x1++) {
                for (int y1 = y - CraftBookPlugin.inst().getConfiguration().gateSearchRadius; y1 <= y + CraftBookPlugin.inst().getConfiguration().gateSearchRadius*2; y1++) {
                    for (int z1 = z - CraftBookPlugin.inst().getConfiguration().gateSearchRadius; z1 <= z + CraftBookPlugin.inst().getConfiguration().gateSearchRadius; z1++) {
                        if (recurseColumn(player, BukkitUtil.toChangedSign(block), block.getWorld().getBlockAt(x1, y1, z1), visitedColumns, close, smallSearchSize)) {
                            foundGate = true;
                        }
                    }
                }
            }
        }

        // bag.flushChanges();

        return foundGate && visitedColumns.size() > 0;
    }

    /**
     * Toggles one column of gate.
     *
     * @param player The Player
     * @param sign The sign block.
     * @param block A part of the column.
     * @param visitedColumns Previously visited columns.
     * @param close Should close or open.
     *
     * @return true if a gate column was found and blocks were changed; false otherwise.
     */
    private boolean recurseColumn(LocalPlayer player, ChangedSign sign, Block block, Set<GateColumn> visitedColumns, Boolean close, boolean smallSearchSize) {

        if (CraftBookPlugin.inst().getConfiguration().gateLimitColumns && visitedColumns.size() > CraftBookPlugin.inst().getConfiguration().gateColumnLimit)
            return false;

        if (!isValidGateBlock(sign, smallSearchSize, new ItemInfo(block), true)) return false;

        CraftBookPlugin.logDebugMessage("Found a possible gate column at " + block.getX() + ":" + block.getY() + ":" + block.getZ(), "gates.search");

        int x = block.getX();
        int z = block.getZ();

        GateColumn column = new GateColumn(sign, block, smallSearchSize);

        // The block above the gate cannot be air -- it has to be some
        // non-fence block
        if (block.getWorld().getBlockAt(x, column.getStartingY() + 1, z).getType() == Material.AIR) return false;

        if (visitedColumns.contains(column)) return false;

        visitedColumns.add(column);

        if (close == null)
            close = !isValidGateBlock(sign, smallSearchSize, new ItemInfo(block.getWorld().getBlockAt(x, column.getStartingY() - 1, z)), true);

        CraftBookPlugin.logDebugMessage("Valid column at " + block.getX() + ":" + block.getY() + ":" + block.getZ() + " is being " + (close ? "closed" : "opened"), "gates.search");
        CraftBookPlugin.logDebugMessage("Column Top: " + column.getStartingY() + " End: " + column.getEndingY(), "gates.search");
        // Recursively go to connected fence blocks of the same level
        // and 'close' or 'open' them
        return toggleColumn(player, sign, block, column, close, visitedColumns, smallSearchSize);
    }

    /**
     * Actually does the closing/opening. Also recurses to nearby columns.
     *
     * @param player The player.
     * @param signBlock The sign block.
     * @param block The top point of the gate.
     * @param close To open or close.
     * @param visitedColumns Previously searched columns.
     */
    private boolean toggleColumn(LocalPlayer player, ChangedSign sign, Block block, GateColumn column, boolean close, Set<GateColumn> visitedColumns, boolean smallSearchSize) {

        // If we want to close the gate then we replace air/water blocks
        // below with fence blocks; otherwise, we want to replace fence
        // blocks below with air
        ItemInfo item;
        if (close)
            item = new ItemInfo(column.getStartingPoint());
        else
            item = new ItemInfo(Material.AIR, 0);

        CraftBookPlugin.logDebugMessage("Setting column at " + block.getX() + ":" + block.getY() + ":" + block.getZ() + " to " + item.toString(), "gates.search");

        for (Vector bl : column.getRegion()) {

            Block blo = BukkitUtil.toBlock(new BlockWorldVector(BukkitUtil.toWorldVector(block).getWorld(), bl));

            //sign = BukkitUtil.toChangedSign(sign.getSign().getBlock());

            if(sign == null) {
                CraftBookPlugin.logDebugMessage("Invalid Sign!", "gates.search");
                return false;
            }

            ChangedSign otherSign = null;

            Block ot = SignUtil.getNextSign(block, sign.getLine(1), 4);
            if(ot != null)
                otherSign = BukkitUtil.toChangedSign(ot);

            if (sign.getLine(2).equalsIgnoreCase("NoReplace")) {
                // If NoReplace is on line 3 of sign, do not replace blocks.
                if (blo.getType() != Material.AIR && !isValidGateBlock(sign, smallSearchSize, new ItemInfo(blo), true))
                    break;
            } else // Allowing water allows the use of gates as flood gates
                if (!canPassThrough(sign, smallSearchSize, blo))
                    break;

            // bag.setBlockID(w, x, y1, z, ID);
            if (CraftBookPlugin.inst().getConfiguration().safeDestruction) {
                if (!close || hasEnoughBlocks(sign, otherSign)) {
                    if (!close && isValidGateBlock(sign, smallSearchSize, new ItemInfo(blo), true))
                        addBlocks(sign, 1);
                    else if (close && canPassThrough(sign, smallSearchSize, blo) && isValidGateBlock(sign, smallSearchSize, item, true) && !item.isSame(blo))
                        removeBlocks(sign, 1);
                    blo.setTypeIdAndData(item.getId(), (byte) item.getData(), true);
                } else if (close && !hasEnoughBlocks(sign, otherSign) && isValidGateBlock(sign, smallSearchSize, item, true))
                    if (player != null) {
                        player.printError("mech.not-enough-blocks");
                        return false;
                    }
            } else
                blo.setTypeIdAndData(item.getId(), (byte) item.getData(), true);

            CraftBookPlugin.logDebugMessage("Set block " + bl.getX() + ":" + bl.getY() + ":" + bl.getZ() + " to " + item.toString(), "gates.search");

            recurseColumn(player, sign, blo.getRelative(1, 0, 0), visitedColumns, close, smallSearchSize);
            recurseColumn(player, sign, blo.getRelative(-1, 0, 0), visitedColumns, close, smallSearchSize);
            recurseColumn(player, sign, blo.getRelative(0, 0, 1), visitedColumns, close, smallSearchSize);
            recurseColumn(player, sign, blo.getRelative(0, 0, -1), visitedColumns, close, smallSearchSize);
        }

        recurseColumn(player, sign, column.getStartingPoint().getRelative(1, 0, 0), visitedColumns, close, smallSearchSize);
        recurseColumn(player, sign, column.getStartingPoint().getRelative(-1, 0, 0), visitedColumns, close, smallSearchSize);
        recurseColumn(player, sign, column.getStartingPoint().getRelative(0, 0, 1), visitedColumns, close, smallSearchSize);
        recurseColumn(player, sign, column.getStartingPoint().getRelative(0, 0, -1), visitedColumns, close, smallSearchSize);

        recurseColumn(player, sign, column.getStartingPoint().getRelative(1, 1, 0), visitedColumns, close, smallSearchSize);
        recurseColumn(player, sign, column.getStartingPoint().getRelative(-1, 1, 0), visitedColumns, close, smallSearchSize);
        recurseColumn(player, sign, column.getStartingPoint().getRelative(0, 1, 1), visitedColumns, close, smallSearchSize);
        recurseColumn(player, sign, column.getStartingPoint().getRelative(0, 1, -1), visitedColumns, close, smallSearchSize);
        return true;
    }

    /**
     * Raised when a block is right clicked.
     *
     * @param event
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onRightClick(SignClickEvent event) {

        if(event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        LocalPlayer player = CraftBookPlugin.inst().wrapPlayer(event.getPlayer());

        ChangedSign sign = event.getSign();

        if (!sign.getLine(1).equals("[Gate]") && !sign.getLine(1).equals("[DGate]")) return;

        boolean smallSearchSize = sign.getLine(1).equals("[DGate]");

        ItemInfo gateBlock = getGateBlock(sign, smallSearchSize);

        if (CraftBookPlugin.inst().getConfiguration().safeDestruction && (gateBlock == null || gateBlock.getType() == Material.AIR || gateBlock.getType() == player.getHeldItemInfo().getType()) && isValidGateBlock(sign, smallSearchSize, player.getHeldItemInfo(), false)) {

            if (!player.hasPermission("craftbook.mech.gate.restock")) {
                if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                    player.printError("mech.restock-permission");
                return;
            }

            int amount = 1;
            if (event.getPlayer().isSneaking())
                amount = Math.min(5, event.getPlayer().getItemInHand().getAmount());
            addBlocks(sign, amount);

            if (!(event.getPlayer().getGameMode() == GameMode.CREATIVE))
                if (event.getPlayer().getItemInHand().getAmount() <= amount)
                    event.getPlayer().setItemInHand(null);
                else
                    event.getPlayer().getItemInHand().setAmount(event.getPlayer().getItemInHand().getAmount() - amount);

            player.print("mech.restock");
            event.setCancelled(true);
            return;
        }

        if (!player.hasPermission("craftbook.mech.gate.use")) {
            if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                player.printError("mech.use-permission");
            return;
        }

        if(!ProtectionUtil.canUse(event.getPlayer(), event.getClickedBlock().getLocation(), event.getBlockFace(), event.getAction())) {
            if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                player.printError("area.use-permissions");
            return;
        }

        if (toggleGates(player, event.getClickedBlock(), smallSearchSize, null))
            player.print("mech.gate.toggle");
        else
            player.printError("mech.gate.not-found");

        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockRedstoneChange(final SourcedBlockRedstoneEvent event) {

        if (!CraftBookPlugin.inst().getConfiguration().gateAllowRedstone) return;

        if (event.isMinor()) return;

        if (!SignUtil.isSign(event.getBlock())) return;

        final ChangedSign sign = BukkitUtil.toChangedSign(event.getBlock());
        if (!sign.getLine(1).equals("[Gate]") && !sign.getLine(1).equals("[DGate]")) return;

        CraftBookPlugin.inst().getServer().getScheduler().runTaskLater(CraftBookPlugin.inst(), new Runnable() {

            @Override
            public void run() {

                toggleGates(null, event.getBlock(), sign.getLine(1).equals("[DGate]"), event.getNewCurrent() > 0);
            }
        }, 2);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSignChange(SignChangeEvent event) {

        if(!event.getLine(1).equalsIgnoreCase("[Gate]") && !event.getLine(1).equalsIgnoreCase("[DGate]")) return;

        LocalPlayer player = CraftBookPlugin.inst().wrapPlayer(event.getPlayer());

        if (event.getLine(1).equalsIgnoreCase("[Gate]")) {
            if(!player.hasPermission("craftbook.mech.gate")) {
                if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                    player.printError("mech.create-permission");
                SignUtil.cancelSign(event);
                return;
            }
            // get the material that this gate should toggle and verify it
            String line0 = event.getLine(0).trim();
            if (line0 != null && !line0.isEmpty()) {
                if (!isValidGateBlock(new ItemInfo(line0))) {
                    player.printError("Line 1 needs to be a valid block id.");
                    SignUtil.cancelSign(event);
                    return;
                }
            }
            event.setLine(1, "[Gate]");
            if (event.getLine(3).equalsIgnoreCase("infinite") && !player.hasPermission("craftbook.mech.gate.infinite"))
                event.setLine(3, "0");
            else if (!event.getLine(3).equalsIgnoreCase("infinite"))
                event.setLine(3, "0");
            player.print("mech.gate.create");
        } else if (event.getLine(1).equalsIgnoreCase("[DGate]")) {
            if (!player.hasPermission("craftbook.mech.gate") && !player.hasPermission("craftbook.mech.dgate")) {
                if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                    player.printError("mech.create-permission");
                SignUtil.cancelSign(event);
                return;
            }
            // get the material that this gate should toggle and verify it
            String line0 = event.getLine(0).trim();
            if (line0 != null && !line0.isEmpty()) {
                if (!isValidGateBlock(new ItemInfo(line0))) {
                    player.printError("mech.gate.valid-item");
                    SignUtil.cancelSign(event);
                    return;
                }
            }
            event.setLine(1, "[DGate]");
            if (event.getLine(3).equalsIgnoreCase("infinite") && !player.hasPermission("craftbook.mech.gate.infinite"))
                event.setLine(3, "0");
            else if (!event.getLine(3).equalsIgnoreCase("infinite"))
                event.setLine(3, "0");
            player.print("mech.dgate.create");
        }
    }

    public boolean isValidGateBlock(ItemInfo block) {

        return CraftBookPlugin.inst().getConfiguration().gateBlocks.contains(block);
    }

    /**
     * Checks if a block can be used in gate.
     * 
     * @param signBlock The sign block.
     * @param smallSearchSize Search small or large.
     * @param block The block to check.
     * @param check Should search.
     * @return
     */
    public boolean isValidGateBlock(ChangedSign sign, boolean smallSearchSize, ItemInfo block, boolean check) {

        ItemInfo type;

        if (sign != null && !sign.getLine(0).isEmpty()) {
            try {
                ItemInfo def = new ItemInfo(sign.getLine(0));
                return block.equals(def);
            } catch (Exception e) {
                if (check) {
                    type = getGateBlock(sign, smallSearchSize);
                    if(type == null || type.getType() == Material.AIR)
                        return block.equals(type);
                }
                return CraftBookPlugin.inst().getConfiguration().gateBlocks.contains(block);
            }
        } else if(check && (type = getGateBlock(sign, smallSearchSize)) != null)
            return block.equals(type);
        else
            return CraftBookPlugin.inst().getConfiguration().gateBlocks.contains(block);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBlockBreak(BlockBreakEvent event) {

        if(EventUtil.shouldIgnoreEvent(event)) return;

        if (!SignUtil.isSign(event.getBlock())) return;

        final ChangedSign sign = BukkitUtil.toChangedSign(event.getBlock());
        if (!sign.getLine(1).equals("[Gate]") && !sign.getLine(1).equals("[DGate]")) return;

        LocalPlayer player = CraftBookPlugin.inst().wrapPlayer(event.getPlayer());

        if(!ProtectionUtil.canBuild(event.getPlayer(), event.getBlock().getLocation(), false)) {
            if(CraftBookPlugin.inst().getConfiguration().showPermissionMessages)
                player.printError("area.break-permissions");
            return;
        }

        int amount = getBlocks(sign);
        if (amount > 0) {
            ItemInfo type = getGateBlock(sign, sign.getLine(1).equals("[DGate]"));
            if(type == null || type.getType() == Material.AIR)
                type = new ItemInfo(Material.FENCE, 0);
            ItemStack toDrop = new ItemStack(type.getType(), amount, (short) type.getData());
            event.getBlock().getWorld().dropItemNaturally(BlockUtil.getBlockCentre(event.getBlock()), toDrop);
        }
    }

    private boolean canPassThrough(ChangedSign sign, boolean smallSearchSize, Block t) {

        Material[] passableBlocks = new Material[9];
        passableBlocks[0] = Material.WATER;
        passableBlocks[1] = Material.STATIONARY_WATER;
        passableBlocks[2] = Material.LAVA;
        passableBlocks[3] = Material.STATIONARY_LAVA;
        passableBlocks[4] = Material.SNOW;
        passableBlocks[5] = Material.LONG_GRASS;
        passableBlocks[6] = Material.VINE;
        passableBlocks[7] = Material.DEAD_BUSH;
        passableBlocks[8] = Material.AIR;

        for (Material aPassableBlock : passableBlocks) { if (aPassableBlock == t.getType()) return true; }

        return isValidGateBlock(sign, smallSearchSize, new ItemInfo(t), true);
    }

    public ItemInfo getGateBlock(ChangedSign sign, boolean smallSearchSize) {

        ItemInfo gateBlock = null;

        if (sign != null && !sign.getLine(0).isEmpty()) {
            try {
                return new ItemInfo(sign.getLine(0));
            } catch (Exception ignored) {
            }
        }
        int x = sign.getX();
        int y = sign.getY();
        int z = sign.getZ();

        if (smallSearchSize) {
            for (int x1 = x - 1; x1 <= x + 1; x1++) {
                for (int y1 = y - 2; y1 <= y + 1; y1++) {
                    for (int z1 = z - 1; z1 <= z + 1; z1++) {
                        if (getFirstBlock(sign, sign.getSign().getBlock().getWorld().getBlockAt(x1, y1, z1), smallSearchSize) != null) {
                            gateBlock = new ItemInfo(getFirstBlock(sign, sign.getSign().getBlock().getWorld().getBlockAt(x1, y1, z1), smallSearchSize));
                        }
                    }
                }
            }
        } else {
            for (int x1 = x - CraftBookPlugin.inst().getConfiguration().gateSearchRadius; x1 <= x + CraftBookPlugin.inst().getConfiguration().gateSearchRadius; x1++) {
                for (int y1 = y - CraftBookPlugin.inst().getConfiguration().gateSearchRadius; y1 <= y + CraftBookPlugin.inst().getConfiguration().gateSearchRadius*2; y1++) {
                    for (int z1 = z - CraftBookPlugin.inst().getConfiguration().gateSearchRadius; z1 <= z + CraftBookPlugin.inst().getConfiguration().gateSearchRadius; z1++) {
                        if (getFirstBlock(sign, sign.getSign().getBlock().getWorld().getBlockAt(x1, y1, z1), smallSearchSize) != null) {
                            gateBlock = new ItemInfo(getFirstBlock(sign, sign.getSign().getBlock().getWorld().getBlockAt(x1, y1, z1), smallSearchSize));
                        }
                    }
                }
            }
        }

        if(CraftBookPlugin.inst().getConfiguration().gateEnforceType && gateBlock != null && gateBlock.getType() != Material.AIR && sign != null) {
            sign.setLine(0, gateBlock.toString());
            sign.update(false);
        }

        return gateBlock;
    }

    public Block getFirstBlock(ChangedSign sign, Block block, boolean smallSearchSize) {

        if (!isValidGateBlock(sign, smallSearchSize, new ItemInfo(block), false)) return null;

        return block;
    }

    public void removeBlocks(ChangedSign s, int amount) {

        if (s.getLine(3).equalsIgnoreCase("infinite")) return;
        setBlocks(s, getBlocks(s) - amount);
    }

    public void addBlocks(ChangedSign s, int amount) {

        if (s.getLine(3).equalsIgnoreCase("infinite")) return;
        setBlocks(s, getBlocks(s) + amount);
    }

    public void setBlocks(ChangedSign s, int amount) {

        if (s.getLine(3).equalsIgnoreCase("infinite")) return;
        s.setLine(3, String.valueOf(amount));
        s.update(false);
    }

    public int getBlocks(ChangedSign s) {

        if (s.getLine(3).equalsIgnoreCase("infinite")) return 0;
        return getBlocks(s, null);
    }

    public int getBlocks(ChangedSign s, ChangedSign other) {

        if (s.getLine(3).equalsIgnoreCase("infinite") || other != null && other.getLine(3).equalsIgnoreCase("infinite"))
            return 0;
        int curBlocks = 0;
        try {
            curBlocks = Integer.parseInt(s.getLine(3));
            if(other != null) {
                try {
                    curBlocks += Integer.parseInt(other.getLine(3));
                    setBlocks(s, curBlocks);
                    setBlocks(other, 0);
                } catch (Exception ignored) {
                }
            }
        } catch (Exception e) {
            curBlocks = 0;
        }
        return curBlocks;
    }

    public boolean hasEnoughBlocks(ChangedSign s) {

        return s.getLine(3).equalsIgnoreCase("infinite") || getBlocks(s) > 0;
    }

    public boolean hasEnoughBlocks(ChangedSign s, ChangedSign other) {

        if(other != null) {
            addBlocks(s, getBlocks(other));
            setBlocks(other, 0);
            return hasEnoughBlocks(s);
        } else
            return hasEnoughBlocks(s);
    }

    protected class GateColumn {

        private final ChangedSign sign;
        private final Block block;
        private final boolean smallSearchSize;

        public GateColumn(ChangedSign sign, Block block, boolean smallSearchSize) {

            this.sign = sign;
            this.block = block;
            this.smallSearchSize = smallSearchSize;
        }

        public Block getStartingPoint() {

            return block.getWorld().getBlockAt(block.getX(), getStartingY(), block.getZ());
        }

        public Block getEndingPoint() {

            return block.getWorld().getBlockAt(block.getX(), getEndingY(), block.getZ());
        }

        public int getStartingY() {

            int curY = block.getY();
            int maxY = Math.min(block.getWorld().getMaxHeight(), block.getY() + CraftBookPlugin.inst().getConfiguration().gateColumnHeight);
            for (int y1 = block.getY() + 1; y1 <= maxY; y1++) {
                if (isValidGateBlock(sign, smallSearchSize, new ItemInfo(block.getWorld().getBlockAt(block.getX(), y1, block.getZ())), true))
                    curY = y1;
                else
                    break;
            }

            return curY;
        }

        public int getEndingY() {

            int minY = Math.max(0, block.getY() - CraftBookPlugin.inst().getConfiguration().gateColumnHeight);
            for (int y = block.getY(); y >= minY; y--)
                if (!canPassThrough(sign, smallSearchSize, block.getWorld().getBlockAt(block.getX(), y, block.getZ()))) return y + 1;
            return 0;
        }

        public int getX() {

            return block.getX();
        }

        public int getZ() {

            return block.getZ();
        }

        public CuboidRegion getRegion() {

            return new CuboidRegion(BukkitUtil.toWorldVector(getStartingPoint().getRelative(0, -1, 0)), BukkitUtil.toWorldVector(getEndingPoint()));
        }

        @Override
        public boolean equals(Object o) {

            if(!(o instanceof GateColumn)) return false;
            return ((GateColumn) o).getX() == getX() && ((GateColumn) o).getZ() == getZ() && block.getWorld().getName().equals(((GateColumn) o).block.getWorld().getName());
        }

        @Override
        public int hashCode() {
            // Constants correspond to glibc's lcg algorithm parameters
            return (getX() * 1103515245 + 12345 ^ getZ() * 1103515245 + 12345) * 1103515245 + 12345;
        }
    }
}
