package qouteall.imm_ptl.core.mixin.common;

import de.nick1st.imm_ptl.events.ServerCleanupEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.ducks.IEMinecraftServer;
import qouteall.imm_ptl.core.network.PacketRedirection;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MixinMinecraftServer implements IEMinecraftServer {
    @Unique
    IPPerServerInfo ipPerServerInfo = new IPPerServerInfo();

    @Inject(
        method = "Lnet/minecraft/server/MinecraftServer;runServer()V",
        at = @At("RETURN")
    )
    private void onServerClose(CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new ServerCleanupEvent((MinecraftServer) (Object) this));
    }

    // Wrap each per-dimension tick with PacketRedirection.withForceRedirect so
    // that any packet sent during the tick (including packets from third-party
    // mods like Sable's sub-level tracking) carries the source dimension tag.
    // Without this, the client resolves the packet against the player's
    // current dimension instead of the packet's source, causing cross-dimension
    // state desyncs after portal teleports.
    @Redirect(
        method = "tickChildren",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerLevel;tick(Ljava/util/function/BooleanSupplier;)V"
        )
    )
    private void ip_wrapServerLevelTick(ServerLevel level, BooleanSupplier hasTimeLeft) {
        PacketRedirection.withForceRedirect(level, () -> level.tick(hasTimeLeft));
    }

    @Override
    public IPPerServerInfo ip_getPerServerInfo() {
        return ipPerServerInfo;
    }
}
