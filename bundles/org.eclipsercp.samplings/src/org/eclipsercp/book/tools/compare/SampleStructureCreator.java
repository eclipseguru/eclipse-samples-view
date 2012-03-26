/*******************************************************************************
 * Copyright (c) 2000, 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipsercp.book.tools.compare;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;

import org.eclipse.compare.CompareUI;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.internal.CompareUIPlugin;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IStructureComparator;
import org.eclipse.compare.structuremergeviewer.IStructureCreator;
import org.eclipse.core.runtime.Assert;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.swt.graphics.Image;

import org.eclipsercp.book.tools.Sample;
import org.eclipsercp.book.tools.Sample.ProjectImport;
import org.eclipsercp.book.tools.SamplesModel;

/**
 * This implementation of the <code>IStructureCreator</code> interface makes the
 * contents of a Sample available as a hierarchical structure of
 * <code>IStructureComparator</code>s.
 * 
 * @since 2.0
 */
public class SampleStructureCreator implements IStructureCreator {

	static class SampleFile extends SampleResource implements IStreamContentAccessor {
		SampleFile(final Sample sample, final IPath path) {
			super(sample, path);
		}

		byte[] appendBytes(final byte[] result, final byte[] buffer, final int length) {
			if (length <= 0) {
				return result;
			}
			int oldLen = 0;
			if (result != null) {
				oldLen = result.length;
			}
			final byte[] newBuf = new byte[oldLen + length];
			if (oldLen > 0) {
				System.arraycopy(result, 0, newBuf, 0, oldLen);
			}
			System.arraycopy(buffer, 0, newBuf, oldLen, length);
			return newBuf;
		}

		public byte[] getBytes() {
			final byte[] result = new byte[0];
			InputStream is = null;
			try {
				try {
					is = getContents();
					int n;
					final byte[] buffer = new byte[0];
					do {
						n = is.read(buffer, 0, 4096);
						appendBytes(result, buffer, n);
					} while (n >= 0);
				} finally {
					is.close();
				}
			} catch (final Exception e) {
			}
			return result;
		}

		public Object[] getChildren() {
			return null;
		}

		public InputStream getContents() throws CoreException {
			final IPath location = sample.getLocation().location;
			final URL entry = sample.getLocation().bundle.getEntry(location.append(path).toString());
			if (entry == null) {
				new ByteArrayInputStream(new byte[0]);
			}
			try {
				return entry.openStream();
			} catch (final IOException e) {
				throw new CoreException(new Status(IStatus.ERROR, "org.eclipsercp.samplings", "Error getting entry", e));
			}
		}

		public String getType() {
			final String s = getName();
			final int pos = s.lastIndexOf('.');
			if (pos >= 0) {
				return s.substring(pos + 1);
			}
			return ITypedElement.UNKNOWN_TYPE;
		}
	}

	static class SampleFolder extends SampleResource {

		private final HashMap children = new HashMap(10);

		SampleFolder(final Sample sample, final IPath path) {
			super(sample, path);
		}

		public SampleResource addChild(final Sample sample, final IPath childPath) {
			final SampleFolder parent = md(childPath.removeLastSegments(1).makeRelative());
			if (childPath.hasTrailingSeparator()) {
				return parent.addFolder(sample, childPath.lastSegment());
			} else {
				return parent.addFile(sample, childPath.lastSegment());
			}
		}

		private SampleFile addFile(final Sample sample, final String name) {
			final SampleFile result = new SampleFile(sample, path.append(name));
			children.put(name, result);
			return result;
		}

		private SampleFolder addFolder(final Sample sample, final String name) {
			final SampleFolder result = new SampleFolder(sample, path.append(name));
			children.put(name, result);
			return result;
		}

		public Object[] getChildren() {
			final Object[] result = new Object[children.size()];
			final Iterator iter = children.values().iterator();
			for (int i = 0; iter.hasNext(); i++) {
				result[i] = iter.next();
			}
			return result;
		}

		public String getType() {
			return ITypedElement.FOLDER_TYPE;
		}

		private SampleFolder md(final IPath parentPath) {
			SampleFolder result = this;
			final String[] segments = parentPath.segments();
			for (int i = 0; i < segments.length; i++) {
				SampleFolder temp = (SampleFolder) result.children.get(segments[i]);
				if (temp == null) {
					temp = result.addFolder(sample, segments[i]);
				}
				result = temp;
			}
			return result;
		}
	}

	/**
	 * Common base class for Sample elements
	 */
	static abstract class SampleResource implements IStructureComparator, ITypedElement {

