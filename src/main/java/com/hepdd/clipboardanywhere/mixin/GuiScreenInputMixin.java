package com.hepdd.clipboardanywhere.mixin;

import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hepdd.clipboardanywhere.client.ClientEventHandler;

/**
 * Intercepts the primary GUI input path used while a screen is open.
 *
 * <p>
 * The lower priority lets GUI libraries such as ModularUI2 keep their own redirect. When that happens,
 * {@link ClientEventHandler} consumes the library's cancellable pre-input event instead.
 * </p>
 */
@Mixin(value = GuiScreen.class, priority = 900)
public abstract class GuiScreenInputMixin {

    @Redirect(
        method = "handleInput",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleMouseInput()V"),
        require = 0)
    private void clipboardanywhere$handleMouseInput(GuiScreen screen) {
        if (!ClientEventHandler.handleGuiMouseInput(screen)) screen.handleMouseInput();
    }

    @Redirect(
        method = "handleInput",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleKeyboardInput()V"),
        require = 0)
    private void clipboardanywhere$handleKeyboardInput(GuiScreen screen) {
        if (!ClientEventHandler.handleGuiKeyboardInput(screen)) screen.handleKeyboardInput();
    }
}
