package org.eclipsercp.book.tools;

import java.util.Enumeration;
import java.util.SortedMap;
import java.util.TreeMap;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.QualifiedName;

public class SamplesModel {

	public static Float getCurrentSampleNumber() {
		final IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
		for (int i = 0; i < projects.length; i++) {
			final IProject project = projects[i];
			try {
				if (!project.isAccessible()) {
					continue;
				}
				final String result = project.getPersistentProperty(IConstants.SAMPLE_NUMBER_KEY);
				if ((result != null) && (result.charAt(0) != '0')) {
					return new Float(result);
				}
			} catch (final CoreException e) {
				Utils.handleError(Utils.getActiveShell(), e, "Error", "Error finding current sample number.");
			}
		}
		return new Float(1.0);
	}

	private Sample[] samples;

	private BundleLocation location;

	public SamplesModel() {
		super();
	}

	/*
	 * Do a breadth-first search of the given bundle location looking for
	 * projects.  Add any discovered projects to the given result.
	 */
	private void collectProjectFiles(final SortedMap result, final BundleLocation base, final IProgressMonitor monitor) {
		if (monitor.isCanceled()) {
			return;
		}
		monitor.subTask("Reading " + base.location);
		for (final Enumeration contents = base.bundle.getEntryPaths(base.location.toString()); (contents != null) && contents.hasMoreElements();) {
			final String entry = (String) contents.nextElement();
			// if the entry is a .project file then we have found a project so get
			// the parent and record that as a project.
			if (!entry.endsWith("/") && entry.endsWith(IProjectDescription.DESCRIPTION_FILE_NAME)) {
				// skip over any snippets folders
				if (base.location.lastSegment().equals("snippets")) {
					break;
				}
				final IPath projectLocation = new Path(entry).removeLastSegments(1);
				final IPath sampleLocation = projectLocation.removeLastSegments(1);
				if (sampleLocation.isEmpty()) {
					continue;
				}
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
		for (final Enumeration contents = base.bundle.getEntryPaths(base.location.toString()); (contents != null) && contents.hasMoreElements();) {
			final String entry = (String) contents.nextElement();
			if (entry.endsWith("/")) {
				collectProjectFiles(result, new BundleLocation(base.bundle, new Path(entry)), monitor);
			}
		}
	}

	public Sample findSampleById(final Float number) {
		for (int i = 0; i < samples.length; i++) {
			final Sample sample = samples[i];
			if (sample.getNumber().floatValue() == number.floatValue()) {
				return sample;
			}
		}
		return null;
	}

	public Sample[] getSamples() {
		return samples;
	}

	public QualifiedName getTagId() {
		return location.getId();
	}

	/**
	 * Initialize the model from the given location.
	 * 
	 * @param location
	 *            the location in which to look for samples
	 * @longOp this operation is long running and shouldn't be run from a
	 *         responsive thread.
	 */
	public void init(final BundleLocation location, final IProgressMonitor monitor) {
		this.location = location;
		updateProjectsList(monitor);
	}

	private void updateProjectsList(final IProgressMonitor monitor) {
		if (location == null) {
			return;
		}
		monitor.beginTask("Searching", 100);
		samples = new Sample[0];
		final SortedMap result = new TreeMap();
		monitor.worked(10);
		collectProjectFiles(result, location, monitor);
		samples = (Sample[]) result.values().toArray(new Sample[result.values().size()]);
		monitor.done();
	}
}
