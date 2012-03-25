/**
 * 
 */
package org.eclipsercp.book.tools.views;

import org.eclipsercp.book.tools.*;

import org.eclipse.jface.resource.JFaceResources;
import org.eclipse.jface.viewers.IFontProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.Image;
import org.eclipse.ui.plugin.AbstractUIPlugin;

public class SamplesLabelProvider extends LabelProvider implements IFontProvider {
	private Image image;

	public String getText(Object element) {
		return ((Sample) element).getLocation().location.lastSegment();
	}
	
	public Image getImage(Object element) {
		if (image == null) {
			image = AbstractUIPlugin.imageDescriptorFromPlugin(IConstants.PLUGIN_ID, "icons/bkmrk_nav.gif").createImage(true);
		}
		return image;
	}
	
	public Font getFont(Object element) {
		Sample sample = (Sample) element;
		if (SamplesModel.getCurrentSampleNumber().equals(sample.getNumber())) {
			return JFaceResources.getFontRegistry().getBold(JFaceResources.DEFAULT_FONT); 
		}
		return null;
	}
}
