package me.imyifeng.whosafterme.client.hud;

//? if !hud_registry {
/*import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;*/
//?}
//? if hud_registry {
import org.joml.Matrix3x2f;
//?}
//? if fapi_modern_id {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import me.imyifeng.whosafterme.mixin.GuiGraphicsExtractorAccessor;
//?}
//? if hud_registry && !fapi_modern_id {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import me.imyifeng.whosafterme.mixin.GuiGraphicsAccessor;*/
//?}

/**
 * The era-neutral drawing surface of the HUD renderer (spec v1 §6): submit quad-ordered
 * triangle meshes from {@link IndicatorShapes} in viewport coordinates. Every Anchor can
 * express those meshes natively (issue #54), with the era fork absorbed here so the
 * renderer never branches:
 *
 * <ul>
 * <li>1.21.1 - the immediate-mode {@code GuiGraphics.bufferSource()} (the only anchor
 * that still exposes it): vertices into {@code RenderType.gui()}, batch ended.</li>
 * <li>1.21.4 - the buffer source went private; {@code drawSpecial} submits vertices and
 * ends the batch itself.</li>
 * <li>1.21.6+ - the extraction-based GUI: the mesh rides an {@link IndicatorGuiElement}
 * into the frame's {@code GuiRenderState}, reached through a small accessor mixin on the
 * graphics class ({@code GuiGraphics} below 26.1, {@code GuiGraphicsExtractor} from
 * 26.1 - spec v1 §6's no-remap era).</li>
 * </ul>
 *
 * <p>All three paths end in the same GUI quad pipelines the era's own {@code fill} uses
 * (position/color only, no textures), so the look is identical across anchors and no raw
 * GL is touched (safe on the 26.x Vulkan backend). The pose at HUD-draw time is the
 * frame's identity; it is still captured and passed along, mirroring what the era's own
 * fills do.
 */
final class GuiCanvas {

    //? if fapi_modern_id {
    private final GuiGraphicsExtractor graphics;
    //?}
    //? if hud_registry && !fapi_modern_id {
    /*private final GuiGraphics graphics;*/
    //?}

    //? if fapi_modern_id {
    GuiCanvas(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }
    //?}
    //? if hud_registry && !fapi_modern_id {
    /*GuiCanvas(GuiGraphics graphics) {
        this.graphics = graphics;
    }*/
    //?}

    /** GUI-scaled viewport width in pixels (spec v1 §5.1). */
    int guiWidth() {
        return graphics.guiWidth();
    }

    /** GUI-scaled viewport height in pixels (spec v1 §5.1). */
    int guiHeight() {
        return graphics.guiHeight();
    }

    /**
     * Submits one quad-ordered triangle mesh ({@link IndicatorShapes}, xy pairs in
     * viewport coordinates) in the given ARGB color. Four vertices per quad, matching
     * the GUI pipelines' quad vertex format on every anchor.
     */
    void triangles(float[] mesh, int argb) {
        //? if !hud_registry {
        /*PoseStack.Pose pose = graphics.pose().last();
        VertexConsumer vertices = graphics.bufferSource().getBuffer(RenderType.gui());
        for (int i = 0; i < mesh.length; i += 2) {
            vertices.addVertex(pose, mesh[i], mesh[i + 1], 0.0f).setColor(argb);
        }
        // The buffer source is shared for the whole frame; ending the batch here draws
        // the mesh now, preserving submission order - the same flush point
        // `drawSpecial` uses on 1.21.4.
        graphics.bufferSource().endBatch();*/
        //?}
        //? if fapi_modern_id {
        renderState().addGuiElement(new IndicatorGuiElement(
                new Matrix3x2f(graphics.pose()), mesh, argb));
        //?}
        //? if hud_registry && !fapi_modern_id {
        /*renderState().submitGuiElement(new IndicatorGuiElement(
                new Matrix3x2f(graphics.pose()), mesh, argb));*/
        //?}
    }

    /** The frame's GUI render state, via the accessor mixin (1.21.6+ only). */
    //? if fapi_modern_id {
    private GuiRenderState renderState() {
        return ((GuiGraphicsExtractorAccessor) graphics).whos_after_me$guiRenderState();
    }
    //?}
    //? if hud_registry && !fapi_modern_id {
    /*private GuiRenderState renderState() {
        return ((GuiGraphicsAccessor) graphics).whos_after_me$guiRenderState();
    }*/
    //?}
}
