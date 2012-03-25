/**
 * 
 */
package org.eclipsercp.book.tools;

import java.net.URL;

import java.io.*;
import java.util.*;

public class Sample {
	
	public static class ProjectImport {
		private Float sampleNumber;
		private String projectName;
		
		public ProjectImport(Float sampleNumber, String projectName) {
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
	
	private BundleLocation location;
	private Float number;
	private List projects = new ArrayList();
	private List imports = new ArrayList();
	private String fileToOpen;

	Sample(BundleLocation location) {
		this.location = location;
		extractSampleNumber();
		extractImports();
	}

	private void extractImports() {
		URL importsLocation = location.bundle.getEntry(location.location.append("imports.def").toString());
		if (importsLocation == null)
			return;
		Properties props = null;
		try {
			props = new Properties() {
				public synchronized Object put(Object key, Object value) {
					try {
						imports.add(new ProjectImport(Float.valueOf((String)key), (String)value));
					} catch (NumberFormatException e) {
						if (key.equals("fileToOpen"))
							fileToOpen = (String)value;
					}
					return null;
				}
			};
			InputStream in = importsLocation.openStream();
			try {
				props.load(in);
			} finally {
				in.close();
			}
		} catch (IOException e) {
			return;
		}
	}

	private void extractSampleNumber() {
		StringTokenizer tz = new StringTokenizer(location.location.lastSegment());
		while (tz.hasMoreTokens()) {
			String elem = tz.nextToken();		
			try {
				number = Float.valueOf(elem);
				return;
			} catch(NumberFormatException e) {
				number = new Float(Float.MAX_VALUE);
			}	
		}
	}

	void addProject(BundleLocation project) {
		projects.add(project);
	}

	public Float getNumber() {
		return number;
	}

	public BundleLocation getLocation() {
		return location;
	}

	public List getProjects() {
		return projects;
	}
	
	public String getFileToOpen() {
		return fileToOpen;
	}
	
	public ProjectImport[] getImports() {
		if (imports == null)
			return new ProjectImport[0];
		return (ProjectImport[]) imports.toArray(new ProjectImport[imports.size()]);
	}
}
