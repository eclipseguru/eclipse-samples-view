package org.eclipsercp.book.tools.actions;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.core.runtime.jobs.IJobManager;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.debug.core.*;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialogWithToggle;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.*;
import org.eclipse.ui.part.FileEditorInput;
import org.eclipse.ui.preferences.ScopedPreferenceStore;
import org.eclipse.ui.progress.UIJob;
import org.eclipsercp.book.tools.*;
import org.eclipsercp.book.tools.Sample.ProjectImport;
import org.osgi.service.prefs.Preferences;

public class ImportSampleOperation implements IRunnableWithProgress {

	private Sample sample;
	private SamplesModel samples;
	private Shell shell;
	private final boolean cleanup;

	public ImportSampleOperation(Shell shell, Sample sample,
			SamplesModel samples, boolean cleanup) {
		this.shell = shell;
		this.sample = sample;
		this.samples = samples;
		this.cleanup = cleanup;
	}

	public void run(IProgressMonitor monitor) {
		if (sample == null)
			return;
		if (cleanup)
			cleanupWorkspace();
		importSample(monitor);
		openDefaultEditor();
	}

	protected void importSample(IProgressMonitor monitor) {
		try {
			importSample(sample, monitor);
		} catch (CoreException e) {
			Utils.handleError(shell, e, "Error", "Importing projects");
		} catch (InvocationTargetException e) {
			Utils.handleError(shell, e, "Error", "Importing projects");
		} catch (InterruptedException e) {
			Utils.handleError(shell, e, "Error", "Importing projects");
		}
	}

	protected void cleanupWorkspace() {
		// delete launch configurations
		ILaunchManager mng = DebugPlugin.getDefault().getLaunchManager();
		try {
			ILaunchConfiguration[] launchConfigs = mng.getLaunchConfigurations();
			for (int i = 0; i < launchConfigs.length; i++) {
				ILaunchConfiguration configuration = launchConfigs[i];
				configuration.delete();
			}
		} catch (CoreException e) {
			Utils.handleError(shell, e, "Error",
					"Problems deleting launch configurations");
		}
	}

	private void openDefaultEditor() {
		if (sample.getFileToOpen() == null)
			return;

		UIJob job = new UIJob("Opening editor") {
			public IStatus runInUIThread(IProgressMonitor monitor) {
				IFile fileToOpen = ResourcesPlugin.getWorkspace().getRoot().getFile(new Path(sample.getFileToOpen()));
				if (fileToOpen == null || !fileToOpen.exists()) 
					return Status.OK_STATUS;
				IWorkbench workbench = PlatformUI.getWorkbench();
				IWorkbenchPage page = workbench.getActiveWorkbenchWindow().getActivePage();
				IEditorRegistry registry = workbench.getEditorRegistry();
				IEditorDescriptor descriptor = registry.getDefaultEditor(fileToOpen.getName());
				String id  = descriptor == null ? "org.eclipse.ui.DefaultTextEditor" : descriptor.getId();
				try {
					page.openEditor(new FileEditorInput(fileToOpen), id);
				} catch (PartInitException e) {
					Utils.handleError(shell, e, "Error","Error opening editor");
				}
				return Status.OK_STATUS;
			};
		};
		job.schedule(750);
	}

