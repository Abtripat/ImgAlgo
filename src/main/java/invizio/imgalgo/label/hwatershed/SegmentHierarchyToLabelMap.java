package invizio.imgalgo.label.hwatershed;



import net.imglib2.Cursor;
import net.imglib2.IterableInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.view.Views;
import net.imglib2.img.Img;


/**
 * 
 * @author Benoit Lombardot
 * 
 */

// if pos is updated tree labeling does not change
// if hMin is updated the segmentMap slice is constant but still need to be relabeled. currently we don't keep a copy and have to redo the clicking

// ideally multithread the copy of the segmentMap hyperslice (look how to optimize that)
// as well as the filling of the labelMap 

// the code definitely needs review there might be some confusion between input data and hyperslice

@Deprecated
public class SegmentHierarchyToLabelMap <T extends RealType<T>> {

	Tree segmentTree0;
	Img<IntType> segmentMap0;
	Img<T> intensity0;
	
	int[] nodeIdToLabel;  	// current tree labeling
	int nLabels; 			// the number of the label in the label map for the current H (defines the tree labeling) and Threshold
	
	
	Img<IntType> segmentMap; // current hyperslice
	IterableInterval<T> intensity; // current hyperslice
	
	/**
	 * 
	 * @param segmentTree a tree where each leaf correspond to a label in the segment map and each other not to a merging of these region
	 * @param segmentMap0 a labelmap defining non overlapping region
	 * @param intensity0 a graylevel image the same size as segmentMap0
	 */
	public SegmentHierarchyToLabelMap(Tree segmentTree, Img<IntType> segmentMap0, Img<T> intensity0 ){
		
		this.segmentTree0 = segmentTree;
		this.segmentMap0 = segmentMap0;
		this.intensity0 = intensity0;
		
	}
	
	/**
	 * @param hMin the minimum  dynamics of a peak not to be merged
	 */
	public void updateTreeLabeling(float hMin ){
		boolean makeNewLabels = false;
		nodeIdToLabel =  TreeUtils.getTreeLabeling(segmentTree0, "dynamics", hMin, makeNewLabels );
		//return getNumberOfLabels();
	}

	//public int updateTreeLabeling(float hMin, boolean makeNewLabels ){
	//	nodeIdToLabel =  TreeUtils.getTreeLabeling(segmentTree0, "dynamics", hMin, makeNewLabels );
	//	return getNumberOfLabels();
	//}
	
	
	/**
	 * 
	 * @return the number of label in the current tree labeling
	 */
	public int getNumberOfLabels(){
		
		return nLabels;
		
		//Set<Integer> labelSet = new HashSet<Integer>();
		//if( nodeIdToLabel != null )
		//	for( int val : nodeIdToLabel)
		//		if( val>0)
		//			labelSet.add(val);
		//return labelSet.size();
	}
	
	
	/**
	 * Relabel the label map according to the tree labeling.
	 * @param threshold , all pixel below threshold are set to 0 
	 * @param percentFlooding , percent of the peak that will be flooded  (percent between label maximum and the threshold) 
	 * @return a label image corresponding to the current tree labeling, threshold, percentFlooding parameters
	 */
	public Img<IntType> getLabelMap( float threshold, float percentFlooding){
		
		intensity = (IterableInterval<T>) intensity0;
		
		int nDims = segmentMap0.numDimensions();
		long[] dims = new long[nDims];
		segmentMap0.dimensions(dims);
		segmentMap = segmentMap0.factory().create(dims, segmentMap0.firstElement().createVariable() );
		Cursor<IntType> cursor = segmentMap.cursor();
		Cursor<IntType> cursor0 = segmentMap0.cursor();
		while(cursor0.hasNext()){
			cursor.next().set( cursor0.next().get() );
		}
		
		
		Img<IntType> labelMap = fillLabelMap(threshold, percentFlooding);
		
		return labelMap;
	}
	
	
	
