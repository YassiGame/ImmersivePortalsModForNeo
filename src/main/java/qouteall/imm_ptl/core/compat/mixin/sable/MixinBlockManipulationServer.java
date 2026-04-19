package qouteall.imm_ptl.core.compat.mixin.sable;

import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;

// IP's canPlayerReach compares the player's (portal-transformed) position against
// Vec3.atCenterOf(requestPos). For a block inside a Sable sub-level, requestPos is
// in sub-level-local coordinates — the block's real world-space position is that
// local position transformed by the sub-level's pose. Without this override, the
// reach check uses the untransformed (local) coordinate and rejects legitimate
// cross-portal sub-level interactions.
@Mixin(BlockManipulationServer.class)
public abstract class MixinBlockManipulationServer {

    @Inject(
        method = "canPlayerReach",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void ip_allowSubLevelReach(
        ResourceKey<Level> dimension, ServerPlayer player, BlockPos requestPos,
        CallbackInfoReturnable<Boolean> cir
    ) {
        ServerLevel targetLevel = player.server.getLevel(dimension);
        if (targetLevel == null) return;

        SubLevel subLevel = Sable.HELPER.getContaining(targetLevel, requestPos);
        if (subLevel == null) return;

        Pose3dc pose = subLevel.logicalPose();
        Vec3 worldBlockCenter = JOMLConversion.toMojang(
            pose.transformPosition(JOMLConversion.toJOML(Vec3.atCenterOf(requestPos)))
        );

        Vec3 playerPos = player.position();
        double playerScale = ScaleUtils.computeBlockReachScale(player);
        double distanceSquare = 6 * 6 * 4 * 4 * playerScale * playerScale;

        if (player.level().dimension() == dimension
            && playerPos.distanceToSqr(worldBlockCenter) < distanceSquare) {
            cir.setReturnValue(true);
            return;
        }

        boolean reachableViaPortal = IPMcHelper.getNearbyPortals(
            player, IPGlobal.maxNormalPortalRadius
        ).anyMatch(portal ->
            portal.getDestDim() == dimension
                && portal.isInteractableBy(player)
                && portal.transformPoint(playerPos).distanceToSqr(worldBlockCenter)
                    < distanceSquare * portal.getScale() * portal.getScale()
        );

        if (reachableViaPortal) {
            cir.setReturnValue(true);
        }
    }
}
