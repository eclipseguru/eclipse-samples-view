package org.eclipsercp.book.tools;

import org.eclipse.core.runtime.QualifiedName;

public interface IConstants {

	/*
	 * Plug-in ids for the tools plug-in.
	 */
	public static final String PLUGIN_ID = "org.eclipsercp.samplings";

	public static final String PROMPT_OVERWRITE_PREF = "dont_ask_again_to_overwrite";

	/**
	 * Persistent property used to cache the sample number currently loaded in
	 * the workspace. The property is stored on each imported project.
	 */
	public QualifiedName SAMPLE_NUMBER_KEY = new QualifiedName("org.eclipsercp.book.tools", "sample-number");
}
