package com.comfy.expandedbanktabs;

import java.util.ArrayList;
import java.util.List;

final class BankTabGroup
{
	private String id;
	private String name;
	private List<String> tabs = new ArrayList<>();
	private boolean collapsed;

	BankTabGroup()
	{
	}

	BankTabGroup(String id, String name)
	{
		this.id = id;
		this.name = name;
	}

	String getId()
	{
		return id;
	}

	String getName()
	{
		return name;
	}

	void setName(String name)
	{
		this.name = name;
	}

	List<String> getTabs()
	{
		return tabs;
	}

	void setTabs(List<String> tabs)
	{
		this.tabs = tabs == null ? new ArrayList<>() : tabs;
	}

	boolean isCollapsed()
	{
		return collapsed;
	}

	void setCollapsed(boolean collapsed)
	{
		this.collapsed = collapsed;
	}
}
