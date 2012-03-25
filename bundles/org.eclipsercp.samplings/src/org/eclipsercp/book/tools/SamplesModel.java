package org.eclipsercp.book.tools;

import java.util.*;
import org.eclipse.core.resources.*;
import org.eclipse.core.runtime.*;
import org.eclipse.ui.PlatformUI;

public class SamplesModel {

	private Sample[] samples;
	private BundleLocation location;

	public SamplesModel() {
		super();
	}
	
	/**
	 * Initialize the model from the given location. 
	 * 
	 * @param location the location in which to look for samples
	 * @longOp this operation is long running and shouldn't be run from a
	 * 	responsive thread.
	 */
	public void init(BundleLocation location, IProgressMonitor monitor) {
		this.location = location;
		updateProjectsList(monitor);
	}

	public Sample[] getSamples() {
		return samples;
	}

	public QualifiedName getTagId() {
		return location.getId();
	}
	
	public static Float getCurrentSampleNumber() {
		IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (int i = 0; i < projects.length; i++) {
			IProject project = projects[i];
			try {
				if (!project.isAccessible())
					continue;
				String result = project.getPersistentProperty(IConstants.SAMPLE_NUMBER_KEY);
				if (result != null && result.charAt(0) != '0')
					return new Float(result);
			} catch (CoreException e) {
				Utils.handleError(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), e, "Error", "Error finding current sample number.");
			}
		}
		return new Float(1.0);
	}

	public Sample findSampleById(Float number) {
		for (int i = 0; i < samples.length; i++) {
			Sample sample = samples[i];
			if (sample.getNumber().floatValue() == number.floatValue())
				return sample;
		}
		return null;
	}
	
	private void updateProjectsList(IProgressMonitor monitor) {
		if (location == null)
			return;
		monitor.beginTask("Searching", 100);
		samples = new Sample[0];
		SortedMap result = new TreeMap();
		monitor.worked(10);
		collectProjectFiles(result, location, monitor);
		samples = (Sample[]) result.values().toArray(new Sample[result.values().size()]);
		monitor.done();
	}

	/*
	 * Do a breadth-first search of the given bundle location looking for 
	 * projects.  Add any discovered projects to the given result.
	 */
	private void collectProjectFiles(SortedMap result, BundleLocation base, IProgressMonitor monitor) {
		if (monitor.isCanceled())
			return;
		monitor.subTask("Reading " + base.location);
		for (Enumeration contents = base.bundle.getEntryPaths(base.location.toString()); contents != null && contents.hasMoreElements();) {
			String entry = (String) contents.nextElement();
			// if the entry is a .project file then we have found a project so get
			// the parent and record that as a project.
			if (!entry.endsWith("/") && entry.endsWith(IProjectDescription.DESCRIPTION_FILE_NAME)) {
				// skip over any snippets folders
				if (base.location.lastSegment().equals("snippets"))
					break;
				IPath projectLocation = new Path(entry).removeLastSegments(1);
				IPath sampleLocation = projectLocation.removeLastSegments(1);
				if (sampleLocation.isEmpty())
					continue;
				Sample sample = (Sample) result.get(sampleLocation.toString());
				if (sample == null) {
					sample = new Sample(new BundleLocation(base.bundle, sampleLocation));
					result.put(sampleLocation.toString(), sample);
				}
				sample.addProject(new BundleLocation(base.bundle, projectLocation));
				// stop as soon as we find the first .project file since nested projects are not supported.
				return;
			}
		}
		// no project description found, so recurse into sub-directories
		for (Enumeration contents = base.bundle.getEntryPaths(base.location.toString()); contents != null && contents.hasMoreElements();) {
			String entry = (String) contents.nextElement();
			if (entry.endsWith("/")) 
				collectProjectFiles(result, new BundleLocation(base.bundle, new Path(entry)), monitor);
		}
	}
}
