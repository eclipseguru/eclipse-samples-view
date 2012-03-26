package org.eclipsercp.book.tools;

import java.util.Enumeration;

import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.QualifiedName;

import org.osgi.framework.Bundle;

public class BundleLocation {
	public IPath location;
	public Bundle bundle;
	private String id;

	public BundleLocation(final Bundle bundle, final IPath location) {
		this.bundle = bundle;
		this.location = location;
	}

	public BundleLocation(final Bundle bundle, final IPath location, final String id) {
		this.bundle = bundle;
		this.location = location;
		this.id = id;
	}

	public Enumeration getEntries() {
		return bundle.getEntryPaths(location.toString());
	}

	public QualifiedName getId() {
		if (id == null) {
			return new QualifiedName("eclipsercp", "id");
		}
		return new QualifiedName("org.eclipsercp.book.tools", id);
	}

	public String toString() {
		return bundle.getSymbolicName() + "/" + location;
	}
}
