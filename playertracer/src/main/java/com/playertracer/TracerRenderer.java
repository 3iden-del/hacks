package com.playertracer;

import java.util.UUID;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Draws the red tracer line + red 3D hitbox wireframe for the selected player.
 * Everything is projected from world space onto the HUD, so it needs no custom
 * render pipeline (and is drawn on top of walls).
 */
public final class TracerRenderer {
	private static final int RED = 0xFFFF1A1A;
	private static final double NEAR = 0.05;
	private static final int TRACER_THICKNESS = 2;
	private static final int BOX_THICKNESS = 1;

	private TracerRenderer() {}

	public static void render(GuiGraphics g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		UUID id = TracerMod.targetUuid;
		if (id == null || mc.player == null || mc.level == null || mc.options.hideGui) {
			return;
		}

		int sw = mc.getWindow().getGuiScaledWidth();
		int sh = mc.getWindow().getGuiScaledHeight();

		Player target = null;
		for (Player p : mc.level.players()) {
			if (p.getUUID().equals(id)) {
				target = p;
				break;
			}
		}
		if (target == null || target.isRemoved()) {
			String msg = "Tracking " + TracerMod.targetName + " (out of range)";
			g.drawString(mc.font, msg, 6, 6, RED, true);
			return;
		}

		float pt = dt.getGameTimeDeltaPartialTick(false);

		// --- camera basis (first-person view of the local player)
		Vec3 cam = mc.player.getEyePosition(pt);
		double yaw = Math.toRadians(mc.player.getViewYRot(pt));
		double pitch = Math.toRadians(mc.player.getViewXRot(pt));
		double cp = Math.cos(pitch), sp = Math.sin(pitch);
		double cy = Math.cos(yaw), sy = Math.sin(yaw);

		double fx = -sy * cp, fy = -sp, fz = cy * cp;        // forward
		double rx = -cy, rz = -sy;                            // right (y = 0)
		double ux = -sy * sp, uy = cp, uz = cy * sp;          // up

		double fov = mc.options.fov().get();
		double focal = (sh / 2.0) / Math.tan(Math.toRadians(fov) / 2.0);

		// --- smooth target box
		double lx = Mth.lerp((double) pt, target.xo, target.getX());
		double ly = Mth.lerp((double) pt, target.yo, target.getY());
		double lz = Mth.lerp((double) pt, target.zo, target.getZ());
		AABB box = target.getBoundingBox().move(lx - target.getX(), ly - target.getY(), lz - target.getZ());

		// 8 corners in camera space {right, up, forward}
		double[][] c = new double[8][];
		for (int i = 0; i < 8; i++) {
			double x = (i & 1) == 0 ? box.minX : box.maxX;
			double y = (i & 2) == 0 ? box.minY : box.maxY;
			double z = (i & 4) == 0 ? box.minZ : box.maxZ;
			double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
			c[i] = new double[] {
					dx * rx + dz * rz,
					dx * ux + dy * uy + dz * uz,
					dx * fx + dy * fy + dz * fz
			};
		}

		// --- hitbox wireframe (12 edges, clipped to the near plane)
		for (int i = 0; i < 8; i++) {
			for (int bit : new int[] {1, 2, 4}) {
				if ((i & bit) != 0) continue;
				double[] a = c[i];
				double[] b = c[i | bit];
				if (a[2] < NEAR && b[2] < NEAR) continue;
				double[] a2 = a, b2 = b;
				if (a[2] < NEAR) a2 = nearClip(a, b);
				else if (b[2] < NEAR) b2 = nearClip(b, a);
				double[] line = {
						sw / 2.0 + focal * a2[0] / a2[2], sh / 2.0 - focal * a2[1] / a2[2],
						sw / 2.0 + focal * b2[0] / b2[2], sh / 2.0 - focal * b2[1] / b2[2]
				};
				drawLine(g, line, sw, sh, BOX_THICKNESS);
			}
		}

		// --- tracer to the center of the hitbox
		double tx = (box.minX + box.maxX) / 2 - cam.x;
		double ty = (box.minY + box.maxY) / 2 - cam.y;
		double tz = (box.minZ + box.maxZ) / 2 - cam.z;
		double tcx = tx * rx + tz * rz;
		double tcy = tx * ux + ty * uy + tz * uz;
		double tcz = tx * fx + ty * fy + tz * fz;

		double ox = sw / 2.0;
		double oy = TracerMod.originCrosshair ? sh / 2.0 : sh;
		double ex, ey;
		if (tcz > NEAR) {
			ex = sw / 2.0 + focal * tcx / tcz;
			ey = sh / 2.0 - focal * tcy / tcz;
		} else {
			// Target is behind you: point the line toward the screen edge on that side.
			double dx = tcx, dy = -tcy;
			double len = Math.hypot(dx, dy);
			if (len < 1e-6) { dx = 0; dy = 1; len = 1; }
			dx /= len; dy /= len;
			double t = 1e9;
			if (dx > 1e-6) t = Math.min(t, (sw - ox) / dx);
			else if (dx < -1e-6) t = Math.min(t, (0 - ox) / dx);
			if (dy > 1e-6) t = Math.min(t, (sh - oy) / dy);
			else if (dy < -1e-6) t = Math.min(t, (0 - oy) / dy);
			ex = ox + dx * t;
			ey = oy + dy * t;
		}
		drawLine(g, new double[] {ox, oy, ex, ey}, sw, sh, TRACER_THICKNESS);

		// --- name + distance above the target's head
		double hx = (box.minX + box.maxX) / 2 - cam.x;
		double hy = box.maxY + 0.3 - cam.y;
		double hz = (box.minZ + box.maxZ) / 2 - cam.z;
		double hcz = hx * fx + hy * fy + hz * fz;
		if (hcz > NEAR) {
			double hcx = hx * rx + hz * rz;
			double hcy = hx * ux + hy * uy + hz * uz;
			int px = (int) (sw / 2.0 + focal * hcx / hcz);
			int py = (int) (sh / 2.0 - focal * hcy / hcz);
			String label = TracerMod.targetName + "  " + String.format("%.1fm", mc.player.distanceTo(target));
			g.drawString(mc.font, label, px - mc.font.width(label) / 2, py - 10, RED, true);
		}
	}

