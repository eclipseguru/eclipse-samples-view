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
package org.eclipsercp.book.tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Enumeration;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.ErrorDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;

/**
 * Useful utility methods with no home
 */
public class Utils {

	public static void copy(final BundleLocation location, final File destination, final boolean overwrite, final IProgressMonitor monitor) throws IOException {
		// the length of the prefix to trim off
		final int rootLength = location.location.toString().length();
		destination.mkdirs();
		for (final Enumeration e = location.getEntries(); (null != e) && e.hasMoreElements();) {
			if (monitor.isCanceled()) {
				return;
			}
			final String entry = (String) e.nextElement();
			if (entry.endsWith("/")) {
				final Path entryPath = new Path(entry);
				copy(new BundleLocation(location.bundle, entryPath), new File(destination, entryPath.lastSegment()), overwrite, monitor);
			} else {
				final OutputStream out = new FileOutputStream(new File(destination, entry.substring(rootLength)));
				final InputStream in = location.bundle.getEntry(entry).openStream();
				copyStream(new BufferedInputStream(in), true, new BufferedOutputStream(out), true);
			}
		}
	}

	/**
	 * Copy an input stream to an output stream. Optionally close the streams
	 * when done. Return the number of bytes written.
	 */
	public static int copyStream(final InputStream in, final boolean closeIn, final OutputStream out, final boolean closeOut) throws IOException {
		try {
			int written = 0;
			final byte[] buffer = new byte[16 * 1024];
			int len;
			while ((len = in.read(buffer)) != -1) {
				out.write(buffer, 0, len);
				written += len;
			}
			return written;
		} finally {
			try {
				if (closeIn) {
					in.close();
				}
			} finally {
				if (closeOut) {
					out.close();
				}
			}
		}
	}

	/**
	 * @return
	 */
	public static Shell getActiveShell() {
		final IWorkbenchWindow window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
		if (window != null) {
			return window.getShell();
		}
		return PlatformUI.getWorkbench().getDisplay().getActiveShell();
	}

	/**
	 * Utility method to handle errors and extract status objects from
	 * exceptions. An error dialog is shown with the title and message provided
	 * and possibly with more information from the status objects.
	 * 
	 * @param shell
	 *            the parent shell of the error dialog
	 * @param exception
	 *            the exception to handle
	 * @param title
	 *            the title of the error dialog
	 * @param message
	 *            the message to show in the error dialog
	 */
	public static void handleError(final Shell shell, final Exception exception, String title, String message) {
		IStatus status = null;
		boolean log = false;
		boolean dialog = false;
		Throwable t = exception;
		if (exception instanceof InvocationTargetException) {
			t = ((InvocationTargetException) exception).getTargetException();
		}
		if (t instanceof CoreException) {
			status = ((CoreException) t).getStatus();
			log = true;
			dialog = true;
		} else if (t instanceof InterruptedException) {
			return;
		} else if (t instanceof OperationCanceledException) {
			return;
		} else {
			status = new Status(IStatus.ERROR, IConstants.PLUGIN_ID, 1, "Error", t);
			log = true;
			dialog = true;
		}
		if (status == null) {
			return;
		}
		if (!status.isOK()) {
			IStatus toShow = status;
			if (status.isMultiStatus()) {
				final IStatus[] children = status.getChildren();
				if (children.length == 1) {
					toShow = children[0];
				}
			}
			if (title == null) {
				title = status.getMessage();
			}
			if (message == null) {
				message = status.getMessage();
			}
			if (dialog && (shell != null)) {
				if (Display.getCurrent() != null) {
					ErrorDialog.openError(shell, title, message, toShow);
				} else {
					final String title2 = title;
					final String message2 = message;
					final IStatus toShow2 = toShow;
					PlatformUI.getWorkbench().getDisplay().asyncExec(new Runnable() {
						@Override
						public void run() {
							ErrorDialog.openError(shell, title2, message2, toShow2);
						}
					});
				}
			}
			if (log || (shell == null)) {
				Platform.getLog(Platform.getBundle(IConstants.PLUGIN_ID)).log(toShow);
			}
		}
	}

	/**
	 * Runs the given runnable in a progress dialog.
	 * 
	 * @param shell
	 *            the parent for the progress dialog
	 * @param runnable
	 *            the operation to run
	 */
	public static void run(final Shell shell, final IRunnableWithProgress runnable) {
		final ProgressMonitorDialog d = new ProgressMonitorDialog(shell);
		try {
			d.run(true, true, runnable);
		} catch (final InvocationTargetException e) {
			ErrorDialog.openError(shell, "Import Error", "Error importing sample", statusFrom(e.getTargetException()));
		} catch (final InterruptedException e) {
			ErrorDialog.openError(shell, "Import Error", "Error importing sample", statusFrom(e));
		}
	}

	/**
	 * Wraps the provided exception in a status object.
	 * 
	 * @param e
	 *            the exception to wrap
	 * @return a status object
	 */
	public static Status statusFrom(final Throwable e) {
		return new Status(IStatus.ERROR, IConstants.PLUGIN_ID, 0, e.toString(), e);
	}
}
