package com.playertracer;

import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

public class TracerMod implements ClientModInitializer {
	public static final String MOD_ID = "playertracer";

	/** UUID of the player currently being tracked (null = nobody). */
	public static volatile UUID targetUuid = null;
	public static volatile String targetName = "";

	/** true = tracer starts at the crosshair, false = starts at bottom-center of the screen. */
	public static boolean originCrosshair = true;

	private static KeyMapping openMenuKey;
	private static KeyMapping clearKey;

	public static void setTarget(UUID id, String name) {
		targetUuid = id;
		targetName = name;
	}

	public static void clearTarget() {
		targetUuid = null;
		targetName = "";
	}

	@Override
	public void onInitializeClient() {
		KeyMapping.Category category = KeyMapping.Category.register(
				Identifier.fromNamespaceAndPath(MOD_ID, "main"));

		// Both keys are rebindable in Options > Controls > Key Binds > "Player Tracer"
		openMenuKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.playertracer.open_menu",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_RSHIFT,
				category));

		clearKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
				"key.playertracer.clear",
				InputConstants.Type.KEYSYM,
				InputConstants.KEY_K,
				category));

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			while (openMenuKey.consumeClick()) {
				if (client.player != null && client.screen == null) {
					client.setScreen(new PlayerMenuScreen());
				}
			}
			while (clearKey.consumeClick()) {
				clearTarget();
			}
		});

		// Forget the target when joining a new world/server.
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> clearTarget());

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "tracer"),
				TracerRenderer::render);
	}
}
