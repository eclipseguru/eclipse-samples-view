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

import java.io.*;
import java.net.URL;
import java.util.*;
import org.eclipse.compare.*;
import org.eclipse.compare.internal.CompareUIPlugin;
import org.eclipse.compare.structuremergeviewer.*;
import org.eclipse.compare.structuremergeviewer.Differencer;
import org.eclipse.core.runtime.*;
import org.eclipse.swt.graphics.Image;
import org.eclipsercp.book.tools.Sample;
import org.eclipsercp.book.tools.SamplesModel;
import org.eclipsercp.book.tools.Sample.ProjectImport;

/**
 * This implementation of the <code>IStructureCreator</code> interface
 * makes the contents of a Sample  available as a
 * hierarchical structure of <code>IStructureComparator</code>s.
 *
 * @since 2.0
 */
public class SampleStructureCreator implements IStructureCreator {

	/**
	 * Common base class for Sample elements
	 */
	static abstract class SampleResource implements IStructureComparator, ITypedElement {

		protected IPath path;
		protected Sample sample;
		
		SampleResource(Sample sample, IPath path) {
			this.sample = sample;
			this.path= path;
		}

		public String getName() {
			return path.lastSegment();
		}

		public IPath getPath() {
			return path;
		}

		public Image getImage() {
			return CompareUI.getImage(getType());
		}

		/*
		 * Returns true if other is ITypedElement and names are equal.
		 * @see IComparator#equals
		 */
		public boolean equals(Object other) {
			if (other instanceof ITypedElement)
				return getName().equals(((ITypedElement) other).getName());
			return super.equals(other);
		}

		public int hashCode() {
			return getName().hashCode();
		}
	}

	static class SampleFolder extends SampleResource {

		private HashMap children= new HashMap(10);

		SampleFolder(Sample sample, IPath path) {
			super(sample, path);
		}

		public String getType() {
			return ITypedElement.FOLDER_TYPE;
		}

		public Object[] getChildren() {
			Object[] result= new Object[children.size()];
			Iterator iter= children.values().iterator();
			for (int i= 0; iter.hasNext(); i++)
				result[i]= iter.next();
			return result;
		}

		public SampleResource addChild(Sample sample, IPath childPath) {
			SampleFolder parent = md(childPath.removeLastSegments(1).makeRelative());
			if (childPath.hasTrailingSeparator())
				return parent.addFolder(sample, childPath.lastSegment());
			else
				return parent.addFile(sample, childPath.lastSegment());
		}

		private SampleFolder addFolder(Sample sample, String name) {
			SampleFolder result = new SampleFolder(sample, path.append(name));
			children.put(name, result);
			return result;
		}

		private SampleFile addFile(Sample sample, String name) {
			SampleFile result = new SampleFile(sample, path.append(name));
			children.put(name, result);
			return result;
		}

		private SampleFolder md(IPath parentPath) {
			SampleFolder result = this;
			String[] segments = parentPath.segments();
			for (int i = 0; i < segments.length; i++) {
				SampleFolder temp = (SampleFolder) result.children.get(segments[i]);
				if (temp == null)
					temp = result.addFolder(sample, segments[i]);
				result = temp;
			}
			return result;
		}
	}

	static class SampleFile extends SampleResource implements IStreamContentAccessor {		
		SampleFile(Sample sample, IPath path) {
			super(sample, path);
		}

		public String getType() {
			String s= this.getName();
			int pos= s.lastIndexOf('.');
			if (pos >= 0)
				return s.substring(pos + 1);
			return ITypedElement.UNKNOWN_TYPE;
		}

		public Object[] getChildren() {
			return null;
		}
		
		public InputStream getContents() throws CoreException {
			IPath location = sample.getLocation().location;
			URL entry = sample.getLocation().bundle.getEntry(location.append(path).toString());
			if (entry == null)
				new ByteArrayInputStream(new byte[0]);
			try {
				return entry.openStream();
			} catch (IOException e) {
				throw new CoreException(new Status(IStatus.ERROR, "org.eclipsercp.samplings", "Error getting entry", e));
			}
		}

