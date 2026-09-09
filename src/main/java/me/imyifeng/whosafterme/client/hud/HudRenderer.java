package me.imyifeng.whosafterme.client.hud;

import java.util.ArrayList;
import java.util.List;

import me.imyifeng.whosafterme.WhosAfterMe;
import me.imyifeng.whosafterme.client.store.ClientThreats;
import me.imyifeng.whosafterme.config.AimingMode;
import me.imyifeng.whosafterme.config.WhosAfterMeConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
//? if hud_registry {
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
//?} else {
/*import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;*/
//?}
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
//? if fapi_modern_id {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;*/
//?}
import net.minecraft.world.entity.Entity;
//? if mojang_identifier {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;*/
//?}

/**
 * The Threat indicator HUD (spec v1 §5, spec v1 §11 ticket 8): every selected Threat
 * renders independently on the Orbit as a short arc with a centered outward triangle,
 * drawn far-to-near so nearer Threats stack on top. All drawing goes through
 * {@link GuiCanvas}, which submits the {@link IndicatorShapes} triangle meshes into each
 * era's native GPU pipeline - real vector geometry on every anchor, no scanline raster
 * (issue #54), and no raw GL, safe on the 26.x Vulkan backend; the geometry math comes
 * from the pure {@link OrbitGeometry}, {@link CameraProjection}, and
 * {@link IndicatorShapes} modules, so this class is only the per-frame glue.
 *
 * <p>Each frame the renderer reads the config live (values apply without a restart),
 * pulls the selected Threat set from {@link ClientThreats#refresh} - which self-heals
 * staleness and applies the indicator cap - and hides entirely when {@code enabled} is
 * false. Registration targets both HUD API lines (spec v1 §6):
 * {@code HudRenderCallback} before 1.21.6, {@code HudElementRegistry.addLast} from
 * 1.21.6 on, with the 26.1+ extraction-based GUI handled through its
 * {@code GuiGraphicsExtractor} entry.
 */
@Environment(EnvType.CLIENT)
public final class HudRenderer {

    /**
     * The prototype-locked indicator color, red (255, 64, 64) - decision #7. Only the
     * alpha byte varies, with the computed indicator opacity.
     */
    private static final int INDICATOR_COLOR = 0xFF4040;

    //? if mojang_identifier {
    private static final Identifier THREAT_ELEMENT_ID =
            Identifier.fromNamespaceAndPath(WhosAfterMe.MOD_ID, "threat_indicators");
    //?} else {
    /*private static final ResourceLocation THREAT_ELEMENT_ID =
            ResourceLocation.fromNamespaceAndPath(WhosAfterMe.MOD_ID, "threat_indicators");*/
    //?}

    private HudRenderer() {
    }

    /** Registers the HUD renderer on this anchor's HUD API line (spec v1 §6). */
    public static void register() {
        // 26.1+: the extraction-based HudElementRegistry line (spec v1 §6). The two
        // pre-26.1 registrations live in condition blocks: stonecutter keeps exactly
        // one of the three lines per anchor, so the branches stay flat, never nested.
        //? if fapi_modern_id {
        HudElementRegistry.addLast(THREAT_ELEMENT_ID, HudRenderer::onHudElement);
        //?}
        //? if hud_registry && !fapi_modern_id {
        /*HudElementRegistry.addLast(THREAT_ELEMENT_ID, HudRenderer::onHudRender);*/
        //?}
        //? if !hud_registry {
        /*HudRenderCallback.EVENT.register(HudRenderer::onHudRender);*/
        //?}
    }

    /**
     * GuiGraphicsExtractor entry for the 26.1+ extraction-based GUI (spec v1 §6).
     */
    //? if fapi_modern_id {
    private static void onHudElement(GuiGraphicsExtractor graphics, DeltaTracker tickCounter) {
        render(new GuiCanvas(graphics));
    }
    //?}

    /**
     * GuiGraphics entry, shared by the two pre-26.1 lines: the
     * {@code HudRenderCallback} anchors (1.21.1, 1.21.4) and the
     * {@code HudElementRegistry} anchors (1.21.8, 1.21.11) take the same parameters.
     */
    //? if !fapi_modern_id {
    /*private static void onHudRender(GuiGraphics graphics, DeltaTracker tickCounter) {
        render(new GuiCanvas(graphics));
    }*/
    //?}

