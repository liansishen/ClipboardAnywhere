package com.hepdd.clipboardanywhere.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.hepdd.clipboardanywhere.client.ClientEventHandler;

@Mixin(Minecraft.class)
public abstract class MinecraftGuiInputMixin {

    @Redirect(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleMouseInput()V"))
    private void clipboardanywhere$handleMouseInput(GuiScreen screen) {
        if (!ClientEventHandler.handleGuiMouseInput(screen)) screen.handleMouseInput();
    }

    @Redirect(
        method = "runTick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiScreen;handleKeyboardInput()V"))
    private void clipboardanywhere$handleKeyboardInput(GuiScreen screen) {
        if (!ClientEventHandler.handleGuiKeyboardInput(screen)) screen.handleKeyboardInput();
    }
}
