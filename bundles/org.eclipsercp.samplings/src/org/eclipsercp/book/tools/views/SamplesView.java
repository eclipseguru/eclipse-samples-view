package org.eclipsercp.book.tools.views;

import org.eclipse.jface.action.Action;

import org.eclipsercp.book.tools.BundleLocation;

import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.viewers.*;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.*;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.eclipsercp.book.tools.*;
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

	protected void setLocation(BundleLocation value) {
		location = value;
	}

	protected void setTargetLocation(BundleLocation value) {
		targetLocation = value;
	}
	
	public void createPartControl(Composite parent) {
		Composite workArea = new Composite(parent, SWT.NONE);
		GridLayout layout = new GridLayout();
		workArea.setLayout(layout);
		workArea.setLayoutData(new GridData(GridData.FILL_BOTH
				| GridData.GRAB_HORIZONTAL | GridData.GRAB_VERTICAL));
		createSamplesList(workArea);
		Dialog.applyDialogFont(workArea);
		updateTargetList();
		updateSamplesList();
		samplesList.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event) {
				buttonPressed(IMPORT_ID);
			}
		});
		fillActionBars(samplesList.getControl());
	}

	protected void fillActionBars(Control control) {
		IActionBars bars = getViewSite().getActionBars();
		importAction = new Action("&Import") {
			public void run() {
				buttonPressed(IMPORT_ID);
			}
		};
		importAction.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.ui", "icons/full/etool16/import_wiz.gif"));
		importAction
				.setToolTipText("Import the selected sample code into the Eclipse workspace");

		targetAction = new Action("&Load Target") {
			public void run() {
				buttonPressed(TARGET_ID);
			}
		};
		targetAction.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin("com.eclipsesource.training.rcp.basic.tools", "icons/glyph4.gif"));
		targetAction
				.setToolTipText("Load the target platform");

		compareAction = new Action("&Compare with Workspace") {
			public void run() {
				buttonPressed(COMPARE_ID);
			}
		};
		compareAction.setImageDescriptor(AbstractUIPlugin.imageDescriptorFromPlugin("org.eclipse.compare", "icons/full/elcl16/twowaycompare_co.gif"));
		compareAction
				.setToolTipText("Compare the projects in your workspace with the currently selected sample code");
		IToolBarManager toolbar = bars.getToolBarManager();
		toolbar.add(importAction);
		toolbar.add(compareAction);
		
		IMenuManager menu = bars.getMenuManager();
		menu.add(importAction);
		menu.add(targetAction);
		menu.add(compareAction);

		MenuManager contextMenu = new MenuManager();
		contextMenu.add(importAction);
		contextMenu.add(compareAction);
		control.setMenu(contextMenu.createContextMenu(control));
	}

	protected Shell getShell() {
		return getSite().getShell();
	}

	protected void buttonPressed(int buttonId) {
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

	private void importProjects() {
		ImportSampleOperation operation = new ImportSampleOperation(
				getShell(), getSelection(), samplesModel, true);
		Utils.run(getShell(), operation);
	}
	
	private void importTarget() {
		if (targetModel.getSamples().length < 1)
			return;
		ImportSampleOperation operation = new ImportSampleOperation(
				getShell(), targetModel.getSamples()[0], targetModel, false);
		Utils.run(getShell(), operation);
	}

	/**
	 * Create the checkbox list for the found samples.
	 * 
	 * @param workArea
	 */
	private void createSamplesList(Composite listComposite) {
//		Label title = new Label(listComposite, SWT.NONE);
//		title.setText("Sample Code:");
		samplesList = new TableViewer(listComposite, SWT.BORDER | SWT.SINGLE);
		GridData listData = new GridData(GridData.GRAB_HORIZONTAL
				| GridData.GRAB_VERTICAL | GridData.FILL_BOTH);
		listData.heightHint = 125;
		listData.widthHint = 100;
		samplesList.getControl().setLayoutData(listData);
		samplesList.setSorter(new ViewerSorter() {
			public int compare(Viewer viewer, Object e1, Object e2) {
				Sample c1 = (Sample) e1;
				Sample c2 = (Sample) e2;
				return c1.getNumber().compareTo(c2.getNumber());
			}
		});
		samplesList.setContentProvider(new ArrayContentProvider());
		samplesList.setLabelProvider(new SamplesLabelProvider());
		samplesList.getControl().setFocus();
	}

	/**
	 * Update the list of Samples and projects
	 * 
	 * @param path
	 */
	protected void updateSamplesList() {
		Job job = new Job("Finding Samples") {
			protected IStatus run(IProgressMonitor monitor) {
				samplesModel = new SamplesModel();
				samplesModel.init(location, monitor);
				getShell().getDisplay().asyncExec(new Runnable() {
					public void run() {
						if (samplesList != null
								&& !samplesList.getControl().isDisposed())
							samplesList.setInput(samplesModel.getSamples());
					}
				});
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}
	
	protected void updateTargetList() {
		Job job = new Job("Finding Targets") {
			protected IStatus run(IProgressMonitor monitor) {
				targetModel = new SamplesModel();
				targetModel.init(targetLocation, monitor);
				return Status.OK_STATUS;
			}
		};
		job.schedule();
	}

	private Sample getSelection() {
		IStructuredSelection selected = (IStructuredSelection) samplesList.getSelection();
		if (selected != null && selected.size() == 1)
			return (Sample) ((IStructuredSelection) selected).getFirstElement();
		return null;
	}

	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus() {
		samplesList.getControl().setFocus();
	}
}
