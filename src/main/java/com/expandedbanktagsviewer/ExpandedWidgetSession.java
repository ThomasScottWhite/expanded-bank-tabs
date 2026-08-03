package com.expandedbanktagsviewer;

import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns bank-interface state that must be restored at the end of a session. */
@Singleton
final class ExpandedWidgetSession
{
	private static final Logger log = LoggerFactory.getLogger(ExpandedWidgetSession.class);
	private final Client client;
	private final Map<Widget, Boolean> hiddenBankItems = new IdentityHashMap<>();
	private Widget layer;
	private Widget layerParent;
	private String previousTitle;

	@Inject
	ExpandedWidgetSession(Client client)
	{
		this.client = client;
	}

	boolean ensureLayer(Widget bankContent, Widget requestedParent, boolean visible, boolean enabled,
		Runnable onLayerReplaced)
	{
		if (!visible || !enabled || bankContent == null || requestedParent == null)
		{
			log.debug("Cannot ensure render layer: visible={}, enabled={}, bankContent={}, requestedParent={}",
				visible, enabled, widgetIdentity(bankContent), widgetIdentity(requestedParent));
			return false;
		}

		Widget[] children = layerParent == null ? null : layerParent.getChildren();
		boolean attached = layer != null && layerParent == requestedParent && children != null
			&& Arrays.stream(children).anyMatch(child -> child == layer);
		if (!attached)
		{
			log.debug("Replacing render layer: oldLayer={}, oldParent={}, requestedParent={}",
				widgetIdentity(layer), widgetIdentity(layerParent), widgetIdentity(requestedParent));
			disposeLayer();
			onLayerReplaced.run();
			layer = requestedParent.createChild(-1, WidgetType.LAYER);
			layerParent = requestedParent;
			log.debug("Created render layer {} under parent {}", widgetIdentity(layer),
				widgetIdentity(layerParent));
		}

		int x;
		int y;
		Point relative = WidgetGeometry.relativeLocation(bankContent, requestedParent);
		if (relative != null)
		{
			x = relative.getX();
			y = relative.getY();
		}
		else
		{
			Point contentLocation = bankContent.getCanvasLocation();
			Point parentLocation = requestedParent.getCanvasLocation();
			if (contentLocation == null || parentLocation == null)
			{
				log.debug("Cannot position render layer: contentLocation={}, parentLocation={}",
					contentLocation, parentLocation);
				return false;
			}
			x = contentLocation.getX() - parentLocation.getX();
			y = contentLocation.getY() - parentLocation.getY();
		}

		int width = Math.max(1, bankContent.getWidth());
		int height = Math.max(1, bankContent.getHeight());
		if (layer.getOriginalX() != x || layer.getOriginalY() != y
			|| layer.getOriginalWidth() != width || layer.getOriginalHeight() != height
			|| layer.getNoClickThrough() || layer.isHidden())
		{
			layer.setOriginalX(x);
			layer.setOriginalY(y);
			layer.setOriginalWidth(width);
			layer.setOriginalHeight(height);
			layer.setNoClickThrough(false);
			layer.setHidden(false);
			layer.revalidate();
		}
		return true;
	}

	Widget getLayer()
	{
		return layer;
	}

	void restoreLayerAfterBankTagsLayout(Widget panel, Runnable applyScroll)
	{
		if (layer == null)
		{
			return;
		}
		layer.setHidden(false);
		layer.revalidate();
		if (panel != null)
		{
			panel.setHidden(false);
			panel.revalidate();
			applyScroll.run();
		}
	}

	void hideLayer()
	{
		if (layer != null)
		{
			layer.setNoClickThrough(true);
			layer.setHidden(true);
			layer.revalidate();
		}
	}

	void clearLayerChildren()
	{
		if (layer != null)
		{
			Widget[] children = layer.getChildren();
			log.debug("Deleting {} children from render layer {}",
				children == null ? 0 : children.length, widgetIdentity(layer));
			layer.deleteAllChildren();
		}
	}

	void disposeLayer()
	{
		if (layer != null)
		{
			log.debug("Disposing render layer {} from parent {}", widgetIdentity(layer),
				widgetIdentity(layerParent));
			layer.deleteAllChildren();
			layer.setOriginalWidth(0);
			layer.setOriginalHeight(0);
			layer.setNoClickThrough(true);
			layer.setHidden(true);
			layer.revalidate();
		}
		layer = null;
		layerParent = null;
	}

	void hideBankItems()
	{
		Widget items = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (items == null || items.getChildren() == null)
		{
			return;
		}
		for (Widget child : items.getChildren())
		{
			if (child != null)
			{
				hiddenBankItems.putIfAbsent(child, child.isHidden());
				child.setHidden(true);
			}
		}
		log.trace("Tracking {} hidden bank item widgets", hiddenBankItems.size());
	}

	void restoreBankItems()
	{
		int restored = hiddenBankItems.size();
		for (Map.Entry<Widget, Boolean> entry : hiddenBankItems.entrySet())
		{
			if (entry.getKey() != null)
			{
				entry.getKey().setHidden(entry.getValue());
			}
		}
		hiddenBankItems.clear();
		if (restored > 0)
		{
			log.debug("Restored {} bank item widget visibility states", restored);
		}
	}

	void showExpandedTitle(String title)
	{
		Widget bankTitle = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (bankTitle != null)
		{
			if (previousTitle == null)
			{
				previousTitle = bankTitle.getText();
			}
			bankTitle.setText(title);
			bankTitle.revalidate();
		}
	}

	void restoreTitle()
	{
		if (previousTitle == null)
		{
			return;
		}
		Widget bankTitle = client.getWidget(InterfaceID.Bankmain.TITLE);
		if (bankTitle != null)
		{
			bankTitle.setText(previousTitle);
			bankTitle.revalidate();
		}
		previousTitle = null;
	}

	void forgetTitle()
	{
		previousTitle = null;
	}

	void dispose()
	{
		restoreBankItems();
		restoreTitle();
		disposeLayer();
	}

	private static String widgetIdentity(Widget widget)
	{
		return widget == null ? "null"
			: widget.getId() + "@" + Integer.toHexString(System.identityHashCode(widget));
	}
}
