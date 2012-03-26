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

import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.Iterator;
import java.util.Set;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareEditorInput;
import org.eclipse.compare.IContentChangeListener;
import org.eclipse.compare.IContentChangeNotifier;
import org.eclipse.compare.ITypedElement;
import org.eclipse.compare.ResourceNode;
import org.eclipse.compare.internal.BufferedResourceNode;
import org.eclipse.compare.internal.CompareEditor;
import org.eclipse.compare.internal.Utilities;
import org.eclipse.compare.structuremergeviewer.DiffNode;
import org.eclipse.compare.structuremergeviewer.DiffTreeViewer;
import org.eclipse.compare.structuremergeviewer.IDiffContainer;
import org.eclipse.compare.structuremergeviewer.IDiffElement;
import org.eclipse.compare.structuremergeviewer.IStructureComparator;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.widgets.Composite;

/**
 * A two-way or three-way compare for arbitrary IResources.
 */
public class ResourceToFileCompareInput extends CompareEditorInput {

	class MyDiffNode extends DiffNode implements IContentChangeListener {

		private boolean fDirty = false;
		private ITypedElement fLastId;
		private String fLastName;

		public MyDiffNode(final IDiffContainer parent, final int description, final ITypedElement ancestor, final ITypedElement left, final ITypedElement right) {
			super(parent, description, ancestor, left, right);
		}

		void clearDirty() {
			fDirty = false;
		}

		public void contentChanged(final IContentChangeNotifier source) {
			fireChange();
		}

		public void fireChange() {
			super.fireChange();
			setDirty(true);
			fDirty = true;
			if (fDiffViewer != null) {
				fDiffViewer.refresh(this);
			}
			setDirty(true);
			try {
				commit(new NullProgressMonitor(), this);
			} catch (final CoreException e) {
			}
		}

		public ITypedElement getId() {
			final ITypedElement id = super.getId();
			if (id == null) {
				return fLastId;
			}
			fLastId = id;
			return id;
		}

		public String getName() {
			if (fLastName == null) {
				fLastName = super.getName();
			}
			if (fDirty) {
				return '<' + fLastName + '>';
			}
			return fLastName;
		}
	}

	/*
	 * Recursively walks the diff tree and commits all changes.
	 */
	private static void commit(final IProgressMonitor pm, final DiffNode node) throws CoreException {

		if (node instanceof MyDiffNode) {
			((MyDiffNode) node).clearDirty();
		}

		final ITypedElement left = node.getLeft();
		if (left instanceof EclipseResourceNode) {
			((EclipseResourceNode) left).commit(pm);
		}

		final ITypedElement right = node.getRight();
		if (right instanceof EclipseResourceNode) {
			((EclipseResourceNode) right).commit(pm);
		}

		final IDiffElement[] children = node.getChildren();
		if (children != null) {
			for (int i = 0; i < children.length; i++) {
				final IDiffElement element = children[i];
				if (element instanceof DiffNode) {
					commit(pm, (DiffNode) element);
				}
			}
		}
	}

	private boolean fThreeWay = false;
	private Object fRoot;
	private IStructureComparator fAncestor;
	private IStructureComparator fLeft;
	private IStructureComparator fRight;
	private IStructureComparator fAncestorResource;
	private IResource fLeftResource;
	private DiffTreeViewer fDiffViewer;
	private IAction fOpenAction;

	private final Float sample1;

	private final Float sample2;

	/*
	 * Creates an compare editor input for the given selection.
	 */
	public ResourceToFileCompareInput(final Float sample1, final Float sample2, final CompareConfiguration config) {
		super(config);
		this.sample1 = sample1;
		this.sample2 = sample2;
		initializeCompareConfiguration();
	}

	private void collectDirtyResources(final Object o, final Set collector) {
		if (o instanceof DiffNode) {
			final DiffNode node = (DiffNode) o;

			final ITypedElement left = node.getLeft();
			if (left instanceof BufferedResourceNode) {
				final BufferedResourceNode bn = (BufferedResourceNode) left;
				if (bn.isDirty()) {
					final IResource resource = bn.getResource();
					if (resource instanceof IFile) {
						collector.add(resource);
					}
				}
			}

			final ITypedElement right = node.getRight();
			if (right instanceof BufferedResourceNode) {
				final BufferedResourceNode bn = (BufferedResourceNode) right;
				if (bn.isDirty()) {
					final IResource resource = bn.getResource();
					if (resource instanceof IFile) {
						collector.add(resource);
					}
				}
			}

			final IDiffElement[] children = node.getChildren();
			if (children != null) {
				for (int i = 0; i < children.length; i++) {
					final IDiffElement element = children[i];
					if (element instanceof DiffNode) {
						collectDirtyResources(element, collector);
					}
				}
			}
		}
	}

	private void copy(final Object element) {
		if (element instanceof DiffNode) {
			((DiffNode) element).copy(false);
			try {
				commit(new NullProgressMonitor(), (DiffNode) element);
			} catch (final CoreException e) {
			}
		}
		if (element instanceof IDiffContainer) {
			final IDiffElement[] children = ((IDiffContainer) element).getChildren();
			for (int i = 0; i < children.length; i++) {
				final IDiffElement node = children[i];
				copy(node);
			}
		}
	};

	private void copyAllRightToLeft(final ISelection selection) {
		if (selection instanceof IStructuredSelection) {
			final Iterator elements = ((IStructuredSelection) selection).iterator();
			while (elements.hasNext()) {
				final Object element = elements.next();
				copy(element);
			}
		}
	}

