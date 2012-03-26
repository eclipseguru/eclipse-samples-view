/*******************************************************************************
 * Copyright (c) 2004, 2005 Jean-Michel Lemieux, Jeff McAffer and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Hyperbola is an RCP application developed for the book 
 *     Eclipse Rich Client Platform - 
 *         Designing, Coding, and Packaging Java Applications 
 *
 * Contributors:
 *     Jean-Michel Lemieux and Jeff McAffer - initial implementation
 *******************************************************************************/
package org.eclipsercp.book.tools.compare;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.compare.IEditableContent;
import org.eclipse.compare.IStreamContentAccessor;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.ResourceNode;
import org.eclipse.compare.structuremergeviewer.IStructureComparator;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;

/**
 * A resource node that is not buffered. Changes made to it are applied directly
 * to the underlying resource.
 * 
 * @since 3.0
 */
public class EclipseResourceNode extends ResourceNode {

	public static byte[] readBytes(final InputStream in) {
		final ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			while (true) {
				final int c = in.read();
				if (c == -1) {
					break;
				}
				bos.write(c);
			}

		} catch (final IOException ex) {
			return null;

		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (final IOException x) {
				}
			}
			try {
				bos.close();
			} catch (final IOException x) {
			}
		}
		return bos.toByteArray();
	}

	private boolean fDirty = false;

	private IFile fDeleteFile;

	/**
	 * Creates a <code>ResourceNode</code> for the given resource.
	 * 
	 * @param resource
	 *            the resource
	 */
	public EclipseResourceNode(final IResource resource) {
		super(resource);
	}

	/**
	 * Commits buffered contents to resource.
	 */
	public void commit(final IProgressMonitor pm) throws CoreException {
		if (fDirty) {

			if (fDeleteFile != null) {
				fDeleteFile.delete(true, true, pm);
				return;
			}

			final IResource resource = getResource();
			if (resource instanceof IFile) {
				final ByteArrayInputStream is = new ByteArrayInputStream(getContent());
				try {
					final IFile file = (IFile) resource;
					if (file.exists()) {
						file.setContents(is, false, true, pm);
					} else {
						createParents(file);
						file.create(is, false, pm);
					}
					fDirty = false;
				} finally {
					fireContentChanged();
					if (is != null) {
						try {
							is.close();
						} catch (final IOException ex) {
						}
					}
				}
			}
		}
	}

	// Filter out CVS, bin, and derived resources
	protected IStructureComparator createChild(final IResource child) {
		final String name = child.getName();
		if (child.isDerived() || name.equals("bin") || name.equals("CVS")) {
			return null;
		}
		return new EclipseResourceNode(child);
	}

	private void createParents(final IResource r) {
		final IContainer p = r.getParent();
		if (p.getType() == IResource.ROOT) {
			return;
		}
		if (p instanceof IContainer) {
			final IContainer f = p;
			if (!f.exists()) {
				createParents(f);
				try {
					if (f.getType() == IResource.PROJECT) {
						final IProject project = ((IProject) f);
						project.create(new NullProgressMonitor());
						project.open(new NullProgressMonitor());
					} else {
						((IFolder) f).create(true, true, new NullProgressMonitor());
					}
				} catch (final CoreException e) {
				}
			}
		}
	}

	/* (non-Javadoc)
	   * @see org.eclipse.compare.ResourceNode#getContents()
	   */
	public InputStream getContents() throws CoreException {
		if (getResource().exists()) {
			return super.getContents();
		}
		return null;
	}

	public ITypedElement replace(ITypedElement child, final ITypedElement other) {

		if (child == null) { // add resource
			// create a node without a resource behind it!
			final IResource resource = getResource();
			if (resource instanceof IContainer) {
				final IContainer folder = (IContainer) resource;
				if (other.getType() == ITypedElement.FOLDER_TYPE) {
					IResource childResource = null;
					if (folder.getType() == IResource.ROOT) {
						childResource = ((IWorkspaceRoot) folder).getProject(other.getName());
					} else {
						childResource = folder.getFolder(new Path(other.getName()));
					}
					child = new EclipseResourceNode(childResource);
				} else {
					final IFile file = folder.getFile(new Path(other.getName()));
					child = new EclipseResourceNode(file);
				}
			}
		}

		if (other == null) { // delete resource
			final IResource resource = getResource();
			if (resource instanceof IFolder) {
				final IFolder folder = (IFolder) resource;
				final IFile file = folder.getFile(child.getName());
				if ((file != null) && file.exists()) {
					fDeleteFile = file;
					fDirty = true;
				}
			}
			return null;
		}

		if ((other instanceof IStreamContentAccessor) && (child instanceof IEditableContent)) {
			final IEditableContent dst = (IEditableContent) child;

			try {
				final InputStream is = ((IStreamContentAccessor) other).getContents();
				if (is != null) {
					final byte[] bytes = readBytes(is);
					if (bytes != null) {
						dst.setContent(bytes);
					}
				}
			} catch (final CoreException ex) {
			}
		}
		fireContentChanged();
		return child;
	}

	public void setContent(final byte[] contents) {
		fDirty = true;
		super.setContent(contents);
	}

}