	private void importSample(final Sample sample,
			IProgressMonitor monitor) throws CoreException,
			InvocationTargetException, InterruptedException {
		boolean prompted = false;
		try {
			monitor.beginTask("Importing", 100);

			if (!deleteProjects(monitor, prompted))
				return;

			// import
			SubProgressMonitor subImportMonitor = new SubProgressMonitor(
					monitor, 70);
			subImportMonitor.beginTask("Importing projects", sample
					.getProjects().size() * 100);

			final List importedProjects = new ArrayList();
			IWorkspaceRunnable workspaceOperation = new IWorkspaceRunnable() {
				public void run(IProgressMonitor monitor) throws CoreException {
					try {
						// Import all the projects for this sample
						for (Iterator it = sample.getProjects().iterator(); it.hasNext();) {
							BundleLocation element = (BundleLocation) it.next();
							importProject(sample, element, true,
									new SubProgressMonitor(monitor, 100));
						}

						// import all the prereqs for this sample
						Sample.ProjectImport[] imports = sample.getImports();
						for (int i = 0; i < imports.length; i++) 
							importPrereq(imports[i], sample, importedProjects, monitor);
					} catch (InvocationTargetException e) {
						throw new CoreException(Utils.statusFrom(e));
					} catch (InterruptedException e) {
						throw new CoreException(Utils.statusFrom(e));
					}
				}

				private void importPrereq(ProjectImport prereq,
						final Sample firstElement,
						final List importedProjects, IProgressMonitor monitor)
						throws InvocationTargetException, InterruptedException,
						CoreException {

					Sample sample = samples.findSampleById(prereq.getSampleNumber());
					if (sample == null)
						return;
					for (Iterator it = sample.getProjects().iterator(); it.hasNext();) {
						BundleLocation project = (BundleLocation) it.next();
						IProject current = ResourcesPlugin.getWorkspace().getRoot().getProject(project.location.lastSegment());
						importedProjects.add(current);
						if (current.getName().endsWith(prereq.getProjectName())) {
							if (current.exists() && !current.isOpen()) {
								current.open(null);
							} else {
								importProject(sample, project, false, new SubProgressMonitor(monitor, 100));
							}
						}
					}

				}
			};
			ResourcesPlugin.getWorkspace().run(workspaceOperation,
					(ISchedulingRule) ResourcesPlugin.getWorkspace().getRoot(),
					IWorkspace.AVOID_UPDATE,
					(IProgressMonitor) subImportMonitor);

			// HACK - to ensure that the imported projects don't have
			// compile errors, close and re-open them.
			try {
				IJobManager jobManager = Platform.getJobManager();
				jobManager.join(ResourcesPlugin.FAMILY_MANUAL_BUILD, monitor);
				jobManager.join(ResourcesPlugin.FAMILY_AUTO_BUILD, monitor);

				IWorkspaceRunnable closeRunnable = new IWorkspaceRunnable() {
					public void run(IProgressMonitor monitor)
							throws CoreException {
						boolean reopen = false;
						for (Iterator it = importedProjects.iterator(); it.hasNext();) {
							IProject project = (IProject) it.next();
							if (project.exists() && existsProblems(project)) {
								reopen = true;
							}
						}
						if (reopen) {
							IProject[] projects = ResourcesPlugin
									.getWorkspace().getRoot().getProjects();
							for (int i = 0; i < projects.length; i++) {
								IProject project = projects[i];
								project.close(new NullProgressMonitor());
							}
						}
					}
				};
				IWorkspaceRunnable openRunnable = new IWorkspaceRunnable() {
					public void run(IProgressMonitor monitor) throws CoreException {
						IProject[] projects = ResourcesPlugin.getWorkspace()
								.getRoot().getProjects();
						for (int i = 0; i < projects.length; i++) {
							IProject project = projects[i];
							project.open(new NullProgressMonitor());
						}
					}
				};
				ResourcesPlugin.getWorkspace().run(closeRunnable,
						new NullProgressMonitor());
				ResourcesPlugin.getWorkspace().run(openRunnable,
						new NullProgressMonitor());

			} catch (InterruptedException e) {
				// just continue.
			}

			subImportMonitor.done();
		} finally {
			monitor.done();
		}
	}

	private boolean deleteProjects(IProgressMonitor monitor, boolean prompted) throws CoreException {
		// deletion
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot()
				.getProjects();
		SubProgressMonitor subDeleteMonitor = new SubProgressMonitor(
				monitor, 30);
		subDeleteMonitor.beginTask("Deleting projects",
				projects.length * 100);
		for (int i = 0; i < projects.length; i++) {
			IProject project = projects[i];
			if (project.isAccessible()
					&& project.getPersistentProperty(samples.getTagId()) != null) {
				if (!prompted && !promptToOverwrite())
					return false;
				prompted = true;
				project.delete(true, true, new SubProgressMonitor(
						subDeleteMonitor, 100));
			}
		}
		subDeleteMonitor.done();
		return true;
	}

