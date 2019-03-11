package portablejim.bbw.core;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.BlockSlab;
import net.minecraft.block.state.IBlockState;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.IFluidBlock;
import portablejim.bbw.BetterBuildersWandsMod;
import portablejim.bbw.basics.EnumFluidLock;
import portablejim.bbw.basics.EnumLock;
import portablejim.bbw.basics.Point3d;
import portablejim.bbw.basics.ReplacementTriplet;
import portablejim.bbw.core.conversion.CustomMapping;
import portablejim.bbw.core.items.ItemBasicWand;
import portablejim.bbw.core.wands.IWand;
import portablejim.bbw.shims.IPlayerShim;
import portablejim.bbw.shims.IWorldShim;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;

/**
 * Does the heavy work of working out the blocks to place and places them.
 */
public class WandWorker {
    private final IWand wand;
    private final IPlayerShim player;
    private final IWorldShim world;

    private HashSet<Point3d> allCandidates = new HashSet<Point3d>();

    public WandWorker(IWand wand, IPlayerShim player, IWorldShim world) {

        this.wand = wand;
        this.player = player;
        this.world = world;
    }

    public ReplacementTriplet getProperItemStack(IWorldShim world, IPlayerShim player, Point3d blockPos, float hitX, float hitY, float hitZ) {
        Block block = world.getBlock(blockPos);
        IBlockState startBlockState = world.getWorld().getBlockState(blockPos.toBlockPos());
        int meta = world.getMetadata(blockPos);
        String blockString = String.format("%s/%s", Block.REGISTRY.getNameForObject(block), meta);
        if(!BetterBuildersWandsMod.instance.configValues.HARD_BLACKLIST_SET.contains(blockString)) {
            ArrayList<CustomMapping> customMappings = BetterBuildersWandsMod.instance.mappingManager.getMappings(block, meta);
            for (CustomMapping customMapping : customMappings) {
                if(player.countItems(customMapping.getItems()) > 0) {
                    return new ReplacementTriplet(customMapping.getLookBlock().getStateFromMeta(customMapping.getMeta()),
                            customMapping.getItems(), customMapping.getPlaceBlock().getStateFromMeta(customMapping.getPlaceMeta()));
                }
            }

            // Handle slabs specially.
            if(startBlockState.getBlock() instanceof BlockSlab) {
                Item itemDropped = startBlockState.getBlock().getItemDropped(startBlockState, world.rand(), 0);
                ItemStack itemStackDropped = new ItemStack(itemDropped, startBlockState.getBlock().quantityDropped(world.rand()), startBlockState.getBlock().damageDropped(startBlockState));
                if(player.countItems(itemStackDropped) > 0) {
                    return new ReplacementTriplet(startBlockState, itemStackDropped, startBlockState);
                }
            }

            RayTraceResult rayTraceResult = ForgeHooks.rayTraceEyes(player.getPlayer(), player.getReachDistance());
            if(rayTraceResult == null) {
                return null;
            }
            ItemStack exactItemstack = block.getPickBlock(startBlockState, rayTraceResult, world.getWorld(), blockPos.toBlockPos(), player.getPlayer());
            if (player.countItems(exactItemstack) > 0) {
                if(exactItemstack.getItem() instanceof ItemBlock) {
                    //IBlockState newState = ((ItemBlock) exactItemstack.getItem()).getBlock().getStateFromMeta(exactItemstack.getMetadata());
                    IBlockState newState = ((ItemBlock)exactItemstack.getItem()).getBlock().getStateForPlacement(world.getWorld(), blockPos.toBlockPos(), player.getPlayer().getHorizontalFacing(), hitX, hitY, hitZ, meta, player.getPlayer(), EnumHand.MAIN_HAND);
                    return new ReplacementTriplet(startBlockState, exactItemstack, newState);
                }
                else {
                    return null;
                }
            }
            return getEquivalentItemStack(blockPos);
        }
        return null;
    }

