/*******************************************************************************
 * Copyright (c) 2000, 2004 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipsercp.book.tools.compare;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;

import org.eclipse.compare.BufferedContent;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.IEditableContent;
import org.eclipse.compare.IEncodedStreamContentAccessor;
import org.eclipse.compare.IModificationDate;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.structuremergeviewer.IStructureComparator;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.jface.util.Assert;
import org.eclipse.swt.graphics.Image;

/**
 * A <code>FileNode</code> wrappers an <code>java.io.File</code> so that it can
 * be used as input for the differencing engine (interfaces
 * <code>IStructureComparator</code> and <code>ITypedElement</code>) and the
 * <code>ReplaceWithEditionDialog</code> (interfaces <code>ITypedElement</code>
 * and <code>IModificationDate</code>).
 * <p>
 * Clients may instantiate this class; it is not intended to be subclassed.
 * </p>
 */
public class FileNode extends BufferedContent implements IEncodedStreamContentAccessor, IStructureComparator, ITypedElement, IEditableContent, IModificationDate {

	private final File fFile;
	private ArrayList fChildren;

	/**
	 * Creates a <code>ResourceNode</code> for the given resource.
	 * 
	 * @param resource
	 *            the resource
	 */
	public FileNode(final File file) {
		fFile = file;
		Assert.isNotNull(file);
	}

	/**
	 * This hook method is called from <code>getChildren</code> once for every
	 * member of a container resource. This implementation creates a new
	 * <code>FileNode</code> for the given child resource. Clients may override
	 * this method to create a different type of
	 * <code>IStructureComparator</code> or to filter children by returning
	 * <code>null</code>.
	 * 
	 * @param child
	 *            the child resource for which a
	 *            <code>IStructureComparator</code> must be returned
	 * @return a <code>ResourceNode</code> for the given child or
	 *         <code>null</code>
	 */
	protected IStructureComparator createChild(final File child) {
		if (child.getName().equals("CVS") || child.getName().equals("bin")) {
			return null;
		}
		return new FileNode(child);
	}

	protected InputStream createStream() throws CoreException {
		// TODO Auto-generated method stub
		return null;
	}

	/*
	 * Returns <code>true</code> if the other object is of type <code>ITypedElement</code>
	 * and their names are identical. The content is not considered.
	 */
	public boolean equals(final Object other) {
		if (other instanceof ITypedElement) {
			final String otherName = ((ITypedElement) other).getName();
			return getName().equals(otherName);
		}
		return super.equals(other);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.compare.IEncodedStreamContentAccessor#getCharset()
	 */
	public String getCharset() {
		return null;
	}

	/* (non Javadoc)
	 * see IStructureComparator.getChildren
	 */
	public Object[] getChildren() {
		if (fChildren == null) {
			fChildren = new ArrayList();
			if (fFile.isDirectory()) {
				final File members[] = fFile.listFiles();
				for (int i = 0; i < members.length; i++) {
					final IStructureComparator child = createChild(members[i]);
					if (child != null) {
						fChildren.add(child);
					}
				}
			}
		}
		return fChildren.toArray();
	}

	/* (non Javadoc)
	 * see IStreamContentAccessor.getContents
	 */
	public InputStream getContents() throws CoreException {
		if (fFile.isFile()) {
			try {
				return new FileInputStream(fFile);
			} catch (final FileNotFoundException e) {
				return null;
			}
		}
		return null;
	}

	/**
	 * Returns the corresponding resource for this object.
	 * 
	 * @return the corresponding resource
	 */
	public File getFile() {
		return fFile;
	}

	/*
	 * (non Javadoc) see ITypedElement.getImage
	 */
	public Image getImage() {
		if (fFile.isDirectory()) {
			return CompareUI.getImage(ITypedElement.FOLDER_TYPE);
		}
		final IPath path = new Path(fFile.getName());
		if (path.getFileExtension() == null) {
			return null;
		}
		return CompareUI.getImage(path.getFileExtension());
	}

	/* (non Javadoc)
	 * see IModificationDate.getModificationDate
	 */
	public long getModificationDate() {
		return fFile.lastModified();
	}

	/* (non Javadoc)
	 * see ITypedElement.getName
	 */
	public String getName() {
		if (fFile != null) {
			return fFile.getName();
		}
		return null;
	}

	/* (non Javadoc)
	 * see ITypedElement.getType
	 */
	public String getType() {
		if (fFile.isDirectory()) {
			return ITypedElement.FOLDER_TYPE;
		}
		final String s = new Path(fFile.getName()).getFileExtension();
		if (s != null) {
			return s;
		}

		return ITypedElement.UNKNOWN_TYPE;
	}

	/**
	 * Returns the hash code of the name.
	 * 
	 * @return a hash code value for this object.
	 */
	public int hashCode() {
		return getName().hashCode();
	}

	/* (non Javadoc)
	 * see IEditableContent.isEditable
	 */
	public boolean isEditable() {
		return false;
	}

	/* (non Javadoc)
	 * see IEditableContent.replace
	 */
	public ITypedElement replace(final ITypedElement child, final ITypedElement other) {
		return child;
	}
}
