package com.spearsradio;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class RadioClient implements ClientModInitializer {
    private static final KeyBinding.Category CAT = KeyBinding.Category.create(Identifier.of("spearsradio", "main"));
    private KeyBinding open, toggle, next, prev, up, down, repeat;

    private KeyBinding key(String n, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding("key.spearsradio." + n, InputUtil.Type.KEYSYM, code, CAT));
    }

    @Override
    public void onInitializeClient() {
        RadioPlayer.load();
        open   = key("open",   GLFW.GLFW_KEY_I);
        toggle = key("toggle", GLFW.GLFW_KEY_BACKSLASH);
        next   = key("next",   GLFW.GLFW_KEY_RIGHT_BRACKET);
        prev   = key("prev",   GLFW.GLFW_KEY_LEFT_BRACKET);
        up     = key("vol_up", GLFW.GLFW_KEY_EQUAL);
        down   = key("vol_down", GLFW.GLFW_KEY_MINUS);
        repeat = key("repeat", GLFW.GLFW_KEY_APOSTROPHE);
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient mc) {
        if (mc.player == null) return;
        while (open.wasPressed()) if (mc.currentScreen == null) mc.setScreen(new RadioScreen());
        while (toggle.wasPressed()) { RadioPlayer.toggle(); msg(mc); }
        while (next.wasPressed())   { RadioPlayer.next();   msg(mc); }
        while (prev.wasPressed())   { RadioPlayer.prev();   msg(mc); }
        while (repeat.wasPressed()) { RadioPlayer.cycleRepeat(); mc.player.sendMessage(Text.literal("Radio repeat: " + RadioPlayer.repeatName()), true); }
        while (up.wasPressed())   { RadioPlayer.setVolume(RadioPlayer.volume + 0.1f); RadioPlayer.save(); vol(mc); }
        while (down.wasPressed()) { RadioPlayer.setVolume(RadioPlayer.volume - 0.1f); RadioPlayer.save(); vol(mc); }
    }

    private void msg(MinecraftClient mc) { mc.player.sendMessage(Text.literal("Radio: " + RadioPlayer.nowPlaying()), true); }
    private void vol(MinecraftClient mc) { mc.player.sendMessage(Text.literal("Radio volume: " + Math.round(RadioPlayer.volume * 100) + "%"), true); }
}