    private ReplacementTriplet getEquivalentItemStack(Point3d blockPos) {
        Block block = world.getBlock(blockPos);
        int meta = world.getMetadata(blockPos);
        IBlockState startBlockState = world.getWorld().getBlockState(blockPos.toBlockPos());
        //ArrayList<ItemStack> items = new ArrayList<ItemStack>();
        String blockString = String.format("%s/%s", Block.REGISTRY.getNameForObject(block), meta);

        if (!block.canSilkHarvest(world.getWorld(), blockPos.toBlockPos(), block.getStateFromMeta(meta), player.getPlayer())) {
            if(!BetterBuildersWandsMod.instance.configValues.SOFT_BLACKLIST_SET.contains(blockString)) {
                Item dropped = block.getItemDropped(block.getStateFromMeta(meta), new Random(), 0);
                ItemStack stack = new ItemStack(dropped, block.quantityDropped(block.getStateFromMeta(meta), 0, new Random()), block.damageDropped(block.getStateFromMeta(meta)));
                if (stack.getItem() instanceof ItemBlock) {
                    return new ReplacementTriplet(startBlockState, stack, ((ItemBlock) stack.getItem()).getBlock().getStateFromMeta(stack.getMetadata()));
                }
            }
        }
        //ForgeEventFactory.fireBlockHarvesting(items,this.world.getWorld(), block, blockPos.x, blockPos.y, blockPos.z, world.getMetadata(blockPos), 0, 1.0F, true, this.player.getPlayer());
        return null;
    }

    private boolean shouldContinue(Point3d currentCandidate, Block targetBlock, int targetMetadata, EnumFacing facing, Block candidateSupportingBlock, int candidateSupportingMeta, AxisAlignedBB blockBB, EnumFluidLock fluidLock) {
        if(!world.blockIsAir(currentCandidate)){
            Block currrentCandidateBlock = world.getBlock(currentCandidate);
            if(!(fluidLock == EnumFluidLock.IGNORE && currrentCandidateBlock != null && (currrentCandidateBlock instanceof IFluidBlock || currrentCandidateBlock instanceof BlockLiquid))) return false;
        };
        /*if((FluidRegistry.getFluid("water").getBlock().equals(world.getBlock(currentCandidate)) || FluidRegistry.getFluid("lava").getBlock().equals(world.getBlock(currentCandidate)))
                && world.getMetadata(currentCandidate) == 0){
            return false;
        }*/
        if(!targetBlock.equals(candidateSupportingBlock)) return false;
        if(targetMetadata != candidateSupportingMeta) return false;
        //if(targetBlock instanceof BlockCrops) return false;
        if(!targetBlock.canPlaceBlockAt(world.getWorld(), new BlockPos(currentCandidate.x, currentCandidate.y, currentCandidate.z))) return false;
        if(!targetBlock.isReplaceable(world.getWorld(), new BlockPos(currentCandidate.x, currentCandidate.y, currentCandidate.z))) return false;

        return !world.entitiesInBox(blockBB);

    }

