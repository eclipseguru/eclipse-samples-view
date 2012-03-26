package org.eclipsercp.book.tools.views;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import org.eclipsercp.book.tools.BundleLocation;
import org.eclipsercp.book.tools.Sample;
import org.eclipsercp.book.tools.SamplesModel;
import org.eclipsercp.book.tools.Utils;
import org.eclipsercp.book.tools.actions.CompareSamplesOperation;
import org.eclipsercp.book.tools.actions.ImportSampleOperation;

public class SamplesView extends ViewPart {
	/*
	 * Button Ids
	 */
	private static final int IMPORT_ID = 101;
	private static final int COMPARE_ID = 102;
	private static final int TARGET_ID = 103;

	private BundleLocation location;
	private BundleLocation targetLocation;
	private TableViewer samplesList;
	private SamplesModel samplesModel;
	private SamplesModel targetModel;
	private Action importAction;
	private Action compareAction;
	private Action targetAction;

	public SamplesView() {
	}

	protected void buttonPressed(final int buttonId) {
		switch (buttonId) {
			case IMPORT_ID:
				importProjects();
				samplesList.update(samplesModel.getSamples(), null);
				break;
			case TARGET_ID:
				importTarget();
				break;
			case COMPARE_ID:
				new CompareSamplesOperation(samplesModel).run(getSelection());
				break;
		}
	}

	public void createPartControl(final Composite parent) {
		final Composite workArea = new Composite(parent, SWT.NONE);
		final GridLayout layout = new GridLayout();
		workArea.setLayout(layout);
		workArea.setLayoutData(new GridData(GridData.FILL_BOTH | GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL));
		createSamplesList(workArea);
		Dialog.applyDialogFont(workArea);
		updateTargetList();
		updateSamplesList();
		samplesList.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(final DoubleClickEvent event) {
				buttonPressed(IMPORT_ID);
			}
		});
		fillActionBars(samplesList.getControl());
	}

	/**
	 * Create the checkbox list for the found samples.
	 * 
	 * @param workArea
	 */
	private void createSamplesList(final Composite listComposite) {
//		Label title = new Label(listComposite, SWT.NONE);
//		title.setText("Sample Code:");
		samplesList = new TableViewer(listComposite, SWT.BORDER | SWT.SINGLE);
		final GridData listData = new GridData(GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL | GridData.FILL_BOTH);
		listData.heightHint = 125;
		listData.widthHint = 100;
		samplesList.getControl().setLayoutData(listData);
		samplesList.setSorter(new ViewerSorter() {
			public int compare(final Viewer viewer, final Object e1, final Object e2) {
				final Sample c1 = (Sample) e1;
				final Sample c2 = (Sample) e2;
				return c1.getNumber().compareTo(c2.getNumber());
			}
		});
		samplesList.setContentProvider(new ArrayContentProvider());
		samplesList.setLabelProvider(new SamplesLabelProvider());
		samplesList.getControl().setFocus();
	}

	protected void fillActionBars(final Control control) {
		final IActionBars bars = getViewSite().getActionBars();
		importAction = new Action("&Import") {
			public void run() {
				buttonPressed(IMPORT_ID);
			}
		};
		importAction.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.ui", "icons/full/etool16/import_wiz.gif"));
		importAction.setToolTipText("Import the selected sample code into the Eclipse workspace");

		targetAction = new Action("&Load Target") {
			public void run() {
				buttonPressed(TARGET_ID);
			}
		};
		targetAction.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin("com.eclipsesource.training.rcp.basic.tools", "icons/glyph4.gif"));
		targetAction.setToolTipText("Load the target platform");

		compareAction = new Action("&Compare with Workspace") {
			public void run() {
				buttonPressed(COMPARE_ID);
			}
		};
		compareAction.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.compare", "icons/full/elcl16/twowaycompare_co.gif"));
		compareAction.setToolTipText("Compare the projects in your workspace with the currently selected sample code");
		final IToolBarManager toolbar = bars.getToolBarManager();
		toolbar.add(importAction);
		toolbar.add(compareAction);

		final IMenuManager menu = bars.getMenuManager();
		menu.add(importAction);
		menu.add(targetAction);
		menu.add(compareAction);

		final MenuManager contextMenu = new MenuManager();
		contextMenu.add(importAction);
		contextMenu.add(compareAction);
		control.setMenu(contextMenu.createContextMenu(control));
	}

	private Sample getSelection() {
		final IStructuredSelection selected = (IStructuredSelection) samplesList.getSelection();
		if ((selected != null) && (selected.size() == 1)) {
			return (Sample) selected.getFirstElement();
		}
		return null;
	}

	protected Shell getShell() {
		return getSite().getShell();
	}

	private void importProjects() {
		final ImportSampleOperation operation = new ImportSampleOperation(getShell(), getSelection(), samplesModel, true);
		Utils.run(getShell(), operation);
	}

	private void importTarget() {
		if (targetModel.getSamples().length < 1) {
			return;
		}
		final ImportSampleOperation operation = new ImportSampleOperation(getShell(), targetModel.getSamples()[0], targetModel, false);
		Utils.run(getShell(), operation);
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		samplesList.getControl().setFocus();
	}

	protected void setLocation(final BundleLocation value) {
		location = value;
	}

	protected void setTargetLocation(final BundleLocation value) {
		targetLocation = value;
	}

	/**
	 * Update the list of Samples and projects
	 * 
	 * @param path
	 */
	protected void updateSamplesList() {
		final Job job = new Job("Finding Samples") {
			protected IStatus run(final IProgressMonitor monitor) {
				samplesModel = new SamplesModel();
				samplesModel.init(location, monitor);
				getShell().getDisplay().asyncExec(new Runnable() {
					public void run() {
						if ((samplesList != null) && !samplesList.getControl().isDisposed()) {
							samplesList.setInput(samplesModel.getSamples());
						}
					}
				});
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	protected void updateTargetList() {
		final Job job = new Job("Finding Targets") {
			protected IStatus run(final IProgressMonitor monitor) {
				targetModel = new SamplesModel();
				targetModel.init(targetLocation, monitor);
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}
}
