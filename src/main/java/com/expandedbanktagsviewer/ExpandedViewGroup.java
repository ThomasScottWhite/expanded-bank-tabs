package com.expandedbanktagsviewer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable group data consumed by the expanded-view renderer. */
final class ExpandedViewGroup
{
	private final String name;
	private final String id;
	private final List<BankTab> tabs;
	private final boolean collapsed;

	ExpandedViewGroup(String name, String id, List<BankTab> tabs, boolean collapsed)
	{
		this.name = name;
		this.id = id;
		this.tabs = Collections.unmodifiableList(new ArrayList<>(tabs));
		this.collapsed = collapsed;
	}

	String getName()
	{
		return name;
	}

	String getId()
	{
		return id;
	}

	List<BankTab> getTabs()
	{
		return tabs;
	}

	boolean isCollapsed()
	{
		return collapsed;
	}
}
