package me.imyifeng.whosafterme.mixin;

/**
 * Exposes the frame's {@code GuiRenderState} that 1.21.6+'s extraction-based
 * {@code GuiGraphics} accumulates into (issue #54): the field is private with no getter
 * on every anchor of that era (verified against the mapped jars), and it is the only
 * submission point for custom {@code GuiElementRenderState} geometry - the vector path
 * {@link me.imyifeng.whosafterme.client.hud.IndicatorGuiElement} rides. Absent on the
 * anchors where this class does not exist (below 1.21.6, and on 26.1+ where the
 * extractor's own accessor takes over).
 */
//? if hud_registry && !fapi_modern_id {
/*import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphics.class)
public interface GuiGraphicsAccessor {

    @Accessor("guiRenderState")
    GuiRenderState whos_after_me$guiRenderState();
}*/
//?}
