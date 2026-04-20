package qouteall.imm_ptl.core.compat.mixin.sable;

import dev.ryanhcode.sable.mixinterface.clip_overwrite.ClipContextExtension;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;
import qouteall.imm_ptl.core.portal.Portal;

// IP's cross-portal raytrace uses BlockGetter.traverseBlocks directly, which
// bypasses Sable's @Overwrite on BlockGetter.clip that handles sub-levels.
// After IP's normal raytrace, do a sub-level-only clip on the remote world
// (via Sable's sable$setIgnoreMainLevel flag) and take the closer hit.
@Mixin(BlockManipulationClient.class)
public abstract class MixinBlockManipulationClient {

    @Inject(
        method = "updateTargetedBlockThroughPortal",
        at = @At("RETURN")
    )
    private static void ip_alsoRaytraceSableSubLevels(
        Vec3 cameraPos, Vec3 viewVector, ResourceKey<Level> playerDimension,
        double beginDistance, double endDistance, Portal portal,
        CallbackInfo ci
    ) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        ClientLevel remoteWorld = ClientWorldLoader.getWorld(portal.getDestDim());
        if (remoteWorld == null) return;

        Vec3 from = portal.transformPoint(cameraPos.add(viewVector.scale(beginDistance)));
        Vec3 to = portal.transformPoint(cameraPos.add(viewVector.scale(endDistance)));

        ClipContext subClipContext = new ClipContext(
            from, to,
            ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE,
            mc.player
        );
        ((ClipContextExtension) subClipContext).sable$setIgnoreMainLevel(true);

        BlockHitResult subHit;
        try {
            subHit = remoteWorld.clip(subClipContext);
        } catch (Throwable t) {
            return;
        }
        if (subHit == null || subHit.getType() == HitResult.Type.MISS) return;

        Vec3 subHitLocationInWorld = ip_projectHitLocationToWorld(remoteWorld, subHit);
        double subDistSqr = subHitLocationInWorld.distanceToSqr(from);

        HitResult currentHit = BlockManipulationClient.remoteHitResult;
        boolean takeSub = true;

        if (currentHit instanceof BlockHitResult currentBhr
            && currentHit.getType() != HitResult.Type.MISS
            && !remoteWorld.getBlockState(currentBhr.getBlockPos()).isAir()) {
            // IP's raytrace found a real main-world block; only replace if the sub-level hit is closer
            double currentDistSqr = currentHit.getLocation().distanceToSqr(from);
            takeSub = subDistSqr < currentDistSqr;
        }

        if (takeSub) {
            BlockManipulationClient.remoteHitResult = subHit;
            BlockManipulationClient.remotePointedDim = portal.getDestDim();
            mc.hitResult = ip_createMissed(from, to);
        }
    }

    private static BlockHitResult ip_createMissed(Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from).normalize();
        return BlockHitResult.miss(to, Direction.getNearest(dir.x, dir.y, dir.z), BlockPos.containing(to));
    }

    private static Vec3 ip_projectHitLocationToWorld(ClientLevel world, BlockHitResult hitResult) {
        SubLevel subLevel = Sable.HELPER.getContaining(world, hitResult.getBlockPos());
        if (subLevel == null) {
            subLevel = Sable.HELPER.getContaining(world, hitResult.getLocation());
        }
        if (subLevel == null) {
            return hitResult.getLocation();
        }

        return JOMLConversion.toMojang(
            subLevel.logicalPose().transformPosition(
                JOMLConversion.toJOML(hitResult.getLocation())
            )
        );
    }
}
