package qouteall.imm_ptl.core.compat.mixin.sable;

import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

// When IP teleports a player across dimensions, Sable's sub-level tracking
// sometimes gets desynced: the server sends a START packet for a plot that
// is still allocated on the client from a previous session on that dimension.
// Vanilla Sable throws IllegalArgumentException, crashing the client.
// On client-side containers only, treat duplicate allocation as a re-sync:
// drop the stale plot so the fresh allocation can proceed. Server-side
// duplicate allocation still throws (it would indicate a real Sable bug).
@Mixin(SubLevelContainer.class)
public abstract class MixinSableSubLevelContainer {

    @Inject(
        method = "allocateSubLevel(Ljava/util/UUID;IILdev/ryanhcode/sable/companion/math/Pose3d;)Ldev/ryanhcode/sable/sublevel/SubLevel;",
        at = @At("HEAD")
    )
    private void ip_dropStalePlot(
        UUID uuid, int x, int z, Pose3d pose,
        CallbackInfoReturnable<SubLevel> cir
    ) {
        SubLevelContainer self = (SubLevelContainer) (Object) this;
        if (!(self.getLevel() instanceof ClientLevel)) {
            return;
        }
        if (self.getSubLevel(x, z) != null) {
            self.removeSubLevel(x, z, SubLevelRemovalReason.REMOVED);
        }
    }
}
