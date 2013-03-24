/**
 *
 */
package org.eclipsercp.book.tools;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.StringTokenizer;

public class Sample {

	public static class ProjectImport {
		private final Float sampleNumber;
		private final String projectName;
		private boolean replace;

		public ProjectImport(final Float sampleNumber, final String projectName) {
			this.sampleNumber = sampleNumber;
			if (projectName.indexOf(';') > -1) {
				final String[] tokens = projectName.split(";");
				this.projectName = tokens[0];
				for (int i = 1; i < tokens.length; i++) {
					if (tokens[i].equals("noreplace")) {
						replace = false;
					}
				}
			} else {
				this.projectName = projectName;
				replace = true;
			}
		}

		public String getProjectName() {
			return projectName;
		}

		public Float getSampleNumber() {
			return sampleNumber;
		}

		public boolean isReplace() {
			return replace;
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
		InputStream in = null;
		try {
			in = importsLocation.openStream();
			final Properties props = new Properties();
			props.load(in);
			for (final Entry<Object, Object> e : props.entrySet()) {
				final Object key = e.getKey();
				if (key.equals("fileToOpen")) {
					fileToOpen = (String) e.getValue();
				} else {
					try {
						imports.add(new ProjectImport(Float.valueOf((String) key), (String) e.getValue()));
					} catch (final NumberFormatException ignore) {
						// unsupported key
					}
				}

			}
		} catch (final Exception e) {
			Utils.handleError(Utils.getActiveShell(), e, "Error", "Unable to read the list of projects to import.");
			return;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (final IOException e) {
					// ignore
				}
			}
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