    public LinkedList<Point3d> getBlockPositionList(Point3d blockLookedAt, EnumFacing placeDirection, int maxBlocks, EnumLock directionLock, EnumLock faceLock, EnumFluidLock fluidLock) {
        LinkedList<Point3d> candidates = new LinkedList<>();
        LinkedList<Point3d> toPlace = new LinkedList<>();

        Block targetBlock = world.getBlock(blockLookedAt);
        int targetMetadata = world.getMetadata(blockLookedAt);
        Point3d startingPoint = blockLookedAt.move(placeDirection);

        int directionMaskInt = directionLock.mask;
        int faceMaskInt = faceLock.mask;

        if (((directionLock != EnumLock.HORIZONTAL && directionLock != EnumLock.VERTICAL) || (placeDirection != EnumFacing.UP && placeDirection != EnumFacing.DOWN))
                && (directionLock != EnumLock.NORTHSOUTH || (placeDirection != EnumFacing.NORTH && placeDirection != EnumFacing.SOUTH))
                && (directionLock != EnumLock.EASTWEST || (placeDirection != EnumFacing.EAST && placeDirection != EnumFacing.WEST))) {
            candidates.add(startingPoint);
        }
        while(candidates.size() > 0 && toPlace.size() < maxBlocks) {
            Point3d currentCandidate = candidates.removeFirst();

            try {
                Point3d supportingPoint = currentCandidate.move(placeDirection.getOpposite());
                Block candidateSupportingBlock = world.getBlock(supportingPoint);
                int candidateSupportingMeta = world.getMetadata(supportingPoint);
                AxisAlignedBB blockBB = targetBlock.getStateFromMeta(targetMetadata).getBoundingBox(world.getWorld(), new BlockPos(currentCandidate.x, currentCandidate.y, currentCandidate.z)).offset(currentCandidate.x, currentCandidate.y, currentCandidate.z);
                if (shouldContinue(currentCandidate, targetBlock, targetMetadata, placeDirection, candidateSupportingBlock, candidateSupportingMeta, blockBB, fluidLock)
                        && allCandidates.add(currentCandidate)) {
                    toPlace.add(currentCandidate);

                    switch (placeDirection) {
                        case DOWN:
                        case UP:
                            if ((faceMaskInt & EnumLock.UP_DOWN_MASK) > 0) {
                                if ((directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.NORTH));
                                if ((directionMaskInt & EnumLock.EAST_WEST_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.EAST));
                                if ((directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.SOUTH));
                                if ((directionMaskInt & EnumLock.EAST_WEST_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.WEST));
                                if ((directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0 && (directionMaskInt & EnumLock.EAST_WEST_MASK) > 0) {
                                    candidates.add(currentCandidate.move(EnumFacing.NORTH).move(EnumFacing.EAST));
                                    candidates.add(currentCandidate.move(EnumFacing.NORTH).move(EnumFacing.WEST));
                                    candidates.add(currentCandidate.move(EnumFacing.SOUTH).move(EnumFacing.EAST));
                                    candidates.add(currentCandidate.move(EnumFacing.SOUTH).move(EnumFacing.WEST));
                                }
                            }
                            break;
                        case NORTH:
                        case SOUTH:
                            if ((faceMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0) {
                                if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.UP));
                                if ((directionMaskInt & EnumLock.EAST_WEST_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.EAST));
                                if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.DOWN));
                                if ((directionMaskInt & EnumLock.EAST_WEST_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.WEST));
                                if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0 && (directionMaskInt & EnumLock.EAST_WEST_MASK) > 0) {
                                    candidates.add(currentCandidate.move(EnumFacing.UP).move(EnumFacing.EAST));
                                    candidates.add(currentCandidate.move(EnumFacing.UP).move(EnumFacing.WEST));
                                    candidates.add(currentCandidate.move(EnumFacing.DOWN).move(EnumFacing.EAST));
                                    candidates.add(currentCandidate.move(EnumFacing.DOWN).move(EnumFacing.WEST));
                                }
                            }
                            break;
                        case WEST:
                        case EAST:
                            if ((faceMaskInt & EnumLock.EAST_WEST_MASK) > 0) {
                                if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.UP));
                                if ((directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.NORTH));
                                if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.DOWN));
                                if ((directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0)
                                    candidates.add(currentCandidate.move(EnumFacing.SOUTH));
                                if ((directionMaskInt & EnumLock.UP_DOWN_MASK) > 0 && (directionMaskInt & EnumLock.NORTH_SOUTH_MASK) > 0) {
                                    candidates.add(currentCandidate.move(EnumFacing.UP).move(EnumFacing.NORTH));
                                    candidates.add(currentCandidate.move(EnumFacing.UP).move(EnumFacing.SOUTH));
                                    candidates.add(currentCandidate.move(EnumFacing.DOWN).move(EnumFacing.NORTH));
                                    candidates.add(currentCandidate.move(EnumFacing.DOWN).move(EnumFacing.SOUTH));
                                }
                            }
                    }
                }
            }
            catch(Exception e) {
                // Can't do anything, could be anything.
                // Skip if anything goes wrong.
            }
        }
        return toPlace;
    }

    public ArrayList<Point3d> placeBlocks(ItemStack wandItem, LinkedList<Point3d> blockPosList, IBlockState targetBlock, ItemStack sourceItems, EnumFacing side, float hitX, float hitY, float hitZ) {
        ArrayList<Point3d> placedBlocks = new ArrayList<>();
        EnumHand hand = player.getPlayer().getHeldItemMainhand().getItem() instanceof ItemBasicWand ? EnumHand.MAIN_HAND : EnumHand.OFF_HAND;
        for(Point3d blockPos : blockPosList) {
            boolean blockPlaceSuccess = false;
            BlockSnapshot snapshot = new BlockSnapshot(world.getWorld(), blockPos.toBlockPos(), targetBlock);
            BlockEvent.PlaceEvent placeEvent = new BlockEvent.PlaceEvent(snapshot, targetBlock, player.getPlayer(), hand);
            MinecraftForge.EVENT_BUS.post(placeEvent);
            if(!placeEvent.isCanceled()) {
                blockPlaceSuccess = world.setBlock(blockPos, targetBlock);
            }

            if(blockPlaceSuccess) {
                world.playPlaceAtBlock(blockPos, targetBlock.getBlock());
                placedBlocks.add(blockPos);
                if (!player.isCreative()) {
                    wand.placeBlock(wandItem, player.getPlayer());
                }
                boolean takeFromInventory = player.useItem(sourceItems);
                if(!takeFromInventory) {
                    BetterBuildersWandsMod.logger.info("BBW takeback: %s", blockPos.toString());
                    world.setBlockToAir(blockPos);
                    placedBlocks.remove(placedBlocks.size() - 1);
                }

                targetBlock.getBlock().onBlockPlacedBy(world.getWorld(), blockPos.toBlockPos(), targetBlock, player.getPlayer(), sourceItems);
            }
        }

        return placedBlocks;
    }
}