		protected IPath path;
		protected Sample sample;

		SampleResource(final Sample sample, final IPath path) {
			this.sample = sample;
			this.path = path;
		}

		/*
		 * Returns true if other is ITypedElement and names are equal.
		 * @see IComparator#equals
		 */
		public boolean equals(final Object other) {
			if (other instanceof ITypedElement) {
				return getName().equals(((ITypedElement) other).getName());
			}
			return super.equals(other);
		}

		public Image getImage() {
			return CompareUI.getImage(getType());
		}

		public String getName() {
			return path.lastSegment();
		}

		public IPath getPath() {
			return path;
		}

		public int hashCode() {
			return getName().hashCode();
		}
	}

	private final SamplesModel fSamples;

	public SampleStructureCreator(final SamplesModel samples) {
		fSamples = samples;
	}

	/**
	 * Returns <code>false</code> since this <code>IStructureCreator</code>
	 * cannot rewrite the diff tree in order to fold certain combinations of
	 * additions and deletions.
	 * <p>
	 * Note: this method is for internal use only. Clients should not call this
	 * method.
	 * 
	 * @return <code>false</code>
	 */
	public boolean canRewriteTree() {
		return false;
	}

	/**
	 * Returns <code>false</code> since we cannot update a Sample.
	 * 
	 * @return <code>false</code>
	 */
	public boolean canSave() {
		return false;
	}

	private boolean filter(final String entry) {
		if (entry.indexOf("CVS") >= 0) {
			return true;
		}
		if (entry.indexOf("imports.def") >= 0) {
			return true;
		}
		return CompareUIPlugin.getDefault().filter(entry, entry.endsWith("/"), false);
	}

	public String getContents(final Object o, final boolean ignoreWhitespace) {
		if (!(o instanceof SampleFile)) {
			return null;
		}

		final byte[] bytes = ((SampleFile) o).getBytes();
		if (bytes != null) {
			return new String(bytes);
		}
		return ""; //$NON-NLS-1$
	}

	public String getName() {
		return "Sample Comparison";
	}

	public IStructureComparator getStructure(final Object input) {
		final Sample sample = (Sample) input;
		final SampleFolder result = new SampleFolder(sample, new Path("")); //$NON-NLS-1$
		try {
			loadImmediateProjects(sample, result);
			loadImportedProjects(sample, result);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return result;
	}

	public void loadImmediateProjects(final Sample sample, final SampleFolder result) throws IOException {
		final String base = sample.getLocation().location.toString();
		loadProjects(sample, base, base, result);
	}

	private void loadImportedProjects(final Sample sample, final SampleFolder result) throws IOException {
		final ProjectImport[] imports = sample.getImports();
		for (int i = 0; i < imports.length; i++) {
			final Float number = imports[i].getSampleNumber();
			final Sample sourceSample = fSamples.findSampleById(number);
			final IPath base = sourceSample.getLocation().location;
			final String target = base.append(imports[i].getProjectName()).toString();
			loadProjects(sourceSample, base.toString(), target, result);
		}
	}

	private void loadProjects(final Sample sample, final String base, final String parent, final SampleFolder result) throws IOException {
		final Enumeration paths = sample.getLocation().bundle.getEntryPaths(parent);
		if (null == paths) {
			return;
		}

		while (paths.hasMoreElements()) {
			final String entry = (String) paths.nextElement();
			if (!filter(entry)) {
				result.addChild(sample, new Path(entry.substring(base.length(), entry.length())));
				if (entry.endsWith("/")) {
					loadProjects(sample, base, entry, result);
				}
			}
		}
	}

	public IStructureComparator locate(final Object path, final Object source) {
		return null;
	}

	/**
	 * Empty implementation since this <code>IStructureCreator</code> cannot
	 * rewrite the diff tree in order to fold certain combinations of additions
	 * and deletions.
	 * <p>
	 * Note: this method is for internal use only. Clients should not call this
	 * method.
	 * 
	 * @param differencer
	 * @param root
	 */
	public void rewriteTree(final Differencer differencer, final IDiffContainer root) {
		// empty default implementation
	}

	/**
	 * Called whenever a copy operation has been performed on a tree node. This
	 * implementation throws an <code>AssertionFailedException</code> since we
	 * cannot update a Sample.
	 * 
	 * @param structure
	 *            the node for which to save the new content
	 * @param input
	 *            the object from which the structure tree was created in
	 *            <code>getStructure</code>
	 */
	public void save(final IStructureComparator structure, final Object input) {
		Assert.isTrue(false);
	}
}