	/**
	 * Relabel a particular slice of the label map according to the tree labeling.	
	 * @param threshold , all pixel below threshold are set to 0 
	 * @param percentFlooding , percent of the peak that will be flooded  (percent between label maximum and the threshold) 
	 * @param dim , dimension along which the slice will be cut
	 * @param pos , position of the slice on long the dimension
	 * @return a label map
	 */
	public Img<IntType> getLabelMap( float threshold, float percentFlooding, int dim, long pos){
		
		int nDims = segmentMap0.numDimensions();
		
		if (nDims>2)
		{	
			long[] dimensions = new long[nDims];
			segmentMap0.dimensions(dimensions);
			long[] newDimensions = new long[nDims-1];
			int count = 0;
			for ( int d = 0; d < nDims ; ++d )
			{
				if(d!=dim){
					newDimensions[count] = dimensions[d];
					count++;
				}
				//else
					//newDimensions[d] = 1;
			}
			segmentMap = segmentMap0.factory().create(newDimensions, segmentMap0.firstElement().createVariable());
			Cursor<IntType> cursor = segmentMap.cursor();
			Cursor<IntType> cursor0 = Views.hyperSlice(segmentMap0, dim, pos).cursor();
			
			while ( cursor.hasNext() )
				cursor.next().set( cursor0.next().get() );
			
			intensity = (IterableInterval<T>) Views.hyperSlice(intensity0, dim, pos);
		}
		else{
			long[] dims = new long[nDims];
			segmentMap0.dimensions(dims);
			segmentMap = segmentMap0.factory().create(dims, segmentMap0.firstElement().createVariable() );
			Cursor<IntType> cursor = segmentMap.cursor();
			Cursor<IntType> cursor0 = segmentMap0.cursor();
			while(cursor0.hasNext()){
				cursor.next().set( cursor0.next().get() );
			}
			intensity = (IterableInterval<T>) intensity0;
		}
		
		Img<IntType> labelMap = fillLabelMap( threshold, percentFlooding);
		
		return labelMap;
	}
	
	
	
	
	protected Img<IntType> fillLabelMap( float threshold, float percentFlooding ){
		
		
		
		double[] Imax = segmentTree0.getFeature("Imax");
		double minI = Imax[0]; // Imax is set to min of the image for the node 0  (it represents the background)
		// create a new (continuous) labeling where only the region visible in the current labeling (and threshold) are numbered
		int nNodes = segmentTree0.getNumNodes();
		int[] nodeIdToLabel2 = new int[nNodes];
		int[] labelled = new int[nNodes];
		int currentLabel = 0;
		for(int i=0; i<nNodes ; i++)
		{
			int parent = nodeIdToLabel[i];
			double ImaxParent = Imax[parent]; 
			if( ImaxParent >= threshold && ImaxParent>minI )
			{
				int parentLabel = labelled[parent];
				if( parentLabel > 0 ) // parent is already labelled, label the node according to its parent
				{
					nodeIdToLabel2[i] = parentLabel;
				}
				else // parent is not labeled yet, create a new label
				{
					currentLabel++;
					labelled[parent] = currentLabel;
					nodeIdToLabel2[i] = currentLabel;
				}
			}
			// else do nothing since the nodeIdToLabel2 is initialized to 0 
		}
		this.nLabels = currentLabel;
		
		
		int nNode = Imax.length;
		float[] peakThresholds = new float[nNode];
		for(int i=0;i<nNode; i++)
			peakThresholds[i] =  threshold + ((float)Imax[i]-threshold)*(1f-percentFlooding/100f);
		
		//System.out.println("lookup node to label: " + Arrays.toString(nodeIdToLabel) );
		//System.out.println("lookup node to label2: " + Arrays.toString(nodeIdToLabel2) );
		//System.out.println("lookup node to Imax: " + Arrays.toString(Imax) );
		//double[] dynamics = segmentTree0.getFeature("dynamics");
		//System.out.println("lookup node to dyn: " + Arrays.toString(dynamics) );
		//System.out.println("lookup node to thresh: " + Arrays.toString(peakThresholds) );
		
		
		
		Cursor<IntType> cursor = segmentMap.cursor();
		Cursor<T> cursorImg = intensity.cursor();
		while( cursor.hasNext() )
		{
			T imgPixel = cursorImg.next();
			float val =imgPixel.getRealFloat();
			
			IntType pixel = cursor.next();
			if(  val >= threshold )
			{
				int node = (int)pixel.getRealFloat();
				int labelRoot = segmentTree0.getNodes().get(node).getLabelRoot();
				if(  val >= peakThresholds[labelRoot]  )
				{	
					int label = nodeIdToLabel2[node];
					float finalVal = (float)label;
					pixel.setReal( finalVal );
				}
				else
					pixel.setReal( 0.0 );
			}
			else
				pixel.setReal( 0.0 );
		}
		return segmentMap;
		
	}
}