	public Viewer createDiffViewer(final Composite parent) {
		fDiffViewer = new DiffTreeViewer(parent, getCompareConfiguration()) {
			private Action fCopyAllRightToLeft;

			protected void fillContextMenu(final IMenuManager manager) {

				if (fOpenAction == null) {
					fOpenAction = new Action() {
						public void run() {
							handleOpen(null);
						}
					};
					fOpenAction.setText("&Show Comparison");
				}

				boolean enable = false;
				final ISelection selection = getSelection();
				if (selection instanceof IStructuredSelection) {
					final IStructuredSelection ss = (IStructuredSelection) selection;
					if (ss.size() == 1) {
						final Object element = ss.getFirstElement();
						if (element instanceof MyDiffNode) {
							final ITypedElement te = ((MyDiffNode) element).getId();
							if (te != null) {
								enable = !ITypedElement.FOLDER_TYPE.equals(te.getType());
							}
						} else {
							enable = true;
						}
					}
				}
				fOpenAction.setEnabled(enable);

				if (fCopyAllRightToLeft == null) {
					fCopyAllRightToLeft = new Action("Copy into &Workspace") {
						public void run() {
							copyAllRightToLeft(getSelection());
						}
					};
				}

				fCopyAllRightToLeft.setEnabled(true);

				manager.add(fOpenAction);
				super.fillContextMenu(manager);
				manager.add(new Separator());
				manager.add(fCopyAllRightToLeft);
			}
		};
		return fDiffViewer;
	}

	/* (non Javadoc)
	 * see IAdaptable.getAdapter
	 */
	public Object getAdapter(final Class adapter) {
		return null;
	}

	/*
	 * Creates a <code>IStructureComparator</code> for the given input.
	 * Returns <code>null</code> if no <code>IStructureComparator</code>
	 * can be found for the <code>IResource</code>.
	 */
	private IStructureComparator getStructure(final IResource input) {
		final ResourceNode node = new EclipseResourceNode(input);
		return node;
	}

	public String getTitle() {
		return "Sample " + sample1 + " - Sample " + sample2;
	}

	/**
	 * Initializes the images in the compare configuration.
	 */
	void initializeCompareConfiguration() {
		final CompareConfiguration cc = getCompareConfiguration();
		cc.setProperty(CompareConfiguration.IGNORE_WHITESPACE, Boolean.TRUE);
		cc.setProperty(CompareEditor.CONFIRM_SAVE_PROPERTY, Boolean.FALSE);
		cc.setLeftEditable(true);
		cc.setLeftLabel("Sample " + sample1 + " (in workspace)");
		cc.setRightEditable(false);
		cc.setRightLabel("Sample " + sample2 + " (in file system)");
	}

	/*
	 * Returns true if compare can be executed for the given selection.
	 */
	public boolean isEnabled(final ISelection s) {
		return true;
	}

	/*
	 * Performs a two-way or three-way diff on the current selection.
	 */
	public Object prepareInput(final IProgressMonitor pm) throws InvocationTargetException {

		try {
			// fix for PR 1GFMLFB: ITPUI:WIN2000 - files that are out of sync with the file system appear as empty              
			fLeftResource.refreshLocal(IResource.DEPTH_INFINITE, pm);
			//fRightResource.refreshLocal(IResource.DEPTH_INFINITE, pm);
			if (fThreeWay && (fAncestorResource != null)) {
				pm.beginTask(Utilities.getString("ResourceCompare.taskName"), IProgressMonitor.UNKNOWN); //$NON-NLS-1$
			}

			final String leftLabel = fLeftResource.getName();
			final String rightLabel = "right";

			String title;
			if (fThreeWay) {
				final String format = Utilities.getString("ResourceCompare.threeWay.title"); //$NON-NLS-1$
				final String ancestorLabel = "ancestor";
				title = MessageFormat.format(format, new String[] { ancestorLabel, leftLabel, rightLabel });
			} else {
				final String format = Utilities.getString("ResourceCompare.twoWay.title"); //$NON-NLS-1$
				title = MessageFormat.format(format, new String[] { leftLabel, rightLabel });
			}
			setTitle(title);

			final Differencer d = new Differencer() {
				protected Object visit(final Object parent, final int description, final Object ancestor, final Object left, final Object right) {
					final MyDiffNode node = new MyDiffNode((IDiffContainer) parent, description, (ITypedElement) ancestor, (ITypedElement) left, (ITypedElement) right);
					final Object leftNode = node.getLeft();
					if ((leftNode != null) && (leftNode instanceof IContentChangeNotifier)) {
						((IContentChangeNotifier) leftNode).addContentChangeListener(node);
					}
					return node;
				}
			};

			fRoot = d.findDifferences(fThreeWay, pm, null, fAncestor, fLeft, fRight);
			return fRoot;

		} catch (final CoreException ex) {
			throw new InvocationTargetException(ex);
		} finally {
			pm.done();
		}
	}

	public void saveChanges(final IProgressMonitor pm) throws CoreException {
		super.saveChanges(pm);
		if (fRoot instanceof DiffNode) {
			try {
				commit(pm, (DiffNode) fRoot);
			} finally {
				if (fDiffViewer != null) {
					fDiffViewer.refresh();
				}
				setDirty(false);
			}
		}
	}

	public void setSelection(final IResource project, final IStructureComparator file) {

		// IResource[] selection= Utilities.getResources(s);

		fThreeWay = false;

		fAncestorResource = file;
		fLeftResource = project;
		fAncestor = null;
		fLeft = getStructure(fLeftResource);
		fRight = file;
		fAncestor = file;
	}
}