		public byte[] getBytes() {
			byte[] result= new byte[0];		
			InputStream is = null;
			try {
				try {
					is = getContents();
					int n;
					byte[] buffer= new byte[0];		
					do {
						n= is.read(buffer, 0, 4096);
						appendBytes(result, buffer, n);
					} while (n >= 0);
				} finally {
					is.close();
				}
			}  catch (Exception e) {
			}
			return result;
		}
		
		byte[] appendBytes(byte[] result, byte[] buffer, int length) {
			if (length <= 0) 
				return result;
			int oldLen= 0;
			if (result != null)
				oldLen= result.length;
			byte[] newBuf= new byte[oldLen + length];
			if (oldLen > 0)
				System.arraycopy(result, 0, newBuf, 0, oldLen);
    			System.arraycopy(buffer, 0, newBuf, oldLen, length);
    			return newBuf;
		}
	}
	
	private SamplesModel fSamples;

	public SampleStructureCreator(SamplesModel samples) {
		fSamples = samples;
	}
	
	public String getName() {
		return "Sample Comparison";
	}

	public IStructureComparator getStructure(Object input) {
		Sample sample = (Sample) input;
		SampleFolder result = new SampleFolder(sample, new Path("")); //$NON-NLS-1$
		try {
			loadImmediateProjects(sample, result);
			loadImportedProjects(sample, result);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}

	private void loadImportedProjects(Sample sample, SampleFolder result) throws IOException {
		ProjectImport[] imports = sample.getImports();
		for (int i = 0; i < imports.length; i++) {
			Float number = imports[i].getSampleNumber();
			Sample sourceSample = fSamples.findSampleById(number);
			IPath base = sourceSample.getLocation().location;
			String target = base.append(imports[i].getProjectName()).toString();
			loadProjects(sourceSample, base.toString(), target, result);
		}
	}

	public void loadImmediateProjects(Sample sample, SampleFolder result) throws IOException {
		String base = sample.getLocation().location.toString();
		loadProjects(sample, base, base, result);
	}

	private void loadProjects(Sample sample, String base, String parent, SampleFolder result) throws IOException {
		Enumeration paths = sample.getLocation().bundle.getEntryPaths(parent);
		while (paths.hasMoreElements()) {
			String entry = (String) paths.nextElement();
			if (!filter(entry)) {
				result.addChild(sample, new Path(entry.substring(base.length(), entry.length())));
				if (entry.endsWith("/"))
					loadProjects(sample, base, entry, result);
			}
		}
	}

	private boolean filter(String entry) {
		if (entry.indexOf("CVS") >= 0)
			return true;
		if (entry.indexOf("imports.def") >= 0)
			return true;
		return CompareUIPlugin.getDefault().filter(entry, entry.endsWith("/"), false);
	}

	public String getContents(Object o, boolean ignoreWhitespace) {
		if (!(o instanceof SampleFile))
			return null;
		
		byte[] bytes= ((SampleFile)o).getBytes();
		if (bytes != null)
			return new String(bytes);
		return ""; //$NON-NLS-1$
	}

	/**
	 * Returns <code>false</code> since we cannot update a Sample.
	 * @return <code>false</code>
	 */
	public boolean canSave() {
		return false;
	}

	/**
	 * Called whenever a copy operation has been performed on a tree node.
	 * This implementation throws an <code>AssertionFailedException</code>
	 * since we cannot update a Sample.
	 *
	 * @param structure the node for which to save the new content
	 * @param input the object from which the structure tree was created in <code>getStructure</code>
	 */
	public void save(IStructureComparator structure, Object input) {
		Assert.isTrue(false); 
	}
	
	public IStructureComparator locate(Object path, Object source) {
		return null;
	}
		
	/**
	 * Returns <code>false</code> since this <code>IStructureCreator</code>
	 * cannot rewrite the diff tree in order to fold certain combinations of
	 * additions and deletions.
	 * <p>
	 * Note: this method is for internal use only. Clients should not call this method. 
	 * @return <code>false</code>
	 */
	public boolean canRewriteTree() {
		return false;
	}
	
	/**
	 * Empty implementation since this <code>IStructureCreator</code>
	 * cannot rewrite the diff tree in order to fold certain combinations of
	 * additions and deletions.
	 * <p>
	 * Note: this method is for internal use only. Clients should not call this method. 
	 * @param differencer
	 * @param root
	 */
	public void rewriteTree(Differencer differencer, IDiffContainer root) {
		// empty default implementation
	}
}

