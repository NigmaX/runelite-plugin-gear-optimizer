package com.gearoptimizer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

/**
 * Images drawn in the game's own style: the stone buttons this plugin adds to game interfaces, and its world map
 * pins.
 */
final class GameButtons
{
	private GameButtons()
	{
	}

	/**
	 * A stone button like the game's own, with an icon in the middle.
	 */
	static BufferedImage stone(Dimension size, boolean hover, BufferedImage icon)
	{
		int w = size.width;
		int h = size.height;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setColor(hover ? new Color(88, 76, 60) : new Color(66, 57, 45));
		g.fillRect(0, 0, w, h);
		g.setColor(new Color(102, 89, 70));
		g.drawLine(1, 1, w - 2, 1);
		g.drawLine(1, 1, 1, h - 2);
		g.setColor(new Color(38, 33, 26));
		g.drawLine(1, h - 2, w - 2, h - 2);
		g.drawLine(w - 2, 1, w - 2, h - 2);
		g.setColor(new Color(16, 14, 11));
		g.drawRect(0, 0, w - 1, h - 1);
		if (icon != null)
		{
			int side = Math.max(8, Math.min(w, h) - 8);
			double scale = Math.min((double) side / icon.getWidth(), (double) side / icon.getHeight());
			int iw = (int) Math.round(icon.getWidth() * scale);
			int ih = (int) Math.round(icon.getHeight() * scale);
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(icon, (w - iw) / 2, (h - ih) / 2, iw, ih, null);
		}
		g.dispose();
		return image;
	}

	/**
	 * A map pin: a coloured teardrop with a dark rim, its tip at the bottom middle, holding an icon on a pale disc.
	 *
	 * @param icon drawn inside the pin, or null for a plain one
	 */
	static BufferedImage pin(Color color, BufferedImage icon)
	{
		int w = 22;
		int h = 30;
		BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = image.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

		Path2D drop = new Path2D.Double();
		drop.moveTo(w / 2.0, h - 1);
		drop.curveTo(w / 2.0 - 3, h - 9, 1, h - 15, 1, 11);
		drop.curveTo(1, 5, 5, 1, w / 2.0, 1);
		drop.curveTo(w - 5, 1, w - 1, 5, w - 1, 11);
		drop.curveTo(w - 1, h - 15, w / 2.0 + 3, h - 9, w / 2.0, h - 1);
		drop.closePath();
		g.setColor(color);
		g.fill(drop);
		g.setColor(new Color(20, 16, 12));
		g.setStroke(new BasicStroke(1.5f));
		g.draw(drop);

		double disc = 15;
		Ellipse2D face = new Ellipse2D.Double((w - disc) / 2, 11 - disc / 2, disc, disc);
		g.setColor(new Color(245, 238, 220));
		g.fill(face);
		if (icon != null)
		{
			double scale = Math.min(12.0 / icon.getWidth(), 12.0 / icon.getHeight());
			int iw = (int) Math.round(icon.getWidth() * scale);
			int ih = (int) Math.round(icon.getHeight() * scale);
			g.drawImage(icon, (w - iw) / 2, 11 - ih / 2, iw, ih, null);
		}
		g.dispose();
		return image;
	}
}