    /** One frame of indicators (spec v1 §5.4): read, resolve, project, paint. */
    private static void render(GuiCanvas canvas) {
        if (!WhosAfterMeConfig.enabled) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            return;
        }

        List<Integer> threats = ClientThreats.refresh(level, player);
        if (threats.isEmpty()) {
            return;
        }

        double radiusBlocks = Math.max(1, WhosAfterMeConfig.detectionRadius);
        double scale = WhosAfterMeConfig.indicatorScale;
        boolean screenRelative = WhosAfterMeConfig.aimingMode == AimingMode.SCREEN_RELATIVE;
        Orbit orbit = Orbit.of(canvas.guiWidth(), canvas.guiHeight(), WhosAfterMeConfig.orbitSize);

        // Camera frame of the geometry modules: yaw + pi turns Minecraft's yaw into the
        // module frame (OrbitGeometry.relativeBearing docs), pitch flips so positive
        // looks up, and the FOV is the game setting's full angle (spec v1 §5.3).
        double facing = Math.toRadians(player.getYRot()) + Math.PI;
        double pitch = -Math.toRadians(player.getXRot());
        double fov = Math.toRadians(client.options.fov().get());

        List<Indicator> indicators = new ArrayList<>(threats.size());
        for (int id : threats) {
            Entity threat = level.getEntity(id);
            if (threat == null) {
                continue;
            }
            // Distance mirrors the server's Detection-radius predicate (ThreatRules):
            // positions, 3D - so proximity matches detection on both sides (spec v1 §5.2).
            double distance = Math.sqrt(player.distanceToSqr(
                    threat.getX(), threat.getY(), threat.getZ()));
            double proximity = OrbitGeometry.proximity(distance, radiusBlocks);
            double opacity = OrbitGeometry.opacity(
                    proximity,
                    OrbitGeometry.edgeFade(distance, radiusBlocks),
                    WhosAfterMeConfig.indicatorOpacity);
            if (opacity <= 0) {
                // Invisible at the Detection-radius edge - skip the geometry entirely.
                continue;
            }
            double relativeBearing = OrbitGeometry.relativeBearing(
                    Math.atan2(threat.getX() - player.getX(), -(threat.getZ() - player.getZ())),
                    facing);
            double orbitAngle = screenRelative
                    ? OrbitGeometry.screenRelativeOrbitAngle(relativeBearing,
                            CameraProjection.project(facing, pitch, fov,
                                    canvas.guiWidth(), canvas.guiHeight(),
                                    threat.getX() - player.getX(),
                                    threat.getEyeY() - player.getEyeY(),
                                    threat.getZ() - player.getZ()))
                    : OrbitGeometry.absoluteOrbitAngle(relativeBearing);
            indicators.add(new Indicator(
                    distance, orbitAngle, OrbitGeometry.halfArcRad(proximity), opacity));
        }

        // Far-to-near (spec v1 §5.2 stacking): nearer Threats paint over farther ones.
        indicators.sort((a, b) -> OrbitGeometry.compareFarToNear(a.distance(), b.distance()));
        for (Indicator indicator : indicators) {
            int argb = argb(indicator.opacity());
            canvas.triangles(
                    IndicatorShapes.arcVertices(
                            orbit, indicator.orbitAngle(), indicator.halfArcRad(),
                            IndicatorShapes.ARC_THICKNESS_PX * scale, screenRelative),
                    argb);
            canvas.triangles(
                    IndicatorShapes.triangleVertices(
                            orbit, indicator.orbitAngle(),
                            IndicatorShapes.TRIANGLE_SIZE_PX * scale, screenRelative),
                    argb);
        }
    }

    /** The indicator color with the computed opacity in the alpha byte. */
    private static int argb(double opacity) {
        int alpha = (int) Math.round(opacity * 255.0);
        return (Math.clamp(alpha, 0, 255) << 24) | INDICATOR_COLOR;
    }

    /** One Threat's per-frame visual parameters, sortable far-to-near. */
    private record Indicator(double distance, double orbitAngle, double halfArcRad, double opacity) {
    }
}
