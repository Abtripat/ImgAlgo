package invizio.imgalgo.label;

import invizio.imgalgo.util.Pixel;
import net.imglib2.Cursor;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.algorithm.neighborhood.Neighborhood;
import net.imglib2.algorithm.neighborhood.RectangleShape;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.basictypeaccess.IntAccess;
import net.imglib2.img.basictypeaccess.array.IntArray;
import net.imglib2.type.NativeType;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.integer.IntType;
import net.imglib2.util.Fraction;
import net.imglib2.view.Views;

/**
 * 
 * @author Benoit Lombardot
 * 
 */


/*
 * TODO:
 *	[-] algorithm similar to HMaxima (simpler though)
 * 	[-] make the code img factory agnostic
 * 		[-] replace the use of parent
 * 		[-] do the pass in reverse order with a random accessible interval
 *  [-] can the algorithm be processed in place
 *  [-] make the connectivity a parameter
 * 	[x] extend a LabelAlgorithm class
 *  [x] use RAI rather than Img		
 
 */

public class CCL < T extends RealType<T> & NativeType<T> > extends DefaultLabelAlgorithm<T>  {

	// parameters
	private float threshold=0;
	
	// data structure for the algorithm
	private int[] parent;
	
	
	
	// speed : 4ms at the best of 10 successive filtering of the blob image
	// 850 ms with t1-head
	public CCL(RandomAccessibleInterval<T> input, float threshold ) {
		
		super(input);
		
		this.threshold = threshold;
	}

	
	@Override
	protected void process()
	{
		long numPixel = Views.iterable( input ).size();
		
		int ndim = input.numDimensions();
		long[] dims = new long[ndim]; input.dimensions(dims);
		parent = new int[(int) numPixel ];
		for( int i=0; i<parent.length; i++)
			parent[i]=-1;
		
		// extend the input
		T minT = input.randomAccess().get().createVariable();
		minT.setReal(minT.getMinValue());
		RandomAccessible< T > input_X = Views.extendValue(input, minT );
		
		
		// create a flat iterable cursor
		//long[] min = new long[ ndim ], max = new long[ ndim ];
        //for ( int d = 0; d < ndim; ++d ){   min[ d ] = 0 ;    max[ d ] = dims[d] - 1 ;  }
        //FinalInterval interval = new FinalInterval( min, max );
        final Cursor< T > ipix = Views.flatIterable( Views.interval( input_X, input)).cursor();
        		
		
		// define the connectivity
		long[][] neigh = Pixel.getConnectivityPos(ndim, Pixel.Connectivity.LEXICO_FULL);
		int[] n_offset = Pixel.getIdxOffsetToCenterPix(neigh, dims);
        final RectangleShape shape = new RectangleShape( 1, true ); // defines a hyper-square of radius one 
        int nNeigh = (int)((Math.pow(3, ndim)-1)/2);
		
        
		// first path, go through all the pixel and check already visited neighbor for existing tree
        int p = -1, n, nlocal;
        for ( final Neighborhood< T > neighborhood : shape.neighborhoods( Views.interval( input_X, input)) )
        {
        	p++;
			ipix.fwd();
			
			if (ipix.get().getRealFloat()>threshold)
			{
				// makeset(p);
				parent[p]=p;
				
				// loop on neighbor
				Cursor<T> nCursor = neighborhood.cursor();
				nlocal=0;
				for( int i = 0; i<nNeigh; i++)
				{
					// if n is in bounds and n is processed ()
					if(nCursor.next().getRealFloat()>threshold)
					{
						n=p+n_offset[nlocal];
						union(n,p);
					}
					nlocal++;
				}
				//is_processed(p)= true, always true given pixel visit order 
			}
			
		}
		
        
		// second path to label the tree
        int current_label = 0;
		for(int i = parent.length-1 ; i>-1 ; i--)
		{
			if (parent[i]>-1)
			{
				if( parent[i]==i ) // create a new label
					parent[i] = ++current_label;
				else // propagate the label of parent. (i.e. by construction parent are always visited first)
					parent[i] = parent[ parent[i] ];
			}
			else{
				parent[i]=0;
			}
		}
		numberOfLabels = current_label;
		
        // create an output image from the label array
		final IntAccess access = new IntArray( parent );
		final Fraction frac = new Fraction(1,1);
		final ArrayImg<IntType, IntAccess> array =	new ArrayImg<IntType, IntAccess>( access, dims, frac );// create a Type that is linked to the container
		//final ArrayImg<IntType, IntAccess> array = new ArrayImg<IntType, IntAccess>( access, dims, 1 );// create a Type that is linked to the container
		final IntType linkedType = new IntType( array );
		// pass it to the DirectAccessContainer
		array.setLinkedType( linkedType );
		
		labelMap =  array;
		
		return;
	}
	
	
	private int find_root(int p)
	{
		if( parent[p]==p )
			return p;
		return parent[p] = find_root(parent[p]);
	}
	
	private void union(int n, int p)
	{
		int r = find_root(n);
		if( r!=p )
			parent[r]=p;
	}
	
//	public <T extends RealType<T> > Img<IntType> Labeling(Img<T> input)
//	{
//		return Labeling( input, 0);
//	}
	

}
