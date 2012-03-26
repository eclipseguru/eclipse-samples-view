/**
 *
 */
package org.eclipsercp.book.tools.views;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;

import org.eclipsercp.book.tools.IConstants;
import org.eclipsercp.book.tools.Sample;
import org.eclipsercp.book.tools.SamplesModel;

public class SamplesLabelProvider extends LabelProvider implements IFontProvider {
	private Image image;

	public void dispose() {
		// properly dispose the image
		image.dispose();

		// super
		super.dispose();
	}

	public Font getFont(final Object element) {
		final Sample sample = (Sample) element;
		if (SamplesModel.getCurrentSampleNumber().equals(sample.getNumber())) {
			return JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT);
		}
		return null;
	}

	public Image getImage(final Object element) {
		if (image == null) {
			image = AbstractUIPlugin.imageDescriptorFromPlugin(IConstants.PLUGIN_ID, "icons/bkmrk_nav.gif").createImage(true);
		}
		return image;
	}

	public String getText(final Object element) {
		return ((Sample) element).getLocation().location.lastSegment();
	}
}
