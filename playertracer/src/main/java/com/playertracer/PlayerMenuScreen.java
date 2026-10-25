package com.playertracer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/** Click a player's name to put the tracer + red hitbox on them. */
public class PlayerMenuScreen extends Screen {
	private static final int ROW_H = 24;
	private static final int TOP = 36;

	private int page = 0;
	private int perPage = 8;
	private int navY = 0;
	private int pages = 1;
	private int playerCount = 0;

	public PlayerMenuScreen() {
		super(Component.literal("Player Tracer"));
	}

	@Override
	protected void init() {
		Minecraft mc = Minecraft.getInstance();

		List<Player> players = new ArrayList<>();
		if (mc.level != null && mc.player != null) {
			for (Player p : mc.level.players()) {
				if (p != mc.player) {
					players.add(p);
				}
			}
			players.sort(Comparator.comparingDouble((Player p) -> p.distanceToSqr(mc.player)));
		}
		playerCount = players.size();

		// Fit as many rows as the screen allows (leave room for 3 control rows).
		perPage = Math.max(3, (this.height - TOP - 110) / ROW_H);
		pages = Math.max(1, (players.size() + perPage - 1) / perPage);
		page = Mth.clamp(page, 0, pages - 1);

		int w = 220;
		int x = (this.width - w) / 2;
		int y = TOP;

		int from = page * perPage;
		int to = Math.min(players.size(), from + perPage);
		for (int i = from; i < to; i++) {
			Player p = players.get(i);
			UUID id = p.getUUID();
			String name = p.getName().getString();
			boolean selected = id.equals(TracerMod.targetUuid);
			String dist = String.format("%.0fm", Math.sqrt(p.distanceToSqr(mc.player)));
			Component label = Component.literal((selected ? "> " : "") + name + "   " + dist);

			addRenderableWidget(Button.builder(label, b -> {
				TracerMod.setTarget(id, name);
				this.onClose();
			}).bounds(x, y, w, 20).build());
			y += ROW_H;
		}

		navY = TOP + perPage * ROW_H + 4;

		Button prev = Button.builder(Component.literal("< Prev"), b -> {
			page--;
			this.rebuildWidgets();
		}).bounds(x, navY, 70, 20).build();
		prev.active = page > 0;
		addRenderableWidget(prev);

		Button next = Button.builder(Component.literal("Next >"), b -> {
			page++;
			this.rebuildWidgets();
		}).bounds(x + w - 70, navY, 70, 20).build();
		next.active = page < pages - 1;
		addRenderableWidget(next);

		addRenderableWidget(Button.builder(Component.literal("Clear target"), b -> {
			TracerMod.clearTarget();
			this.onClose();
		}).bounds(x, navY + 24, w / 2 - 2, 20).build());

		addRenderableWidget(Button.builder(originLabel(), b -> {
			TracerMod.originCrosshair = !TracerMod.originCrosshair;
			b.setMessage(originLabel());
		}).bounds(x + w / 2 + 2, navY + 24, w / 2 - 2, 20).build());

		addRenderableWidget(Button.builder(Component.literal("Done"), b -> this.onClose())
				.bounds(x, navY + 48, w, 20).build());
	}

	private static Component originLabel() {
		return Component.literal("Line from: " + (TracerMod.originCrosshair ? "Crosshair" : "Bottom"));
	}

	@Override
	public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
		super.render(g, mouseX, mouseY, delta);

		String title = "Player Tracer - click a name";
		g.drawString(this.font, title, (this.width - this.font.width(title)) / 2, 14, 0xFFFFFFFF, true);

		if (playerCount == 0) {
			String msg = "No other players loaded nearby";
			g.drawString(this.font, msg, (this.width - this.font.width(msg)) / 2, TOP + 6, 0xFFAAAAAA, true);
		}

		String pg = (page + 1) + " / " + pages;
		g.drawString(this.font, pg, (this.width - this.font.width(pg)) / 2, navY + 6, 0xFFFFFFFF, true);

		if (TracerMod.targetUuid != null) {
			String t = "Tracking: " + TracerMod.targetName;
			g.drawString(this.font, t, (this.width - this.font.width(t)) / 2, navY + 74, 0xFFFF5555, true);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
