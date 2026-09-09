package me.imyifeng.whosafterme.client.hud;

import java.util.List;

import org.joml.Matrix3x2fStack;
//? if hud_registry {
//?} else {
/*import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;*/
//?}
//? if fapi_modern_id {
import net.minecraft.client.gui.GuiGraphicsExtractor;
//?} else {
/*import net.minecraft.client.gui.GuiGraphics;*/
//?}

/**
 * The era-neutral drawing surface of the HUD renderer (spec v1 §6): paint axis-aligned
 * bars inside a translated and rotated local frame. Every Anchor can express
 * {@link IndicatorShapes}' placed shapes this way through {@code fill} and the GUI pose
 * stack only - no raw GL (safe on the 26.x Vulkan backend) and no buffer-source access
 * (which the 26.x extraction-based GUI does not expose). The renderer stays identical
 * across both HUD API lines; the only era differences absorbed here are the GUI graphics
 * type ({@code GuiGraphicsExtractor} on 26.1+, {@code GuiGraphics} below) and the pose
 * stack type (flat {@code Matrix3x2fStack} from 1.21.6, {@code PoseStack} before), both
 * forked through the workspace's version constants.
 */
final class GuiCanvas {

    //? if fapi_modern_id {
    private final GuiGraphicsExtractor graphics;
    //?} else {
    /*private final GuiGraphics graphics;*/
    //?}

    //? if fapi_modern_id {
    GuiCanvas(GuiGraphicsExtractor graphics) {
    //?} else {
    /*GuiCanvas(GuiGraphics graphics) {*/
    //?}
        this.graphics = graphics;
    }

    /** GUI-scaled viewport width in pixels (spec v1 §5.1). */
    int guiWidth() {
        return graphics.guiWidth();
    }

    /** GUI-scaled viewport height in pixels (spec v1 §5.1). */
    int guiHeight() {
        return graphics.guiHeight();
    }

    /**
     * Paints {@code ops} in a local frame translated to ({@code x}, {@code y}) and
     * rotated by {@code rotationRad}, in the given ARGB color. {@code fill} captures the
     * pose stack's current matrix, so the bars land as the rotated shape.
     */
    void fillRotated(double x, double y, double rotationRad, List<IndicatorShapes.ShapeOp> ops, int argb) {
        // The GUI pose stack changed type at 1.21.6 (spec v1 §6): a flat 2x3 matrix
        // stack from there on, the 3D PoseStack before.
        //? if hud_registry {
        Matrix3x2fStack pose = graphics.pose();
        pose.pushMatrix();
        pose.translate((float) x, (float) y);
        pose.rotate((float) rotationRad);
        //?} else {
        /*PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0.0);
        pose.mulPose(new Quaternionf().rotationZ((float) rotationRad));*/
        //?}
        for (IndicatorShapes.ShapeOp op : ops) {
            graphics.fill(
                    (int) Math.round(op.x() - op.halfWidth()),
                    (int) Math.round(op.y() - op.halfHeight()),
                    (int) Math.round(op.x() + op.halfWidth()),
                    (int) Math.round(op.y() + op.halfHeight()),
                    argb);
        }
        //? if hud_registry {
        pose.popMatrix();
        //?} else {
        /*pose.popPose();*/
        //?}
    }
}
