package com.alchhero;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.QuantityFormatter;

/**
 * Sidebar panel showing the live Grand Exchange profit scan: every flagged
 * alch target ranked by profit per cast, with buy price, affordable
 * quantity at the configured budget, and a manual rescan button.
 */
class AlchHeroPanel extends PluginPanel
{
	private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm:ss");
	private static final Color PROFIT_GREEN = new Color(0, 230, 118);

	private final AlchHeroPlugin plugin;
	private final ItemManager itemManager;

	private final JLabel statusLabel = new JLabel();
	private final JPanel listPanel = new JPanel();

	AlchHeroPanel(AlchHeroPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel header = new JPanel(new BorderLayout(0, 4));
		header.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Alch Hero: GE Profit Scanner");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JButton rescan = new JButton("Rescan now");
		rescan.addActionListener(e -> plugin.requestRescan());

		header.add(title, BorderLayout.NORTH);
		header.add(statusLabel, BorderLayout.CENTER);
		header.add(rescan, BorderLayout.SOUTH);

		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setBackground(ColorScheme.DARK_GRAY_COLOR);

		add(header, BorderLayout.NORTH);
		add(listPanel, BorderLayout.CENTER);

		rebuild();
	}

	/**
	 * Repopulates the list from the plugin's latest scan results. Must be
	 * called on the Swing EDT.
	 */
	void rebuild()
	{
		long scanTime = plugin.getLastScanTime();
		statusLabel.setText(scanTime == 0
			? "Waiting for first price scan..."
			: "Scanned " + TIME_FORMAT.format(new Date(scanTime))
				+ " | Nature rune " + plugin.getLastNatureCost() + " gp");

		listPanel.removeAll();

		List<AlchTarget> results = plugin.getScanResults();
		if (results.isEmpty())
		{
			JLabel empty = new JLabel("<html>No targets pass the current filters.<br>"
				+ "Try lowering min profit or volume<br>in the plugin settings.</html>");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			empty.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
			listPanel.add(empty);
		}
		else
		{
			for (AlchTarget target : results)
			{
				listPanel.add(buildRow(target));
				listPanel.add(Box.createVerticalStrut(6));
			}
		}

		listPanel.revalidate();
		listPanel.repaint();
	}

	private JPanel buildRow(AlchTarget target)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
		row.setToolTipText("Buy " + QuantityFormatter.formatNumber(target.getGePrice())
			+ " gp | HA " + QuantityFormatter.formatNumber(target.getHaValue())
			+ " gp | GE limit " + (target.getGeLimit() > 0 ? QuantityFormatter.formatNumber(target.getGeLimit()) : "unknown")
			+ " | 24h volume "
			+ (target.getVolume() >= 0 ? QuantityFormatter.formatNumber(target.getVolume()) : "unknown"));

		JLabel icon = new JLabel();
		AsyncBufferedImage image = itemManager.getImage(target.getId());
		image.addTo(icon);
		row.add(icon, BorderLayout.WEST);

		JPanel text = new JPanel(new BorderLayout());
		text.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		JLabel name = new JLabel(target.getName());
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);

		JLabel detail = new JLabel("Buy " + QuantityFormatter.quantityToStackSize(target.getGePrice())
			+ " | Max " + QuantityFormatter.quantityToStackSize(target.getMaxAffordable()));
		detail.setFont(FontManager.getRunescapeSmallFont());
		detail.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		text.add(name, BorderLayout.NORTH);
		text.add(detail, BorderLayout.SOUTH);
		row.add(text, BorderLayout.CENTER);

		JLabel profit = new JLabel("+" + QuantityFormatter.formatNumber(target.getProfit()));
		profit.setFont(FontManager.getRunescapeBoldFont());
		profit.setForeground(PROFIT_GREEN);
		profit.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(profit, BorderLayout.EAST);

		// Clicking a row types the item into the GE search, if a buy offer
		// search is open. Purely fills the search text; the player buys.
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		Color base = ColorScheme.DARKER_GRAY_COLOR;
		Color hover = ColorScheme.DARK_GRAY_HOVER_COLOR;
		row.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				plugin.searchGeFor(target);
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				row.setBackground(hover);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				row.setBackground(base);
			}
		});

		return row;
	}
}