	/**
	 * Returns whether the given project contains any problem markers of the
	 * specified severity.
	 * 
	 * @param proj
	 *            the project to search
	 * @return whether the given project contains any problems that should stop
	 *         it from launching
	 * @throws CoreException
	 *             if an error occurs while searching for problem markers
	 */
	protected boolean existsProblems(IProject proj) throws CoreException {
		IMarker[] markers = proj.findMarkers(IMarker.PROBLEM, true,
				IResource.DEPTH_INFINITE);
		if (markers.length > 0) {
			for (int i = 0; i < markers.length; i++) {
				if (isLaunchProblem(markers[i])) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Returns whether the given problem should potentially abort the launch. By
	 * default if the problem has an error severity, the problem is considered a
	 * potential launch problem. Subclasses may override to specialize error
	 * detection.
	 * 
	 * @param problemMarker
	 *            candidate problem
	 * @return whether the given problem should potentially abort the launch
	 * @throws CoreException
	 *             if any exceptions occurr while accessing marker attributes
	 */
	protected boolean isLaunchProblem(IMarker problemMarker)
			throws CoreException {
		Integer severity = (Integer) problemMarker
				.getAttribute(IMarker.SEVERITY);
		if (severity != null) {
			return severity.intValue() >= IMarker.SEVERITY_ERROR;
		}

		return false;
	}

	public boolean promptToOverwrite() {
		Preferences prefs = new InstanceScope()
				.getNode(IConstants.PLUGIN_ID);
		boolean defaultValue = prefs.getBoolean(
				IConstants.PROMPT_OVERWRITE_PREF, true);
		if (!defaultValue)
			return true;
		final boolean[] retVal = { false };
		shell.getDisplay().syncExec(new Runnable() {
			public void run() {
				ScopedPreferenceStore store = new ScopedPreferenceStore(
						new InstanceScope(), IConstants.PLUGIN_ID);
				Dialog d = MessageDialogWithToggle
						.openOkCancelConfirm(
								shell,
								"Warning",
								"This will delete all imported projects and in your workspace and replace them with the projects for the selected samples. Are you sure about this?",
								"Don't show this message again", false, store,
								IConstants.PROMPT_OVERWRITE_PREF);
				retVal[0] = d.getReturnCode() == Dialog.OK;
				try {
					store.save();
				} catch (IOException e) {
					Utils.handleError(shell, e, "Error",
							"Problems saving preferences");
				}
			}
		});
		return retVal[0];
	}

	/**
	 * Import the project described in record. If it is successful return true.
	 * 
	 * @param sample
	 * @return boolean <code>true</code> of successult
	 * @throws InterruptedException
	 * @throws InvocationTargetException
	 */
	private boolean importProject(final Sample sample,
			final BundleLocation record, final boolean updateProperty,
			IProgressMonitor monitor) throws InvocationTargetException,
			InterruptedException {
		monitor.beginTask("Importing", 2000);

		String projectName = record.location.lastSegment();
		final IWorkspace workspace = ResourcesPlugin.getWorkspace();
		final IProject project = workspace.getRoot().getProject(projectName);
		final IProjectDescription description = workspace.newProjectDescription(projectName);
		URL instanceLocation = Platform.getInstanceLocation().getURL();
		File projectDir = new File(instanceLocation.getPath(), projectName);
		projectDir.mkdirs();
		try {
			Utils.copy(record, projectDir, true, monitor);
			description.setLocation(null);
			if (project.exists()) {
				if (!project.isOpen())
					project.open(null);
				project.delete(true, null);
			}
			project.create(description, new SubProgressMonitor(monitor, 1000));
			if (monitor.isCanceled())
				throw new OperationCanceledException();
			project.open(IResource.BACKGROUND_REFRESH, new SubProgressMonitor(
					monitor, 1000));
			if (updateProperty)
				project.setPersistentProperty(
						IConstants.SAMPLE_NUMBER_KEY, sample.getNumber().toString());
			project.setPersistentProperty(samples.getTagId(), "samples-manager-project");
		} catch (Exception e) {
			Utils.handleError(shell, e, "Error", "Problem importing projects");
		}

		return true;
	}
}
