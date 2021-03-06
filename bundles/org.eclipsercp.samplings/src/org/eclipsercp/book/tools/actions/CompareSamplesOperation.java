package org.eclipsercp.book.tools.actions;

import org.eclipse.compare.CompareConfiguration;
import org.eclipse.compare.CompareUI;
import org.eclipse.compare.structuremergeviewer.IStructureComparator;
import org.eclipse.core.resources.ResourcesPlugin;

import org.eclipsercp.book.tools.Sample;
import org.eclipsercp.book.tools.SamplesModel;
import org.eclipsercp.book.tools.compare.ResourceToFileCompareInput;
import org.eclipsercp.book.tools.compare.SampleStructureCreator;

public class CompareSamplesOperation {

	SamplesModel samples;

	public CompareSamplesOperation(final SamplesModel samples) {
		this.samples = samples;
	}

	public void run(final Sample firstElement) {
		final CompareConfiguration cc = new CompareConfiguration();
		// TODO it is unclear why we are always comparing to number 1?
		final ResourceToFileCompareInput input = new ResourceToFileCompareInput(SamplesModel.getCurrentSampleNumber(), firstElement.getNumber(), cc);

		// Get the path within the zip file to use as the comparison base
		final SampleStructureCreator creator = new SampleStructureCreator(samples);
		final IStructureComparator comparator = creator.getStructure(firstElement);
		input.setSelection(ResourcesPlugin.getWorkspace().getRoot(), comparator);

		CompareUI.openCompareEditor(input);
	}
}
