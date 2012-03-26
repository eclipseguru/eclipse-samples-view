/**
 * 
 */
package org.eclipsercp.book.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

public class Sample {

	public static class ProjectImport {
		private final Float sampleNumber;
		private final String projectName;

		public ProjectImport(final Float sampleNumber, final String projectName) {
			this.sampleNumber = sampleNumber;
			this.projectName = projectName;
		}

		public String getProjectName() {
			return projectName;
		}

		public Float getSampleNumber() {
			return sampleNumber;
		}
	}

	private final BundleLocation location;
	private Float number;
	private final List projects = new ArrayList();
	private final List imports = new ArrayList();
	private String fileToOpen;

	Sample(final BundleLocation location) {
		this.location = location;
		extractSampleNumber();
		extractImports();
	}

	void addProject(final BundleLocation project) {
		projects.add(project);
	}

	private void extractImports() {
		final URL importsLocation = location.bundle.getEntry(location.location.append("imports.def").toString());
		if (importsLocation == null) {
			return;
		}
		Properties props = null;
		try {
			props = new Properties() {
				/** serialVersionUID */
				private static final long serialVersionUID = 1L;

				public synchronized Object put(final Object key, final Object value) {
					try {
						imports.add(new ProjectImport(Float.valueOf((String) key), (String) value));
					} catch (final NumberFormatException e) {
						if (key.equals("fileToOpen")) {
							fileToOpen = (String) value;
						}
					}
					return null;
				}
			};
			final InputStream in = importsLocation.openStream();
			try {
				props.load(in);
			} finally {
				in.close();
			}
		} catch (final IOException e) {
			return;
		}
	}

	private void extractSampleNumber() {
		final StringTokenizer tz = new StringTokenizer(location.location.lastSegment());
		while (tz.hasMoreTokens()) {
			final String elem = tz.nextToken();
			try {
				number = Float.valueOf(elem);
				return;
			} catch (final NumberFormatException e) {
				number = new Float(Float.MAX_VALUE);
			}
		}
	}

	public String getFileToOpen() {
		return fileToOpen;
	}

	public ProjectImport[] getImports() {
		if (imports == null) {
			return new ProjectImport[0];
		}
		return (ProjectImport[]) imports.toArray(new ProjectImport[imports.size()]);
	}

	public BundleLocation getLocation() {
		return location;
	}

	public Float getNumber() {
		return number;
	}

	public List getProjects() {
		return projects;
	}
}
