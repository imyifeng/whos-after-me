//? if hud_registry {
package me.imyifeng.whosafterme.client.hud;

//? if fapi_modern_id {
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
//?} else {
/*import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.render.state.GuiElementRenderState;*/
//?}
import org.joml.Matrix3x2f;

/**
 * One indicator mesh as a GUI render-state element (issue #54): the 1.21.6+
 * extraction-based GUI does not draw on submission - it accumulates element states that
 * the GUI renderer later turns into vertices - so the {@link IndicatorShapes} meshes
 * ride this state into the frame's {@code GuiRenderState} (submitted by
 * {@link GuiCanvas} through the accessor mixin). The shape follows
 * {@code BlitRenderState}'s pattern: a captured 2D pose, the viewport-space quad
 * vertices, and one ARGB color, built into the era's plain GUI pipeline (position/color,
 * no texture - exactly what the era's own {@code fill} uses).
 *
 * <p>The element interfaces fork inside this file (spec v1 §6): below 26.1 the state
 * lives at {@code net.minecraft.client.gui.render.state} and its
 * {@code buildVertices} receives the layer's z; from 26.1 Mojang moved it to
 * {@code net.minecraft.client.renderer.state.gui} and dropped the z (verified against
 * the mapped jars).
 *
 * @param pose the graphics' pose at submission time (the frame's identity at HUD draw)
 * @param mesh quad-ordered xy pairs in viewport coordinates (IndicatorShapes contract)
 * @param argb the fill color including the computed indicator opacity
 */
record IndicatorGuiElement(Matrix3x2f pose, float[] mesh, int argb)
        implements GuiElementRenderState {

    /**
     * Builds the quads into the GUI buffer: every four vertices form one quad, each
     * vertex transformed by the captured pose and colored flat - the same vertices the
     * era's {@code ColoredRectangleRenderState} would build for a fill, minus the
     * rectangle constraint. 1.21.8's interface hands in the stratum z; 1.21.11 dropped
     * it again (see the {@code gui_element_layer_z} constant).
     */
    //? if gui_element_layer_z {
    /*@Override
    public void buildVertices(VertexConsumer vertices, float z) {
        for (int i = 0; i < mesh.length; i += 2) {
            vertices.addVertexWith2DPose(pose, mesh[i], mesh[i + 1], z).setColor(argb);
        }
    }*/
    //?}
    //? if !gui_element_layer_z {
    @Override
    public void buildVertices(VertexConsumer vertices) {
        for (int i = 0; i < mesh.length; i += 2) {
            vertices.addVertexWith2DPose(pose, mesh[i], mesh[i + 1]).setColor(argb);
        }
    }
    //?}

    /** The plain position/color GUI pipeline - the {@code fill} pipeline of the era. */
    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    /** No texture: the mesh is flat color (the era's fills pass the same). */
    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    /**
     * No scissor: the HUD renderer never enables one, matching the {@code null} the
     * era's fills pass outside a scissor block.
     */
    @Override
    public ScreenRectangle scissorArea() {
        return null;
    }

    /**
     * The element's screen bounds: the axis-aligned box of the mesh vertices grown by
     * the pose - the envelope the GUI renderer uses to order elements into strata (the
     * render state drops elements with empty bounds, so this must stay a real box).
     */
    @Override
    public ScreenRectangle bounds() {
        float minX = mesh[0];
        float minY = mesh[1];
        float maxX = mesh[0];
        float maxY = mesh[1];
        for (int i = 2; i < mesh.length; i += 2) {
            minX = Math.min(minX, mesh[i]);
            maxX = Math.max(maxX, mesh[i]);
            minY = Math.min(minY, mesh[i + 1]);
            maxY = Math.max(maxY, mesh[i + 1]);
        }
        int left = (int) Math.floor(minX);
        int top = (int) Math.floor(minY);
        return new ScreenRectangle(
                left, top, (int) Math.ceil(maxX) - left, (int) Math.ceil(maxY) - top)
                .transformMaxBounds(pose);
    }
}
//?}
