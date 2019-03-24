package portablejim.bbw.shims;

import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/**
 * Wrap functions for a creative player.
 */
public class CreativePlayerShim extends BasicPlayerShim implements IPlayerShim {

    public CreativePlayerShim(EntityPlayer player) {
        super(player);
        this.assumedReachDistance = 5F;
    }

    @Override
    public int countItems(ItemStack itemStack) {
        return Integer.MAX_VALUE;
    }

    @Override
    public ItemStack useItem(ItemStack itemStack) {
        return itemStack;
    }

    @Override
    public ItemStack getNextItem(Block block, int meta) {
        return new ItemStack(block, 1, meta);
    }
}
