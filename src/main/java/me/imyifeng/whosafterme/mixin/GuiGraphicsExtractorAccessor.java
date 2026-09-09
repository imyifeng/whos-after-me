package me.imyifeng.whosafterme.mixin;

//? if fapi_modern_id {
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the frame's {@code GuiRenderState} that 26.1+'s extraction-based
 * {@code GuiGraphicsExtractor} accumulates into (issue #54): the field is private with
 * no getter (verified against the 26.x jars - no member of the extractor's public
 * surface returns the state), and it is the only submission point for custom
 * {@code GuiElementRenderState} geometry - the vector path
 * {@link me.imyifeng.whosafterme.client.hud.IndicatorGuiElement} rides.
 */
@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {

    @Accessor("guiRenderState")
    GuiRenderState whos_after_me$guiRenderState();
}
//?}