	/** Returns the point where segment in->out crosses the near plane (z = NEAR); 'in' is the point behind it. */
	private static double[] nearClip(double[] behind, double[] front) {
		double t = (NEAR - behind[2]) / (front[2] - behind[2]);
		return new double[] {
				behind[0] + t * (front[0] - behind[0]),
				behind[1] + t * (front[1] - behind[1]),
				NEAR
		};
	}

	/** Clip to the screen (Liang-Barsky) and draw as a rotated thin rectangle. */
	private static void drawLine(GuiGraphics g, double[] l, int sw, int sh, int thickness) {
		double minX = -4, minY = -4, maxX = sw + 4, maxY = sh + 4;
		double dx = l[2] - l[0], dy = l[3] - l[1];
		double[] p = {-dx, dx, -dy, dy};
		double[] q = {l[0] - minX, maxX - l[0], l[1] - minY, maxY - l[1]};
		double t0 = 0, t1 = 1;
		for (int i = 0; i < 4; i++) {
			if (p[i] == 0) {
				if (q[i] < 0) return;
			} else {
				double r = q[i] / p[i];
				if (p[i] < 0) {
					if (r > t1) return;
					if (r > t0) t0 = r;
				} else {
					if (r < t0) return;
					if (r < t1) t1 = r;
				}
			}
		}
		float x1 = (float) (l[0] + t0 * dx), y1 = (float) (l[1] + t0 * dy);
		float x2 = (float) (l[0] + t1 * dx), y2 = (float) (l[1] + t1 * dy);
		float ddx = x2 - x1, ddy = y2 - y1;
		float len = (float) Math.hypot(ddx, ddy);
		if (len < 0.5f) return;

		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(x1, y1);
		pose.rotate((float) Math.atan2(ddy, ddx));
		int half = thickness / 2;
		g.fill(0, -half, (int) Math.ceil(len), thickness - half, RED);
		pose.popMatrix();
	}
}
